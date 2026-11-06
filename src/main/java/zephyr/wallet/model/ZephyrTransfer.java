package zephyr.wallet.model;

import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.utils.GenUtils;
import zephyr.common.ZephyrError;

/**
 * Models a base transfer of funds to or from the wallet.
 * 
 * Transfers are either of type ZephyrIncomingTransfer or ZephyrOutgoingTransfer so this class is abstract.
 */
public abstract class ZephyrTransfer {

  private ZephyrTxWallet tx;
  private BigInteger amount;
  private Integer accountIndex;
  private String assetType;
  
  public ZephyrTransfer() {
    // nothing to initialize
  }
  
  public ZephyrTransfer(final ZephyrTransfer transfer) {
    this.amount = transfer.amount;
    this.accountIndex = transfer.accountIndex;
    this.assetType = transfer.assetType;
    validate();
  }
  
  public abstract ZephyrTransfer copy();
  
  @JsonBackReference
  public ZephyrTxWallet getTx() {
    return tx;
  }
  
  public ZephyrTransfer setTx(ZephyrTxWallet tx) {
    this.tx = tx;
    return this;
  }
  
  @JsonIgnore
  public Boolean isOutgoing() {
    return !isIncoming();
  }
  
  @JsonProperty("isIncoming")
  public abstract Boolean isIncoming();
  
  public BigInteger getAmount() {
    return amount;
  }
  
  public ZephyrTransfer setAmount(BigInteger amount) {
    this.amount = amount;
    return this;
  }
  
  public Integer getAccountIndex() {
    return accountIndex;
  }

  public ZephyrTransfer setAccountIndex(Integer accountIndex) {
    this.accountIndex = accountIndex;
    validate();
    return this;
  }
  
  /**
   * Returns the asset of this transfer as the core reports it.
   * 
   * @return the asset name (see {@link ZephyrAssets}; V1 names pass through unchanged), null if not reported
   */
  @JsonProperty("asset_type")
  public String getAssetType() {
    return assetType;
  }
  
  /**
   * Sets the asset of this transfer.
   * 
   * @param assetType is the asset name (see {@link ZephyrAssets})
   * @return this transfer for chaining
   */
  @JsonProperty("asset_type")
  public ZephyrTransfer setAssetType(String assetType) {
    this.assetType = assetType;
    return this;
  }
  
  /**
   * Updates this transfer by merging the latest information from the given
   * transfer.
   * 
   * Merging can modify or build references to the transfer given so it
   * should not be re-used or it should be copied before calling this method.
   * 
   * @param transfer is the transfer to merge into this one
   * @return the merged transfer
   */
  public ZephyrTransfer merge(ZephyrTransfer transfer) {
    assert(transfer instanceof ZephyrTransfer);
    if (this == transfer) return this;
    
    // merge txs if they're different which comes back to merging transfers
    if (this.getTx() != transfer.getTx()) {
      this.getTx().merge(transfer.getTx());
      return this;
    }
    
    // otherwise merge transfer fields
    this.setAccountIndex(GenUtils.reconcile(this.getAccountIndex(), transfer.getAccountIndex()));
    this.setAssetType(GenUtils.reconcile(this.getAssetType(), transfer.getAssetType()));
    
    // TODO zephyr: failed tx in pool (after testUpdateLockedDifferentAccounts()) causes non-originating saved wallets to return duplicate incoming transfers but one has amount of 0
    if (this.getAmount() != null && transfer.getAmount() != null && !this.getAmount().equals(transfer.getAmount()) && (BigInteger.valueOf(0).equals(this.getAmount()) || BigInteger.valueOf(0).equals(transfer.getAmount()))) {
      System.err.println("WARNING: zephyr returning transfers with 0 amount/numSuggestedConfirmations");
    } else {
      this.setAmount(GenUtils.reconcile(this.getAmount(), transfer.getAmount()));
    }
    
    return this;
  }
  
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(GenUtils.kvLine("Is incoming", this.isIncoming(), indent));
    sb.append(GenUtils.kvLine("Amount", this.getAmount() != null ? this.getAmount().toString() : null, indent));
    sb.append(GenUtils.kvLine("Account index", this.getAccountIndex(), indent));
    sb.append(GenUtils.kvLine("Asset type", this.getAssetType(), indent));
    String str = sb.toString();
    return str.isEmpty() ? str :  str.substring(0, str.length() - 1);	  // strip last newline
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((accountIndex == null) ? 0 : accountIndex.hashCode());
    result = prime * result + ((amount == null) ? 0 : amount.hashCode());
    result = prime * result + ((assetType == null) ? 0 : assetType.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrTransfer other = (ZephyrTransfer) obj;
    if (accountIndex == null) {
      if (other.accountIndex != null) return false;
    } else if (!accountIndex.equals(other.accountIndex)) return false;
    if (amount == null) {
      if (other.amount != null) return false;
    } else if (!amount.equals(other.amount)) return false;
    if (assetType == null) {
      if (other.assetType != null) return false;
    } else if (!assetType.equals(other.assetType)) return false;
    return true;
  }
  
  private void validate() {
    if (accountIndex != null && accountIndex < 0) throw new ZephyrError("Account index must be >= 0");
  }
}
