# 7. Sending

In Zephyr, a transfer and a conversion are the same operation. Both are built with `createTxs` (or
`sweepUnlocked`) and a `ZephyrTxConfig`; what makes one a transfer and the other a conversion is
the **currency pair** on the config. There are no separate conversion methods.

## The currency pair on every send

Every config names a source currency (the asset you spend) and a destination currency (the asset
the recipient gets). Both are required for **every** send, a plain transfer included; without them
the core throws "Must specify source currency". The account to send from is required as well
("Must specify account index to send from").

```java
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

## Create, then relay

`createTxs` builds and signs; by default it does **not** broadcast. Show the user the fee, then
relay:

```java
List<ZephyrTxWallet> txs = wallet.createTxs(config);      // relay defaults to false
BigInteger fee = txs.get(0).getFee();                     // in the source asset
String hash = wallet.relayTx(txs.get(0));                 // now it is sent
```

`setRelay(true)` on the config does both in one call. `createTx` is the same as `createTxs` for a
config that must fit in one transaction.

## Fees

**Fees are paid in the source asset.** A ZPH transfer pays its fee in ZPH, a ZRS transfer in ZRS,
and a ZPH → ZRS mint in ZPH. On the mainnet test run a 10 ZPH transfer paid 0.000308 ZPH, a 50 ZPH
mint 0.0004574 ZPH and a ZRS sweep 0.0004456 ZRS.

## Sweeping

`sweepUnlocked` sends everything unlocked from an account (or chosen subaddresses) to one address.

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
