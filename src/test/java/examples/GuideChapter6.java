package examples;

import zephyr.common.ZephyrUtils;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrWalletConfig;

/**
 * The first smoke test of chapter 6 of the user guide (docs/guide/06-android.md), compiled with
 * the tests so that the guide's code keeps matching the API. Plain Java, no Android classes: an
 * app calls it off the main thread and logs the result. The tests do not run it.
 */
public class GuideChapter6 {

  // loads the native library, creates an offline wallet in memory and returns its primary address
  public static String smokeTest() {
    ZephyrUtils.loadNativeLibrary();
    ZephyrWalletFull wallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
        .setNetworkType(ZephyrNetworkType.MAINNET)
        .setPassword("smoke-test"));  // no path and no node: nothing touches storage or the network
    try {
      return wallet.getPrimaryAddress();
    } finally {
      wallet.close();
    }
  }
}
