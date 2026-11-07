# 7. Coming from monero-java

zephyr-java is derived from [monero-java](https://github.com/woodser/monero-java) at commit
`53a54543` (2023-07-10), the API era that matches the Zephyr core. If you know monero-java, or
monero-ts and its documentation, you know most of zephyr-java: the classes, the methods and the
model are the same, renamed once. This chapter lists the renaming rules, the differences that
are not renames, and how to use both libraries in one program.

Monero additions to monero-java after `53a54543` are not in zephyr-java: they need parts of the
native core that Zephyr's core does not have. The same goes for upstream's "subtract fee from
destinations", which the Zephyr core would ignore without an error.

## The renaming rules

The rename was done once, mechanically, over the whole source tree:

| monero-java | zephyr-java |
|---|---|
| package `monero.*` | package `zephyr.*` |
| package `common.*` | package `zephyr.lib.*` |
| class `Monero*` | class `Zephyr*` |
| `MoneroUtils.xmrToAtomicUnits(double)` | `ZephyrUtils.toAtomicUnits(double)` |
| `MoneroUtils.atomicUnitsToXmr(BigInteger)` | `ZephyrUtils.fromAtomicUnits(BigInteger)` |
| native library `monero-java` | native library `zephyr-java` |
| Maven `io.github.monero-ecosystem:monero-java` (now `io.github.woodser:monero-java`) | Maven `io.github.loki-notfound:zephyr-java` |

Everything else keeps its name: method names, parameters, the model's fields and the query
classes. So a monero-ts example ports by renaming its types.

The twenty classes you meet first:

| monero-java | zephyr-java |
|---|---|
| `monero.wallet.MoneroWalletFull` | `zephyr.wallet.ZephyrWalletFull` |
| `monero.wallet.MoneroWallet` | `zephyr.wallet.ZephyrWallet` |
| `monero.wallet.MoneroWalletRpc` | `zephyr.wallet.ZephyrWalletRpc` |
| `monero.daemon.MoneroDaemonRpc` | `zephyr.daemon.ZephyrDaemonRpc` |
| `monero.common.MoneroUtils` | `zephyr.common.ZephyrUtils` |
| `monero.common.MoneroRpcConnection` | `zephyr.common.ZephyrRpcConnection` |
| `monero.common.MoneroConnectionManager` | `zephyr.common.ZephyrConnectionManager` |
| `monero.common.MoneroError` | `zephyr.common.ZephyrError` |
| `monero.daemon.model.MoneroNetworkType` | `zephyr.daemon.model.ZephyrNetworkType` |
| `monero.wallet.model.MoneroWalletConfig` | `zephyr.wallet.model.ZephyrWalletConfig` |
| `monero.wallet.model.MoneroWalletListener` | `zephyr.wallet.model.ZephyrWalletListener` |
| `monero.wallet.model.MoneroTxConfig` | `zephyr.wallet.model.ZephyrTxConfig` |
| `monero.wallet.model.MoneroTxWallet` | `zephyr.wallet.model.ZephyrTxWallet` |
| `monero.wallet.model.MoneroTxQuery` | `zephyr.wallet.model.ZephyrTxQuery` |
| `monero.wallet.model.MoneroTransfer` | `zephyr.wallet.model.ZephyrTransfer` |
| `monero.wallet.model.MoneroIncomingTransfer` | `zephyr.wallet.model.ZephyrIncomingTransfer` |
| `monero.wallet.model.MoneroOutgoingTransfer` | `zephyr.wallet.model.ZephyrOutgoingTransfer` |
| `monero.wallet.model.MoneroOutputWallet` | `zephyr.wallet.model.ZephyrOutputWallet` |
| `monero.wallet.model.MoneroAccount` | `zephyr.wallet.model.ZephyrAccount` |
| `monero.wallet.model.MoneroDestination` | `zephyr.wallet.model.ZephyrDestination` |

A helper from `common.utils`, such as `common.utils.GenUtils`, is now
`zephyr.lib.utils.GenUtils`.

## What is different

These are the changes a port has to make by hand. The compiler finds every one of them.

**Balances are per asset.** monero-java's six balance methods return a `BigInteger`. In
zephyr-java:

| monero-java | zephyr-java |
|---|---|
| `BigInteger getBalance()` | `ZephyrBalance getBalance()` |
| `BigInteger getBalance(Integer accountIdx)` | `Map<Integer, ZephyrBalance> getBalance(Integer accountIdx)`, keyed by subaddress |
| `BigInteger getBalance(Integer accountIdx, Integer subaddressIdx)` | `BigInteger getBalance(String assetType, Integer accountIdx, Integer subaddressIdx)` |
| — | `BigInteger getBalance(String assetType, Integer accountIdx)` |

The unlocked balance has the same four shapes, and `ZephyrAccount` and `ZephyrSubaddress` hold a
`ZephyrBalance` instead of a `BigInteger`. [Chapter 3](03-assets-and-balances.md) explains them.

**The listener names the asset.** `onBalancesChanged(BigInteger, BigInteger)` became
`onBalancesChanged(BigInteger, BigInteger, String assetType)`. The old method still exists in
`ZephyrWalletListener` but is `final` and deprecated, so a ported listener that overrides it does
not compile, rather than compiling and never being called. Outputs carry the asset in
`getAssetType()` (empty for unconfirmed outputs, [chapter 2](02-wallets.md#the-listener)).

**Every send names a currency pair.** A `ZephyrTxConfig` without `setSourceCurrency` and
`setDestinationCurrency` fails with "Must specify source currency", a plain transfer included.
This is the one difference the compiler does not find; [chapter 4](04-sending-and-converting.md)
has the pairs.

**New calls.** `getReserveInfo()` and `getCirculatingSupply()` on the wallet
([chapter 5](05-reserve-and-supply.md)), `ZephyrAssets` with the asset names, and the pricing record
on daemon block headers.

**Addresses and constants.** Address decoding stays pure Java (no native library needed) but
knows Zephyr's prefixes, which are multi-byte varints: mainnet addresses start with `ZEPHYR`.
`ZephyrNetworkType` carries Zephyr's codes. `ZephyrUtils.RING_SIZE` is 16, the network rule of the
Zephyr core, not Monero's 12. Keccak-256 and hex are the library's own code (`Keccak256`,
`GenUtils`), so there is no dependency on Bouncy Castle or Apache commons-codec.

**The RPC classes are a first pass.** `ZephyrWalletRpc` talks to `zephyr-wallet-rpc` for the base
coin only and rejects the per-asset calls, reserve info and supply with a clear message. Its
`isClosed()` understands zephyr-wallet-rpc's error code -13. Its ZMQ listener needs jeromq, which
the AAR does not bring ([chapter 6](06-android.md#the-dependency)).

## Both libraries in one program

zephyr-java was built so that it can sit next to monero-java in one JVM or one app: a wallet that
supports both coins needs both.

- **No class clashes.** The packages differ (`zephyr.*` against `monero.*` and `common.*`).
- **No native clashes.** Each library is its own shared object (`libzephyr-java.so`,
  `libmonero-java.so`), and zephyr-java's exports only JNI symbols. The one name both libraries
  export is `JNI_OnLoad`, which the JVM looks up per library, so it is no conflict.
- **Tested.** The coexistence test loads monero-java 0.8.65 from Maven Central and zephyr-java in
  one JVM, validates a Zephyr address in Java and natively and a Monero address with
  monero-java, and checks that the two dynamic symbol sets share exactly `{JNI_OnLoad}` and no
  `Java_*` name.

**Jackson decides the versions.** monero-java 0.8.65 needs Jackson 2.15 or newer (it uses
`StreamReadConstraints`); zephyr-java declares 2.13.5. A build tool resolves one Jackson for the
whole program, normally the highest; with Maven's nearest-wins rule you may have to pin it
yourself. The coexistence test runs both libraries on Jackson 2.21.7, which shows that zephyr-java
works on the 2.21 line. On Android, Jackson 2.14 and newer need minSdk 26
([chapter 6](06-android.md#the-dependency)).

The coexistence test lives in `src/test/java/test/TestCoexistence.java` and runs with
`mvn -Pcoexistence test`; the default build does not compile it, because it needs monero-java on
the class path.
