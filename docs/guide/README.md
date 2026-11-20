# zephyr-java user guide

This guide explains how to use zephyr-java in an application. It covers desktop and server JVMs
with the JAR, and Android apps with the AAR. It assumes you know Java; it does not assume you know
monero-java. The [README](../../README.md) has the coordinates and a complete quick-start program.
The API reference is the [Javadoc of the current release](https://java-docs.lateo.dev/javadoc/).

**Not affiliated with ZephyrProtocol.** An independent project.

This guide describes version `2.3.0-sec.2-java.5`.

| Chapter | What it covers |
|---|---|
| [1. Getting started](01-getting-started.md) | what zephyr-java is and is not, requirements, the JAR and the AAR, the native library on the desktop, a first program |
| [2. Wallets](02-wallets.md) | network types, create, restore, view-only, open from files, bytes or a config, the password, when to save, close, addresses and keys |
| [3. Connecting to a node](03-connecting-to-a-node.md) | the connection and its URI, http and https, TLS and `ZephyrTls`, the remote-node trade-off, syncing, heights, an unreachable node |
| [4. Threads and listeners](04-threads-and-listeners.md) | blocking calls, the threads callbacks run on, the first listener, the five callbacks, what a callback may call, exceptions in callbacks, closing, several threads, the end of the process, what is not known, releases up to `java.4` |
| [5. Assets, accounts and balances](05-assets-and-balances.md) | ZPH, ZSD, ZRS, ZYS and the V1 names, amounts and their conversion, `ZephyrBalance`, the four balance shapes, accounts, subaddresses and labels, locked and unlocked |
| [6. History](06-history.md) | transactions and transfers per asset, queries, the pool, conversions in the history, notes and keys, outputs and their asset, payment checks |
| [7. Sending](07-sending.md) | the currency pair, create then relay, what to show, fees and priorities, several destinations, sweeps, failures, saving after a send |
| [8. Conversions and the reserve](08-conversions-and-the-reserve.md) | the ten pairs, the reserve-ratio rules, every field of `ZephyrReserveInfo`, the six supply keys, building a conversion, the mint formula, converting everything, what was tested |
| [9. Errors and exceptions](09-errors.md) | `ZephyrError`, where messages come from, what is not a `ZephyrError`, the messages a wallet meets, logging safely, the native log |
| [10. Android](10-android.md) | the dependency and the packaging line, devices, ABIs and sizes, the RPC classes below API 26, the network, storage, backup, lifecycle, logs, a first test on a device, R8 rules |
| [11. The RPC classes](11-the-rpc-classes.md) | `ZephyrDaemonRpc` and the pricing record, the removed wallet RPC client, `ZephyrRpcError`, dependencies |
| [12. Coming from monero-java](12-coming-from-monero-java.md) | the renaming rules, what is different, both libraries in one program |
| [13. Known limitations](13-known-limitations.md) | every limitation with its status and a link, what was tested and what was not, what `2.3.0-sec.2-java.5` fixed |
| [14. Building and verifying](14-building-and-verifying.md) | the pins, the root certificates, the patches, the builds, reproducibility, the manifest, signatures, reproducing a release, the guide's site |

## Conventions

- **Fragments.** A short Java block that uses `wallet` means an open `ZephyrWalletFull`; the block
  does not show how it was opened.
- **Compiled examples.** A block whose label is "Java, compiled" on the site (info string
  `java examples/<Class>.java` in the Markdown) is copied from that class in
  `src/test/java/examples/`. The class is compiled with every `mvn test`, and the test
  `TestExamples` fails when the block and the class differ. Other blocks are fragments and are
  not compiled.
- **Placeholders.** Values you supply are written in angle brackets, such as `<version>`, and
  explained below the block. Example hosts end in `.example.invalid`.
- **Amounts.** Every amount in the API is a `BigInteger` in atomic units, 10^12 per coin for every
  asset. Prose names amounts in coins with the asset's ticker after the number.
- **Dates.** Dates are absolute and in UTC. A statement about a release names that release.

## Where the statements come from

Statements about the Zephyr core come from the pinned sources and from test runs against mainnet.
Those runs took place from 2026-11-06 to 2026-11-10, during the work on the releases up to
`2.3.0-sec.2-java.4`. Each such statement names its date where it matters.

Statements marked "observed on 2026-11-18" or "observed on 2026-11-19" come from runs against a
mainnet node on Linux `x86_64` (Debian 13, OpenJDK 21), read-only, or offline. They used
development builds of the `2.3.0-sec.2-java.5` sources as they stood on those days; some used the
Linux library built in the release container. None of those runs was made on Android.
Statements about the Android emulator name it. Those about `2.3.0-sec.2-java.5` come from runs on
2026-11-19 and 2026-11-20 ([What was tested](13-known-limitations.md#what-was-tested)).

Statements marked "observed on 2026-11-17" come from runs of the same kind with
`2.3.0-sec.2-java.4`. Most used a developer build of its sources. Those marked "with the released
JAR" used `zephyr-java-2.3.0-sec.2-java.4.jar` and its own native library. Where `java.5` changed
the behaviour, the guide names `java.4` with them.

One run is an observation, not a guarantee: a run that found no failure proves no safety.

Statements marked "read in the code" come from the source of this library or of the pinned core,
not from a run. Where a fact is not known, the guide says so.
