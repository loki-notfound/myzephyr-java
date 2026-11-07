package zephyr.wallet.model;


import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.utils.GenUtils;

/**
 * Zephyr subaddress model.
 */
public class ZephyrSubaddress {

  private Integer accountIndex;
  private Integer index;
  private String address;
  private String label;
  private ZephyrBalance balance;
  private ZephyrBalance unlockedBalance;
  private Long numUnspentOutputs;
  private Boolean isUsed;
  private Long numBlocksToUnlock;
  
  public ZephyrSubaddress() {
    // nothing to construct
  }
  
  public ZephyrSubaddress(String address) {
    this.address = address;
  }
  
  public ZephyrSubaddress(int accountIndex, int index) {
    this.accountIndex = accountIndex;
    this.index = index;
  }
  
  public Integer getAccountIndex() {
    return accountIndex;
  }
  
  public ZephyrSubaddress setAccountIndex(Integer accountIndex) {
    this.accountIndex = accountIndex;
    return this;
  }
  
  public Integer getIndex() {
    return index;
  }
  
  public ZephyrSubaddress setIndex(Integer index) {
    this.index = index;
    return this;
  }
  
  public String getAddress() {
    return address;
  }
  
  public ZephyrSubaddress setAddress(String address) {
    this.address = address;
    return this;
  }
  
  public String getLabel() {
    return label;
  }
  
  public ZephyrSubaddress setLabel(String label) {
    this.label = label;
    return this;
  }
  
  /**
   * Returns the subaddress's balance per asset. It lists only the assets the subaddress holds; an
   * absent asset is zero ({@link ZephyrBalance#get(String)}, {@link ZephyrBalance#equals(Object)}).
   *
   * @return the subaddress's balance per asset in atomic units
   */
  public ZephyrBalance getBalance() {
    return balance;
  }
  
  /**
   * Sets the subaddress's balance per asset.
   *
   * @param balance is the subaddress's balance per asset in atomic units
   * @return this subaddress for chaining
   */
  public ZephyrSubaddress setBalance(ZephyrBalance balance) {
    this.balance = balance;
    return this;
  }
  
  /**
   * Returns the subaddress's unlocked (spendable) balance per asset; an absent asset is zero.
   *
   * @return the subaddress's unlocked balance per asset in atomic units
   */
  public ZephyrBalance getUnlockedBalance() {
    return unlockedBalance;
  }
  
  /**
   * Sets the subaddress's unlocked balance per asset.
   *
   * @param unlockedBalance is the subaddress's unlocked balance per asset in atomic units
   * @return this subaddress for chaining
   */
  public ZephyrSubaddress setUnlockedBalance(ZephyrBalance unlockedBalance) {
    this.unlockedBalance = unlockedBalance;
    return this;
  }
  
  public Long getNumUnspentOutputs() {
    return numUnspentOutputs;
  }
  
  public ZephyrSubaddress setNumUnspentOutputs(Long numUnspentOutputs) {
    this.numUnspentOutputs = numUnspentOutputs;
    return this;
  }
  
  @JsonProperty("isUsed")
  public Boolean isUsed() {
    return isUsed;
  }
  
  public ZephyrSubaddress setIsUsed(Boolean isUsed) {
    this.isUsed = isUsed;
    return this;
  }

  public Long getNumBlocksToUnlock() {
    return numBlocksToUnlock;
  }

  public ZephyrSubaddress setNumBlocksToUnlock(Long numBlocksToUnlock) {
    this.numBlocksToUnlock = numBlocksToUnlock;
    return this;
  }
  
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(GenUtils.kvLine("Account index", this.getAccountIndex(), indent));
    sb.append(GenUtils.kvLine("Subaddress index", this.getIndex(), indent));
    sb.append(GenUtils.kvLine("Address", this.getAddress(), indent));
    sb.append(GenUtils.kvLine("Label", this.getLabel(), indent));
    if (this.getBalance() != null) {
      sb.append(GenUtils.kvLine("Balance", "", indent));
      if (!this.getBalance().isEmpty()) sb.append(this.getBalance().toString(indent + 1) + "\n");
    }
    if (this.getUnlockedBalance() != null) {
      sb.append(GenUtils.kvLine("Unlocked balance", "", indent));
      if (!this.getUnlockedBalance().isEmpty()) sb.append(this.getUnlockedBalance().toString(indent + 1) + "\n");
    }
    sb.append(GenUtils.kvLine("Num unspent outputs", this.getNumUnspentOutputs(), indent));
    sb.append(GenUtils.kvLine("Is used", this.isUsed(), indent));
    sb.append(GenUtils.kvLine("Num blocks to unlock", this.getNumBlocksToUnlock(), indent));
    String str = sb.toString();
    return str.substring(0, str.length() - 1);  // strip last newline
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((accountIndex == null) ? 0 : accountIndex.hashCode());
    result = prime * result + ((address == null) ? 0 : address.hashCode());
    result = prime * result + ((balance == null) ? 0 : balance.hashCode());
    result = prime * result + ((index == null) ? 0 : index.hashCode());
    result = prime * result + ((isUsed == null) ? 0 : isUsed.hashCode());
    result = prime * result + ((label == null) ? 0 : label.hashCode());
    result = prime * result + ((numBlocksToUnlock == null) ? 0 : numBlocksToUnlock.hashCode());
    result = prime * result + ((numUnspentOutputs == null) ? 0 : numUnspentOutputs.hashCode());
    result = prime * result + ((unlockedBalance == null) ? 0 : unlockedBalance.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrSubaddress other = (ZephyrSubaddress) obj;
    if (accountIndex == null) {
      if (other.accountIndex != null) return false;
    } else if (!accountIndex.equals(other.accountIndex)) return false;
    if (address == null) {
      if (other.address != null) return false;
    } else if (!address.equals(other.address)) return false;
    if (balance == null) {
      if (other.balance != null) return false;
    } else if (!balance.equals(other.balance)) return false;
    if (index == null) {
      if (other.index != null) return false;
    } else if (!index.equals(other.index)) return false;
    if (isUsed == null) {
      if (other.isUsed != null) return false;
    } else if (!isUsed.equals(other.isUsed)) return false;
    if (label == null) {
      if (other.label != null) return false;
    } else if (!label.equals(other.label)) return false;
    if (numBlocksToUnlock == null) {
      if (other.numBlocksToUnlock != null) return false;
    } else if (!numBlocksToUnlock.equals(other.numBlocksToUnlock)) return false;
    if (numUnspentOutputs == null) {
      if (other.numUnspentOutputs != null) return false;
    } else if (!numUnspentOutputs.equals(other.numUnspentOutputs)) return false;
    if (unlockedBalance == null) {
      if (other.unlockedBalance != null) return false;
    } else if (!unlockedBalance.equals(other.unlockedBalance)) return false;
    return true;
  }
}
