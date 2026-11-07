# 8. Known limitations

These are the limits a wallet app runs into today. Most come from the Zephyr core (zephyr-cpp and
`wallet2`), which zephyr-java builds unchanged: the backports change the core only for security
fixes, so functional gaps stay until the core itself changes. Each entry says what happens, its
status, and where the finding comes from.
<!-- PLAN §2 "Own project. The owner maintains the backports; the core base never changes"; ZEPHYR-DELTA §1 (the backports policy leaves non-security findings alone) -->

## In the core

**`getOutputs()` reports no asset.** The core keeps an output's asset but does not serialize it, so
the outputs that `getOutputs()` returns have no `assetType`. Coin control per asset (choosing
outputs of one asset by key image) is therefore not possible through the output query. Balances
and transfers are not affected: they carry the asset. *Status:* core behaviour, open.
<!-- ZEPHYR-DELTA §1 "Outputs: m_asset_type is not serialized. getOutputs() cannot report the asset"; ZEPHYR-DELTA §3 "This limits coin control in an app" -->

**The asset of an unconfirmed output notification is empty.** `onOutputReceived` for a payment
seen in the pool arrives with `getAssetType()` equal to `""`: `wallet2`'s callback for pool
payments has no asset parameter, and zephyr-cpp passes an empty string. The notification at
confirmation goes through a callback that carries the asset. Use `onBalancesChanged` (which names
the asset) or `getTxs()` (which reports the right asset for the same pool transfer). The official
Zephyr wallets do the same and leave `onOutputReceived` unused. *Status:* core behaviour, not a
security matter, so it stays; documented in the Javadoc of `onOutputReceived` (register D23).
Observed on mainnet on 2026-11-07.
<!-- QUESTIONS R8-9 (verbatim: "onOutputReceived [ 10000000000000]"; monero_wallet_full.cpp:818; wallet2.h:141); ZEPHYR-DELTA §1 "Pool outputs have no asset in the listener"; register D23 -->

**`changePassword` fails for a wallet opened from bytes.** On a wallet opened with
`openWalletData` (or created without a path), `changePassword` throws
`failed to read file ".keys"`: the core verifies the old password against the keys file on disk,
which such a wallet does not have. The same call on a wallet with files is not affected. No stray
file is written. *Status:* core behaviour (the core took only half of a later monero-cpp fix),
confirmed on mainnet in R8. Apps that store bytes can keep the password stable, or restore the
wallet from its mnemonic under the new password (which costs a re-sync).
<!-- ANALYSIS.md:47-50 (ZephyrProtocol's 38c80df took over half of monero-cpp 6fedf2d; verify_password against the file on disk); evidence/r8/integration-r8.md:158 (verbatim "zephyr.common.ZephyrError: failed to read file \".keys\"", both runs); QUESTIONS R8-5 (git status unchanged: no stray file) -->

**`checkTxKey` returns amount 0.** `checkTxKey` (and, from the same code, `checkTxProof`) reads the
received amount of the asset `ZEPH` only. Since hard fork 11 no new output can be `ZEPH`, so for
every transaction after height 536,000 the amount is 0. The check itself works: on mainnet,
`checkTxKey` on a confirmed 10 ZPH transfer returned `good = true`, the right confirmations and
`receivedAmount = 0`. An app can prove that a transaction reached an address, not how much it
carried. *Status:* core behaviour, confirmed on mainnet on 2026-11-07; `checkTxProof` read in the
code, not tested.
<!-- ZEPHYR-DELTA §3 (check_tx_key/check_tx_proof read received_amount["ZEPH"]; confirmed on mainnet 2026-11-07, R8 funds run); evidence/r8b/funds-r8.md finding 1 -->

**`sweepOutput` validates with `ZEPH`.** Sweeping one output by key image validates the
destination with a hard-coded `ZEPH` asset and builds the transaction without an asset. Since
hard fork 11 no `ZEPH` output is valid, so `sweepOutput` is most likely unusable on mainnet. The
other sweeps take the currency pair from the config and work. *Status:* found by reading the core;
not tested.
<!-- ZEPHYR-DELTA §3 (monero_wallet_full.cpp:2541, create_transactions_single without an asset; "Untested; found in the review of 2026-11-06") -->

**`sweepUnlocked` without an account index sweeps too much.** The core builds the list of
subaddresses to sweep with one iterator and tests another, so without an account index it sweeps
every subaddress of a funded account. With one subaddress, or with `setAccountIndex(...)`, this
does not matter. Always set the account index. *Status:* core, read in the code; a candidate for
the backports.
<!-- ZEPHYR-DELTA §1 (sweep_unlocked iter/iter2, zephyr-cpp lines 2320-2321, "a candidate for the backports") and QUESTIONS R8-8 -->

**Testnet and stagenet do not reach the V2 rules.** In the pinned Zephyr tree the hard-fork tables
of testnet and stagenet stop at hard fork 3 (height 100), and the V2 asset names are not allowed
before hard fork 8. A wallet on those networks never sees ZPH, ZSD, ZRS or ZYS. Development and
tests that need the current rules run on mainnet with small amounts. *Status:* the state of the
core's network tables.
<!-- ZEPHYR-DELTA §3 "Testnet and stagenet: in this tree their hard-fork tables stop at HF 3 (height 100)" -->

## In zephyr-java

**The RPC classes are a first pass.** `ZephyrWalletRpc` serves the base coin; its per-asset
balance calls, the per-subaddress map, `getReserveInfo` and `getCirculatingSupply` throw a clear
"not supported" error until the port to `zephyr-wallet-rpc` is done. `ZephyrDaemonRpc` reads the
pricing record of block headers. The full wallet (`ZephyrWalletFull`) is the complete API.
*Status:* planned work (register D11).
<!-- register D11, D12; PLAN §5 R7 result; evidence/r8/run-4.txt:53 -->

**`isClosed()` of `ZephyrWalletRpc` and error code -13.** monero-java recognises a closed RPC
wallet by error code -8 with "No wallet file"; `zephyr-wallet-rpc` v2.3.0 answers -13, so
`isClosed()` returned `false` for a closed wallet. *Status:* fixed on 2026-11-07, `isClosed()`
accepts both codes.
<!-- QUESTIONS R8-5 (-13: No wallet file, ZephyrWalletRpc.java:2003-2010); round-12 commit 1 -->

**Conversions other than ZPH → ZRS are not tested on mainnet.** ZPH transfers, a ZPH sweep, a ZRS
sweep and ZPH → ZRS mints with `createTxs` and with `sweepUnlocked` ran on mainnet with real
funds. `MINT_STABLE` and `REDEEM_RESERVE` were refused by the reserve ratio at the time, and no ZSD
or ZYS was at hand for the other pairs. They use the same code path in zephyr-java (only the pair
differs), but they have not been seen working.
<!-- PLAN §5 R8 second part (five transactions: A → B ZPH, mint, B → owner ZPH, mint-sweep, A → owner ZRS; "MINT_STABLE, REDEEM_* and the yield pairs wait for a ratio above 4.0 or for ZSD") -->

## In the build and the packaging

**Android: not yet run on a device or an emulator.** The Android libraries, the AAR and a test app
are built and checked (exports, page alignment, packaging), but the build host has no hardware
virtualisation, so nothing has run on Android yet. The test app is ready for that check
([chapter 6](06-android.md#checking-on-a-device-the-test-app)). *Status:* pending a device test.
<!-- PLAN §5 R9 result ("Not yet proven: running on a device or emulator") and R10 ("The VM has no KVM") -->

**Android: TLS needs the app's help.** The native wallet does not use Android's certificate store;
`https://` nodes need `SSL_CERT_FILE` or `SSL_CERT_DIR` in the environment, and Android's own
certificate directory as `SSL_CERT_DIR` is untested ([chapter 6](06-android.md#tls-to-a-node)).
<!-- evidence/r10/packaging-r10.md stage E; PLAN §5 R10 and R11 -->

**Android libraries contain build paths.** The Android natives are built with NDK r18b, whose
clang 7 cannot rewrite the paths in `__FILE__`. About 700 strings per library (source file names
in error messages, and the location of lambdas in function names) therefore carry the build
directory. They leak nothing beyond that directory name, but they make the Android natives
reproducible only when built at the same path; release builds use a fixed path, as F-Droid does.
The Linux natives carry no build path. *Status:* lasts while the core pins NDK r18b.
<!-- RELEASES §4 (refined 2026-11-07, R11: Android reproducible at the same build path only; clang 7.0.2 knows -fdebug-prefix-map but not -ffile-prefix-map); QUESTIONS R11-7 and R11-8 (707/705/707 strings, of which __FILE__ and 3 lambda locations; Linux 0) -->

**Linux: glibc 2.36 or newer.** The JAR's Linux natives need Debian 12, Ubuntu 23.04, RHEL 10 or
newer. *Status:* by design (the release natives are built in a Debian 12 container).
<!-- PLAN §7.10 option A; RELEASES §4 -->

**macOS and Windows: no packaged natives.** The JAR carries Linux libraries only; on other systems
the library must be built and put on `java.library.path`. *Status:* later.
<!-- RELEASES §3 ("macOS and Windows later"); ZephyrUtils.loadNativeLibrary ("is not packaged yet") -->

**`armeabi-v7a` needs VFPv4.** Devices with Cortex-A8 or A9 cores cannot run the 32-bit ARM library.
*Status:* inherited from Zephyr's own build flags.
<!-- evidence/r9/android-r9.md:163-165; RELEASES §7 -->
