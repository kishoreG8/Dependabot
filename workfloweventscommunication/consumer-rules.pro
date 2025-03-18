-keep class com.trimble.ttm.workfloweventscommunication.model.** { *; }
-keep class com.trimble.ttm.workfloweventscommunication.workflowEventListener.** { *; }
-keep class com.trimble.ttm.workfloweventscommunication.manager.** { *; }

# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
