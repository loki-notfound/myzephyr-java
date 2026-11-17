# 6. History

This chapter covers what the wallet's history shows: conversions, the amount of a checked
payment and the asset of an output.

## Conversions in the history

After a conversion is confirmed, `getTxs()` and `getTransfers()` show it as follows:

- The **outgoing transfer's** amount is the source amount (50 ZPH), in the source asset.
- The outgoing **destination** shows the **destination amount** in the destination currency
  (52.60432 ZRS). On the transaction that `createTxs` returned, the same destination still shows
  the source amount (50 ZPH). Code that compares the two must expect that.
- An **incoming transfer** in the destination currency carries the minted amount, with account and
  subaddress set once confirmed.

The converted funds unlock like received funds, 10 blocks after the conversion is mined
([chapter 5](05-assets-and-balances.md#locked-and-unlocked)).

## checkTxKey returns amount 0

**What you see.** In the full wallet (`ZephyrWalletFull`), `checkTxKey` and `checkTxProof` return
`receivedAmount = 0` for every current transaction: on mainnet on 2026-11-07, `checkTxKey` on a
confirmed 10 ZPH transfer returned `good = true`, the right number of confirmations and amount 0.

**Why.** The core reads the received amount of the asset `ZEPH` only, from a map that holds the
amount per asset. Since hard fork 11 (height 536,000) no new output can be `ZEPH`, so the entry is
always empty.

**Workaround.** Trust the check for `isGood()` and the confirmations, and read the amount from the
wallet's own history, where every transfer carries its asset. The receiver checks the key it got
from the sender and sums its incoming transfers per asset:

```java examples/GuideHistory.java
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

```java examples/GuideHistory.java
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
once the transaction is confirmed ([Conversions in the history](#conversions-in-the-history)).

**Not possible.** A third party without the receiving or sending wallet cannot learn the amount
from the key or the proof; it learns that the transaction paid the address and how many
confirmations it has. *Status:* core behaviour, confirmed on mainnet on 2026-11-07; `checkTxProof`
uses the same code (read in the code, not tested). A core fix would read the destination asset's
entry; it is not a security fix, so the backports leave it. Through `ZephyrWalletRpc` the amount
comes back `null` for a different reason (see "The RPC classes are a first pass" below).

## getOutputs() reports no asset

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

```java examples/GuideHistory.java
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

```java examples/GuideHistory.java
static List<ZephyrOutputWallet> unspentOutputs(ZephyrWallet wallet, String asset) {
  List<ZephyrOutputWallet> outputs = new ArrayList<>();
  for (ZephyrOutputWallet output : wallet.getOutputs(new ZephyrOutputQuery().setIsSpent(false))) {
    if (asset.equals(assetOf(wallet, output))) outputs.add(output);
  }
  return outputs;
}
```

`ZephyrWallet.getOutputAssetType(output)` implements this rule from release `2.3.0-sec.2-java.2`
on; with earlier releases, use the snippet. It costs one `getTxs` call per output; cache by
transaction hash for many outputs. On the mainnet test wallet it named all five outputs right,
among them a 50 ZPH → ZRS conversion to the wallet's own address whose ZRS output and ZPH change
sit at the same subaddress.

The alternative is the listener: `onOutputReceived` carries the asset for every output once it is
in a block, change included (read in the core's code), so an app that follows all syncs with a
listener can record the asset per key image itself. The notification for an output seen in the pool
has an empty asset (see the first entry below).

**Not possible.** One case reads wrong: a conversion to one's own account whose converted amount is
split over several outputs at one subaddress, or whose change equals the converted amount, reads as
the source asset. *Status:* core behaviour, open.
