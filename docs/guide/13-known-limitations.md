# 13. Known limitations

These are the limits a wallet app runs into today. Most come from the Zephyr core (zephyr-cpp and
`wallet2`), which zephyr-java builds unchanged: the backports change the core only for security
fixes, so functional gaps stay until the core itself changes. Each entry says what happens, its
status, and where the finding comes from.

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
