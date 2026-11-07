# 8. Known limitations

These are the limits a wallet app runs into today. Most come from the Zephyr core (zephyr-cpp and
`wallet2`), which zephyr-java builds unchanged: the backports change the core only for security
fixes, so functional gaps stay until the core itself changes. Each entry says what happens, its
status, and where the finding comes from.

## In the core

**`getOutputs()` reports no asset.** The core keeps an output's asset but does not serialize it, so
the outputs that `getOutputs()` returns have no `assetType`. Coin control per asset (choosing
outputs of one asset by key image) is therefore not possible through the output query. Balances
and transfers are not affected: they carry the asset. *Status:* core behaviour, open.

**The asset of an unconfirmed output notification is empty.** `onOutputReceived` for a payment
seen in the pool arrives with `getAssetType()` equal to `""`: `wallet2`'s callback for pool
payments has no asset parameter, and zephyr-cpp passes an empty string. The notification at
confirmation goes through a callback that carries the asset. Use `onBalancesChanged` (which names
the asset) or `getTxs()` (which reports the right asset for the same pool transfer). The official
Zephyr wallets do the same and leave `onOutputReceived` unused. *Status:* core behaviour, not a
security matter, so it stays; documented in the Javadoc of `onOutputReceived`.
Observed on mainnet on 2026-11-07.

**`changePassword` fails for a wallet opened from bytes.** On a wallet opened with
`openWalletData` (or created without a path), `changePassword` throws
`failed to read file ".keys"`: the core verifies the old password against the keys file on disk,
which such a wallet does not have. The same call on a wallet with files is not affected. No stray
file is written. *Status:* core behaviour (the core took only half of a later monero-cpp fix),
confirmed on mainnet. Apps that store bytes can keep the password stable, or restore the
wallet from its mnemonic under the new password (which costs a re-sync).

**`checkTxKey` returns amount 0.** `checkTxKey` (and, from the same code, `checkTxProof`) reads the
received amount of the asset `ZEPH` only. Since hard fork 11 no new output can be `ZEPH`, so for
every transaction after height 536,000 the amount is 0. The check itself works: on mainnet,
`checkTxKey` on a confirmed 10 ZPH transfer returned `good = true`, the right confirmations and
`receivedAmount = 0`. An app can prove that a transaction reached an address, not how much it
carried. *Status:* core behaviour, confirmed on mainnet on 2026-11-07; `checkTxProof` read in the
code, not tested.

**`sweepOutput` validates with `ZEPH`.** Sweeping one output by key image validates the
destination with a hard-coded `ZEPH` asset and builds the transaction without an asset. Since
hard fork 11 no `ZEPH` output is valid, so `sweepOutput` is most likely unusable on mainnet. The
other sweeps take the currency pair from the config and work. *Status:* found by reading the core;
not tested.

**`sweepUnlocked` without an account index sweeps too much.** The core builds the list of
subaddresses to sweep with one iterator and tests another, so without an account index it sweeps
every subaddress of a funded account. With one subaddress, or with `setAccountIndex(...)`, this
does not matter. Always set the account index. *Status:* core, read in the code; a candidate for
the backports.

**Testnet and stagenet do not reach the V2 rules.** In the pinned Zephyr tree the hard-fork tables
of testnet and stagenet stop at hard fork 3 (height 100), and the V2 asset names are not allowed
before hard fork 8. A wallet on those networks never sees ZPH, ZSD, ZRS or ZYS. Development and
tests that need the current rules run on mainnet with small amounts. *Status:* the state of the
core's network tables.

## In zephyr-java

**The RPC classes are a first pass.** `ZephyrWalletRpc` serves the base coin; its per-asset
balance calls, the per-subaddress map, `getReserveInfo` and `getCirculatingSupply` throw a clear
"not supported" error until the port to `zephyr-wallet-rpc` is done. `ZephyrDaemonRpc` reads the
pricing record of block headers. The full wallet (`ZephyrWalletFull`) is the complete API.
*Status:* planned work (the port to `zephyr-wallet-rpc`).

**`isClosed()` of `ZephyrWalletRpc` and error code -13.** monero-java recognises a closed RPC
wallet by error code -8 with "No wallet file"; `zephyr-wallet-rpc` v2.3.0 answers -13, so
`isClosed()` returned `false` for a closed wallet. *Status:* fixed on 2026-11-07, `isClosed()`
accepts both codes.

**Conversions other than ZPH → ZRS are not tested on mainnet.** ZPH transfers, a ZPH sweep, a ZRS
sweep and ZPH → ZRS mints with `createTxs` and with `sweepUnlocked` ran on mainnet with real
funds. `MINT_STABLE` and `REDEEM_RESERVE` were refused by the reserve ratio at the time, and no ZSD
or ZYS was at hand for the other pairs. They use the same code path in zephyr-java (only the pair
differs), but they have not been seen working.

## In the build and the packaging

**Android: not yet run on a device or an emulator.** The Android libraries and the AAR are built
and checked (exports, page alignment, packaging), but the build host has no hardware
virtualisation, so nothing has run on Android yet. *Status:* pending a device test.

**Android: TLS needs the app's help.** The native wallet does not use Android's certificate store;
`https://` nodes need `SSL_CERT_FILE` or `SSL_CERT_DIR` in the environment, and Android's own
certificate directory as `SSL_CERT_DIR` is untested ([chapter 6](06-android.md#tls-to-a-node)).

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
