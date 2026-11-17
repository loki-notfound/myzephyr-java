# 6. History

The wallet's history is what its syncs found: transactions, the transfers in them, and the
outputs. Every transfer carries its asset. This chapter covers reading the history and its
queries, the pool, conversions, notes and keys. It ends with two results the core gets wrong: the
amount of a checked payment and the asset of an output.

The blocks marked "Java, compiled" in this chapter come from
`src/test/java/examples/GuideHistory.java`.

## Transactions and transfers

The history holds what the wallet's syncs found, so sync first to see new transactions.

| Call | Returns |
|---|---|
| `getTxs()` | every transaction of the wallet, as `ZephyrTxWallet` |
| `getTxs(hash, ...)`, `getTxs(hashes)` | the transactions with these hashes; throws for a hash the wallet does not know |
| `getTxs(hashes, missing)` | the same, and collects unknown hashes in `missing` instead of throwing |
| `getTxs(query)` | the transactions that match a `ZephyrTxQuery` |
| `getTransfers()`, `getTransfers(accountIdx)`, `getTransfers(accountIdx, subaddressIdx)` | the transfers, as `ZephyrTransfer` |
| `getTransfers(query)` | the transfers that match a `ZephyrTransferQuery` |
| `getIncomingTransfers()`, `getOutgoingTransfers()` | the transfers of one direction |

A `ZephyrTxWallet` has at most one outgoing transfer (`getOutgoingTransfer()`) and a list of
incoming transfers (`getIncomingTransfers()`), each `null` when there is none. It also reports
its hash, height, fee, confirmations and state (`isConfirmed()`, `inTxPool()`, `isFailed()`,
`isLocked()`). The outgoing transfer lists its destinations (`getDestinations()`).

**The asset is on each transfer.** `ZephyrTransfer.getAssetType()` and
`ZephyrDestination.getAssetType()` name the asset of an amount. `ZephyrTxWallet.getIncomingAmount()`
adds the amounts of all incoming transfers and names no asset; read the transfers instead.

`getTxs(hash)` throws a `ZephyrError` "Wallet missing requested tx hashes: " with the hashes when
the wallet does not know a transaction. `getTransfers` and `getOutputs` with hashes in their query
throw a plain `RuntimeException` with the same message. To get the known ones and the unknown
hashes:

```java examples/GuideHistory.java
List<String> missing = new ArrayList<>();
List<ZephyrTxWallet> txs = wallet.getTxs(hashes, missing);  // getTxs(hash) alone throws for a missing hash
```

How the history calls perform with a large history, and whether they need paging, has not been
measured for zephyr-java.

## Queries

The query classes select transactions, transfers and outputs. The setters return the query, so
they chain:

```java examples/GuideHistory.java
return wallet.getTxs(new ZephyrTxQuery()
    .setIsOutgoing(true)
    .setIsConfirmed(true)
    .setMinHeight(fromHeight)
    .setMaxHeight(toHeight));
```

| Query | Main setters |
|---|---|
| `ZephyrTxQuery` | `setIsIncoming`, `setIsOutgoing`, `setHash`, `setHashes`, `setMinHeight`, `setMaxHeight`, `setIsConfirmed`, `setInTxPool`, `setIsFailed`, `setIsLocked`, `setTransferQuery` |
| `ZephyrTransferQuery` | `setIsIncoming`, `setIsOutgoing`, `setAccountIndex`, `setSubaddressIndex`, `setSubaddressIndices`, `setAddress`, `setTxQuery` |
| `ZephyrOutputQuery` | `setIsSpent`, `setAccountIndex`, `setSubaddressIndex`, `setIsFrozen`, `setMinAmount`, `setMaxAmount`, `setTxQuery` |

**No query filters by asset.** Filter on `getAssetType()` in the app:

```java examples/GuideHistory.java
for (ZephyrTransfer transfer : wallet.getTransfers(new ZephyrTransferQuery().setIsIncoming(true))) {
  if (asset.equals(transfer.getAssetType())) total = total.add(transfer.getAmount());
}
```

## The pool

A transaction in the pool, sent but not in a block, is part of the history with `inTxPool()`
true; `setInTxPool(true)` selects such transactions. `getTxs()` reports the right asset for a
pool transfer (observed on mainnet on 2026-11-07). `onOutputReceived` for the same payment has an
empty asset ([Outputs in the pool carry no asset](04-threads-and-listeners.md#outputs-in-the-pool-carry-no-asset)).
Pool funds count in the balance, not in the unlocked balance
([Locked and unlocked](05-assets-and-balances.md#locked-and-unlocked)).

## Conversions in the history

After a conversion is confirmed, `getTxs()` and `getTransfers()` show it as follows:

- The **outgoing transfer's** amount is the source amount (50 ZPH), in the source asset.
- The outgoing **destination** shows the **destination amount** in the destination currency
  (52.60432 ZRS). On the transaction that `createTxs` returned, the same destination still shows
  the source amount (50 ZPH). Code that compares the two must expect that.
- An **incoming transfer** in the destination currency carries the minted amount, with account and
  subaddress set once confirmed.

The converted funds unlock like received funds, 10 blocks after the conversion is mined
([Locked and unlocked](05-assets-and-balances.md#locked-and-unlocked)).

## Notes and transaction keys

`setTxNote(hash, note)` stores a note for a transaction; `getTxNote(hash)` and
`getTxNotes(hashes)` read notes back. `getTxKey(hash)` returns the secret key of a transaction the
wallet sent; the receiver checks a payment with it ([checkTxKey returns amount 0](#checktxkey-returns-amount-0)).

```java examples/GuideHistory.java
wallet.setTxNote(txHash, "rent, November");
String note = wallet.getTxNote(txHash);
String txKey = wallet.getTxKey(txHash);  // the secret key of a transaction this wallet sent
```

Notes and transaction keys exist only in the wallet's cache. Save the wallet after a send and
after a note ([When to save](02-wallets.md#when-to-save)); a wallet restored from its mnemonic
has neither.

## checkTxKey returns amount 0

**What you see.** In the full wallet (`ZephyrWalletFull`), `checkTxKey` and `checkTxProof` return
`receivedAmount = 0` for every current transaction. On mainnet on 2026-11-07, `checkTxKey` on a
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
from the key or the proof. It learns that the transaction paid the address and how many
confirmations it has. *Status:* core behaviour, confirmed on mainnet on 2026-11-07; `checkTxProof`
uses the same code (read in the code, not tested). A core fix would read the destination asset's
entry; it is not a security fix, so the backports leave it. Through `ZephyrWalletRpc` the amount
comes back `null` for a different reason ([ZephyrWalletRpc](11-the-rpc-classes.md#zephyrwalletrpc)).

## getOutputs() reports no asset

**What you see.** In the full wallet, every `ZephyrOutputWallet` that `getOutputs()` returns has
`getAssetType() == null`. On mainnet on 2026-11-08, all five outputs of a test wallet that had held
ZPH and ZRS came back without an asset.

**Why.** The core keeps an output's asset but does not serialize it in the output query. Balances,
transfers and the listener's confirmed-output callbacks carry the asset; only the output query
lacks it.

**Workaround.** `ZephyrWallet.getOutputAssetType(output)`, from release `2.3.0-sec.2-java.2` on,
takes the asset from the output's transaction. Coin control per asset filters the outputs through
it:

```java examples/GuideHistory.java
static List<ZephyrOutputWallet> unspentOutputs(ZephyrWallet wallet, String asset) {
  List<ZephyrOutputWallet> outputs = new ArrayList<>();
  for (ZephyrOutputWallet output : wallet.getOutputs(new ZephyrOutputQuery().setIsSpent(false))) {
    if (asset.equals(wallet.getOutputAssetType(output))) outputs.add(output);  // one getTxs call per output
  }
  return outputs;
}
```

The rule it follows:

- For a transaction the wallet received, the incoming transfer at the output's account and
  subaddress names the asset (a received transaction credits one asset per subaddress).
- For a transaction the wallet sent from the output's account, the core lists no incoming transfer
  for the change. An output without a matching transfer in another asset is therefore in the
  source asset, which the outgoing transfer carries.
- It returns `null` when the output's transaction is not in the wallet or no transfer matches.

It costs one `getTxs` call per output; cache by transaction hash for many outputs. On the mainnet
test wallet it named all five outputs right. Among them was a 50 ZPH → ZRS conversion to the
wallet's own address, whose ZRS output and ZPH change sit at the same subaddress. The method's
Javadoc describes the rule step by step; an app on a release before `java.2` can follow it.

The alternative is the listener. `onOutputReceived` carries the asset for every output once it is
in a block, change included (read in the core's code). An app that follows all syncs with a
listener can record the asset per key image itself. The notification for an output seen in the
pool has an empty asset
([Outputs in the pool carry no asset](04-threads-and-listeners.md#outputs-in-the-pool-carry-no-asset)).

**Not possible.** One case reads wrong: a conversion to one's own account whose converted amount is
split over several outputs at one subaddress, or whose change equals the converted amount, reads as
the source asset. *Status:* core behaviour, open.
