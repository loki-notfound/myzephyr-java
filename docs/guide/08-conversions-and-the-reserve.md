# 8. Conversions and the reserve

A conversion is a send whose source and destination currencies differ. It is built with the same
`createTxs` or `sweepUnlocked` as a transfer ([Sending](07-sending.md)). This chapter covers which
conversions exist and the reserve whose state decides which of them the chain accepts.

The blocks marked "Java, compiled" in this chapter come from
`src/test/java/examples/GuideConversions.java`.

## The ten pairs

The core maps the pair to a transaction type and rejects every other pair with "not a valid tx
type":

| Source → destination | Transaction type | Ratio rule (checked by the chain after the conversion) |
|---|---|---|
| ZPH → ZPH | `TRANSFER` | none |
| ZSD → ZSD | `STABLE_TRANSFER` | none |
| ZRS → ZRS | `RESERVE_TRANSFER` | none |
| ZYS → ZYS | `YIELD_TRANSFER` | none |
| ZPH → ZSD | `MINT_STABLE` | spot **and** moving-average ratio at or above 4.0 |
| ZSD → ZPH | `REDEEM_STABLE` | the reserve's assets above zero |
| ZPH → ZRS | `MINT_RESERVE` | spot and moving-average ratio below 8.0 (no limit while fewer than 100 ZSD circulate) |
| ZRS → ZPH | `REDEEM_RESERVE` | spot and moving-average ratio at or above 4.0 |
| ZSD → ZYS | `MINT_YIELD` | none |
| ZYS → ZSD | `REDEEM_YIELD` | none |

The audit pairs (V1 → V2) and the V1 names exist in the code. Since hard fork 11 the chain accepts
neither, so on mainnet only the ten pairs above work.

## The reserve ratio

The ratio rules depend on the state of the reserve, which changes with the price. A reserve ratio
of 4.0 (400.00%) is the floor for `MINT_STABLE` and `REDEEM_RESERVE`, and 8.0 (800.00%) the
ceiling for `MINT_RESERVE`. On 2026-11-06 the reserve ratio was 2.64, so `MINT_STABLE` and
`REDEEM_RESERVE` were refused; `MINT_RESERVE`, `REDEEM_STABLE` and the yield pairs were allowed by
the ratio rules. On 2026-11-17 it was 1.849 (moving average 1.986), and the core refused the same two
pairs by their ratio.

The chain checks the ratio **after** the conversion, with the conversion's own amounts added. A
ratio slightly above 4.0 can therefore still refuse a large `MINT_STABLE`. An app should check
`getReserveInfo()` ([Reserve info](#reserve-info)) before it offers a conversion, and expect
`createTxs` to throw a `ZephyrError` anyway. The core's message names the ratio the conversion
would produce, and it is the authoritative answer. Observed on 2026-11-17 with `setRelay(false)`:

| Pair | Message |
|---|---|
| ZPH → ZSD | `Spot reserve ratio not satisfied. New reserve ratio would be 1.848989555682 which is less than minimum 4.0` |
| ZRS → ZPH | `Reserve ratio not satisfied. New reserve ratio would be 1.848989637479 which is less than the minimum 4.0` |

The two messages differ in their wording ("Spot reserve ratio not satisfied" against "Reserve
ratio not satisfied"). Both arrive as the same `ZephyrError` with a `null` code, like "not enough
money". Only the text tells them apart, and it can change with the core
([The messages a wallet meets](09-errors.md#the-messages-a-wallet-meets)). For a conversion the core checked
the decimal places first, then the ratio, then the funds. An amount of one atomic unit gave the
decimal-places message for every conversion pair ([Building a conversion](#building-a-conversion)).

## Reserve info

Zephyr's stable asset has a reserve of ZPH behind it. Whether a conversion is allowed depends on
the state of that reserve ([The ten pairs](#the-ten-pairs)), and a wallet app usually shows it.
Two wallet calls report it: `getReserveInfo()` and `getCirculatingSupply()`. Both ask the node;
they are not computed from the wallet's own outputs. Both need a full wallet connected to a node.

## getReserveInfo()

```java examples/GuideConversions.java
ZephyrReserveInfo info = wallet.getReserveInfo();
BigDecimal ratio = info.getReserveRatio();       // 2.33 means 233%
```

The core computes the values from the circulating supply and the pricing record of the block
below the node's current height. Amounts are `BigInteger` atomic units (10^12 per unit) and can
exceed 2^64, because the core computes them as 128-bit integers. The two ratios are `BigDecimal`
with the full precision of the core's `double`.

| Getter | JSON key | Meaning | Unit |
|---|---|---|---|
| `getZephReserve()` | `zeph_reserve` | ZPH held in the reserve | atomic ZPH |
| `getNumStables()` | `num_stables` | ZSD in circulation | atomic ZSD |
| `getNumReserves()` | `num_reserves` | ZRS in circulation | atomic ZRS |
| `getAssets()` | `assets` | the reserve's ZPH valued at the oracle's spot price | atomic units of the stable's unit (as ZSD) |
| `getAssetsMa()` | `assets_ma` | the same at the moving-average price; **0 on the current hard fork** | as `assets` |
| `getLiabilities()` | `liabilities` | the stable asset outstanding, equal to `num_stables` | as `assets` |
| `getEquity()` | `equity` | `assets − liabilities`, or 0 when negative | as `assets` |
| `getEquityMa()` | `equity_ma` | the moving-average equity; **0 on the current hard fork** | as `assets` |
| `getReserveRatio()` | `reserve_ratio` | `assets / liabilities` at the spot price | ratio |
| `getReserveRatioMa()` | `reserve_ratio_ma` | the moving average of that ratio over the pricing-record history | ratio |
| `getHfVersion()` | `hf_version` | the hard-fork version the node is on | number |
| `getZyieldCirc()` | `zyield_circ` | ZYS in circulation | atomic ZYS |
| `getZyieldRsv()` | `zyield_rsv` | the yield reserve (the supply's `YIELD` entry) | atomic units |

> [!WARNING]
> **Do not divide by `assetsMa` or `equityMa`.** In the core's code path for every hard fork after
> version 4 they are set to 0, and the mainnet test runs (hard fork 11) reported 0 every time. The
> moving average that matters for the conversion rules is `reserveRatioMa`, which is populated.

Two more edge cases come from the same code. When no ZSD circulates, both ratios and the two yield
values are 0 (not infinite). When the core cannot get the pricing record of the tip, it returns
before computing, and every value except `hfVersion` stays 0. Without a node that happens without
an exception. Treat a ratio of 0 as unknown, not as a ratio.

An example from the mainnet test run, at wallet height 902,123, hard fork 11 (amounts shown in
whole units here):

| Field | Value |
|---|---|
| ZPH reserve | 4,926,112.04 ZPH |
| Stables in circulation (liabilities) | 362,406.29 ZSD |
| Reserves in circulation | 3,229,813.95 ZRS |
| Assets | 845,297.43 |
| Equity | 482,891.14 |
| Reserve ratio | 2.3325 |
| Reserve ratio MA | 2.6213 |
| ZYS in circulation | 157,437.68 ZYS |
| Yield reserve | 313,963.17 |

The spot ratio was 2.33 (233.25%) and the moving average 2.62 (262.13%), both below 4.0. So
`MINT_STABLE` and `REDEEM_RESERVE` were refused at height 902,123, and `MINT_RESERVE` (below 8.0)
was allowed.

The values come from the oracle's pricing record, which every block header carries;
[ZephyrDaemonRpc and the pricing record](11-the-rpc-classes.md#zephyrdaemonrpc-and-the-pricing-record)
shows how to read it.

## getCirculatingSupply()

```java examples/GuideConversions.java
Map<String, BigInteger> supply = wallet.getCirculatingSupply();
```

The map comes from the node unchanged. On mainnet it has **six** keys, not four:

| Key | Meaning |
|---|---|
| `ZPH`, `ZSD`, `ZRS`, `ZYS` | the circulating supply of the four assets |
| `DJED` | the ZPH held in the reserve; equal to `getZephReserve()` |
| `YIELD` | the yield reserve; equal to `getZyieldRsv()` |

`DJED` and `YIELD` are not assets a wallet can hold. A user interface that lists "the supply of
every asset" should take the four V2 keys (`ZephyrAssets.V2`) and treat the other two as
properties of the reserve. Older names may appear on other networks or heights. The core itself
accepts both eras when it reads the map (`ZEPH` or `DJED` for the reserve, `ZEPHUSD` or `ZSD` and
so on).

On the mainnet test run at height 902,123 the six values were, in whole units: ZPH 12,356,073.23,
ZSD 362,406.29, ZRS 3,229,813.95, ZYS 157,437.68, DJED 4,926,112.04 and YIELD 313,963.17.

## Building a conversion

A conversion is a send with two different currencies, usually to your own address:

```java examples/GuideConversions.java
List<ZephyrTxWallet> txs = wallet.createTxs(new ZephyrTxConfig()
    .setAddress(wallet.getPrimaryAddress())
    .setAmount(new BigDecimal("50").movePointRight(12).toBigIntegerExact())   // in ZPH
    .setSourceCurrency(ZephyrAssets.ZPH)
    .setDestinationCurrency(ZephyrAssets.ZRS)
    .setAccountIndex(0));
```

Four rules come from the core:

1. **The amount is the source amount** (here: 50 ZPH). The destination amount follows from the
   price.
2. **It must be a multiple of 10^8 atomic units**, that is, at most four decimal places. Otherwise
   the core throws "Mint/redeem TX amounts permit at most 4 decimal places". Round down before you
   build. `sweepUnlocked` is the exception: it rounds each converted amount down itself and adds
   the remainder to the fee.
3. **The price comes from the wallet's tip.** The core uses the pricing record of the block below
   the wallet's current height when it builds the transaction, so sync right before you convert.
4. **The minted amount is known before you relay.** The returned transaction carries an incoming
   transfer in the destination currency whose amount is the converted amount; show it to the user
   as the quote. Until the transaction is confirmed that transfer has no subaddress index.

Rounding down to four decimal places:

```java examples/GuideConversions.java
static BigInteger roundDownForConversion(BigInteger amount) {
  BigInteger step = BigInteger.TEN.pow(8);
  return amount.subtract(amount.mod(step));
}
```

**A conversion's pricing record must be recent** (read in the code; not run). The chain accepts a
conversion only while its pricing record is at most 10 blocks below the current height, about
20 min at the two-minute block target. The node checks it when the transaction enters the pool,
when a block is assembled and when a block is validated. The pool also drops a waiting conversion
once its record has become too old. For an app: build a conversion right after a sync, relay it
promptly, and rebuild a quote the user has not confirmed within a few minutes. A wallet that is
more than about 10 blocks behind builds conversions the node refuses.

## Fees of a conversion

A conversion pays its fee in the source asset, like a transfer: a ZPH → ZRS mint pays in ZPH. On
the mainnet test run a 50 ZPH mint with `createTxs` paid 0.0004574 ZPH. A mint with
`sweepUnlocked` paid 0.0005346 ZPH, because the unconvertible residue went into the fee
([Converting everything at once](#converting-everything-at-once)).

For a conversion from another asset the wallet computes the fee in ZPH and converts it into the
source asset (read in the core's code; not run). It uses a moving-average price of the pricing
record: ZSD at `stable_ma`, ZRS at `reserve_ma`, and ZYS through ZSD and `yield_price`. The node checks
it back the same way.

### How the mint amount is computed (ZPH → ZRS)

The chain's formula for `MINT_RESERVE` takes the reserve coin price `P = max(reserve, reserve_ma)`
from the pricing record (the price of one ZRS in ZPH, times 10^12) and computes, in integers:

```text
rate     = 10^24 / P                       (ZRS per ZPH, times 10^12)
rate     = rate - rate / 100               (the 1% conversion fee)
rate     = rate - rate mod 10^4
minted   = amount * rate / 10^12
```

Both mints of the mainnet test run match it exactly. The pricing record was the one at the
wallet's tip, height h0 − 1, where h0 was the node's height when the transaction was built.
`reserve_ma` was the larger price both times:

| Mint | Pricing record | `reserve_ma` | Rate after fee | Minted |
|---|---|---|---|---|
| 50 ZPH with `createTxs` | 902,152 | `940987350000` | 1.0520864 ZRS per ZPH | 52.60432 ZRS |
| 40.9987 ZPH with `sweepUnlocked` | 902,163 | `939802020000` | 1.05341335 ZRS per ZPH | 43.188577912645 ZRS |

The other conversions use their own functions next to this one in the same file. They were not
exercised on mainnet in this project, because the reserve ratio did not allow them or no ZSD was
at hand.

## Converting everything at once

`sweepUnlocked` takes the same currency pair as `createTxs`, so it can also convert everything at
once. Build without relaying, show the total fee and the quote, then relay every transaction. The
returned transactions carry the converted amount in an incoming transfer, as for `createTxs` (read
in the core's code):

```java examples/GuideConversions.java
List<ZephyrTxWallet> txs = wallet.sweepUnlocked(new ZephyrTxConfig()
    .setAddress(wallet.getPrimaryAddress())
    .setSourceCurrency(ZephyrAssets.ZPH)
    .setDestinationCurrency(ZephyrAssets.ZRS)
    .setAccountIndex(0));                 // relay defaults to false: nothing is sent yet
BigInteger fee = BigInteger.ZERO;
BigInteger minted = BigInteger.ZERO;
for (ZephyrTxWallet tx : txs) {
  fee = fee.add(tx.getFee());             // in ZPH
  if (tx.getIncomingTransfers() == null) continue;
  for (ZephyrIncomingTransfer transfer : tx.getIncomingTransfers()) minted = minted.add(transfer.getAmount());  // in ZRS
}
System.out.println("fee " + GuideBalances.formatFull(fee) + " ZPH, quote " + GuideBalances.formatFull(minted) + " ZRS");
return wallet.relayTxs(txs);              // only after the user has agreed to fee and quote
```

The mainnet test run used `setRelay(true)` instead, which builds and relays in one call without a
review. That run left exactly 0 ZPH. 40.9987 ZPH was converted, and the unconvertible
residue went into the fee (0.0005346 ZPH, against 0.0004574 for the 50 ZPH `createTxs` mint).

## What was tested

**Conversions other than ZPH → ZRS are not tested on mainnet.** ZPH transfers, a ZPH sweep, a ZRS
sweep and ZPH → ZRS mints with `createTxs` and with `sweepUnlocked` ran on mainnet with real
funds. `MINT_STABLE` and `REDEEM_RESERVE` were refused by the reserve ratio at the time, and no ZSD
or ZYS was at hand for the other pairs. They use the same code path in zephyr-java (only the pair
differs), but they have not been seen working.

On 2026-11-17 the six conversion pairs reached the core's checks on a wallet without funds
([The reserve ratio](#the-reserve-ratio)). None of those conversions was built, and nothing was
relayed.
