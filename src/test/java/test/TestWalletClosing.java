package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zephyr.common.ZephyrError;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrOutputWallet;
import zephyr.wallet.model.ZephyrTxConfig;
import zephyr.wallet.model.ZephyrWalletConfig;
import zephyr.wallet.model.ZephyrWalletListener;

/**
 * Closing a wallet and delivering listener callbacks, without a node: in-memory and offline wallet
 * files only. The native callbacks are driven by calling the wallet's private callback receiver
 * by reflection, so these tests cover the Java half of the listener rules; the native half
 * (threads attached by the core, close() during a running sync) is covered by
 * {@link TestWalletClosingChildren} in the integration profile.
 */
public class TestWalletClosing {

  /** Values handed to the callbacks; none may appear in the library's log records. */
  private static final String AMOUNT = "987654321987";
  private static final String TX_HASH = "feedc0de00112233445566778899aabbccddeeff00112233445566778899aabb";

  private final List<LogRecord> records = new CopyOnWriteArrayList<LogRecord>();
  private final Handler capture = new Handler() {
    @Override public void publish(LogRecord record) { records.add(record); }
    @Override public void flush() { }
    @Override public void close() { }
  };
  private final List<ZephyrWalletFull> opened = new ArrayList<ZephyrWalletFull>();

  @BeforeEach
  public void captureLog() {
    Logger.getLogger(ZephyrWalletFull.class.getName()).addHandler(capture);
  }

  @AfterEach
  public void releaseLogAndWallets() {
    Logger.getLogger(ZephyrWalletFull.class.getName()).removeHandler(capture);
    for (ZephyrWalletFull wallet : opened) {
      try {
        wallet.close();
      } catch (RuntimeException e) {
        // the test's own outcome counts; a wallet that cannot be closed here is reported by it
      }
    }
  }

  private ZephyrWalletFull inMemoryWallet() {
    ZephyrWalletFull wallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig().setPassword("closing-test").setNetworkType(ZephyrNetworkType.MAINNET));
    opened.add(wallet);
    return wallet;
  }

  // -------------------------- callbacks by reflection -------------------------

  private static Object field(ZephyrWalletFull wallet, String name) throws Exception {
    Field field = ZephyrWalletFull.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(wallet);
  }

  /** The call lock: a thread that holds its read side is inside one of the wallet's calls. */
  private static ReentrantReadWriteLock callLock(ZephyrWalletFull wallet) throws Exception {
    return (ReentrantReadWriteLock) field(wallet, "callLock");
  }

  private static Object callbackReceiver(ZephyrWalletFull wallet) throws Exception {
    Field field = ZephyrWalletFull.class.getDeclaredField("jniListener");
    field.setAccessible(true);
    return field.get(wallet);
  }

  /** Calls one callback of the receiver as the bridge does; a Throwable that escapes is returned. */
  private static Throwable deliver(ZephyrWalletFull wallet, String name, Object... args) throws Exception {
    Object receiver = callbackReceiver(wallet);
    Method target = null;
    for (Method method : receiver.getClass().getDeclaredMethods()) {
      if (method.getName().equals(name) && method.getParameterCount() == args.length) target = method;
    }
    assertNotNull(target, "callback " + name + " with " + args.length + " parameters");
    target.setAccessible(true);
    try {
      target.invoke(receiver, args);
      return null;
    } catch (InvocationTargetException e) {
      return e.getCause();
    }
  }

  private static Throwable deliverSyncProgress(ZephyrWalletFull wallet) throws Exception {
    return deliver(wallet, "onSyncProgress", 5L, 1L, 10L, 0.5d, "Synchronizing");
  }

  private static Throwable deliverNewBlock(ZephyrWalletFull wallet) throws Exception {
    return deliver(wallet, "onNewBlock", 5L);
  }

  private static Throwable deliverBalancesChanged(ZephyrWalletFull wallet, String amount) throws Exception {
    return deliver(wallet, "onBalancesChanged", amount, amount, "ZPH");
  }

  private static Throwable deliverOutputReceived(ZephyrWalletFull wallet, String amount) throws Exception {
    return deliver(wallet, "onOutputReceived", 5L, TX_HASH, amount, "ZPH", 0, 0, 2, 0L, false);
  }

  private static Throwable deliverOutputSpent(ZephyrWalletFull wallet, String amount) throws Exception {
    return deliver(wallet, "onOutputSpent", 5L, TX_HASH, amount, "ZPH", "0", "0", 2, 0L, false);
  }

  /** All five callbacks with well-formed data; the Throwables that escaped, by callback name. */
  private static List<String> deliverAll(ZephyrWalletFull wallet) throws Exception {
    List<String> escaped = new ArrayList<String>();
    Throwable t;
    if ((t = deliverSyncProgress(wallet)) != null) escaped.add("onSyncProgress: " + t);
    if ((t = deliverNewBlock(wallet)) != null) escaped.add("onNewBlock: " + t);
    if ((t = deliverBalancesChanged(wallet, AMOUNT)) != null) escaped.add("onBalancesChanged: " + t);
    if ((t = deliverOutputReceived(wallet, AMOUNT)) != null) escaped.add("onOutputReceived: " + t);
    if ((t = deliverOutputSpent(wallet, AMOUNT)) != null) escaped.add("onOutputSpent: " + t);
    return escaped;
  }

  /** Records every callback by name. */
  private static class Recorder extends ZephyrWalletListener {
    final List<String> calls = new CopyOnWriteArrayList<String>();
    @Override public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) { calls.add("onSyncProgress"); }
    @Override public void onNewBlock(long height) { calls.add("onNewBlock"); }
    @Override public void onBalancesChanged(BigInteger newBalance, BigInteger newUnlockedBalance, String assetType) { calls.add("onBalancesChanged"); }
    @Override public void onOutputReceived(ZephyrOutputWallet output) { calls.add("onOutputReceived"); }
    @Override public void onOutputSpent(ZephyrOutputWallet output) { calls.add("onOutputSpent"); }
  }

  /** Throws from every callback: an Error from onSyncProgress, a RuntimeException from the rest. */
  private static class Thrower extends ZephyrWalletListener {
    int thrown;
    private RuntimeException boom(String name) { thrown++; return new RuntimeException("listener test: thrown from " + name); }
    @Override public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) { thrown++; throw new AssertionError("listener test: Error thrown from onSyncProgress"); }
    @Override public void onNewBlock(long height) { throw boom("onNewBlock"); }
    @Override public void onBalancesChanged(BigInteger newBalance, BigInteger newUnlockedBalance, String assetType) { throw boom("onBalancesChanged"); }
    @Override public void onOutputReceived(ZephyrOutputWallet output) { throw boom("onOutputReceived"); }
    @Override public void onOutputSpent(ZephyrOutputWallet output) { throw boom("onOutputSpent"); }
  }

  private static final List<String> ALL_FIVE = Collections.unmodifiableList(java.util.Arrays.asList(
      "onSyncProgress", "onNewBlock", "onBalancesChanged", "onOutputReceived", "onOutputSpent"));

  private List<LogRecord> warnings() {
    List<LogRecord> warnings = new ArrayList<LogRecord>();
    for (LogRecord record : records) if (record.getLevel().intValue() >= Level.WARNING.intValue()) warnings.add(record);
    return warnings;
  }

  /** A log record's message and parameters, without its Throwable (which is the listener's own). */
  private static String recordText(LogRecord record) {
    StringBuilder text = new StringBuilder(String.valueOf(record.getMessage()));
    if (record.getParameters() != null) for (Object parameter : record.getParameters()) text.append(' ').append(parameter);
    return text.toString();
  }

  // ---------------------------------- tests -----------------------------------

  /**
   * A wrong password reaches the caller as ZephyrError, the library's exception type, not as a
   * plain java.lang.Exception from the native side. The message keeps the core's text.
   */
  @Test
  public void wrongPasswordThrowsZephyrError(@TempDir Path dir) {
    String path = dir.resolve("w").toString();
    ZephyrWalletFull wallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig().setPath(path).setPassword("right password").setNetworkType(ZephyrNetworkType.MAINNET));
    wallet.close();
    ZephyrError e = assertThrows(ZephyrError.class, () -> ZephyrWalletFull.openWallet(path, "wrong password", ZephyrNetworkType.MAINNET));
    assertEquals("invalid password", e.getMessage());
  }

  /**
   * A listener that throws (an Error or a RuntimeException) from any of the five callbacks does
   * not stop the other listeners and does not reach the native caller; each throw is logged once at
   * WARNING, without the callback's data.
   */
  @Test
  public void throwingListenerDoesNotStopTheOthers() throws Exception {
    ZephyrWalletFull wallet = inMemoryWallet();
    Thrower thrower = new Thrower();
    Recorder recorder = new Recorder();
    wallet.addListener(thrower);
    wallet.addListener(recorder);
    List<String> escaped = deliverAll(wallet);
    assertTrue(escaped.isEmpty(), "Throwables reached the native caller: " + escaped);
    assertEquals(ALL_FIVE, recorder.calls, "callbacks of the listener after the throwing one");
    assertEquals(5, thrower.thrown, "throws of the first listener");
    List<LogRecord> warnings = warnings();
    assertEquals(5, warnings.size(), "WARNING records, one per throw: " + warnings.size());
    for (LogRecord warning : warnings) {
      assertNotNull(warning.getThrown(), "the listener's Throwable is attached to the record");
      String text = recordText(warning);
      assertFalse(text.contains(AMOUNT) || text.contains(TX_HASH), "a WARNING carries the callback's data: " + text);
    }
  }

  /** Callback data the library cannot turn into objects is contained the same way. */
  @Test
  public void malformedCallbackDataDoesNotReachTheNativeCaller() throws Exception {
    ZephyrWalletFull wallet = inMemoryWallet();
    Recorder recorder = new Recorder();
    wallet.addListener(recorder);
    List<String> escaped = new ArrayList<String>();
    Throwable t;
    if ((t = deliverBalancesChanged(wallet, "not a number")) != null) escaped.add("onBalancesChanged: " + t);
    if ((t = deliverOutputReceived(wallet, "not a number")) != null) escaped.add("onOutputReceived: " + t);
    if ((t = deliverOutputSpent(wallet, "not a number")) != null) escaped.add("onOutputSpent: " + t);
    assertTrue(escaped.isEmpty(), "Throwables reached the native caller: " + escaped);
    assertTrue(recorder.calls.isEmpty(), "callbacks with data that could not be read: " + recorder.calls);
    assertEquals(3, warnings().size(), "WARNING records, one per malformed callback");
  }

  /** Once the wallet is closed, every callback returns silently and no listener is called. */
  @Test
  public void callbacksAfterCloseAreSilent() throws Exception {
    ZephyrWalletFull wallet = inMemoryWallet();
    Recorder recorder = new Recorder();
    wallet.addListener(recorder);
    wallet.close();
    List<String> escaped = deliverAll(wallet);
    assertTrue(escaped.isEmpty(), "callbacks after close threw: " + escaped);
    assertTrue(recorder.calls.isEmpty(), "listeners called after close: " + recorder.calls);
    assertTrue(warnings().isEmpty(), "WARNING records after close: " + warnings().size());
  }

  /**
   * close() from inside a listener callback is refused with ZephyrError and leaves the wallet
   * open and usable; a later close() from the caller's thread closes it.
   */
  @Test
  public void closeFromACallbackIsRefused() throws Exception {
    ZephyrWalletFull wallet = inMemoryWallet();
    AtomicReference<Throwable> fromClose = new AtomicReference<Throwable>();
    wallet.addListener(new ZephyrWalletListener() {
      @Override public void onNewBlock(long height) {
        try {
          wallet.close();
        } catch (Throwable t) {
          fromClose.set(t);
        }
      }
    });
    assertEquals(null, deliverNewBlock(wallet), "the callback threw");
    assertTrue(fromClose.get() instanceof ZephyrError, "close() from a callback: " + fromClose.get());
    assertFalse(wallet.isClosed(), "the wallet was closed from its own callback");
    assertTrue(wallet.getHeight() >= 0);
    wallet.close();
    assertTrue(wallet.isClosed());
  }

  /**
   * A listener may call the wallet, remove itself and add another listener from inside a callback:
   * the call lock is reentrant for the callback's thread, and the listener set is copied for each
   * delivery.
   */
  @Test
  public void listenerMayUseTheWalletFromACallback() throws Exception {
    ZephyrWalletFull wallet = inMemoryWallet();
    Recorder added = new Recorder();
    List<Long> heights = new CopyOnWriteArrayList<Long>();
    wallet.addListener(new ZephyrWalletListener() {
      @Override public void onNewBlock(long height) {
        heights.add(wallet.getHeight());
        wallet.removeListener(this);
        wallet.addListener(added);
      }
    });
    assertEquals(null, deliverNewBlock(wallet), "the callback threw");
    assertEquals(1, heights.size(), "wallet calls from the callback");
    assertEquals(Collections.singleton(added), wallet.getListeners());
    assertEquals(null, deliverNewBlock(wallet), "the second delivery threw");
    assertEquals(Collections.singletonList("onNewBlock"), added.calls);
  }

  /**
   * close() is idempotent, and two threads closing the same wallet at once both return with the
   * wallet closed (the native wallet is freed once).
   */
  @Test
  public void concurrentCloseIsSafe() throws Exception {
    for (int i = 0; i < 20; i++) {
      ZephyrWalletFull wallet = inMemoryWallet();
      CountDownLatch start = new CountDownLatch(1);
      List<Throwable> failures = new CopyOnWriteArrayList<Throwable>();
      List<Thread> closers = new ArrayList<Thread>();
      for (int j = 0; j < 2; j++) {
        Thread closer = new Thread(() -> {
          try {
            start.await();
            wallet.close();
          } catch (Throwable t) {
            failures.add(t);
          }
        }, "closer-" + j);
        closer.start();
        closers.add(closer);
      }
      start.countDown();
      for (Thread closer : closers) {
        closer.join(TimeUnit.SECONDS.toMillis(60));
        assertFalse(closer.isAlive(), closer.getName() + " still closing after 60 s");
      }
      assertTrue(failures.isEmpty(), "close() threw: " + failures);
      assertTrue(wallet.isClosed());
      wallet.close();
      assertThrows(ZephyrError.class, wallet::getHeight);
    }
  }

  /** The interrupt status of a thread that calls close() survives the close. */
  @Test
  public void closeKeepsTheInterruptStatus() {
    ZephyrWalletFull wallet = inMemoryWallet();
    Thread.currentThread().interrupt();
    try {
      wallet.close();
      assertTrue(Thread.currentThread().isInterrupted(), "interrupt status lost by close()");
      assertTrue(wallet.isClosed());
    } finally {
      Thread.interrupted();
    }
  }

  /** Calls on a closed wallet throw ZephyrError("Wallet is closed"); a wallet call does not hang. */
  @Test
  public void callsAfterCloseThrow() {
    ZephyrWalletFull wallet = inMemoryWallet();
    wallet.close();
    ZephyrError e = assertThrows(ZephyrError.class, wallet::getHeight);
    assertEquals("Wallet is closed", e.getMessage());
    assertThrows(ZephyrError.class, () -> wallet.getReserveProofWallet("message"));
    assertThrows(ZephyrError.class, wallet::getData);
    assertThrows(ZephyrError.class, () -> wallet.changePassword("closing-test", "new"));
  }

  /**
   * close() from a thread that is inside one of the wallet's own calls (it holds the call lock's
   * read side, as a listener of a foreground sync does) is refused, and the wallet stays open; a
   * nested call on that thread enters the call lock again.
   */
  @Test
  public void closeFromInsideACallIsRefused() throws Exception {
    ZephyrWalletFull wallet = inMemoryWallet();
    ReentrantReadWriteLock lock = callLock(wallet);
    lock.readLock().lock();
    try {
      ZephyrError e = assertThrows(ZephyrError.class, wallet::close);
      assertEquals("Cannot close the wallet from inside one of its own calls or listener callbacks", e.getMessage());
      assertFalse(wallet.isClosed(), "the wallet was closed from inside its own call");
      assertTrue(wallet.getHeight() >= 0, "a nested call");
    } finally {
      lock.readLock().unlock();
    }
    wallet.close();
    assertTrue(wallet.isClosed());
  }

  /**
   * A callback delivered on a thread that is inside a call (the way a foreground sync delivers
   * them): the listener's reads enter the call lock again, and close() from there is refused.
   */
  @Test
  public void callbackOnAThreadInsideACall() throws Exception {
    ZephyrWalletFull wallet = inMemoryWallet();
    List<Long> heights = new CopyOnWriteArrayList<Long>();
    AtomicReference<Throwable> fromClose = new AtomicReference<Throwable>();
    wallet.addListener(new ZephyrWalletListener() {
      @Override public void onNewBlock(long height) {
        heights.add(wallet.getHeight());
        try {
          wallet.close();
        } catch (Throwable t) {
          fromClose.set(t);
        }
      }
    });
    ReentrantReadWriteLock lock = callLock(wallet);
    lock.readLock().lock();
    try {
      assertEquals(null, deliverNewBlock(wallet), "the callback threw");
    } finally {
      lock.readLock().unlock();
    }
    assertEquals(1, heights.size(), "reads from the callback");
    assertTrue(fromClose.get() instanceof ZephyrError, "close() from the callback: " + fromClose.get());
    assertFalse(wallet.isClosed());
  }

  /**
   * close() racing calls that run on another thread, 100 rounds on in-memory wallets: no crash,
   * the other thread ends with ZephyrError("Wallet is closed") and nothing else, and close()
   * returns. A native call outside the call lock would be freed under the running call here.
   */
  @Test
  public void closeRacesRunningCalls() throws Exception {
    Random random = new Random(25);
    for (int round = 0; round < 100; round++) {
      ZephyrWalletFull wallet = inMemoryWallet();
      AtomicReference<Throwable> ended = new AtomicReference<Throwable>();
      AtomicInteger loops = new AtomicInteger();
      CountDownLatch running = new CountDownLatch(1);
      Thread caller = new Thread(() -> {
        try {
          while (true) {
            wallet.getData();
            wallet.getAccounts(true);
            wallet.createSubaddress(0);
            wallet.getBalance();
            loops.incrementAndGet();
            running.countDown();
          }
        } catch (Throwable t) {
          ended.set(t);
          running.countDown();
        }
      }, "caller-" + round);
      caller.start();
      assertTrue(running.await(60, TimeUnit.SECONDS), "round " + round + ": the caller did not start");
      Thread.sleep(random.nextInt(5));
      long start = System.nanoTime();
      wallet.close();
      long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      caller.join(TimeUnit.SECONDS.toMillis(30));
      assertFalse(caller.isAlive(), "round " + round + ": the caller still runs 30 s after close()");
      Throwable t = ended.get();
      assertTrue(t instanceof ZephyrError && "Wallet is closed".equals(t.getMessage()), "round " + round + ": the caller ended with " + t);
      assertTrue(ms < TimeUnit.SECONDS.toMillis(30), "round " + round + ": close() took " + ms + " ms");
    }
  }

  private interface Call {
    Object run() throws Exception;
  }

  /** A call's outcome: "returned", or the class and message of what it threw. */
  private static String outcome(Call call) {
    try {
      call.run();
      return "returned";
    } catch (Throwable t) {
      return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
  }

  /**
   * The calls that cannot run inside a listener callback are refused there with ZephyrError,
   * before anything reaches the core: every sync, startSyncing(), scanTxs(), rescanSpent() and
   * rescanBlockchain(), the multisig calls that lock the core's sync mutex, and every call that
   * relays (the core notifies the listeners inline). Reads and the same calls without relay keep
   * working. Offline, on an in-memory wallet without funds: nothing could be built or sent.
   */
  @Test
  public void callsThatCannotRunInACallbackAreRefused() throws Exception {
    ZephyrWalletFull wallet = inMemoryWallet();
    String address = wallet.getPrimaryAddress();
    String hash = String.join("", Collections.nCopies(64, "0"));
    Map<String, Call> refused = new LinkedHashMap<String, Call>();
    refused.put("sync()", wallet::sync);
    refused.put("sync(listener)", () -> wallet.sync(new ZephyrWalletListener()));
    refused.put("sync(startHeight)", () -> wallet.sync(1L));
    refused.put("sync(startHeight, listener)", () -> wallet.sync(1L, new ZephyrWalletListener()));
    refused.put("startSyncing()", () -> { wallet.startSyncing(); return null; });
    refused.put("startSyncing(period)", () -> { wallet.startSyncing(5000L); return null; });
    refused.put("scanTxs", () -> { wallet.scanTxs(Arrays.asList(hash)); return null; });
    refused.put("rescanSpent", () -> { wallet.rescanSpent(); return null; });
    refused.put("rescanBlockchain", () -> { wallet.rescanBlockchain(); return null; });
    refused.put("makeMultisig", () -> wallet.makeMultisig(Arrays.asList("not multisig info"), 2, "closing-test"));
    refused.put("exchangeMultisigKeys", () -> wallet.exchangeMultisigKeys(Arrays.asList("not multisig info"), "closing-test"));
    refused.put("createTx(relay)", () -> wallet.createTx(new ZephyrTxConfig().setAccountIndex(0).setAddress(address).setAmount(BigInteger.ONE).setRelay(true)));
    refused.put("createTxs(relay)", () -> wallet.createTxs(new ZephyrTxConfig().setAccountIndex(0).setAddress(address).setAmount(BigInteger.ONE).setRelay(true)));
    refused.put("sweepUnlocked(relay)", () -> wallet.sweepUnlocked(new ZephyrTxConfig().setAccountIndex(0).setAddress(address).setRelay(true)));
    refused.put("sweepOutput(relay)", () -> wallet.sweepOutput(new ZephyrTxConfig().setAddress(address).setKeyImage(hash).setRelay(true)));
    refused.put("sweepDust(true)", () -> wallet.sweepDust(true));
    refused.put("relayTx", () -> wallet.relayTx("00"));
    refused.put("relayTxs", () -> wallet.relayTxs(Arrays.asList("00")));
    refused.put("submitTxs", () -> wallet.submitTxs("00"));
    Map<String, Call> allowed = new LinkedHashMap<String, Call>();
    allowed.put("getHeight", wallet::getHeight);
    allowed.put("getBalance", wallet::getBalance);
    allowed.put("getAccounts", () -> wallet.getAccounts(true));
    allowed.put("stopSyncing", () -> { wallet.stopSyncing(); return null; });
    allowed.put("createTxs(no relay)", () -> wallet.createTxs(new ZephyrTxConfig().setAccountIndex(0).setAddress(address).setAmount(BigInteger.ONE).setRelay(false)));
    allowed.put("sweepDust(false)", () -> wallet.sweepDust(false));
    Map<String, String> inCallback = new LinkedHashMap<String, String>();
    wallet.addListener(new ZephyrWalletListener() {
      @Override public void onNewBlock(long height) {
        for (Map.Entry<String, Call> call : refused.entrySet()) inCallback.put(call.getKey(), outcome(call.getValue()));
        for (Map.Entry<String, Call> call : allowed.entrySet()) inCallback.put(call.getKey(), outcome(call.getValue()));
      }
    });
    assertEquals(null, deliverNewBlock(wallet), "the callback threw");
    List<String> notRefused = new ArrayList<String>();
    for (String name : refused.keySet()) {
      String result = inCallback.get(name);
      if (!result.startsWith("ZephyrError: ") || !result.contains("from inside a listener callback")) notRefused.add(name + " -> " + result);
    }
    assertTrue(notRefused.isEmpty(), "not refused inside a callback: " + notRefused);
    for (String name : Arrays.asList("getHeight", "getBalance", "getAccounts", "stopSyncing")) assertEquals("returned", inCallback.get(name), name + " inside a callback");
    for (String name : Arrays.asList("createTxs(no relay)", "sweepDust(false)")) {
      assertFalse(inCallback.get(name).contains("from inside a listener callback"), name + " was refused: " + inCallback.get(name));
    }
    // outside a callback the same calls reach the core (offline, so they fail there)
    String outside = outcome(wallet::sync);
    assertFalse(outside.contains("from inside a listener callback"), "sync() outside a callback: " + outside);
  }

  /**
   * The native listener is created and added to the core's listeners by the wallet's first
   * addListener(), and kept until close(): later additions and removals of Java listeners, also
   * from inside a callback, only switch its delivery; the native listener stays the same; close()
   * frees it. A wallet that never gets a listener has none.
   */
  @Test
  public void nativeListenerIsAddedOnceAndOnlySwitched() throws Exception {
    ZephyrWalletFull wallet = inMemoryWallet();
    assertEquals(0L, field(wallet, "jniListenerHandle"), "a native listener before the first addListener()");
    Recorder first = new Recorder();
    wallet.addListener(first);
    long handle = (Long) field(wallet, "jniListenerHandle");
    assertNotEquals(0L, handle, "no native listener after the first addListener()");
    assertEquals(Boolean.TRUE, field(wallet, "listenerDelivery"));
    wallet.removeListener(first);
    assertEquals(Boolean.FALSE, field(wallet, "listenerDelivery"), "delivering with no Java listener");
    assertEquals(handle, field(wallet, "jniListenerHandle"), "the native listener changed");
    wallet.addListener(new ZephyrWalletListener() {
      @Override public void onNewBlock(long height) { wallet.removeListener(this); }
    });
    assertEquals(Boolean.TRUE, field(wallet, "listenerDelivery"));
    assertEquals(null, deliverNewBlock(wallet), "the callback threw");
    assertEquals(Boolean.FALSE, field(wallet, "listenerDelivery"), "delivering after the last listener removed itself");
    wallet.addListener(first);
    assertEquals(Boolean.TRUE, field(wallet, "listenerDelivery"));
    assertEquals(handle, field(wallet, "jniListenerHandle"), "the native listener changed");
    wallet.close();
    assertEquals(0L, field(wallet, "jniListenerHandle"), "the native listener was not freed by close()");
  }

  /**
   * The delivery switch follows the Java listener set when several threads add and remove
   * listeners at once, some of them from inside callbacks: after every interleaving it is on
   * exactly while the set is not empty.
   */
  @Test
  public void deliverySwitchFollowsTheListenerSet() throws Exception {
    ZephyrWalletFull wallet = inMemoryWallet();
    int rounds = 2000;
    CountDownLatch start = new CountDownLatch(1);
    List<String> failures = new CopyOnWriteArrayList<String>();
    List<Thread> threads = new ArrayList<Thread>();
    List<ZephyrWalletListener> kept = new CopyOnWriteArrayList<ZephyrWalletListener>();
    for (int i = 0; i < 4; i++) {
      boolean fromCallback = i % 2 == 1;
      boolean keepLast = !fromCallback; // the listeners of the callback threads are never kept: a stale callback may still add and remove them
      Thread thread = new Thread(() -> {
        ZephyrWalletListener own = new ZephyrWalletListener();
        try {
          start.await();
          for (int n = 0; n < rounds; n++) {
            if (fromCallback) {
              // add and remove from inside a callback delivered on this thread
              ZephyrWalletListener trigger = new ZephyrWalletListener() {
                @Override public void onNewBlock(long height) {
                  wallet.addListener(own);
                  wallet.removeListener(own);
                }
              };
              wallet.addListener(trigger);
              Throwable t = deliverNewBlock(wallet);
              if (t != null) failures.add("escaped: " + t);
              wallet.removeListener(trigger);
            } else {
              wallet.addListener(own);
              wallet.removeListener(own);
            }
          }
          if (keepLast) {
            wallet.addListener(own);
            kept.add(own);
          }
        } catch (Throwable t) {
          failures.add(t.toString());
        }
      }, "switcher-" + i);
      threads.add(thread);
    }
    for (Thread thread : threads) thread.start();
    start.countDown();
    for (Thread thread : threads) {
      thread.join(TimeUnit.SECONDS.toMillis(120));
      assertFalse(thread.isAlive(), thread.getName() + " still running after 120 s");
    }
    assertTrue(failures.isEmpty(), "failures: " + failures);
    assertEquals(2, wallet.getListeners().size(), "listeners left");
    assertEquals(Boolean.TRUE, field(wallet, "listenerDelivery"), "not delivering with listeners registered");
    for (ZephyrWalletListener listener : kept) wallet.removeListener(listener);
    assertEquals(Boolean.FALSE, field(wallet, "listenerDelivery"), "delivering with no listener registered");
  }
}
