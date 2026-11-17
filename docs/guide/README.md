# zephyr-java user guide

This guide explains how to use zephyr-java in an application: on a desktop or server JVM with the
JAR, and in an Android app with the AAR. It assumes you know Java; it does not assume you know
monero-java. The API reference is the Javadoc; the [README](../../README.md) has the coordinates
and a complete quick-start program.

**Not affiliated with ZephyrProtocol.** An independent project.

| Chapter | What it covers |
|---|---|
| [1. Getting started](01-getting-started.md) | the JAR and the AAR, loading the native library, the glibc floor, a first program |
| [2. Wallets](02-wallets.md) | network types, create, restore, open from files or bytes, the password, save and close |
| [3. Connecting to a node](03-connecting-to-a-node.md) | setting the node, http and https, TLS to a node and `ZephyrTls`, when the node cannot be reached, syncing |
| [4. Threads and listeners](04-threads-and-listeners.md) | blocking calls, callbacks on a native thread, keeping callbacks short, the five callbacks, outputs in the pool |
| [5. Assets, accounts and balances](05-assets-and-balances.md) | ZPH, ZSD, ZRS, ZYS and the V1 names, amounts, `ZephyrBalance`, the four balance shapes, locked and unlocked |
| [6. History](06-history.md) | conversions in the history, the amount of a checked payment, the asset of an output |
| [7. Sending](07-sending.md) | the currency pair, create then relay, fees, sweeps |
| [8. Conversions and the reserve](08-conversions-and-the-reserve.md) | the ten pairs and the reserve-ratio rules, every field of `ZephyrReserveInfo`, the six supply keys, building a conversion, the mint formula, converting everything, what was tested |
| [9. Errors and exceptions](09-errors.md) | the native log |
| [10. Android](10-android.md) | the dependency and the packaging line, devices and ABIs, the RPC classes below API 26, the network, storage, backup, lifecycle, logs, a first test on a device, R8 rules |
| [11. The RPC classes](11-the-rpc-classes.md) | `ZephyrDaemonRpc` and the pricing record, `ZephyrWalletRpc` as a first pass, dependencies |
| [12. Coming from monero-java](12-coming-from-monero-java.md) | the renaming rules, what is different, both libraries in one program |
| [13. Known limitations](13-known-limitations.md) | what was tested |
| [14. Building and verifying](14-building-and-verifying.md) | the pins, the patches, the builds, reproducibility, the manifest, signatures, the guide's site |

Statements about the Zephyr core in this guide come from the pinned sources and from test runs
against mainnet in 2026-11.
