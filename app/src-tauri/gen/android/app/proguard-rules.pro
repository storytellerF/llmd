# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Rust JNI entry points and callbacks use these class, field, and method names directly.
-keep class com.storytellerf.llmd.LlmdNativeServer {
    public static final com.storytellerf.llmd.LlmdNativeServer INSTANCE;
    public native <methods>;
}

-keep class com.storytellerf.llmd.LlmdAndroidBridge {
    public static final com.storytellerf.llmd.LlmdAndroidBridge INSTANCE;
    public java.lang.String listModelsJson();
    public void chatCompletionAsync(long, java.lang.String);
}

# Android WebView JavaScript bridge methods are invoked by name from the bundled UI.
-keepclassmembers class com.storytellerf.llmd.MainActivity$ModelImportBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# litertlm-android's native library resolves its Kotlin API classes and getters by JNI name.
-keep class com.google.ai.edge.litertlm.** {
    *;
}
