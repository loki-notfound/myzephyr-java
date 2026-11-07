# Consumer R8/ProGuard rules of zephyr-java, applied to every app that uses the AAR.

# The native library binds its JNI exports by class and method name (Java_zephyr_..._<method>Jni),
# so every class with native methods keeps its name and the names of those methods.
-keepclasseswithmembernames class * {
    native <methods>;
}

# The bridge looks this listener up by name (FindClass "zephyr/wallet/ZephyrWalletFull$WalletJniListener")
# and calls onSyncProgress, onNewBlock, onBalancesChanged, onOutputReceived and onOutputSpent by
# name and signature (GetMethodID). The bridge's other lookups are java.* classes only; its
# com/m2049r lookups are commented out.
-keep class zephyr.wallet.ZephyrWalletFull$WalletJniListener { *; }

# Jackson maps the native JSON to these classes by reflection: constructors, setters, getters
# and field names must survive.
-keep class zephyr.wallet.model.** { *; }
-keep class zephyr.daemon.model.** { *; }
-keep class zephyr.common.** { *; }

# jeromq is compile-only in the AAR: ZephyrWalletRpc's ZMQ listener needs it, and an app that uses
# that listener adds jeromq itself; without this an app's R8 reports the absent classes as errors.
-dontwarn org.zeromq.**

# Jackson reads its annotations (@JsonProperty, @JsonIgnore, ...) and the generic signatures of
# fields and methods (List<ZephyrOutput>, Map<String, BigInteger>) at run time.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
