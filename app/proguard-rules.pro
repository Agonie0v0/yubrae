# KairoLedger ProGuard rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.kairoledger.app.data.db.** { *; }
-keep class com.kairoledger.app.core.model.** { *; }
-keep class com.kairoledger.app.sync.** { *; }
-keep class com.kairoledger.app.capture.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
