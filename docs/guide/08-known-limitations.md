# 8. Known limitations

These are the limits a wallet app runs into today. Most come from the Zephyr core (zephyr-cpp and
`wallet2`), which zephyr-java builds unchanged: the backports change the core only for security
fixes, so functional gaps stay until the core itself changes. Each entry says what happens, its
status, and where the finding comes from.

## In the core

### checkTxKey returns amount 0

**What you see.** In the full wallet (`ZephyrWalletFull`), `checkTxKey` and `checkTxProof` return
`receivedAmount = 0` for every current transaction: on mainnet on 2026-11-07, `checkTxKey` on a
confirmed 10 ZPH transfer returned `good = true`, the right number of confirmations and amount 0.

**Why.** The core reads the received amount of the asset `ZEPH` only, from a map that holds the
amount per asset. Since hard fork 11 (height 536,000) no new output can be `ZEPH`, so the entry is
always empty.

**Workaround.** Trust the check for `isGood()` and the confirmations, and read the amount from the
wallet's own history, where every transfer carries its asset. The receiver checks the key it got
from the sender and sums its incoming transfers per asset:

```java
static Map<String, BigInteger> receivedPerAsset(ZephyrWallet wallet, String txHash, String txKey, String address, long minConfirmations) {
  ZephyrCheckTx check = wallet.checkTxKey(txHash, txKey, address);
  if (!check.isGood()) throw new IllegalStateException("the transaction does not pay " + address);
  if (check.getNumConfirmations() < minConfirmations) throw new IllegalStateException("only " + check.getNumConfirmations() + " confirmations");
  // check.getReceivedAmount() is 0 for every current transaction: do not use it
  Map<String, BigInteger> received = new HashMap<>();
  for (ZephyrTxWallet tx : wallet.getTxs(txHash)) {
    if (tx.getIncomingTransfers() == null) continue;
    for (ZephyrIncomingTransfer transfer : tx.getIncomingTransfers()) {
      received.merge(transfer.getAssetType(), transfer.getAmount(), BigInteger::add);
    }
  }
  return received;
}
```

The sender has the amount in the destinations of its outgoing transfer:

```java
static Map<String, BigInteger> sentPerAsset(ZephyrWallet wallet, String txHash, String address) {
  Map<String, BigInteger> sent = new HashMap<>();
  for (ZephyrTxWallet tx : wallet.getTxs(txHash)) {
    ZephyrOutgoingTransfer outgoing = tx.getOutgoingTransfer();
    if (outgoing == null || outgoing.getDestinations() == null) continue;
    for (ZephyrDestination destination : outgoing.getDestinations()) {
      if (address.equals(destination.getAddress())) sent.merge(destination.getAssetType(), destination.getAmount(), BigInteger::add);
    }
  }
  return sent;
}
```

`getTxs(hash)` throws when the transaction is not in the wallet, so the receiver's wallet must be
synced past it. The destinations are local data of the sending wallet: a wallet restored from its
mnemonic does not have them. For a conversion, the destination shows the destination amount only
once the transaction is confirmed ([chapter 4](04-sending-and-converting.md#the-history-of-a-conversion)).

**Not possible.** A third party without the receiving or sending wallet cannot learn the amount
from the key or the proof; it learns that the transaction paid the address and how many
confirmations it has. *Status:* core behaviour, confirmed on mainnet on 2026-11-07; `checkTxProof`
uses the same code (read in the code, not tested). A core fix would read the destination asset's
entry; it is not a security fix, so the backports leave it. Through `ZephyrWalletRpc` the amount
comes back `null` for a different reason (see "The RPC classes are a first pass" below).

### getOutputs() reports no asset

**What you see.** In the full wallet, every `ZephyrOutputWallet` that `getOutputs()` returns has
`getAssetType() == null`: on mainnet on 2026-11-08, all five outputs of a test wallet that had held
ZPH and ZRS came back without an asset.

**Why.** The core keeps an output's asset but does not serialize it in the output query. Balances,
transfers and the listener's confirmed-output callbacks carry the asset; only the output query
lacks it.

**Workaround.** Take the asset from the output's transaction. For a transaction the wallet
received, the incoming transfer at the output's account and subaddress names the asset (a received
transaction credits one asset per subaddress). For a transaction the wallet sent from the output's
account, the core lists no incoming transfer for the change, so an output without a matching
transfer in another asset is in the source asset, which the outgoing transfer carries:

```java
static String assetOf(ZephyrWallet wallet, ZephyrOutputWallet output) {
  List<ZephyrTxWallet> txs = wallet.getTxs(Arrays.asList(output.getTx().getHash()), new ArrayList<String>());
  if (txs.isEmpty()) return null;  // the transaction is not in the wallet
  ZephyrTxWallet tx = txs.get(0);
  ZephyrOutgoingTransfer outgoing = tx.getOutgoingTransfer();
  boolean spentFromThisAccount = outgoing != null && output.getAccountIndex().equals(outgoing.getAccountIndex());
  if (tx.getIncomingTransfers() != null) {
    for (ZephyrIncomingTransfer transfer : tx.getIncomingTransfers()) {
      if (!output.getAccountIndex().equals(transfer.getAccountIndex())) continue;
      if (!output.getSubaddressIndex().equals(transfer.getSubaddressIndex())) continue;
      if (!spentFromThisAccount) return transfer.getAssetType();  // a received transaction: one asset per subaddress
      if (!Objects.equals(transfer.getAssetType(), outgoing.getAssetType()) && transfer.getAmount().equals(output.getAmount())) {
        return transfer.getAssetType();  // the converted output of a conversion to this account
      }
    }
  }
  return spentFromThisAccount ? outgoing.getAssetType() : null;  // change and same-asset sends to this account: the source asset
}
```

Coin control per asset then filters the outputs through it:

```java
static List<ZephyrOutputWallet> unspentOutputs(ZephyrWallet wallet, String asset) {
  List<ZephyrOutputWallet> outputs = new ArrayList<>();
  for (ZephyrOutputWallet output : wallet.getOutputs(new ZephyrOutputQuery().setIsSpent(false))) {
    if (asset.equals(assetOf(wallet, output))) outputs.add(output);
  }
  return outputs;
}
```

The next release adds this rule to the API as `ZephyrWallet.getOutputAssetType(output)`; the snippet
is the interim form. It costs one `getTxs` call per output; cache by transaction hash for many
outputs. On the mainnet test wallet it named all five outputs right, among them a 50 ZPH → ZRS
conversion to the wallet's own address whose ZRS output and ZPH change sit at the same subaddress.

The alternative is the listener: `onOutputReceived` carries the asset for every output once it is
in a block, change included (read in the core's code), so an app that follows all syncs with a
listener can record the asset per key image itself. The notification for an output seen in the pool
has an empty asset (see the first entry below).

**Not possible.** One case reads wrong: a conversion to one's own account whose converted amount is
split over several outputs at one subaddress, or whose change equals the converted amount, reads as
the source asset. *Status:* core behaviour, open.

### Other limitations in the core

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
Through the RPC classes the two core quirks above look different: `zephyr-wallet-rpc` returns
the `checkTxKey` amount per asset and the asset of every output, which this first pass does not
read yet, so `getReceivedAmount()` comes back `null` and the outputs carry no asset.
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
