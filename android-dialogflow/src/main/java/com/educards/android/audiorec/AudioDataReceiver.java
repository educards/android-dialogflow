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
