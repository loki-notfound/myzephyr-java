package zephyr.wallet.model;

/**
 * Result from syncing a Zephyr wallet.
 */
public class ZephyrSyncResult {

  private Long numBlocksFetched;
  private Boolean receivedMoney;
  
  public ZephyrSyncResult() {
    this(null, null);
  }
  
  public ZephyrSyncResult(Long numBlocksFetched, Boolean receivedMoney) {
    this.numBlocksFetched = numBlocksFetched;
    this.receivedMoney = receivedMoney;
  }
  
  public Long getNumBlocksFetched() {
    return numBlocksFetched;
  }
  
  public void setNumBlocksFetched(Long numBlocksFetched) {
    this.numBlocksFetched = numBlocksFetched;
  }
  
  public Boolean getReceivedMoney() {
    return receivedMoney;
  }
  
  public void setReceivedMoney(Boolean receivedMoney) {
    this.receivedMoney = receivedMoney;
  }
}
