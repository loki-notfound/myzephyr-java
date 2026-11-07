package zephyr.wallet.model;

import java.util.List;

import zephyr.lib.utils.GenUtils;

/**
 * Zephyr account model.
 */
public class ZephyrAccount {

  private Integer index;
  private String primaryAddress;
  private ZephyrBalance balance;
  private ZephyrBalance unlockedBalance;
  private String tag;
  private List<ZephyrSubaddress> subaddresses;
  
  public ZephyrAccount() {
    super();
  }
  
  /**
   * Constructs an account.
   *
   * @param index is the account index
   * @param primaryAddress is the account's primary address
   * @param balance is the account's balance per asset
   * @param unlockedBalance is the account's unlocked balance per asset
   * @param subaddresses are the account's subaddresses (may be null)
   */
  public ZephyrAccount(int index, String primaryAddress, ZephyrBalance balance, ZephyrBalance unlockedBalance, List<ZephyrSubaddress> subaddresses) {
    super();
    this.index = index;
    this.primaryAddress = primaryAddress;
    this.balance = balance;
    this.unlockedBalance = unlockedBalance;
    this.subaddresses = subaddresses;
  }
  
  public Integer getIndex() {
    return index;
  }
  
  public void setIndex(Integer index) {
    this.index = index;
  }
  
  public String getPrimaryAddress() {
    return primaryAddress;
  }

  public void setPrimaryAddress(String primaryAddress) {
    this.primaryAddress = primaryAddress;
  }
  
  /**
   * Returns the account's balance per asset. It lists only the assets the account holds; an
   * absent asset is zero ({@link ZephyrBalance#get(String)}, {@link ZephyrBalance#equals(Object)}).
   *
   * @return the account's balance per asset in atomic units
   */
  public ZephyrBalance getBalance() {
    return balance;
  }
  
  /**
   * Sets the account's balance per asset.
   *
   * @param balance is the account's balance per asset in atomic units
   */
  public void setBalance(ZephyrBalance balance) {
    this.balance = balance;
  }
  
  /**
   * Returns the account's unlocked (spendable) balance per asset; an absent asset is zero.
   *
   * @return the account's unlocked balance per asset in atomic units
   */
  public ZephyrBalance getUnlockedBalance() {
    return unlockedBalance;
  }
  
  /**
   * Sets the account's unlocked balance per asset.
   *
   * @param unlockedBalance is the account's unlocked balance per asset in atomic units
   */
  public void setUnlockedBalance(ZephyrBalance unlockedBalance) {
    this.unlockedBalance = unlockedBalance;
  }
  
  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }
  
  public List<ZephyrSubaddress> getSubaddresses() {
    return subaddresses;
  }
  
  public void setSubaddresses(List<ZephyrSubaddress> subaddresses) {
    this.subaddresses = subaddresses;
    if (subaddresses != null) {
      for (ZephyrSubaddress subaddress : subaddresses) {
        subaddress.setAccountIndex(index);
      }
    }
  }
  
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(GenUtils.kvLine("Index", this.getIndex(), indent));
    sb.append(GenUtils.kvLine("Primary address", this.getPrimaryAddress(), indent));
    if (this.getBalance() != null) {
      sb.append(GenUtils.kvLine("Balance", "", indent));
      if (!this.getBalance().isEmpty()) sb.append(this.getBalance().toString(indent + 1) + "\n");
    }
    if (this.getUnlockedBalance() != null) {
      sb.append(GenUtils.kvLine("Unlocked balance", "", indent));
      if (!this.getUnlockedBalance().isEmpty()) sb.append(this.getUnlockedBalance().toString(indent + 1) + "\n");
    }
    sb.append(GenUtils.kvLine("Tag", this.getTag(), indent));
    if (this.getSubaddresses() != null) {
      sb.append(GenUtils.kvLine("Subaddresses", "", indent));
      for (int i = 0; i < this.getSubaddresses().size(); i++) {
        sb.append(GenUtils.kvLine(i + 1, "", indent + 1));
        sb.append(this.getSubaddresses().get(i).toString(indent + 2) + "\n");
      }
    }
    String str = sb.toString();
    return str.substring(0, str.length() - 1);  // strip last newline
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((balance == null) ? 0 : balance.hashCode());
    result = prime * result + ((index == null) ? 0 : index.hashCode());
    result = prime * result + ((primaryAddress == null) ? 0 : primaryAddress.hashCode());
    result = prime * result + ((subaddresses == null) ? 0 : subaddresses.hashCode());
    result = prime * result + ((tag == null) ? 0 : tag.hashCode());
    result = prime * result + ((unlockedBalance == null) ? 0 : unlockedBalance.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrAccount other = (ZephyrAccount) obj;
    if (balance == null) {
      if (other.balance != null) return false;
    } else if (!balance.equals(other.balance)) return false;
    if (index == null) {
      if (other.index != null) return false;
    } else if (!index.equals(other.index)) return false;
    if (primaryAddress == null) {
      if (other.primaryAddress != null) return false;
    } else if (!primaryAddress.equals(other.primaryAddress)) return false;
    if (subaddresses == null) {
      if (other.subaddresses != null) return false;
    } else if (!subaddresses.equals(other.subaddresses)) return false;
    if (tag == null) {
      if (other.tag != null) return false;
    } else if (!tag.equals(other.tag)) return false;
    if (unlockedBalance == null) {
      if (other.unlockedBalance != null) return false;
    } else if (!unlockedBalance.equals(other.unlockedBalance)) return false;
    return true;
  }
}
