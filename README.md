# Android Dialogflow Client Library

[![GitHub release](https://img.shields.io/github/v/release/educards/android-dialogflow?include_prereleases&style=flat-square)](https://github.com/educards/android-dialogflow/releases)
[![GitHub license](https://img.shields.io/github/license/educards/android-dialogflow?style=flat-square)](https://github.com/educards/android-dialogflow/blob/main/LICENSE)

In 2021 Dialogflow switched its API to v2.
While for API v1 there was an [official Android library](https://github.com/dialogflow/dialogflow-android-client), for API v2 there isn't.
The only officialy supported client libs for Dialogflow v2 API are for mayor programming languages (Java, Go, C#, etc.), but not for Android platform.

* [Dialogflow API v1 shutdown announcement](https://cloud.google.com/dialogflow/docs/release-notes#June_29_2020)
* [Dialogflow API v2 overview](https://cloud.google.com/dialogflow/es/docs/reference/rest/v2-overview)
* [Dialogflow API v2 Java client library](https://cloud.google.com/dialogflow/es/docs/reference/libraries/java) \
  *(Java client library does not support Android)*

While there is a good reason not to access Dialogflow directly from Android app in production (security, authentication and access controls)
this library is nevertheless **suitable for testing and prototyping**.

# API
* **`DialogflowIntentDetector`**: Entry point of intent detection (`startIntentDetection()`).
* **`DialogflowIntentObserver`**: Observes the state of intent detection (`onResponseIntent`, `onComplete`, `onError`).
* **`AudioRecordingThread`**: Working thread which records the audio by utilizing Android's [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord) (`isRecording()`, `requestStop()`, `isStopRequested()`).
* **`AudioDataReceiver`**: Listener of recorded audio data. May be used for live waveform/audio level rendering or any other audio data processing.

# Integration

### Dependencies
**``build.gradle``**
```gradle
dependencies {
    implementation "com.educards:android-dialogflow:0.1.0"
}
```

### Permissions
**``AndroidManifest.xml``**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### Activity/Fragment code

#### Initialization

As already mentioned, there are good reasons not to access Dialogfrom directly from Android (security etc.).
And in this block you can see, that we need to keep Dialogflow credentials directly on the device.
Keep in mind, that this lib is intended for testing and prototyping.

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

        // Custom observer which handles intent detection callbacks
        // (onComplete, onResponse, onError, ...).
        new MyIntentObserver()
    );
}
```

#### Starting intent detection

The most important method here is `startIntentDetection()`. Rest of the code is initialization and permissions checking.

```java
private DialogflowIntentDetector dialogflowIntentDetector;

/**
 * Starts Dialogflow intent detection.
 * The detection will be stopped implicitly by Dialogflow itself when
 * the utterence is finished or explicitly when we stop sending audio data.
 * @see AudioRecordingThread#requestStop()
 */
private void startDialogflowIntentDetection() {

    // Check for audio recording permissions.
    // The ActivityCompat.checkSelfPermission() is just an illustration here.
    // Any other method to request app permission may be used here.
    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
    
        // Intent detector may be reused multiple times therefore
        // it's sufficient to initialize it only once.
        if (dialogflowIntentDetector == null) {
            dialogflowIntentDetector = initDialogflowV2();
        }
        
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

#### Handling 'Voice-to-Intent' callbacks

This is where we handle all states of Dialogflow's 'Voice-to-Intent' procedure.
Once the intent is detected (or not) the `handleDialogflowIntent(intent)` callback is called.

```java
class MyIntentObserver implements DialogflowIntentObserver {

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

#### Cleanup
```java
@Override
protected void onDestroy() {
    if (dialogflowIntentDetector != null) {
        dialogflowIntentDetector.close();
    }
    super.onDestroy();
}

```

## Acknowledgment
Special thanks to Svitlana Dzyuban who kindly donated us Educards GitHub account and thus allowing us to release this library.

## License
```
Copyright 2021 Educards Learning, SL

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
