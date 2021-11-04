# Android Dialogflow Client Library

In 2021 Dialogflow switched its API to v2.
While for API v1 there was an [official Android library](https://github.com/dialogflow/dialogflow-android-client), for API v2 there isn't.
The only officialy supported client libs for Dialogflow v2 API are for mayor programming languages (Java, Go, C#, etc.), but not for Android platform.

* [Dialogflow API v1 shutdown announcement](https://cloud.google.com/dialogflow/docs/release-notes#November_14_2019)
* [Dialogflow API v2 overview](https://cloud.google.com/dialogflow/es/docs/reference/rest/v2-overview)
* [Dialogflow API v2 Java client library](https://cloud.google.com/dialogflow/es/docs/reference/libraries/java) \
  *(Java client library does not support Android)*

While there is a good reason not to access Dialogflow directly from Android in production (security, authentication and access controls)
this library is **suitable for testing and prototyping**.
