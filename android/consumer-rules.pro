# Consumer R8/ProGuard rules of zephyr-java, applied to every app that uses the AAR.

# The native library binds its JNI exports by class and method name (Java_zephyr_..._<method>Jni),
# so every class with native methods keeps its name and the names of those methods.
-keepclasseswithmembernames class * {
    native <methods>;
}

# The bridge looks this listener up by name (FindClass "zephyr/wallet/ZephyrWalletFull$WalletJniListener")
# and calls onSyncProgress, onNewBlock, onBalancesChanged, onOutputReceived and onOutputSpent by
# name and signature (GetMethodID). It also looks up zephyr.common.ZephyrError by name (FindClass
# in JNI_OnLoad) and throws it with its String constructor; the zephyr.common rule below keeps
# both. The bridge's other class lookups are java.* classes only; its com/m2049r lookups are
# commented out. Its field lookups follow below.
-keep class zephyr.wallet.ZephyrWalletFull$WalletJniListener { *; }

# The bridge also reads and writes two private fields of the wallet by name (GetFieldID
# "jniWalletHandle" and "jniListenerHandle", type long): the native addresses of the wallet and of
# its listener. Java reads neither (the listener's is written by the bridge alone), so without this
# rule R8 could rename or remove them, and the first native call would fail with NoSuchFieldError.
# A -keepclassmembers rule without allow modifiers keeps both, under these names, whenever the
# class is kept.
-keepclassmembers class zephyr.wallet.ZephyrWalletFull {
    private long jniWalletHandle;
    private long jniListenerHandle;
}

# Jackson maps the native JSON to these classes by reflection: constructors, setters, getters
# and field names must survive.
-keep class zephyr.wallet.model.** { *; }
-keep class zephyr.daemon.model.** { *; }
-keep class zephyr.common.** { *; }

# Jackson reads its annotations (@JsonProperty, @JsonIgnore, ...) and the generic signatures of
# fields and methods (List<ZephyrOutput>, Map<String, BigInteger>) at run time.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
