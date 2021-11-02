package com.educards.android.dialogflow;

import com.educards.android.audiorec.AudioRecordingThread;

/**
 * Optional initializer of {@link AudioRecordingThread}.
 */
public interface AudioRecordingThreadInitializer {

    /**
     * Called prior to {@link AudioRecordingThread#startRecording()} to initialize {@link AudioRecordingThread}.
     */
    void onAudioRecordingThreadInit(AudioRecordingThread audioRecordingThread);

}
