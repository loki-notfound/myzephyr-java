package zephyr.wallet.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.types.Filter;
import zephyr.lib.utils.GenUtils;
import zephyr.common.ZephyrError;
import zephyr.daemon.model.ZephyrBlock;
import zephyr.daemon.model.ZephyrOutput;

/**
 * Configures a query to retrieve transactions.
 * 
 * All transactions are returned except those that do not meet the criteria defined in this query.
 */
public class ZephyrTxQuery extends ZephyrTxWallet implements Filter<ZephyrTxWallet> {
  
  private Boolean isOutgoing;
  private Boolean isIncoming;
  private List<String> hashes;
  private Boolean hasPaymentId;
  private List<String> paymentIds;
  private Long height;
  private Long minHeight;
  private Long maxHeight;
  private Boolean includeOutputs;
  /**
   * The transfer query that at least one transfer of a matching transaction meets, or null.
   */
  protected ZephyrTransferQuery transferQuery;
  /**
   * The output query that at least one input of a matching transaction meets, or null.
   */
  protected ZephyrOutputQuery inputQuery;
  /**
   * The output query that at least one output of a matching transaction meets, or null.
   */
  protected ZephyrOutputQuery outputQuery;
  
  /**
   * Creates a query that every transaction meets.
   */
  public ZephyrTxQuery() {
    
  }
  
  /**
   * Creates a deep copy of the given query, its transfer, input and output queries included.
   *
   * @param query is the query to copy
   */
  public ZephyrTxQuery(final ZephyrTxQuery query) {
    super(query);
    this.isOutgoing = query.isOutgoing;
    this.isIncoming = query.isIncoming;
    if (query.hashes != null) this.hashes = new ArrayList<String>(query.hashes);
    this.hasPaymentId = query.hasPaymentId;
    if (query.paymentIds != null) this.paymentIds = new ArrayList<String>(query.paymentIds);
    this.height = query.height;
    this.minHeight = query.minHeight;
    this.maxHeight = query.maxHeight;
    this.includeOutputs = query.includeOutputs;
    if (query.transferQuery != null) this.setTransferQuery(new ZephyrTransferQuery(query.transferQuery));
    if (query.inputQuery != null) this.setInputQuery(new ZephyrOutputQuery(query.inputQuery));
    if (query.outputQuery != null) this.setOutputQuery(new ZephyrOutputQuery(query.outputQuery));
  }
  
  @Override
  public ZephyrTxQuery copy() {
    return new ZephyrTxQuery(this);
  }
  
  /**
   * Returns whether a matching transaction must have an outgoing transfer (true) or must have none
   * (false).
   *
   * @return the requirement, null for either
   */
  @Override
  @JsonProperty("isOutgoing")
  public Boolean isOutgoing() {
    return isOutgoing;
  }

  /**
   * Sets whether a matching transaction must have an outgoing transfer (true) or must have none
   * (false).
   *
   * @param isOutgoing is the requirement, null for either
   * @return this query for chaining
   */
  @Override
  public ZephyrTxQuery setIsOutgoing(Boolean isOutgoing) {
    this.isOutgoing = isOutgoing;
    return this;
  }

  /**
   * Returns whether a matching transaction must have an incoming transfer (true) or must have none
   * (false).
   *
   * @return the requirement, null for either
   */
  @Override
  @JsonProperty("isIncoming")
  public Boolean isIncoming() {
    return isIncoming;
  }

  /**
   * Sets whether a matching transaction must have an incoming transfer (true) or must have none
   * (false).
   *
   * @param isIncoming is the requirement, null for either
   * @return this query for chaining
   */
  @Override
  public ZephyrTxQuery setIsIncoming(Boolean isIncoming) {
    this.isIncoming = isIncoming;
    return this;
  }
  
  /**
   * Sets the one transaction hash to match; it also sets {@link #getHashes()} to this hash alone.
   *
   * @param hash is the hash
   * @return this query for chaining
   */
  @Override
  public ZephyrTxQuery setHash(String hash) {
    super.setHash(hash);
    return setHashes(Arrays.asList(hash));
  }

  /**
   * Returns the hashes of the transactions to match.
   *
   * @return the hashes, null for any transaction
   */
  public List<String> getHashes() {
    return hashes;
  }

  /**
   * Sets the hashes of the transactions to match. {@code getTxs} with this query throws for a hash
   * the wallet does not know; {@code getTxs(hashes, missing)} collects such hashes instead. See the
   * user guide, {@code docs/guide/06-history.md}, section "Transactions and transfers".
   *
   * @param hashes are the hashes, null for any transaction
   * @return this query for chaining
   */
  public ZephyrTxQuery setHashes(List<String> hashes) {
    this.hashes = hashes;
    return this;
  }
  
  /**
   * Sets the hashes of the transactions to match, as {@link #setHashes(List)} does.
   *
   * @param hashes are the hashes
   * @return this query for chaining
   */
  public ZephyrTxQuery setHashes(String... hashes) {
    this.hashes = GenUtils.arrayToList(hashes);
    return this;
  }
  
  /**
   * Returns whether a matching transaction must have a payment ID (true) or must have none (false).
   *
   * @return the requirement, null for either
   */
  @JsonProperty("hasPaymentId")
  public Boolean hasPaymentId() {
    return hasPaymentId;
  }

  /**
   * Sets whether a matching transaction must have a payment ID (true) or must have none (false).
   *
   * @param hasPaymentId is the requirement, null for either
   * @return this query for chaining
   */
  public ZephyrTxQuery setHasPaymentId(Boolean hasPaymentId) {
    this.hasPaymentId = hasPaymentId;
    return this;
  }

  /**
   * Returns the payment IDs to match: a transaction matches if its payment ID is one of them.
   *
   * @return the payment IDs, null for any
   */
  public List<String> getPaymentIds() {
    return paymentIds;
  }

  /**
   * Sets the payment IDs to match: a transaction matches if its payment ID is one of them.
   *
   * @param paymentIds are the payment IDs as hex, null for any
   * @return this query for chaining
   */
  public ZephyrTxQuery setPaymentIds(List<String> paymentIds) {
    this.paymentIds = paymentIds;
    return this;
  }
  
  /**
   * Sets the one payment ID to match, as a list of one in {@link #getPaymentIds()}.
   *
   * @param paymentId is the payment ID as hex
   * @return this query for chaining
   */
  @Override
  public ZephyrTxQuery setPaymentId(String paymentId) {
    return setPaymentIds(Arrays.asList(paymentId));
  }
  
  /**
   * Returns the height of the block a matching transaction must be in.
   *
   * @return the height, null for any
   */
  @Override
  public Long getHeight() {
    return height;
  }

  /**
   * Sets the height of the block a matching transaction must be in; a transaction in no block does
   * not match.
   *
   * @param height is the block height, null for any
   * @return this query for chaining
   */
  public ZephyrTxQuery setHeight(Long height) {
    this.height = height;
    return this;
  }

  /**
   * Returns the lowest block height to match, inclusive.
   *
   * @return the height, null for no lower bound
   */
  public Long getMinHeight() {
    return minHeight;
  }

  /**
   * Sets the lowest block height to match, inclusive. In {@code ZephyrWalletFull} the core then
   * leaves out the transactions that are in no block, such as those in the pool (read in the core's
   * code); {@link #meetsCriteria(ZephyrTxWallet)} keeps them.
   *
   * @param minHeight is the height, null for no lower bound
   * @return this query for chaining
   */
  public ZephyrTxQuery setMinHeight(Long minHeight) {
    this.minHeight = minHeight;
    return this;
  }

  /**
   * Returns the highest block height to match, inclusive.
   *
   * @return the height, null for no upper bound
   */
  public Long getMaxHeight() {
    return maxHeight;
  }

  /**
   * Sets the highest block height to match, inclusive; a transaction in no block does not match.
   *
   * @param maxHeight is the height, null for no upper bound
   * @return this query for chaining
   */
  public ZephyrTxQuery setMaxHeight(Long maxHeight) {
    this.maxHeight = maxHeight;
    return this;
  }

  /**
   * Returns whether {@code getTxs} fills the outputs of each transaction.
   *
   * @return true to fill them, null or false otherwise
   */
  public Boolean getIncludeOutputs() {
    return includeOutputs;
  }

  /**
   * Sets whether {@code getTxs} fills the outputs of each transaction: the wallet's own outputs of
   * it, from the same data as {@code getOutputs()}, so without an asset. See the user guide,
   * {@code docs/guide/06-history.md}, section "getOutputs() reports no asset".
   *
   * @param includeOutputs is true to fill them
   * @return this query for chaining
   */
  public ZephyrTxQuery setIncludeOutputs(Boolean includeOutputs) {
    this.includeOutputs = includeOutputs;
    return this;
  }

  /**
   * Returns the transfer query that at least one transfer of a matching transaction meets.
   *
   * @return the transfer query, null if none
   */
  public ZephyrTransferQuery getTransferQuery() {
    return transferQuery;
  }

  /**
   * Sets the transfer query that at least one transfer of a matching transaction meets, and makes
   * this query the transfer query's transaction query.
   *
   * @param transferQuery is the transfer query, null for none
   * @return this query for chaining
   */
  public ZephyrTxQuery setTransferQuery(ZephyrTransferQuery transferQuery) {
    this.transferQuery = transferQuery;
    if (transferQuery != null) transferQuery.txQuery = this;
    return this;
  }
  
  /**
   * Returns the output query that at least one input of a matching transaction meets.
   *
   * @return the input query, null if none
   */
  public ZephyrOutputQuery getInputQuery() {
    return inputQuery;
  }
  
  /**
   * Sets the output query that at least one input of a matching transaction meets, and makes this
   * query its transaction query. In {@code ZephyrWalletFull} the transactions of the history carry
   * no inputs, so a query with an input query matches none (read in the core's code).
   *
   * @param inputQuery is the input query, null for none
   * @return this query for chaining
   */
  public ZephyrTxQuery setInputQuery(ZephyrOutputQuery inputQuery) {
    this.inputQuery = inputQuery;
    if (inputQuery != null) inputQuery.txQuery = this;
    return this;
  }
  
  /**
   * Returns the output query that at least one output of a matching transaction meets.
   *
   * @return the output query, null if none
   */
  public ZephyrOutputQuery getOutputQuery() {
    return outputQuery;
  }

  /**
   * Sets the output query that at least one output of a matching transaction meets, and makes this
   * query its transaction query.
   *
   * @param outputQuery is the output query, null for none
   * @return this query for chaining
   */
  public ZephyrTxQuery setOutputQuery(ZephyrOutputQuery outputQuery) {
    this.outputQuery = outputQuery;
    if (outputQuery != null) outputQuery.txQuery = this;
    return this;
  }
  
  @Override
  public boolean meetsCriteria(ZephyrTxWallet tx) {
    return meetsCriteria(tx, true);
  }
  
  /**
   * Returns whether the given transaction meets this query, evaluated in Java.
   *
   * @param tx is the transaction to test
   * @param queryChildren is true to apply the transfer, input and output queries too
   * @return true if the transaction meets the query
   */
  protected boolean meetsCriteria(ZephyrTxWallet tx, boolean queryChildren) {
    if (tx == null) throw new ZephyrError("No tx given to ZephyrTxQuery.meetsCriteria()");
    
    // filter on tx
    if (this.getHash() != null && !this.getHash().equals(tx.getHash())) return false;
    if (this.getPaymentId() != null && !this.getPaymentId().equals(tx.getPaymentId())) return false;
    if (this.isConfirmed() != null && this.isConfirmed() != tx.isConfirmed()) return false;
    if (this.inTxPool() != null && this.inTxPool() != tx.inTxPool()) return false;
    if (this.getRelay() != null && this.getRelay() != tx.getRelay()) return false;
    if (this.isRelayed() != null && this.isRelayed() != tx.isRelayed()) return false;
    if (this.isFailed() != null && this.isFailed() != tx.isFailed()) return false;
    if (this.isMinerTx() != null && this.isMinerTx() != tx.isMinerTx()) return false;
    if (this.isLocked() != null && this.isLocked() != tx.isLocked()) return false;
    
    // filter on having a payment id
    if (this.hasPaymentId() != null) {
      if (this.hasPaymentId() && tx.getPaymentId() == null) return false;
      if (!this.hasPaymentId() && tx.getPaymentId() != null) return false;
    }
    
    // filter on incoming
    if (this.isIncoming() != null && this.isIncoming() != Boolean.TRUE.equals(tx.isIncoming())) return false;
    
    // filter on outgoing
    if (this.isOutgoing() != null && this.isOutgoing() != Boolean.TRUE.equals(tx.isOutgoing())) return false;
    
    // filter on remaining fields
    Long txHeight = tx.getBlock() == null ? null : tx.getBlock().getHeight();
    if (this.getHashes() != null && !this.getHashes().contains(tx.getHash())) return false;
    if (this.getPaymentIds() != null && !this.getPaymentIds().contains(tx.getPaymentId())) return false;
    if (this.getHeight() != null && !this.getHeight().equals(txHeight)) return false;
    if (this.getMinHeight() != null && txHeight != null && txHeight < this.getMinHeight()) return false; // do not filter unconfirmed
    if (this.getMaxHeight() != null && (txHeight == null || txHeight > this.getMaxHeight())) return false;
    
    // done if not querying transfers or outputs
    if (!queryChildren) return true;
    
    // at least one transfer must meet transfer query if defined
    if (this.getTransferQuery() != null) {
      boolean matchFound = false;
      if (tx.getOutgoingTransfer() != null && this.getTransferQuery().meetsCriteria(tx.getOutgoingTransfer(), false)) matchFound = true;
      else if (tx.getIncomingTransfers() != null) {
        for (ZephyrTransfer incomingTransfer : tx.getIncomingTransfers()) {
          if (this.getTransferQuery().meetsCriteria(incomingTransfer, false)) {
            matchFound = true;
            break;
          }
        }
      }
      if (!matchFound) return false;
    }
    
    // at least one input must meet input query if defined
    if (this.getInputQuery() != null) {
      if (tx.getInputs() == null || tx.getInputs().isEmpty()) return false;
      boolean matchFound = false;
      for (ZephyrOutputWallet input : tx.getInputsWallet()) {
        if (this.getInputQuery().meetsCriteria(input, false)) {
          matchFound = true;
          break;
        }
      }
      if (!matchFound) return false;
    }
    
    // at least one output must meet output query if defined
    if (this.getOutputQuery() != null) {
      if (tx.getOutputs() == null || tx.getOutputs().isEmpty()) return false;
      boolean matchFound = false;
      for (ZephyrOutputWallet output : tx.getOutputsWallet()) {
        if (this.getOutputQuery().meetsCriteria(output, false)) {
          matchFound = true;
          break;
        }
      }
      if (!matchFound) return false;
    }
    
    return true;  // transaction meets query criteria
  }
  
  @Override
  public String toString() {
    throw new RuntimeException("ZephyrTxQuery.toString() not implemented");
  }
  
  // ------------------- OVERRIDE CO-VARIANT RETURN TYPES ---------------------

  @Override
  public ZephyrTxQuery setIncomingTransfers(List<ZephyrIncomingTransfer> incomingTransfers) {
    super.setIncomingTransfers(incomingTransfers);
    return this;
  }

  @Override
  public ZephyrTxQuery setOutgoingTransfer(ZephyrOutgoingTransfer outgoingTransfer) {
    super.setOutgoingTransfer(outgoingTransfer);
    return this;
  }

  @Override
  public ZephyrTxQuery setOutputs(List<ZephyrOutput> outputs) {
    super.setOutputs(outputs);
    return this;
  }

  @Override
  public ZephyrTxQuery setNote(String note) {
    super.setNote(note);
    return this;
  }
  
  @Override
  public ZephyrTxQuery setIsLocked(Boolean isLocked) {
    super.setIsLocked(isLocked);
    return this;
  }

  @Override
  public ZephyrTxQuery setBlock(ZephyrBlock block) {
    super.setBlock(block);
    return this;
  }
  
  @Override
  public ZephyrTxQuery setVersion(Integer version) {
    super.setVersion(version);
    return this;
  }

  @Override
  public ZephyrTxQuery setIsMinerTx(Boolean isMinerTx) {
    super.setIsMinerTx(isMinerTx);
    return this;
  }

  @Override
  public ZephyrTxQuery setFee(BigInteger fee) {
    super.setFee(fee);
    return this;
  }

  @Override
  public ZephyrTxQuery setRingSize(Integer ringSize) {
    super.setRingSize(ringSize);
    return this;
  }

  @Override
  public ZephyrTxQuery setRelay(Boolean relay) {
    super.setRelay(relay);
    return this;
  }

  @Override
  public ZephyrTxQuery setIsRelayed(Boolean isRelayed) {
    super.setIsRelayed(isRelayed);
    return this;
  }

  @Override
  public ZephyrTxQuery setIsConfirmed(Boolean isConfirmed) {
    super.setIsConfirmed(isConfirmed);
    return this;
  }

  @Override
  public ZephyrTxQuery setInTxPool(Boolean inTxPool) {
    super.setInTxPool(inTxPool);
    return this;
  }

  @Override
  public ZephyrTxQuery setNumConfirmations(Long numConfirmations) {
    super.setNumConfirmations(numConfirmations);
    return this;
  }

  @Override
  public ZephyrTxQuery setUnlockHeight(Long unlockHeight) {
    super.setUnlockHeight(unlockHeight);
    return this;
  }

  @Override
  public ZephyrTxQuery setLastRelayedTimestamp(Long lastRelayedTimestamp) {
    super.setLastRelayedTimestamp(lastRelayedTimestamp);
    return this;
  }

  @Override
  public ZephyrTxQuery setReceivedTimestamp(Long receivedTimestamp) {
    super.setReceivedTimestamp(receivedTimestamp);
    return this;
  }

  @Override
  public ZephyrTxQuery setIsDoubleSpendSeen(Boolean isDoubleSpend) {
    super.setIsDoubleSpendSeen(isDoubleSpend);
    return this;
  }

  @Override
  public ZephyrTxQuery setKey(String key) {
    super.setKey(key);
    return this;
  }

  @Override
  public ZephyrTxQuery setFullHex(String hex) {
    super.setFullHex(hex);
    return this;
  }

  @Override
  public ZephyrTxQuery setPrunedHex(String prunedHex) {
    super.setPrunedHex(prunedHex);
    return this;
  }

  @Override
  public ZephyrTxQuery setPrunableHex(String prunableHex) {
    super.setPrunableHex(prunableHex);
    return this;
  }

  @Override
  public ZephyrTxQuery setPrunableHash(String prunableHash) {
    super.setPrunableHash(prunableHash);
    return this;
  }

  @Override
  public ZephyrTxQuery setSize(Long size) {
    super.setSize(size);
    return this;
  }

  @Override
  public ZephyrTxQuery setWeight(Long weight) {
    super.setWeight(weight);
    return this;
  }

  @Override
  public ZephyrTxQuery setInputs(List<ZephyrOutput> inputs) {
    super.setInputs(inputs);
    return this;
  }

  @Override
  public ZephyrTxQuery setOutputIndices(List<Long> outputIndices) {
    super.setOutputIndices(outputIndices);
    return this;
  }

  @Override
  public ZephyrTxQuery setMetadata(String metadata) {
    super.setMetadata(metadata);
    return this;
  }

  @Override
  public ZephyrTxQuery setTxSet(ZephyrTxSet commonTxSets) {
    super.setTxSet(commonTxSets);
    return this;
  }

  @Override
  public ZephyrTxQuery setExtra(int[] extra) {
    super.setExtra(extra);
    return this;
  }

  @Override
  public ZephyrTxQuery setRctSignatures(Object rctSignatures) {
    super.setRctSignatures(rctSignatures);
    return this;
  }

  @Override
  public ZephyrTxQuery setRctSigPrunable(Object rctSigPrunable) {
    super.setRctSigPrunable(rctSigPrunable);
    return this;
  }

  @Override
  public ZephyrTxQuery setIsKeptByBlock(Boolean isKeptByBlock) {
    super.setIsKeptByBlock(isKeptByBlock);
    return this;
  }

  @Override
  public ZephyrTxQuery setIsFailed(Boolean isFailed) {
    super.setIsFailed(isFailed);
    return this;
  }
  
  @Override
  public ZephyrTxQuery setLastFailedHeight(Long lastFailedHeight) {
    super.setLastFailedHeight(lastFailedHeight);
    return this;
  }

  @Override
  public ZephyrTxQuery setLastFailedHash(String lastFailedId) {
    super.setLastFailedHash(lastFailedId);
    return this;
  }

  @Override
  public ZephyrTxQuery setMaxUsedBlockHeight(Long maxUsedBlockHeight) {
    super.setMaxUsedBlockHeight(maxUsedBlockHeight);
    return this;
  }

  @Override
  public ZephyrTxQuery setMaxUsedBlockHash(String maxUsedBlockId) {
    super.setMaxUsedBlockHash(maxUsedBlockId);
    return this;
  }

  @Override
  public ZephyrTxQuery setSignatures(List<String> signatures) {
    super.setSignatures(signatures);
    return this;
  }
}
