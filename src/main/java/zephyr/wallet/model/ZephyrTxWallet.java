package zephyr.wallet.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.utils.GenUtils;
import zephyr.common.ZephyrError;
import zephyr.daemon.model.ZephyrBlock;
import zephyr.daemon.model.ZephyrOutput;
import zephyr.daemon.model.ZephyrTx;

/**
 * Models a Zephyr transaction with wallet extensions.
 */
public class ZephyrTxWallet extends ZephyrTx {

  private ZephyrTxSet txSet;
  private Boolean isIncoming;
  private Boolean isOutgoing;
  private List<ZephyrIncomingTransfer> incomingTransfers;
  private ZephyrOutgoingTransfer outgoingTransfer;
  private String note;
  private Boolean isLocked;
  private BigInteger inputSum;
  private BigInteger outputSum;
  private String changeAddress;
  private BigInteger changeAmount;
  private Integer numDummyOutputs;
  private String extraHex;  // TODO: refactor ZephyrTx to only use extra as hex string
  
  public ZephyrTxWallet() {
    // nothing to initialize
  }
  
  public ZephyrTxWallet(final ZephyrTxWallet tx) {
    super(tx);
    this.txSet = tx.txSet;
    this.isIncoming = tx.isIncoming;
    this.isOutgoing = tx.isOutgoing;
    if (tx.incomingTransfers != null) {
      this.incomingTransfers = new ArrayList<ZephyrIncomingTransfer>();
      for (ZephyrIncomingTransfer transfer : tx.incomingTransfers) {
        this.incomingTransfers.add(transfer.copy().setTx(this));
      }
    }
    if (tx.outgoingTransfer != null) this.outgoingTransfer = tx.outgoingTransfer.copy().setTx(this);
    this.note = tx.note;
    this.isLocked = tx.isLocked;
    this.inputSum = tx.inputSum;
    this.outputSum = tx.outputSum;
    this.changeAddress = tx.changeAddress;
    this.changeAmount = tx.changeAmount;
    this.numDummyOutputs = tx.numDummyOutputs;
    this.extraHex = tx.extraHex;
  }
  
  @Override
  public ZephyrTxWallet copy() {
    return new ZephyrTxWallet(this);
  }
  
  @JsonBackReference("tx_set")
  public ZephyrTxSet getTxSet() {
    return txSet;
  }
  
  public ZephyrTxWallet setTxSet(ZephyrTxSet txSet) {
    this.txSet = txSet;
    return this;
  }
  
  @JsonProperty("isIncoming")
  public Boolean isIncoming() {
    return isIncoming;
  }
  
  public ZephyrTxWallet setIsIncoming(Boolean isIncoming) {
    this.isIncoming = isIncoming;
    return this;
  }
  
  @JsonProperty("isOutgoing")
  public Boolean isOutgoing() {
    return isOutgoing;
  }
  
  public ZephyrTxWallet setIsOutgoing(Boolean isOutgoing) {
    this.isOutgoing = isOutgoing;
    return this;
  }
  
  public BigInteger getIncomingAmount() {
    if (getIncomingTransfers() == null) return null;
    BigInteger incomingAmt = BigInteger.valueOf(0);
    for (ZephyrTransfer transfer : this.getIncomingTransfers()) incomingAmt = incomingAmt.add(transfer.getAmount());
    return incomingAmt;
  }
  
  public BigInteger getOutgoingAmount() {
    return getOutgoingTransfer() != null ? getOutgoingTransfer().getAmount() : null;
  }
  
  @JsonIgnore
  public List<ZephyrTransfer> getTransfers() {
    return getTransfers(null);
  }
  
  public List<ZephyrTransfer> getTransfers(ZephyrTransferQuery query) {
    List<ZephyrTransfer> transfers = new ArrayList<ZephyrTransfer>();
    if (getOutgoingTransfer() != null && (query == null || query.meetsCriteria(getOutgoingTransfer()))) transfers.add(getOutgoingTransfer());
    if (getIncomingTransfers() != null) {
      for (ZephyrTransfer transfer : getIncomingTransfers()) {
        if (query == null || query.meetsCriteria(transfer)) transfers.add(transfer);
      }
    }
    return transfers;
  }
  
  public List<ZephyrTransfer> filterTransfers(ZephyrTransferQuery query) {
    List<ZephyrTransfer> transfers = new ArrayList<ZephyrTransfer>();
    
    // collect outgoing transfer or erase if filtered
    if (getOutgoingTransfer() != null && (query == null || query.meetsCriteria(getOutgoingTransfer()))) transfers.add(getOutgoingTransfer());
    else setOutgoingTransfer(null);
    
    // collect incoming transfers or erase if filtered
    if (getIncomingTransfers() != null) {
      List<ZephyrTransfer> toRemoves = new ArrayList<ZephyrTransfer>();
      for (ZephyrTransfer transfer : getIncomingTransfers()) {
        if (query == null || query.meetsCriteria(transfer)) transfers.add(transfer);
        else toRemoves.add(transfer);
      }
      getIncomingTransfers().removeAll(toRemoves);
      if (getIncomingTransfers().isEmpty()) setIncomingTransfers(null);
    }
    
    return transfers;
  }
  
  @JsonManagedReference
  public List<ZephyrIncomingTransfer> getIncomingTransfers() {
    return incomingTransfers;
  }
  
  public ZephyrTxWallet setIncomingTransfers(List<ZephyrIncomingTransfer> incomingTransfers) {
    this.incomingTransfers = incomingTransfers;
    return this;
  }
  
  @JsonManagedReference
  public ZephyrOutgoingTransfer getOutgoingTransfer() {
    return outgoingTransfer;
  }
  
  public ZephyrTxWallet setOutgoingTransfer(ZephyrOutgoingTransfer outgoingTransfer) {
    this.outgoingTransfer = outgoingTransfer;
    return this;
  }
  
  /**
   * Set the tx's inputs (ZephyrOutputWallet) which contain information relative
   * to a wallet.
   * 
   * Callers must cast to extended type (ZephyrOutput) because Java
   * paramaterized types do not recognize inheritance.
   * 
   * @param inputs are ZephyrOutputWallets to set for the wallet tx
   * @return ZephyrTxWallet is a reference to this tx for chaining
   */
  @Override
  public ZephyrTxWallet setInputs(List<ZephyrOutput> inputs) {
    
    // validate that all inputs are wallet inputs
    if (inputs != null) {
      for (ZephyrOutput input : inputs) {
        if (!(input instanceof ZephyrOutputWallet)) throw new ZephyrError("Wallet transaction inputs must be of type ZephyrOutputWallet");
      }
    }
    super.setInputs(inputs);
    return this;
  }
  
  /**
   * Set inputs with compile-time binding to ZephyrOutputWallet for deserialization.
   * 
   * @param inputs are the tx's inputs
   * @return ZephyrTxWallet is a reference to this tx for chaining
   */
  @JsonProperty("inputs")
  public ZephyrTxWallet setInputsWallet(List<ZephyrOutputWallet> inputs) {
    return setInputs(new ArrayList<ZephyrOutput>(inputs));
  }
  
  /**
   * Returns a copy of this model's inputs as a list of type ZephyrOutputWallet.
   * 
   * @return inputs of type ZephyrOutputWallet
   */
  public List<ZephyrOutputWallet> getInputsWallet() {
    return getInputsWallet(null);
  }
  
  public List<ZephyrOutputWallet> getInputsWallet(ZephyrOutputQuery query) {
    List<ZephyrOutputWallet> inputsWallet = new ArrayList<ZephyrOutputWallet>();
    List<ZephyrOutput> inputs = getInputs();
    if (inputs == null) return inputsWallet;
    for (ZephyrOutput output : inputs) {
      if (query == null || query.meetsCriteria((ZephyrOutputWallet) output)) inputsWallet.add((ZephyrOutputWallet) output);
    }
    return inputsWallet;
  }
  
  /**
   * Set the tx's outputs (ZephyrOutputWallet) which contain information relative
   * to a wallet.
   * 
   * Callers must cast to extended type (ZephyrOutput) because Java
   * paramaterized types do not recognize inheritance.
   * 
   * @param outputs are ZephyrOutputWallets to set for the wallet tx
   * @return ZephyrTxWallet is a reference to this tx for chaining
   */
  @Override
  public ZephyrTxWallet setOutputs(List<ZephyrOutput> outputs) {
    
    // validate that all outputs are wallet outputs
    if (outputs != null) {
      for (ZephyrOutput output : outputs) {
        if (!(output instanceof ZephyrOutputWallet)) throw new ZephyrError("Wallet transaction outputs must be of type ZephyrOutputWallet");
      }
    }
    super.setOutputs(outputs);
    return this;
  }
  
  /**
   * Set outputs with compile-time binding to ZephyrOutputWallet for deserialization.
   * 
   * @param outputs are the tx's outputs
   * @return ZephyrTxWallet is a reference to this tx for chaining
   */
  @JsonProperty("outputs")
  public ZephyrTxWallet setOutputsWallet(List<ZephyrOutputWallet> outputs) {
    return setOutputs(new ArrayList<ZephyrOutput>(outputs));
  }
  
  /**
   * Returns a copy of this model's outputs as a list of type ZephyrOutputWallet.
   * 
   * @return outputs of type ZephyrOutputWallet
   */
  public List<ZephyrOutputWallet> getOutputsWallet() {
    return getOutputsWallet(null);
  }
  
  public List<ZephyrOutputWallet> getOutputsWallet(ZephyrOutputQuery query) {
    List<ZephyrOutputWallet> outputsWallet = new ArrayList<ZephyrOutputWallet>();
    List<ZephyrOutput> outputs = getOutputs();
    if (outputs == null) return outputsWallet;
    for (ZephyrOutput output : outputs) {
      if (query == null || query.meetsCriteria((ZephyrOutputWallet) output)) outputsWallet.add((ZephyrOutputWallet) output);
    }
    return outputsWallet;
  }
  
  public List<ZephyrOutputWallet> filterOutputsWallet(ZephyrOutputQuery query) {
    List<ZephyrOutputWallet> outputs = new ArrayList<ZephyrOutputWallet>();
    if (getOutputs() != null) {
      List<ZephyrOutput> toRemoves = new ArrayList<ZephyrOutput>();
      for (ZephyrOutput output : getOutputs()) {
        if (query == null || query.meetsCriteria((ZephyrOutputWallet) output)) outputs.add((ZephyrOutputWallet) output);
        else toRemoves.add(output);
      }
      getOutputs().removeAll(toRemoves);
      if (getOutputs().isEmpty()) setOutputs(null);
    }
    return outputs;
  }
  
  public String getNote() {
    return note;
  }
  
  public ZephyrTxWallet setNote(String note) {
    this.note = note;
    return this;
  }
  
  @JsonProperty("isLocked")
  public Boolean isLocked() {
    return isLocked;
  }
  
  public ZephyrTxWallet setIsLocked(Boolean isLocked) {
    this.isLocked = isLocked;
    return this;
  }

  public BigInteger getInputSum() {
    return inputSum;
  }

  public ZephyrTxWallet setInputSum(BigInteger inputSum) {
    this.inputSum = inputSum;
    return this;
  }

  public BigInteger getOutputSum() {
    return outputSum;
  }

  public ZephyrTxWallet setOutputSum(BigInteger outputSum) {
    this.outputSum = outputSum;
    return this;
  }

  public String getChangeAddress() {
    return changeAddress;
  }

  public ZephyrTxWallet setChangeAddress(String changeAddress) {
    this.changeAddress = changeAddress;
    return this;
  }

  public BigInteger getChangeAmount() {
    return changeAmount;
  }

  public ZephyrTxWallet setChangeAmount(BigInteger changeAmount) {
    this.changeAmount = changeAmount;
    return this;
  }

  public Integer getNumDummyOutputs() {
    return numDummyOutputs;
  }

  public ZephyrTxWallet setNumDummyOutputs(Integer numDummyOutputs) {
    this.numDummyOutputs = numDummyOutputs;
    return this;
  }

  public String getExtraHex() {
    return extraHex;
  }

  public ZephyrTxWallet setExtraHex(String extraHex) {
    this.extraHex = extraHex;
    return this;
  }
  
  @Override
  public ZephyrTxWallet merge(ZephyrTx tx) {
    if (tx != null && !(tx instanceof ZephyrTxWallet)) throw new ZephyrError("Wallet transaction must be merged with type ZephyrTxWallet");
    return merge((ZephyrTxWallet) tx);
  }
  
  /**
   * Updates this transaction by merging the latest information from the given
   * transaction.
   * 
   * Merging can modify or build references to the transaction given so it
   * should not be re-used or it should be copied before calling this method.
   * 
   * @param tx is the transaction to merge into this transaction
   * @return this tx for chaining
   */
  public ZephyrTxWallet merge(ZephyrTxWallet tx) {
    if (!(tx instanceof ZephyrTxWallet)) throw new ZephyrError("Wallet transaction must be merged with type ZephyrTxWallet");
    if (this == tx) return this;
    
    // merge base classes
    super.merge(tx);
    
    // merge tx set if they're different which comes back to merging txs
    if (txSet != tx.getTxSet()) {
      if (txSet == null) {
        txSet = new ZephyrTxSet();
        txSet.setTxs(this);
      }
      if (tx.getTxSet() == null) {
        tx.setTxSet(new ZephyrTxSet());
        tx.getTxSet().setTxs(tx);
      }
      txSet.merge(tx.getTxSet());
      return this;
    }
    
    // merge incoming transfers
    if (tx.getIncomingTransfers() != null) {
      if (this.getIncomingTransfers() == null) this.setIncomingTransfers(new ArrayList<ZephyrIncomingTransfer>());
      for (ZephyrIncomingTransfer transfer : tx.getIncomingTransfers()) {
        transfer.setTx(this);
        mergeIncomingTransfer(this.getIncomingTransfers(), transfer);
      }
    }
    
    // merge outgoing transfer
    if (tx.getOutgoingTransfer() != null) {
      tx.getOutgoingTransfer().setTx(this);
      if (this.getOutgoingTransfer() == null) this.setOutgoingTransfer(tx.getOutgoingTransfer());
      else this.getOutgoingTransfer().merge(tx.getOutgoingTransfer());
    }
    
    // merge simple extensions
    this.setIsIncoming(GenUtils.reconcile(this.isIncoming(), tx.isIncoming()));
    this.setIsOutgoing(GenUtils.reconcile(this.isOutgoing(), tx.isOutgoing()));
    this.setNote(GenUtils.reconcile(this.getNote(), tx.getNote()));
    this.setIsLocked(GenUtils.reconcile(this.isLocked(), tx.isLocked(), null, false, null));  // tx can become unlocked
    this.setInputSum(GenUtils.reconcile(this.getInputSum(), tx.getInputSum()));
    this.setOutputSum(GenUtils.reconcile(this.getOutputSum(), tx.getOutputSum()));
    this.setChangeAddress(GenUtils.reconcile(this.getChangeAddress(), tx.getChangeAddress()));
    this.setChangeAmount(GenUtils.reconcile(this.getChangeAmount(), tx.getChangeAmount()));
    this.setNumDummyOutputs(GenUtils.reconcile(this.getNumDummyOutputs(), tx.getNumDummyOutputs()));
    this.setExtraHex(GenUtils.reconcile(this.getExtraHex(), tx.getExtraHex()));
    
    return this;  // for chaining
  }
  
  @Override
  public String toString() {
    return toString(0, false);
  }
  
  @Override
  public String toString(int indent) {
    return toString(indent, false);
  }
  
  public String toString(int indent, boolean oneLine) {
    StringBuilder sb = new StringBuilder();
    
    // represent tx with one line string
    // TODO: proper csv export
    if (oneLine) {
      sb.append(this.getHash() + ", ");
      sb.append((this.isConfirmed() ? this.getBlock().getTimestamp() : this.getReceivedTimestamp()) + ", ");
      sb.append(this.isConfirmed() + ", ");
      sb.append((this.getOutgoingAmount() != null? this.getOutgoingAmount().toString() : "") + ", ");
      sb.append(this.getIncomingAmount() != null ? this.getIncomingAmount().toString() : "");
      return sb.toString();
    }
    
    // otherwise stringify all fields
    sb.append(super.toString(indent) + "\n");
    sb.append(GenUtils.kvLine("Is incoming", this.isIncoming(), indent));
    sb.append(GenUtils.kvLine("Incoming amount", this.getIncomingAmount(), indent));
    if (this.getIncomingTransfers() != null) {
      sb.append(GenUtils.kvLine("Incoming transfers", "", indent));
      for (int i = 0; i < this.getIncomingTransfers().size(); i++) {
        sb.append(GenUtils.kvLine(i + 1, "", indent + 1));
        sb.append(this.getIncomingTransfers().get(i).toString(indent + 2) + "\n");
      }
    }
    sb.append(GenUtils.kvLine("Is outgoing", this.isOutgoing(), indent));
    sb.append(GenUtils.kvLine("Outgoing amount", this.getOutgoingAmount(), indent));
    if (this.getOutgoingTransfer() != null) {
      sb.append(GenUtils.kvLine("Outgoing transfer", "", indent));
      sb.append(this.getOutgoingTransfer().toString(indent + 1) + "\n");
    }
    sb.append(GenUtils.kvLine("Note", this.getNote(), indent));
    sb.append(GenUtils.kvLine("Is locked", this.isLocked(), indent));
    sb.append(GenUtils.kvLine("Input sum", this.getInputSum(), indent));
    sb.append(GenUtils.kvLine("Output sum", this.getOutputSum(), indent));
    sb.append(GenUtils.kvLine("Change address", this.getChangeAddress(), indent));
    sb.append(GenUtils.kvLine("Change amount", this.getChangeAmount(), indent));
    sb.append(GenUtils.kvLine("Num dummy outputs", this.getNumDummyOutputs(), indent));
    sb.append(GenUtils.kvLine("Extra hex", this.getExtraHex(), indent));
    String str = sb.toString();
    return str.substring(0, str.length() - 1);  // strip last newline
  }
  
  // private helper to merge transfers
  private static void mergeIncomingTransfer(List<ZephyrIncomingTransfer> transfers, ZephyrIncomingTransfer transfer) {
    for (ZephyrIncomingTransfer aTransfer : transfers) {
      if (aTransfer.getAccountIndex() == transfer.getAccountIndex() && aTransfer.getSubaddressIndex() == transfer.getSubaddressIndex()) {
        aTransfer.merge(transfer);
        return;
      }
    }
    transfers.add(transfer);
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((changeAddress == null) ? 0 : changeAddress.hashCode());
    result = prime * result + ((changeAmount == null) ? 0 : changeAmount.hashCode());
    result = prime * result + ((extraHex == null) ? 0 : extraHex.hashCode());
    result = prime * result + ((incomingTransfers == null) ? 0 : incomingTransfers.hashCode());
    result = prime * result + ((inputSum == null) ? 0 : inputSum.hashCode());
    result = prime * result + ((isIncoming == null) ? 0 : isIncoming.hashCode());
    result = prime * result + ((isOutgoing == null) ? 0 : isOutgoing.hashCode());
    result = prime * result + ((isLocked == null) ? 0 : isLocked.hashCode());
    result = prime * result + ((note == null) ? 0 : note.hashCode());
    result = prime * result + ((numDummyOutputs == null) ? 0 : numDummyOutputs.hashCode());
    result = prime * result + ((outgoingTransfer == null) ? 0 : outgoingTransfer.hashCode());
    result = prime * result + ((outputSum == null) ? 0 : outputSum.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrTxWallet other = (ZephyrTxWallet) obj;
    if (changeAddress == null) {
      if (other.changeAddress != null) return false;
    } else if (!changeAddress.equals(other.changeAddress)) return false;
    if (changeAmount == null) {
      if (other.changeAmount != null) return false;
    } else if (!changeAmount.equals(other.changeAmount)) return false;
    if (extraHex == null) {
      if (other.extraHex != null) return false;
    } else if (!extraHex.equals(other.extraHex)) return false;
    if (incomingTransfers == null) {
      if (other.incomingTransfers != null) return false;
    } else if (!incomingTransfers.equals(other.incomingTransfers)) return false;
    if (inputSum == null) {
      if (other.inputSum != null) return false;
    } else if (!inputSum.equals(other.inputSum)) return false;
    if (isIncoming == null) {
      if (other.isIncoming != null) return false;
    } else if (!isIncoming.equals(other.isIncoming)) return false;
    if (isOutgoing == null) {
      if (other.isOutgoing != null) return false;
    } else if (!isOutgoing.equals(other.isOutgoing)) return false;
    if (isLocked == null) {
      if (other.isLocked != null) return false;
    } else if (!isLocked.equals(other.isLocked)) return false;
    if (note == null) {
      if (other.note != null) return false;
    } else if (!note.equals(other.note)) return false;
    if (numDummyOutputs == null) {
      if (other.numDummyOutputs != null) return false;
    } else if (!numDummyOutputs.equals(other.numDummyOutputs)) return false;
    if (outgoingTransfer == null) {
      if (other.outgoingTransfer != null) return false;
    } else if (!outgoingTransfer.equals(other.outgoingTransfer)) return false;
    if (outputSum == null) {
      if (other.outputSum != null) return false;
    } else if (!outputSum.equals(other.outputSum)) return false;
    return true;
  }
  
  // ------------------- OVERRIDE CO-VARIANT RETURN TYPES ---------------------

  @Override
  public ZephyrTxWallet setBlock(ZephyrBlock block) {
    super.setBlock(block);
    return this;
  }

  @Override
  public ZephyrTxWallet setHash(String hash) {
    super.setHash(hash);
    return this;
  }

  @Override
  public ZephyrTxWallet setVersion(Integer version) {
    super.setVersion(version);
    return this;
  }

  @Override
  public ZephyrTxWallet setIsMinerTx(Boolean isMinerTx) {
    super.setIsMinerTx(isMinerTx);
    return this;
  }

  @Override
  public ZephyrTxWallet setPaymentId(String paymentId) {
    super.setPaymentId(paymentId);
    return this;
  }

  @Override
  public ZephyrTxWallet setFee(BigInteger fee) {
    super.setFee(fee);
    return this;
  }

  @Override
  public ZephyrTxWallet setRingSize(Integer ringSize) {
    super.setRingSize(ringSize);
    return this;
  }

  @Override
  public ZephyrTxWallet setRelay(Boolean relay) {
    super.setRelay(relay);
    return this;
  }

  @Override
  public ZephyrTxWallet setIsRelayed(Boolean isRelayed) {
    super.setIsRelayed(isRelayed);
    return this;
  }

  @Override
  public ZephyrTxWallet setIsConfirmed(Boolean isConfirmed) {
    super.setIsConfirmed(isConfirmed);
    return this;
  }

  @Override
  public ZephyrTxWallet setInTxPool(Boolean inTxPool) {
    super.setInTxPool(inTxPool);
    return this;
  }

  @Override
  public ZephyrTxWallet setNumConfirmations(Long numConfirmations) {
    super.setNumConfirmations(numConfirmations);
    return this;
  }

  @Override
  public ZephyrTxWallet setUnlockHeight(Long unlockHeight) {
    super.setUnlockHeight(unlockHeight);
    return this;
  }

  @Override
  public ZephyrTxWallet setLastRelayedTimestamp(Long lastRelayedTimestamp) {
    super.setLastRelayedTimestamp(lastRelayedTimestamp);
    return this;
  }

  @Override
  public ZephyrTxWallet setReceivedTimestamp(Long receivedTimestamp) {
    super.setReceivedTimestamp(receivedTimestamp);
    return this;
  }

  @Override
  public ZephyrTxWallet setIsDoubleSpendSeen(Boolean isDoubleSpend) {
    super.setIsDoubleSpendSeen(isDoubleSpend);
    return this;
  }

  @Override
  public ZephyrTxWallet setKey(String key) {
    super.setKey(key);
    return this;
  }

  @Override
  public ZephyrTxWallet setFullHex(String hex) {
    super.setFullHex(hex);
    return this;
  }

  @Override
  public ZephyrTxWallet setPrunedHex(String prunedHex) {
    super.setPrunedHex(prunedHex);
    return this;
  }

  @Override
  public ZephyrTxWallet setPrunableHex(String prunableHex) {
    super.setPrunableHex(prunableHex);
    return this;
  }

  @Override
  public ZephyrTxWallet setPrunableHash(String prunableHash) {
    super.setPrunableHash(prunableHash);
    return this;
  }

  @Override
  public ZephyrTxWallet setSize(Long size) {
    super.setSize(size);
    return this;
  }

  @Override
  public ZephyrTxWallet setWeight(Long weight) {
    super.setWeight(weight);
    return this;
  }

  @Override
  public ZephyrTxWallet setOutputIndices(List<Long> outputIndices) {
    super.setOutputIndices(outputIndices);
    return this;
  }

  @Override
  public ZephyrTxWallet setMetadata(String metadata) {
    super.setMetadata(metadata);
    return this;
  }

  @Override
  public ZephyrTxWallet setExtra(int[] extra) {
    super.setExtra(extra);
    return this;
  }

  @Override
  public ZephyrTxWallet setRctSignatures(Object rctSignatures) {
    super.setRctSignatures(rctSignatures);
    return this;
  }

  @Override
  public ZephyrTxWallet setRctSigPrunable(Object rctSigPrunable) {
    super.setRctSigPrunable(rctSigPrunable);
    return this;
  }

  @Override
  public ZephyrTxWallet setIsKeptByBlock(Boolean isKeptByBlock) {
    super.setIsKeptByBlock(isKeptByBlock);
    return this;
  }

  @Override
  public ZephyrTxWallet setIsFailed(Boolean isFailed) {
    super.setIsFailed(isFailed);
    return this;
  }

  @Override
  public ZephyrTxWallet setLastFailedHeight(Long lastFailedHeight) {
    super.setLastFailedHeight(lastFailedHeight);
    return this;
  }

  @Override
  public ZephyrTxWallet setLastFailedHash(String lastFailedId) {
    super.setLastFailedHash(lastFailedId);
    return this;
  }

  @Override
  public ZephyrTxWallet setMaxUsedBlockHeight(Long maxUsedBlockHeight) {
    super.setMaxUsedBlockHeight(maxUsedBlockHeight);
    return this;
  }

  @Override
  public ZephyrTxWallet setMaxUsedBlockHash(String maxUsedBlockId) {
    super.setMaxUsedBlockHash(maxUsedBlockId);
    return this;
  }

  @Override
  public ZephyrTxWallet setSignatures(List<String> signatures) {
    super.setSignatures(signatures);
    return this;
  }
}
