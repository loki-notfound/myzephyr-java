package examples;

import java.math.BigInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

  // one thread per wallet: every call goes through it, so close() never runs during sync();
  // background syncing must have been stopped, and its scan finished, before
  static Future<?> closeOnWalletThread(ExecutorService walletThread, ZephyrWalletFull wallet) {
    return walletThread.submit(() -> {
      wallet.save();   // runs after a sync() submitted earlier has returned
      wallet.close();
    });
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

  // background syncing, a workaround: stop it, wait until no callback has come for a while, then close
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
