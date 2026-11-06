package zephyr.daemon.model;

/**
 * Receives notifications as a daemon is updated.
 */
public class ZephyrDaemonListener {
  
  private ZephyrBlockHeader lastHeader;

  /**
   * Called when a new block is added to the chain.
   * 
   * @param header is the header of the block added to the chain
   */
  public void onBlockHeader(ZephyrBlockHeader header) {
    lastHeader = header;
  }
  
  /**
   * Get the last notified block header.
   * 
   * @return the last notified block header
   */
  public ZephyrBlockHeader getLastBlockHeader() {
    return lastHeader;
  }
}
