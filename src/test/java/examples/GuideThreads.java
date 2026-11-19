package examples;

import java.math.BigInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrWalletListener;

/**
 * The thread rules of the user guide (docs/guide/04-threads-and-listeners.md), compiled with the
 * tests so that the guide's code keeps matching the API. The tests do not run it.
 */
public class GuideThreads {

  // keep callbacks short: hand the event to an executor and return at once
  static void listenOnExecutor(ZephyrWalletFull wallet) {
    ExecutorService events = Executors.newSingleThreadExecutor();
    wallet.addListener(new ZephyrWalletListener() {
      @Override
      public void onBalancesChanged(BigInteger balance, BigInteger unlockedBalance, String assetType) {
        events.submit(() -> showBalance(assetType, balance, unlockedBalance));  // return at once
      }
    });
  }

  // catch every exception inside a callback; submit() itself throws once the executor is shut down
  static void listenSafely(ZephyrWalletFull wallet, ExecutorService events) {
    wallet.addListener(new ZephyrWalletListener() {
      @Override
      public void onNewBlock(long height) {
        try {
          events.submit(() -> showHeight(height));
        } catch (Exception e) {  // nothing may leave the callback
          logError(e);
        }
      }
    });
  }

  // one thread per wallet: it syncs on a schedule and saves between two syncs, so a save never
  // meets a refresh (background syncing is not used)
  static void syncOnWalletThread(ScheduledExecutorService walletThread, ZephyrWalletFull wallet) {
    walletThread.scheduleWithFixedDelay(() -> {
      try {
        wallet.sync();
        wallet.save();  // between two syncs on this thread: no refresh runs
      } catch (Exception e) {
        logError(e);    // an exception that left the task would end the schedule
      }
    }, 0, 30, TimeUnit.SECONDS);
  }

  // closing on the wallet thread: close(true) stops a sync, waits for the calls in flight and the
  // core's background sync thread, then saves and closes
  static Future<?> closeOnWalletThread(ExecutorService walletThread, ZephyrWalletFull wallet) {
    return walletThread.submit(() -> wallet.close(true));
  }

  // a listener that notes when the last callback came; add it before startSyncing()
  static AtomicLong watchCallbacks(ZephyrWalletFull wallet) {
    AtomicLong lastCallbackMillis = new AtomicLong(System.currentTimeMillis());
    wallet.addListener(new ZephyrWalletListener() {
      @Override
      public void onNewBlock(long height) {
        lastCallbackMillis.set(System.currentTimeMillis());
      }
    });
    return lastCallbackMillis;
  }

  // releases up to 2.3.0-sec.2-java.4 only, a workaround for background syncing: stop it, wait until
  // no callback has come for a while, then close (2.3.0-sec.2-java.5 refuses save() in a callback
  // and makes close() wait by itself)
  static void stopAndClose(ZephyrWalletFull wallet, AtomicLong lastCallbackMillis, long quietMillis) throws InterruptedException {
    wallet.stopSyncing();  // returns at once; the batch in progress goes on
    while (System.currentTimeMillis() - lastCallbackMillis.get() < quietMillis) {
      Thread.sleep(100);   // no call reports whether a scan still runs, so wait for quiet
    }
    wallet.save();
    wallet.close();
  }

  private static void showBalance(String assetType, BigInteger balance, BigInteger unlockedBalance) {
    System.out.println(assetType + ": " + balance + " (unlocked " + unlockedBalance + ")");
  }

  private static void showHeight(long height) {
    System.out.println("block " + height);
  }

  private static void logError(Exception e) {
    System.err.println("listener failed: " + e);
  }
}
