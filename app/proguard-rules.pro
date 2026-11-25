# ONNX Runtime's JNI code looks its Java classes up by name.
-keep class ai.onnxruntime.** { *; }
# zip4j and libphonenumber are used as-is; their warnings about optional classes are harmless.
-dontwarn net.lingala.zip4j.**
-dontwarn com.google.i18n.phonenumbers.**
