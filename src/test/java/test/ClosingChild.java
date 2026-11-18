package test;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import zephyr.common.ZephyrError;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrOutputWallet;
import zephyr.wallet.model.ZephyrSyncResult;
import zephyr.wallet.model.ZephyrTxConfig;
import zephyr.wallet.model.ZephyrWalletConfig;
import zephyr.wallet.model.ZephyrWalletListener;

/**
 * The program {@link TestWalletClosingChildren} runs in a JVM of its own, one scenario per run.
 * Success is the JVM ending by itself after main returns, which is what the parent checks. The
 * core's own threads are attached to the JVM as daemon threads for each callback, so a thread left
 * attached would no longer keep the JVM alive: the background scenarios check the callback
 * threads themselves instead (each callback's {@code java.lang.Thread} must have ended once
 * {@code close()} has returned, which it does only when the bridge detached it). A scenario that
 * sees something wrong prints a line starting with "FAIL" and exits with status 2.
 *
 * <p>Arguments: the scenario, then the wallet path (the keys file of a wallet without a cache,
 * so it scans from its restore height), except for "silent". The node's uri comes from the system
 * property {@code zephyr.daemon.uri}, the wallet's password from the environment variable
 * {@code ZEPHYR_CHILD_PASSWORD}; neither is printed, and neither are addresses, keys or hashes.</p>
 */
public class ClosingChild {

  private static final long T0 = System.currentTimeMillis();
  private static final long WAIT_MS = 240000;
  /** H1: how long close() may wait right after startSyncing(); a whole catch-up scan of the test wallet takes longer. */
  static final long H1_LIMIT_MS = 3000;
  /** H2: how long before close(true) returns the wallet file may have been written. */
  static final long H2_GAP_LIMIT_MS = 500;
  /** W1: threads the JVM may start during a background scan with no listener registered. */
  static final long W1_THREAD_LIMIT = 50;
  /** H3: how long a call from inside a callback may take before the thread counts as stuck. */
  static final long STUCK_MS = 30000;

  private static void log(String message) {
    System.out.println(String.format("[%8.3f s] [%s] %s", (System.currentTimeMillis() - T0) / 1000.0, Thread.currentThread().getName(), message));
    System.out.flush();
  }

  private static void fail(String reason) {
    log("FAIL " + reason);
    System.exit(2);
  }

  /** For a thread that is stuck: its stack and every other thread's, then the process ends at once. */
  private static void failStuck(String reason) {
    log("FAIL " + reason);
    for (java.util.Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
      StringBuilder trace = new StringBuilder("stack of '" + entry.getKey().getName() + "' (" + entry.getKey().getState() + "):");
      for (StackTraceElement element : entry.getValue()) trace.append("\n    at ").append(element);
      log(trace.toString());
    }
    Runtime.getRuntime().halt(2);
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
      case "h1": h1(args[1], args[2], args[3]); break;
      case "h2": h2(args[1]); break;
      case "h3": h3(args[1], args[2]); break;
      case "w1": w1(args[1]); break;
      case "exitopen": exitOpen(args[1]); break;
      default: fail("unknown scenario " + args[0]);
    }
    log("RESULT ok");
    log("MAIN-RETURNS");
  }

  private static String password() {
    String password = System.getenv("ZEPHYR_CHILD_PASSWORD");
    if (password == null) fail("ZEPHYR_CHILD_PASSWORD not set");
    return password;
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
    final Set<Thread> threads = ConcurrentHashMap.newKeySet(); // every thread that delivered a callback
    Counting(String throwFrom) { this.throwFrom = throwFrom; }
    private void maybeThrow(String kind) {
      if (kind.equals(throwFrom) && !thrown) {
        thrown = true;
        log("listener throws from " + kind + " now");
        throw new RuntimeException("closing test: thrown from " + kind);
      }
    }
    @Override public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
      threads.add(Thread.currentThread());
      if (progress.incrementAndGet() == 1) { threadName = Thread.currentThread().getName(); log("first onSyncProgress: height " + height + " on thread '" + threadName + "' daemon=" + Thread.currentThread().isDaemon()); }
      firstProgress.countDown();
      maybeThrow("progress");
    }
    @Override public void onNewBlock(long height) { threads.add(Thread.currentThread()); blocks.incrementAndGet(); maybeThrow("block"); }
    @Override public void onBalancesChanged(BigInteger newBalance, BigInteger newUnlockedBalance, String assetType) { threads.add(Thread.currentThread()); balances.incrementAndGet(); maybeThrow("balances"); }
    @Override public void onOutputReceived(ZephyrOutputWallet output) { threads.add(Thread.currentThread()); received.incrementAndGet(); }
    @Override public void onOutputSpent(ZephyrOutputWallet output) { threads.add(Thread.currentThread()); spent.incrementAndGet(); }
    String counts() {
      return "onSyncProgress=" + progress + " onNewBlock=" + blocks + " onBalancesChanged=" + balances + " onOutputReceived=" + received + " onOutputSpent=" + spent + " thrown=" + thrown;
    }
  }

  private static void awaitFirstProgress(Counting listener) throws InterruptedException {
    if (!listener.firstProgress.await(WAIT_MS, TimeUnit.MILLISECONDS)) fail("no onSyncProgress within " + WAIT_MS + " ms");
  }

  private static long timedClose(ZephyrWalletFull wallet) {
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
    return ms;
  }

  /**
   * The background scenarios, after close() returned: every thread that delivered a callback of
   * the core's sync thread must have ended. The bridge attaches that native thread for each
   * callback and detaches it at the callback's end, which ends the callback's java.lang.Thread; a
   * thread left attached stays alive (as a daemon thread it no longer keeps the JVM from exiting,
   * so the JVM's exit does not show it).
   */
  private static void assertCallbackThreadsEnded(Set<Thread> threads) {
    List<String> alive = new ArrayList<String>();
    for (Thread thread : threads) {
      if (thread == Thread.currentThread()) fail("a background callback ran on the main thread");
      if (thread.isAlive()) alive.add(thread.getName() + (thread.isDaemon() ? "(d)" : "") + " " + thread.getState());
    }
    if (threads.isEmpty()) fail("no callback thread was recorded");
    if (!alive.isEmpty()) fail(alive.size() + " of " + threads.size() + " callback thread(s) still alive after close(): " + alive);
    log("all " + threads.size() + " callback thread(s) ended");
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
    assertCallbackThreadsEnded(listener.threads);
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
    assertCallbackThreadsEnded(listener.threads);
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
    assertCallbackThreadsEnded(listener.threads);
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
    Set<Thread> callbackThreads = ConcurrentHashMap.newKeySet();
    wallet.addListener(new ZephyrWalletListener() {
      @Override public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
        callbackThreads.add(Thread.currentThread());
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
    assertCallbackThreadsEnded(callbackThreads);
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

  /**
   * H1: close() right after startSyncing() ("start"), or after startSyncing() and stopSyncing()
   * ("stop"), on a wallet far behind the node. The core's new sync thread has not begun its refresh
   * yet, and a stop that comes before the refresh starts is lost there (the refresh resets the
   * core's run flag): close() must keep stopping it and return within about one batch of blocks,
   * not after a whole catch-up scan. The third argument is the delay between startSyncing() and
   * close(), in times one daemon RPC takes.
   */
  private static void h1(String path, String variant, String rpcs) throws Exception {
    ZephyrWalletFull wallet = open(path);
    long rpcStart = System.nanoTime();
    long daemonHeight = wallet.getDaemonHeight(); // one daemon RPC: the unit of the delay below
    long rpcNanos = System.nanoTime() - rpcStart;
    log("blocks behind the node: " + (daemonHeight - Math.max(wallet.getHeight(), wallet.getRestoreHeight())) + "; one daemon RPC took " + TimeUnit.NANOSECONDS.toMicros(rpcNanos) + " us");
    Counting listener = new Counting("none");
    wallet.addListener(listener);
    // the core's sync thread makes about three daemon RPCs before its refresh begins: waiting a
    // part of that lets the stop land there rather than before the thread reaches its loop
    long delayNanos = (long) (Double.parseDouble(rpcs) * rpcNanos);
    wallet.startSyncing(1000L);
    if (delayNanos > 0) Thread.sleep(TimeUnit.NANOSECONDS.toMillis(delayNanos), (int) (delayNanos % 1000000));
    if (variant.equals("stop")) wallet.stopSyncing();
    log((variant.equals("stop") ? "stopSyncing() and " : "") + "close() " + TimeUnit.NANOSECONDS.toMicros(delayNanos) + " us after startSyncing() returned (" + rpcs + " RPC times)");
    long ms = timedClose(wallet);
    log("after close: " + listener.counts());
    if (ms > H1_LIMIT_MS) fail("close() right after startSyncing()" + (variant.equals("stop") ? " and stopSyncing()" : "") + " waited " + ms + " ms (limit " + H1_LIMIT_MS + " ms)");
    if (!listener.threads.isEmpty()) assertCallbackThreadsEnded(listener.threads);
  }

  /**
   * H2: close(true) while the core's sync thread is inside a refresh, held there by a listener that
   * blocks in onNewBlock for 2 s. The save must come after that thread has stopped: the wallet file
   * is written at the end of close(true), not while the refresh is paused, and the saved wallet
   * opens with the scanned blocks.
   */
  private static void h2(String path) throws Exception {
    ZephyrWalletFull wallet = open(path);
    long restoreHeight = wallet.getRestoreHeight();
    CountDownLatch blocked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicBoolean armed = new AtomicBoolean();
    Counting listener = new Counting("none") {
      @Override public void onNewBlock(long height) {
        super.onNewBlock(height);
        if (armed.compareAndSet(true, false)) {
          log("listener blocks in onNewBlock at height " + height);
          blocked.countDown();
          try {
            release.await();
          } catch (InterruptedException e) {
            // released
          }
        }
      }
    };
    wallet.addListener(listener);
    wallet.startSyncing(1000L);
    awaitFirstProgress(listener);
    armed.set(true);
    if (!blocked.await(WAIT_MS, TimeUnit.MILLISECONDS)) fail("the listener was not called within " + WAIT_MS + " ms");
    Thread releaser = new Thread(() -> {
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        // release now
      }
      log("releasing the listener");
      release.countDown();
    }, "releaser");
    releaser.start();
    File cache = new File(path);
    long start = System.currentTimeMillis();
    wallet.close(true);
    long end = System.currentTimeMillis();
    log("CLOSE-WAIT-MS " + (end - start));
    if (!cache.isFile()) fail("no wallet file after close(true)");
    long written = cache.lastModified();
    long gap = end - written;
    log("H2-GAP-MS " + gap + " (the wallet file was written " + (written - start) + " ms after close(true) started)");
    if (gap > H2_GAP_LIMIT_MS) fail("close(true) wrote the wallet file " + gap + " ms before it returned: the save ran while the core's sync thread was still inside its refresh");
    ZephyrWalletFull reopened = ZephyrWalletFull.openWallet(path, password(), ZephyrNetworkType.MAINNET); // offline
    long height = reopened.getHeight();
    reopened.close();
    log("reopened offline: height " + height + ", restore height " + restoreHeight);
    if (height <= restoreHeight) fail("the saved wallet holds no scanned block: height " + height);
    assertCallbackThreadsEnded(listener.threads);
  }

  private interface Call {
    String run() throws Exception;
  }

  /** One wallet call from inside a callback: its result, or the Throwable it ended with. */
  private static String attempt(String name, Call call) {
    try {
      return name + ": " + call.run();
    } catch (Throwable t) {
      return name + ": " + t.getClass().getSimpleName() + ": " + t.getMessage();
    }
  }

  /**
   * H3: wallet calls from inside a callback of a running sync ("fg": the caller's sync(); "bg":
   * startSyncing()). sync(), scanTxs() and rescanBlockchain() would lock the core's sync mutex,
   * which the refresh that delivers the callback holds, and the thread would wait for itself; these
   * and every relaying call are refused with ZephyrError, before anything is built. Reads work. A
   * call that has not returned after 30 s counts as stuck: the stacks of all threads are printed
   * and the process ends.
   */
  private static void h3(String path, String mode) throws Exception {
    ZephyrWalletFull wallet = open(path);
    List<String> outcomes = new CopyOnWriteArrayList<String>();
    AtomicBoolean entered = new AtomicBoolean();
    CountDownLatch inCallback = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(1);
    Set<Thread> callbackThreads = ConcurrentHashMap.newKeySet();
    String unknownHash = String.join("", java.util.Collections.nCopies(64, "0"));
    wallet.addListener(new ZephyrWalletListener() {
      @Override public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
        callbackThreads.add(Thread.currentThread());
        if (!entered.compareAndSet(false, true)) return;
        log("in the first onSyncProgress on thread '" + Thread.currentThread().getName() + "'");
        inCallback.countDown();
        outcomes.add(attempt("getHeight()", () -> "height " + wallet.getHeight()));
        outcomes.add(attempt("sync()", () -> "returned blocks fetched " + wallet.sync().getNumBlocksFetched()));
        outcomes.add(attempt("scanTxs()", () -> { wallet.scanTxs(Arrays.asList(unknownHash)); return "returned"; }));
        outcomes.add(attempt("rescanBlockchain()", () -> { wallet.rescanBlockchain(); return "returned"; }));
        outcomes.add(attempt("startSyncing()", () -> { wallet.startSyncing(); return "returned"; }));
        outcomes.add(attempt("createTx(relay)", () -> { wallet.createTx(new ZephyrTxConfig().setRelay(true)); return "returned"; }));
        outcomes.add(attempt("getBalance()", () -> "returned " + (wallet.getBalance() != null)));
        for (String outcome : outcomes) log("from the callback: " + outcome);
        done.countDown();
      }
    });
    Thread syncer = null;
    AtomicReference<String> syncOutcome = new AtomicReference<String>();
    if (mode.equals("fg")) {
      syncer = new Thread(() -> syncOutcome.set(attempt("sync() of the syncer", () -> "returned blocks fetched " + wallet.sync().getNumBlocksFetched())), "syncer");
      syncer.start();
    } else {
      wallet.startSyncing(1000L);
    }
    if (!inCallback.await(WAIT_MS, TimeUnit.MILLISECONDS)) fail("no onSyncProgress within " + WAIT_MS + " ms");
    if (!done.await(STUCK_MS, TimeUnit.MILLISECONDS)) failStuck("the calls from inside the callback have not returned after " + STUCK_MS + " ms; so far: " + outcomes);
    if (!outcomes.get(0).startsWith("getHeight(): height ")) fail("a read from the callback: " + outcomes.get(0));
    if (!outcomes.get(6).equals("getBalance(): returned true")) fail("a read from the callback: " + outcomes.get(6));
    for (String outcome : outcomes.subList(1, 6)) {
      if (!outcome.contains("ZephyrError: ") || !outcome.contains("from inside a listener callback")) fail("not refused from the callback: " + outcome);
    }
    if (syncer != null) {
      syncer.join(WAIT_MS);
      if (syncer.isAlive()) failStuck("the syncer did not end");
      log(syncOutcome.get());
      if (!syncOutcome.get().startsWith("sync() of the syncer: returned")) fail(syncOutcome.get());
    } else {
      wallet.stopSyncing();
    }
    timedClose(wallet);
    if (syncer == null) assertCallbackThreadsEnded(callbackThreads);
  }

  /**
   * W1: a listener added and removed again leaves no native listener registered with the core, so a
   * background scan with no listener makes no callback upcalls. Every upcall from the core's thread
   * attaches it as a new java.lang.Thread, so the count of threads the JVM started during the scan
   * shows them. A listener added during the scan receives callbacks again.
   */
  private static void w1(String path) throws Exception {
    ZephyrWalletFull wallet = open(path);
    long from = Math.max(wallet.getHeight(), wallet.getRestoreHeight());
    long daemonHeight = wallet.getDaemonHeight();
    Counting removed = new Counting("none");
    wallet.addListener(removed);
    wallet.removeListener(removed);
    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    long startedBefore = threadBean.getTotalStartedThreadCount();
    wallet.startSyncing(1000L);
    long target = Math.min(from + 2500, daemonHeight - 1);
    long end = System.currentTimeMillis() + WAIT_MS;
    while (wallet.getHeight() < target) {
      if (System.currentTimeMillis() > end) fail("the scan did not reach height " + target + " within " + WAIT_MS + " ms");
      Thread.sleep(50);
    }
    long started = threadBean.getTotalStartedThreadCount() - startedBefore;
    long scanned = wallet.getHeight() - from;
    log("W1-THREADS-STARTED " + started + " while " + scanned + " blocks were scanned with no listener registered");
    Counting added = new Counting("none");
    wallet.addListener(added);
    awaitFirstProgress(added);
    log("the listener added during the scan got callbacks: " + added.counts());
    wallet.removeListener(added);
    wallet.stopSyncing();
    timedClose(wallet);
    if (removed.progress.get() + removed.blocks.get() + removed.balances.get() > 0) fail("the removed listener got callbacks: " + removed.counts());
    if (started > W1_THREAD_LIMIT) fail(started + " threads started during a scan of " + scanned + " blocks with no listener: the native listener stayed registered with the core");
    assertCallbackThreadsEnded(added.threads);
  }

  /**
   * T4: main returns while background syncing runs and the wallet is still open; the JVM must exit
   * with status 0 and leave no crash report, wherever the core's thread is at that moment.
   */
  private static void exitOpen(String path) throws Exception {
    ZephyrWalletFull wallet = open(path);
    Counting listener = new Counting("none");
    wallet.addListener(listener);
    wallet.startSyncing(1000L);
    awaitFirstProgress(listener);
    log("main returns with the wallet open and syncing: " + listener.counts());
  }
}
