# Android Dialogflow Client Library

In 2021 Dialogflow switched its API to v2.
While for API v1 there was an [official Android library](https://github.com/dialogflow/dialogflow-android-client), for API v2 there isn't.
The only officialy supported client libs for Dialogflow v2 API are for mayor programming languages (Java, Go, C#, etc.), but not for Android platform.

* [Dialogflow API v1 shutdown announcement](https://cloud.google.com/dialogflow/docs/release-notes#November_14_2019)
* [Dialogflow API v2 overview](https://cloud.google.com/dialogflow/es/docs/reference/rest/v2-overview)
* [Dialogflow API v2 Java client library](https://cloud.google.com/dialogflow/es/docs/reference/libraries/java) \
  *(Java client library does not support Android)*

While there is a good reason not to access Dialogflow directly from Android app in production (security, authentication and access controls)
this library is nevertheless **suitable for testing and prototyping**.

# Integration

**build.gradle**
```gradle
dependencies {
    implementation "com.educards:android-dialogflow:0.0.1"
}
```

**Activity/Fragment code**
```java
private final int DIALOGFLOW_CREDENTIALS = R.raw.your_dialogflow_key_261310_72cc3d2abb42;

/**
 * Language code of streamed audio.
 * @see https://cloud.google.com/dialogflow/es/docs/reference/language
 */
private final String DIALOGFLOW_LNG_CODE = "en";
private DialogflowIntentDetector dialogflowIntentDetector;

private void start

private void initDialogflowV2(
        String dialogflowSessionUuid,
        int dialogflowCredentials,
        String dialogflowLngCode) {

    return new DialogflowIntentDetector(getActivity(), dialogflowSessionUuid, dialogflowCredentials, dialogflowLngCode, new DialogflowIntentObserver() {
    
        private StreamingDetectIntentResponse detectedIntent;

        @Override
        public void onStart(DialogflowIntentDetector detector, StreamController controller) {
            detectedIntent = null;
        }

        @Override
        public void onResponseIntent(DialogflowIntentDetector detector, StreamingDetectIntentResponse response) {
            detectedIntent = response;
        }
        
        @Override
        public void onComplete(DialogflowIntentDetector detector) {
            activity.runOnUiThread(() -> onDialogflowIntent(toString(detectedIntent)));
        }

        @Override
        public void onResponseEndOfUtterance(DialogflowIntentDetector detector, StreamingDetectIntentResponse response) {
            // Called at the end of utterance
            // (no more utteranced detected in the streaming audio)
        }

        @Override
        public void onResponse(DialogflowIntentDetector detector, StreamingDetectIntentResponse response) {
            // Called to deliver intermediate results
        }

        @Override
        public void onError(DialogflowIntentDetector detector, Throwable t) {
            // Called on error
        }

    });
}

```
