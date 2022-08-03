/*
 * Copyright © 2021 Educards Learning, SL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
 *      Intent detector which uses the {@link AudioRecordingThread} to record
 *      audio input from mic and passes the recorded audio data down to gRPC API of Dialogflow to detect
 *      the intent online.
 *  </li>
 *  <li>Audio data are streamed at every point of the procedure. No audio data are saved to filesystem during intent detection.</li>
 *  <li>It's valid to call the {@link #startIntentDetection(AudioRecordingThreadInitializer)} multiple times.</li>
 *  <li>{@link #close()} needs to be called to clean up resources such as threads.</li>
 * </ul>
 *
 * @see #startIntentDetection(AudioRecordingThreadInitializer)
 */
public class DialogflowIntentDetector implements AutoCloseable {

    private static final String TAG = "DialogflowIntentDetect";

    private final Context context;

    /**
     * Dialogflow supported language code (see <a href="https://cloud.google.com/dialogflow/docs/reference/language">Dialogflow languages</a>).
     */
    private final String lngCode;

    private final DialogflowIntentObserver observer;

    /**
     * Monitor used to synchronize access to {@link #audioRecordingThread}
     * and {@link #dialogflowClientStream} since these fields are accessed and modified
     * from worker threads.
     */
    private final Object monitor = new Object();
    private volatile boolean stopRequested;

    private volatile AudioRecordingThread audioRecordingThread;
    private volatile ClientStream<StreamingDetectIntentRequest> dialogflowClientStream;

    private SessionsClient dialogflowSessionsClient;
    private SessionName dialogflowSessionName;

    /**
     * @param perAgentCredentialsRawRes JSON formatted GPC project key stored in raw resource folder.
     *                                  Google requires a dedicated GCP project for each Dialogflow agent.
     *                                  There is no way to manage multiple Dialogflow agents under a single GCP project.
     *                                  Therefore for each Dialogflow agent you would like to query you need to initialize
     *                                  dedicated {@link DialogflowIntentDetector} with a corresponding GCP project key.
     * @param lngCode Language supported by your Dialogflow agent
     *                (see <a href="https://cloud.google.com/dialogflow/docs/reference/language">Dialogflow languages</a>).
     */
    public DialogflowIntentDetector(Context context, String sessionUuid, int perAgentCredentialsRawRes, String lngCode, DialogflowIntentObserver observer) {
        this.context = context;
        this.lngCode = lngCode;
        this.observer = observer;

        initDialogflowV2(perAgentCredentialsRawRes, sessionUuid);
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
            throw new RuntimeException("Failed to initialize Dialogflow client.", t);
        }
    }

    /**
     * @see #startIntentDetection(AudioRecordingThreadInitializer)
     */
    public void startIntentDetection() {
        startIntentDetection(null);
    }

    /**
     * Runs the {@link AudioRecordingThread} and passes audio bytes down to streaming gRPC API
     * of Dialogflow to detect the desired intent.
     */
    public void startIntentDetection(@Nullable AudioRecordingThreadInitializer audioRecordingThreadInitializer) {

        synchronized (monitor) {

            if (audioRecordingThread != null && !audioRecordingThread.isStopRequested()) {
                if (BuildConfig.DEBUG) Log.d(TAG, AudioRecordingThread.class.getSimpleName() + " is already running.");
                return;
            }

            audioRecordingThread = new AudioRecordingThread();
            audioRecordingThread.addAudioDataReceiver(new DialogflowStreamingReceiver());

            // Also allow the client of this library to do some
            // custom configuration if desired.
            if (audioRecordingThreadInitializer != null) {
                audioRecordingThreadInitializer.onAudioRecordingThreadInit(audioRecordingThread);
            }

            stopRequested = false;
            audioRecordingThread.startRecording();
        }
    }

    /**
     * Requests the stop of intent detection.
     * This won't close the intent detector, just stop intent detection, therefore
     * calling {@link #startIntentDetection()} again is allowed.
     *
     * @see #close()
     */
    public void requestStop() {
        requestStop(null);
    }

    /**
     * Same as {@link #requestStop()}, but enables the client to
     * be notified on stop event (<code>stoppedCallback</code>).
     * <code>stoppedCallback</code> is invoked immediately
     * is the detector is already stopped.
     */
    public void requestStop(@Nullable Runnable stoppedCallback) {
        synchronized (monitor) {

            if (audioRecordingThread != null && !isStopRequested()) {
                // running and no stop has yet been request
                audioRecordingThread.requestStop(stoppedCallback);
                stopRequested = true;

            } else if (audioRecordingThread == null) {
                // already stopped
                // notify the observer immediately
                if (stoppedCallback != null) stoppedCallback.run();

            } else {
                // previously requested stop is in progress
            }
        }
    }

    /**
     * @return
     * <ul>
     *     <li><code>true</code> if intent detection has been stopped due to
     *         an explicit request to stop it ({@link #requestStop()}).</li>
     *     <li><code>false</code> if:
     *     <ul>
     *         <li>intent detection has not yet been started</li>
     *         <li>intent detection has been started (again) and is in progress</li>
     *         <li>intent detection was stopped implicitly by the intent detection engine (an intent has been detected)</li>
     *     </ul>
     * </ul>
     */
    public boolean isStopRequested() {
        synchronized (monitor) {
            return stopRequested;
        }
    }

    public boolean isRunning() {
        synchronized (monitor) {
            if (audioRecordingThread != null) {
                return audioRecordingThread.isRecording();
            } else {
                return false;
            }
        }
    }

    public boolean isActive() {
        synchronized (monitor) {
            return isRunning() && !isStopRequested();
        }
    }

    /**
     * Stops intent detection and releases all resources.
     */
    @Override
    public void close() {

        requestStop();

        synchronized (monitor) {
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

        private static final String TAG = "DialogflowStreamingRec";

        @Override
        public void onAudioRecordingStarted() {
            if (BuildConfig.DEBUG) Log.d(TAG, String.format("onAudioRecordingStarted() [thread=%s]", Thread.currentThread().getName()));

            dialogflowSessionsClient.streamingDetectIntentCallable().call(new BidiStreamObserverImpl());
        }

        @Override
        public void onAudioDataReceived(byte[] audioData, int length) {

            // This callback is called each time a buffer (audioData) is flushed from AudioRecorder.
            // We further pass the buffer to Dialogflow to detect the intent - it is configured to use
            // the streaming API. We will be notified once the intent is detected.

            synchronized (monitor) {

                if (audioRecordingThread.isStopRequested()) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, String.format(
                                "Received audio data ignored [audioRecordingThread.isStopRequested() = %s]",
                                audioRecordingThread.isStopRequested()));
                    }
                    return;
                }

                if (dialogflowClientStream == null) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Waiting for clientStream initialization");
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
            if (BuildConfig.DEBUG) {
                Log.d(TAG, String.format(
                        "onAudioRecordingStopped() [thread=%s]",
                        Thread.currentThread().getName()));
            }

            synchronized (monitor) {
                if (dialogflowClientStream != null) {
                    dialogflowClientStream.closeSend();
                    dialogflowClientStream = null;
                }
                audioRecordingThread = null;
            }
        }

    }

    class BidiStreamObserverImpl implements BidiStreamObserver<StreamingDetectIntentRequest, StreamingDetectIntentResponse> {

        private static final String TAG = "BidiStreamObserverImpl";

        @Override
        public void onReady(ClientStream<StreamingDetectIntentRequest> stream) {
            if (BuildConfig.DEBUG) Log.d(TAG, String.format("onReady() [thread=%s]", Thread.currentThread().getName()));

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
            if (BuildConfig.DEBUG) Log.d(TAG, String.format("onStart() [thread=%s]", Thread.currentThread().getName()));
            observer.onStart(DialogflowIntentDetector.this, controller);
        }

        @Override
        public void onResponse(StreamingDetectIntentResponse response) {
            if (BuildConfig.DEBUG) Log.d(TAG, String.format("onResponse() [thread=%s]", Thread.currentThread().getName()));

            observer.onResponse(DialogflowIntentDetector.this, response);

            if (!response.getQueryResult().getIntent().getDisplayName().isEmpty())
            {   // Intent detected?
                requestStopAudioRecording();
                observer.onResponseIntent(DialogflowIntentDetector.this, response);

            } else if (response.getRecognitionResult().getMessageType() == StreamingRecognitionResult.MessageType.END_OF_SINGLE_UTTERANCE)
            {   // End of utterance?
                requestStopAudioRecording();
                observer.onResponseEndOfUtterance(DialogflowIntentDetector.this, response);
            }
        }

        private void requestStopAudioRecording() {
            if (BuildConfig.DEBUG) Log.d(TAG, String.format("requestStopAudioRecording() [thread=%s]", Thread.currentThread().getName()));
            synchronized (monitor) {
                if (audioRecordingThread != null) {
                    audioRecordingThread.requestStop();
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            Log.e(TAG, String.format("onError() [thread=%s]", Thread.currentThread().getName()), t);
            requestStopAudioRecording();
            observer.onError(DialogflowIntentDetector.this, t);
        }

        @Override
        public void onComplete() {
            if (BuildConfig.DEBUG) Log.d(TAG, String.format("onComplete() [thread=%s]", Thread.currentThread().getName()));
            observer.onComplete(DialogflowIntentDetector.this);
        }

    }

}
