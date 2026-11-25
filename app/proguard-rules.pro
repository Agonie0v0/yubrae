# Yubrae ProGuard rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.yubrae.app.KairoApplication { *; }
-keep class com.yubrae.app.MainActivity { *; }
-keep class com.yubrae.app.ui.viewmodel.KairoMainViewModel { *; }
-keep class com.yubrae.app.ui.viewmodel.KairoViewModelFactory { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class com.yubrae.app.data.** { *; }
-keep class com.yubrae.app.core.** { *; }
-keep class com.yubrae.app.sync.** { *; }
-keep class com.yubrae.app.capture.** { *; }
-keep class com.yubrae.app.update.** { *; }
-keep class com.yubrae.app.importer.** { *; }
-keep class com.yubrae.app.widget.** { *; }
-keep class androidx.glance.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn jxl.**
-dontwarn org.apache.log4j.**
-keep class jxl.** { *; }
