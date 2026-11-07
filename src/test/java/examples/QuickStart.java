package examples;

import java.math.BigDecimal;
import java.math.BigInteger;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrAssets;
import zephyr.wallet.model.ZephyrBalance;
import zephyr.wallet.model.ZephyrTxConfig;
import zephyr.wallet.model.ZephyrTxWallet;
import zephyr.wallet.model.ZephyrWalletConfig;
import zephyr.wallet.model.ZephyrWalletListener;

/**
 * Opens or creates a mainnet wallet, syncs it, prints the balance per asset and, with --send,
 * builds a ZPH transfer without relaying it. This example never spends.
 *
 * Usage: QuickStart <wallet path> <password> <daemon URI> [--send <address> <amount in ZPH>]
 */
public class QuickStart {

  public static void main(String[] args) {
    boolean send = args.length == 6 && args[3].equals("--send");
    if (args.length != 3 && !send) {
      System.err.println("Usage: QuickStart <wallet path> <password> <daemon URI> [--send <address> <amount in ZPH>]");
      System.exit(2);
    }
    String path = args[0];
    String password = args[1];
    String daemonUri = args[2];

    // open the wallet files at the path, or create a new random wallet there
    ZephyrWalletFull wallet = ZephyrWalletFull.walletExists(path)
        ? ZephyrWalletFull.openWallet(path, password, ZephyrNetworkType.MAINNET, daemonUri)
        : ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
            .setPath(path)
            .setPassword(password)
            .setNetworkType(ZephyrNetworkType.MAINNET)
            .setServerUri(daemonUri));
    try {
      System.out.println("Primary address: " + wallet.getPrimaryAddress());

      // sync; the listener runs on a native thread, once per block fetched
      wallet.sync(new ZephyrWalletListener() {
        @Override
        public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
          System.out.printf("Synced block %d of %d (%.1f%%)%n", height, endHeight - 1, percentDone * 100);
        }
      });

      // the balance per asset; amounts are atomic units, 10^12 per coin for every asset
      ZephyrBalance balance = wallet.getBalance();
      ZephyrBalance unlocked = wallet.getUnlockedBalance();
      for (String asset : ZephyrAssets.V2) {
        System.out.println(asset + ": " + toCoins(balance.get(asset)) + " (unlocked " + toCoins(unlocked.get(asset)) + ")");
      }

      // build a plain ZPH transfer: every send names the source and the destination currency
      if (send) {
        ZephyrTxWallet tx = wallet.createTx(new ZephyrTxConfig()
            .setAddress(args[4])
            .setAmount(new BigDecimal(args[5]).movePointRight(12).toBigIntegerExact())
            .setSourceCurrency(ZephyrAssets.ZPH)
            .setDestinationCurrency(ZephyrAssets.ZPH)
            .setAccountIndex(0)
            .setRelay(false));  // built and signed, never broadcast
        System.out.println("Built, not relayed: tx " + tx.getHash() + ", fee " + toCoins(tx.getFee()) + " ZPH");
      }
      wallet.save();
    } finally {
      wallet.close();
    }
  }

  private static String toCoins(BigInteger atomicUnits) {
    return new BigDecimal(atomicUnits, 12).toPlainString();
  }
}
