# 8. Conversions and the reserve

This chapter covers the conversions between the assets and the reserve whose state decides which
of them the chain accepts.

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

The audit pairs (V1 → V2) and the V1 names exist in the code, but since hard fork 11 the chain
accepts neither, so on mainnet only the ten pairs above work.

## The reserve ratio

The ratio rules depend on the state of the reserve, which changes with the price. On 2026-11-06
the reserve ratio was 2.64, so `MINT_STABLE` and `REDEEM_RESERVE` were refused; `MINT_RESERVE`,
`REDEEM_STABLE` and the yield pairs could be built. An app must expect `createTxs` to throw
"Reserve ratio not satisfied" (the message names the ratio the conversion would produce) and
should check `getReserveInfo()` ([Reserve info](#reserve-info)) before it offers a
conversion.

Note that the chain checks the ratio **after** the conversion, with the conversion's own amounts
added, so a ratio just above 4.0 can still refuse a large `MINT_STABLE`. The exception from the
core's message ("New reserve ratio would be ...") is the authoritative answer.

## Reserve info

Zephyr's stable asset is backed by a reserve of ZPH. Whether a conversion is allowed depends on the
state of that reserve ([The ten pairs](#the-ten-pairs)), and a wallet app
usually shows it. Two wallet calls report it: `getReserveInfo()` and `getCirculatingSupply()`.
Both ask the node; they are not computed from the wallet's own outputs.

Both need a connected wallet. They are part of the full wallet only; `ZephyrWalletRpc` rejects
them until the RPC port ("Not supported by ZephyrWalletRpc until the RPC port: reserve info").

## getReserveInfo()

```java
ZephyrReserveInfo info = wallet.getReserveInfo();
BigDecimal ratio = info.getReserveRatio();       // 2.33 means 233 %
```

The core computes the values from the circulating supply and the pricing record of the block
below the node's current height. Amounts are `BigInteger` atomic units (10^12 per unit) and can
exceed 2^64, because the core computes them as 128-bit integers; the two ratios are `BigDecimal`
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

**Do not divide by `assetsMa` or `equityMa`.** In the core's code path for every hard fork after
version 4 they are set to 0, and the mainnet test runs (hard fork 11) reported 0 every time. The
moving average that matters for the conversion rules is `reserveRatioMa`, which is populated.

Two more edge cases come from the same code. When no ZSD circulates, both ratios are 0 (not
infinite). When the core cannot get the pricing record of the tip, it returns before computing,
and every value except `hfVersion` stays 0.

An example from the mainnet test run, at wallet height 902123, hard fork 11 (amounts shown in
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

With a spot ratio of 2.33 and a moving average of 2.62, both below 4.0, `MINT_STABLE` and
`REDEEM_RESERVE` were refused at that time, and `MINT_RESERVE` (below 8.0) was allowed.

## getCirculatingSupply()

```java
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
properties of the reserve. Older names may appear on other networks or heights; the core itself
accepts both eras when it reads the map (`ZEPH` or `DJED` for the reserve, `ZEPHUSD` or `ZSD` and
so on).

On the mainnet test run at height 902123 the six values were, in whole units: ZPH 12,356,073.23,
ZSD 362,406.29, ZRS 3,229,813.95, ZYS 157,437.68, DJED 4,926,112.04 and YIELD 313,963.17.

## Building a conversion

A conversion is a send with two different currencies, usually to your own address:

```java
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

### How the mint amount is computed (ZPH → ZRS)

The chain's formula for `MINT_RESERVE` takes the reserve coin price `P = max(reserve, reserve_ma)`
from the pricing record (the price of one ZRS in ZPH, times 10^12) and computes, in integers:

```text
rate     = 10^24 / P                       (ZRS per ZPH, times 10^12)
rate     = rate - rate / 100               (the 1 % conversion fee)
rate     = rate - rate mod 10^4
minted   = amount * rate / 10^12
```

Both mints of the mainnet test run match it exactly, with the pricing record at the wallet's tip
(height h0 − 1, where h0 was the node's height when the transaction was built) and
`reserve_ma` as the larger price both times:

| Mint | Pricing record | `reserve_ma` | Rate after fee | Minted |
|---|---|---|---|---|
| 50 ZPH with `createTxs` | 902152 | 940987350000 | 1.0520864 ZRS per ZPH | 52.60432 ZRS |
| 40.9987 ZPH with `sweepUnlocked` | 902163 | 939802020000 | 1.05341335 ZRS per ZPH | 43.188577912645 ZRS |

The other conversions use their own functions next to this one in the same file; they were not
exercised on mainnet in this project, because the reserve ratio did not allow them or no ZSD was
at hand.

## Converting everything at once

It takes the same currency pair, so it can also convert everything at once:

```java
List<ZephyrTxWallet> txs = wallet.sweepUnlocked(new ZephyrTxConfig()
    .setAddress(wallet.getPrimaryAddress())
    .setSourceCurrency(ZephyrAssets.ZPH)
    .setDestinationCurrency(ZephyrAssets.ZRS)
    .setAccountIndex(0)
    .setRelay(true));
```

On the mainnet test run this left exactly 0 ZPH: 40.9987 ZPH was converted, and the unconvertible
residue went into the fee (0.0005346 ZPH, against 0.0004574 for the 50 ZPH `createTxs` mint).

## What was tested

**Conversions other than ZPH → ZRS are not tested on mainnet.** ZPH transfers, a ZPH sweep, a ZRS
sweep and ZPH → ZRS mints with `createTxs` and with `sweepUnlocked` ran on mainnet with real
funds. `MINT_STABLE` and `REDEEM_RESERVE` were refused by the reserve ratio at the time, and no ZSD
or ZYS was at hand for the other pairs. They use the same code path in zephyr-java (only the pair
differs), but they have not been seen working.
