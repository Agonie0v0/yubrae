# KairoLedger ProGuard rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.kairoledger.app.KairoApplication { *; }
-keep class com.kairoledger.app.MainActivity { *; }
-keep class com.kairoledger.app.ui.viewmodel.KairoMainViewModel { *; }
-keep class com.kairoledger.app.ui.viewmodel.KairoViewModelFactory { *; }
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class com.kairoledger.app.data.** { *; }
-keep class com.kairoledger.app.core.** { *; }
-keep class com.kairoledger.app.sync.** { *; }
-keep class com.kairoledger.app.capture.** { *; }
-keep class com.kairoledger.app.importer.** { *; }
-keep class com.kairoledger.app.widget.** { *; }
-keep class androidx.glance.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
