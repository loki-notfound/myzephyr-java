package zephyr.daemon.model;

/**
 * Models the result of downloading an update.
 */
public class ZephyrDaemonUpdateDownloadResult extends ZephyrDaemonUpdateCheckResult {

  private String downloadPath;
  
  public ZephyrDaemonUpdateDownloadResult(ZephyrDaemonUpdateCheckResult checkResult) {
    super(checkResult);
  }

  public String getDownloadPath() {
    return downloadPath;
  }

  public void setDownloadPath(String downloadPath) {
    this.downloadPath = downloadPath;
  }
}
