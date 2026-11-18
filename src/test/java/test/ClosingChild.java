package test;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import zephyr.common.ZephyrError;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrOutputWallet;
import zephyr.wallet.model.ZephyrSyncResult;
import zephyr.wallet.model.ZephyrWalletConfig;
import zephyr.wallet.model.ZephyrWalletListener;

/**
 * The program {@link TestWalletClosingChildren} runs in a JVM of its own, one scenario per run.
 * Success is the JVM ending by itself after main returns, which is what the parent checks: a
 * native thread left attached would keep it alive. A scenario that sees something wrong prints a
 * line starting with "FAIL" and exits with status 2.
 *
 * <p>Arguments: the scenario, then the wallet path (the keys file of a wallet without a cache,
 * so it scans from its restore height), except for "silent". The node's uri comes from the system
 * property {@code zephyr.daemon.uri}, the wallet's password from the environment variable
 * {@code ZEPHYR_CHILD_PASSWORD}; neither is printed, and neither are addresses, keys or hashes.</p>
 */
public class ClosingChild {

  private static final long T0 = System.currentTimeMillis();
  private static final long WAIT_MS = 240000;

  private static void log(String message) {
    System.out.println(String.format("[%8.3f s] [%s] %s", (System.currentTimeMillis() - T0) / 1000.0, Thread.currentThread().getName(), message));
    System.out.flush();
  }

  private static void fail(String reason) {
    log("FAIL " + reason);
    System.exit(2);
  }

  public static void main(String[] args) throws Exception {
    log("scenario " + args[0] + (args.length > 2 ? " " + args[2] : "") + "; java " + System.getProperty("java.version"));
    switch (args[0]) {
      case "n3b": n3b(args[1], args[2]); break;
      case "n4a": n4a(args[1]); break;
      case "n4b": n4b(args[1]); break;
      case "stopclose": stopClose(args[1]); break;
      case "fromcallback": fromCallback(args[1]); break;
      case "silent": silent(); break;
      default: fail("unknown scenario " + args[0]);
    }
    log("RESULT ok");
    log("MAIN-RETURNS");
  }

  private static ZephyrWalletFull open(String path) {
    String uri = System.getProperty("zephyr.daemon.uri");
    String password = System.getenv("ZEPHYR_CHILD_PASSWORD");
    if (uri == null || password == null) fail("zephyr.daemon.uri or ZEPHYR_CHILD_PASSWORD not set");
    ZephyrWalletFull wallet = ZephyrWalletFull.openWallet(path, password, ZephyrNetworkType.MAINNET, uri);
    log("opened; restore height " + wallet.getRestoreHeight() + ", height " + wallet.getHeight() + ", daemon height " + wallet.getDaemonHeight());
    return wallet;
  }

  /** Counts callbacks; optionally throws once from the first callback of one kind. */
  private static class Counting extends ZephyrWalletListener {
    final String throwFrom;
    final AtomicInteger progress = new AtomicInteger(), blocks = new AtomicInteger(), balances = new AtomicInteger(), received = new AtomicInteger(), spent = new AtomicInteger();
    final CountDownLatch firstProgress = new CountDownLatch(1);
    volatile boolean thrown;
    volatile String threadName;
    Counting(String throwFrom) { this.throwFrom = throwFrom; }
    private void maybeThrow(String kind) {
      if (kind.equals(throwFrom) && !thrown) {
        thrown = true;
        log("listener throws from " + kind + " now");
        throw new RuntimeException("closing test: thrown from " + kind);
      }
    }
    @Override public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
      if (progress.incrementAndGet() == 1) { threadName = Thread.currentThread().getName(); log("first onSyncProgress: height " + height + " on thread '" + threadName + "' daemon=" + Thread.currentThread().isDaemon()); }
      firstProgress.countDown();
      maybeThrow("progress");
    }
    @Override public void onNewBlock(long height) { blocks.incrementAndGet(); maybeThrow("block"); }
    @Override public void onBalancesChanged(BigInteger newBalance, BigInteger newUnlockedBalance, String assetType) { balances.incrementAndGet(); maybeThrow("balances"); }
    @Override public void onOutputReceived(ZephyrOutputWallet output) { received.incrementAndGet(); }
    @Override public void onOutputSpent(ZephyrOutputWallet output) { spent.incrementAndGet(); }
    String counts() {
      return "onSyncProgress=" + progress + " onNewBlock=" + blocks + " onBalancesChanged=" + balances + " onOutputReceived=" + received + " onOutputSpent=" + spent + " thrown=" + thrown;
    }
  }

  private static void awaitFirstProgress(Counting listener) throws InterruptedException {
    if (!listener.firstProgress.await(WAIT_MS, TimeUnit.MILLISECONDS)) fail("no onSyncProgress within " + WAIT_MS + " ms");
  }

  private static void timedClose(ZephyrWalletFull wallet) {
    long start = System.nanoTime();
    wallet.close();
    long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    log("CLOSE-WAIT-MS " + ms);
    if (!wallet.isClosed()) fail("isClosed() false after close()");
    try {
      wallet.getHeight();
      fail("getHeight() after close() returned");
    } catch (ZephyrError e) {
      log("getHeight() after close() threw ZephyrError: " + e.getMessage());
    }
  }

  private static void threads(String when) {
    List<String> names = new ArrayList<String>();
    for (Thread t : Thread.getAllStackTraces().keySet()) names.add(t.getName() + (t.isDaemon() ? "(d)" : ""));
    java.util.Collections.sort(names);
    log("threads " + when + " (" + names.size() + "): " + names);
  }

  /** N3b: a listener throws under startSyncing(); the scan reaches the tip, then stop and close. */
  private static void n3b(String path, String target) throws Exception {
    ZephyrWalletFull wallet = open(path);
    long daemonHeight = wallet.getDaemonHeight();
    Counting listener = new Counting(target);
    wallet.addListener(listener);
    wallet.startSyncing(1000L);
    log("startSyncing(1000) returned");
    long end = System.currentTimeMillis() + WAIT_MS;
    while (wallet.getHeight() < daemonHeight) {
      if (System.currentTimeMillis() > end) fail("the scan did not reach height " + daemonHeight + " within " + WAIT_MS + " ms");
      Thread.sleep(500);
    }
    log("reached the daemon height " + daemonHeight + "; " + listener.counts());
    if (!listener.thrown) fail("the listener never threw");
    threads("before stop");
    wallet.stopSyncing();
    timedClose(wallet);
    log("after close: " + listener.counts());
    threads("after close");
  }

  /** N4a: close() from main while another thread is inside a foreground sync(). */
  private static void n4a(String path) throws Exception {
    ZephyrWalletFull wallet = open(path);
    Counting listener = new Counting("none");
    wallet.addListener(listener);
    AtomicReference<String> outcome = new AtomicReference<String>();
    Thread syncer = new Thread(() -> {
      try {
        ZephyrSyncResult result = wallet.sync();
        outcome.set("returned");
        log("sync() returned: blocks fetched " + result.getNumBlocksFetched());
      } catch (ZephyrError e) {
        outcome.set("ZephyrError: " + e.getMessage());
        log("sync() threw ZephyrError: " + e.getMessage());
      } catch (Throwable t) {
        outcome.set(t.getClass().getName() + ": " + t.getMessage());
        log("sync() threw " + t.getClass().getName() + ": " + t.getMessage());
      }
    }, "syncer");
    syncer.start();
    awaitFirstProgress(listener);
    log("before close: height " + wallet.getHeight() + "; " + listener.counts());
    timedClose(wallet);
    if (syncer.isAlive()) log("syncer still alive right after close()");
    syncer.join(WAIT_MS);
    if (syncer.isAlive()) fail("the syncer did not end");
    String result = outcome.get();
    if (!"returned".equals(result) && !"ZephyrError: Wallet is closed".equals(result)) fail("sync() ended with " + result);
    log("after close: " + listener.counts());
    threads("end");
  }

  /** N4b: close() during a busy background scan. */
  private static void n4b(String path) throws Exception {
    ZephyrWalletFull wallet = open(path);
    Counting listener = new Counting("none");
    wallet.addListener(listener);
    wallet.startSyncing(1000L);
    awaitFirstProgress(listener);
    log("before close: height " + wallet.getHeight() + "; " + listener.counts());
    timedClose(wallet);
    int before = listener.progress.get() + listener.blocks.get();
    Thread.sleep(2000);
    int after = listener.progress.get() + listener.blocks.get();
    if (after != before) fail((after - before) + " callbacks after close() returned");
    log("after close: " + listener.counts());
    threads("end");
  }

  /** stopSyncing() directly followed by close(), during a busy background scan. */
  private static void stopClose(String path) throws Exception {
    ZephyrWalletFull wallet = open(path);
    Counting listener = new Counting("none");
    wallet.addListener(listener);
    wallet.startSyncing(1000L);
    awaitFirstProgress(listener);
    log("before stopSyncing: height " + wallet.getHeight() + "; " + listener.counts());
    wallet.stopSyncing();
    timedClose(wallet);
    log("after close: " + listener.counts());
    threads("end");
  }

  /**
   * close() from inside a callback of background syncing is refused; the listener can still call
   * the wallet; close() from main afterwards closes it.
   */
  private static void fromCallback(String path) throws Exception {
    ZephyrWalletFull wallet = open(path);
    AtomicReference<Throwable> fromClose = new AtomicReference<Throwable>();
    AtomicReference<String> fromHeight = new AtomicReference<String>();
    CountDownLatch tried = new CountDownLatch(1);
    wallet.addListener(new ZephyrWalletListener() {
      @Override public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
        if (tried.getCount() == 0) return;
        try {
          fromHeight.set("height " + wallet.getHeight());
        } catch (Throwable t) {
          fromHeight.set("getHeight() threw " + t);
        }
        try {
          wallet.close();
          fromClose.set(null);
        } catch (Throwable t) {
          fromClose.set(t);
        }
        log("in the callback on thread '" + Thread.currentThread().getName() + "' daemon=" + Thread.currentThread().isDaemon() + ": " + fromHeight.get() + "; close() " + (fromClose.get() == null ? "returned" : "threw " + fromClose.get().getClass().getName() + ": " + fromClose.get().getMessage()));
        tried.countDown();
      }
    });
    wallet.startSyncing(1000L);
    if (!tried.await(WAIT_MS, TimeUnit.MILLISECONDS)) fail("no onSyncProgress within " + WAIT_MS + " ms");
    if (!(fromClose.get() instanceof ZephyrError)) fail("close() from a callback: " + fromClose.get());
    if (fromHeight.get() == null || !fromHeight.get().startsWith("height ")) fail("getHeight() from a callback: " + fromHeight.get());
    if (wallet.isClosed()) fail("the wallet was closed from its own callback");
    log("main: wallet still open, height " + wallet.getHeight());
    timedClose(wallet);
    threads("end");
  }

  /**
   * close() while another thread's call waits on a node that accepts the connection and never
   * answers (setDaemonConnection, which checks the connection): how long close() waits, and that
   * it returns. No node needed.
   */
  private static void silent() throws Exception {
    ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    List<Socket> held = new ArrayList<Socket>();
    Thread acceptor = new Thread(() -> {
      try {
        while (true) {
          Socket socket = server.accept();
          synchronized (held) { held.add(socket); }
          log("silent server: accepted connection " + held.size());
        }
      } catch (Exception e) {
        // server closed
      }
    }, "silent-server");
    acceptor.setDaemon(true);
    acceptor.start();
    ZephyrWalletFull wallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig().setPassword("silent").setNetworkType(ZephyrNetworkType.MAINNET));
    String uri = "http://127.0.0.1:" + server.getLocalPort();
    AtomicReference<String> outcome = new AtomicReference<String>();
    CountDownLatch started = new CountDownLatch(1);
    Thread caller = new Thread(() -> {
      started.countDown();
      long t = System.nanoTime();
      try {
        wallet.setDaemonConnection(uri); // checks the connection: waits for an answer that never comes
        outcome.set("returned");
      } catch (Throwable e) {
        outcome.set(e.getClass().getName() + ": " + e.getMessage());
      }
      log("setDaemonConnection(silent server) ended after " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t) + " ms: " + outcome.get());
    }, "caller");
    caller.start();
    started.await();
    Thread.sleep(2000);
    log("caller alive 2 s after it started: " + caller.isAlive());
    timedClose(wallet);
    caller.join(WAIT_MS);
    if (caller.isAlive()) fail("the caller did not end");
    log("caller outcome: " + outcome.get());
    server.close();
    synchronized (held) { for (Socket socket : held) socket.close(); }
    threads("end");
  }
}
