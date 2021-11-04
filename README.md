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

### Dependencies (build.gradle)
```gradle
dependencies {
    implementation "com.educards:android-dialogflow:0.0.1"
}
```

### Permissions (AndroidManifest.xml)
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### Activity/Fragment code

**Initialization**
```java
/**
 * Initializes the DialogflowIntentDetector.
 */
private DialogflowIntentDetector initDialogflowV2() {
    return new DialogflowIntentDetector(context,

        // Session UUID used for logging and error reporting.
        UUID.randomUUID().toString(),

        // JSON file that contains your service account key.
        // The file is stored in res/raw/ resource folder.
        R.raw.your_dialogflow_key_261310_72cc3d2abb42,

        // Language code of the streamed audio.
        // https://cloud.google.com/dialogflow/es/docs/reference/language
        "en",

        // Observer which handles all the lifecycle callback
        // of intent detection (onComplete, onResponse, onError, ...).
        new IntentObserver()
    );
}
```

**Starting intent detection**
```java
private DialogflowIntentDetector dialogflowIntentDetector;

/**
 * Starts Dialogflow intent detection.
 * The detection will be stopped implicitly by Dialogflow itself when
 * the utterence is finished or explicitly when we stop sending audio data.
 * @see AudioRecordingThread#requestStop()
 */
private void startDialogflowIntentDetection() {
    
    // Intent detector may be reused multiple times therefore
    // it's sufficient to initialize it only once.
    if (dialogflowIntentDetector == null) {
        dialogflowIntentDetector = initDialogflowV2();
    }

    // Check for audio recording permissions.
    // The ActivityCompat.checkSelfPermission() is just an illustration here.
    // Any other method to request app permission may be used here.
    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
        
        // Start recording audio and stream it down to Dialogflow.
        dialogflowIntentDetector.startIntentDetection(
        
            // AudioRecordingThreadInitializer can be passed as an additional argument
            // if there is a need to further configure audio recording thread prior
            // to starting the detection. A typical usage might be to setup custom AudioDataReceiver
            // to render live amplitude/level of recorded audio (waveform).
            
            //recordingThread -> { recordingThread.addAudioDataReceiver(new MyAudioWaveform()); }
        );
        
    } else {
        // Permission must be granted prior to calling this method again.
        requestAudioPermission();
    }
}
```

**Handling intent**
```java
class IntentObserver implements DialogflowIntentObserver {

    private StreamingDetectIntentResponse detectedIntent;

    @Override
    public void onStart(
            DialogflowIntentDetector detector,
            StreamController controller) {
        detectedIntent = null;
    }

    @Override
    public void onResponseIntent(
            DialogflowIntentDetector detector,
            StreamingDetectIntentResponse response) {
        detectedIntent = response;
    }

    @Override
    public void onComplete(DialogflowIntentDetector detector) {
        activity.runOnUiThread(() -> handleDialogflowIntent(detectedIntent));
    }

    @Override
    public void onResponseEndOfUtterance(
            DialogflowIntentDetector detector,
            StreamingDetectIntentResponse response) {
        // Called at the end of utterance
        // (no more utteranced detected in the recorded audio)
    }

    @Override
    public void onResponse(
            DialogflowIntentDetector detector,
            StreamingDetectIntentResponse response) {
        // Called to deliver intermediate result
    }

    @Override
    public void onError(DialogflowIntentDetector detector, Throwable t) {
        // Called on error
    }
    
    /**
     * Called on UI thread after intent detection has been completed.
     */
    private void handleDialogflowIntent(StreamingDetectIntentResponse detectedIntent) {
    
        String intentString = DialogflowIntentDetector.getIntentString(detectedIntent);

        if (DialogflowIntentDetector.UNKNOWN_INTENT.equals(intentString)) {
            // TODO handle unknown intent
        } else if (intentString.equals("intent_do_this")) {
            // TODO handle intent
        } else if (intentString.equals("intent_do_that")) {
            // TODO handle intent
        } else ...
    }

});
```

**Cleanup**
```java
@Override
protected void onDestroy() {
    if (dialogflowIntentDetector != null) {
        dialogflowIntentDetector.close();
    }
    super.onDestroy();
}

```
