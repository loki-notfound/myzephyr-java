package examples;

import zephyr.common.ZephyrRpcConnection;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrSyncResult;
import zephyr.wallet.model.ZephyrWalletListener;

/**
 * The node connection and syncing of the user guide (docs/guide/03-connecting-to-a-node.md),
 * compiled with the tests so that the guide's code keeps matching the API. The tests do not run
 * it.
 */
public class GuideNode {

  /** Where an app shows the progress of a sync, for example a progress bar. */
  public interface ProgressView {
    void post(double fraction);
  }

  // set the node of an open wallet; the user name and password only for a node with RPC login
  static boolean connect(ZephyrWalletFull wallet, String user, String pass) {
    wallet.setDaemonConnection(new ZephyrRpcConnection("http://node.example.invalid:17767", user, pass));
    boolean connected = wallet.isConnectedToDaemon();
    return connected;
  }

  // sync once, on a worker thread, with a listener for this sync only
  static long syncWithProgress(ZephyrWalletFull wallet, ProgressView progress) {
    ZephyrSyncResult result = wallet.sync(new ZephyrWalletListener() {
      @Override
      public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
        progress.post(percentDone);   // 0.0 to 1.0
      }
    });
    long fetched = result.getNumBlocksFetched();
    return fetched;
  }

  // keep the wallet in sync in the background, every 30 s instead of the default 10 s
  static void syncInBackground(ZephyrWalletFull wallet) {
    wallet.startSyncing(30000L);
  }
}
