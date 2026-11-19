# 9. Errors and exceptions

This chapter covers what a wallet app sees when something goes wrong. It names the exception
types and where their messages come from, lists the messages a wallet meets, and shows how to log
them safely. It ends with the core's own log.

## ZephyrError

`zephyr.common.ZephyrError` is the exception of the library. In `2.3.0-sec.2-java.5` every public
method of `ZephyrWalletFull` and of `ZephyrUtils` throws it and nothing else, apart from the cases
in [What is not a ZephyrError](#what-is-not-a-zephyrerror). It extends `RuntimeException`, so the
compiler does not ask you to catch it. It carries a message and an optional `Integer` code:

- `getMessage()` is the text;
- `getCode()` is the code, or `null`;
- `toString()` is the message, or "code: message" when there is a code.

The core's failures arrive with the core's message and no code. Two calls set the code -1:
`getReserveProofAccount` and `checkReserveProof`. Checks in the Java code, such as a missing
network type or a closed wallet, throw a `ZephyrError` as well.

## Where messages come from

| Source | What arrives | Example |
|---|---|---|
| a check in the Java code | `ZephyrError` with the library's text | "Wallet is closed" |
| the core, through any wallet call | `ZephyrError` with the core's text, code `null` | "not enough money", "invalid password" |
| a call refused inside a listener callback | `ZephyrError` | "Cannot call save() from inside a listener callback; call it after the callback has returned" |
| an argument check | `ZephyrError` | "Log level must be an integer >= 0" |
| a `null` argument the Java code does not check itself | `ZephyrError` with the Java runtime's text for the `NullPointerException` | — |
| an unknown hash in a transaction, transfer or output query | `ZephyrError` | "Wallet missing requested tx hashes: " and the hashes |
| an inherited method that zephyr-java does not implement | `ZephyrError` | "ZephyrWalletFull.tagAccounts() not implemented" |
| the core, out of memory | `OutOfMemoryError`, not wrapped | — |
| the RPC classes, an RPC or HTTP error | `ZephyrRpcError`, a `ZephyrError` with the RPC code or HTTP status | [ZephyrRpcError](11-the-rpc-classes.md#zephyrrpcerror) |
| the RPC classes, no connection | a plain `ZephyrError` whose message is the cause's text | — |

The JNI bridge turns every C++ exception, the core's and its own, into a `ZephyrError`, in every
native call, except a failure to allocate memory, which becomes an `OutOfMemoryError`. The Java
side turns any other `RuntimeException` or `AssertionError` of a wallet call into a `ZephyrError`
with the same message. It leaves out the cause, which can carry the data that failed to parse.

## What is not a ZephyrError

- **`OutOfMemoryError`**, when the core runs out of memory.
- **The Java runtime's own errors when the native library cannot be loaded** (read in the code).
  The first use of `ZephyrWalletFull` then fails with an `ExceptionInInitializerError` around the
  `ZephyrError`, and later uses with a `NoClassDefFoundError`. A native call of `ZephyrUtils` fails
  with an `UnsatisfiedLinkError`. Call `ZephyrUtils.loadNativeLibrary()` first to get the
  `ZephyrError` with both reasons ([The native library](01-getting-started.md#the-native-library)).
- **The other classes.** The rule covers `ZephyrWalletFull` and `ZephyrUtils` only. A model class
  used directly can throw other exceptions: `ZephyrTxConfig.setAmount(String)` throws a
  `NumberFormatException` for text that is not a number. `ZephyrTls`, the RPC classes and the
  connection manager were not checked for the rule.

**Releases up to `2.3.0-sec.2-java.4`** let the core's failure through as `java.lang.Exception` in
many calls: opening and creating wallets, `save`, `moveTo`, `getData`, the balance calls,
`getReserveInfo`, `getCirculatingSupply` and others. Observed on 2026-11-17 with the released JAR:
a wrong password threw `java.lang.Exception` "invalid password". Some native calls did not catch
the core's failure at all, and it ended the process. Among them were the account and subaddress
calls, `getAddress`, the key getters and the calls that build a transaction from a config. Observed
on 2026-11-19 with a development build whose bridge is the same in those places: `createSubaddress`
with an account index out of range ended the process with `SIGABRT`. So did `createTx` with an
amount of 2^64 or more. In their source, an unknown hash in a transfer or output
query threw a plain `RuntimeException`, and argument checks an `AssertionError`. A `null`
attribute key or value, in `getAttribute(null)`, `setAttribute(null, v)` or
`setAttribute(k, null)`, aborted the process. That was read in the bridge's code and observed on
2026-11-18 with a development build whose bridge is the same in that place. On those releases,
catch `Exception` around wallet calls, not only `ZephyrError`. `2.3.0-sec.2-java.5` refuses a
`null` attribute key or value with "Must provide attribute key" or "Must provide attribute value".

`ZephyrUtils.validateAddress` throws a `ZephyrError` that names the problem; `isValidAddress`
returns `false` instead of throwing.

## The messages a wallet meets

The messages below appear in this guide, in the library's source or in the runs of 2026-11-17 to
2026-11-19. The core's messages are its own text and can change with the core.

| Message | Thrown by | What the app can do |
|---|---|---|
| "Wallet does not exist at path: " and the path | `openWallet` | offer to create or restore a wallet |
| "invalid password" | `openWallet` | ask for the password again |
| "Wallet already exists: " and the path | `createWallet` | choose another path |
| "Cannot specify restore height when creating random wallet" | `createWallet` | leave the restore height out |
| "Wallet may be initialized with a mnemonic or keys but not both" | `createWallet` | give one of the two |
| "Must specify a network type: 'mainnet', 'testnet' or 'stagenet'" | `createWallet`, `openWallet` with a config | set `MAINNET` |
| "Wallet is closed" | every call after `close()`, and a `sync()`, `rescanBlockchain()`, `scanTxs()` or `importMultisigHex()` that `close()` cut short | open the wallet again |
| "Cannot call " and the call, then " from inside a listener callback; call it after the callback has returned" | the calls refused inside a callback ([What a callback may call](04-threads-and-listeners.md#what-a-callback-may-call)) | hand the work to a thread of your own |
| "Cannot close the wallet from inside one of its own calls or listener callbacks" | `close()` | close from a thread of your own |
| "Wallet was closed, but saving it failed: " and the core's reason | `close(true)` | the wallet is closed; what changed since the last save is lost ([Closing](02-wallets.md#closing)) |
| "Wallet is not connected to daemon" | `sync()`, `getDaemonHeight()` and other calls that need the node, when the last connection check failed | check the node; for `https://` the message adds a hint at TLS ([When the node cannot be reached](03-connecting-to-a-node.md#when-the-node-cannot-be-reached)) |
| "Failed to connect to daemon" | `getDaemonHeight()` and the like, when the node went away after the last connection check | check the node |
| "failed to get circulating supply. Make sure you are connected to a daemon." | `getCirculatingSupply` | connect the wallet to a node |
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
| "Wallet missing requested tx hashes: " and the hashes | `getTxs(hash)`, and `getTransfers` or `getOutputs` with hashes in the query | sync, or use `getTxs(hashes, missing)` |
| "Must provide attribute key", "Must provide attribute value" | `getAttribute`, `setAttribute` | pass a key and a value |
| "Amount is not a finite number: " and the value | `ZephyrUtils.toAtomicUnits(double)` | check the input ([Amounts](05-assets-and-balances.md#amounts)) |
| "Address has invalid ..." or "Address network type mismatch" | `validateAddress` | ask the user to check the address |
| "The RPC classes need java.time: ..." | the RPC classes on API 24 and 25 | [The RPC classes below API 26](10-android.md#the-rpc-classes-below-api-26) |

**What an app can tell apart.** The type separates only the broad cases. A library check and a
core failure are a `ZephyrError`, and an RPC error a `ZephyrRpcError`. Every refusal of `createTxs`
by the core is the same `ZephyrError` with a `null` code; only the text differs. So the message table above is the only handle, and its
texts are not guaranteed to stay the same across releases of the core. As advice:

- check what you can before you build: round a conversion to four decimals, compare the amount
  with the unlocked balance, read `getReserveInfo()`;
- match a known message only to choose a better screen text, and fall back to a general failure;
- keep the message for the log.

In the runs of 2026-11-17 the messages of `createTxs` held only the core's own text. But a core
message can carry text from the node. The core passes the node's own error text into some messages
(read in the core's code; [A remote node](03-connecting-to-a-node.md#a-remote-node)). Show such text
as plain text, never as HTML.

## Showing an error

As advice for screens, state what happened, why if known, and what to do next. For example, for
"not enough money": "This account cannot pay the amount and its fee. Enter a smaller amount, or
wait for pending funds to unlock." Keep the core's message for the log.

## Logging safely

Never log the mnemonic, the private keys or the wallet password. `toString()` reaches a log
without an explicit call: through `printStackTrace()`, through `"" + e`, and through the
throwable output of most loggers. For a `ZephyrError` without a code, `getMessage()` and
`toString()` are the same text.

**`ZephyrRpcError.toString()` appends the RPC method and its parameters** as JSON. From
`2.3.0-sec.2-java.5` on it prints the values of secret parameters as `<redacted>`, while
`getRpcParams()` still holds them. Log `getMessage()` and `getCode()`, and never log
`getRpcParams()`.

> [!WARNING]
> **Up to `2.3.0-sec.2-java.4` the library logged secrets at some settings.** At `FINE` the
> transaction config and the transactions with their secret keys; from `setLogLevel(2)` request
> bodies with passwords and mnemonics; and `ZephyrRpcError.toString()` the parameters, passwords
> included. Keep `FINE` logging of the library and `setLogLevel` off on those releases, and do not
> log `ZephyrRpcError.toString()`.

What the library logs in `2.3.0-sec.2-java.5` (read in the source):

| Setting | What is logged, and where |
|---|---|
| `java.util.logging` level `FINE` for `zephyr.wallet.ZephyrWalletFull` | for `createTxs` and `sweepUnlocked`, the number of transactions built and their hashes; never the config, the transactions or their keys. Also a failed stop at close, and a failed switch of the native listener |
| `WARNING` for `zephyr.wallet.ZephyrWalletFull` | a listener that threw: its class, the callback's name and its `Throwable`; callback data that could not be read: the exception's class only ([An exception thrown in a callback](04-threads-and-listeners.md#an-exception-thrown-in-a-callback)) |
| `ZephyrUtils.setLogLevel(2)` | every request body and URI of the RPC classes, on `System.out`, with the values of secret names and the URI's user name and password as `<redacted>` |
| `ZephyrUtils.setLogLevel(3)` | every response as well, up to 10,000 characters, redacted by the same names |
| `setPrintStackTrace(true)` on a `ZephyrRpcConnection` | the redacted request body, with a stack trace, on stderr |
| any `ZephyrUtils.setLogLevel` call, `configureNativeLogging` | the core's log, which holds the primary address, amounts and transaction hashes ([The native log](#the-native-log)); from level 3 also the transaction config of `sweepOutput` and `getPaymentUri`, which the bridge writes at trace level |

The redacted names are one list. It applies alike to requests, responses and the parameters of
`ZephyrRpcError`, at any depth and without regard to case. The names are `password`, `old_password`,
`new_password`, `seed`, `seed_offset`, `spendkey`, `viewkey`, `tx_key`, `key`, `tx_key_list`,
`tx_metadata`, `tx_metadata_list`, `signed_txset`, `multisig_txset`, `multisig_info` and `info`.

`key` and `info` are generic names: a node's answer with an output's public key under `key` is
redacted too. Some fields are private but not secret, and are printed: key images, transaction
hashes, a transaction's hex, an unsigned transaction set, exported outputs, signed key images and
addresses. A log at levels 2 and 3 is still private data.

Other places print, without secrets. A failed check of `ZephyrConnectionManager` and a failed poll
of `ZephyrDaemonRpc` print their stack trace, which can name the node's host and port.
`ZephyrDaemonRpc` logs unexpected fields of a node's answer at `WARNING`, and the output lines of a
`zephyrd` process it started at `FINER`. `ZephyrTransfer` prints a fixed warning on `System.err`
when two versions of a transfer disagree and one has the amount 0.

On Android, `System.out` and `System.err` go to logcat. In a released app, keep `FINE` logging of
the library off and do not call `setLogLevel` or `configureNativeLogging`. Without them the core
printed nothing in the runs of 2026-11-17.

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
before waiter dtor - waiting now". Without a listener there were 4 such lines. In
`2.3.0-sec.2-java.5` the native listener stays in the core's listeners once a listener was added.
Whether the log then keeps growing after the last listener is removed was not run.

These runs used a developer build of the `2.3.0-sec.2-java.4` sources on Linux. A level-0 log of the
released JAR held the same lines on 2026-11-17: the primary address, the wallet path, and the
amounts and hashes of the outputs. They were not repeated with `2.3.0-sec.2-java.5`, which does
not change the core's sources or its logging. The bridge's own two error lines changed, and
neither carries wallet data. At trace level, from `setLogLevel(3)`, the bridge writes the
transaction config of `sweepOutput` and `getPaymentUri` into the log: addresses, amounts and a key
image, but no secret key (read in the code). Levels above 1 were not tried. On Android,
`configureNativeLogging` has not been tried; nothing the core logs appears in logcat
([Logs](10-android.md#logs)).
