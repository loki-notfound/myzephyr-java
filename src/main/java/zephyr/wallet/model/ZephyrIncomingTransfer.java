package zephyr.wallet.model;

import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.utils.GenUtils;

/**
 * Models an incoming transfer of funds to the wallet.
 */
public class ZephyrIncomingTransfer extends ZephyrTransfer {

  private Integer subaddressIndex;
  private String address;
  private Long numSuggestedConfirmations;
  
  public ZephyrIncomingTransfer() {
    // nothing to initialize
  }
  
  public ZephyrIncomingTransfer(final ZephyrIncomingTransfer transfer) {
    super(transfer);
    this.subaddressIndex = transfer.subaddressIndex;
    this.address = transfer.address;
    this.numSuggestedConfirmations = transfer.numSuggestedConfirmations;
  }
  
  @Override
  public ZephyrIncomingTransfer copy() {
    return new ZephyrIncomingTransfer(this);
  }
  
  @JsonProperty("isIncoming")
  public Boolean isIncoming() {
    return true;
  }
  
  public Integer getSubaddressIndex() {
    return subaddressIndex;
  }
  
  public ZephyrIncomingTransfer setSubaddressIndex(Integer subaddressIndex) {
    this.subaddressIndex = subaddressIndex;
    return this;
  }
  
  public String getAddress() {
    return address;
  }

  public ZephyrIncomingTransfer setAddress(String address) {
    this.address = address;
    return this;
  }
  
  /**
   * Return how many confirmations till it's not economically worth re-writing the chain.
   * That is, the number of confirmations before the transaction is highly unlikely to be
   * double spent or overwritten and may be considered settled, e.g. for a merchant to trust
   * as finalized.
   * 
   * @return the number of confirmations before it's not worth rewriting the chain
   */
  public Long getNumSuggestedConfirmations() {
    return numSuggestedConfirmations;
  }
  
  public ZephyrIncomingTransfer setNumSuggestedConfirmations(Long numSuggestedConfirmations) {
    this.numSuggestedConfirmations = numSuggestedConfirmations;
    return this;
  }

  public ZephyrIncomingTransfer merge(ZephyrTransfer transfer) {
    GenUtils.assertTrue(transfer instanceof ZephyrIncomingTransfer);
    return merge((ZephyrIncomingTransfer) transfer);
  }
  
  /**
   * Updates this transaction by merging the latest information from the given
   * transaction.
   * 
   * Merging can modify or build references to the transfer given so it
   * should not be re-used or it should be copied before calling this method.
   * 
   * @param transfer is the transfer to merge into this one
   * @return this transfer for chaining
   */
  public ZephyrIncomingTransfer merge(ZephyrIncomingTransfer transfer) {
    super.merge(transfer);
    assert(transfer instanceof ZephyrIncomingTransfer);
    if (this == transfer) return this;
    this.setSubaddressIndex(GenUtils.reconcile(this.getSubaddressIndex(), transfer.getSubaddressIndex()));
    this.setAddress(GenUtils.reconcile(this.getAddress(), transfer.getAddress()));
    this.setNumSuggestedConfirmations(GenUtils.reconcile(this.getNumSuggestedConfirmations(), transfer.getNumSuggestedConfirmations(), null, null, false));
    return this;
  }
  
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString(indent) + "\n");
    sb.append(GenUtils.kvLine("Subaddress index", this.getSubaddressIndex(), indent));
    sb.append(GenUtils.kvLine("Address", this.getAddress(), indent));
    sb.append(GenUtils.kvLine("Num suggested confirmations", getNumSuggestedConfirmations(), indent));
    String str = sb.toString();
    return str.substring(0, str.length() - 1);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((address == null) ? 0 : address.hashCode());
    result = prime * result + ((subaddressIndex == null) ? 0 : subaddressIndex.hashCode());
    result = prime * result + ((numSuggestedConfirmations == null) ? 0 : numSuggestedConfirmations.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrIncomingTransfer other = (ZephyrIncomingTransfer) obj;
    if (address == null) {
      if (other.address != null) return false;
    } else if (!address.equals(other.address)) return false;
    if (subaddressIndex == null) {
      if (other.subaddressIndex != null) return false;
    } else if (!subaddressIndex.equals(other.subaddressIndex)) return false;
    if (numSuggestedConfirmations == null) {
      if (other.numSuggestedConfirmations != null) return false;
    } else if (!numSuggestedConfirmations.equals(other.numSuggestedConfirmations)) return false;
    return true;
  }
  
  //------------------- OVERRIDE CO-VARIANT RETURN TYPES ---------------------

  @Override
  public ZephyrIncomingTransfer setTx(ZephyrTxWallet tx) {
    super.setTx(tx);
    return this;
  }
  
  @Override
  public ZephyrIncomingTransfer setAmount(BigInteger amount) {
    super.setAmount(amount);
    return this;
  }
  
  @Override
  public ZephyrIncomingTransfer setAccountIndex(Integer accountIndex) {
    super.setAccountIndex(accountIndex);
    return this;
  }
  
  @Override
  public ZephyrIncomingTransfer setAssetType(String assetType) {
    super.setAssetType(assetType);
    return this;
  }
}
