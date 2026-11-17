# 3. Connecting to a node

This chapter covers the wallet's connection to a Zephyr node: setting the node, http and https,
TLS to a node, what happens when the node cannot be reached, and syncing.

## Setting the node

The core talks to a Zephyr node (`zephyrd`) over the node's RPC port. Set the connection when you
create or open the wallet, or later:

```java
wallet.setDaemonConnection(new ZephyrRpcConnection("http://node.example:17767", user, pass));
boolean connected = wallet.isConnectedToDaemon();
```

The user name and password are only for nodes that require RPC login. 17767 is the default RPC
port of a mainnet node. A wallet without a
connection still opens: it shows the state of its last sync, but cannot sync or send.
`setProxyUri` routes the core's connection through a proxy (inherited from monero-java, meant for
Tor); it has not been tested with Zephyr.

A URI without a port gets the scheme's default (443 or 80), and `getDaemonConnection()` reports it
with the port.

**The port.** A URI without a port gets the scheme's default (443 for `https://`, 80 for
`http://`); `getDaemonConnection()` then reports the URI with the port. (In `java.1` to `java.3`
the core connected to port 0 and blocked for minutes.) A scheme written in another case
(`HTTPS://`) reaches the core in lower case: the core switches TLS on only for a lowercase
`https`, and in `java.1` to `java.3` such a URI was spoken in clear text.

## http and https

An `http://` URI sends everything in clear text, which is fine for a node you run yourself on a
network you trust. An `https://` URI makes the core verify the node's certificate and its host name.

**Plain `http://` to a node you trust**, for example your own node on your own network, needs no
certificates at all.

## TLS to a node

The native wallet makes its own connections to the node, with the OpenSSL inside the native
library; with an `https://` URI the core verifies the node's certificate and checks its host name.
Which root certificates it trusts is decided by `zephyr.common.ZephyrTls` (the Java RPC classes
are not affected: they use the trust store of the Java runtime).

**`https://` works without setup.** By default the native wallet trusts the root certificates
shipped in the library and nothing else: Mozilla's list of root certificates as curl publishes it
(`ZephyrTls.getShippedCertificatesDate()`, `2026-11-05` in this release: 118 certificates), the same
on every device and renewed with each release. Before the first `https://` connection of a
`ZephyrWalletFull` the library writes them to `zephyr-java-tls/ca-bundle.pem` in the app's cache
directory (`java.io.tmpdir`) and names that file in `SSL_CERT_FILE`, with an empty directory in
`SSL_CERT_DIR`, so that exactly the bundle is trusted. When the process environment already names
`SSL_CERT_FILE` or `SSL_CERT_DIR` and the app has not called `ZephyrTls`, the library leaves it
alone: the app has chosen.

**Correction.** In `java.1` to `java.3` this chapter told apps to set `SSL_CERT_FILE` or
`SSL_CERT_DIR` themselves. On the Android images this was measured on (the emulator, API 24 and
API 35) that setting had no effect: OpenSSL ignored the environment in an app process (it asked
`getauxval(AT_SECURE)`, which was set in the app process on both), so an `https://` node could not
be verified there. Other devices were not measured. `java.4` fixes this in the build (patch
`0006` of [chapter 14](14-building-and-verifying.md#build-layer-patches)) and adds `ZephyrTls`.

On the desktop JVM nothing changes unless the application calls `ZephyrTls` itself: the Linux
library uses OpenSSL's default store (`/etc/ssl`), and `SSL_CERT_FILE` or `SSL_CERT_DIR` in the
environment override it, as before.

On Android the native wallet trusts the root certificates shipped in the library by default;
`ZephyrTls` changes that ([TLS to a node](#tls-to-a-node)). On the desktop JVM nothing
changes unless the application calls `ZephyrTls` itself: OpenSSL's default store (`/etc/ssl`) and
`SSL_CERT_FILE` or `SSL_CERT_DIR` in the environment apply.

### Trusting other certificates

**A node whose certificate comes from a private CA, or from a CA only the device knows**, needs
`addTrustedCertificates` or `setUseSystemCertificates(true)`. The options, all in
`src/test/java/examples/GuideTls.java` (compiled with the tests):

```java
ZephyrTls.addTrustedCertificates(caPem);    // your own CA, or a self-signed node certificate
                                            // whose name matches the node's host name
ZephyrTls.setUseSystemCertificates(true);   // the device's system store as well
ZephyrTls.setUseUserCertificates(true);     // the certificates the user installed
ZephyrTls.setUseShippedCertificates(false); // without the shipped root certificates
ZephyrTls.setCaFile(pem);                   // exactly this file and nothing else (null: back)
```

Configure `ZephyrTls` before the wallet is created or its connection is set: the core reads the
bundle whenever a wallet sets up its connection, and a changed configuration is applied before the
next `https://` connection of a wallet (`ZephyrTls.apply()` applies it at once and returns the
file in use). The environment belongs to the whole process, so the configuration holds for every
wallet of the app. Android 7.0's system store has no ISRG Root X1, the root of Let's Encrypt: with
the system store alone a Let's Encrypt node fails there; the shipped certificates cover it.

### Newer root certificates

**Newer root certificates before the next release** (opt-in; the library never downloads by
itself). `ZephyrTls.downloadCertificates(file)` fetches the current list from curl
(`https://curl.se/ca/cacert.pem`) over a connection that trusts the certificates of the current
configuration, checks it, and writes the file; it does not change the configuration. The app adds
the file at start:

```java
File roots = new File(context.getFilesDir(), "cacert.pem");
if (roots.isFile()) ZephyrTls.addTrustedCertificates(roots);   // at start, before the wallet
...
ZephyrTls.downloadCertificates(roots);                          // later, on a background thread
```

The download contacts a third party (curl.se, unless the app names another source with
`downloadCertificates(url, file, sha256)`) and so shows it the device's address; call it from a
background thread and not more than once a day. The file is trusted because the `https`
connection is: whoever could forge a certificate under the current roots could add a root. An app
that can get the checksum from a source of its own should pass it. Apps that do not call it get new
roots with each zephyr-java release.

### What TLS cannot do

**What it cannot do.** There is no certificate pinning. A certificate whose host name does not
match the URI is refused (the core checks the name), so a self-signed certificate needs the right
name. The app's Network Security Configuration does not apply to the native wallet.

## When the node cannot be reached

When verification fails, `isConnectedToDaemon()` is false and calls that need the node throw "Wallet
is not connected to daemon" with a hint at TLS; the reason is in the native log, which on Android
does not reach logcat ([chapter 10](10-android.md#logs)).

**When verification fails**, `isConnectedToDaemon()` returns false, and a call that needs the node
(`sync()`, `getDaemonHeight()` and the like) throws "Wallet is not connected to daemon: the node is
an https node; its certificate may not be trusted (see ZephyrTls and the user guide, chapter 6)".
The native log says "SSL certificate is not in the allowed list, connection dropped"
([Logs](10-android.md#logs)).

## Syncing

`sync()` fetches the blocks since the wallet's height and returns when the wallet is at the tip.
It blocks, so call it on a worker thread. `sync(listener)` adds a listener for that sync only.

```java
ZephyrSyncResult result = wallet.sync(new ZephyrWalletListener() {
  @Override
  public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
    progress.post(percentDone);   // 0.0 to 1.0
  }
});
long fetched = result.getNumBlocksFetched();
```

Progress is reported once per block fetched. A wallet that is already at the tip fetches nothing
and gets **no** progress call; do not wait for one. The same holds for a new random wallet, which
starts at the node's height.

## Background sync

`startSyncing()` keeps the wallet in sync in the background, every 10 seconds by default
(`startSyncing(periodInMs)` sets another period); `stopSyncing()` ends it. Listeners added with
`addListener` receive the events of background syncs too.
