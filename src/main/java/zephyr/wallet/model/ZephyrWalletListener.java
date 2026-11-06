package zephyr.wallet.model;

import java.math.BigInteger;

/**
 * Default wallet listener which takes no action on notifications.
 */
public class ZephyrWalletListener implements ZephyrWalletListenerI {

  @Override
  public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) { }

  @Override
  public void onNewBlock(long height) { }
  
  @Override
  public void onBalancesChanged(BigInteger newBalance, BigInteger newUnlockedBalance) { }

  @Override
  public void onOutputReceived(ZephyrOutputWallet output) { }

  @Override
  public void onOutputSpent(ZephyrOutputWallet output) { }
}
