# General
-dontobfuscate


#Config for wulkanowy
-keep class io.github.wulkanowy.** {*;}


#Config for firebase crashlitycs
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception


#Config for Okio and OkHttp
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn okhttp3.internal.platform.ConscryptPlatform


#Config for MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }


#Config for Material Components
-keep class com.google.android.material.tabs.** { *; }
