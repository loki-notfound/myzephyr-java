/**
 * Copyright (c) woodser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package zephyr.wallet;

import zephyr.lib.utils.GenUtils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import zephyr.common.ZephyrError;
import zephyr.common.ZephyrRpcConnection;
import zephyr.daemon.model.ZephyrBlock;
import zephyr.daemon.model.ZephyrKeyImage;
import zephyr.wallet.model.ZephyrAccount;
import zephyr.wallet.model.ZephyrAddressBookEntry;
import zephyr.wallet.model.ZephyrIncomingTransfer;
import zephyr.wallet.model.ZephyrIntegratedAddress;
import zephyr.wallet.model.ZephyrMessageSignatureType;
import zephyr.wallet.model.ZephyrOutgoingTransfer;
import zephyr.wallet.model.ZephyrOutputWallet;
import zephyr.wallet.model.ZephyrSubaddress;
import zephyr.wallet.model.ZephyrSyncResult;
import zephyr.wallet.model.ZephyrTransfer;
import zephyr.wallet.model.ZephyrTransferQuery;
import zephyr.wallet.model.ZephyrTxConfig;
import zephyr.wallet.model.ZephyrTxQuery;
import zephyr.wallet.model.ZephyrTxSet;
import zephyr.wallet.model.ZephyrTxWallet;
import zephyr.wallet.model.ZephyrWalletListenerI;

/**
 * Abstract default implementation of a Zephyr wallet.
 */
abstract class ZephyrWalletDefault implements ZephyrWallet {
  
  /** The registered listeners, in the order they were added. */
  protected Set<ZephyrWalletListenerI> listeners;
  
  public ZephyrWalletDefault() {
    this.listeners = new LinkedHashSet<ZephyrWalletListenerI>();
  }
  
  @Override
  public void addListener(ZephyrWalletListenerI listener) {
    listeners.add(listener);
  }
  
  @Override
  public void removeListener(ZephyrWalletListenerI listener) {
    if (!listeners.contains(listener)) throw new ZephyrError("Listener is not registered with wallet");
    listeners.remove(listener);
  }
  
  @Override
  public Set<ZephyrWalletListenerI> getListeners() {
    return new HashSet<ZephyrWalletListenerI>(listeners);
  }
  
  @Override
  public void setDaemonConnection(String uri) {
    setDaemonConnection(uri, null, null);
  }
  
  @Override
  public void setDaemonConnection(String uri, String username, String password) {
    if (uri == null) setDaemonConnection((ZephyrRpcConnection) null);
    else setDaemonConnection(new ZephyrRpcConnection(uri, username, password));
  }
  
  @Override
  public String getPrimaryAddress() {
    return getAddress(0, 0);
  }
  
  @Override
  public ZephyrIntegratedAddress getIntegratedAddress() {
    return getIntegratedAddress(null, null);
  }
  
  @Override
  public ZephyrSyncResult sync() {
    return sync(null, null);
  }
  
  @Override
  public ZephyrSyncResult sync(ZephyrWalletListenerI listener) {
    return sync(null, listener);
  }
  
  @Override
  public ZephyrSyncResult sync(Long startHeight) {
    return sync(startHeight, null);
  }
  
  @Override
  public ZephyrSyncResult sync(Long startHeight, ZephyrWalletListenerI listener) {
    return sync(startHeight, listener);
  }
  
  @Override
  public void startSyncing() {
    startSyncing(null);
  }
  
  @Override
  public BigInteger getBalance(String assetType, Integer accountIdx) {
    return getBalance(assetType, accountIdx, null);
  }
  
  @Override
  public BigInteger getUnlockedBalance(String assetType, Integer accountIdx) {
    return getUnlockedBalance(assetType, accountIdx, null);
  }
  
  @Override
  public List<ZephyrAccount> getAccounts() {
    return getAccounts(false, null);
  }
  
  @Override
  public List<ZephyrAccount> getAccounts(boolean includeSubaddresses) {
    return getAccounts(includeSubaddresses, null);
  }
  
  @Override
  public List<ZephyrAccount> getAccounts(String tag) {
    return getAccounts(false, tag);
  }
  
  @Override
  public ZephyrAccount getAccount(int accountIdx) {
    return getAccount(accountIdx, false);
  }
  
  @Override
  public ZephyrAccount createAccount() {
    return createAccount(null);
  }

  @Override
  public void setAccountLabel(int accountIdx, String label) {
    setSubaddressLabel(accountIdx, 0, label);
  }
  
  @Override
  public List<ZephyrSubaddress> getSubaddresses(int accountIdx) {
    return getSubaddresses(accountIdx, null);
  }
  
  @Override
  public ZephyrSubaddress getSubaddress(int accountIdx, int subaddressIdx) {
    List<ZephyrSubaddress> subaddresses = getSubaddresses(accountIdx, Arrays.asList(subaddressIdx));
    if (subaddresses.isEmpty()) throw new ZephyrError("Subaddress at index " + subaddressIdx + " is not initialized");
    GenUtils.assertEquals("Only 1 subaddress should be returned", 1, subaddresses.size());
    return subaddresses.get(0);
  }
  
  @Override
  public ZephyrSubaddress createSubaddress(int accountIdx) {
    return createSubaddress(accountIdx,  null);
  }
  
  @Override
  public ZephyrTxWallet getTx(String txHash) {
    return getTxs(txHash).get(0);
  }
  
  @Override
  public List<ZephyrTxWallet> getTxs() {
    return getTxs(new ZephyrTxQuery());
  }
  
  @Override
  public List<ZephyrTxWallet> getTxs(String... txHashes) {
    return getTxs(new ZephyrTxQuery().setHashes(txHashes));
  }
  
  @Override
  public List<ZephyrTxWallet> getTxs(List<String> txHashes) {
    return getTxs(txHashes, null);
  }
  
  @Override
  public List<ZephyrTxWallet> getTxs(List<String> txHashes, Collection<String> missingTxHashes) {
    return getTxs(new ZephyrTxQuery().setHashes(txHashes), missingTxHashes);
  }
  
  @Override
  public List<ZephyrTxWallet> getTxs(ZephyrTxQuery query) {
    return getTxs(query, null);
  }
  
  @Override
  public List<ZephyrTransfer> getTransfers() {
    return getTransfers(null);
  }
  
  @Override
  public List<ZephyrTransfer> getTransfers(int accountIdx) {
    ZephyrTransferQuery query = new ZephyrTransferQuery().setAccountIndex(accountIdx);
    return getTransfers(query);
  }
  
  @Override
  public List<ZephyrTransfer> getTransfers(int accountIdx, int subaddressIdx) {
    ZephyrTransferQuery query = new ZephyrTransferQuery().setAccountIndex(accountIdx).setSubaddressIndex(subaddressIdx);
    return getTransfers(query);
  }
  

  @Override
  public List<ZephyrIncomingTransfer> getIncomingTransfers() {
    return getIncomingTransfers(null);
  }

  @Override
  public List<ZephyrIncomingTransfer> getIncomingTransfers(ZephyrTransferQuery query) {
    
    // copy query and set direction
    query = normalizeTransferQuery(query);
    if (Boolean.FALSE.equals(query.isIncoming())) throw new ZephyrError("Transfer query contradicts getting incoming transfers");
    query.setIsIncoming(true);
    
    // fetch and cast transfers
    List<ZephyrIncomingTransfer> inTransfers = new ArrayList<ZephyrIncomingTransfer>();
    for (ZephyrTransfer transfer : getTransfers(query)) {
      inTransfers.add((ZephyrIncomingTransfer) transfer);
    }
    return inTransfers;
  }

  @Override
  public List<ZephyrOutgoingTransfer> getOutgoingTransfers() {
    return getOutgoingTransfers(null);
  }

  @Override
  public List<ZephyrOutgoingTransfer> getOutgoingTransfers(ZephyrTransferQuery query) {
    
    // copy query and set direction
    query = normalizeTransferQuery(query);
    if (Boolean.FALSE.equals(query.isOutgoing())) throw new ZephyrError("Transfer query contradicts getting outgoing transfers");
    query.setIsOutgoing(true);
    
    // fetch and cast transfers
    List<ZephyrOutgoingTransfer> outTransfers = new ArrayList<ZephyrOutgoingTransfer>();
    for (ZephyrTransfer transfer : getTransfers(query)) {
      outTransfers.add((ZephyrOutgoingTransfer) transfer);
    }
    return outTransfers;
  }
  
  @Override
  public List<ZephyrOutputWallet> getOutputs() {
    return getOutputs(null);
  }
  
  @Override
  public String exportOutputs() {
    return exportOutputs(false);
  }
  
  @Override
  public List<ZephyrKeyImage> exportKeyImages() {
    return exportKeyImages(false);
  }
  
  @Override
  public ZephyrTxWallet createTx(ZephyrTxConfig config) {
    if (config == null) throw new ZephyrError("Send request cannot be null");
    if (Boolean.TRUE.equals(config.getCanSplit())) throw new ZephyrError("Cannot request split transactions with createTx() which prevents splitting; use createTxs() instead");
    config = config.copy();
    config.setCanSplit(false);
    return createTxs(config).get(0);
  }
  
  @Override
  public String relayTx(String txMetadata) {
    return relayTxs(Arrays.asList(txMetadata)).get(0);
  }
  
  @Override
  public String relayTx(ZephyrTxWallet tx) {
    return relayTx(tx.getMetadata());
  }
  
  @Override
  public List<String> relayTxs(List<ZephyrTxWallet> txs) {
    List<String> txHexes = new ArrayList<String>();
    for (ZephyrTxWallet tx : txs) txHexes.add(tx.getMetadata());
    return relayTxs(txHexes);
  }
  
  @Override
  public ZephyrTxSet describeUnsignedTxSet(String unsignedTxHex) {
    return describeTxSet(new ZephyrTxSet().setUnsignedTxHex(unsignedTxHex));
  }
  
  @Override
  public ZephyrTxSet describeMultisigTxSet(String multisigTxHex) {
    return describeTxSet(new ZephyrTxSet().setMultisigTxHex(multisigTxHex));
  }
  
  @Override
  public String signMessage(String message) {
    return signMessage(message, ZephyrMessageSignatureType.SIGN_WITH_SPEND_KEY, 0, 0);
  }
  
  @Override
  public String getTxProof(String txHash, String address) {
    return getTxProof(txHash, address, null);
  }
  
  @Override
  public String getSpendProof(String txHash) {
    return getSpendProof(txHash, null);
  }
  
  @Override
  public String getTxNote(String txHash) {
    return getTxNotes(Arrays.asList(txHash)).get(0);
  }
  
  @Override
  public void setTxNote(String txHash, String note) {
    setTxNotes(Arrays.asList(txHash), Arrays.asList(note));
  }
  
  @Override
  public List<ZephyrAddressBookEntry> getAddressBookEntries() {
    return getAddressBookEntries(null);
  }
  
  @Override
  public boolean isMultisig() {
    return getMultisigInfo().isMultisig();
  }
  
  @Override
  public int importMultisigHex(String... multisigHexes) {
    return importMultisigHex(Arrays.asList(multisigHexes));
  }
  
  @Override
  public void close() {
    close(false); // close without saving
  }
  
  /**
   * Returns a copy of the query (a new query for null) whose tx query refers back to it, so that
   * the transfer filter and the tx filter can be applied together.
   *
   * @param query is the transfer query (null for every transfer)
   * @return the normalized copy
   */
  protected ZephyrTransferQuery normalizeTransferQuery(ZephyrTransferQuery query) {
    if (query == null) query = new ZephyrTransferQuery();
    else {
      if (query.getTxQuery() == null) query = query.copy();
      else {
        ZephyrTxQuery txQuery = query.getTxQuery().copy();
        if (query.getTxQuery().getTransferQuery() == query) query = txQuery.getTransferQuery();
        else {
          GenUtils.assertNull("Transfer query's tx query must be circular reference or null", query.getTxQuery().getTransferQuery());
          query = query.copy();
          query.setTxQuery(txQuery);
        }
      }
    }
    if (query.getTxQuery() == null) query.setTxQuery(new ZephyrTxQuery());
    query.getTxQuery().setTransferQuery(query);
    if (query.getTxQuery().getBlock() == null) query.getTxQuery().setBlock(new ZephyrBlock().setTxs(query.getTxQuery()));
    return query;
  }
}
