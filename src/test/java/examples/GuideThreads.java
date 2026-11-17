package examples;

import java.math.BigInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    return walletThread.submit(() -> wallet.close(true));  // runs after a sync() submitted earlier has returned
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
