package examples;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import zephyr.wallet.ZephyrWallet;
import zephyr.wallet.model.ZephyrCheckTx;
import zephyr.wallet.model.ZephyrDestination;
import zephyr.wallet.model.ZephyrIncomingTransfer;
import zephyr.wallet.model.ZephyrOutgoingTransfer;
import zephyr.wallet.model.ZephyrOutputQuery;
import zephyr.wallet.model.ZephyrOutputWallet;
import zephyr.wallet.model.ZephyrTxWallet;

/**
 * The workarounds of chapter 8 of the user guide (docs/guide/08-known-limitations.md), compiled
 * with the tests so that the guide's code keeps matching the API. The tests do not run it.
 */
public class GuideChapter8 {

  // checkTxKey returns amount 0: the receiver checks the key, then reads the amount from its own history
  static Map<String, BigInteger> receivedPerAsset(ZephyrWallet wallet, String txHash, String txKey, String address, long minConfirmations) {
    ZephyrCheckTx check = wallet.checkTxKey(txHash, txKey, address);
    if (!check.isGood()) throw new IllegalStateException("the transaction does not pay " + address);
    if (check.getNumConfirmations() < minConfirmations) throw new IllegalStateException("only " + check.getNumConfirmations() + " confirmations");
    // check.getReceivedAmount() is 0 for every current transaction: do not use it
    Map<String, BigInteger> received = new HashMap<>();
    for (ZephyrTxWallet tx : wallet.getTxs(txHash)) {
      if (tx.getIncomingTransfers() == null) continue;
      for (ZephyrIncomingTransfer transfer : tx.getIncomingTransfers()) {
        received.merge(transfer.getAssetType(), transfer.getAmount(), BigInteger::add);
      }
    }
    return received;
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

  // getOutputs() reports no asset: take it from the output's transaction (the rule of ZephyrWallet.getOutputAssetType, for earlier releases)
  static String assetOf(ZephyrWallet wallet, ZephyrOutputWallet output) {
    List<ZephyrTxWallet> txs = wallet.getTxs(Arrays.asList(output.getTx().getHash()), new ArrayList<String>());
    if (txs.isEmpty()) return null;  // the transaction is not in the wallet
    ZephyrTxWallet tx = txs.get(0);
    ZephyrOutgoingTransfer outgoing = tx.getOutgoingTransfer();
    boolean spentFromThisAccount = outgoing != null && output.getAccountIndex().equals(outgoing.getAccountIndex());
    if (tx.getIncomingTransfers() != null) {
      for (ZephyrIncomingTransfer transfer : tx.getIncomingTransfers()) {
        if (!output.getAccountIndex().equals(transfer.getAccountIndex())) continue;
        if (!output.getSubaddressIndex().equals(transfer.getSubaddressIndex())) continue;
        if (!spentFromThisAccount) return transfer.getAssetType();  // a received transaction: one asset per subaddress
        if (!Objects.equals(transfer.getAssetType(), outgoing.getAssetType()) && transfer.getAmount().equals(output.getAmount())) {
          return transfer.getAssetType();  // the converted output of a conversion to this account
        }
      }
    }
    return spentFromThisAccount ? outgoing.getAssetType() : null;  // change and same-asset sends to this account: the source asset
  }

  // coin control per asset: the unspent outputs of one asset
  static List<ZephyrOutputWallet> unspentOutputs(ZephyrWallet wallet, String asset) {
    List<ZephyrOutputWallet> outputs = new ArrayList<>();
    for (ZephyrOutputWallet output : wallet.getOutputs(new ZephyrOutputQuery().setIsSpent(false))) {
      if (asset.equals(assetOf(wallet, output))) outputs.add(output);
    }
    return outputs;
  }
}
