# 5. Reserve info and circulating supply

Zephyr's stable asset is backed by a reserve of ZPH. Whether a conversion is allowed depends on the
state of that reserve ([chapter 4](04-sending-and-converting.md#valid-pairs)), and a wallet app
usually shows it. Two wallet calls report it: `getReserveInfo()` and `getCirculatingSupply()`.
Both ask the node; they are not computed from the wallet's own outputs.
<!-- zephyr-cpp monero_wallet_full.cpp:1678-1704 (get_reserve_info takes the daemon height and the pricing record from the node); zephyr wallet2.cpp:10165-10186 (get_circulating_supply and get_pricing_record_history from the daemon) -->

Both need a connected wallet. They are part of the full wallet only; `ZephyrWalletRpc` rejects
them until the RPC port ("Not supported by ZephyrWalletRpc until the RPC port: reserve info").
<!-- evidence/r8/run-4.txt:53 (verbatim message); register D11 -->

## getReserveInfo()

```java
ZephyrReserveInfo info = wallet.getReserveInfo();
BigDecimal ratio = info.getReserveRatio();       // 2.33 means 233 %
```

The core computes the values from the circulating supply and the pricing record of the block
below the node's current height. Amounts are `BigInteger` atomic units (10^12 per unit) and can
exceed 2^64, because the core computes them as 128-bit integers; the two ratios are `BigDecimal`
with the full precision of the core's `double`.
<!-- zephyr-cpp monero_wallet_full.cpp:1695-1703 (pricing record at bc_height - 1); ZephyrReserveInfo.java class Javadoc (decimal strings from the bridge, 128-bit); PLAN §5 R3/R4 results (uint128 and ratios as decimal strings; test with a uint128 above 2^64) -->

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
<!-- zephyr cryptonote_tx_utils.cpp:538-578 (get_circulating_asset_amounts: zeph_reserve from DJED/ZEPH, num_stables from ZSD/ZEPHUSD, num_reserves from ZRS/ZEPHRSV; get_yield_asset_amounts: ZYS/ZYIELD and YIELD/ZYIELDRSV) and :677-704 (hf_version > HF_VERSION_PR_UPDATE: assets = zeph_reserve * spot / COIN, liabilities = num_stables, equity = max(assets - liabilities, 0), reserve_ratio = assets / num_stables, reserve_ratio_ma from the pricing record history, assets_ma = 0, equity_ma = 0) -->

**Do not divide by `assetsMa` or `equityMa`.** In the core's code path for every hard fork after
version 4 they are set to 0, and the R8 mainnet runs (hard fork 11) reported 0 every time. The
moving average that matters for the conversion rules is `reserveRatioMa`, which is populated.
<!-- cryptonote_tx_utils.cpp:700-701 (assets_ma = 0; equity_ma = 0); ZEPHYR-DELTA §1 ("assets_ma and equity_ma were 0 on mainnet at height 902102 (R8)"); evidence/r8/run-4.txt:31-33 -->

Two more edge cases come from the same code. When no ZSD circulates, both ratios are 0 (not
infinite). When the core cannot get the pricing record of the tip, it returns before computing,
and every value except `hfVersion` stays 0.
<!-- cryptonote_tx_utils.cpp:679-689 (num_stables == 0: ratios 0); zephyr-cpp monero_wallet_full.cpp:1696-1700 (hf_version set first; return when get_pricing_record fails); zephyr_jni_bridge.cpp:1074-1086 (every value initialised to 0) -->

An example from the R8 run on mainnet, at wallet height 902123, hard fork 11 (amounts shown in
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
<!-- evidence/r8/run-4.txt:26-39 (verbatim atomic values, divided by 10^12 and rounded here) -->

With a spot ratio of 2.33 and a moving average of 2.62, both below 4.0, `MINT_STABLE` and
`REDEEM_RESERVE` were refused at that time, and `MINT_RESERVE` (below 8.0) was allowed.
<!-- cryptonote_tx_utils.cpp:750-1005 (RESERVE_RATIO_MIN 4.0, RESERVE_RATIO_MAX 8.0 in both branches); ZEPHYR-DELTA §1 reserve-ratio paragraph -->

Note that the chain checks the ratio **after** the conversion, with the conversion's own amounts
added, so a ratio just above 4.0 can still refuse a large `MINT_STABLE`. The exception from the
core's message ("New reserve ratio would be ...") is the authoritative answer.
<!-- cryptonote_tx_utils.cpp:750-1005 (reserve_ratio_satisfied with tally_zeph, tally_stables, tally_reserves; error_reason "New reserve ratio would be ...") -->

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
<!-- ZEPHYR-DELTA §1 ("get_circulating_supply() returns six entries on mainnet (observed 2026-11-07, R8)"); evidence/r8/integration-r8.md:144-148 (both runs: DJED equals the ZPH reserve, YIELD equals the yield reserve) -->

`DJED` and `YIELD` are not assets a wallet can hold. A user interface that lists "the supply of
every asset" should take the four V2 keys (`ZephyrAssets.V2`) and treat the other two as
properties of the reserve. Older names may appear on other networks or heights; the core itself
accepts both eras when it reads the map (`ZEPH` or `DJED` for the reserve, `ZEPHUSD` or `ZSD` and
so on).
<!-- ZephyrWallet.getCirculatingSupply Javadoc; cryptonote_tx_utils.cpp:538-578 (both names accepted) -->

On the R8 run at height 902123 the six values were, in whole units: ZPH 12,356,073.23,
ZSD 362,406.29, ZRS 3,229,813.95, ZYS 157,437.68, DJED 4,926,112.04 and YIELD 313,963.17.
<!-- evidence/r8/run-4.txt:40 -->

## Pricing records on block headers

The reserve figures derive from the oracle's pricing record, which every Zephyr block header
carries. `ZephyrDaemonRpc` reads it into `ZephyrBlockHeader.getPricingRecord()`, a
`ZephyrPricingRecord` with `spot`, `movingAverage`, `stable`, `stableMa`, `reserve`, `reserveMa`,
`reserveRatio`, `reserveRatioMa`, `yieldPrice` (each times 10^12), `timestamp` and `signature`.
An app that talks to a node's RPC directly can use it, for example to show the ZRS price
(`reserve`, `reserveMa`) that a mint would use.
<!-- register D12 (ZephyrPricingRecord on the block header, parsed by ZephyrDaemonRpc); ZephyrPricingRecord.java getters; evidence/r8/integration-r8.md:96-100 (values such as reserve 891934760000, reserve ratio 2408501170000) -->
