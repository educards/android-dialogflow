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
import com.google.protobuf.Value;

import java.util.Map;

/**
 * Wrapper of the {@link StreamingDetectIntentResponse} which provides
 * methods to access and handle response data in a more convenient way.
 *
 * <ul>
 *     <li>To wrap the {@link StreamingDetectIntentResponse response} use {@link #wrap(StreamingDetectIntentResponse)}.</li>
 *     <li>Name of the detected intent is parsed and stored in {@link #getIntentName()}.</li>
 *     <li>Original response is available by {@link #getOrigResponse()}.</li>
 *     <li>Named parameter values can be acquired by helper methods:</li>
 *     <ul>
 *         <li>{@link #getParameterField(String)}</li>
 *         <li>{@link #getParameterFieldString(String)}</li>
 *         <li>{@link #getParameterFieldDouble(String)}</li>
 *         <li>etc.</li>
 *     </ul>
 * </ul>
 *
 * @see #wrap(StreamingDetectIntentResponse)
 */
public class DialogflowIntentResponse {

    /**
     * Intent returned if Dialogflow couldn't infer any intent from the utterance
     * sent to server. This most commonly happens if there simply doesn't exist any intent definition
     * for the uttered command or if the audio quality is too bad.
     */
    private static final String UNKNOWN_INTENT = "unknown";

    /**
     * Wraps detected intent object to {@link DialogflowIntentResponse}.
     */
    public static DialogflowIntentResponse wrap(StreamingDetectIntentResponse detectedIntent) {
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

    private DialogflowIntentResponse(String intentName, StreamingDetectIntentResponse origResponse) {
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

    /**
     * @return Value of a <code>String</code> parameter field or <code>null</code>.
     */
    public String getParameterFieldString(String fieldName) {
        Value value = getParameterField(fieldName);
        return value == null ? null : value.getStringValue();
    }

    /**
     * @return Value of a <code>Double</code> parameter field or <code>null</code>.
     */
    public Double getParameterFieldDouble(String fieldName) {
        Value value = getParameterField(fieldName);
        return value == null ? null : value.getNumberValue();
    }

    /**
     * @return Value of a <code>Boolean</code> parameter field or <code>null</code>.
     */
    public Boolean getParameterFieldBool(String fieldName) {
        Value value = getParameterField(fieldName);
        return value == null ? null : value.getBoolValue();
    }

    /**
     * @return Dynamically typed value of a parameter wrapped by this response.
     */
    public Value getParameterField(String fieldName) {
        Map<String, Value> fieldsMap = origResponse.getQueryResult().getParameters().getFieldsMap();
        return fieldsMap.get(fieldName);
    }

    @Override
    public String toString() {
        return "DialogflowIntentResponse{" +
                "intentName='" + intentName + '\'' +
                '}';
    }

}
