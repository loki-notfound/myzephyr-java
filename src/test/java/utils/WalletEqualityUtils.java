package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import zephyr.daemon.model.ZephyrOutput;
import zephyr.daemon.model.ZephyrTx;
import zephyr.wallet.ZephyrWallet;
import zephyr.wallet.ZephyrWalletRpc.IncomingTransferComparator;
import zephyr.wallet.model.ZephyrAccount;
import zephyr.wallet.model.ZephyrAssets; // temporary until R8
import zephyr.wallet.model.ZephyrIncomingTransfer;
import zephyr.wallet.model.ZephyrOutgoingTransfer;
import zephyr.wallet.model.ZephyrOutputQuery;
import zephyr.wallet.model.ZephyrOutputWallet;
import zephyr.wallet.model.ZephyrSubaddress;
import zephyr.wallet.model.ZephyrTransfer;
import zephyr.wallet.model.ZephyrTransferQuery;
import zephyr.wallet.model.ZephyrTxQuery;
import zephyr.wallet.model.ZephyrTxWallet;

/**
 * Utilities to deep compare wallets.
 */
public class WalletEqualityUtils {

  /**
   * Compares two wallets for equality using only on-chain data.
   * 
   * This test will sync the two wallets until their height is equal to guarantee equal state.
   * 
   * @param w1 a wallet to compare
   * @param w2 a wallet to compare
   */
  public static void testWalletEqualityOnChain(ZephyrWallet w1, ZephyrWallet w2) {
    TestUtils.WALLET_TX_TRACKER.reset(); // all wallets need to wait for txs to confirm to reliably sync
    
    // wait for relayed txs associated with wallets to clear pool
    assertEquals(w1.isConnectedToDaemon(), w2.isConnectedToDaemon());
    if (w1.isConnectedToDaemon()) TestUtils.WALLET_TX_TRACKER.waitForWalletTxsToClearPool(w1, w2);
    
    // sync the wallets until same height
    while (w1.getHeight() != w2.getHeight()) {
      w1.sync();
      w2.sync();
    }
    
    // test that wallets are equal using only on-chain data
    assertEquals(w1.getHeight(), w2.getHeight());
    assertEquals(w1.getMnemonic(), w2.getMnemonic());
    assertEquals(w1.getPrimaryAddress(), w2.getPrimaryAddress());
    assertEquals(w1.getPrivateViewKey(), w2.getPrivateViewKey());
    assertEquals(w1.getPrivateSpendKey(), w2.getPrivateSpendKey());
    ZephyrTxQuery txQuery = new ZephyrTxQuery().setIsConfirmed(true);
    testTxWalletsEqualOnChain(w1.getTxs(txQuery), w2.getTxs(txQuery));
    txQuery.setIncludeOutputs(true);
    testTxWalletsEqualOnChain(w1.getTxs(txQuery), w2.getTxs(txQuery));  // fetch and compare outputs
    testAccountsEqualOnChain(w1.getAccounts(true), w2.getAccounts(true));
    assertEquals(w1.getBalance(), w2.getBalance());
    assertEquals(w1.getUnlockedBalance(), w2.getUnlockedBalance());
    ZephyrTransferQuery transferQuery = new ZephyrTransferQuery().setTxQuery(new ZephyrTxQuery().setIsConfirmed(true));
    testTransfersEqualOnChain(w1.getTransfers(transferQuery), w2.getTransfers(transferQuery));
    ZephyrOutputQuery outputQuery = new ZephyrOutputQuery().setTxQuery(new ZephyrTxQuery().setIsConfirmed(true));
    testOutputWalletsEqualOnChain(w1.getOutputs(outputQuery), w2.getOutputs(outputQuery));
  }
  
  private static void testAccountsEqualOnChain(List<ZephyrAccount> accounts1, List<ZephyrAccount> accounts2) {
    for (int i = 0; i < Math.max(accounts1.size(), accounts2.size()); i++) {
      if (i < accounts1.size() && i < accounts2.size()) {
        testAccountEqualOnChain(accounts1.get(i), accounts2.get(i));
      } else if (i >= accounts1.size()) {
        for (int j = i; j < accounts2.size(); j++) {
          assertEquals(BigInteger.valueOf(0), accounts2.get(j).getBalance().get(ZephyrAssets.ZPH)); // temporary until R8
          assertTrue(accounts2.get(j).getSubaddresses().size() >= 1);
          for (ZephyrSubaddress subaddress : accounts2.get(j).getSubaddresses()) assertFalse(subaddress.isUsed());
        }
        return;
      } else {
        for (int j = i; j < accounts1.size(); j++) {
          assertEquals(BigInteger.valueOf(0), accounts1.get(j).getBalance().get(ZephyrAssets.ZPH)); // temporary until R8
          assertTrue(accounts1.get(j).getSubaddresses().size() >= 1);
          for (ZephyrSubaddress subaddress : accounts1.get(j).getSubaddresses()) assertFalse(subaddress.isUsed());
        }
        return;
      }
    }
  }
  
  private static void testAccountEqualOnChain(ZephyrAccount account1, ZephyrAccount account2) {
    
    // nullify off-chain data for comparison
    List<ZephyrSubaddress> subaddresses1 = account1.getSubaddresses();
    List<ZephyrSubaddress> subaddresses2 = account2.getSubaddresses();
    account1.setSubaddresses(null);
    account2.setSubaddresses(null);
    account1.setTag(null);
    account2.setTag(null);
    
    // test account equality
    assertEquals(account1, account2);
    testSubaddressesEqualOnChain(subaddresses1, subaddresses2);
  }
  
  private static void testSubaddressesEqualOnChain(List<ZephyrSubaddress> subaddresses1, List<ZephyrSubaddress> subaddresses2) {
    for (int i = 0; i < Math.max(subaddresses1.size(), subaddresses2.size()); i++) {
      if (i < subaddresses1.size() && i < subaddresses2.size()) {
        testSubaddressesEqualOnChain(subaddresses1.get(i), subaddresses2.get(i));
      } else if (i >= subaddresses1.size()) {
        for (int j = i; j < subaddresses2.size(); j++) {
          assertEquals(BigInteger.valueOf(0), subaddresses2.get(j).getBalance().get(ZephyrAssets.ZPH)); // temporary until R8
          assertFalse(subaddresses2.get(j).isUsed());
        }
        return;
      } else {
        for (int j = i; j < subaddresses1.size(); j++) {
          assertEquals(BigInteger.valueOf(0), subaddresses1.get(i).getBalance().get(ZephyrAssets.ZPH)); // temporary until R8
          assertFalse(subaddresses1.get(j).isUsed());
        }
        return;
      }
    }
  }
  
  private static void testSubaddressesEqualOnChain(ZephyrSubaddress subaddress1, ZephyrSubaddress subaddress2) {
    subaddress1.setLabel(null); // nullify off-chain data for comparison
    subaddress2.setLabel(null);
    assertEquals(subaddress1, subaddress2);
  }
  
  private static void testTxWalletsEqualOnChain(List<ZephyrTxWallet> txs1, List<ZephyrTxWallet> txs2) {
    
    // nullify off-chain data for comparison
    List<ZephyrTxWallet> allTxs = new ArrayList<ZephyrTxWallet>(txs1);
    allTxs.addAll(txs2);
    for (ZephyrTxWallet tx : allTxs) {
      tx.setNote(null);
      if (tx.getOutgoingTransfer() != null) {
        tx.getOutgoingTransfer().setAddresses(null);
      }
    }
    
    // compare txs
    assertEquals(txs1.size(), txs2.size());
    for (ZephyrTxWallet tx1 : txs1) {
      boolean found = false;
      for (ZephyrTxWallet tx2 : txs2) {
        if (tx1.getHash().equals(tx2.getHash())) {
          
          // transfer cached info if known for comparison
          if (tx1.getOutgoingTransfer() != null && tx1.getOutgoingTransfer().getDestinations() != null) {
            if (tx2.getOutgoingTransfer() == null || tx2.getOutgoingTransfer().getDestinations() == null) transferCachedInfo(tx1, tx2);
          } else if (tx2.getOutgoingTransfer() != null && tx2.getOutgoingTransfer().getDestinations() != null) {
            transferCachedInfo(tx2, tx1);
          }
          
          // test tx equality by merging
          assertTrue(TestUtils.txsMergeable(tx1, tx2), "Txs are not mergeable");
          assertEquals(tx1, tx2);
          found = true;
          
          // test block equality except txs to ignore order
          List<ZephyrTx> blockTxs1 = tx1.getBlock().getTxs();
          List<ZephyrTx> blockTxs2 = tx2.getBlock().getTxs();
          tx1.getBlock().setTxs();
          tx2.getBlock().setTxs();
          assertEquals(tx1.getBlock(), tx2.getBlock());
          tx1.getBlock().setTxs(blockTxs1);
          tx2.getBlock().setTxs(blockTxs2);
        }
      }
      assertTrue(found);  // each tx must have one and only one match
    }
  }
  
  private static void transferCachedInfo(ZephyrTxWallet src, ZephyrTxWallet tgt) {
    
    // fill in missing incoming transfers when sending from/to the same account
    if (src.getIncomingTransfers() != null) {
      for (ZephyrIncomingTransfer inTransfer : src.getIncomingTransfers()) {
        if (inTransfer.getAccountIndex() == src.getOutgoingTransfer().getAccountIndex()) {
          tgt.getIncomingTransfers().add(inTransfer);
        }
      }
      Collections.sort(tgt.getIncomingTransfers(), new IncomingTransferComparator());
    }
    
    // transfer info to outgoing transfer
    if (tgt.getOutgoingTransfer() == null) tgt.setOutgoingTransfer(src.getOutgoingTransfer());
    else {
      tgt.getOutgoingTransfer().setDestinations(src.getOutgoingTransfer().getDestinations());
      tgt.getOutgoingTransfer().setAmount(src.getOutgoingTransfer().getAmount());
    }
    
    // transfer payment id if outgoing // TODO: monero-wallet-rpc does not provide payment id for outgoing transfer when cache missing https://github.com/monero-project/monero/issues/8378
    if (tgt.getOutgoingTransfer() != null) tgt.setPaymentId(src.getPaymentId());
  }
  
  private static void testTransfersEqualOnChain(List<ZephyrTransfer> transfers1, List<ZephyrTransfer> transfers2) {
    assertEquals(transfers1.size(), transfers2.size());
    
    // test and collect transfers per transaction
    Map<String, List<ZephyrTransfer>> txsTransfers1 = new HashMap<String, List<ZephyrTransfer>>();
    Map<String, List<ZephyrTransfer>> txsTransfers2 = new HashMap<String, List<ZephyrTransfer>>();
    Long lastHeight = null;
    ZephyrTxWallet lastTx1 = null;
    ZephyrTxWallet lastTx2 = null;
    for (int i = 0; i < transfers1.size(); i++) {
      ZephyrTransfer transfer1 = transfers1.get(i);
      ZephyrTransfer transfer2 = transfers2.get(i);
      
      // transfers must have same height even if they don't belong to same tx (because tx ordering within blocks is not currently provided by wallet2)
      assertEquals((long) transfer1.getTx().getHeight(), (long) transfer2.getTx().getHeight());
      
      // transfers must be in ascending order by height
      if (lastHeight == null) lastHeight = transfer1.getTx().getHeight();
      else assertTrue(lastHeight <= transfer1.getTx().getHeight());
      
      // transfers must be consecutive per transaction
      if (lastTx1 != transfer1.getTx()) {
        assertFalse(txsTransfers1.containsKey(transfer1.getTx().getHash()));  // cannot be seen before
        lastTx1 = transfer1.getTx();
      }
      if (lastTx2 != transfer2.getTx()) {
        assertFalse(txsTransfers2.containsKey(transfer2.getTx().getHash()));  // cannot be seen before
        lastTx2 = transfer2.getTx();
      }
      
      // collect tx1 transfer
      List<ZephyrTransfer> txTransfers1 = txsTransfers1.get(transfer1.getTx().getHash());
      if (txTransfers1 == null) {
        txTransfers1 = new ArrayList<ZephyrTransfer>();
        txsTransfers1.put(transfer1.getTx().getHash(), txTransfers1);
      }
      txTransfers1.add(transfer1);
      
      // collect tx2 transfer
      List<ZephyrTransfer> txTransfers2 = txsTransfers2.get(transfer2.getTx().getHash());
      if (txTransfers2 == null) {
        txTransfers2 = new ArrayList<ZephyrTransfer>();
        txsTransfers2.put(transfer2.getTx().getHash(), txTransfers2);
      }
      txTransfers2.add(transfer2);
    }
    
    // compare collected transfers per tx for equality
    for (String txHash : txsTransfers1.keySet()) {
      List<ZephyrTransfer> txTransfers1 = txsTransfers1.get(txHash);
      List<ZephyrTransfer> txTransfers2 = txsTransfers2.get(txHash);
      assertEquals(txTransfers1.size(), txTransfers2.size());
      
      // normalize and compare transfers
      for (int i = 0; i < txTransfers1.size(); i++) {
        ZephyrTransfer transfer1 = txTransfers1.get(i);
        ZephyrTransfer transfer2 = txTransfers2.get(i);
        
        // normalize outgoing transfers
        if (transfer1 instanceof ZephyrOutgoingTransfer) {
          ZephyrOutgoingTransfer ot1 = (ZephyrOutgoingTransfer) transfer1;
          ZephyrOutgoingTransfer ot2 = (ZephyrOutgoingTransfer) transfer2;
    
          // transfer destination info if known for comparison
          if (ot1.getDestinations() != null) {
            if (ot2.getDestinations() == null) transferCachedInfo(ot1.getTx(), ot2.getTx());
          } else if (ot2.getDestinations() != null) {
            transferCachedInfo(ot2.getTx(), ot1.getTx());
          }
          
          // nullify other local wallet data
          ot1.setAddresses(null);
          ot2.setAddresses(null);
        }
        
        // normalize incoming transfers
        else {
          ZephyrIncomingTransfer it1 = (ZephyrIncomingTransfer) transfer1;
          ZephyrIncomingTransfer it2 = (ZephyrIncomingTransfer) transfer2;
          it1.setAddress(null);
          it2.setAddress(null);
        }
        
        // compare transfer equality
        assertEquals(transfer1, transfer2);
      }
    }
  }
  
  private static void testOutputWalletsEqualOnChain(List<ZephyrOutputWallet> outputs1, List<ZephyrOutputWallet> outputs2) {
    assertEquals(outputs1.size(), outputs2.size());
    
    // test and collect outputs per transaction
    Map<String, List<ZephyrOutputWallet>> txsOutputs1 = new HashMap<String, List<ZephyrOutputWallet>>();
    Map<String, List<ZephyrOutputWallet>> txsOutputs2 = new HashMap<String, List<ZephyrOutputWallet>>();
    Long lastHeight = null;
    ZephyrTxWallet lastTx1 = null;
    ZephyrTxWallet lastTx2 = null;
    for (int i = 0; i < outputs1.size(); i++) {
      ZephyrOutputWallet output1 = outputs1.get(i);
      ZephyrOutputWallet output2 = outputs2.get(i);
      
      // outputs must have same height even if they don't belong to same tx (because tx ordering within blocks is not currently provided by wallet2)
      assertEquals((long) output1.getTx().getHeight(), (long) output2.getTx().getHeight());
      
      // outputs must be in ascending order by height
      if (lastHeight == null) lastHeight = output1.getTx().getHeight();
      else assertTrue(lastHeight <= output1.getTx().getHeight());
      
      // outputs must be consecutive per transaction
      if (lastTx1 != output1.getTx()) {
        assertFalse(txsOutputs1.containsKey(output1.getTx().getHash()));  // cannot be seen before
        lastTx1 = output1.getTx();
      }
      if (lastTx2 != output2.getTx()) {
        assertFalse(txsOutputs2.containsKey(output2.getTx().getHash()));  // cannot be seen before
        lastTx2 = output2.getTx();
      }
      
      // collect tx1 output
      List<ZephyrOutputWallet> txOutputs1 = txsOutputs1.get(output1.getTx().getHash());
      if (txOutputs1 == null) {
        txOutputs1 = new ArrayList<ZephyrOutputWallet>();
        txsOutputs1.put(output1.getTx().getHash(), txOutputs1);
      }
      txOutputs1.add(output1);
      
      // collect tx2 output
      List<ZephyrOutputWallet> txOutputs2 = txsOutputs2.get(output2.getTx().getHash());
      if (txOutputs2 == null) {
        txOutputs2 = new ArrayList<ZephyrOutputWallet>();
        txsOutputs2.put(output2.getTx().getHash(), txOutputs2);
      }
      txOutputs2.add(output2);
    }
    
    // compare collected outputs per tx for equality
    for (String txHash : txsOutputs1.keySet()) {
      List<ZephyrOutputWallet> txOutputs1 = txsOutputs1.get(txHash);
      List<ZephyrOutputWallet> txOutputs2 = txsOutputs2.get(txHash);
      assertEquals(txOutputs1.size(), txOutputs2.size());
      
      // normalize and compare outputs
      for (int i = 0; i < txOutputs1.size(); i++) {
        ZephyrOutput output1 = txOutputs1.get(i);
        ZephyrOutput output2 = txOutputs2.get(i);
        assertEquals(output1.getTx().getHash(), output2.getTx().getHash());
        assertEquals(output1, output2);
      }
    }
  }
}
