# 3. Assets and balances

A Zephyr wallet holds several assets at once, so every balance in zephyr-java is a balance **per
asset**. This chapter names the assets, shows the four shapes of the balance API and explains
locked and unlocked amounts.

## The assets

Since the V2 hard fork the network has four assets:

| Asset | Name | Constant |
|---|---|---|
| `ZPH` | the base coin | `ZephyrAssets.ZPH` |
| `ZSD` | the stable asset | `ZephyrAssets.ZSD` |
| `ZRS` | the reserve asset | `ZephyrAssets.ZRS` |
| `ZYS` | the yield asset | `ZephyrAssets.ZYS` |

`ZephyrAssets.V2` lists the four in this order, which is the natural order for a user interface.

Before that fork the same assets had other names: `ZEPH`, `ZEPHUSD`, `ZEPHRSV` and `ZYIELD` (V1).
They still appear in two places: in the history of wallets that received funds before the fork,
and as entries in a wallet's balance. Since hard fork 11 (height 536,000, 2025-06-15) the chain
rejects any new output with a V1 asset, and the audit transactions that moved V1 coins to V2 are
no longer allowed, so V1 amounts that were not audited in time cannot be spent. A user interface
shows the four V2 assets; the V1 entries matter only for the history.

In the API the asset is always an open `String`, never an enum. The core may report names this
library has no constant for, and they pass through unchanged. Amounts are `BigInteger` atomic
units, 10^12 per coin, the same for every asset.

## ZephyrBalance

`ZephyrBalance` is a small map from asset name to amount:

```java
ZephyrBalance balance = wallet.getBalance();
BigInteger zph = balance.get(ZephyrAssets.ZPH);   // BigInteger.ZERO if the asset is absent, never null
Set<String> assets = balance.getAssetTypes();     // sorted
Map<String, BigInteger> map = balance.toMap();    // unmodifiable, sorted by asset
```

Its JSON form is the plain object the core emits, `{"ZPH":123,"ZSD":0}`.

**Absent equals zero.** `get` returns zero for an asset the balance does not list, and `equals` and
`hashCode` follow the same rule: `{ZPH=1}` equals `{ZPH=1, ZRS=0}`. The rule exists because the
two sources of balances list zeros differently (below), and a balance from one should equal the
same amounts from the other. `toMap()` still shows exactly the entries that are there.

## The four shapes of the balance API

```java
ZephyrBalance all            = wallet.getBalance();                          // the whole wallet
Map<Integer, ZephyrBalance> m = wallet.getBalance(0);                        // account 0, per subaddress
BigInteger acct              = wallet.getBalance(ZephyrAssets.ZPH, 0);       // one asset, one account
BigInteger sub               = wallet.getBalance(ZephyrAssets.ZPH, 0, 1);    // one asset, one subaddress
```

The same four exist as `getUnlockedBalance(...)`.

- **`getBalance()`** returns the wallet's balance per asset and lists **every** asset the core
  knows, zeros included. A fresh wallet shows eight entries: the four V2 and the four V1 names, all
  zero. Filter with `ZephyrAssets.V2` for display.
- **`getBalance(accountIdx)`** returns the balances of the account's subaddresses, as a map keyed by
  **subaddress index**. It lists only subaddresses and assets that hold something; for an empty
  account the map is empty.
- **`getBalance(asset, accountIdx)`** and **`getBalance(asset, accountIdx, subaddressIdx)`** return
  one amount. A `null` account index throws "Must provide account index".

Accounts and subaddresses carry their balances too, as `ZephyrBalance`:

```java
for (ZephyrAccount account : wallet.getAccounts(true)) {          // true: with subaddresses
  ZephyrBalance accountBalance = account.getBalance();             // only the assets it holds
  for (ZephyrSubaddress subaddress : account.getSubaddresses()) {
    BigInteger zrs = subaddress.getUnlockedBalance().get(ZephyrAssets.ZRS);
  }
}
```

On the R8 mainnet run, a wallet holding 101 ZPH reported eight entries from `getBalance()`
(`ZPH=101000000000000` and seven zeros) and `{ZPH=101000000000000}` for its account. Per asset
they agree, and with the absent-equals-zero rule the two objects are equal.

## Locked and unlocked

The **balance** counts everything the wallet owns, including funds that are not yet spendable. The
**unlocked balance** counts what can be spent now. Two things lock funds:

- **Received funds** (including the change of your own transactions) unlock 10 blocks after the
  block that contains them. At the network's two-minute block target that is about 20 minutes;
  on the R8 run the waits until the change of a transfer and the output of a conversion were
  spendable were 982, 1,203 and 1,684 seconds.
- **Converted funds** follow the same rule: the ZRS from a mint was spendable 10 blocks after the
  mint was mined. [Chapter 4](04-sending-and-converting.md) covers conversions.

Funds in the pool (sent to you but not yet in a block) count in the balance, not in the unlocked
balance. On the R8 run a receiving wallet showed `onBalancesChanged` with `ZPH 10000000000000/0`
(balance 10 ZPH, unlocked 0) as soon as the transfer reached the pool.

A send can only use unlocked funds. An app that shows one number should show the balance and,
next to it, how much of it is still locked.

## Keeping balances up to date

Two ways work together:

- Call the balance methods after each `sync()` (or on a timer while `startSyncing()` runs). They
  read the wallet's local state and do not contact the node.
- Listen for `onBalancesChanged(balance, unlockedBalance, assetType)`. It fires once per asset that
  changed, so a mint that turns ZPH into ZRS produces one call for ZPH and one for ZRS.

The listener's numbers are the wallet-level amounts of that asset, like `getBalance().get(asset)`.
Do not use the asset of `onOutputReceived` for a pending payment: it is empty in the pool
([chapter 2](02-wallets.md#the-listener)).
