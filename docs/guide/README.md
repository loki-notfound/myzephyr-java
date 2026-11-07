# zephyr-java user guide

This guide explains how to use zephyr-java in an application: on a desktop or server JVM with the
JAR, and in an Android app with the AAR. It assumes you know Java; it does not assume you know
monero-java. The API reference is the Javadoc; the [README](../../README.md) has the coordinates
and a complete quick-start program.

**Not affiliated with ZephyrProtocol.** An independent project.

| Chapter | What it covers |
|---|---|
| [1. Getting started](01-getting-started.md) | the JAR and the AAR, loading the native library, threads, the glibc floor, amounts |
| [2. Wallets](02-wallets.md) | create, restore, open from files or bytes, the node connection, sync, the listener, save and close |
| [3. Assets and balances](03-assets-and-balances.md) | ZPH, ZSD, ZRS, ZYS and the V1 names, `ZephyrBalance`, the four balance shapes, locked and unlocked |
| [4. Sending and converting](04-sending-and-converting.md) | the currency pair, valid pairs and the reserve-ratio rules, fees, conversions and the mint formula, sweeps, history |
| [5. Reserve info and circulating supply](05-reserve-and-supply.md) | every field of `ZephyrReserveInfo`, the six supply keys, pricing records |
| [6. Android](06-android.md) | the dependency and the packaging line, devices and ABIs, R8 rules, storage, lifecycle, TLS to a node, the test app |
| [7. Coming from monero-java](07-coming-from-monero-java.md) | the renaming rules, what is different, both libraries in one program |
| [8. Known limitations](08-known-limitations.md) | the core findings and the open points, each with its status |
| [9. Building from source and verifying releases](09-building-and-verifying.md) | the pins, the patches, the builds, reproducibility, the manifest, signatures |

Statements about the Zephyr core in this guide come from the pinned sources and from test runs
against mainnet in 2026-11; each one names its source in an HTML comment next to it, visible in
the Markdown source of the page.
