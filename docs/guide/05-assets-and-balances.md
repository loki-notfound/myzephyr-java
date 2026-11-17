# 5. Assets, accounts and balances

A Zephyr wallet holds several assets at once, so every balance in zephyr-java is a balance **per
asset**. This chapter names the assets, shows how to convert and format amounts, and shows the
four shapes of the balance API. It then covers accounts, subaddresses and labels, and locked and
unlocked amounts.

The blocks marked "Java, compiled" in this chapter come from
`src/test/java/examples/GuideBalances.java`.

## The assets

Since the V2 hard fork the network has four assets:

| Asset | Name | Constant |
|---|---|---|
| `ZPH` | the base coin | `ZephyrAssets.ZPH` |
| `ZSD` | the stable asset | `ZephyrAssets.ZSD` |
| `ZRS` | the reserve asset | `ZephyrAssets.ZRS` |
| `ZYS` | the yield asset | `ZephyrAssets.ZYS` |

`ZephyrAssets.V2` lists the four in this order, which is the natural order for a user interface.

Before that fork the same assets had other names, the V1 names: `ZEPH`, `ZEPHUSD`, `ZEPHRSV` and
`ZYIELD`. They still appear in two places: in the history of wallets that received funds before
the fork, and as entries in a wallet's balance. Since hard fork 11 (height 536,000, 2025-06-15)
the chain rejects any new output with a V1 asset. The audit transactions that moved V1 coins to V2
are no longer allowed either, so V1 amounts that were not audited in time cannot be spent. A user
interface shows the four V2 assets; the V1 entries matter only for the history. `ZephyrAssets` has
no constants for the V1 names.

In the API the asset is always an open `String`, never an enum. The core may report names this
library has no constant for, and they pass through unchanged.

## Amounts

Every amount in the API is a `BigInteger` in atomic units: 10^12 atomic units are one coin, for
every asset (ZPH, ZSD, ZRS, ZYS alike). For user input and display, `BigDecimal` is exact:

```java examples/GuideBalances.java
// amounts: coins as the user types them, to atomic units; more than 12 decimals throws ArithmeticException
static BigInteger toAtomicUnits(String coins) {
  BigInteger atomicUnits = new BigDecimal(coins.trim()).movePointRight(12).toBigIntegerExact();
  if (atomicUnits.signum() <= 0) throw new IllegalArgumentException("the amount must be above zero");
  return atomicUnits;
}

// amounts: atomic units to coins with all 12 decimals, for a send review or a detail view
static String formatFull(BigInteger atomicUnits) {
  return new BigDecimal(atomicUnits, 12).toPlainString();  // 1500000000000 -> "1.500000000000"
}
```

`new BigDecimal(String)` throws a `NumberFormatException` for text that is not a number. Neither
`BigDecimal` nor `ZephyrTxConfig.setAmount` rejects zero or a negative amount, so the helper does.
A balance
overview can show fewer decimals. This helper keeps 2 to 4 and never shows a non-zero amount as
zero:

```java examples/GuideBalances.java
// amounts: atomic units to coins with 2 to 4 decimals, for a balance overview; never shows dust as zero
static String formatShort(BigInteger atomicUnits) {
  BigDecimal coins = new BigDecimal(atomicUnits, 12);
  BigDecimal shown = coins.setScale(4, RoundingMode.DOWN).stripTrailingZeros();
  if (shown.signum() == 0 && coins.signum() != 0) return coins.stripTrailingZeros().toPlainString();
  return shown.scale() < 2 ? shown.setScale(2).toPlainString() : shown.toPlainString();
}
```

Write the asset's ticker after the number (`1.50 ZPH`). Before a send, show the amount and the fee
with all 12 decimals ([What to show before you relay](07-sending.md#what-to-show-before-you-relay)).

**Do not use `ZephyrUtils.toAtomicUnits(double)` for user input.** It is the only conversion from
coins that `ZephyrUtils` has, and it builds the amount from the `double`'s text (`"" + amount`). It
fails for values that `Double.toString` prints in scientific notation: below 0.001, and from
10^7. It also drops or truncates decimals silently. Its results in `2.3.0-sec.2-java.4`:

| Input | Result |
|---|---|
| `0.0001`, `0.000999` | `NumberFormatException: Illegal embedded sign character` |
| `0.001` | 1,000,000,000 |
| `9999999.999` | 9,999,999,999,000,000,000 |
| `1e7` | `NumberFormatException: For input string: "10E7"` |
| `12345678.9` | `NumberFormatException: For input string: "3456789E7"` |
| `1.5`, `0.1` | 1,500,000,000,000 and 100,000,000,000 |
| `1.0000000000001` (13 decimals) | 1,000,000,000,000: the 13th decimal is dropped without an error |
| `0.1 + 0.2` (that is `0.30000000000000004`) | 300,000,000,000: truncated without an error |

`ZephyrUtils.fromAtomicUnits(BigInteger)` returns a `double`. One atomic unit gives `1.0E-12`,
which `toAtomicUnits` then rejects. The round trip is exact only while the amount fits a double's
precision and prints in plain notation. For example, 123,456,789,012,345,678 atomic units come back
as 123,456,789,012,345,670. These results follow from `Double.toString`, which is specified Java
behaviour; they were run on desktop Java 21 on 2026-11-17, not on Android.

`ZephyrTxConfig.setAmount(String)` takes atomic units, not coins: `setAmount("1.5")` throws a
`NumberFormatException`, and `setAmount("1")` sets one atomic unit.

## ZephyrBalance

`ZephyrBalance` is a small map from asset name to amount:

```java examples/GuideBalances.java
ZephyrBalance balance = wallet.getBalance();
BigInteger zph = balance.get(ZephyrAssets.ZPH);   // BigInteger.ZERO if the asset is absent, never null
Set<String> assets = balance.getAssetTypes();     // sorted
Map<String, BigInteger> map = balance.toMap();    // unmodifiable, sorted by asset
```

Its JSON form is the plain object the core emits, `{"ZPH":123,"ZSD":0}`.

**Absent equals zero.** `get` returns zero for an asset the balance does not list, and `equals` and
`hashCode` follow the same rule: `{ZPH=1}` equals `{ZPH=1, ZRS=0}`. The rule exists because the
two sources of balances list zeros differently ([The four shapes](#the-four-shapes-of-the-balance-api)).
A balance from one should equal the same amounts from the other. `toMap()` still shows exactly
the entries that are there.

## The four shapes of the balance API

```java examples/GuideBalances.java
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

```java examples/GuideBalances.java
for (ZephyrAccount account : wallet.getAccounts(true)) {          // true: with subaddresses
  ZephyrBalance accountBalance = account.getBalance();             // only the assets it holds
  for (ZephyrSubaddress subaddress : account.getSubaddresses()) {
    BigInteger zrs = subaddress.getUnlockedBalance().get(ZephyrAssets.ZRS);
    System.out.println(account.getIndex() + "/" + subaddress.getIndex() + " " + accountBalance + " " + zrs);
  }
}
```

On the mainnet test run, a wallet holding 101 ZPH reported eight entries from `getBalance()`
(`ZPH=101000000000000` and seven zeros) and `{ZPH=101000000000000}` for its account. Per asset
they agree, and with the absent-equals-zero rule the two objects are equal.

## Accounts and subaddresses

A wallet has accounts, and each account has subaddresses. Account 0, subaddress 0 is the primary
address. A subaddress is a separate receiving address of the same wallet, for example one per
invoice or per payer:

```java examples/GuideBalances.java
ZephyrAccount savings = wallet.createAccount("Savings");
ZephyrSubaddress invoice = wallet.createSubaddress(0, "Invoice 0001");
wallet.setSubaddressLabel(0, invoice.getIndex(), "Invoice 0001, paid");
```

| Call | Returns |
|---|---|
| `getAccounts()`, `getAccounts(true)` | every account, with or without its subaddresses |
| `getAccount(accountIdx)` | one account |
| `getSubaddresses(accountIdx)`, `getSubaddress(accountIdx, subaddressIdx)` | the subaddresses of an account, or one |
| `createAccount(label)`, `createSubaddress(accountIdx, label)` | a new account or subaddress |
| `setSubaddressLabel(accountIdx, subaddressIdx, label)` | changes a label |

Reading accounts and their balances ran on mainnet. Creating accounts and subaddresses and setting
their labels are inherited from monero-java; not tested against Zephyr. Save after such changes
([When to save](02-wallets.md#when-to-save)).

**Labels are text from a wallet file.** A wallet opened from a file or from bytes you did not write
can carry any label. As advice: show labels as plain text, never as HTML (on Android, not through
`Html.fromHtml`), and keep them visually apart from your own text.

## Locked and unlocked

The **balance** counts everything the wallet owns, including funds that cannot be spent until they
unlock. The **unlocked balance** counts what can be spent now. Two things lock funds:

- **Received funds** (including the change of your own transactions) unlock 10 blocks after the
  block that contains them. At the network's two-minute block target that is about 20 min. On the
  mainnet test run the waits until the change of a transfer and the output of a conversion were
  spendable were 982 s, 1,203 s and 1,684 s.
- **Converted funds** follow the same rule: the ZRS from a mint was spendable 10 blocks after the
  mint was mined. [Conversions and the reserve](08-conversions-and-the-reserve.md) covers
  conversions.

Funds in the pool (sent to you but not in a block) count in the balance, not in the unlocked
balance. On the mainnet test run a receiving wallet showed `onBalancesChanged` with
`ZPH 10000000000000/0` (balance 10 ZPH, unlocked 0) as soon as the transfer reached the pool.

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
([Outputs in the pool carry no asset](04-threads-and-listeners.md#outputs-in-the-pool-carry-no-asset)).
