# 9. Errors and exceptions

This chapter covers what a wallet app sees when something goes wrong. It names the exception
types and where their messages come from, lists the messages a wallet meets, and shows how to log
them safely. It ends with the core's own log.

## ZephyrError

`zephyr.common.ZephyrError` is the exception most calls throw. It extends `RuntimeException`, so
the compiler does not ask you to catch it. It carries a message and an optional `Integer` code:

- `getMessage()` is the text;
- `getCode()` is the code, or `null`;
- `toString()` is the message, or "code: message" when there is a code.

Most wallet calls catch the native failure and throw a `ZephyrError` with the core's message and
no code. Two calls set the code -1: `getReserveProofAccount` and `checkReserveProof`. Checks in
the Java code, such as a missing network type or a closed wallet, throw a `ZephyrError` as well.

## Where messages come from

| Source | What arrives | Example |
|---|---|---|
| a check in the Java code | `ZephyrError` with the library's text | "Wallet is closed" |
| the core, through a wallet call that wraps it | `ZephyrError` with the core's text, code `null` | "not enough money" |
| the core, through a call that does not wrap it | `java.lang.Exception` with the core's text | "invalid password" |
| the core, out of memory | `OutOfMemoryError`, not wrapped | — |
| an argument check | `AssertionError` | "Log level must be an integer >= 0" |
| an unknown hash in a transfer or output query | `RuntimeException` | "Wallet missing requested tx hashes: " and the hashes |
| an inherited method that zephyr-java does not implement | `RuntimeException` | "ZephyrWalletFull.tagAccounts() not implemented" |
| the RPC classes | `ZephyrRpcError`, a `ZephyrError` with the RPC code | [ZephyrRpcError](11-the-rpc-classes.md#zephyrrpcerror) |

The JNI bridge turns a C++ exception into a `java.lang.Exception` (an `IOException` for an I/O
failure, an `OutOfMemoryError` when memory runs out). The wallet calls catch the `Exception` and
rethrow it as a `ZephyrError`, so an `OutOfMemoryError` passes through unchanged.

**Three calls do not wrap.** `openWallet` from a path, `openWalletData` and `createWallet` for a
random wallet or one from a mnemonic pass the core's failure on as it comes (read in the source).
Observed on 2026-11-17, also with the released JAR: `openWallet` with a wrong password threw
`java.lang.Exception` with the message "invalid password", not a `ZephyrError`. Catch `Exception` around these calls
([Opening wallet files](02-wallets.md#opening-wallet-files)). The other failures of those calls
were not observed.

`ZephyrUtils.validateAddress` turns its `AssertionError` into a `ZephyrError`; `isValidAddress`
returns `false` instead of throwing.

## The messages a wallet meets

The messages below appear in this guide, in the library's source or in the runs of 2026-11-17. The
core's messages are its own text and can change with the core.

| Message | Thrown by | What the app can do |
|---|---|---|
| "Wallet does not exist at path: " and the path | `openWallet` | offer to create or restore a wallet |
| "invalid password" (`java.lang.Exception`) | `openWallet` | ask for the password again |
| "Wallet already exists: " and the path | `createWallet` | choose another path |
| "Cannot specify restore height when creating random wallet" | `createWallet` | leave the restore height out |
| "Wallet may be initialized with a mnemonic or keys but not both" | `createWallet` | give one of the two |
| "Must specify a network type: 'mainnet', 'testnet' or 'stagenet'" | `createWallet`, `openWallet` with a config | set `MAINNET` |
| "Wallet is closed" | any call after `close()` | open the wallet again |
| "Wallet is not connected to daemon" | `sync()`, `getDaemonHeight()` and other calls that need the node | check the node; for `https://` see [TLS to a node](03-connecting-to-a-node.md#tls-to-a-node) |
| "failed to connect to daemon: " and the URI | `getHeightByDate` | check the node |
| `failed to read file ".keys"` | `changePassword` on a wallet from bytes | [The password](02-wallets.md#the-password) |
| "Invalid original password." | `changePassword` | ask for the old password again |
| "Must specify source currency", "Must specify account index to send from" | `createTxs`, `sweepUnlocked` | set the currency pair and the account |
| "not a valid tx type" | `createTxs` | use one of [the ten pairs](08-conversions-and-the-reserve.md#the-ten-pairs) |
| "not enough money" | `createTxs` | offer a smaller amount |
| "Spot reserve ratio not satisfied. ..." or "Reserve ratio not satisfied. ..." | `createTxs` for a conversion | [The reserve ratio](08-conversions-and-the-reserve.md#the-reserve-ratio) |
| "Mint/redeem TX amounts permit at most 4 decimal places" | `createTxs` for a conversion | round down to four decimals |
| "Cannot set amount because ZephyrTxConfig already has multiple destinations" | `ZephyrTxConfig.setAmount` | set the amount on each destination |
| "Must provide account index" | `getBalance(asset, null)` | pass an account index |
| "Wallet missing requested tx hashes: " and the hashes | `getTxs(hash)` | sync, or use `getTxs(hashes, missing)` |
| "Address has invalid ..." or "Address network type mismatch" | `validateAddress` | ask the user to check the address |
| "The RPC classes need java.time: ..." | the RPC classes on API 24 and 25 | [The RPC classes below API 26](10-android.md#the-rpc-classes-below-api-26) |
| "Not supported by ZephyrWalletRpc until the RPC port: reserve info" | `ZephyrWalletRpc` | use `ZephyrWalletFull` |

**Match on the type, not on the text.** As advice: catch `ZephyrError`, and `Exception` around the
calls that do not wrap. Treat the message as text for a log or a screen, not as an API.
Whether the core's messages are stable across releases is not known. In the runs of 2026-11-17 the
messages of `createTxs` held only the core's own text. Whether a core message can carry text from
the node is not known.

## Showing an error

As advice for screens, state what happened, why if known, and what to do next. For example, for
"not enough money": "This account cannot pay the amount and its fee. Enter a smaller amount, or
wait for pending funds to unlock." Keep the core's message for the log.

## Logging safely

> [!WARNING]
> **Never log `ZephyrRpcError.toString()`.** It appends the RPC method and its parameters as JSON.
> The calls of `ZephyrWalletRpc` that open or create a wallet put the wallet's password into those
> parameters, and its `setDaemonConnection` the node's login password. Log `getMessage()` and
> `getCode()` instead.

Never log the mnemonic, the private keys or the wallet password. For a `ZephyrError` without a
code, `getMessage()` and `toString()` are the same text.

## The native log

The core has its own log, separate from Java's. `ZephyrUtils.setLogLevel(level)` sets the level of
zephyr-java's own messages (printed with `System.out`) and, once the native library is loaded, the
core's level as well. `ZephyrUtils.configureNativeLogging(path, console)` sends the core's log to
a file.

Observed on 2026-11-17 on the desktop:

| Setting | Where the core's log goes |
|---|---|
| neither call | nowhere: no line on stdout or stderr, no file, even for errors |
| `setLogLevel(0)` | warnings and errors on stdout, no file |
| `setLogLevel(1)` | informational lines as well, still on stdout only |
| `configureNativeLogging(path, false)` | the file at `path`, nothing on the console; without `setLogLevel` at the categories of level 0 |
| `configureNativeLogging(path, true)` | the file, and the same lines with ANSI colour codes on stdout |

Nothing went to stderr in any variant. The "no file" results rest on a search of the likely
directories, not on a trace of every file the process opened.

**The log holds private data.** At level 0 it already records the wallet's primary address, the
wallet file's path, and for each received or spent output the amount and the transaction hash. At
level 1 it adds the node's URI. At level 1 the console line for an exception also carries the
absolute source path of the machine that built the library. Treat a log file like the wallet: keep
it in the app's private storage, out of backups and crash reports, and switch it on only to debug.

**The log can grow fast.** With a listener registered, a level-0 file log grew by one error line
per scanned block: about 1.1 MB for 7,700 blocks. The line was "wait should have been called
before waiter dtor - waiting now". Without a listener there were 4 such lines.

These runs used a developer build of the `2.3.0-sec.2-java.4` sources on Linux. A level-0 log of the
released JAR held the same lines on 2026-11-17: the primary address, the wallet path, and the
amounts and hashes of the outputs. Levels above 1 were not tried. On Android, `configureNativeLogging` has not been tried; nothing the core logs appears
in logcat ([Logs](10-android.md#logs)).
