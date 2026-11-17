# 11. The RPC classes

This chapter covers `ZephyrDaemonRpc` and `ZephyrWalletRpc`, the classes that talk to a node's
RPC interface and to `zephyr-wallet-rpc` instead of running the wallet in the process.

## ZephyrDaemonRpc and the pricing record

The reserve figures derive from the oracle's pricing record, which every Zephyr block header
carries. `ZephyrDaemonRpc` reads it into `ZephyrBlockHeader.getPricingRecord()`, a
`ZephyrPricingRecord` with `spot`, `movingAverage`, `stable`, `stableMa`, `reserve`, `reserveMa`,
`reserveRatio`, `reserveRatioMa`, `yieldPrice` (each times 10^12), `timestamp` and `signature`.
An app that talks to a node's RPC directly can use it, for example to show the ZRS price
(`reserve`, `reserveMa`) that a mint would use.

## ZephyrWalletRpc

**The RPC classes are a first pass.** `ZephyrWalletRpc` serves the base coin; its per-asset
balance calls, the per-subaddress map, `getReserveInfo` and `getCirculatingSupply` throw a clear
"not supported" error until the port to `zephyr-wallet-rpc` is done. `ZephyrDaemonRpc` reads the
pricing record of block headers. The full wallet (`ZephyrWalletFull`) is the complete API.
Through the RPC classes the two core quirks above look different: `zephyr-wallet-rpc` returns
the `checkTxKey` amount per asset and the asset of every output, which this first pass does not
read yet, so `getReceivedAmount()` comes back `null` and the outputs carry no asset.
*Status:* planned work (the port to `zephyr-wallet-rpc`).

**`isClosed()` of `ZephyrWalletRpc` and error code -13.** monero-java recognises a closed RPC
wallet by error code -8 with "No wallet file"; `zephyr-wallet-rpc` v2.3.0 answers -13, so
`isClosed()` returned `false` for a closed wallet. *Status:* fixed on 2026-11-07, `isClosed()`
accepts both codes.

## Dependencies

**jeromq** is a compile-only dependency. Only `ZephyrWalletRpc`'s ZMQ listener uses it; an app that
uses that listener adds `org.zeromq:jeromq` itself. The shipped R8 rules carry a `-dontwarn` for
it, so an app without jeromq builds cleanly.
