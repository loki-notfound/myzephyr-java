package zephyr.wallet.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import zephyr.lib.utils.GenUtils;
import java.math.BigInteger;
import java.util.List;
import zephyr.common.ZephyrError;
import zephyr.daemon.model.ZephyrKeyImage;
import zephyr.daemon.model.ZephyrOutput;
import zephyr.daemon.model.ZephyrTx;

/**
 * Models a Zephyr output with wallet extensions.
 */
public class ZephyrOutputWallet extends ZephyrOutput {

  private Integer accountIndex;
  private Integer subaddressIndex;
  private Boolean isSpent;
  private Boolean isFrozen;
  
  public ZephyrOutputWallet() {
    super();
  }
  
  /**
   * Deep copy constructor.
   * 
   * @param output is the output to initialize from
   */
  public ZephyrOutputWallet(final ZephyrOutputWallet output) {
    super(output);
    this.accountIndex = output.accountIndex;
    this.subaddressIndex = output.subaddressIndex;
    this.isSpent = output.isSpent;
    this.isFrozen = output.isFrozen;
  }
  
  @Override
  public ZephyrOutputWallet copy() {
    return new ZephyrOutputWallet(this);
  }
  
  @Override
  public ZephyrTxWallet getTx() {
    return (ZephyrTxWallet) super.getTx();
  }
  
  @Override
  @JsonIgnore
  public ZephyrOutputWallet setTx(ZephyrTx tx) {
    if (tx != null && !(tx instanceof ZephyrTxWallet)) throw new ZephyrError("Wallet output's transaction must be of type ZephyrTxWallet");
    super.setTx(tx);
    return this;
  }
  
  @JsonProperty("tx")
  public ZephyrOutputWallet setTx(ZephyrTxWallet tx) {
    super.setTx(tx);
    return this;
  }
  
  public Integer getAccountIndex() {
    return accountIndex;
  }
  
  public ZephyrOutputWallet setAccountIndex(Integer accountIndex) {
    this.accountIndex = accountIndex;
    return this;
  }
  
  public Integer getSubaddressIndex() {
    return subaddressIndex;
  }
  
  public ZephyrOutputWallet setSubaddressIndex(Integer subaddressIndex) {
    this.subaddressIndex = subaddressIndex;
    return this;
  }
  
  @JsonProperty("isSpent")
  public Boolean isSpent() {
    return isSpent;
  }
  
  public ZephyrOutputWallet setIsSpent(Boolean isSpent) {
    this.isSpent = isSpent;
    return this;
  }
  
  /**
   * Indicates if this output has been deemed 'malicious' and will therefore
   * not be spent by the wallet.
   * 
   * @return Boolean is whether or not this output is frozen
   */
  @JsonProperty("isFrozen")
  public Boolean isFrozen() {
    return isFrozen;
  }
  
  public ZephyrOutputWallet setIsFrozen(Boolean isFrozen) {
    this.isFrozen = isFrozen;
    return this;
  }
  
  /**
   * Convenience method to indicate if this output's tx is locked.
   * 
   * @return a boolean indicating the locked state of this output's tx
   */
  @JsonIgnore
  public Boolean isLocked() {
    if (getTx() == null) return null;
    return getTx().isLocked();
  }
  
  @Override
  public ZephyrOutputWallet merge(ZephyrOutput output) {
    return merge((ZephyrOutputWallet) output);
  }
  
  public ZephyrOutputWallet merge(ZephyrOutputWallet output) {
    if (this == output) return this;
    super.merge(output);
    this.setAccountIndex(GenUtils.reconcile(this.getAccountIndex(), output.getAccountIndex()));
    this.setSubaddressIndex(GenUtils.reconcile(this.getSubaddressIndex(), output.getSubaddressIndex()));
    this.setIsSpent(GenUtils.reconcile(this.isSpent(), output.isSpent(), null, true, null)); // output can become spent
    this.setIsFrozen(GenUtils.reconcile(this.isFrozen(), output.isFrozen()));
    return this;
  }
  
  @Override
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString(indent) + "\n");
    sb.append(GenUtils.kvLine("Account index", this.getAccountIndex(), indent));
    sb.append(GenUtils.kvLine("Subaddress index", this.getSubaddressIndex(), indent));
    sb.append(GenUtils.kvLine("Is spent", this.isSpent(), indent));
    sb.append(GenUtils.kvLine("Is frozen", this.isFrozen(), indent));
    String str = sb.toString();
    return str.substring(0, str.length() - 1);  // strip last newline
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((accountIndex == null) ? 0 : accountIndex.hashCode());
    result = prime * result + ((isFrozen == null) ? 0 : isFrozen.hashCode());
    result = prime * result + ((isSpent == null) ? 0 : isSpent.hashCode());
    result = prime * result + ((subaddressIndex == null) ? 0 : subaddressIndex.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrOutputWallet other = (ZephyrOutputWallet) obj;
    if (accountIndex == null) {
      if (other.accountIndex != null) return false;
    } else if (!accountIndex.equals(other.accountIndex)) return false;
    if (isFrozen == null) {
      if (other.isFrozen != null) return false;
    } else if (!isFrozen.equals(other.isFrozen)) return false;
    if (isSpent == null) {
      if (other.isSpent != null) return false;
    } else if (!isSpent.equals(other.isSpent)) return false;
    if (subaddressIndex == null) {
      if (other.subaddressIndex != null) return false;
    } else if (!subaddressIndex.equals(other.subaddressIndex)) return false;
    return true;
  }
  
  // ------------------- OVERRIDE CO-VARIANT RETURN TYPES ---------------------

  @Override
  public ZephyrOutputWallet setKeyImage(ZephyrKeyImage keyImage) {
    super.setKeyImage(keyImage);
    return this;
  }
  
  @Override
  public ZephyrOutputWallet setAmount(BigInteger amount) {
    super.setAmount(amount);
    return this;
  }
  
  @Override
  public ZephyrOutputWallet setIndex(Long index) {
    super.setIndex(index);
    return this;
  }
  
  @Override
  public ZephyrOutputWallet setRingOutputIndices(List<Long> ringOutputIndices) {
    super.setRingOutputIndices(ringOutputIndices);
    return this;
  }
  
  @Override
  public ZephyrOutputWallet setStealthPublicKey(String stealthPublicKey) {
    super.setStealthPublicKey(stealthPublicKey);
    return this;
  }
}
