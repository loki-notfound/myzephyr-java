package examples;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrAssets;
import zephyr.wallet.model.ZephyrIncomingTransfer;
import zephyr.wallet.model.ZephyrReserveInfo;
import zephyr.wallet.model.ZephyrTxConfig;
import zephyr.wallet.model.ZephyrTxWallet;

/**
 * The conversions and the reserve of the user guide (docs/guide/08-conversions-and-the-reserve.md),
 * compiled with the tests so that the guide's code keeps matching the API. The tests do not run
 * it, and nothing here is relayed unless an app calls convertEverything.
 */
public class GuideConversions {

  // the state of the reserve, from the node
  static void reserve(ZephyrWalletFull wallet) {
    ZephyrReserveInfo info = wallet.getReserveInfo();
    BigDecimal ratio = info.getReserveRatio();       // 2.33 means 233%
    Map<String, BigInteger> supply = wallet.getCirculatingSupply();
    System.out.println(ratio + " " + supply);
  }

  // a conversion amount must be a multiple of 10^8 atomic units: at most four decimal places
  static BigInteger roundDownForConversion(BigInteger amount) {
    BigInteger step = BigInteger.TEN.pow(8);
    return amount.subtract(amount.mod(step));
  }

  // a ZPH -> ZRS mint to the wallet's own address, built but not relayed
  static List<ZephyrTxWallet> buildMint(ZephyrWalletFull wallet) {
    List<ZephyrTxWallet> txs = wallet.createTxs(new ZephyrTxConfig()
        .setAddress(wallet.getPrimaryAddress())
        .setAmount(new BigDecimal("50").movePointRight(12).toBigIntegerExact())   // in ZPH
        .setSourceCurrency(ZephyrAssets.ZPH)
        .setDestinationCurrency(ZephyrAssets.ZRS)
        .setAccountIndex(0));
    return txs;
  }

  // convert everything unlocked in account 0 from ZPH to ZRS: build, show fee and quote, then relay
  static List<String> convertEverything(ZephyrWalletFull wallet) {
    List<ZephyrTxWallet> txs = wallet.sweepUnlocked(new ZephyrTxConfig()
        .setAddress(wallet.getPrimaryAddress())
        .setSourceCurrency(ZephyrAssets.ZPH)
        .setDestinationCurrency(ZephyrAssets.ZRS)
        .setAccountIndex(0));                 // relay defaults to false: nothing is sent yet
    BigInteger fee = BigInteger.ZERO;
    BigInteger minted = BigInteger.ZERO;
    for (ZephyrTxWallet tx : txs) {
      fee = fee.add(tx.getFee());             // in ZPH
      if (tx.getIncomingTransfers() == null) continue;
      for (ZephyrIncomingTransfer transfer : tx.getIncomingTransfers()) minted = minted.add(transfer.getAmount());  // in ZRS
    }
    System.out.println("fee " + GuideBalances.formatFull(fee) + " ZPH, quote " + GuideBalances.formatFull(minted) + " ZRS");
    return wallet.relayTxs(txs);              // only after the user has agreed to fee and quote
  }
}
