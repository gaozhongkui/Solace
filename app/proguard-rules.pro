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
-keepattributes SourceFile,LineNumberTable

#---------基础库忽略----------
-dontwarn okhttp3.internal.platform.**
-dontwarn com.lbe.uniads.**


-dontwarn javax.annotation.**
-dontwarn javax.inject.**
# OkHttp3
-dontwarn okhttp3.logging.**
-keep class okhttp3.internal.**{*;}
-dontwarn okio.**

#需要keep的数据类
-keep class * implements java.io.Serializable {*;}
-keep class org.apache.** { *; }


-keep class org.json.** { *; }
-keep class org.xmlpull.** { *; }
-keep class okhttp3.** { *; }

-keep class com.da.**{*;}
-keep interface com.da.**{*;}

-keepattributes Signature

-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type

-keep class com.avl.engine.** { *; }

-assumenosideeffects class android.util.Log{
public static *** v(...);
public static *** i(...);
public static *** d(...);
public static *** w(...);
public static *** e(...);
}

##---------------Begin: proguard configuration for Gson  ----------
# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**
#-keep class com.google.gson.stream.** { *; }

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

##---------------End: proguard configuration for Gson  ----------

-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

-keep @kotlinx.android.parcel.Parcelize public class *

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions
-keep class * extends com.google.protobuf.nano.MessageNano { *; }
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation


-keep @androidx.annotation.Keep class **{
@androidx.annotation.Keep <fields>;
@androidx.annotation.Keep <methods>;
}

-keep class androidx.compose.ui.platform.AndroidComposeView { *; }


-keep class androidx.compose.ui.platform.AndroidComposeView { *; }

# Keep Firebase/FCM related tokens
-keepattributes Signature, *Annotation*
-keep class com.google.firebase.** { *; }


# 保持枚举，因为 Proto 经常通过反射查找枚举值
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保护所有 Retrofit 接口类
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# 保护 network 包下所有类（包括接口、RetrofitClient 等）
-keep class com.glowvid.hypecut.network.** { *; }
-keep interface com.glowvid.hypecut.network.** { *; }

# 保护 HttpCore 库的核心类（lbe.otter 提供，Retrofit/Proto 运行时依赖）
-keep class com.glowvid.httpcore.** { *; }
-keep interface com.glowvid.httpcore.** { *; }

# Retrofit 2.x 规则
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# OkHttp 3.x 规则
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**

# 明确保护 parseFrom 反射调用（MatrixCallAdapterFactory 通过反射调用 parseFrom）
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    public static ** parseFrom(byte[]);
    public static ** parseFrom(java.io.InputStream);
    public static ** newInstance(...);
    public static ** getDefaultInstance();
}


-keep class io.nekohasekai.libbox.** { *; }