package zephyr.wallet.model;

import java.math.BigInteger;

/**
 * Default wallet listener which takes no action on notifications.
 */
public class ZephyrWalletListener implements ZephyrWalletListenerI {

  /**
   * Constructs a listener that ignores every notification; override the callbacks you need.
   */
  public ZephyrWalletListener() { }

  @Override
  public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) { }

  @Override
  public void onNewBlock(long height) { }
  
  @Override
  public void onBalancesChanged(BigInteger newBalance, BigInteger newUnlockedBalance, String assetType) { }
  
  /**
   * Not called by zephyr-java: balances change per asset. Kept final so that a listener ported
   * from monero-java fails to compile here instead of never being notified.
   *
   * @param newBalance is not used
   * @param newUnlockedBalance is not used
   * @deprecated override {@link #onBalancesChanged(BigInteger, BigInteger, String)} instead
   */
  @Deprecated
  public final void onBalancesChanged(BigInteger newBalance, BigInteger newUnlockedBalance) { }

  /**
   * The unconfirmed notification carries no asset type (the core does not pass it for pool
   * outputs); the confirmed and unlocked notifications do. Use {@code onBalancesChanged} or
   * {@code getTxs()} for the asset of an unconfirmed output.
   */
  @Override
  public void onOutputReceived(ZephyrOutputWallet output) { }

  @Override
  public void onOutputSpent(ZephyrOutputWallet output) { }
}
