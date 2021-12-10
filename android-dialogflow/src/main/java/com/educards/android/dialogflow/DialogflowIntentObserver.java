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

import com.google.api.gax.rpc.StreamController;
import com.google.cloud.dialogflow.v2.StreamingDetectIntentResponse;

/**
 * Dialogflow observer of the intent detection procedure.
 *
 * <p>The order of callbacks is guaranteed to be:
 *
 * <ul>
 *   <li>exactly 1 {@link #onStart}</li>
 *   <li>0 or more {@link #onResponse}, {@link #onResponseIntent}, {@link #onResponseEndOfUtterance}
 *   <li>exactly 1 {@link #onError} or {@link #onComplete}
 * </ul>
 * </p>
 */
public interface DialogflowIntentObserver {

    /**
     * Delegated callback of {@link com.google.api.gax.rpc.ResponseObserver#onStart}.
     */
    void onStart(DialogflowIntentDetector detector, StreamController controller);

        /**
     * Called if the server has detected an intent.
     * <p>To process all intermediate results while streaming the audio use {@link #onResponse} instead.</p>
     */
    void onResponseIntent(DialogflowIntentDetector detector, StreamingDetectIntentResponse response);

    /**
     * Called if the server has detected the end of the user's speech
     * utterance and expects no additional inputs.
     */
    void onResponseEndOfUtterance(DialogflowIntentDetector detector, StreamingDetectIntentResponse response);

    /**
     * Delegated callback of {@link com.google.api.gax.rpc.ResponseObserver#onResponse}.
     */
    void onResponse(DialogflowIntentDetector detector, StreamingDetectIntentResponse response);

    /**
     * Delegated callback of {@link com.google.api.gax.rpc.ResponseObserver#onError}.
     */
    void onError(DialogflowIntentDetector detector, Throwable t);

    /**
     * Delegated callback of {@link com.google.api.gax.rpc.ResponseObserver#onComplete}.
     */
    void onComplete(DialogflowIntentDetector detector);

}
