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
public interface IntentObserver {

    /**
     * Delegated callback of {@link com.google.api.gax.rpc.ResponseObserver#onStart}.
     */
    void onStart(StreamingIntentDetector detector, StreamController controller);

        /**
     * Called if the server has detected an intent.
     * <p>To process all intermediate results while streaming the audio use {@link #onResponse} instead.</p>
     */
    void onResponseIntent(StreamingIntentDetector detector, StreamingDetectIntentResponse response);

    /**
     * Called if the server has detected the end of the user's speech
     * utterance and expects no additional inputs.
     */
    void onResponseEndOfUtterance(StreamingIntentDetector detector, StreamingDetectIntentResponse response);

    /**
     * Delegated callback of {@link com.google.api.gax.rpc.ResponseObserver#onResponse}.
     */
    void onResponse(StreamingIntentDetector detector, StreamingDetectIntentResponse response);

    /**
     * Delegated callback of {@link com.google.api.gax.rpc.ResponseObserver#onError}.
     */
    void onError(StreamingIntentDetector detector, Throwable t);

    /**
     * Delegated callback of {@link com.google.api.gax.rpc.ResponseObserver#onComplete}.
     */
    void onComplete(StreamingIntentDetector detector);

}
