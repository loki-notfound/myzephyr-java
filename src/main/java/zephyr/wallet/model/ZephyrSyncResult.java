package zephyr.wallet.model;

/**
 * Result from syncing a Zephyr wallet.
 */
public class ZephyrSyncResult {

  private Long numBlocksFetched;
  private Boolean receivedMoney;
  
  /**
   * Creates a result with both fields null.
   */
  public ZephyrSyncResult() {
    this(null, null);
  }
  
  /**
   * Creates a result.
   *
   * @param numBlocksFetched is the number of blocks the sync fetched
   * @param receivedMoney is true if the sync added an output to the wallet
   */
  public ZephyrSyncResult(Long numBlocksFetched, Boolean receivedMoney) {
    this.numBlocksFetched = numBlocksFetched;
    this.receivedMoney = receivedMoney;
  }
  
  /**
   * Returns the number of blocks the sync fetched from the node. A sync against a node that is
   * itself still syncing returns 0 without an error (read in the core's code). See the user guide,
   * {@code docs/guide/03-connecting-to-a-node.md}, section "Syncing".
   *
   * @return the number of blocks
   */
  public Long getNumBlocksFetched() {
    return numBlocksFetched;
  }
  
  /**
   * Sets the number of blocks the sync fetched.
   *
   * @param numBlocksFetched is the number of blocks
   */
  public void setNumBlocksFetched(Long numBlocksFetched) {
    this.numBlocksFetched = numBlocksFetched;
  }
  
  /**
   * Returns whether the sync added an output to the wallet: received funds, or the change of one of
   * its own sends.
   *
   * @return true if an output was added
   */
  public Boolean getReceivedMoney() {
    return receivedMoney;
  }
  
  /**
   * Sets whether the sync added an output to the wallet.
   *
   * @param receivedMoney is true if an output was added
   */
  public void setReceivedMoney(Boolean receivedMoney) {
    this.receivedMoney = receivedMoney;
  }
}
