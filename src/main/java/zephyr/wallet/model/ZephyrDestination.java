package zephyr.wallet.model;

import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.utils.GenUtils;

/**
 * Models an outgoing transfer destination.
 */
public class ZephyrDestination {

  private String address;
  private BigInteger amount;
  private String assetType;
  
  public ZephyrDestination() {
    // nothing to construct
  }
  
  public ZephyrDestination(String address) {
    super();
    this.address = address;
  }
  
  public ZephyrDestination(String address, BigInteger amount) {
    super();
    this.address = address;
    this.amount = amount;
  }
  
  public ZephyrDestination(ZephyrDestination destination) {
    this.address = destination.address;
    this.amount = destination.amount;
    this.assetType = destination.assetType;
  }

  public String getAddress() {
    return address;
  }
  
  public void setAddress(String address) {
    this.address = address;
  }
  
  public BigInteger getAmount() {
    return amount;
  }
  
  public void setAmount(BigInteger amount) {
    this.amount = amount;
  }
  
  /**
   * Returns the asset of this destination as the core reports it on transfers.
   * 
   * On a send this field does not decide what is sent: the currency pair of the tx config (source
   * and destination currency) holds for the whole transaction.
   * 
   * @return the asset name (see {@link ZephyrAssets}), null if not reported
   */
  @JsonProperty("asset_type")
  public String getAssetType() {
    return assetType;
  }
  
  /**
   * Sets the asset of this destination.
   * 
   * @param assetType is the asset name (see {@link ZephyrAssets})
   */
  @JsonProperty("asset_type")
  public void setAssetType(String assetType) {
    this.assetType = assetType;
  }
  
  public ZephyrDestination copy() {
    return new ZephyrDestination(this);
  }
  
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(GenUtils.kvLine("Address", this.getAddress(), indent));
    sb.append(GenUtils.kvLine("Amount", this.getAmount() != null ? this.getAmount().toString() : null, indent));
    sb.append(GenUtils.kvLine("Asset type", this.getAssetType(), indent));
    String str = sb.toString();
    return str.substring(0, str.length() - 1);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((address == null) ? 0 : address.hashCode());
    result = prime * result + ((amount == null) ? 0 : amount.hashCode());
    result = prime * result + ((assetType == null) ? 0 : assetType.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrDestination other = (ZephyrDestination) obj;
    if (address == null) {
      if (other.address != null) return false;
    } else if (!address.equals(other.address)) return false;
    if (amount == null) {
      if (other.amount != null) return false;
    } else if (!amount.equals(other.amount)) return false;
    if (assetType == null) {
      if (other.assetType != null) return false;
    } else if (!assetType.equals(other.assetType)) return false;
    return true;
  }
}
