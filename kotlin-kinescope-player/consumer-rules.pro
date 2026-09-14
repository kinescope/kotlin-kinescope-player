# Keep protobuf lite runtime used by analytics (R8 otherwise strips GeneratedMessageLite).
-keep class com.google.protobuf.** { *; }
-keep class com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-keep class io.kinescope.sdk.analytics.proto.** { *; }
