package examples;

import zephyr.common.ZephyrRpcConnection;
import zephyr.common.ZephyrUtils;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrWalletConfig;

/**
 * The wallet life cycle of the user guide (docs/guide/02-wallets.md), compiled with the tests so
 * that the guide's code keeps matching the API. The tests do not run it.
 */
public class GuideWallets {

  /** Where an app keeps the two byte arrays of a wallet, for example an encrypted database. */
  public interface Store {
    void put(String key, byte[] value);
    byte[] get(String key);
  }

  /** How an app shows a message to the user. */
  public interface Messages {
    void show(String text);
  }

  // a new random wallet, stored as files at the path
  static String createWallet(String password) {
    ZephyrWalletFull wallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
        .setPath("wallets/main")
        .setPassword(password)
        .setNetworkType(ZephyrNetworkType.MAINNET)
        .setServerUri("http://node.example.invalid:17767"));
    String mnemonic = wallet.getMnemonic();  // 25 words; show it to the user once, then forget it
    wallet.save();
    wallet.close();
    return mnemonic;
  }

  // a wallet restored from its mnemonic, scanning from a restore height
  static ZephyrWalletFull restore(String password, String mnemonic, String nodeUri) {
    ZephyrWalletFull restored = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
        .setPath("wallets/restored")
        .setPassword(password)
        .setNetworkType(ZephyrNetworkType.MAINNET)
        .setMnemonic(mnemonic)
        .setRestoreHeight(536000L)
        .setServerUri(nodeUri));
    return restored;
  }

  // a view-only wallet from the primary address and the private view key
  static ZephyrWalletFull viewOnly(String password, String address, String privateViewKey, long restoreHeight, String nodeUri) {
    ZephyrWalletFull viewOnly = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
        .setPassword(password)
        .setNetworkType(ZephyrNetworkType.MAINNET)
        .setPrimaryAddress(address)
        .setPrivateViewKey(privateViewKey)
        .setRestoreHeight(restoreHeight)
        .setServerUri(nodeUri));
    return viewOnly;  // isViewOnly() is true
  }

  // the wallet files at the path, opened; a wrong password is not a ZephyrError
  static ZephyrWalletFull open(String path, String password, String nodeUri, Messages messages) {
    ZephyrWalletFull wallet = null;
    if (ZephyrWalletFull.walletExists(path)) {
      try {
        wallet = ZephyrWalletFull.openWallet(path, password, ZephyrNetworkType.MAINNET, nodeUri);
      } catch (Exception e) {  // "invalid password" arrives as java.lang.Exception
        messages.show("The wallet could not be opened: " + e.getMessage());
      }
    }
    return wallet;
  }

  // the keys and the cache as two byte arrays, stored by the app
  static void storeBytes(ZephyrWalletFull wallet, Store store) {
    byte[][] data = wallet.getData();        // data[0] = keys, data[1] = cache
    store.put("keys", data[0]);
    store.put("cache", data[1]);
  }

  // the same wallet, opened again from the two arrays
  static ZephyrWalletFull openBytes(String password, String nodeUri, Store store) {
    ZephyrWalletFull wallet = ZephyrWalletFull.openWalletData(password, ZephyrNetworkType.MAINNET,
        store.get("keys"), store.get("cache"), new ZephyrRpcConnection(nodeUri));
    return wallet;
  }

  // the same with a config, which also names the node
  static ZephyrWalletFull openBytesWithConfig(String password, String nodeUri, Store store) {
    ZephyrWalletFull wallet = ZephyrWalletFull.openWallet(new ZephyrWalletConfig()
        .setPassword(password)
        .setNetworkType(ZephyrNetworkType.MAINNET)
        .setKeysData(store.get("keys"))
        .setCacheData(store.get("cache"))
        .setServerUri(nodeUri));
    return wallet;
  }

  // a wallet held in memory gets files: moveTo writes <path> and <path>.keys
  static void giveFiles(ZephyrWalletFull wallet) {
    wallet.moveTo("wallets/main");
    wallet.save();  // writes to the new files from now on
  }

  // sync, use, save and close the wallet
  static void useAndClose(ZephyrWalletFull wallet) {
    try {
      wallet.sync();
      System.out.println(wallet.getBalance());
      wallet.save();     // reports a failure; close(true) would not
    } finally {
      wallet.close();
    }
  }

  // check a recipient address in pure Java before sending; no native library needed
  static boolean isMainnetAddress(String recipient) {
    return ZephyrUtils.isValidAddress(recipient, ZephyrNetworkType.MAINNET);
  }
}
