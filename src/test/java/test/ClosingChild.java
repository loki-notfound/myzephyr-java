package test;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.management.ThreadMXBean;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import zephyr.common.ZephyrError;
import zephyr.daemon.ZephyrDaemonRpc;
import zephyr.daemon.model.ZephyrBlock;
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
 * core's own threads are attached to the JVM as daemon threads (once per thread, on its first
 * callback, and detached when the thread exits), so a thread left attached would no longer keep
 * the JVM alive: the background scenarios check the callback threads themselves instead (each
 * callback thread's {@code java.lang.Thread} must have ended as soon as {@code close()} has
 * returned, which it does only when the bridge detached it). A scenario that sees something wrong
 * prints a line starting with "FAIL" and exits with status 2.
 *
 * <p>Arguments: the scenario, then the wallet path (the keys file of a wallet without a cache,
 * so it scans from its restore height), except for "silent", "freedclose" and "corefailure" (which
 * takes the name of its case and needs no node). The node's uri comes from the system
 * property {@code zephyr.daemon.uri}, the wallet's password from the environment variable
 * {@code ZEPHYR_CHILD_PASSWORD}; neither is printed, and neither are addresses, keys or hashes.</p>
 */
public class ClosingChild {

  private static final long T0 = System.currentTimeMillis();
  private static final long WAIT_MS = 240000;
  /** H1: how long close() may wait right after startSyncing(); a whole catch-up scan of the test wallet takes longer. */
  static final long H1_LIMIT_MS = 3000;
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

  /** Exit status of a child whose precondition does not hold (it could not fail): the parent skips the test. */
  static final int SKIP_STATUS = 3;

  /** For a scenario whose precondition does not hold: a line starting with "SKIP", then status 3. */
  private static void skip(String reason) {
    log("SKIP " + reason);
    System.exit(SKIP_STATUS);
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
      case "churn": churn(args[1]); break;
      case "rescanclose": rescanClose(args[1]); break;
      case "scanclose": scanClose(args[1]); break;
      case "fgtoggle": foregroundToggle(args[1]); break;
      case "h1held": h1Held(args[1], args[2]); break;
      case "freedclose": freedClose(); break;
      case "corefailure": coreFailure(args[1]); break;
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
   * The background scenarios, right after close() returned: every thread that delivered a callback
   * of the core's sync thread must have ended. The bridge attaches that native thread once, on its
   * first callback, and detaches it when the thread exits; the core's close() joins that thread, so
   * the detach has happened before close() returns, and a detached thread's java.lang.Thread has
   * ended. A thread left attached stays alive (as a daemon thread it no longer keeps the JVM from
   * exiting, so the JVM's exit does not show it). The count of distinct callback threads shows how
   * often the bridge attached a thread.
   *
   * <p>The scenarios that use it make no wallet call on their own threads that could deliver a
   * callback (no sync, scan, relay or pool read there; addListener() and removeListener() never
   * deliver one themselves), so a callback on the main thread fails it.</p>
   */
  private static void assertCallbackThreadsEnded(Set<Thread> threads) {
    List<String> alive = new ArrayList<String>();
    int checked = 0;
    for (Thread thread : threads) {
      if (thread == Thread.currentThread()) fail("a background callback ran on the main thread");
      checked++;
      if (thread.isAlive()) alive.add(thread.getName() + (thread.isDaemon() ? "(d)" : "") + " " + thread.getState());
    }
    if (checked == 0) fail("no callback thread of the core was recorded");
    if (!alive.isEmpty()) fail(alive.size() + " of " + checked + " callback thread(s) still alive after close(): " + alive);
    log("all " + checked + " callback thread(s) ended");
  }

  /** Threads the JVM started or had attached so far (every attach of a native thread counts). */
  private static long threadsStarted() {
    return ManagementFactory.getThreadMXBean().getTotalStartedThreadCount();
  }

  /** How many threads were started or attached during a scan that delivered callbacks. */
  private static void logThreadsStarted(long before, Counting listener) {
    int callbacks = listener.progress.get() + listener.blocks.get() + listener.balances.get() + listener.received.get() + listener.spent.get();
    log("THREADS-STARTED " + (threadsStarted() - before) + " during the scan, for " + callbacks + " callbacks on " + listener.threads.size() + " distinct callback thread(s)");
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
    long started = threadsStarted();
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
    assertCallbackThreadsEnded(listener.threads);
    logThreadsStarted(started, listener);
    log("after close: " + listener.counts());
    threads("after close");
  }

  /**
   * N4a: close() from main while another thread is inside a foreground sync(); the sync, cut short,
   * ends with ZephyrError("Wallet is closed").
   */
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
    // the sync was cut short at its first block: it must not return a result that looks complete
    if (!"ZephyrError: Wallet is closed".equals(result)) fail("sync() cut short by close() ended with " + result);
    log("after close: " + listener.counts());
    threads("end");
  }

  /** N4b: close() during a busy background scan. */
  private static void n4b(String path) throws Exception {
    ZephyrWalletFull wallet = open(path);
    Counting listener = new Counting("none");
    wallet.addListener(listener);
    long started = threadsStarted();
    wallet.startSyncing(1000L);
    awaitFirstProgress(listener);
    log("before close: height " + wallet.getHeight() + "; " + listener.counts());
    timedClose(wallet);
    assertCallbackThreadsEnded(listener.threads);
    logThreadsStarted(started, listener);
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
   * blocks in onNewBlock for 2 s. The save must come after that thread has stopped. The wallet
   * starts from its keys file alone, so its cache file is first written by this close(true): the
   * file must not exist yet when the listener lets the refresh go (a save during the hold would have
   * written it), which depends neither on the file system's time stamps (1 or 2 s granularity on
   * some) nor on where the core's batches of blocks end. The reopened wallet must hold the block the
   * refresh was held at; it holds the rest of that batch too, unless that block ended its batch (the
   * refresh checks its stop flag between batches only). The file's time is logged only.
   */
  private static void h2(String path) throws Exception {
    ZephyrWalletFull wallet = open(path);
    long restoreHeight = wallet.getRestoreHeight();
    File cache = new File(path);
    if (cache.exists()) fail("the wallet's cache file exists before close(true): the check of when it is written cannot work");
    AtomicReference<Boolean> writtenDuringHold = new AtomicReference<Boolean>();
    CountDownLatch blocked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicBoolean armed = new AtomicBoolean();
    AtomicLong heldAt = new AtomicLong(-1);
    Counting listener = new Counting("none") {
      @Override public void onNewBlock(long height) {
        super.onNewBlock(height);
        if (armed.compareAndSet(true, false)) {
          heldAt.set(height);
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
      writtenDuringHold.set(cache.exists());
      log("releasing the listener; the wallet's cache file " + (writtenDuringHold.get() ? "exists" : "does not exist yet"));
      release.countDown();
    }, "releaser");
    releaser.start();
    long start = System.currentTimeMillis();
    wallet.close(true);
    long end = System.currentTimeMillis();
    log("CLOSE-WAIT-MS " + (end - start));
    assertCallbackThreadsEnded(listener.threads);
    releaser.join();
    if (!cache.isFile()) fail("no wallet file after close(true)");
    long written = cache.lastModified();
    log("H2-GAP-MS " + (end - written) + " (the wallet file's time is " + (written - start) + " ms after close(true) started; logged only)");
    if (writtenDuringHold.get()) fail("the wallet's cache file was written while the listener held the refresh: the save ran while the core's sync thread was still inside its refresh");
    ZephyrWalletFull reopened = ZephyrWalletFull.openWallet(path, password(), ZephyrNetworkType.MAINNET); // offline
    long height = reopened.getHeight();
    reopened.close();
    // the core calls onNewBlock(h) once block h is in the wallet's chain (height h + 1)
    log("reopened offline: height " + height + ", restore height " + restoreHeight + ", held at block " + heldAt.get()
        + (height == heldAt.get() + 1 ? " (the held block ended its batch)" : ""));
    if (height <= restoreHeight) fail("the saved wallet holds no scanned block: height " + height);
    if (height < heldAt.get() + 1) fail("the saved wallet ends at height " + height + ", below the block the listener held the refresh at (" + heldAt.get() + ")");
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
   * W1: a listener added and removed again leaves the native listener switched off, so a background
   * scan with no listener makes no callback upcalls: the wallet's own count of the callbacks that
   * reached Java (read by reflection) must not grow during that scan. The count of threads the JVM
   * started during the scan, which showed the upcalls while the bridge attached a thread for every
   * callback, is still logged and limited; with one attach per thread it can no longer show them.
   * A listener added during the scan receives callbacks again.
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
    long upcallsBefore = upcalls(wallet);
    wallet.startSyncing(1000L);
    long target = Math.min(from + 2500, daemonHeight - 1);
    long end = System.currentTimeMillis() + WAIT_MS;
    while (wallet.getHeight() < target) {
      if (System.currentTimeMillis() > end) fail("the scan did not reach height " + target + " within " + WAIT_MS + " ms");
      Thread.sleep(50);
    }
    long started = threadBean.getTotalStartedThreadCount() - startedBefore;
    long upcalls = upcalls(wallet) - upcallsBefore;
    long scanned = wallet.getHeight() - from;
    log("W1-THREADS-STARTED " + started + " and W1-UPCALLS " + upcalls + " while " + scanned + " blocks were scanned with no listener registered");
    Counting added = new Counting("none");
    wallet.addListener(added);
    awaitFirstProgress(added);
    log("the listener added during the scan got callbacks: " + added.counts());
    wallet.removeListener(added);
    wallet.stopSyncing();
    timedClose(wallet);
    if (removed.progress.get() + removed.blocks.get() + removed.balances.get() > 0) fail("the removed listener got callbacks: " + removed.counts());
    if (upcalls > 0) fail(upcalls + " callbacks reached Java during a scan of " + scanned + " blocks with no listener: the native listener kept delivering");
    if (started > W1_THREAD_LIMIT) fail(started + " threads started during a scan of " + scanned + " blocks with no listener: the native listener stayed registered with the core");
    // main added and removed the listeners, during the scan too: neither call delivers a callback itself
    if (added.threads.contains(Thread.currentThread()) || removed.threads.contains(Thread.currentThread())) fail("a callback was delivered on main, inside its own addListener() or removeListener()");
    assertCallbackThreadsEnded(added.threads);
  }

  /** The wallet's count of callbacks the bridge delivered into Java (a private field, for the tests only). */
  private static long upcalls(ZephyrWalletFull wallet) throws Exception {
    Field listenerField = ZephyrWalletFull.class.getDeclaredField("jniListener");
    listenerField.setAccessible(true);
    Object listener = listenerField.get(wallet);
    Field upcallsField = listener.getClass().getDeclaredField("upcalls");
    upcallsField.setAccessible(true);
    return ((AtomicLong) upcallsField.get(listener)).get();
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

  /** F1: blocks the churn scenario scans while the listener is toggled. */
  static final long CHURN_BLOCKS = 6000;

  /**
   * F1 of the review of S1b: a listener added and removed in a tight loop on one thread while the
   * core's sync thread scans in the background, and removing itself from its own onNewBlock: the
   * set of listeners the core copies several times per block must not change under that copy. The
   * JVM must not crash; the toggled listener receives callbacks while it is registered. The
   * wallet's first listener is added (and removed) before startSyncing(), as the Javadoc of
   * addListener() asks: that first addition is the one change of the core's set that remains.
   *
   * <p>A smoke test: a crash shows the race, a pass does not prove it absent (without a memory
   * checker, a copy of a one-element set taken during a change mostly survives). That later
   * switches never call the core's set is proven offline, by the native listener's own count
   * ({@code TestWalletClosing.coreListenerSetIsCalledOnlyByTheFirstAddition}).</p>
   */
  private static void churn(String path) throws Exception {
    ZephyrWalletFull wallet = open(path);
    long from = Math.max(wallet.getHeight(), wallet.getRestoreHeight());
    long target = Math.min(from + CHURN_BLOCKS, wallet.getDaemonHeight() - 1);
    AtomicBoolean stop = new AtomicBoolean();
    AtomicLong toggles = new AtomicLong(), selfRemovals = new AtomicLong();
    AtomicReference<Throwable> unexpected = new AtomicReference<Throwable>();
    Counting toggled = new Counting("none") {
      @Override public void onNewBlock(long height) {
        super.onNewBlock(height);
        try {
          wallet.removeListener(this);
          selfRemovals.incrementAndGet();
        } catch (ZephyrError e) {
          // removed by the churner meanwhile
        }
      }
    };
    wallet.addListener(toggled);
    wallet.removeListener(toggled);
    wallet.startSyncing(1000L);
    Thread churner = new Thread(() -> {
      while (!stop.get()) {
        try {
          wallet.addListener(toggled);
          try {
            wallet.removeListener(toggled);
          } catch (ZephyrError e) {
            if (!String.valueOf(e.getMessage()).contains("not registered")) throw e; // removed itself from its callback
          }
          toggles.incrementAndGet();
        } catch (Throwable t) {
          unexpected.compareAndSet(null, t);
          return;
        }
      }
    }, "churner");
    churner.start();
    long end = System.currentTimeMillis() + WAIT_MS;
    while (wallet.getHeight() < target && churner.isAlive()) {
      if (System.currentTimeMillis() > end) fail("the scan did not reach height " + target + " within " + WAIT_MS + " ms");
      Thread.sleep(50);
    }
    stop.set(true);
    churner.join(WAIT_MS);
    if (unexpected.get() != null) fail("the churner ended with " + unexpected.get());
    log("CHURN " + toggles.get() + " add/remove pairs and " + selfRemovals.get() + " removals from the callback while " + (wallet.getHeight() - from) + " blocks were scanned; " + toggled.counts());
    wallet.stopSyncing();
    timedClose(wallet);
    if (toggled.blocks.get() == 0) fail("the toggled listener never received a callback");
    // the churner only adds and removes the listener: neither call delivers a callback itself (the
    // churner has ended by now, so the check of the threads below would not see it)
    if (toggled.threads.contains(churner)) fail("a callback was delivered on the churner, inside its own addListener() or removeListener()");
    assertCallbackThreadsEnded(toggled.threads);
  }

  /**
   * F3 of the review of S1b: close() while another thread's rescanBlockchain() runs: the rescan,
   * cut short, ends with ZephyrError("Wallet is closed"), never as if it had completed. A rescan
   * delivers no sync progress on this wallet, so close() comes once the wallet's height has moved
   * 200 blocks past the restore height.
   */
  private static void rescanClose(String path) throws Exception {
    ZephyrWalletFull wallet = open(path);
    long restoreHeight = wallet.getRestoreHeight();
    AtomicReference<String> outcome = new AtomicReference<String>();
    Thread rescanner = new Thread(() -> outcome.set(attempt("rescanBlockchain()", () -> { wallet.rescanBlockchain(); return "returned"; })), "rescanner");
    rescanner.start();
    long end = System.currentTimeMillis() + WAIT_MS;
    while (wallet.getHeight() < restoreHeight + 200) {
      if (!rescanner.isAlive()) fail("rescanBlockchain() ended before its refresh was under way: " + outcome.get());
      if (System.currentTimeMillis() > end) fail("the wallet's height did not pass " + (restoreHeight + 200) + " within " + WAIT_MS + " ms");
      Thread.sleep(20);
    }
    log("before close: height " + wallet.getHeight());
    timedClose(wallet);
    rescanner.join(WAIT_MS);
    if (rescanner.isAlive()) fail("the rescanner did not end");
    log(outcome.get());
    if (!"rescanBlockchain(): ZephyrError: Wallet is closed".equals(outcome.get())) fail("rescanBlockchain() cut short by close() ended with " + outcome.get());
  }

  /**
   * F3 of the review of S1b: close() while another thread's scanTxs() refreshes the wallet: the
   * scan, cut short, ends with ZephyrError("Wallet is closed"). The transaction is one of a block
   * shortly above the wallet's restore height, read from the node (read-only; its hash is not
   * printed): the core processes it, moves the wallet's refresh up to that block and refreshes
   * from there to the tip. That refresh delivers no sync progress, so close() comes once the
   * wallet's height has moved 200 blocks past the transaction's block.
   */
  private static void scanClose(String path) throws Exception {
    String uri = System.getProperty("zephyr.daemon.uri");
    if (uri == null) fail("zephyr.daemon.uri not set");
    String txHash = null;
    long txHeight = -1;
    ZephyrWalletFull wallet = open(path);
    long restoreHeight = wallet.getRestoreHeight();
    ZephyrDaemonRpc daemon = new ZephyrDaemonRpc(uri);
    for (long height = restoreHeight + 10; height < restoreHeight + 400 && txHash == null; height++) {
      ZephyrBlock block = daemon.getBlockByHeight(height);
      if (block.getTxHashes() != null && !block.getTxHashes().isEmpty()) {
        txHash = block.getTxHashes().get(0);
        txHeight = height;
      }
    }
    daemon.getRpcConnection().setCredentials(null, null); // closes its HTTP client and with it its connection to the node
    if (txHash == null) fail("no transaction in the 390 blocks above the restore height");
    log("scanTxs() of a transaction in block " + txHeight + " (restore height " + restoreHeight + ")");
    String hash = txHash;
    AtomicReference<String> outcome = new AtomicReference<String>();
    Thread scanner = new Thread(() -> outcome.set(attempt("scanTxs()", () -> { wallet.scanTxs(Arrays.asList(hash)); return "returned"; })), "scanner");
    scanner.start();
    long end = System.currentTimeMillis() + WAIT_MS;
    while (wallet.getHeight() < txHeight + 200) {
      if (!scanner.isAlive()) fail("scanTxs() ended before its refresh was under way: " + outcome.get());
      if (System.currentTimeMillis() > end) fail("the wallet's height did not pass block " + (txHeight + 200) + " within " + WAIT_MS + " ms");
      Thread.sleep(20);
    }
    log("before close: height " + wallet.getHeight());
    timedClose(wallet);
    scanner.join(WAIT_MS);
    if (scanner.isAlive()) fail("the scanner did not end");
    log(outcome.get());
    if (!"scanTxs(): ZephyrError: Wallet is closed".equals(outcome.get())) fail("scanTxs() cut short by close() ended with " + outcome.get());
  }

  /**
   * Listeners changed from main while a foreground sync() delivers callbacks: a listener added then
   * receives callbacks at once, and once the last listener is removed none of them receives a
   * callback after those in flight have ended; neither change waits for the sync to end.
   */
  private static void foregroundToggle(String path) throws Exception {
    ZephyrWalletFull wallet = open(path);
    Counting first = new Counting("none");
    wallet.addListener(first);
    AtomicReference<String> outcome = new AtomicReference<String>();
    Thread syncer = new Thread(() -> outcome.set(attempt("sync()", () -> "returned blocks fetched " + wallet.sync().getNumBlocksFetched())), "syncer");
    syncer.start();
    awaitFirstProgress(first);
    Counting second = new Counting("none");
    long start = System.nanoTime();
    wallet.addListener(second);
    log("addListener() during the sync returned after " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + " ms");
    awaitFirstProgress(second);
    log("the listener added during the sync got callbacks: " + second.counts());
    start = System.nanoTime();
    wallet.removeListener(first);
    wallet.removeListener(second);
    log("removing both listeners during the sync returned after " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + " ms");
    Thread.sleep(100); // a callback in flight may still reach them
    String settled = first.counts() + " / " + second.counts();
    long observed = System.nanoTime();
    while (syncer.isAlive() && System.nanoTime() - observed < TimeUnit.MILLISECONDS.toNanos(500)) Thread.sleep(10);
    long observedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - observed);
    String later = first.counts() + " / " + second.counts();
    if (!later.equals(settled)) fail("a removed listener still receives callbacks: " + settled + " then " + later);
    if (observedMs < 200) fail("the sync ended " + observedMs + " ms after the removal: too soon to see whether the removed listeners got callbacks");
    log("no callback for the removed listeners during " + observedMs + " ms of the running sync");
    timedClose(wallet);
    syncer.join(WAIT_MS);
    if (syncer.isAlive()) fail("the syncer did not end");
    log(outcome.get());
    if (!outcome.get().equals("sync(): ZephyrError: Wallet is closed") && !outcome.get().startsWith("sync(): returned")) fail("sync() ended with " + outcome.get());
  }

  /** H1, held window: seconds the core caches the node's height (30) and a margin for its 1 s clock. */
  static final long H1_CACHE_S = 32;
  /** H1, held window: blocks the wallet must be behind the node, so that a lost stop costs a long scan. */
  static final long H1_MIN_BEHIND = 2000;

  /**
   * H1 with the window reached on purpose. The wallet talks to the node through a local
   * {@link Forwarder}. The core caches the node's height for 30 s from its connection check; once
   * that has expired, the core's sync thread asks the node for it again (get_info) at the start of
   * its sync, before its refresh begins and resets the core's stop flag. The forwarder holds that
   * request; close() (after stopSyncing() in the "stop" variant) comes while it is held, and the
   * request goes on 500 ms after close() began. A stop that is not repeated is lost then, and close()
   * waits for the whole catch-up scan; with the stop repeated, close() returns within the limit.
   *
   * <p>That holds only when the released sync thread would scan for long: the core refreshes only
   * when the node is synced with its peers, and the scan is long only when the wallet is far behind.
   * The child checks both before it arms the forwarder (from the node heights the core has cached
   * since its connection check) and skips, with the reason, when either does not hold.</p>
   */
  private static void h1Held(String path, String variant) throws Exception {
    String uri = System.getProperty("zephyr.daemon.uri");
    if (uri == null) fail("zephyr.daemon.uri not set");
    URI node = new URI(uri);
    Forwarder forwarder = new Forwarder(node.getHost(), node.getPort());
    ZephyrWalletFull wallet = ZephyrWalletFull.openWallet(path, password(), ZephyrNetworkType.MAINNET, "http://127.0.0.1:" + forwarder.port());
    long behind = wallet.getDaemonHeight() - Math.max(wallet.getHeight(), wallet.getRestoreHeight());
    boolean nodeSynced = wallet.isDaemonSynced();
    long connected = System.nanoTime(); // the core's cached node heights expire 30 s after its last request, at the latest this one
    log("opened through the local forwarder; blocks behind the node: " + behind + "; node synced with its peers: " + nodeSynced);
    if (!nodeSynced) skip("the node is not synced with its peers: the core's sync thread would not refresh, so a lost stop would cost nothing and the test could not fail");
    if (behind < H1_MIN_BEHIND) skip("the wallet is " + behind + " blocks behind the node, fewer than " + H1_MIN_BEHIND + ": a lost stop would cost a short scan only, and the test could not fail");
    Counting listener = new Counting("none");
    wallet.addListener(listener);
    long waitMs = TimeUnit.NANOSECONDS.toMillis(TimeUnit.SECONDS.toNanos(H1_CACHE_S) - (System.nanoTime() - connected));
    log("waiting " + waitMs + " ms until the core's cached node height has expired");
    if (waitMs > 0) Thread.sleep(waitMs);
    forwarder.holdNext("\"get_info\"");
    wallet.startSyncing(1000L);
    if (!forwarder.awaitHeld(20000)) fail("the core's sync thread did not ask the node for its height within 20 s: the window before its refresh was not reached");
    log("the core's sync thread waits for the node's answer to get_info, before its refresh");
    if (variant.equals("stop")) wallet.stopSyncing();
    Thread releaser = new Thread(() -> {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        // let it go now
      }
      log("the forwarder lets the request go");
      forwarder.release();
    }, "releaser");
    releaser.start();
    log((variant.equals("stop") ? "stopSyncing() and " : "") + "close() while the request is held");
    long ms = timedClose(wallet);
    forwarder.close();
    log("after close: " + listener.counts());
    if (ms > H1_LIMIT_MS) fail("close() with the core's sync thread held before its refresh" + (variant.equals("stop") ? " (after stopSyncing())" : "") + " waited " + ms + " ms (limit " + H1_LIMIT_MS + " ms): the stop was lost");
    if (!listener.threads.isEmpty()) assertCallbackThreadsEnded(listener.threads);
  }

  private static Field walletField(String name) throws Exception {
    Field field = ZephyrWalletFull.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  /**
   * C1 of the review of S1c: close() again after the native close freed the wallet but threw an
   * Error before Java recorded it (a save at close that fails with the core's std::bad_alloc, which
   * reaches Java as OutOfMemoryError: closeJni has freed the wallet and zeroed its handle, and
   * close() caught only Exception). No run here can make the core run out of memory in its save, so
   * the state is made by reflection: a normal close(), then the wallet's "freed" flag cleared again.
   * close() and close(true) must then return at once without a native call on the freed wallet
   * (which would end this JVM), and record the wallet as freed. No node needed.
   */
  private static void freedClose() throws Exception {
    ZephyrWalletFull wallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig().setPassword("freed").setNetworkType(ZephyrNetworkType.MAINNET));
    wallet.addListener(new ZephyrWalletListener());
    wallet.close();
    Field handle = walletField("jniWalletHandle"), freed = walletField("isFreed"), walletPassword = walletField("password");
    if ((Long) handle.get(wallet) != 0L) fail("the native wallet was not freed by close()");
    freed.set(wallet, false);          // as close() left it when closeJni freed the wallet and threw an Error
    walletPassword.set(wallet, "freed"); // and the password with it
    log("the wallet is freed, its handle zero, and marked as not freed");
    long start = System.nanoTime();
    wallet.close();      // reached stopSyncingJni() on the freed wallet
    wallet.close(true);
    log("CLOSE-WAIT-MS " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    if (!(Boolean) freed.get(wallet)) fail("close() did not record the freed wallet as freed");
    if (walletPassword.get(wallet) != null) fail("close() kept the password of the freed wallet");
    if (!wallet.isClosed()) fail("isClosed() false");
    try {
      wallet.getHeight();
      fail("getHeight() on the freed wallet returned");
    } catch (ZephyrError e) {
      log("getHeight() threw ZephyrError: " + e.getMessage());
    }
  }

  /** 2^64: one more than the largest value the core's unsigned 64-bit fields hold. */
  private static final BigInteger TWO_TO_THE_64 = BigInteger.ONE.shiftLeft(64);

  /**
   * One call whose core function throws, offline (an in-memory wallet, no node, relay off, nothing
   * built): the call must throw ZephyrError with the core's message (for "reserveproof", the
   * message of the bridge's own conversion of the amount). Up to the bridge change these exceptions
   * left the native call outside a try block, which ended the process (SIGABRT). The case
   * "largebinary" must return: the bridge copied the converted bytes through an array on the native
   * stack, which overflowed (SIGSEGV) for a result of more than about 1 MB.
   */
  private static void coreFailure(String which) throws Exception {
    if (which.equals("largebinary")) {
      char[] text = new char[4000000];
      Arrays.fill(text, 'a');
      java.util.Map<String, Object> map = new java.util.HashMap<String, Object>();
      map.put("text", new String(text));
      byte[] binary = zephyr.common.ZephyrUtils.mapToBinary(map);
      if (binary.length <= text.length) fail("mapToBinary() returned " + binary.length + " bytes");
      log("mapToBinary() of a 4,000,000-character string returned " + binary.length + " bytes");
      return;
    }
    if (which.equals("blocks")) {
      // a binary blocks response whose block blob does not parse
      java.util.Map<String, Object> block = new java.util.HashMap<String, Object>();
      block.put("block", "00");
      java.util.Map<String, Object> response = new java.util.HashMap<String, Object>();
      response.put("blocks", Arrays.asList(block));
      byte[] binary = zephyr.common.ZephyrUtils.mapToBinary(response);
      expectCoreFailure(which, () -> zephyr.common.ZephyrUtils.binaryBlocksToMap(binary), "failed to parse block blob at index 0");
      return;
    }
    ZephyrWalletFull wallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig().setPassword("core-failure").setNetworkType(ZephyrNetworkType.MAINNET));
    try {
      String address = wallet.getPrimaryAddress();
      // a destination amount the core's config parser cannot hold; its message names the C++ type
      ZephyrTxConfig tooLarge = new ZephyrTxConfig().setAddress(address).setAmount(TWO_TO_THE_64);
      String unconvertible = "conversion of data to type";
      switch (which) {
        case "createsubaddress": expectCoreFailure(which, () -> wallet.createSubaddress(1000, "label"), "account index is out of bound"); break;
        case "createtx": expectCoreFailure(which, () -> wallet.createTx(tooLarge), unconvertible); break;
        case "createtxfee": expectCoreFailure(which, () -> wallet.createTx(new ZephyrTxConfig().setAddress(address).setAmount(BigInteger.ONE).setFee(TWO_TO_THE_64)), unconvertible); break;
        case "sweepunlocked": expectCoreFailure(which, () -> wallet.sweepUnlocked(tooLarge), unconvertible); break;
        case "sweepoutput": expectCoreFailure(which, () -> wallet.sweepOutput(tooLarge.copy().setKeyImage("00")), unconvertible); break;
        case "paymenturi": expectCoreFailure(which, () -> wallet.getPaymentUri(tooLarge), unconvertible); break;
        // an amount the bridge itself cannot convert to the core's unsigned 64-bit type
        case "reserveproof": expectCoreFailure(which, () -> wallet.getReserveProofAccount(0, TWO_TO_THE_64, "message"), "bad lexical cast"); break;
        default: fail("unknown case " + which);
      }
    } finally {
      wallet.close();
    }
  }

  /** Runs the call; it must throw ZephyrError whose message starts with the expected text. */
  private static void expectCoreFailure(String which, Runnable call, String expected) {
    try {
      call.run();
      fail(which + ": returned normally");
    } catch (ZephyrError e) {
      if (e.getMessage() == null || !e.getMessage().startsWith(expected)) fail(which + ": ZephyrError(\"" + e.getMessage() + "\"), expected a message starting with \"" + expected + "\"");
      log(which + ": ZephyrError(\"" + e.getMessage() + "\")");
    }
  }

  /**
   * A local TCP forwarder to the node for one child: each connection the wallet opens gets its own
   * connection to the node, and bytes are copied both ways unchanged. Once armed with
   * {@link #holdNext}, it holds the first piece of a request from the wallet that contains the
   * marker until {@link #release}.
   */
  private static class Forwarder {
    private final ServerSocket server;
    private final String host;
    private final int port;
    private final AtomicReference<byte[]> marker = new AtomicReference<byte[]>();
    private final CountDownLatch held = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);
    private final List<Socket> sockets = new CopyOnWriteArrayList<Socket>();

    Forwarder(String host, int port) throws Exception {
      this.host = host;
      this.port = port;
      server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
      Thread acceptor = new Thread(this::accept, "forwarder");
      acceptor.setDaemon(true);
      acceptor.start();
    }

    int port() { return server.getLocalPort(); }
    void holdNext(String text) { marker.set(text.getBytes(StandardCharsets.US_ASCII)); }
    boolean awaitHeld(long ms) throws InterruptedException { return held.await(ms, TimeUnit.MILLISECONDS); }
    void release() { released.countDown(); }

    void close() {
      release();
      try {
        server.close();
      } catch (Exception e) {
        // closed
      }
      for (Socket socket : sockets) {
        try {
          socket.close();
        } catch (Exception e) {
          // closed
        }
      }
    }

    private void accept() {
      try {
        while (true) {
          Socket wallet = server.accept();
          Socket node = new Socket(host, port);
          sockets.add(wallet);
          sockets.add(node);
          pump(wallet, node, true);
          pump(node, wallet, false);
        }
      } catch (Exception e) {
        // the forwarder was closed
      }
    }

    private void pump(Socket from, Socket to, boolean fromWallet) {
      Thread thread = new Thread(() -> {
        byte[] buffer = new byte[65536];
        try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
          int n;
          while ((n = in.read(buffer)) > 0) {
            byte[] m = marker.get();
            if (fromWallet && m != null && contains(buffer, n, m) && marker.compareAndSet(m, null)) {
              held.countDown();
              released.await();
            }
            out.write(buffer, 0, n);
            out.flush();
          }
        } catch (Exception e) {
          // one side closed its connection: both are closed
        }
      }, fromWallet ? "forwarder-to-node" : "forwarder-to-wallet");
      thread.setDaemon(true);
      thread.start();
    }

    private static boolean contains(byte[] buffer, int length, byte[] marker) {
      outer:
      for (int i = 0; i + marker.length <= length; i++) {
        for (int j = 0; j < marker.length; j++) if (buffer[i + j] != marker[j]) continue outer;
        return true;
      }
      return false;
    }
  }
}
