# 4. Sending and converting

In Zephyr, a transfer and a conversion are the same operation. Both are built with `createTxs` (or
`sweepUnlocked`) and a `ZephyrTxConfig`; what makes one a transfer and the other a conversion is
the **currency pair** on the config. There are no separate conversion methods.
<!-- ZEPHYR-DELTA §1 "Conversions ... are ordinary createTxs and sweepUnlocked calls with two different currencies" -->

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
<!-- zephyr-cpp monero_wallet_full.cpp:2100 ("Must specify account index to send from") and :2102 ("Must specify source currency"); ZEPHYR-DELTA §1 "Tx config"; QUESTIONS R8-8 (setAccountIndex(0) on every createTxs and sweepUnlocked) -->

The pair holds for the whole transaction and all its destinations. `ZephyrDestination` and the
transfers have an `assetType` field too, but it only **reports** the asset of a transfer in the
history. Setting it on a destination does not select anything: the core ignores it when sending
and takes the pair from the config.
<!-- ZEPHYR-DELTA §1 "create_txs passes destination_currency to validate_transfer and ignores the destination's own asset_type when sending (that field only reports, on transfers)" -->

## Valid pairs

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
<!-- ZEPHYR-DELTA §1 "Valid pairs (zephyr get_tx_type)" and the reserve-ratio paragraph; zephyr cryptonote_tx_utils.cpp:478 (get_tx_type) and :750-1005 (reserve_ratio_satisfied, two branches by hard fork with the same thresholds: RESERVE_RATIO_MIN 4 * COIN / 4.0, RESERVE_RATIO_MAX 8 * COIN / 8.0; MINT_RESERVE allowed below 100 * COIN stables; MINT_YIELD/REDEEM_YIELD return true); zephyr-cpp monero_wallet_full.cpp:2112 ("not a valid tx type") -->

The audit pairs (V1 → V2) and the V1 names exist in the code, but since hard fork 11 the chain
accepts neither, so on mainnet only the ten pairs above work.
<!-- ZEPHYR-DELTA §1 (AUDIT_* only in HF 8 and 10; HF 11 rejects audit transactions and V1 outputs) -->

The ratio rules depend on the state of the reserve, which changes with the price. On 2026-11-06
the reserve ratio was 2.64, so `MINT_STABLE` and `REDEEM_RESERVE` were refused; `MINT_RESERVE`,
`REDEEM_STABLE` and the yield pairs could be built. An app must expect `createTxs` to throw
"Reserve ratio not satisfied" (the message names the ratio the conversion would produce) and
should check `getReserveInfo()` ([chapter 5](05-reserve-and-supply.md)) before it offers a
conversion.
<!-- ZEPHYR-DELTA §1 reserve-ratio paragraph (2.64 on 2026-11-06); cryptonote_tx_utils.cpp:826-1001 (error_reason strings name the new ratio) -->

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
<!-- ZephyrWallet.java Javadoc of createTx/createTxs ("relay ... (default false)"), relayTx -->

**Fees are paid in the source asset.** A ZPH transfer pays its fee in ZPH, a ZRS transfer in ZRS,
and a ZPH → ZRS mint in ZPH. On the R8 mainnet run a 10 ZPH transfer paid 0.000308 ZPH, a 50 ZPH
mint 0.0004574 ZPH and a ZRS sweep 0.0004456 ZRS.
<!-- ZEPHYR-DELTA §3 "Every fee is paid in the source asset"; evidence/r8b/funds-r8.md (test 1/2 fee 0.000308, test 4 fee 0.0004574, test 7 fee 0.0004456 ZRS) -->

## Conversions

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
<!-- wallet2.cpp:10404 (multiples of 10^8, "permit at most 4 decimal places"); wallet2.cpp:11420-11424 (sweep rounds down and adds the remainder to the fee); wallet2.cpp:10377-10382 (pricing record of get_blockchain_current_height() - 1); QUESTIONS R8-8 (minted amount exposed as total_amount_dest, monero_wallet_full.cpp:634, 654, 2229, 2482); evidence/r8b/funds-r8.md observation 5 (no subaddress index until confirmed) -->

### How the mint amount is computed (ZPH → ZRS)

The chain's formula for `MINT_RESERVE` takes the reserve coin price `P = max(reserve, reserve_ma)`
from the pricing record (the price of one ZRS in ZPH, times 10^12) and computes, in integers:

```
rate     = 10^24 / P                       (ZRS per ZPH, times 10^12)
rate     = rate - rate / 100               (the 1 % conversion fee)
rate     = rate - rate mod 10^4
minted   = amount * rate / 10^12
```
<!-- zephyr cryptonote_tx_utils.cpp:1186-1211 (zeph_to_zephrsv: max(pr.reserve, pr.reserve_ma), COIN*COIN / price, 1 % fee from HF_VERSION_V5, rate -= rate % 10000, amount * rate / COIN) -->

Both mints of the R8 mainnet run match it exactly, with the pricing record at the wallet's tip
(height h0 − 1, where h0 was the node's height when the transaction was built) and
`reserve_ma` as the larger price both times:

| Mint | Pricing record | `reserve_ma` | Rate after fee | Minted |
|---|---|---|---|---|
| 50 ZPH with `createTxs` | 902152 | 940987350000 | 1.0520864 ZRS per ZPH | 52.60432 ZRS |
| 40.9987 ZPH with `sweepUnlocked` | 902163 | 939802020000 | 1.05341335 ZRS per ZPH | 43.188577912645 ZRS |
<!-- evidence/r8b/funds-r8.md "The conversion formula" (902152 reserve_ma 940987350000 -> 52.604320000000; implied rate 1052086400000) and test 6 (40.9987 ZPH -> 43.188577912645 ZRS, formula match at 902163 = h0 − 1); evidence/r8b/run-funds-2.txt:220-225 (902163 reserve 859384000000, reserve_ma 939802020000 -> 43188577912645); both rates recomputed with the formula above (1052086400000, 1053413350000) -->

The other conversions use their own functions next to this one in the same file; they were not
exercised on mainnet in this project, because the reserve ratio did not allow them or no ZSD was
at hand.
<!-- PLAN §5 R8 ("MINT_STABLE, REDEEM_* and the yield pairs wait for a ratio above 4.0 or for ZSD") -->

## Sweeping

`sweepUnlocked` sends everything unlocked from an account (or chosen subaddresses) to one address.
It takes the same currency pair, so it can also convert everything at once:

```java
List<ZephyrTxWallet> txs = wallet.sweepUnlocked(new ZephyrTxConfig()
    .setAddress(wallet.getPrimaryAddress())
    .setSourceCurrency(ZephyrAssets.ZPH)
    .setDestinationCurrency(ZephyrAssets.ZRS)
    .setAccountIndex(0)
    .setRelay(true));
```

On the R8 run this left exactly 0 ZPH: 40.9987 ZPH was converted, and the unconvertible residue
went into the fee (0.0005346 ZPH, against 0.0004574 for the 50 ZPH `createTxs` mint). Set the
account index: without one, the core's subaddress selection for a sweep has a slip that sweeps
every subaddress of a funded account.
<!-- ZEPHYR-DELTA §3 ("sweepUnlocked with two currencies works ... folds wallet2's unconvertible residue into the fee"; "sweep_unlocked builds the subaddress list with iter2 but tests iter", monero_wallet_full.cpp:2320-2321); evidence/r8b/funds-r8.md observation 2 -->

`sweepOutput` (one output by key image) is a different path and does not work for V2 assets; see
[chapter 8](08-known-limitations.md).

## The history of a conversion

After a conversion is confirmed, `getTxs()` and `getTransfers()` show it as follows:

- The **outgoing transfer's** amount is the source amount (50 ZPH), in the source asset.
- The outgoing **destination** shows the **destination amount** in the destination currency
  (52.60432 ZRS). On the transaction that `createTxs` returned, the same destination still shows
  the source amount (50 ZPH). Code that compares the two must expect that.
- An **incoming transfer** in the destination currency carries the minted amount, with account and
  subaddress set once confirmed.
<!-- ZEPHYR-DELTA §3 conversion bullets; evidence/r8b/funds-r8.md observations 4 and 5 (monero_wallet_full.cpp:246, 345 fill the destination from d.dest_amount) -->

The converted funds unlock like received funds, 10 blocks after the conversion is mined
([chapter 3](03-assets-and-balances.md#locked-and-unlocked)).
