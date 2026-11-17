package examples;

import zephyr.common.ZephyrUtils;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrWalletConfig;

/**
 * The first program of the user guide (docs/guide/01-getting-started.md, section "A first
 * program"), compiled with the tests so that the guide's code keeps matching the API. Plain Java,
 * no Android classes: an app calls it off the main thread (docs/guide/10-android.md, section "A
 * first test on a device"). On the desktop, main prints the library's version and the address. The
 * tests do not run it.
 */
public class GuideFirstProgram {

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

  public static void main(String[] args) {
    System.out.println("zephyr-java " + ZephyrUtils.getVersion());
    System.out.println("primary address: " + smokeTest());
  }
}
