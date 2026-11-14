# Keep the sherpa-onnx JNI surface intact if minification is ever enabled.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
