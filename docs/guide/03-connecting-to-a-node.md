# 3. Connecting to a node

A wallet needs a Zephyr node (`zephyrd`) to sync and to send. This chapter covers the connection
and its URI, http and https, and TLS to a node with `ZephyrTls`. It then covers the trade-off of a
remote node, syncing, the heights a wallet reports, and a node that cannot be reached.

The blocks marked "Java, compiled" in this chapter come from
`src/test/java/examples/GuideNode.java` and `src/test/java/examples/GuideTls.java`.

## Setting the node

The core talks to the node over the node's RPC port. Set the connection when you create or open
the wallet (`setServerUri`, `setServerUsername` and `setServerPassword` on `ZephyrWalletConfig`, or
a `ZephyrRpcConnection` for `openWallet` and `openWalletData`), or later:

```java examples/GuideNode.java
wallet.setDaemonConnection(new ZephyrRpcConnection("http://node.example.invalid:17767", user, pass));
boolean connected = wallet.isConnectedToDaemon();
```

Replace `node.example.invalid` with your node's host name. The user name and password are only for
nodes that require RPC login. `17767` is the default RPC port of a mainnet node.
`getDaemonConnection()` returns the connection in use.

A wallet without a connection still opens: it shows the state of its last sync, but cannot sync or
send. `setProxyUri` routes the core's connection through a proxy (inherited from monero-java,
meant for Tor); it has not been tested with Zephyr.

**The port.** A URI without a port gets the scheme's default: 443 for `https://`, 80 for
`http://`. `getDaemonConnection()` then reports the URI with the port. A scheme written in another
case (`HTTPS://`) reaches the core in lower case; the core switches TLS on only for a lowercase
`https`. Releases `java.1` to `java.3` behaved differently. For a URI without a port the core connected
to port 0 and blocked for minutes, and an `HTTPS://` URI was spoken in clear text.

## http and https

An `http://` URI sends everything in clear text. That is fine for a node you run yourself on a
network you trust, which needs no certificates at all. An `https://` URI makes the core verify the
node's certificate and its host name.

## TLS to a node

The native wallet makes its own connections to the node, with the OpenSSL inside the native
library. Which root certificates it trusts is decided by `zephyr.common.ZephyrTls`. The Java RPC
classes are not affected: they use the trust store of the Java runtime.

### The default trust

**`https://` works without setup on Android.** By default the native wallet trusts the root
certificates shipped in the library and nothing else. They are Mozilla's list of root certificates
as curl publishes it, the same on every device and renewed with each release.
`ZephyrTls.getShippedCertificatesDate()` returns their date: `2026-11-05` in this release, with 118
certificates.

Before the first `https://` connection of a `ZephyrWalletFull` on Android, the library writes them
to `zephyr-java-tls/ca-bundle.pem` in the app's cache directory (`java.io.tmpdir`). It names that
file in `SSL_CERT_FILE`, with an empty directory in `SSL_CERT_DIR`, so that exactly the bundle is
trusted. When the process environment already names `SSL_CERT_FILE` or `SSL_CERT_DIR` and the app
has not called `ZephyrTls`, the library leaves it alone: the app has chosen.

**On the desktop JVM nothing changes unless the application calls `ZephyrTls` itself.** The Linux
library uses OpenSSL's default store (`/etc/ssl`), and `SSL_CERT_FILE` or `SSL_CERT_DIR` in the
environment override it. Once the app calls a configuration method of `ZephyrTls`, its
configuration applies on the desktop as on Android.

Releases `java.1` to `java.3` told apps to set `SSL_CERT_FILE` or `SSL_CERT_DIR` themselves. On the
Android images this was measured on (the emulator, API 24 and API 35) that setting had no effect,
so an `https://` node could not be verified there; other devices were not measured. `java.4` fixes this in the build (patch `0006`
of [Build-layer patches](14-building-and-verifying.md#build-layer-patches)) and adds `ZephyrTls`.
The release notes have the details.

### Trusting other certificates

**A node whose certificate comes from a private CA, or from a CA only the device knows**, needs
`addTrustedCertificates` or `setUseSystemCertificates(true)`. The options:

```java examples/GuideTls.java
ZephyrTls.addTrustedCertificates(caPem);    // your own CA, or a self-signed node certificate
                                            // whose name matches the node's host name
ZephyrTls.setUseSystemCertificates(true);   // the device's system store as well
ZephyrTls.setUseUserCertificates(true);     // the certificates the user installed
ZephyrTls.setUseShippedCertificates(false); // without the shipped root certificates
ZephyrTls.setCaFile(pem);                   // exactly this file and nothing else (null: back)
```

Configure `ZephyrTls` before the wallet is created or its connection is set. The core reads the
bundle whenever a wallet sets up its connection. A changed configuration is applied before the
next `https://` connection of a wallet; `ZephyrTls.apply()` applies it at once and returns the
file in use. The environment belongs to the whole process, so the configuration holds for every
wallet of the app.

Android 7.0's system store has no ISRG Root X1, the root of Let's Encrypt. With the system store
alone a Let's Encrypt node fails there; the shipped certificates cover it.

### Newer root certificates

**Newer root certificates before the next release** are opt-in; the library never downloads by
itself. `ZephyrTls.downloadCertificates(file)` fetches the current list from curl
(`https://curl.se/ca/cacert.pem`) over a connection that trusts the certificates of the current
configuration. It checks the list, writes the file and returns the number of certificates; it does
not change the configuration. The app adds the file at start:

```java examples/GuideTls.java
// TLS: at start, the root certificates downloaded earlier, when there are any
public static void addDownloadedRoots(File filesDir) {
  File roots = new File(filesDir, "cacert.pem");
  if (roots.isFile()) ZephyrTls.addTrustedCertificates(roots);
}

// TLS: the download of the current root certificates (curl.se), on a background thread, at most
// once a day; the next start adds them with addDownloadedRoots
public static int downloadRoots(File filesDir) {
  return ZephyrTls.downloadCertificates(new File(filesDir, "cacert.pem"));
}
```

The trade-off of the download:

- **What it does:** it brings root certificates newer than the release's.
- **What the third party sees:** curl.se, unless the app names another source with
  `downloadCertificates(url, file, sha256)`, sees the device's address. Call it from a background
  thread and not more than once a day.
- **What it cannot protect against:** the file is trusted because the `https` connection is.
  Whoever could forge a certificate under the current roots could add a root.
- **How to avoid it:** an app that can get the checksum from a source of its own should pass it.
  Apps that do not download get new roots with each zephyr-java release.

### What TLS cannot do

There is no certificate pinning. A certificate whose host name does not match the URI is refused
(the core checks the name and passes no SSL options through), so a self-signed certificate needs
the right name. The app's Network Security Configuration does not apply to the native wallet.
*Status:* pinning and own SSL options need the core.

## A remote node

> [!WARNING]
> **A remote node is a trust trade-off.** A node run by someone else lets the wallet sync and send
> without your own copy of the chain. Its operator sees the address your connections come from,
> when the wallet syncs, the decoy outputs it asks for to build a transaction, and every
> transaction it sends. The wallet gets every block from the node, so a node that is behind or
> holds data back shows the wallet an old state. The wallet runs in your process and keeps its
> keys there. Run your own node to remove the trade-off.

**The core treats every node as trusted** (monero's trusted-daemon mode; read in the core's code).
It sets the node as trusted for every connection, so wallet2's precautions for an untrusted node
are off. For example, the node's own error text is passed into exception messages instead of a
neutral "daemon error". For an app this means that a node you do not run gets more trust than
monero's wallet would give it. Prefer your own node, and treat text in error messages as text from
the node. What else a node can learn from the core's requests has not been established for
zephyr-java.

## Syncing

`sync()` fetches the blocks since the wallet's height, scans them for the wallet's outputs and
returns when the wallet is at the tip. It blocks, so call it on a worker thread.

**A node that is itself still syncing gives an empty sync.** The core syncs only when the node
reports that it is synced, that is when `getDaemonHeight()` is at least `getDaemonMaxPeerHeight()`.
Otherwise `sync()` returns at once with 0 blocks fetched and no error, and the wallet stays where it
was (read in the core's code; not run). Check `isDaemonSynced()` before you trust a sync, and show
the user that the node is behind.
`sync(listener)` adds a listener for that sync only:

```java examples/GuideNode.java
ZephyrSyncResult result = wallet.sync(new ZephyrWalletListener() {
  @Override
  public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
    progress.post(percentDone);   // 0.0 to 1.0
  }
});
long fetched = result.getNumBlocksFetched();
```

`sync(startHeight)` starts at another height instead of the last synced block. A start below the
restore height also lowers the restore height (read in the core's code).

Progress is reported once per block fetched. A wallet that is already at the tip fetches nothing
and gets **no** progress call; do not wait for one. The same holds for a new random wallet, which
starts at the node's height.

The first sync of a restored wallet scans from its restore height to the tip
([Restoring from a mnemonic](02-wallets.md#restoring-from-a-mnemonic)). How long that takes over
the internet from an old restore height has not been measured for zephyr-java. Save after the
first sync ([When to save](02-wallets.md#when-to-save)).

## Background sync

`startSyncing()` keeps the wallet in sync in the background, every 10 s by default.
`startSyncing(periodInMs)` sets another period; `stopSyncing()` ends it. Listeners added with
`addListener` receive the events of background syncs too.

```java examples/GuideNode.java
wallet.startSyncing(30000L);
```

Background syncing runs on threads of the core. Its callbacks, and closing a wallet while it
syncs, follow the rules of [Threads and listeners](04-threads-and-listeners.md).

**Background syncing reports no errors to the app.** In the core's code, a refresh that fails is
printed on the process's standard output, and the next period tries again. The line starts with
"monero_wallet_full failed to background synchronize: ". A node that is down or not synced makes the
background sync do nothing, silently. Compare `getHeight()` with `getDaemonHeight()`, or call
`isConnectedToDaemon()`, to notice a sync that has stalled.

## Heights

Several calls report heights. Observed on 2026-11-17 with a test wallet (restore height 902,102,
its cache at height 904,719) and a node at height 909,823, then with a new random wallet:

| Call | No node, or node unreachable | Connected, before sync | After sync | Node removed after sync | New random wallet, before / after its first sync |
|---|---|---|---|---|---|
| `getHeight()` | 904,719 | 904,719 | 909,823 | 909,823 | 1 / 909,823 |
| `getDaemonHeight()` | throws | 909,823 | 909,823 | throws | 909,823 / 909,823 |
| `getDaemonMaxPeerHeight()` | throws | 909,823 | 909,823 | throws | 909,823 / 909,823 |
| `isDaemonSynced()` | throws | true | true | throws | true / true |
| `isSynced()` | false | false | true | true | false / true |
| `getRestoreHeight()` | 902,102 | 902,102 | 902,102 | 902,102 | 909,823 / 909,823 |
| `getHeightByDate(2026, 11, 1)` | throws | 897,383 | 897,383 | throws | 897,383 |
| `isConnectedToDaemon()` | false | true | true | false | true / true |

What the table shows:

- `getHeight()` is the wallet's own height. After a sync it equals `getDaemonHeight()`.
- A new random wallet reports `getHeight()` 1 until its first sync, not its restore height. Its
  restore height is the node's height when it was created.
- The calls about the node throw a `ZephyrError` "Wallet is not connected to daemon" when no node
  was reachable at the last connection check; they return no default value. `getHeightByDate` needs the node as well and throws
  "failed to connect to daemon: " followed by the URI.
- `isSynced()` keeps its last value when the node goes away.

During the first sync of a restored wallet, `getHeight()` can be far below the restore height
while the core fetches block hashes. Observed on 2026-11-17: 79,979 for a restore height of
902,102. A node that is itself still syncing was not available in the runs; what the core's code
does then is in [Syncing](#syncing).
`setRestoreHeight(height)` changes the restore height of an open wallet.

## When the node cannot be reached

`isConnectedToDaemon()` returns false, and a call that needs the node (`sync()`,
`getDaemonHeight()` and the like) throws a `ZephyrError` "Wallet is not connected to daemon". The
balance methods still work: they read the wallet's local state.

That message comes from the result of the last connection check (read in the core's code). The
core makes that check when the connection is set and on each `isConnectedToDaemon()`. A node that goes
away after that check gives other messages, such as "Failed to connect to daemon" from
`getDaemonHeight()`. Call `isConnectedToDaemon()` to refresh the state.

**For an `https://` node** the message gets a hint at TLS. The library adds it to every "Wallet is
not connected to daemon" of an `https://` URI, whatever the cause. The cause can be an untrusted
certificate, but also a node that is down or a wrong port:

```text
Wallet is not connected to daemon: the node is an https node; its certificate may not be trusted (see ZephyrTls and the user guide, chapter 6)
```

Releases up to `2.3.0-sec.2-java.4` print "chapter 6" in that message. The section it means is
[TLS to a node](#tls-to-a-node) in this chapter. When the certificate is the cause, the native log
says "SSL certificate is not in the allowed list, connection dropped"
([The native log](09-errors.md#the-native-log)).
