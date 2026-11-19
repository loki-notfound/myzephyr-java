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

# Jackson reads the type argument of a type token from the generic superclass of the anonymous
# subclass the library creates (new TypeReference<Map<String, Object>>() {} in ZephyrWalletFull,
# ZephyrUtils, ZephyrDaemonRpc and zephyr.lib.utils.JsonUtils). R8's full mode keeps the Signature
# attribute above only on classes that a rule keeps, and nothing kept these subclasses: every
# parse through them failed with "TypeReference constructed without actual type information",
# so a minified app could not create or open a wallet (seen with AGP 9.5). These are the rules R8
# documents for type tokens; the classes may still be renamed or removed when unused.
-keep,allowobfuscation,allowshrinking class com.fasterxml.jackson.core.type.TypeReference
-keep,allowobfuscation,allowshrinking class * extends com.fasterxml.jackson.core.type.TypeReference

# ZephyrWalletFull maps the native JSON of accounts, subaddresses, address book entries, key
# images, transaction sets and blocks onto private nested classes (AccountsContainer and the other
# *Container classes, ZephyrBlockWallet) by reflection, and writes key images to the native side
# through one of them: their constructors, public fields and annotated setter must survive under
# their names. Without this rule R8 renamed or removed them.
-keep class zephyr.wallet.ZephyrWalletFull$*Container { *; }
-keep class zephyr.wallet.ZephyrWalletFull$ZephyrBlockWallet { *; }

# java.util.logging names the logger of ZephyrDaemonRpc after its class, so a logging configuration
# for zephyr.daemon.ZephyrDaemonRpc applies only while the class keeps its name. (ZephyrWalletFull
# keeps its name through the rule for native methods.)
-keepnames class zephyr.daemon.ZephyrDaemonRpc

# The Apache HTTP client (httpclient5, used by the RPC classes) and slf4j-api refer to classes that
# neither the AAR nor Android provides: Brotli decompression (an optional dependency of
# httpclient5), Kerberos/SPNEGO authentication (org.ietf.jgss, part of the desktop JDK only) and
# slf4j 1.7's static binding (supplied by a logging backend when there is one; without it slf4j
# falls back to its no-operation logger). zephyr-java uses none of them. Without these rules a
# minified app build stops in R8 with "Missing class" errors (seen with AGP 9.5, R8 full mode).
-dontwarn org.brotli.dec.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
