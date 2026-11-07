# 5. Reserve info and circulating supply

Zephyr's stable asset is backed by a reserve of ZPH. Whether a conversion is allowed depends on the
state of that reserve ([chapter 4](04-sending-and-converting.md#valid-pairs)), and a wallet app
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

Note that the chain checks the ratio **after** the conversion, with the conversion's own amounts
added, so a ratio just above 4.0 can still refuse a large `MINT_STABLE`. The exception from the
core's message ("New reserve ratio would be ...") is the authoritative answer.

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

## Pricing records on block headers

The reserve figures derive from the oracle's pricing record, which every Zephyr block header
carries. `ZephyrDaemonRpc` reads it into `ZephyrBlockHeader.getPricingRecord()`, a
`ZephyrPricingRecord` with `spot`, `movingAverage`, `stable`, `stableMa`, `reserve`, `reserveMa`,
`reserveRatio`, `reserveRatioMa`, `yieldPrice` (each times 10^12), `timestamp` and `signature`.
An app that talks to a node's RPC directly can use it, for example to show the ZRS price
(`reserve`, `reserveMa`) that a mint would use.
