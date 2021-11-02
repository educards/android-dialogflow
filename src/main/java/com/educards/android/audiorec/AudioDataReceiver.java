package com.educards.android.audiorec;

/**
 * Receiver (consumer) of audio data produced by {@link AudioRecordingThread}.
 */
public interface AudioDataReceiver {

    void onAudioRecordingStarted();

    /**
     * <ul>
     * <li>Called to process audio data chunk by the receiver.</li>
     * <li>This callback is called synchronously from {@link AudioRecordingThread}, therefore
     *     it shouldn't be blocking to ensure realtime audio processing.</li>
     * <li>For audio data definition see {@link AudioRecordingConstants}.</li>
     * </ul>
     */
    void onAudioDataReceived(byte[] data, int length);

    void onAudioRecordingStopped();

}
