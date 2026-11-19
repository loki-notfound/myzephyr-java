# 11. The RPC classes

`ZephyrDaemonRpc` talks to a node's RPC interface, through `ZephyrRpcConnection`. This chapter
covers what it does in `2.3.0-sec.2-java.5`, its errors, its TLS and its dependencies, and the
wallet RPC client that releases up to `2.3.0-sec.2-java.4` had. The full wallet,
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

## The wallet RPC client was removed

Releases up to `2.3.0-sec.2-java.4` had `ZephyrWalletRpc`, a client for a `zephyr-wallet-rpc`
process. It was a first pass that served the base coin only. `2.3.0-sec.2-java.5` removes it,
with `zephyr.common.SslOptions` and the jeromq dependency that only its ZMQ listener used. Nobody
was known to use it.

Use `ZephyrWalletFull`, which runs the wallet in your process and has the whole API. An app that
must talk to a `zephyr-wallet-rpc` process can send its JSON-RPC requests with
`ZephyrRpcConnection.sendJsonRequest`, which stays. `ZephyrDaemonRpc`, `ZephyrRpcConnection` and
`ZephyrRpcError` stay as they were.

## ZephyrRpcError

For an RPC error or an HTTP error, the RPC classes throw `zephyr.common.ZephyrRpcError`, a
`ZephyrError` with three more facts:

- `getCode()` is the RPC error code, or the HTTP status for an HTTP error;
- `getRpcMethod()` is the RPC method, `null` for an HTTP error;
- `getRpcParams()` is the request's parameters, `null` for an HTTP error.

A failure to connect is not a `ZephyrRpcError`: it arrives as a plain `ZephyrError` whose message is
the cause's text (read in the source). An HTTP error's message holds the HTTP status and the body
of the answer, which is text from the server.

Its `toString()` appends the method and the parameters as JSON. From `2.3.0-sec.2-java.5` on,
the values of secret parameters such as passwords print as `<redacted>`; `getRpcParams()` still
holds them ([Logging safely](09-errors.md#logging-safely)). Catch `ZephyrError` and log the
method, the code and the message:

```java examples/GuideRpc.java
if (e instanceof ZephyrRpcError) return "RPC " + ((ZephyrRpcError) e).getRpcMethod() + " failed: " + e.getCode() + " " + e.getMessage();
return "no RPC answer: " + e.getMessage();  // for example no connection: a plain ZephyrError
```

## TLS and Android

`ZephyrDaemonRpc` uses the trust store of the Java runtime. `ZephyrTls` does not affect them; it is
for the native wallet ([TLS to a node](03-connecting-to-a-node.md#tls-to-a-node)).

It sends its requests through the Apache HTTP client, which needs `java.time`. On Android below
API 26 an app needs core library desugaring for it
([The RPC classes below API 26](10-android.md#the-rpc-classes-below-api-26)).

## Dependencies

The RPC classes use the Apache HTTP client 5 (`httpclient5` 5.2.1) and Jackson 2.13.5, which both
artifacts bring. The jeromq dependency is gone: up to `2.3.0-sec.2-java.4` it was compile-only,
for the wallet RPC client's ZMQ listener.
