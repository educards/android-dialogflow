/*
 * Copyright © 2022 Educards Learning, SL.
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

import com.google.cloud.dialogflow.v2.StreamingDetectIntentResponse;

/**
 * Optional wrapper of the Dialogflow response.
 *
 * <ul>
 *     <li>For convenience, the detected intent name is parsed and stored in {@link #getIntentName()}.</li>
 *     <li>For any other data query {@link #getOrigResponse()}.</li>
 * </ul>
 *
 * @see #parse(StreamingDetectIntentResponse)
 */
public class DialogflowIntentResponse {

    /**
     * Intent returned if Dialogflow couldn't infer any intent from the utterance
     * sent to server. This most commonly happens if there simply doesn't exist any intent definition
     * for the uttered command or if the audio quality is too bad.
     */
    private static final String UNKNOWN_INTENT = "unknown";

    /**
     * Helper method to convert detected intent object to {@link DialogflowIntentResponse}.
     */
    public static DialogflowIntentResponse parse(StreamingDetectIntentResponse detectedIntent) {
        if (detectedIntent == null) {
            return new DialogflowIntentResponse(UNKNOWN_INTENT, null);
        } else {
            String intentName = detectedIntent.getQueryResult().getIntent().getDisplayName();
            return new DialogflowIntentResponse(
                    (intentName == null || intentName.isEmpty()) ? UNKNOWN_INTENT : intentName,
                    detectedIntent);
        }
    }

    private String intentName;

    private StreamingDetectIntentResponse origResponse;

    public DialogflowIntentResponse(String intentName, StreamingDetectIntentResponse origResponse) {
        this.intentName = intentName;
        this.origResponse = origResponse;
    }

    /**
     * Intent name parsed from {@link #getOrigResponse()}
     * or {@link #UNKNOWN_INTENT} if the response was `null`.
     */
    public String getIntentName() {
        return intentName;
    }

    /**
     * Original Dialogflow response.
     */
    public StreamingDetectIntentResponse getOrigResponse() {
        return origResponse;
    }

}
