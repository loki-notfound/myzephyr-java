package zephyr.wallet.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.types.Filter;
import zephyr.lib.utils.GenUtils;
import zephyr.common.ZephyrError;

/**
 * Configures a query to retrieve transfers.
 * 
 * All transfers are returned except those that do not meet the criteria defined in this query.
 */
public class ZephyrTransferQuery extends ZephyrTransfer implements Filter<ZephyrTransfer> {

  /**
   * The transaction query that the transaction of a matching transfer meets, or null.
   */
  protected ZephyrTxQuery txQuery;
  private Boolean isIncoming;
  private String address;
  private List<String> addresses;
  private Integer subaddressIndex;
  private List<Integer> subaddressIndices;
  private List<ZephyrDestination> destinations;
  private Boolean hasDestinations;
  
  /**
   * Creates a query that every transfer meets.
   */
  public ZephyrTransferQuery() {
    
  }
  
  /**
   * Creates a copy of the given query; the copy refers to the same transaction query.
   *
   * @param query is the query to copy
   */
  public ZephyrTransferQuery(final ZephyrTransferQuery query) {
    super(query);
    this.isIncoming = query.isIncoming;
    this.address = query.address;
    if (query.addresses != null) this.addresses = new ArrayList<String>(query.addresses);
    this.subaddressIndex = query.subaddressIndex;
    if (query.subaddressIndices != null) this.subaddressIndices = new ArrayList<Integer>(query.subaddressIndices);
    if (query.destinations != null) {
      this.destinations = new ArrayList<ZephyrDestination>();
      for (ZephyrDestination destination : query.getDestinations()) this.destinations.add(destination.copy());
    }
    this.hasDestinations = query.hasDestinations;
    this.txQuery = query.txQuery; // reference original by default, ZephyrTxQuery's deep copy will set this to itself
    validate();
  }
  
  @Override
  public ZephyrTransferQuery copy() {
    return new ZephyrTransferQuery(this);
  }
  
  /**
   * Returns the transaction query that the transaction of a matching transfer meets.
   *
   * @return the transaction query, null if none
   */
  @JsonIgnore
  public ZephyrTxQuery getTxQuery() {
    return txQuery;
  }

  /**
   * Sets the transaction query that the transaction of a matching transfer meets, and makes this
   * query its transfer query.
   *
   * @param txQuery is the transaction query, null for none
   * @return this query for chaining
   */
  public ZephyrTransferQuery setTxQuery(ZephyrTxQuery txQuery) {
    this.txQuery = txQuery;
    if (txQuery != null) txQuery.transferQuery = this;
    return this;
  }

  /**
   * Returns the direction to match: true for incoming transfers only, false for outgoing ones only.
   *
   * @return the direction, null for both
   */
  @Override
  public Boolean isIncoming() {
    return isIncoming;
  }

  /**
   * Sets the direction to match: true for incoming transfers only, false for outgoing ones only.
   *
   * @param isIncoming is the direction, null for both
   * @return this query for chaining
   */
  public ZephyrTransferQuery setIsIncoming(Boolean isIncoming) {
    this.isIncoming = isIncoming;
    return this;
  }
  
  /**
   * Returns the direction to match: true for outgoing transfers only, false for incoming ones only.
   *
   * @return the direction, null for both
   */
  @Override
  public Boolean isOutgoing() {
    return isIncoming == null ? null : !isIncoming;
  }
  
  /**
   * Sets the direction to match: true for outgoing transfers only, false for incoming ones only.
   *
   * @param isOutgoing is the direction, null for both
   * @return this query for chaining
   */
  public ZephyrTransferQuery setIsOutgoing(Boolean isOutgoing) {
    isIncoming = isOutgoing == null ? null : !isOutgoing;
    return this;
  }

  /**
   * Returns the address to match, see {@link #setAddress(String)}.
   *
   * @return the address, null for any
   */
  public String getAddress() {
    return address;
  }

  /**
   * Sets the address to match: an incoming transfer matches if it was received at this address, an
   * outgoing transfer if it spent from this address (one of its {@code getAddresses()}).
   *
   * @param address is the address, null for any
   * @return this query for chaining
   */
  public ZephyrTransferQuery setAddress(String address) {
    this.address = address;
    return this;
  }

  /**
   * Returns the addresses to match, see {@link #setAddresses(List)}.
   *
   * @return the addresses, null for any
   */
  public List<String> getAddresses() {
    return addresses;
  }

  /**
   * Sets the addresses to match: an incoming transfer matches if it was received at one of them, an
   * outgoing transfer if it spent from one of them. {@code ZephyrWalletFull} does not support it:
   * the core throws "monero_transfer_query addresses deserialization not implemented" for a query
   * that sets it (read in the core's code). Use {@link #setAddress(String)} or filter in the app.
   *
   * @param addresses are the addresses, null for any
   * @return this query for chaining
   */
  public ZephyrTransferQuery setAddresses(List<String> addresses) {
    this.addresses = addresses;
    return this;
  }
  
  /**
   * Sets the addresses to match, as {@link #setAddresses(List)} does.
   *
   * @param addresses are the addresses
   * @return this query for chaining
   */
  public ZephyrTransferQuery setAddresses(String... addresses) {
    this.addresses = GenUtils.arrayToList(addresses);
    return this;
  }

  /**
   * Returns the subaddress index to match, see {@link #setSubaddressIndex(Integer)}.
   *
   * @return the subaddress index, null for any
   */
  public Integer getSubaddressIndex() {
    return subaddressIndex;
  }

  /**
   * Sets the subaddress index to match: an incoming transfer matches if it was received at this
   * subaddress, an outgoing transfer if it spent from it. A negative index throws a
   * {@code ZephyrError}.
   *
   * @param subaddressIndex is the subaddress index, null for any
   * @return this query for chaining
   */
  public ZephyrTransferQuery setSubaddressIndex(Integer subaddressIndex) {
    this.subaddressIndex = subaddressIndex;
    validate();
    return this;
  }

  /**
   * Returns the subaddress indices to match, see {@link #setSubaddressIndices(List)}.
   *
   * @return the subaddress indices, null for any
   */
  public List<Integer> getSubaddressIndices() {
    return subaddressIndices;
  }

  /**
   * Sets the subaddress indices to match: an incoming transfer matches if it was received at one of
   * them, an outgoing transfer if it spent from one of them. A negative index throws a
   * {@code ZephyrError}.
   *
   * @param subaddressIndices are the subaddress indices, null for any
   * @return this query for chaining
   */
  public ZephyrTransferQuery setSubaddressIndices(List<Integer> subaddressIndices) {
    this.subaddressIndices = subaddressIndices;
    validate();
    return this;
  }
  
  /**
   * Sets the subaddress indices to match, as {@link #setSubaddressIndices(List)} does.
   *
   * @param subaddressIndices are the subaddress indices
   * @return this query for chaining
   */
  public ZephyrTransferQuery setSubaddressIndices(Integer... subaddressIndices) {
    this.subaddressIndices = GenUtils.arrayToList(subaddressIndices);
    validate();
    return this;
  }

  /**
   * Returns the destinations set on this query, which are not a criterion, see
   * {@link #setDestinations(List)}.
   *
   * @return the destinations, null if none
   */
  public List<ZephyrDestination> getDestinations() {
    return destinations;
  }

  /**
   * Sets destinations on this query. They are not a criterion:
   * {@link #meetsCriteria(ZephyrTransfer)} ignores them. {@code ZephyrWalletFull} does not support
   * it: the core throws "monero_transfer_query destinations deserialization not implemented" for a
   * query that sets it (read in the core's code).
   *
   * @param destinations are the destinations
   * @return this query for chaining
   */
  public ZephyrTransferQuery setDestinations(List<ZephyrDestination> destinations) {
    this.destinations = destinations;
    return this;
  }

  /**
   * Returns whether a matching outgoing transfer must have destinations (true) or must have none
   * (false), see {@link #setHasDestinations(Boolean)}.
   *
   * @return the requirement, null for either
   */
  @JsonProperty("hasDestinations")
  public Boolean hasDestinations() {
    return hasDestinations;
  }

  /**
   * Sets whether a matching outgoing transfer must have destinations (true) or must have none
   * (false). In {@code ZephyrWalletFull} no incoming transfer matches once it is set, to either
   * value (read in the core's code); {@link #meetsCriteria(ZephyrTransfer)} leaves out incoming
   * transfers only for true.
   *
   * @param hasDestinations is the requirement, null for either
   * @return this query for chaining
   */
  public ZephyrTransferQuery setHasDestinations(Boolean hasDestinations) {
    this.hasDestinations = hasDestinations;
    return this;
  }

  @Override
  public boolean meetsCriteria(ZephyrTransfer transfer) {
    return meetsCriteria(transfer, true);
  }

  /**
   * Returns whether the given transfer meets this query, evaluated in Java.
   *
   * @param transfer is the transfer to test
   * @param queryParent is true to apply the transaction query to the transfer's transaction too
   * @return true if the transfer meets the query
   */
  protected boolean meetsCriteria(ZephyrTransfer transfer, boolean queryParent) {
    GenUtils.assertNotNull("transfer is null", transfer);
    
    // filter on common fields
    if (this.isIncoming() != null && this.isIncoming() != transfer.isIncoming()) return false;
    if (this.isOutgoing() != null && this.isOutgoing() != transfer.isOutgoing()) return false;
    if (this.getAmount() != null && this.getAmount().compareTo(transfer.getAmount()) != 0) return false;
    if (this.getAccountIndex() != null && !this.getAccountIndex().equals(transfer.getAccountIndex())) return false;
    
    // filter on incoming fields
    if (transfer instanceof ZephyrIncomingTransfer) {
      if (Boolean.TRUE.equals(this.hasDestinations())) return false;
      ZephyrIncomingTransfer inTransfer = (ZephyrIncomingTransfer) transfer;
      if (this.getAddress() != null && !this.getAddress().equals(inTransfer.getAddress())) return false;
      if (this.getAddresses() != null && !this.getAddresses().contains(inTransfer.getAddress())) return false;
      if (this.getSubaddressIndex() != null && !this.getSubaddressIndex().equals(inTransfer.getSubaddressIndex())) return false;
      if (this.getSubaddressIndices() != null && !this.getSubaddressIndices().contains(inTransfer.getSubaddressIndex())) return false;
    }

    // filter on outgoing fields
    else if (transfer instanceof ZephyrOutgoingTransfer) {
      ZephyrOutgoingTransfer outTransfer = (ZephyrOutgoingTransfer) transfer;
      
      // filter on addresses
      if (getAddress() != null && (outTransfer.getAddresses() == null || !outTransfer.getAddresses().contains(this.getAddress()))) return false;   // TODO: will filter all transfers if they don't contain addresses
      if (getAddresses() != null) {
        List<String> intersections = new ArrayList<String>(this.getAddresses());
        intersections.retainAll(outTransfer.getAddresses());
        if (intersections.isEmpty()) return false;  // must have overlapping addresses
      }
      
      // filter on subaddress indices
      if (getSubaddressIndex() != null && (outTransfer.getSubaddressIndices() == null || !outTransfer.getSubaddressIndices().contains(this.getSubaddressIndex()))) return false;
      if (getSubaddressIndices() != null) {
        List<Integer> intersections = new ArrayList<Integer>(this.getSubaddressIndices());
        intersections.retainAll(outTransfer.getSubaddressIndices());
        if (intersections.isEmpty()) return false;  // must have overlapping subaddress indices
      }
      
      // filter on having destinations
      if (hasDestinations() != null) {
        if (hasDestinations() && outTransfer.getDestinations() == null) return false;
        if (!hasDestinations() && outTransfer.getDestinations() != null) return false;
      }
      
      // filter on destinations TODO: start with test for this
//    if (this.getDestionations() != null && this.getDestionations() != transfer.getDestionations()) return false;
    }
    
    // otherwise invalid type
    else throw new RuntimeException("Transfer must be ZephyrIncomingTransfer or ZephyrOutgoingTransfer");
    
    // filter with tx filter
    if (queryParent && getTxQuery() != null && !getTxQuery().meetsCriteria(transfer.getTx(), false)) return false;
    return true;
  }
  
  private void validate() {
    if (subaddressIndex != null && subaddressIndex < 0) throw new ZephyrError("Subaddress index must be >= 0");
    if (subaddressIndices != null) for (Integer subaddressIdx : subaddressIndices) if (subaddressIdx < 0) throw new ZephyrError("Subaddress indices must be >= 0");
  }
  
  // ------------------- OVERRIDE CO-VARIANT RETURN TYPES ---------------------

  @Override
  public ZephyrTransferQuery setTx(ZephyrTxWallet tx) {
    super.setTx(tx);
    return this;
  }

  @Override
  public ZephyrTransferQuery setAmount(BigInteger amount) {
    super.setAmount(amount);
    return this;
  }

  @Override
  public ZephyrTransferQuery setAccountIndex(Integer accountIndex) {
    super.setAccountIndex(accountIndex);
    return this;
  }
}
