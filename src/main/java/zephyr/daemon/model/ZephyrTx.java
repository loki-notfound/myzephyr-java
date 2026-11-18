package zephyr.daemon.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.utils.GenUtils;

/**
 * Represents a transaction on the Zephyr network.
 */
public class ZephyrTx {
  
  /**
   * The payment ID of sixteen zeros, which stands for no payment ID. {@code ZephyrWalletFull}
   * reports a transaction with this payment ID as having none ({@link #getPaymentId()} is null).
   */
  public static final String DEFAULT_PAYMENT_ID = "0000000000000000";

  private ZephyrBlock block;
  private String hash;
  private Integer version;
  private Boolean isMinerTx;
  private String paymentId;
  private BigInteger fee;
  private Integer ringSize;
  private Boolean relay;
  private Boolean isRelayed;
  private Boolean isConfirmed;
  private Boolean inTxPool;
  private Long numConfirmations;
  private Long unlockHeight;
  private Long lastRelayedTimestamp;
  private Long receivedTimestamp;
  private Boolean isDoubleSpendSeen;
  private String key;
  private String fullHex;
  private String prunedHex;
  private String prunableHex;
  private String prunableHash;
  private Long size;
  private Long weight;
  private List<ZephyrOutput> inputs;
  private List<ZephyrOutput> outputs;
  private List<Long> outputIndices;
  private String metadata;
  private int[] extra;  // TODO: switch to string for consistency with ZephyrTxWallet
  private Object rctSignatures; // TODO: implement
  private Object rctSigPrunable;  // TODO: implement
  private Boolean isKeptByBlock;
  private Boolean isFailed;
  private Long lastFailedHeight;
  private String lastFailedHash;
  private Long maxUsedBlockHeight;
  private String maxUsedBlockHash;
  private List<String> signatures;
  
  /**
   * Creates an empty transaction; every field is null.
   */
  public ZephyrTx() {
    // nothing to build
  }
  
  /**
   * Construct this transaction as a deep copy of the given transaction.
   * 
   * @param tx is the transaction to make a deep copy of
   */
  public ZephyrTx(final ZephyrTx tx) {
    this.hash = tx.hash;
    this.version = tx.version;
    this.isMinerTx = tx.isMinerTx;
    this.paymentId = tx.paymentId;
    this.fee = tx.fee;
    this.ringSize = tx.ringSize;
    this.relay = tx.relay;
    this.isRelayed = tx.isRelayed;
    this.isConfirmed = tx.isConfirmed;
    this.inTxPool = tx.inTxPool;
    this.numConfirmations = tx.numConfirmations;
    this.unlockHeight = tx.unlockHeight;
    this.lastRelayedTimestamp = tx.lastRelayedTimestamp;
    this.receivedTimestamp = tx.receivedTimestamp;
    this.isDoubleSpendSeen = tx.isDoubleSpendSeen;
    this.key = tx.key;
    this.fullHex = tx.fullHex;
    this.prunedHex = tx.prunedHex;
    this.prunableHex = tx.prunableHex;
    this.prunableHash = tx.prunableHash;
    this.size = tx.size;
    this.weight = tx.weight;
    if (tx.inputs != null) {
      this.inputs = new ArrayList<ZephyrOutput>();
      for (ZephyrOutput input : tx.inputs) inputs.add(input.copy().setTx(this));
    }
    if (tx.outputs != null) {
      this.outputs = new ArrayList<ZephyrOutput>();
      for (ZephyrOutput output : tx.outputs) outputs.add(output.copy().setTx(this));
    }
    if (tx.outputIndices != null) this.outputIndices = new ArrayList<Long>(tx.outputIndices);
    this.metadata = tx.metadata;
    if (tx.extra != null) this.extra = tx.extra.clone();
    this.rctSignatures = tx.rctSignatures;
    this.rctSigPrunable = tx.rctSigPrunable;
    this.isKeptByBlock = tx.isKeptByBlock;
    this.isFailed = tx.isFailed;
    this.lastFailedHeight = tx.lastFailedHeight;
    this.lastFailedHash = tx.lastFailedHash;
    this.maxUsedBlockHeight = tx.maxUsedBlockHeight;
    this.maxUsedBlockHash = tx.maxUsedBlockHash;
    if (tx.signatures != null) this.signatures = new ArrayList<String>(tx.signatures);
  }
  
  /**
   * Returns a deep copy of this transaction, its inputs and outputs included. The copy does not
   * reference the block, so its {@link #getHeight()} is null.
   *
   * @return the copy
   */
  public ZephyrTx copy() {
    return new ZephyrTx(this);
  }
  
  /**
   * Returns the block that contains this transaction.
   *
   * @return the block, null while the transaction is in no block (in the pool, or built and not yet
   * relayed)
   */
  @JsonBackReference("block_txs")
  public ZephyrBlock getBlock() {
    return block;
  }
  
  /**
   * Sets the block that contains this transaction.
   *
   * @param block is the block, null for none
   * @return this transaction for chaining
   */
  public ZephyrTx setBlock(ZephyrBlock block) {
    this.block = block;
    return this;
  }
  
  /**
   * Returns the height of the block that contains this transaction.
   *
   * @return the block height, null while the transaction is in no block: a transaction in the pool,
   * or one that {@code createTxs} has just built
   */
  public Long getHeight() {
    return this.getBlock() == null ? null : this.getBlock().getHeight();
  }
  
  /**
   * Returns the hash of the transaction, its ID (64 hex characters).
   *
   * @return the hash
   */
  public String getHash() {
    return hash;
  }
  
  /**
   * Sets the hash of the transaction.
   *
   * @param hash is the hash
   * @return this transaction for chaining
   */
  public ZephyrTx setHash(String hash) {
    this.hash = hash;
    return this;
  }
  
  /**
   * Returns the version of the transaction format. {@code ZephyrWalletFull} does not fill it on the
   * transactions of {@code getTxs} and {@code createTxs} (read in the core's code); the transaction
   * of an output notification to a listener carries it.
   *
   * @return the version, null if unknown
   */
  public Integer getVersion() {
    return version;
  }
  
  /**
   * Sets the version of the transaction format.
   *
   * @param version is the version
   * @return this transaction for chaining
   */
  public ZephyrTx setVersion(Integer version) {
    this.version = version;
    return this;
  }
  
  /**
   * Returns whether this is a miner (coinbase) transaction, which creates the block reward.
   *
   * @return true for a miner transaction, null if unknown
   */
  @JsonProperty("isMinerTx")
  public Boolean isMinerTx() {
    return isMinerTx;
  }
  
  /**
   * Sets whether this is a miner transaction.
   *
   * @param isMinerTx is true for a miner transaction
   * @return this transaction for chaining
   */
  public ZephyrTx setIsMinerTx(Boolean isMinerTx) {
    this.isMinerTx = isMinerTx;
    return this;
  }
  
  /**
   * Returns the payment ID of the transaction as hex. {@code ZephyrWalletFull} shortens a
   * 64-character ID whose last 48 characters are zeros to its first 16 characters, and reports the
   * all-zero ID as null (read in the core's code).
   *
   * @return the payment ID, null if the transaction has none
   */
  public String getPaymentId() {
    return paymentId;
  }
  
  /**
   * Sets the payment ID.
   *
   * @param paymentId is the payment ID as hex
   * @return this transaction for chaining
   */
  public ZephyrTx setPaymentId(String paymentId) {
    this.paymentId = paymentId;
    return this;
  }
  
  /**
   * Returns the fee of the transaction in atomic units (10^12 per coin). The fee is paid in the
   * source asset, the asset the transaction spends, also for a conversion. See the user guide,
   * {@code docs/guide/07-sending.md}, section "Fees and priorities".
   *
   * @return the fee in atomic units of the source asset, null if unknown
   */
  public BigInteger getFee() {
    return fee;
  }
  
  /**
   * Sets the fee.
   *
   * @param fee is the fee in atomic units
   * @return this transaction for chaining
   */
  public ZephyrTx setFee(BigInteger fee) {
    this.fee = fee;
    return this;
  }
  
  /**
   * Returns the ring size: the number of outputs each input is hidden among, its own included.
   * {@code ZephyrWalletFull} sets the constant 16 (the core's {@code RING_SIZE}) on the
   * transactions that {@code createTxs} and the sweeps return without reading the built
   * transaction, leaves it null on the transactions of {@code getTxs}, and {@code describeTxSet}
   * reports the smallest ring of each transaction (read in the core's code).
   *
   * @return the ring size, null if unknown
   */
  public Integer getRingSize() {
    return ringSize;
  }
  
  /**
   * Sets the ring size.
   *
   * @param ringSize is the ring size
   * @return this transaction for chaining
   */
  public ZephyrTx setRingSize(Integer ringSize) {
    this.ringSize = ringSize;
    return this;
  }
  
  /**
   * Returns whether the transaction is meant to be relayed: on a transaction that {@code createTxs}
   * or a sweep returns, the relay setting of its configuration; true for the transactions of
   * {@code getTxs}.
   *
   * @return whether the transaction is meant to be relayed, null if unknown
   */
  public Boolean getRelay() {
    return relay;
  }
  
  /**
   * Sets whether the transaction is meant to be relayed.
   *
   * @param relay is true to relay
   * @return this transaction for chaining
   */
  public ZephyrTx setRelay(Boolean relay) {
    this.relay = relay;
    return this;
  }
  
  /**
   * Returns whether the transaction has been relayed to the network. A transaction that
   * {@code createTxs} built without relaying reports false; relaying it later does not update the
   * returned object, so read it again with {@code getTxs}.
   *
   * @return true if relayed, null if unknown
   */
  @JsonProperty("isRelayed")
  public Boolean isRelayed() {
    return isRelayed;
  }
  
  /**
   * Sets whether the transaction has been relayed.
   *
   * @param isRelayed is true if relayed
   * @return this transaction for chaining
   */
  public ZephyrTx setIsRelayed(Boolean isRelayed) {
    this.isRelayed = isRelayed;
    return this;
  }
  
  /**
   * Returns whether the transaction is in a block.
   *
   * @return true if in a block, null if unknown
   */
  @JsonProperty("isConfirmed")
  public Boolean isConfirmed() {
    return isConfirmed;
  }
  
  /**
   * Sets whether the transaction is in a block.
   *
   * @param isConfirmed is true if in a block
   * @return this transaction for chaining
   */
  public ZephyrTx setIsConfirmed(Boolean isConfirmed) {
    this.isConfirmed = isConfirmed;
    return this;
  }
  
  /**
   * Returns whether the transaction is in the node's pool: relayed, and not yet in a block.
   *
   * @return true if in the pool, null if unknown
   */
  @JsonProperty("inTxPool")
  public Boolean inTxPool() {
    return inTxPool;
  }
  
  /**
   * Sets whether the transaction is in the pool.
   *
   * @param inTxPool is true if in the pool
   * @return this transaction for chaining
   */
  public ZephyrTx setInTxPool(Boolean inTxPool) {
    this.inTxPool = inTxPool;
    return this;
  }
  
  /**
   * Returns the number of confirmations: for a wallet transaction, the wallet's height as of its
   * last sync minus the height of the transaction's block; 0 in the pool.
   *
   * @return the number of confirmations, null if unknown
   */
  public Long getNumConfirmations() {
    return numConfirmations;
  }
  
  /**
   * Sets the number of confirmations.
   *
   * @param numConfirmations is the number of confirmations
   * @return this transaction for chaining
   */
  public ZephyrTx setNumConfirmations(Long numConfirmations) {
    this.numConfirmations = numConfirmations;
    return this;
  }
  
  /**
   * Returns the unlock time the transaction itself carries: 0 when it sets none, as the
   * transactions this library builds do unless {@code ZephyrTxConfig.setUnlockHeight} is used.
   * Received funds and change unlock 10 blocks after the block that contains them in any case;
   * {@code ZephyrTxWallet.isLocked()} tells whether they have. See the user guide,
   * {@code docs/guide/05-assets-and-balances.md}, section "Locked and unlocked".
   *
   * @return the unlock time (a block height), 0 for none, null if unknown
   */
  public Long getUnlockHeight() {
    return unlockHeight;
  }
  
  /**
   * Sets the unlock time the transaction carries.
   *
   * @param unlockHeight is the unlock height, 0 for none
   * @return this transaction for chaining
   */
  public ZephyrTx setUnlockHeight(Long unlockHeight) {
    this.unlockHeight = unlockHeight;
    return this;
  }
  
  /**
   * Returns when the transaction was last relayed, in seconds since the Unix epoch.
   * {@code ZephyrWalletFull} sets it only on a transaction that {@code createTxs} or a sweep
   * relayed in the same call (the local time of the call).
   *
   * @return the time in seconds, null if unknown
   */
  public Long getLastRelayedTimestamp() {
    return lastRelayedTimestamp;
  }
  
  /**
   * Sets when the transaction was last relayed.
   *
   * @param lastRelayedTimestamp is the time in seconds since the Unix epoch
   * @return this transaction for chaining
   */
  public ZephyrTx setLastRelayedTimestamp(Long lastRelayedTimestamp) {
    this.lastRelayedTimestamp = lastRelayedTimestamp;
    return this;
  }
  
  /**
   * Returns when the node received the transaction into its pool, in seconds since the Unix epoch.
   * {@code ZephyrWalletFull} does not fill it (null). {@code ZephyrDaemonRpc} fills it from the
   * node; its transaction calls are inherited from monero-java; not tested against Zephyr.
   *
   * @return the time in seconds, null if unknown
   */
  public Long getReceivedTimestamp() {
    return receivedTimestamp;
  }
  
  /**
   * Sets when the node received the transaction.
   *
   * @param receivedTimestamp is the time in seconds since the Unix epoch
   * @return this transaction for chaining
   */
  public ZephyrTx setReceivedTimestamp(Long receivedTimestamp) {
    this.receivedTimestamp = receivedTimestamp;
    return this;
  }
  
  /**
   * Returns whether the node saw another transaction spend the same inputs while this one was in
   * the pool. {@code ZephyrWalletFull} reports false for transactions in a block and the node's
   * flag for incoming transactions in the pool.
   *
   * @return true if a double spend was seen, null if unknown
   */
  @JsonProperty("isDoubleSpendSeen")
  public Boolean isDoubleSpendSeen() {
    return isDoubleSpendSeen;
  }
  
  /**
   * Sets whether a double spend was seen.
   *
   * @param isDoubleSpend is true if a double spend was seen
   * @return this transaction for chaining
   */
  public ZephyrTx setIsDoubleSpendSeen(Boolean isDoubleSpend) {
    this.isDoubleSpendSeen = isDoubleSpend;
    return this;
  }
  
  /**
   * Returns the secret key of the transaction as hex. {@code ZephyrWalletFull} sets it on the
   * transactions that {@code createTxs} and the sweeps return; the transactions of {@code getTxs}
   * do not carry it, {@code getTxKey(hash)} reads it from the wallet. With the key and the hash
   * anyone can check the transaction against an address, so treat it as a secret and do not log it.
   *
   * @return the secret key, null if not known here
   */
  public String getKey() {
    return key;
  }
  
  /**
   * Sets the secret key of the transaction.
   *
   * @param key is the secret key as hex
   * @return this transaction for chaining
   */
  public ZephyrTx setKey(String key) {
    this.key = key;
    return this;
  }
  
  /**
   * Returns the complete transaction, serialized as hex. {@code ZephyrWalletFull} sets it on the
   * transactions that {@code createTxs} and the sweeps return, and not on those of {@code getTxs}.
   *
   * @return the transaction as hex, null if not known here
   */
  public String getFullHex() {
    return fullHex;
  }
  
  /**
   * Sets the complete transaction as hex.
   *
   * @param fullHex is the transaction as hex
   * @return this transaction for chaining
   */
  public ZephyrTx setFullHex(String fullHex) {
    this.fullHex = fullHex;
    return this;
  }
  
  /**
   * Returns the pruned part of the transaction (without its prunable signature data) as hex.
   * {@code ZephyrWalletFull} does not fill it (null). {@code ZephyrDaemonRpc} fills it from the
   * node; its transaction calls are inherited from monero-java; not tested against Zephyr.
   *
   * @return the pruned transaction as hex, null if not known
   */
  public String getPrunedHex() {
    return prunedHex;
  }
  
  /**
   * Sets the pruned part of the transaction as hex.
   *
   * @param prunedHex is the pruned part as hex
   * @return this transaction for chaining
   */
  public ZephyrTx setPrunedHex(String prunedHex) {
    this.prunedHex = prunedHex;
    return this;
  }
  
  /**
   * Returns the prunable part of the transaction (its signature data) as hex.
   * {@code ZephyrWalletFull} does not fill it (null). {@code ZephyrDaemonRpc} fills it from the
   * node; its transaction calls are inherited from monero-java; not tested against Zephyr.
   *
   * @return the prunable part as hex, null if not known
   */
  public String getPrunableHex() {
    return prunableHex;
  }
  
  /**
   * Sets the prunable part of the transaction as hex.
   *
   * @param prunableHex is the prunable part as hex
   * @return this transaction for chaining
   */
  public ZephyrTx setPrunableHex(String prunableHex) {
    this.prunableHex = prunableHex;
    return this;
  }
  
  /**
   * Returns the hash of the prunable part of the transaction. {@code ZephyrWalletFull} does not
   * fill it (null). {@code ZephyrDaemonRpc} fills it from the node; its transaction calls are
   * inherited from monero-java; not tested against Zephyr.
   *
   * @return the hash, null if not known
   */
  public String getPrunableHash() {
    return prunableHash;
  }
  
  /**
   * Sets the hash of the prunable part.
   *
   * @param prunableHash is the hash
   * @return this transaction for chaining
   */
  public ZephyrTx setPrunableHash(String prunableHash) {
    this.prunableHash = prunableHash;
    return this;
  }
  
  /**
   * Returns the size of the transaction in bytes. {@code ZephyrWalletFull} does not fill it (null).
   * {@code ZephyrDaemonRpc} fills it from the node; its transaction calls are inherited from
   * monero-java; not tested against Zephyr.
   *
   * @return the size in bytes, null if not known
   */
  public Long getSize() {
    return size;
  }
  
  /**
   * Sets the size of the transaction.
   *
   * @param size is the size in bytes
   * @return this transaction for chaining
   */
  public ZephyrTx setSize(Long size) {
    this.size = size;
    return this;
  }
  
  /**
   * Returns the weight of the transaction in bytes, the measure its fee is based on.
   * {@code ZephyrWalletFull} sets it on the transactions that {@code createTxs} and the sweeps
   * return.
   *
   * @return the weight, null if not known
   */
  public Long getWeight() {
    return weight;
  }
  
  /**
   * Sets the weight of the transaction.
   *
   * @param weight is the weight in bytes
   * @return this transaction for chaining
   */
  public ZephyrTx setWeight(Long weight) {
    this.weight = weight;
    return this;
  }
  
  /**
   * Returns the inputs of the transaction: the outputs it spends. In {@code ZephyrWalletFull} the
   * inputs of a transaction that {@code createTxs} or a sweep returns carry only their key images,
   * the transactions of {@code getTxs} carry none, and the transaction of a spent notification to a
   * listener carries the spent output.
   *
   * @return the inputs, null if not known
   */
  @JsonManagedReference
  public List<ZephyrOutput> getInputs() {
    return inputs;
  }
  
  /**
   * Sets the inputs of the transaction.
   *
   * @param inputs are the inputs
   * @return this transaction for chaining
   */
  public ZephyrTx setInputs(List<ZephyrOutput> inputs) {
    this.inputs = inputs;
    return this;
  }
  
  /**
   * Returns the outputs of the transaction. For a wallet transaction these are only the wallet's
   * own outputs ({@code ZephyrOutputWallet}). {@code ZephyrWalletFull.getTxs} fills them only when
   * the query asks for them ({@code ZephyrTxQuery.setIncludeOutputs(true)} or an output query),
   * from the same data as {@code getOutputs()}, so they carry no asset.
   *
   * @return the outputs, null if not known
   */
  @JsonManagedReference
  public List<ZephyrOutput> getOutputs() {
    return outputs;
  }
  
  /**
   * Sets the outputs of the transaction.
   *
   * @param outputs are the outputs
   * @return this transaction for chaining
   */
  public ZephyrTx setOutputs(List<ZephyrOutput> outputs) {
    this.outputs = outputs;
    return this;
  }
  
  /**
   * Returns the global indices of the transaction's outputs on the chain. {@code ZephyrWalletFull}
   * does not fill it (null). {@code ZephyrDaemonRpc} fills it from the node; its transaction calls
   * are inherited from monero-java; not tested against Zephyr.
   *
   * @return the output indices, null if not known
   */
  public List<Long> getOutputIndices() {
    return outputIndices;
  }
  
  /**
   * Sets the global indices of the outputs.
   *
   * @param outputIndices are the output indices
   * @return this transaction for chaining
   */
  public ZephyrTx setOutputIndices(List<Long> outputIndices) {
    this.outputIndices = outputIndices;
    return this;
  }
  
  /**
   * Returns the serialized pending transaction that {@code relayTx} and {@code relayTxs} send.
   * {@code ZephyrWalletFull} sets it on the transactions that {@code createTxs} and the sweeps
   * return; the transactions of {@code getTxs} do not carry it. It contains the transaction's
   * secret key, so treat it as a secret and do not log it.
   *
   * @return the metadata, null if not known here
   */
  public String getMetadata() {
    return metadata;
  }
  
  /**
   * Sets the serialized pending transaction.
   *
   * @param metadata is the metadata
   * @return this transaction for chaining
   */
  public ZephyrTx setMetadata(String metadata) {
    this.metadata = metadata;
    return this;
  }
  
  /**
   * Returns the extra field of the transaction as byte values from 0 to 255.
   * {@code ZephyrWalletFull} does not fill it (null). {@code ZephyrDaemonRpc} fills it from the
   * node; its transaction calls are inherited from monero-java; not tested against Zephyr.
   *
   * @return the extra field, null if not known
   */
  public int[] getExtra() {
    return extra;
  }
  
  /**
   * Sets the extra field of the transaction.
   *
   * @param extra is the extra field as byte values
   * @return this transaction for chaining
   */
  public ZephyrTx setExtra(int[] extra) {
    this.extra = extra;
    return this;
  }
  
  /**
   * Returns the RingCT signature data of the transaction as an untyped structure.
   * {@code ZephyrWalletFull} does not fill it (null). {@code ZephyrDaemonRpc} fills it from the
   * node; its transaction calls are inherited from monero-java; not tested against Zephyr.
   *
   * @return the signature data, null if not known
   */
  public Object getRctSignatures() {
    return rctSignatures;
  }
  
  /**
   * Sets the RingCT signature data.
   *
   * @param rctSignatures is the signature data
   * @return this transaction for chaining
   */
  public ZephyrTx setRctSignatures(Object rctSignatures) {
    this.rctSignatures = rctSignatures;
    return this;
  }
  
  /**
   * Returns the prunable RingCT signature data of the transaction as an untyped structure.
   * {@code ZephyrWalletFull} does not fill it (null). {@code ZephyrDaemonRpc} fills it from the
   * node; its transaction calls are inherited from monero-java; not tested against Zephyr.
   *
   * @return the prunable signature data, null if not known
   */
  public Object getRctSigPrunable() {
    return rctSigPrunable;
  }
  
  /**
   * Sets the prunable RingCT signature data.
   *
   * @param rctSigPrunable is the prunable signature data
   * @return this transaction for chaining
   */
  public ZephyrTx setRctSigPrunable(Object rctSigPrunable) {
    this.rctSigPrunable = rctSigPrunable;
    return this;
  }
  
  /**
   * Returns the node's flag that it keeps this pool transaction because it came with a block.
   * {@code ZephyrWalletFull} does not fill it (null). {@code ZephyrDaemonRpc} fills it from the
   * node; its transaction calls are inherited from monero-java; not tested against Zephyr.
   *
   * @return the flag, null if not known
   */
  @JsonProperty("isKeptByBlock")
  public Boolean isKeptByBlock() {
    return isKeptByBlock;
  }
  
  /**
   * Sets the node's kept-by-block flag.
   *
   * @param isKeptByBlock is the flag
   * @return this transaction for chaining
   */
  public ZephyrTx setIsKeptByBlock(Boolean isKeptByBlock) {
    this.isKeptByBlock = isKeptByBlock;
    return this;
  }
  
  /**
   * Returns whether the transaction failed: an outgoing transaction that the wallet's core records
   * as failed, which is then not in the pool. {@code ZephyrWalletFull} reports false for incoming
   * transactions and for transactions in a block.
   *
   * @return true if failed, null if unknown
   */
  @JsonProperty("isFailed")
  public Boolean isFailed() {
    return isFailed;
  }
  
  /**
   * Sets whether the transaction failed.
   *
   * @param isFailed is true if failed
   * @return this transaction for chaining
   */
  public ZephyrTx setIsFailed(Boolean isFailed) {
    this.isFailed = isFailed;
    return this;
  }
  
  /**
   * Returns the height of the block in which the node last failed to include this pool transaction.
   * {@code ZephyrWalletFull} does not fill it (null). {@code ZephyrDaemonRpc} fills it from the
   * node; its transaction calls are inherited from monero-java; not tested against Zephyr.
   *
   * @return the height, null if not known
   */
  public Long getLastFailedHeight() {
    return lastFailedHeight;
  }
  
  /**
   * Sets the height of the last failed inclusion.
   *
   * @param lastFailedHeight is the height
   * @return this transaction for chaining
   */
  public ZephyrTx setLastFailedHeight(Long lastFailedHeight) {
    this.lastFailedHeight = lastFailedHeight;
    return this;
  }
  
  /**
   * Returns the hash of the block in which the node last failed to include this pool transaction.
   * {@code ZephyrWalletFull} does not fill it (null). {@code ZephyrDaemonRpc} fills it from the
   * node; its transaction calls are inherited from monero-java; not tested against Zephyr.
   *
   * @return the block hash, null if not known
   */
  public String getLastFailedHash() {
    return lastFailedHash;
  }
  
  /**
   * Sets the hash of the block of the last failed inclusion.
   *
   * @param lastFailedHash is the block hash
   * @return this transaction for chaining
   */
  public ZephyrTx setLastFailedHash(String lastFailedHash) {
    this.lastFailedHash = lastFailedHash;
    return this;
  }
  
  /**
   * Returns the height of the highest block that the inputs of this pool transaction refer to.
   * {@code ZephyrWalletFull} does not fill it (null). {@code ZephyrDaemonRpc} fills it from the
   * node; its transaction calls are inherited from monero-java; not tested against Zephyr.
   *
   * @return the height, null if not known
   */
  public Long getMaxUsedBlockHeight() {
    return maxUsedBlockHeight;
  }
  
  /**
   * Sets the height of the highest block the inputs refer to.
   *
   * @param maxUsedBlockHeight is the height
   * @return this transaction for chaining
   */
  public ZephyrTx setMaxUsedBlockHeight(Long maxUsedBlockHeight) {
    this.maxUsedBlockHeight = maxUsedBlockHeight;
    return this;
  }
  
  /**
   * Returns the hash of the highest block that the inputs of this pool transaction refer to.
   * {@code ZephyrWalletFull} does not fill it (null). {@code ZephyrDaemonRpc} fills it from the
   * node; its transaction calls are inherited from monero-java; not tested against Zephyr.
   *
   * @return the block hash, null if not known
   */
  public String getMaxUsedBlockHash() {
    return maxUsedBlockHash;
  }
  
  /**
   * Sets the hash of the highest block the inputs refer to.
   *
   * @param maxUsedBlockHash is the block hash
   * @return this transaction for chaining
   */
  public ZephyrTx setMaxUsedBlockHash(String maxUsedBlockHash) {
    this.maxUsedBlockHash = maxUsedBlockHash;
    return this;
  }
  
  /**
   * Returns the signatures of the transaction as the node reports them. {@code ZephyrWalletFull}
   * does not fill it (null). {@code ZephyrDaemonRpc} fills it from the node; its transaction calls
   * are inherited from monero-java; not tested against Zephyr.
   *
   * @return the signatures, null if not known
   */
  public List<String> getSignatures() {
    return signatures;
  }
  
  /**
   * Sets the signatures of the transaction.
   *
   * @param signatures are the signatures
   * @return this transaction for chaining
   */
  public ZephyrTx setSignatures(List<String> signatures) {
    this.signatures = signatures;
    return this;
  }
  
  /**
   * Merges the information of the given transaction (the same transaction, seen another way) into
   * this one. Fields this one lacks are taken over; states that only move forward (confirmed,
   * relayed, failed, double spend seen, the number of confirmations) take the later value; other
   * fields that both carry must agree, or the merge throws. The given transaction may be modified
   * or referenced afterwards, so do not reuse it.
   *
   * @param tx is the transaction to merge into this one
   * @return this transaction for chaining
   */
  public ZephyrTx merge(ZephyrTx tx) {
    if (this == tx) return this;
    
    // merge blocks if they're different
    if (this.getBlock() != tx.getBlock()) {
      if (this.getBlock() == null) {
        this.setBlock(tx.getBlock());
        this.getBlock().getTxs().set(this.getBlock().getTxs().indexOf(tx), this); // update block to point to this tx
      } else if (tx.getBlock() != null) {
        this.getBlock().merge(tx.getBlock()); // comes back to merging txs
        return this;
      }
    }
    
    // otherwise merge tx fields
    this.setHash(GenUtils.reconcile(this.getHash(), tx.getHash()));
    this.setVersion(GenUtils.reconcile(this.getVersion(), tx.getVersion()));
    this.setPaymentId(GenUtils.reconcile(this.getPaymentId(), tx.getPaymentId()));
    this.setFee(GenUtils.reconcile(this.getFee(), tx.getFee()));
    this.setRingSize(GenUtils.reconcile(this.getRingSize(), tx.getRingSize()));
    this.setIsConfirmed(GenUtils.reconcile(this.isConfirmed(), tx.isConfirmed(), null, true, null));  // tx can become confirmed
    this.setIsMinerTx(GenUtils.reconcile(this.isMinerTx(), tx.isMinerTx(), null, null, null));
    this.setRelay(GenUtils.reconcile(this.getRelay(), tx.getRelay(), null, true, null));        // tx can become relayed
    this.setIsRelayed(GenUtils.reconcile(this.isRelayed(), tx.isRelayed(), null, true, null));  // tx can become relayed
    this.setIsDoubleSpendSeen(GenUtils.reconcile(this.isDoubleSpendSeen(), tx.isDoubleSpendSeen(), null, true, null)); // double spend can become seen
    this.setKey(GenUtils.reconcile(this.getKey(), tx.getKey()));
    this.setFullHex(GenUtils.reconcile(this.getFullHex(), tx.getFullHex()));
    this.setPrunedHex(GenUtils.reconcile(this.getPrunedHex(), tx.getPrunedHex()));
    this.setPrunableHex(GenUtils.reconcile(this.getPrunableHex(), tx.getPrunableHex()));
    this.setPrunableHash(GenUtils.reconcile(this.getPrunableHash(), tx.getPrunableHash()));
    this.setSize(GenUtils.reconcile(this.getSize(), tx.getSize()));
    this.setWeight(GenUtils.reconcile(this.getWeight(), tx.getWeight()));
    this.setOutputIndices(GenUtils.reconcile(this.getOutputIndices(), tx.getOutputIndices()));
    this.setMetadata(GenUtils.reconcile(this.getMetadata(), tx.getMetadata()));
    this.setExtra(GenUtils.reconcileIntArrays(this.getExtra(), tx.getExtra()));
    this.setRctSignatures(GenUtils.reconcile(this.getRctSignatures(), tx.getRctSignatures()));
    this.setRctSigPrunable(GenUtils.reconcile(this.getRctSigPrunable(), tx.getRctSigPrunable()));
    this.setIsKeptByBlock(GenUtils.reconcile(this.isKeptByBlock(), tx.isKeptByBlock()));
    this.setIsFailed(GenUtils.reconcile(this.isFailed(), tx.isFailed(), null, true, null));
    this.setLastFailedHeight(GenUtils.reconcile(this.getLastFailedHeight(), tx.getLastFailedHeight()));
    this.setLastFailedHash(GenUtils.reconcile(this.getLastFailedHash(), tx.getLastFailedHash()));
    this.setMaxUsedBlockHeight(GenUtils.reconcile(this.getMaxUsedBlockHeight(), tx.getMaxUsedBlockHeight()));
    this.setMaxUsedBlockHash(GenUtils.reconcile(this.getMaxUsedBlockHash(), tx.getMaxUsedBlockHash()));
    this.setSignatures(GenUtils.reconcile(this.getSignatures(), tx.getSignatures()));
    this.setUnlockHeight(GenUtils.reconcile(this.getUnlockHeight(), tx.getUnlockHeight()));
    this.setNumConfirmations(GenUtils.reconcile(this.getNumConfirmations(), tx.getNumConfirmations(), null, null, true)); // num confirmations can increase
    
    // merge inputs
    if (tx.getInputs() != null) {
      for (ZephyrOutput merger : tx.getInputs()) {
        boolean merged = false;
        merger.setTx(this);
        if (this.getInputs() == null) this.setInputs(new ArrayList<ZephyrOutput>());
        for (ZephyrOutput mergee : this.getInputs()) {
          if (mergee.getKeyImage().getHex().equals(merger.getKeyImage().getHex())) {
            mergee.merge(merger);
            merged = true;
            break;
          }
        }
        if (!merged) this.getInputs().add(merger);
      }
    }
    
    // merge outputs
    if (tx.getOutputs() != null) {
      for (ZephyrOutput output : tx.getOutputs()) output.setTx(this);
      if (this.getOutputs() == null) this.setOutputs(tx.getOutputs());
      else {
        
        // merge outputs if key image or stealth public key present, otherwise append
        for (ZephyrOutput merger : tx.getOutputs()) {
          boolean merged = false;
          merger.setTx(this);
          for (ZephyrOutput mergee : this.getOutputs()) {
            if ((merger.getKeyImage() != null && mergee.getKeyImage().getHex().equals(merger.getKeyImage().getHex())) ||
                (merger.getStealthPublicKey() != null && mergee.getStealthPublicKey().equals(merger.getStealthPublicKey()))) {
             mergee.merge(merger);
             merged = true;
             break;
            }
          }
          if (!merged) this.getOutputs().add(merger); // append output
        }
      }
    }
    
    // handle unrelayed -> relayed -> confirmed
    if (this.isConfirmed()) {
      this.setInTxPool(false);
      this.setReceivedTimestamp(null);
      this.setLastRelayedTimestamp(null);
    } else {
      this.setInTxPool(GenUtils.reconcile(this.inTxPool(), tx.inTxPool(), null, true, null)); // unrelayed -> tx pool
      this.setReceivedTimestamp(GenUtils.reconcile(this.getReceivedTimestamp(), tx.getReceivedTimestamp(), null, null, false)); // take earliest receive time
      this.setLastRelayedTimestamp(GenUtils.reconcile(this.getLastRelayedTimestamp(), tx.getLastRelayedTimestamp(), null, null, true));  // take latest relay time
    }
    
    return this;  // for chaining
  }
  
  @Override
  public String toString() {
    return toString(0);
  }
  
  /**
   * Returns the fields of this transaction that are set, one per line, indented.
   *
   * @param indent is the indentation in levels of two spaces
   * @return the description
   */
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(GenUtils.getIndent(indent) + "=== TX ===\n");
    sb.append(GenUtils.kvLine("Tx hash", getHash(), indent));
    sb.append(GenUtils.kvLine("Height", getHeight(), indent));
    sb.append(GenUtils.kvLine("Version", getVersion(), indent));
    sb.append(GenUtils.kvLine("Is miner tx", isMinerTx(), indent));
    sb.append(GenUtils.kvLine("Payment ID", getPaymentId(), indent));
    sb.append(GenUtils.kvLine("Fee", getFee(), indent));
    sb.append(GenUtils.kvLine("Ring size", getRingSize(), indent));
    sb.append(GenUtils.kvLine("Relay", getRelay(), indent));
    sb.append(GenUtils.kvLine("Is relayed", isRelayed(), indent));
    sb.append(GenUtils.kvLine("Is confirmed", isConfirmed(), indent));
    sb.append(GenUtils.kvLine("In tx pool", inTxPool(), indent));
    sb.append(GenUtils.kvLine("Num confirmations", getNumConfirmations(), indent));
    sb.append(GenUtils.kvLine("Unlock height", getUnlockHeight(), indent));
    sb.append(GenUtils.kvLine("Last relayed time", getLastRelayedTimestamp(), indent));
    sb.append(GenUtils.kvLine("Received time", getReceivedTimestamp(), indent));
    sb.append(GenUtils.kvLine("Is double spend", isDoubleSpendSeen(), indent));
    sb.append(GenUtils.kvLine("Key", getKey(), indent));
    sb.append(GenUtils.kvLine("Full hex", getFullHex(), indent));
    sb.append(GenUtils.kvLine("Pruned hex", getPrunedHex(), indent));
    sb.append(GenUtils.kvLine("Prunable hex", getPrunableHex(), indent));
    sb.append(GenUtils.kvLine("Prunable hash", getPrunableHash(), indent));
    sb.append(GenUtils.kvLine("Size", getSize(), indent));
    sb.append(GenUtils.kvLine("Weight", getWeight(), indent));
    sb.append(GenUtils.kvLine("Output indices", getOutputIndices(), indent));
    sb.append(GenUtils.kvLine("Metadata", getMetadata(), indent));
    sb.append(GenUtils.kvLine("Extra", Arrays.toString(getExtra()), indent));
    sb.append(GenUtils.kvLine("RCT signatures", getRctSignatures(), indent));
    sb.append(GenUtils.kvLine("RCT sig prunable", getRctSigPrunable(), indent));
    sb.append(GenUtils.kvLine("Kept by block", isKeptByBlock(), indent));
    sb.append(GenUtils.kvLine("Is failed", isFailed(), indent));
    sb.append(GenUtils.kvLine("Last failed height", getLastFailedHeight(), indent));
    sb.append(GenUtils.kvLine("Last failed hash", getLastFailedHash(), indent));
    sb.append(GenUtils.kvLine("Max used block height", getMaxUsedBlockHeight(), indent));
    sb.append(GenUtils.kvLine("Max used block hash", getMaxUsedBlockHash(), indent));
    sb.append(GenUtils.kvLine("Signatures", getSignatures(), indent));
    if (getInputs() != null) {
      sb.append(GenUtils.kvLine("Inputs", "", indent));
      for (int i = 0; i < getInputs().size(); i++) {
        sb.append(GenUtils.kvLine(i + 1, "", indent + 1));
        sb.append(getInputs().get(i).toString(indent + 2));
        sb.append('\n');
      }
    }
    if (getOutputs() != null) {
      sb.append(GenUtils.kvLine("Outputs", "", indent));
      for (int i = 0; i < getOutputs().size(); i++) {
        sb.append(GenUtils.kvLine(i + 1, "", indent + 1));
        sb.append(getOutputs().get(i).toString(indent + 2));
        sb.append('\n');
      }
    }
    String str = sb.toString();
    return str.substring(0, str.length() - 1);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((relay == null) ? 0 : relay.hashCode());
    result = prime * result + Arrays.hashCode(extra);
    result = prime * result + ((fee == null) ? 0 : fee.hashCode());
    result = prime * result + ((fullHex == null) ? 0 : fullHex.hashCode());
    result = prime * result + ((hash == null) ? 0 : hash.hashCode());
    result = prime * result + ((inTxPool == null) ? 0 : inTxPool.hashCode());
    result = prime * result + ((isMinerTx == null) ? 0 : isMinerTx.hashCode());
    result = prime * result + ((isConfirmed == null) ? 0 : isConfirmed.hashCode());
    result = prime * result + ((isDoubleSpendSeen == null) ? 0 : isDoubleSpendSeen.hashCode());
    result = prime * result + ((isFailed == null) ? 0 : isFailed.hashCode());
    result = prime * result + ((isKeptByBlock == null) ? 0 : isKeptByBlock.hashCode());
    result = prime * result + ((isRelayed == null) ? 0 : isRelayed.hashCode());
    result = prime * result + ((key == null) ? 0 : key.hashCode());
    result = prime * result + ((lastFailedHeight == null) ? 0 : lastFailedHeight.hashCode());
    result = prime * result + ((lastFailedHash == null) ? 0 : lastFailedHash.hashCode());
    result = prime * result + ((lastRelayedTimestamp == null) ? 0 : lastRelayedTimestamp.hashCode());
    result = prime * result + ((maxUsedBlockHeight == null) ? 0 : maxUsedBlockHeight.hashCode());
    result = prime * result + ((maxUsedBlockHash == null) ? 0 : maxUsedBlockHash.hashCode());
    result = prime * result + ((metadata == null) ? 0 : metadata.hashCode());
    result = prime * result + ((ringSize == null) ? 0 : ringSize.hashCode());
    result = prime * result + ((numConfirmations == null) ? 0 : numConfirmations.hashCode());
    result = prime * result + ((outputIndices == null) ? 0 : outputIndices.hashCode());
    result = prime * result + ((paymentId == null) ? 0 : paymentId.hashCode());
    result = prime * result + ((prunableHash == null) ? 0 : prunableHash.hashCode());
    result = prime * result + ((prunableHex == null) ? 0 : prunableHex.hashCode());
    result = prime * result + ((prunedHex == null) ? 0 : prunedHex.hashCode());
    result = prime * result + ((rctSigPrunable == null) ? 0 : rctSigPrunable.hashCode());
    result = prime * result + ((rctSignatures == null) ? 0 : rctSignatures.hashCode());
    result = prime * result + ((receivedTimestamp == null) ? 0 : receivedTimestamp.hashCode());
    result = prime * result + ((signatures == null) ? 0 : signatures.hashCode());
    result = prime * result + ((size == null) ? 0 : size.hashCode());
    result = prime * result + ((unlockHeight == null) ? 0 : unlockHeight.hashCode());
    result = prime * result + ((version == null) ? 0 : version.hashCode());
    result = prime * result + ((inputs == null) ? 0 : inputs.hashCode());
    result = prime * result + ((outputs == null) ? 0 : outputs.hashCode());
    result = prime * result + ((weight == null) ? 0 : weight.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrTx other = (ZephyrTx) obj;
    if (relay == null) {
      if (other.relay != null) return false;
    } else if (!relay.equals(other.relay)) return false;
    if (!Arrays.equals(extra, other.extra)) return false;
    if (fee == null) {
      if (other.fee != null) return false;
    } else if (!fee.equals(other.fee)) return false;
    if (fullHex == null) {
      if (other.fullHex != null) return false;
    } else if (!fullHex.equals(other.fullHex)) return false;
    if (hash == null) {
      if (other.hash != null) return false;
    } else if (!hash.equals(other.hash)) return false;
    if (inTxPool == null) {
      if (other.inTxPool != null) return false;
    } else if (!inTxPool.equals(other.inTxPool)) return false;
    if (isMinerTx == null) {
      if (other.isMinerTx != null) return false;
    } else if (!isMinerTx.equals(other.isMinerTx)) return false;
    if (isConfirmed == null) {
      if (other.isConfirmed != null) return false;
    } else if (!isConfirmed.equals(other.isConfirmed)) return false;
    if (isDoubleSpendSeen == null) {
      if (other.isDoubleSpendSeen != null) return false;
    } else if (!isDoubleSpendSeen.equals(other.isDoubleSpendSeen)) return false;
    if (isFailed == null) {
      if (other.isFailed != null) return false;
    } else if (!isFailed.equals(other.isFailed)) return false;
    if (isKeptByBlock == null) {
      if (other.isKeptByBlock != null) return false;
    } else if (!isKeptByBlock.equals(other.isKeptByBlock)) return false;
    if (isRelayed == null) {
      if (other.isRelayed != null) return false;
    } else if (!isRelayed.equals(other.isRelayed)) return false;
    if (key == null) {
      if (other.key != null) return false;
    } else if (!key.equals(other.key)) return false;
    if (lastFailedHeight == null) {
      if (other.lastFailedHeight != null) return false;
    } else if (!lastFailedHeight.equals(other.lastFailedHeight)) return false;
    if (lastFailedHash == null) {
      if (other.lastFailedHash != null) return false;
    } else if (!lastFailedHash.equals(other.lastFailedHash)) return false;
    if (lastRelayedTimestamp == null) {
      if (other.lastRelayedTimestamp != null) return false;
    } else if (!lastRelayedTimestamp.equals(other.lastRelayedTimestamp)) return false;
    if (maxUsedBlockHeight == null) {
      if (other.maxUsedBlockHeight != null) return false;
    } else if (!maxUsedBlockHeight.equals(other.maxUsedBlockHeight)) return false;
    if (maxUsedBlockHash == null) {
      if (other.maxUsedBlockHash != null) return false;
    } else if (!maxUsedBlockHash.equals(other.maxUsedBlockHash)) return false;
    if (metadata == null) {
      if (other.metadata != null) return false;
    } else if (!metadata.equals(other.metadata)) return false;
    if (ringSize == null) {
      if (other.ringSize != null) return false;
    } else if (!ringSize.equals(other.ringSize)) return false;
    if (numConfirmations == null) {
      if (other.numConfirmations != null) return false;
    } else if (!numConfirmations.equals(other.numConfirmations)) return false;
    if (outputIndices == null) {
      if (other.outputIndices != null) return false;
    } else if (!outputIndices.equals(other.outputIndices)) return false;
    if (paymentId == null) {
      if (other.paymentId != null) return false;
    } else if (!paymentId.equals(other.paymentId)) return false;
    if (prunableHash == null) {
      if (other.prunableHash != null) return false;
    } else if (!prunableHash.equals(other.prunableHash)) return false;
    if (prunableHex == null) {
      if (other.prunableHex != null) return false;
    } else if (!prunableHex.equals(other.prunableHex)) return false;
    if (prunedHex == null) {
      if (other.prunedHex != null) return false;
    } else if (!prunedHex.equals(other.prunedHex)) return false;
    if (rctSigPrunable == null) {
      if (other.rctSigPrunable != null) return false;
    } else if (!rctSigPrunable.equals(other.rctSigPrunable)) return false;
    if (rctSignatures == null) {
      if (other.rctSignatures != null) return false;
    } else if (!rctSignatures.equals(other.rctSignatures)) return false;
    if (receivedTimestamp == null) {
      if (other.receivedTimestamp != null) return false;
    } else if (!receivedTimestamp.equals(other.receivedTimestamp)) return false;
    if (signatures == null) {
      if (other.signatures != null) return false;
    } else if (!signatures.equals(other.signatures)) return false;
    if (size == null) {
      if (other.size != null) return false;
    } else if (!size.equals(other.size)) return false;
    if (unlockHeight == null) {
      if (other.unlockHeight != null) return false;
    } else if (!unlockHeight.equals(other.unlockHeight)) return false;
    if (version == null) {
      if (other.version != null) return false;
    } else if (!version.equals(other.version)) return false;
    if (inputs == null) {
      if (other.inputs != null) return false;
    } else if (!inputs.equals(other.inputs)) return false;
    if (outputs == null) {
      if (other.outputs != null) return false;
    } else if (!outputs.equals(other.outputs)) return false;
    if (weight == null) {
      if (other.weight != null) return false;
    } else if (!weight.equals(other.weight)) return false;
    return true;
  }
}
