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
#Proguard-rules were used to protect the actual data from code shrink and obfuscation.

#Firebase Analytics
-keepattributes Signature

#Firebase Crashlytics
-keep class com.crashlytics.** { *; }
-dontwarn com.crashlytics.**

#Launcher library models
-keep class com.trimble.launchercommunicationlib.commons.model.** { *; }
-keep class com.trimble.ttm.routemanifest.model.** { *; }
-keep class com.trimble.ttm.formlibrary.model.** { *; }
-keep class com.trimble.ttm.commons.model.** { *; }

# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# after adding jackson xml parser this package throws error, so excluded it from warnings
-dontwarn aQute.bnd.annotation.spi.ServiceProvider

# With R8 full mode generic signatures are stripped for classes that are not
# kept. Suspend functions are wrapped in continuations where the type argument
# is used.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# R8 full mode strips generic signatures from return types if not kept.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# With R8 full mode generic signatures are stripped for classes that are not kept.
-keep,allowobfuscation,allowshrinking class retrofit2.Response