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

package com.educards.android.dialogflow

import com.educards.android.dialogflow.DialogflowIntentResponse.Companion.UNKNOWN_INTENT
import com.google.cloud.dialogflow.v2.StreamingDetectIntentResponse

/**
 * Optional wrapper of the Dialogflow response.
 *
 * * For convenience, the detected intent name is parsed and stored in [intentName].
 * * For any other data query [origResponse].
 *
 * @see [parse]
 */
data class DialogflowIntentResponse(

    /**
     * Intent name parsed from [origResponse]
     * or [UNKNOWN_INTENT] if the response was `null`.
     */
    val intentName: String,

    /**
     * Original Dialogflow response.
     */
    val origResponse: StreamingDetectIntentResponse?

) {

    companion object {

        /**
         * Intent returned if Dialogflow couldn't infer any intent from the utterance
         * sent to server. This most commonly happens if there simply doesn't exist any intent definition
         * for the uttered command or if the audio quality is too bad.
         */
        const val UNKNOWN_INTENT = "unknown"

        /**
         * Helper method to convert detected intent object to [DialogflowIntentResponse].
         */
        fun parse(detectedIntent: StreamingDetectIntentResponse?): DialogflowIntentResponse {
            return if (detectedIntent == null) {
                DialogflowIntentResponse(UNKNOWN_INTENT, null)
            } else {
                val intentName = detectedIntent.queryResult.intent.displayName
                DialogflowIntentResponse(
                    if (intentName == null || intentName.isEmpty()) UNKNOWN_INTENT else intentName,
                    detectedIntent)
            }
        }
    }

}
