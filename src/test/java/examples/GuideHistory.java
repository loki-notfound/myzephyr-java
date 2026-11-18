package examples;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import zephyr.wallet.ZephyrWallet;
import zephyr.wallet.model.ZephyrDestination;
import zephyr.wallet.model.ZephyrIncomingTransfer;
import zephyr.wallet.model.ZephyrOutgoingTransfer;
import zephyr.wallet.model.ZephyrOutputQuery;
import zephyr.wallet.model.ZephyrOutputWallet;
import zephyr.wallet.model.ZephyrTransfer;
import zephyr.wallet.model.ZephyrTransferQuery;
import zephyr.wallet.model.ZephyrTxQuery;
import zephyr.wallet.model.ZephyrTxWallet;

/**
 * The history of the user guide (docs/guide/06-history.md): queries, notes and the workarounds of
 * the sections "checkTxKey returns amount 0" and "getOutputs() reports no asset", compiled with
 * the tests so that the guide's code keeps matching the API. The tests do not run it.
 */
public class GuideHistory {

  // checkTxKey returns amount 0: the receiver reads what it received from its own history
  static Map<String, BigInteger> receivedPerAsset(ZephyrWallet wallet, String txHash, String address, long minConfirmations) {
    Map<String, BigInteger> received = new HashMap<>();
    for (ZephyrTxWallet tx : wallet.getTxs(txHash)) {  // throws when the wallet does not know the transaction
      Long confirmations = tx.getNumConfirmations();
      if (confirmations == null || confirmations < minConfirmations) throw new IllegalStateException("only " + confirmations + " confirmations");
      if (tx.getIncomingTransfers() == null) continue;
      for (ZephyrIncomingTransfer transfer : tx.getIncomingTransfers()) {
        if (!address.equals(transfer.getAddress())) continue;  // only what this address received
        received.merge(transfer.getAssetType(), transfer.getAmount(), BigInteger::add);
      }
    }
    return received;  // empty: the transaction paid nothing to this address
  }

  // checkTxKey returns amount 0: the sender has the amount in the destinations of its outgoing transfer
  static Map<String, BigInteger> sentPerAsset(ZephyrWallet wallet, String txHash, String address) {
    Map<String, BigInteger> sent = new HashMap<>();
    for (ZephyrTxWallet tx : wallet.getTxs(txHash)) {
      ZephyrOutgoingTransfer outgoing = tx.getOutgoingTransfer();
      if (outgoing == null || outgoing.getDestinations() == null) continue;
      for (ZephyrDestination destination : outgoing.getDestinations()) {
        if (address.equals(destination.getAddress())) sent.merge(destination.getAssetType(), destination.getAmount(), BigInteger::add);
      }
    }
    return sent;
  }

  // getOutputs() reports no asset: coin control per asset, the unspent outputs of one asset
  static List<ZephyrOutputWallet> unspentOutputs(ZephyrWallet wallet, String asset) {
    List<ZephyrOutputWallet> outputs = new ArrayList<>();
    for (ZephyrOutputWallet output : wallet.getOutputs(new ZephyrOutputQuery().setIsSpent(false))) {
      if (asset.equals(wallet.getOutputAssetType(output))) outputs.add(output);  // one getTxs call per output
    }
    return outputs;
  }

  // the incoming transfers of one asset; the queries have no asset filter, so the app filters
  static BigInteger receivedInAsset(ZephyrWallet wallet, String asset) {
    BigInteger total = BigInteger.ZERO;
    for (ZephyrTransfer transfer : wallet.getTransfers(new ZephyrTransferQuery().setIsIncoming(true))) {
      if (asset.equals(transfer.getAssetType())) total = total.add(transfer.getAmount());
    }
    return total;
  }

  // the confirmed outgoing transactions between two heights
  static List<ZephyrTxWallet> sentBetween(ZephyrWallet wallet, long fromHeight, long toHeight) {
    return wallet.getTxs(new ZephyrTxQuery()
        .setIsOutgoing(true)
        .setIsConfirmed(true)
        .setMinHeight(fromHeight)
        .setMaxHeight(toHeight));
  }

  // transactions by hash; the hashes the wallet does not know are collected instead of thrown
  static List<ZephyrTxWallet> knownTxs(ZephyrWallet wallet, List<String> hashes) {
    List<String> missing = new ArrayList<>();
    List<ZephyrTxWallet> txs = wallet.getTxs(hashes, missing);  // getTxs(hash) alone throws for a missing hash
    if (!missing.isEmpty()) System.out.println("not in the wallet: " + missing);
    return txs;
  }

  // a note for a transaction; it lives in the wallet's cache until the wallet is saved
  static void noteAndKey(ZephyrWallet wallet, String txHash) {
    wallet.setTxNote(txHash, "rent, November");
    String note = wallet.getTxNote(txHash);
    String txKey = wallet.getTxKey(txHash);  // the secret key of a transaction this wallet sent
    System.out.println(note + " " + (txKey != null));
  }
}
