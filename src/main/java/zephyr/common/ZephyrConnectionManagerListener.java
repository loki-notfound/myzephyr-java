package zephyr.common;

/**
 * Default connection manager listener which takes no action on notifications.
 */
public interface ZephyrConnectionManagerListener {

  /**
   * Notified on connection change events.
   * 
   * @param connection - the connection manager's current connection
   */
  public void onConnectionChanged(ZephyrRpcConnection connection);
}
