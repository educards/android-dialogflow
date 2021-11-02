package com.educards.android.dialogflow;

import android.content.Context;
import android.util.Log;

import com.educards.android.audiorec.AudioDataReceiver;
import com.educards.android.audiorec.AudioRecordingConstants;
import com.educards.android.audiorec.AudioRecordingThread;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.BidiStreamObserver;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.AudioEncoding;
import com.google.cloud.dialogflow.v2.InputAudioConfig;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.StreamingDetectIntentRequest;
import com.google.cloud.dialogflow.v2.StreamingDetectIntentResponse;
import com.google.cloud.dialogflow.v2.StreamingRecognitionResult;
import com.google.protobuf.ByteString;

import java.io.InputStream;

import javax.annotation.Nullable;

/**
 * <ul>
 *  <li>
 *      Dialogflow intent detector which uses the {@link AudioRecordingThread} to record
 *      audio input and passes the recorded audio data down to streaming gRPC API of Dialogflow to detect
 *      the intent online.
 *  </li>
 *  <li>It's valid to call the {@link #startIntentDetection(AudioRecordingThreadInitializer)} multiple times.</li>
 *  <li>{@link #close()} needs to be called to clean up resources such as threads.</li>
 * </ul>
 *
 * @see #startIntentDetection(AudioRecordingThreadInitializer)
 */
public class StreamingIntentDetector implements AutoCloseable {

    private static final String TAG = "StreamingIntentDetector";

    /**
     * Intent returned if the Dialogflow understood the utterance, but couldn't infer
     * any intent. This most commonly happens if there simply doesn't exist any intent definition
     * for the uttered command.
     */
    public static final String UNKNOWN_INTENT = "unknown";

    private Context context;

    /**
     * Dialogflow supported language code (see <a href="https://cloud.google.com/dialogflow/docs/reference/language">Dialogflow languages</a>).
     */
    private String lngCode;

    private IntentObserver observer;

    /**
     * Monitor used to synchronize access to {@link #audioRecordingThread}
     * and {@link #dialogflowClientStream} since these fields are accessed and modified
     * from worker threads.
     */
    private Object monitor = new Object();

    private volatile AudioRecordingThread audioRecordingThread;
    private volatile ClientStream<StreamingDetectIntentRequest> dialogflowClientStream;

    private SessionsClient dialogflowSessionsClient;
    private SessionName dialogflowSessionName;

    /**
     * @param credentialsRawRes JSON server key stored in 'raw' resource folder.
     * @param lngCode Dialogflow supported language code (see <a href="https://cloud.google.com/dialogflow/docs/reference/language">Dialogflow languages</a>).
     */
    public StreamingIntentDetector(Context context, String sessionUuid, int credentialsRawRes, String lngCode, IntentObserver observer) {
        this.context = context;
        this.lngCode = lngCode;
        this.observer = observer;

        initDialogflowV2(credentialsRawRes, sessionUuid);
    }

    /**
     * Initializes Dialogflow V2 client.
     */
    private void initDialogflowV2(int credentialsRawRes, String sessionUuid) {

        try {

            InputStream stream = context.getResources().openRawResource(credentialsRawRes);
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream);
            String projectId = ((ServiceAccountCredentials)credentials).getProjectId();

            SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
            SessionsSettings sessionsSettings = settingsBuilder
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();

            dialogflowSessionsClient = SessionsClient.create(sessionsSettings);
            dialogflowSessionName = SessionName.of(projectId, sessionUuid);

        } catch (Throwable t) {
            throw new RuntimeException("Failed to initialize Dialogflow client.");
        }
    }

    /**
     * Runs the {@link AudioRecordingThread} and passes audio bytes down to streaming gRPC API
     * of Dialogflow to detect the desired intent.
     */
    public void startIntentDetection(@Nullable AudioRecordingThreadInitializer audioRecordingThreadInitializer) {

        synchronized (monitor) {

            if (audioRecordingThread != null && !audioRecordingThread.isStopRequested()) {
                Log.d(TAG, AudioRecordingThread.class.getSimpleName() + " is already running.");
                return;
            }

            audioRecordingThread = new AudioRecordingThread();
            audioRecordingThread.addAudioDataReceiver(new DialogflowStreamingReceiver());

            // Also allow the client of this library to do some
            // custom configuration if desired.
            if (audioRecordingThreadInitializer != null) {
                audioRecordingThreadInitializer.onAudioRecordingThreadInit(audioRecordingThread);
            }

            audioRecordingThread.startRecording();
        }
    }

    @Override
    public void close() {
        synchronized (monitor) {
            if (audioRecordingThread != null) {
                audioRecordingThread.requestStop();
            }
            if (dialogflowSessionsClient != null) {
                dialogflowSessionsClient.close();
            }
        }
    }

    /**
     * {@link AudioDataReceiver} which immediately streams the received audio data
     * down to Dialogflow server by utilizing {@link #dialogflowSessionsClient}.
     */
    class DialogflowStreamingReceiver implements AudioDataReceiver {

        @Override
        public void onAudioRecordingStarted() {
            Log.d(TAG, String.format(
                    "onAudioRecordingStarted() [thread=%s]",
                    Thread.currentThread().getName()));

            dialogflowSessionsClient.streamingDetectIntentCallable().call(new BidiStreamObserverImpl());
        }

        @Override
        public void onAudioDataReceived(byte[] audioData, int length) {

            // This callback is called each time a buffer (audioData) is flushed from AudioRecorder.
            // We further pass the buffer to Dialogflow to detect the intent - it is configured to use
            // the streaming API. We will be notified once the intent is detected.

            synchronized (monitor) {

                if (audioRecordingThread.isStopRequested()) {
                    Log.d(TAG, String.format(
                            "Received audio data ignored [audioRecordingThread.isStopRequested() = %s]",
                            audioRecordingThread.isStopRequested()));
                    return;
                }

                if (dialogflowClientStream == null) {
                    Log.d(TAG, "Waiting for clientStream initialization");
                    try {
                        monitor.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                try {
                    dialogflowClientStream.send(
                            StreamingDetectIntentRequest.newBuilder()
                                    .setInputAudio(ByteString.copyFrom(audioData, 0, length))
                                    .build());

                } catch (Throwable t) {
                    Log.e(TAG, "Streaming intent detection failed", t);
                }
            }
        }

        @Override
        public void onAudioRecordingStopped() {
            Log.d(TAG, String.format(
                    "onAudioRecordingStopped() [thread=%s]",
                    Thread.currentThread().getName()));

            synchronized (monitor) {
                dialogflowClientStream.closeSend();
                dialogflowClientStream = null;
                audioRecordingThread = null;
            }
        }

    }

    class BidiStreamObserverImpl implements BidiStreamObserver<StreamingDetectIntentRequest, StreamingDetectIntentResponse> {

        @Override
        public void onReady(ClientStream<StreamingDetectIntentRequest> stream) {
            Log.d(TAG, String.format(
                    "BidiStreamObserver.onReady() [thread=%s]",
                    Thread.currentThread().getName()));

            // The first request must **only** contain the audio configuration.
            sendAudioConfig(stream);

            synchronized (monitor) {
                dialogflowClientStream = stream;

                // The bidiStream is ready for streaming the audio up to server.
                // Release the audio recording thread which is waiting until the
                // networking client is ready.
                monitor.notify();
            }
        }

        private void sendAudioConfig(ClientStream<StreamingDetectIntentRequest> stream) {

            InputAudioConfig inputAudioConfig =
                    InputAudioConfig.newBuilder()

                            .setAudioEncoding(AudioEncoding.AUDIO_ENCODING_LINEAR_16)
                            .setSampleRateHertz(AudioRecordingConstants.SAMPLE_RATE)

                            .setLanguageCode(lngCode)

                            // The recognizer will detect a single spoken utterance in input
                            // audio. Recognition ceases when it detects the audio's voice has
                            // stopped or paused. In this case, once a detected intent is received, the
                            // client should close the stream and start a new request with a new stream as
                            // needed.
                            .setSingleUtterance(true)

                            .build();

            QueryInput queryInput = QueryInput.newBuilder().setAudioConfig(inputAudioConfig).build();

            stream.send(
                    StreamingDetectIntentRequest.newBuilder()
                            .setSession(dialogflowSessionName.toString())
                            .setQueryInput(queryInput)
                            .build());
        }

        @Override
        public void onStart(StreamController controller) {
            Log.d(TAG, String.format(
                    "BidiStreamObserver.onStart() [thread=%s]",
                    Thread.currentThread().getName()));
            observer.onStart(StreamingIntentDetector.this, controller);
        }

        @Override
        public void onResponse(StreamingDetectIntentResponse response) {
            Log.d(TAG, String.format(
                    "BidiStreamObserver.onResponse() [thread=%s]",
                    Thread.currentThread().getName()));

            observer.onResponse(StreamingIntentDetector.this, response);

            if (!response.getQueryResult().getIntent().getDisplayName().isEmpty())
            {   // Intent detected?
                requestStopAudioRecording();
                observer.onResponseIntent(StreamingIntentDetector.this, response);

            } else if (response.getRecognitionResult().getMessageType() == StreamingRecognitionResult.MessageType.END_OF_SINGLE_UTTERANCE)
            {   // End of utterance?
                requestStopAudioRecording();
                observer.onResponseEndOfUtterance(StreamingIntentDetector.this, response);
            }
        }

        private void requestStopAudioRecording() {
            synchronized (monitor) {
                if (audioRecordingThread != null) {
                    audioRecordingThread.requestStop();
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            Log.e(TAG, String.format(
                    "BidiStreamObserver.onError() [thread=%s]",
                    Thread.currentThread().getName()), t);

            observer.onError(StreamingIntentDetector.this, t);
        }

        @Override
        public void onComplete() {
            Log.d(TAG, String.format(
                    "BidiStreamObserver.onComplete() [thread=%s]",
                    Thread.currentThread().getName()));

            observer.onComplete(StreamingIntentDetector.this);
        }

    }

}
