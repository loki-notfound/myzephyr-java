package examples;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrAccount;
import zephyr.wallet.model.ZephyrAssets;
import zephyr.wallet.model.ZephyrBalance;
import zephyr.wallet.model.ZephyrSubaddress;

/**
 * The assets, amounts, balances and accounts of the user guide (docs/guide/05-assets-and-balances.md),
 * compiled with the tests so that the guide's code keeps matching the API. The tests do not run
 * it.
 */
public class GuideBalances {

  // amounts: coins as the user types them, to atomic units; more than 12 decimals throws ArithmeticException
  static BigInteger toAtomicUnits(String coins) {
    return new BigDecimal(coins.trim()).movePointRight(12).toBigIntegerExact();
  }

  // amounts: atomic units to coins with all 12 decimals, for a send review or a detail view
  static String formatFull(BigInteger atomicUnits) {
    return new BigDecimal(atomicUnits, 12).toPlainString();  // 1500000000000 -> "1.500000000000"
  }

  // amounts: atomic units to coins with 2 to 4 decimals, for a balance overview; never shows dust as zero
  static String formatShort(BigInteger atomicUnits) {
    BigDecimal coins = new BigDecimal(atomicUnits, 12);
    BigDecimal shown = coins.setScale(4, RoundingMode.DOWN).stripTrailingZeros();
    if (shown.signum() == 0 && coins.signum() != 0) return coins.stripTrailingZeros().toPlainString();
    return shown.scale() < 2 ? shown.setScale(2).toPlainString() : shown.toPlainString();
  }

  // ZephyrBalance: a small map from asset name to amount
  static void readBalance(ZephyrWalletFull wallet) {
    ZephyrBalance balance = wallet.getBalance();
    BigInteger zph = balance.get(ZephyrAssets.ZPH);   // BigInteger.ZERO if the asset is absent, never null
    Set<String> assets = balance.getAssetTypes();     // sorted
    Map<String, BigInteger> map = balance.toMap();    // unmodifiable, sorted by asset
    System.out.println(zph + " " + assets + " " + map);
  }

  // the four shapes of the balance API
  static void fourShapes(ZephyrWalletFull wallet) {
    ZephyrBalance all            = wallet.getBalance();                          // the whole wallet
    Map<Integer, ZephyrBalance> m = wallet.getBalance(0);                        // account 0, per subaddress
    BigInteger acct              = wallet.getBalance(ZephyrAssets.ZPH, 0);       // one asset, one account
    BigInteger sub               = wallet.getBalance(ZephyrAssets.ZPH, 0, 1);    // one asset, one subaddress
    System.out.println(all + " " + m + " " + acct + " " + sub);
  }

  // accounts and subaddresses carry their balances as ZephyrBalance
  static void accountBalances(ZephyrWalletFull wallet) {
    for (ZephyrAccount account : wallet.getAccounts(true)) {          // true: with subaddresses
      ZephyrBalance accountBalance = account.getBalance();             // only the assets it holds
      for (ZephyrSubaddress subaddress : account.getSubaddresses()) {
        BigInteger zrs = subaddress.getUnlockedBalance().get(ZephyrAssets.ZRS);
        System.out.println(account.getIndex() + "/" + subaddress.getIndex() + " " + accountBalance + " " + zrs);
      }
    }
  }

  // a new account and a new receiving address in account 0, each with a label
  static String newAddresses(ZephyrWalletFull wallet) {
    ZephyrAccount savings = wallet.createAccount("Savings");
    ZephyrSubaddress invoice = wallet.createSubaddress(0, "Invoice 0001");
    wallet.setSubaddressLabel(0, invoice.getIndex(), "Invoice 0001, paid");
    return savings.getIndex() + " " + invoice.getAddress();
  }
}
