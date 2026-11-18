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
  
  /**
   * Creates an empty outgoing transfer.
   */
  public ZephyrOutgoingTransfer() {
    // nothing to initialize
  }
  
  /**
   * Creates a copy of the given outgoing transfer with copies of its destinations, without its
   * transaction.
   *
   * @param transfer is the transfer to copy
   */
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
  
  /**
   * Returns the indices of the subaddresses of the account that the transaction spent from.
   *
   * @return the subaddress indices, null if not known: on a transaction that {@code createTxs} or a
   * sweep returns they are known only when exactly one subaddress index was requested
   */
  public List<Integer> getSubaddressIndices() {
    return subaddressIndices;
  }

  /**
   * Sets the indices of the subaddresses spent from.
   *
   * @param subaddressIndices are the subaddress indices
   * @return this transfer for chaining
   */
  public ZephyrOutgoingTransfer setSubaddressIndices(List<Integer> subaddressIndices) {
    this.subaddressIndices = subaddressIndices;
    return this;
  }
  
  /**
   * Returns the addresses of the subaddresses that the transaction spent from, in the order of
   * {@link #getSubaddressIndices()}.
   *
   * @return the addresses, null if not known, as on the transactions that {@code createTxs} and the
   * sweeps return
   */
  public List<String> getAddresses() {
    return addresses;
  }

  /**
   * Sets the addresses of the subaddresses spent from.
   *
   * @param addresses are the addresses
   * @return this transfer for chaining
   */
  public ZephyrOutgoingTransfer setAddresses(List<String> addresses) {
    this.addresses = addresses;
    return this;
  }

  /**
   * Returns the recipients of the transfer with their amounts. They are local data of the sending
   * wallet: a wallet restored from its mnemonic does not have them for earlier sends. For a
   * conversion the destination's amount changes meaning (see
   * {@link ZephyrDestination#getAmount()}). On the transactions that {@code createTxs} and
   * {@code sweepUnlocked} return, a destination's {@link ZephyrDestination#getAssetType()} is not
   * the asset sent: the core repeats the asset of the configuration's destination, or its default
   * {@code "ZEPH"} when that has none (read in the core's code). The currency pair of the
   * configuration is what was sent.
   *
   * @return the destinations, null if not known, and on the transactions of a {@code createTxs}
   * send that the core split into several transactions
   */
  public List<ZephyrDestination> getDestinations() {
    return destinations;
  }
  
  /**
   * Sets the recipients of the transfer.
   *
   * @param destinations are the destinations
   * @return this transfer for chaining
   */
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

  @Override
  public ZephyrOutgoingTransfer setAssetType(String assetType) {
    super.setAssetType(assetType);
    return this;
  }
}
