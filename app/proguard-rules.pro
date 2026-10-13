# 保留行号信息（便于崩溃分析）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 网络相关类的混淆规则

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepattributes Signature, InnerClasses
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keepattributes *Annotation*

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# CircleImageView
-dontwarn de.hdodenhof.circleimageview.**

# Shimmer
-dontwarn com.facebook.shimmer.**

# 保留 BuildConfig
-keep class com.petshop.BuildConfig { *; }

# 保留 API 接口类
-keep class com.petshop.api.ApiService { *; }
-keep class com.petshop.api.ApiService$** { *; }

# 保留模型类
-keep class com.petshop.model.** { *; }

# 保留枚举
-keepclassmembers enum * { *; }

# 保留 Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保留 ViewBinding
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(android.view.LayoutInflater);
}
