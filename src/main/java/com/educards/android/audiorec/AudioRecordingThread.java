package com.educards.android.audiorec;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;

/**
 * Audio recording thread with the support to stream data to the subscribed {@link AudioDataReceiver receiver}.
 *
 * @see #startRecording()
 * @see #isRecording()
 * @see #requestStop()
 * @see #isStopRequested()
 */
public class AudioRecordingThread {

    private static final String TAG = "AudioRecordingThread";

    private List<AudioDataReceiver> receivers;

    private volatile boolean stopRequested;

    private Thread thread;

    public AudioRecordingThread() {
    }

    public boolean addAudioDataReceiver(AudioDataReceiver receiver) {
        if (receivers == null) {
            receivers = new LinkedList<>();
        }
        return receivers.add(receiver);
    }

    public boolean removeAudioDataReceiver(AudioDataReceiver receiver) {
        if (receivers != null) {
            return receivers.remove(receiver);
        } else {
            return false;
        }
    }

    /**
     * <ul>
     *     <li>Starts new audio recording thread.</li>
     *     <li>Continuously publishes audio data to the subscribed receiver until
     *     {@link #requestStop() recording stop} is requested.</li>
     * </ul>
     */
    public void startRecording() {

        if (thread != null) {
            Log.w(TAG, "Redundant request to start audio recording");
            return;
        }

        stopRequested = false;

        thread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            record();
        }, AudioRecordingThread.class.getSimpleName());

        thread.start();
    }

    public void requestStop() {
        Log.d(TAG, "Audio recording stop requested");

        if (thread == null)
            return;

        stopRequested = true;
        thread = null;
    }

    public boolean isStopRequested() {
        return stopRequested;
    }

    public boolean isRecording() {
        return thread != null && thread.isAlive();
    }

    private void record() {

        Log.d(TAG, "Initializing AudioRecorder");

        byte[] audioBuffer = new byte[AudioRecordingConstants.BUFFER_SIZE];
        AudioRecord audioRecord = initAudioRecord(audioBuffer.length);

        // Start
        audioRecord.startRecording();
        Log.v(TAG, "Recording started");
        if (receivers != null) {
            for (AudioDataReceiver receiver : receivers) receiver.onAudioRecordingStarted();
        }

        // Listen/record in loop
        long bytesRead = 0;
        while (!stopRequested) {

            int audioChunk = audioRecord.read(audioBuffer, 0, audioBuffer.length);

            switch (audioChunk) {
                case AudioRecord.ERROR_INVALID_OPERATION:
                case AudioRecord.ERROR_BAD_VALUE:
                case AudioRecord.ERROR_DEAD_OBJECT:
                case AudioRecord.ERROR:
                    Log.e(TAG, String.format("Audio recording failed [error=%d]", audioChunk));
                    // TODO Show this error also on UI somehow
                    break;
            }

            bytesRead += audioChunk;
            if (receivers != null) {
                for (AudioDataReceiver receiver : receivers) receiver.onAudioDataReceived(audioBuffer, audioBuffer.length);
            }
        }

        // Stop
        audioRecord.stop();
        Log.v(TAG, String.format("Recording stopped [bytesRead=%d]", bytesRead));
        if (receivers != null) {
            for (AudioDataReceiver receiver : receivers) receiver.onAudioRecordingStopped();
        }
        audioRecord.release();
    }

    private AudioRecord initAudioRecord(int bufferSize) {

        AudioRecord audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                AudioRecordingConstants.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new RuntimeException(String.format("AudioRecord initialization failed " +
                            "[AudioRecord.state=%d, AudioRecord.recordingState=%d]",
                    audioRecord.getState(), audioRecord.getRecordingState()));
        }

        return audioRecord;
    }
}
