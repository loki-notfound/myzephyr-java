package utils;

import zephyr.wallet.ZephyrWallet;

/**
 * Prints the height of the wallet.
 */
public class PrintHeight {

  public static void main(String[] args) {
    ZephyrWallet wallet = TestUtils.getWalletRpc();
    System.out.println("Height: " + wallet.getHeight());
  }
}
