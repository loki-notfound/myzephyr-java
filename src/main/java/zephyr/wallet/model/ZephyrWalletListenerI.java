package zephyr.wallet.model;

import java.math.BigInteger;

/**
 * Interface to receive wallet notifications.
 *
 * <p>The threads callbacks run on (read from the core's code): a callback runs on a thread that is
 * inside a core call that notifies. That is the core's own background sync thread
 * ({@code startSyncing()}), or a Java thread inside {@code sync()}, {@code rescanBlockchain()},
 * {@code scanTxs()}, {@code importMultisigHex()}, {@code importKeyImages()} (on a wallet connected
 * to a node) or a call that relays ({@code createTx}, {@code createTxs}, {@code sweepUnlocked},
 * {@code sweepOutput} and {@code sweepDust} with relay; {@code relayTx}, {@code relayTxs},
 * {@code submitTxs}). {@code onNewBlock} and {@code onSyncProgress} come from a sync (background
 * syncing, {@code sync()}, {@code rescanBlockchain()}); the other calls deliver output and balance
 * callbacks. The core queues its notifications in one queue that has no thread of its own: each
 * of these threads runs queued notifications while it waits for its own, so a callback can run on
 * another of these threads than the one whose work caused it. Callbacks of different threads can run at the same time: a listener must be
 * thread-safe (an application with a UI hands the data to its UI thread and returns).
 * {@code addListener()} and {@code removeListener()} never deliver a callback themselves; a
 * listener that is added receives the notifications from the next one on, and a listener that is
 * removed receives none once the callbacks already under way have returned. Which wallet calls a
 * callback may make is described at {@code ZephyrWalletFull.addListener()}.</p>
 */
public interface ZephyrWalletListenerI {
  
  /**
   * Invoked as the wallet is synchronized.
   * 
   * @param height - height of the synced block 
   * @param startHeight - starting height of the sync request
   * @param endHeight - ending height of the sync request
   * @param percentDone - sync progress as a percentage
   * @param message is a human-readable description of the current progress
   */
  public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message);
  
  /**
   * Invoked when a new block is added to the chain.
   * 
   * @param height - the height of the new block (i.e. the number of blocks before it).
   */
  public void onNewBlock(long height);

  /**
   * Invoked when the wallet's balances change, once per asset whose balance changed.
   * 
   * @param newBalance - new wallet balance of the asset in atomic units
   * @param newUnlockedBalance - new unlocked wallet balance of the asset in atomic units
   * @param assetType - the asset whose balance changed (an open string, see ZephyrAssets)
   */
  public void onBalancesChanged(BigInteger newBalance, BigInteger newUnlockedBalance, String assetType);
  
  /**
   * Invoked 3 times per received output: once when unconfirmed, once when confirmed, and
   * once when unlocked.
   * 
   * <p>The unconfirmed notification carries no asset type (the core does not pass it for pool
   * outputs); the confirmed and unlocked notifications do. Use {@code onBalancesChanged} or
   * {@code getTxs()} for the asset of an unconfirmed output.</p>
   * 
   * @param output - the received output
   */
  public void onOutputReceived(ZephyrOutputWallet output);
  
  /**
   * Invoked twice per spent output: once when confirmed and once when unlocked.
   * 
   * @param output - the spent output
   */
  public void onOutputSpent(ZephyrOutputWallet output); // TODO (zephyr): support notification of unconfirmed spent funds
}