# 11. The RPC classes

`ZephyrDaemonRpc` talks to a node's RPC interface, and `ZephyrWalletRpc` to a `zephyr-wallet-rpc`
process instead of running the wallet in your own process. This chapter covers what each one does
in `2.3.0-sec.2-java.4`, their errors, their TLS and their dependencies. The full wallet,
`ZephyrWalletFull`, is the complete API.

The blocks marked "Java, compiled" in this chapter come from `src/test/java/examples/GuideRpc.java`.

## ZephyrDaemonRpc and the pricing record

The reserve figures derive from the oracle's pricing record, which every Zephyr block header
carries. `ZephyrDaemonRpc` reads it into `ZephyrBlockHeader.getPricingRecord()`, a
`ZephyrPricingRecord` with `spot`, `movingAverage`, `stable`, `stableMa`, `reserve`, `reserveMa`,
`reserveRatio`, `reserveRatioMa`, `yieldPrice` (each times 10^12), `timestamp` and `signature`.
An app that talks to a node's RPC directly can use it, for example to show the ZRS price
(`reserve`, `reserveMa`) that a mint would use:

```java examples/GuideRpc.java
ZephyrDaemonRpc daemon = new ZephyrDaemonRpc(nodeUri);
ZephyrPricingRecord record = daemon.getLastBlockHeader().getPricingRecord();
return record.getReserve();  // the price of one ZRS in ZPH, times 10^12
```

`new ZephyrDaemonRpc(uri, username, password)` is for a node with RPC login. Block headers with
their pricing record were read from a mainnet node on the desktop, and `ZephyrDaemonRpc` read a
mainnet node on the Android emulator. The calls of `ZephyrDaemonRpc` other than the node's
information and block headers are inherited from monero-java; not tested against Zephyr.

## ZephyrWalletRpc

**The RPC classes are a first pass.** `ZephyrWalletRpc` serves the base coin. Its per-asset
balance calls, the per-subaddress balance map, `getReserveInfo` and `getCirculatingSupply` throw a
`ZephyrError` until the port to `zephyr-wallet-rpc` is done, for example:

```text
Not supported by ZephyrWalletRpc until the RPC port: reserve info
```

The other messages end in "per-asset balances", "the per-subaddress balance map" and "circulating
supply". *Status:* planned work (the port to `zephyr-wallet-rpc`).

Through the RPC classes the two core quirks of the history look different
([checkTxKey returns amount 0](06-history.md#checktxkey-returns-amount-0),
[getOutputs() reports no asset](06-history.md#getoutputs-reports-no-asset)).
`zephyr-wallet-rpc` returns the `checkTxKey` amount per asset and the asset of every output, which
this first pass does not read. So `getReceivedAmount()` comes back `null` and the outputs carry no
asset.

**`isClosed()` and error code -13.** monero-java recognises a closed RPC wallet by error code -8
with "No wallet file"; `zephyr-wallet-rpc` v2.3.0 answers -13. `isClosed()` returned `false` for a
closed wallet until the fix of 2026-11-07; it now accepts both codes.

## ZephyrRpcError

For an RPC error or an HTTP error, the RPC classes throw `zephyr.common.ZephyrRpcError`, a
`ZephyrError` with three more facts:

- `getCode()` is the RPC error code, or the HTTP status for an HTTP error;
- `getRpcMethod()` is the RPC method, `null` for an HTTP error;
- `getRpcParams()` is the request's parameters, `null` for an HTTP error.

A failure to connect is not a `ZephyrRpcError`: it arrives as a plain `ZephyrError` whose message is
the cause's text (read in the source). An HTTP error's message holds the HTTP status and the body
of the answer, which is text from the server.

Its `toString()` appends the method and the parameters as JSON, and the parameters can hold the
wallet's password or the node's login password
([Logging safely](09-errors.md#logging-safely)). Catch `ZephyrError` and log the method, the code and
the message:

```java examples/GuideRpc.java
if (e instanceof ZephyrRpcError) return "RPC " + ((ZephyrRpcError) e).getRpcMethod() + " failed: " + e.getCode() + " " + e.getMessage();
return "no RPC answer: " + e.getMessage();  // for example no connection: a plain ZephyrError
```

## TLS and Android

The RPC classes use the trust store of the Java runtime. `ZephyrTls` does not affect them; it is
for the native wallet ([TLS to a node](03-connecting-to-a-node.md#tls-to-a-node)).

They send their requests through the Apache HTTP client, which needs `java.time`. On Android below
API 26 an app needs core library desugaring for them
([The RPC classes below API 26](10-android.md#the-rpc-classes-below-api-26)).

## Dependencies

The RPC classes use the Apache HTTP client 5 (`httpclient5` 5.2.1) and Jackson 2.13.5, which both
artifacts bring.

**jeromq** is a compile-only dependency. Only `ZephyrWalletRpc`'s ZMQ listener uses it; an app that
uses that listener adds `org.zeromq:jeromq` itself. The shipped R8 rules carry a `-dontwarn` for
it, so an app without jeromq builds cleanly.
