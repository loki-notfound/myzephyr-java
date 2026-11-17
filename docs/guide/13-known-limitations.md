# 13. Known limitations

These are the limits a wallet app runs into today. Most come from the Zephyr core (zephyr-cpp and
`wallet2`), which zephyr-java builds unchanged: the backports change the core only for security
fixes, so functional gaps stay until the core itself changes. Each entry says what happens, its
status, and where the finding comes from.

## In the core

### Other limitations in the core

**The asset of an unconfirmed output notification is empty.** `onOutputReceived` for a payment
seen in the pool arrives with `getAssetType()` equal to `""`: `wallet2`'s callback for pool
payments has no asset parameter, and zephyr-cpp passes an empty string. The notification at
confirmation goes through a callback that carries the asset. Use `onBalancesChanged` (which names
the asset) or `getTxs()` (which reports the right asset for the same pool transfer). The official
Zephyr wallets do the same and leave `onOutputReceived` unused. *Status:* core behaviour, not a
security matter, so it stays; documented in the Javadoc of `onOutputReceived`.
Observed on mainnet on 2026-11-07.

**Testnet and stagenet do not reach the V2 rules.** In the pinned Zephyr tree the hard-fork tables
of testnet and stagenet stop at hard fork 3 (height 100), and the V2 asset names are not allowed
before hard fork 8. A wallet on those networks never sees ZPH, ZSD, ZRS or ZYS. Development and
tests that need the current rules run on mainnet with small amounts. *Status:* the state of the
core's network tables.

## In the build and the packaging

**TLS to a node: no pinning, the host name must match.** The native wallet trusts root
certificates (on Android by default those shipped in the library; `ZephyrTls` adds the system
store, user certificates, own certificates or one exact file), but it cannot pin a certificate, a
certificate whose host name does not match the URI is refused (the core checks the name and
passes no SSL options through), and the app's Network Security Configuration does not apply
([chapter 3](03-connecting-to-a-node.md#tls-to-a-node)). In `java.1` to `java.3` the documented
`SSL_CERT_FILE`/`SSL_CERT_DIR` setting had no effect on the Android images it was measured on
(the emulator, API 24 and API 35: OpenSSL ignored the environment in an app process; other devices
were not measured), so `https://` nodes could not be verified there; fixed in `java.4`. On the JVM
the variables worked and still work when `ZephyrTls` is not used. *Status:* pinning and own SSL
options need the core.

**Android 7: the RPC classes need core library desugaring.** `ZephyrDaemonRpc` and
`ZephyrWalletRpc` send their requests through the Apache HTTP client, which needs `java.time`, and
Android has it from API 26. On API 24 and 25 every request throws a `ZephyrError` that says
so, unless the app enables core library desugaring
([chapter 10](10-android.md#the-rpc-classes-below-api-26)). `ZephyrWalletFull` is not affected.
*Status:* lasts while minSdk is 24.

**Android libraries contain build paths.** The Android natives are built with NDK r18b, whose
clang 7 cannot rewrite the paths in `__FILE__`. About 700 strings per library (source file names
in error messages, and the location of lambdas in function names) therefore carry the build
directory. They leak nothing beyond that directory name, but they make the Android natives
reproducible only when built at the same path; release builds use a fixed path, as F-Droid does.
The Linux natives carry no build path. *Status:* lasts while the core pins NDK r18b.

**Linux: glibc 2.36 or newer.** The JAR's Linux natives need Debian 12, Ubuntu 23.04, RHEL 10 or
newer. *Status:* by design (the release natives are built in a Debian 12 container).

**macOS and Windows: no packaged natives.** The JAR carries Linux libraries only; on other systems
the library must be built and put on `java.library.path`. *Status:* later.

**`armeabi-v7a` needs VFPv4.** Devices with Cortex-A8 or A9 cores cannot run the 32-bit ARM library.
*Status:* inherited from Zephyr's own build flags.

## What was tested

**Where it has run.** The `x86_64` library ran on the Android emulator as a release check, on
API 24 and on API 35 with 16 KB pages, in debug builds (so under ART's CheckJNI): the library
loads and validates addresses natively, an offline wallet works, a wallet restored from its
mnemonic syncs against a mainnet node and opens again from its bytes, and `ZephyrDaemonRpc` reads
the node (on API 24 with desugaring). `https://` to a mainnet node with a publicly signed
certificate works there without setup (the shipped root certificates), on API 24 and on API 35
with 16 KB pages; with the system store alone it works on API 35 and fails on API 24, which lacks
ISRG Root X1. The `arm64-v8a` and `armeabi-v7a` libraries are built and
checked the same way (exports, page alignment, packaging) but have **not yet run on hardware**.

**Android: the ARM libraries have not yet run on hardware.** The `x86_64` library and the wallet
run on the Android emulator, on API 24 and on API 35 with 16 KB pages, as a release check
([chapter 10](10-android.md#devices)). The `arm64-v8a` and `armeabi-v7a` libraries are built and
checked (exports, page alignment, packaging) but have not run on a device. *Status:* pending a
device test.
