package utils;

import java.util.ArrayList;
import java.util.List;

import zephyr.daemon.model.ZephyrTx;
import zephyr.wallet.ZephyrWallet;
import zephyr.wallet.model.ZephyrTxQuery;
import zephyr.wallet.model.ZephyrTxWallet;

/**
 * Prints transactions in a wallet.
 */
public class PrintTransactions {

  public static void main(String[] args) {
    ZephyrWallet wallet = TestUtils.getWalletRpc();
    ZephyrTxQuery query = new ZephyrTxQuery();
    List<String> hashes = new ArrayList<String>();
    hashes.add("d16e603efed00a3d2e14085d0ebe96734145d9eba851f6afd02ab76f9d5b62c0");
    query.setHashes(hashes);
    List<ZephyrTxWallet> txs = wallet.getTxs(query);
    for (ZephyrTx tx : txs) {
      System.out.println(tx);
    }
  }
}
