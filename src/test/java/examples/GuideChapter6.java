package examples;

import java.io.File;
import zephyr.common.ZephyrTls;
import zephyr.common.ZephyrUtils;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrWalletConfig;

/**
 * The code of chapter 6 of the user guide (docs/guide/06-android.md): the first smoke test and the
 * TLS configurations, compiled with the tests so that the guide's code keeps matching the API.
 * Plain Java, no Android classes: an app passes its own directories (getFilesDir()) and calls the
 * methods off the main thread. The tests do not run them.
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

  // TLS: a node whose certificate comes from your own CA, or a self-signed node certificate whose
  // name matches the node's host name: trust it in addition to the shipped root certificates
  public static void trustOwnCa(File caPem) {
    ZephyrTls.addTrustedCertificates(caPem);  // before the wallet is created or its connection set
  }

  // TLS: also the certificates the user installed on the device, for example a company CA
  public static void trustUserCertificates() {
    ZephyrTls.setUseUserCertificates(true);
  }

  // TLS: the device's system store as well as the shipped root certificates
  public static void trustSystemStore() {
    ZephyrTls.setUseSystemCertificates(true);
  }

  // TLS: exactly one file and nothing else, for example a bundle the app maintains itself
  public static void trustExactlyOneFile(File pem) {
    ZephyrTls.setCaFile(pem);
  }

  // TLS: at start, the root certificates downloaded earlier, when there are any
  public static void addDownloadedRoots(File filesDir) {
    File roots = new File(filesDir, "cacert.pem");
    if (roots.isFile()) ZephyrTls.addTrustedCertificates(roots);
  }

  // TLS: the download of the current root certificates (curl.se), on a background thread, at most
  // once a day; the next start adds them with addDownloadedRoots
  public static int downloadRoots(File filesDir) {
    return ZephyrTls.downloadCertificates(new File(filesDir, "cacert.pem"));
  }
}
