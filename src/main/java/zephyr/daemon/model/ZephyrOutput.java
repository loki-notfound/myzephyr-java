package zephyr.daemon.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.utils.GenUtils;

/**
 * Models a Zephyr transaction output.
 */
public class ZephyrOutput {

  private ZephyrTx tx;
  private ZephyrKeyImage keyImage;
  private BigInteger amount;
  private Long index;
  private List<Long> ringOutputIndices;
  private String stealthPublicKey;
  private String assetType;
  
  public ZephyrOutput() {
    // nothing to build
  }
  
  public ZephyrOutput(final ZephyrOutput output) {
    if (output.keyImage != null) this.keyImage = output.keyImage.copy();
    this.amount = output.amount;
    this.index = output.index;
    if (output.ringOutputIndices != null) this.ringOutputIndices = new ArrayList<Long>(output.ringOutputIndices);
    this.stealthPublicKey = output.stealthPublicKey;
    this.assetType = output.assetType;
  }
  
  public ZephyrOutput copy() {
    return new ZephyrOutput(this);
  }
  
  @JsonBackReference
  public ZephyrTx getTx() {
    return tx;
  }
  
  public ZephyrOutput setTx(ZephyrTx tx) {
    this.tx = tx;
    return this;
  }
  
  public ZephyrKeyImage getKeyImage() {
    return keyImage;
  }
  
  public ZephyrOutput setKeyImage(ZephyrKeyImage keyImage) {
    this.keyImage = keyImage;
    return this;
  }
  
  public BigInteger getAmount() {
    return amount;
  }
  
  public ZephyrOutput setAmount(BigInteger amount) {
    this.amount = amount;
    return this;
  }
  
  public Long getIndex() {
    return index;
  }
  
  public ZephyrOutput setIndex(Long index) {
    this.index = index;
    return this;
  }
  
  public List<Long> getRingOutputIndices() {
    return ringOutputIndices;
  }
  
  public ZephyrOutput setRingOutputIndices(List<Long> ringOutputIndices) {
    this.ringOutputIndices = ringOutputIndices;
    return this;
  }
  
  public String getStealthPublicKey() {
    return stealthPublicKey;
  }
  
  public ZephyrOutput setStealthPublicKey(String stealthPublicKey) {
    this.stealthPublicKey = stealthPublicKey;
    return this;
  }
  
  /**
   * Returns the asset of this output.
   * 
   * The core does not serialize the asset of an output; the wallet listener callbacks set it.
   * 
   * @return the asset name (see zephyr.wallet.model.ZephyrAssets), null if not known
   */
  @JsonProperty("asset_type")
  public String getAssetType() {
    return assetType;
  }
  
  /**
   * Sets the asset of this output.
   * 
   * @param assetType is the asset name
   * @return this output for chaining
   */
  @JsonProperty("asset_type")
  public ZephyrOutput setAssetType(String assetType) {
    this.assetType = assetType;
    return this;
  }
  
  @Override
  public String toString() {
    return toString(0);
  }
  
  public ZephyrOutput merge(ZephyrOutput output) {
    GenUtils.assertTrue(output instanceof ZephyrOutput);
    if (this == output) return this;
    
    // merge txs if they're different which comes back to merging outputs
    if (this.getTx() != output.getTx()) this.getTx().merge(output.getTx());
    
    // otherwise merge output fields
    else {
      if (this.getKeyImage() == null) this.setKeyImage(output.getKeyImage());
      else if (output.getKeyImage() != null) this.getKeyImage().merge(output.getKeyImage());
      this.setAmount(GenUtils.reconcile(this.getAmount(), output.getAmount()));
      this.setIndex(GenUtils.reconcile(this.getIndex(), output.getIndex()));
      this.setAssetType(GenUtils.reconcile(this.getAssetType(), output.getAssetType()));
    }

    return this;
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    if (getKeyImage() != null) {
      sb.append(GenUtils.kvLine("Key image", "", indent));
      sb.append(getKeyImage().toString(indent + 1) + "\n");
    }
    sb.append(GenUtils.kvLine("Amount", getAmount(), indent));
    sb.append(GenUtils.kvLine("Index", getIndex(), indent));
    sb.append(GenUtils.kvLine("Ring output indices", getRingOutputIndices(), indent));
    sb.append(GenUtils.kvLine("Stealth public key", getStealthPublicKey(), indent));
    sb.append(GenUtils.kvLine("Asset type", getAssetType(), indent));
    String str = sb.toString();
    return str.isEmpty() ? str : str.substring(0, str.length() - 1);  // strip newline
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((amount == null) ? 0 : amount.hashCode());
    result = prime * result + ((assetType == null) ? 0 : assetType.hashCode());
    result = prime * result + ((index == null) ? 0 : index.hashCode());
    result = prime * result + ((keyImage == null) ? 0 : keyImage.hashCode());
    result = prime * result + ((ringOutputIndices == null) ? 0 : ringOutputIndices.hashCode());
    result = prime * result + ((stealthPublicKey == null) ? 0 : stealthPublicKey.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrOutput other = (ZephyrOutput) obj;
    if (amount == null) {
      if (other.amount != null) return false;
    } else if (!amount.equals(other.amount)) return false;
    if (assetType == null) {
      if (other.assetType != null) return false;
    } else if (!assetType.equals(other.assetType)) return false;
    if (index == null) {
      if (other.index != null) return false;
    } else if (!index.equals(other.index)) return false;
    if (keyImage == null) {
      if (other.keyImage != null) return false;
    } else if (!keyImage.equals(other.keyImage)) return false;
    if (ringOutputIndices == null) {
      if (other.ringOutputIndices != null) return false;
    } else if (!ringOutputIndices.equals(other.ringOutputIndices)) return false;
    if (stealthPublicKey == null) {
      if (other.stealthPublicKey != null) return false;
    } else if (!stealthPublicKey.equals(other.stealthPublicKey)) return false;
    return true;
  }
}
