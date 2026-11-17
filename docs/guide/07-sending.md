# 7. Sending

In Zephyr, a transfer and a conversion are the same operation. Both are built with `createTxs` (or
`sweepUnlocked`) and a `ZephyrTxConfig`. What makes one a transfer and the other a conversion is
the **currency pair** on the config. There are no separate conversion methods. This chapter covers
transfers; [Conversions and the reserve](08-conversions-and-the-reserve.md) covers conversions.

The blocks marked "Java, compiled" in this chapter come from
`src/test/java/examples/GuideSending.java`.

## The currency pair on every send

Every config names a source currency (the asset you spend) and a destination currency (the asset
the recipient gets). Both are required for **every** send, a plain transfer included; without them
the core throws "Must specify source currency". The account to send from is required as well
("Must specify account index to send from").

```java examples/GuideSending.java
ZephyrTxWallet tx = wallet.createTx(new ZephyrTxConfig()
    .setAddress(recipient)
    .setAmount(new BigDecimal("2.5").movePointRight(12).toBigIntegerExact())
    .setSourceCurrency(ZephyrAssets.ZPH)
    .setDestinationCurrency(ZephyrAssets.ZPH)
    .setAccountIndex(0));
```

The pair holds for the whole transaction and all its destinations. `ZephyrDestination` and the
transfers have an `assetType` field too, but it only **reports** the asset of a transfer in the
history. Setting it on a destination does not select anything: the core ignores it when sending
and takes the pair from the config.

The amount is in atomic units ([Amounts](05-assets-and-balances.md#amounts)). Check the
recipient's address first ([Addresses and keys](02-wallets.md#addresses-and-keys)).

## Create, then relay

`createTxs` builds and signs; by default it does **not** broadcast. It returns a list, because the
core can split a send into several transactions. Show the user the total fee, then relay all of
them:

```java examples/GuideSending.java
List<ZephyrTxWallet> txs = wallet.createTxs(config);      // relay defaults to false; can be several
BigInteger fee = BigInteger.ZERO;
for (ZephyrTxWallet tx : txs) fee = fee.add(tx.getFee()); // in the source asset
System.out.println("fee " + fee);                         // show it; relay only after the user agrees
return wallet.relayTxs(txs);                              // now every transaction is sent
```

`relayTxs(txs)` relays every transaction of the list and returns their hashes. Relaying only the
first one of a split send would send part of it. `createTx` sets `setCanSplit(false)`: it returns
one transaction or throws, and `relayTx(tx)` then sends it. `setRelay(true)` on the config builds
and relays in one call, without a review.

## What to show before you relay

As advice for the review screen: show every amount with all 12 decimals and its asset, the
recipient's address and the fee. A send can return several transactions, so add up their fees:

```java examples/GuideSending.java
BigInteger fee = BigInteger.ZERO;
for (ZephyrTxWallet tx : txs) fee = fee.add(tx.getFee());  // a send can take several transactions
String asset = config.getSourceCurrency();
return "Send " + GuideBalances.formatFull(config.getAmount()) + " " + asset
    + " to " + config.getAddress()
    + ", network fee " + GuideBalances.formatFull(fee) + " " + asset;
```

Name the action on the button that relays, for example "Send 2.500000000000 ZPH", not "OK". For a
conversion, also show the converted amount
([Building a conversion](08-conversions-and-the-reserve.md#building-a-conversion)).

## Fees and priorities

**Fees are paid in the source asset.** A ZPH transfer pays its fee in ZPH and a ZRS transfer in
ZRS. On the mainnet test run a 10 ZPH transfer paid 0.000308 ZPH and a ZRS sweep 0.0004456 ZRS.
The fee of a conversion is in [Fees of a conversion](08-conversions-and-the-reserve.md#fees-of-a-conversion).

`setPriority` takes a `ZephyrTxPriority`: `DEFAULT`, `UNIMPORTANT`, `NORMAL` or `ELEVATED`. The
mainnet sends of this project used the default. Which fee tier `DEFAULT` selects in the Zephyr
core has not been established for zephyr-java. The other priorities, and `setFee`,
`setUnlockHeight`, `setPaymentId` and `setCanSplit`, are inherited from monero-java; not tested
against Zephyr.

## Several destinations

`addDestination(address, amount)` adds a destination; `setDestinations` sets them all. The currency
pair holds for every destination:

```java examples/GuideSending.java
return wallet.createTxs(new ZephyrTxConfig()
    .addDestination(first, new BigInteger("1000000000000"))      // 1 ZPH
    .addDestination(second, new BigInteger("2500000000000"))     // 2.5 ZPH
    .setSourceCurrency(ZephyrAssets.ZPH)
    .setDestinationCurrency(ZephyrAssets.ZPH)
    .setAccountIndex(0));
```

`setAmount` is for a config with one destination: on a config that already has several, it throws
"Cannot set amount because ZephyrTxConfig already has multiple destinations". Several destinations
in one transaction are inherited from monero-java; not tested against Zephyr.

## When the core refuses

`createTxs` throws a `ZephyrError` whose `getCode()` is `null` when the core refuses to build.
Observed on 2026-11-17 with `setRelay(false)` on a synced mainnet wallet without funds: transfers
in ZPH and in ZSD, of one coin and of one atomic unit, threw "not enough money".

Not established:

- the message when the amount exceeds the unlocked balance while the total balance would be enough
  (the core has a separate error for funds that are not unlocked);
- the messages about fees;
- what happens when the node refuses a relayed transaction.

Treat the message as text to
show or log, not as a value to compare ([Errors and exceptions](09-errors.md)).

## Sweeping

`sweepUnlocked` sends everything unlocked from an account (or chosen subaddresses) to one address.

```java examples/GuideSending.java
return wallet.sweepUnlocked(new ZephyrTxConfig()
    .setAddress(recipient)
    .setSourceCurrency(ZephyrAssets.ZRS)
    .setDestinationCurrency(ZephyrAssets.ZRS)
    .setAccountIndex(0));  // always set the account index
```

**`sweepUnlocked` without an account index sweeps too much.** The core builds the list of
subaddresses to sweep with one iterator and tests another. Without an account index it therefore
sweeps every subaddress of a funded account. With one subaddress, or with `setAccountIndex(...)`,
this does not matter. Always set the account index. *Status:* core, read in the code; a candidate
for the backports.

**`sweepOutput` validates with `ZEPH`.** Sweeping one output by key image validates the
destination with a hard-coded `ZEPH` asset and builds the transaction without an asset. Since
hard fork 11 no `ZEPH` output is valid, so `sweepOutput` is most likely unusable on mainnet. The
other sweeps take the currency pair from the config and work. *Status:* found by reading the core;
not tested.

## After a send

Save the wallet, or store its bytes again, right after a send: the secret key of the transaction
exists only in the cache ([When to save](02-wallets.md#when-to-save)). The transaction appears in
the history in the pool, and its change unlocks 10 blocks after it is mined
([Locked and unlocked](05-assets-and-balances.md#locked-and-unlocked)).
