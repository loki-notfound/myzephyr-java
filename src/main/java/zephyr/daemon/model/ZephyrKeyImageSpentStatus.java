package zephyr.daemon.model;

import zephyr.common.ZephyrError;

/**
 * Enumerate key image spent statuses.
 */
public enum ZephyrKeyImageSpentStatus {
  NOT_SPENT,
  CONFIRMED,
  TX_POOL;
  
  public static ZephyrKeyImageSpentStatus valueOf(int status) {
    if (status == 0) return NOT_SPENT;
    else if (status == 1) return CONFIRMED;
    else if (status == 2) return TX_POOL;
    throw new ZephyrError("Invalid integer value for spent status: " + status);
  }
}
