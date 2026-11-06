package utils;

import java.util.UUID;

import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.wallet.ZephyrWallet;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrWalletConfig;

/**
 * Scratchpad for quick scripting.
 */
public class Scratchpad {

  public static void main(String[] args) {
    
    // initialize daemon, wallet, and direct rpc interface
//    ZephyrDaemon daemon = TestUtils.getDaemonRpc();
//    ZephyrWalletRpc walletRpc = TestUtils.getWalletRpc();
//    ZephyrWalletFull walletFull = TestUtils.getWalletFull();
    
    // -------------------------------- SCRATCHPAD ----------------------------
    
    // create wallet from mnemonic
    ZephyrWallet walletFull = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
      .setPath("./test_wallets/" + UUID.randomUUID().toString())  // leave blank for in-memory wallet
      .setPassword("abctesting123")
      .setNetworkType(ZephyrNetworkType.TESTNET)
      .setServerUri("http://localhost:38081")
      .setServerUsername("superuser")
      .setServerPassword("abctesting123")
      .setMnemonic("biggest duets beware eskimos coexist igloo pamphlet lagoon odometer hounded jukebox enough pride cocoa nylon wolf geometry buzzer vivid federal idols gang semifinal subtly coexist")
      .setRestoreHeight(573800l));
    walletFull.sync(new WalletSyncPrinter());
    System.out.println("Full wallet daemon height: " + walletFull.getDaemonHeight());
    System.out.println("Full wallet mnemonic: " + walletFull.getMnemonic());
    
//    walletFull.createTx(new ZephyrTxConfig()
//            .addDestination("52FnB7ABUrKJzVQRpbMNrqDFWbcKLjFUq8Rgek7jZEuB6WE2ZggXaTf4FK6H8gQymvSrruHHrEuKhMN3qTMiBYzREKsmRKM", walletFull.getUnlockedBalance(0).divide(new BigInteger("8")).multiply(new BigInteger("1")))
//            .addDestination("52aPELZwrwvVBNK4pvRZPNj4U5EEkZBsNTR2jozCLYyrhQySvYbWebTQEdt7RS9nFnRY9r88eFpt6UcsHKnVpCQDAFKu1Az", walletFull.getUnlockedBalance(0).divide(new BigInteger("8")).multiply(new BigInteger("1")))
//            .setAccountIndex(0)
//            .setRelay(true));
  }
}
