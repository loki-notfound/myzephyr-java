package zephyr.wallet.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.utils.GenUtils;

/**
 * Models an outgoing transfer of funds from the wallet.
 */
public class ZephyrOutgoingTransfer extends ZephyrTransfer {

  private List<Integer> subaddressIndices;
  private List<String> addresses;
  private List<ZephyrDestination> destinations;
  
  public ZephyrOutgoingTransfer() {
    // nothing to initialize
  }
  
  public ZephyrOutgoingTransfer(final ZephyrOutgoingTransfer transfer) {
    super(transfer);
    if (transfer.subaddressIndices != null) this.subaddressIndices = new ArrayList<Integer>(transfer.subaddressIndices);
    if (transfer.addresses != null) this.addresses = new ArrayList<String>(transfer.addresses);
    if (transfer.destinations != null) {
      this.destinations = new ArrayList<ZephyrDestination>();
      for (ZephyrDestination destination : transfer.getDestinations()) {
        this.destinations.add(destination.copy()); 
      }
    }
  }
  
  @Override
  public ZephyrOutgoingTransfer copy() {
    return new ZephyrOutgoingTransfer(this);
  }
  
  @JsonProperty("isIncoming")
  public Boolean isIncoming() {
    return false;
  }
  
  public List<Integer> getSubaddressIndices() {
    return subaddressIndices;
  }

  public ZephyrOutgoingTransfer setSubaddressIndices(List<Integer> subaddressIndices) {
    this.subaddressIndices = subaddressIndices;
    return this;
  }
  
  public List<String> getAddresses() {
    return addresses;
  }

  public ZephyrOutgoingTransfer setAddresses(List<String> addresses) {
    this.addresses = addresses;
    return this;
  }

  public List<ZephyrDestination> getDestinations() {
    return destinations;
  }
  
  public ZephyrOutgoingTransfer setDestinations(List<ZephyrDestination> destinations) {
    this.destinations = destinations;
    return this;
  }
  
  public ZephyrOutgoingTransfer merge(ZephyrTransfer transfer) {
    GenUtils.assertTrue(transfer instanceof ZephyrOutgoingTransfer);
    return merge((ZephyrOutgoingTransfer) transfer);
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
  public ZephyrOutgoingTransfer merge(ZephyrOutgoingTransfer transfer) {
    super.merge(transfer);
    GenUtils.assertTrue(transfer instanceof ZephyrOutgoingTransfer);
    if (this == transfer) return this;
    this.setSubaddressIndices(GenUtils.reconcile(this.getSubaddressIndices(), transfer.getSubaddressIndices()));
    this.setAddresses(GenUtils.reconcile(this.getAddresses(), transfer.getAddresses()));
    this.setDestinations(GenUtils.reconcile(this.getDestinations(), transfer.getDestinations()));
    return this;
  }
  
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString(indent) + "\n");
    sb.append(GenUtils.kvLine("Subaddress indices", this.getSubaddressIndices(), indent));
    sb.append(GenUtils.kvLine("Addresses", this.getAddresses(), indent));
    if (this.getDestinations() != null) {
      sb.append(GenUtils.kvLine("Destinations", "", indent));
      for (int i = 0; i < this.getDestinations().size(); i++) {
        sb.append(GenUtils.kvLine(i + 1, "", indent + 1));
        sb.append(getDestinations().get(i).toString(indent + 2) + "\n");
      }
    }
    String str = sb.toString();
    return str.substring(0, str.length() - 1);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((addresses == null) ? 0 : addresses.hashCode());
    result = prime * result + ((destinations == null) ? 0 : destinations.hashCode());
    result = prime * result + ((subaddressIndices == null) ? 0 : subaddressIndices.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrOutgoingTransfer other = (ZephyrOutgoingTransfer) obj;
    if (addresses == null) {
      if (other.addresses != null) return false;
    } else if (!addresses.equals(other.addresses)) return false;
    if (destinations == null) {
      if (other.destinations != null) return false;
    } else if (!destinations.equals(other.destinations)) return false;
    if (subaddressIndices == null) {
      if (other.subaddressIndices != null) return false;
    } else if (!subaddressIndices.equals(other.subaddressIndices)) return false;
    return true;
  }
  
  // ------------------- OVERRIDE CO-VARIANT RETURN TYPES ---------------------

  @Override
  public ZephyrOutgoingTransfer setTx(ZephyrTxWallet tx) {
    super.setTx(tx);
    return this;
  }

  @Override
  public ZephyrOutgoingTransfer setAmount(BigInteger amount) {
    super.setAmount(amount);
    return this;
  }

  @Override
  public ZephyrOutgoingTransfer setAccountIndex(Integer accountIndex) {
    super.setAccountIndex(accountIndex);
    return this;
  }
}
