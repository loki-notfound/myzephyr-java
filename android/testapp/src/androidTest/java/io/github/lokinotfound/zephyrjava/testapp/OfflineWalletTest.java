package io.github.lokinotfound.zephyrjava.testapp;

import android.test.AndroidTestCase;
import zephyr.common.TestAppNativeCheck;
import zephyr.common.ZephyrUtils;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrAddressType;
import zephyr.wallet.model.ZephyrWalletConfig;

/**
 * Instrumented test for a device (JUnit 3, the platform's InstrumentationTestRunner): an offline
 * in-memory wallet, no path and no server, so nothing touches the network or the storage.
 */
public class OfflineWalletTest extends AndroidTestCase {

  public void testOfflineInMemoryWallet() {
    ZephyrUtils.loadNativeLibrary();
    ZephyrWalletFull wallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
        .setNetworkType(ZephyrNetworkType.MAINNET)
        .setPassword("testapp-offline"));
    try {
      assertFalse(wallet.isConnectedToDaemon());
      assertEquals(25, wallet.getMnemonic().trim().split("\\s+").length);
      String address = wallet.getPrimaryAddress();
      assertEquals(ZephyrNetworkType.MAINNET, ZephyrUtils.decodeAddress(address).getNetworkType());
      assertEquals(ZephyrAddressType.PRIMARY_ADDRESS, ZephyrUtils.decodeAddress(address).getAddressType());
      TestAppNativeCheck.validate(address, ZephyrNetworkType.MAINNET); // throws if the core rejects it
    } finally {
      wallet.close();
    }
    assertTrue(AddressVectors.run().endsWith("all ok"));
  }
}
