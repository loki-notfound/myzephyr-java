package examples;

import java.io.File;
import zephyr.common.ZephyrTls;

/**
 * The TLS configurations of the user guide (docs/guide/03-connecting-to-a-node.md, section "TLS to a
 * node"), compiled with the tests so that the guide's code keeps matching the API. Plain Java, no
 * Android classes: an app passes its own directories (getFilesDir()) and calls the methods off the
 * main thread. The tests do not run them.
 */
public class GuideTls {

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
