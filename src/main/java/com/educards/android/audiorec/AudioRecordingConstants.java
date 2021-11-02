package com.educards.android.audiorec;

public interface AudioRecordingConstants {

    int SAMPLE_RATE = 16000;

    // Buffer size in bytes: for 0.1 second of audio
    int BUFFER_SIZE = (int)(SAMPLE_RATE * 0.1 * 2);

}
