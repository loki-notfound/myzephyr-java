package examples;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrAssets;
import zephyr.wallet.model.ZephyrTxConfig;
import zephyr.wallet.model.ZephyrTxPriority;
import zephyr.wallet.model.ZephyrTxWallet;

/**
 * The sending of the user guide (docs/guide/07-sending.md), compiled with the tests so that the
 * guide's code keeps matching the API. The tests do not run it, and nothing here is relayed
 * unless an app calls relay().
 */
public class GuideSending {

  // a plain ZPH transfer: the currency pair names the same asset twice
  static ZephyrTxWallet buildTransfer(ZephyrWalletFull wallet, String recipient) {
    ZephyrTxWallet tx = wallet.createTx(new ZephyrTxConfig()
        .setAddress(recipient)
        .setAmount(new BigDecimal("2.5").movePointRight(12).toBigIntegerExact())
        .setSourceCurrency(ZephyrAssets.ZPH)
        .setDestinationCurrency(ZephyrAssets.ZPH)
        .setAccountIndex(0));
    return tx;
  }

  // create, show the total fee, then relay every transaction of the send
  static List<String> relay(ZephyrWalletFull wallet, ZephyrTxConfig config) {
    List<ZephyrTxWallet> txs = wallet.createTxs(config);      // relay defaults to false; can be several
    BigInteger fee = BigInteger.ZERO;
    for (ZephyrTxWallet tx : txs) fee = fee.add(tx.getFee()); // in the source asset
    System.out.println("fee " + fee);                         // show it; relay only after the user agrees
    return wallet.relayTxs(txs);                              // now every transaction is sent
  }

  // what to show before relaying: the amount, the asset, the address and the fee, with all 12 decimals
  static String review(ZephyrTxConfig config, List<ZephyrTxWallet> txs) {
    BigInteger fee = BigInteger.ZERO;
    for (ZephyrTxWallet tx : txs) fee = fee.add(tx.getFee());  // a send can take several transactions
    String asset = config.getSourceCurrency();
    return "Send " + GuideBalances.formatFull(config.getAmount()) + " " + asset
        + " to " + config.getAddress()
        + ", network fee " + GuideBalances.formatFull(fee) + " " + asset;
  }

  // a priority other than the default
  static ZephyrTxConfig withPriority(ZephyrTxConfig config) {
    return config.setPriority(ZephyrTxPriority.ELEVATED);
  }

  // two destinations in one transaction; the currency pair holds for both
  static List<ZephyrTxWallet> buildToTwo(ZephyrWalletFull wallet, String first, String second) {
    return wallet.createTxs(new ZephyrTxConfig()
        .addDestination(first, new BigInteger("1000000000000"))      // 1 ZPH
        .addDestination(second, new BigInteger("2500000000000"))     // 2.5 ZPH
        .setSourceCurrency(ZephyrAssets.ZPH)
        .setDestinationCurrency(ZephyrAssets.ZPH)
        .setAccountIndex(0));
  }

  // sweep everything unlocked from account 0 to one address, in the same asset
  static List<ZephyrTxWallet> sweepAccount(ZephyrWalletFull wallet, String recipient) {
    return wallet.sweepUnlocked(new ZephyrTxConfig()
        .setAddress(recipient)
        .setSourceCurrency(ZephyrAssets.ZRS)
        .setDestinationCurrency(ZephyrAssets.ZRS)
        .setAccountIndex(0));  // always set the account index
  }
}
