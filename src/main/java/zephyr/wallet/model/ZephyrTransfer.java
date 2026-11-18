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
  
  /**
   * Creates an empty transfer.
   */
  public ZephyrTransfer() {
    // nothing to initialize
  }
  
  /**
   * Creates a copy of the given transfer's amount, account index and asset, without its
   * transaction.
   *
   * @param transfer is the transfer to copy
   */
  public ZephyrTransfer(final ZephyrTransfer transfer) {
    this.amount = transfer.amount;
    this.accountIndex = transfer.accountIndex;
    this.assetType = transfer.assetType;
    validate();
  }
  
  /**
   * Returns a copy of this transfer without its transaction reference.
   *
   * @return the copy
   */
  public abstract ZephyrTransfer copy();
  
  /**
   * Returns the transaction this transfer belongs to.
   *
   * @return the transaction
   */
  @JsonBackReference
  public ZephyrTxWallet getTx() {
    return tx;
  }
  
  /**
   * Sets the transaction this transfer belongs to.
   *
   * @param tx is the transaction
   * @return this transfer for chaining
   */
  public ZephyrTransfer setTx(ZephyrTxWallet tx) {
    this.tx = tx;
    return this;
  }
  
  /**
   * Returns whether this is an outgoing transfer, the opposite of {@link #isIncoming()}.
   *
   * @return true for an outgoing transfer
   */
  @JsonIgnore
  public Boolean isOutgoing() {
    return !isIncoming();
  }
  
  /**
   * Returns whether this is an incoming transfer.
   *
   * @return true for a {@link ZephyrIncomingTransfer}, false for a {@link ZephyrOutgoingTransfer}
   */
  @JsonProperty("isIncoming")
  public abstract Boolean isIncoming();
  
  /**
   * Returns the amount of the transfer in atomic units (10^12 per coin) of its asset
   * ({@link #getAssetType()}). For an outgoing transfer it is what the wallet sent, without fee and
   * change, in the source asset; for a conversion that is the source amount. See the user guide,
   * {@code docs/guide/05-assets-and-balances.md}, section "Amounts".
   *
   * @return the amount, null if not known
   */
  public BigInteger getAmount() {
    return amount;
  }
  
  /**
   * Sets the amount of the transfer.
   *
   * @param amount is the amount in atomic units
   * @return this transfer for chaining
   */
  public ZephyrTransfer setAmount(BigInteger amount) {
    this.amount = amount;
    return this;
  }
  
  /**
   * Returns the index of the account the funds went to (incoming) or came from (outgoing).
   *
   * @return the account index, null if not known: the incoming transfer of a conversion that
   * {@code createTxs} returns has none
   */
  public Integer getAccountIndex() {
    return accountIndex;
  }

  /**
   * Sets the index of the account; a negative index throws a {@code ZephyrError}.
   *
   * @param accountIndex is the account index
   * @return this transfer for chaining
   */
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
  
  /**
   * Returns the fields of this transfer that are set, one per line, indented.
   *
   * @param indent is the indentation in levels of two spaces
   * @return the description
   */
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
