package zephyr.wallet.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.utils.GenUtils;
import zephyr.lib.utils.JsonUtils;
import zephyr.common.ZephyrError;

/**
 * Configures a transaction to send, sweep, or create a payment URI.
 */
public class ZephyrTxConfig {

  private List<ZephyrDestination> destinations;
  private String paymentId;
  private ZephyrTxPriority priority;
  private BigInteger fee;
  private Integer accountIndex;
  private List<Integer> subaddressIndices;
  private Long unlockHeight;
  private Boolean canSplit;
  private Boolean relay;
  private String note;
  private String recipientName;
  private BigInteger belowAmount;
  private Boolean sweepEachSubaddress;
  private String keyImage;
  private String sourceCurrency;
  private String destinationCurrency;
  
  /**
   * Creates an empty configuration. A send needs at least a destination, the account to send from
   * and the currency pair ({@link #setSourceCurrency(String)},
   * {@link #setDestinationCurrency(String)}). See the user guide, {@code docs/guide/07-sending.md},
   * section "The currency pair on every send".
   */
  public ZephyrTxConfig() {
    // necessary for deserialization
  }
  
  ZephyrTxConfig(final ZephyrTxConfig config) {
    if (config.destinations != null) {
      this.destinations = new ArrayList<ZephyrDestination>();
      for (ZephyrDestination destination : config.getDestinations()) this.destinations.add(destination.copy());
    }
    this.paymentId = config.paymentId;
    this.priority = config.priority;
    this.fee = config.fee;
    this.accountIndex = config.accountIndex;
    if (config.subaddressIndices != null) this.subaddressIndices = new ArrayList<Integer>(config.subaddressIndices);
    this.unlockHeight = config.unlockHeight;
    this.canSplit = config.canSplit;
    this.relay = config.relay;
    this.note = config.note;
    this.recipientName = config.recipientName;
    this.belowAmount = config.belowAmount;
    this.sweepEachSubaddress = config.sweepEachSubaddress;
    this.keyImage = config.keyImage;
    this.sourceCurrency = config.sourceCurrency;
    this.destinationCurrency = config.destinationCurrency;
  }
  
  /**
   * Returns a copy of this configuration with copies of its destinations.
   *
   * @return the copy
   */
  public ZephyrTxConfig copy() {
    return new ZephyrTxConfig(this);
  }
  
  /**
   * Set the address of a single-destination configuration.
   * 
   * @param address - the address to set for the single destination
   * @return ZephyrTxConfig this configuration for chaining
   */
  public ZephyrTxConfig setAddress(String address) {
    if (this.destinations != null && this.destinations.size() > 1) throw new ZephyrError("Cannot set address because ZephyrTxConfig already has multiple destinations");
    if (this.destinations == null || this.destinations.isEmpty()) addDestination(new ZephyrDestination(address));
    else this.destinations.get(0).setAddress(address);
    return this;
  }
  
  /**
   * Get the address of a single-destination configuration.
   * 
   * @return String the address of the single destination
   */
  @JsonIgnore
  public String getAddress() {
    if (this.destinations == null || this.destinations.size() != 1) throw new ZephyrError("Cannot get address because ZephyrTxConfig does not have exactly one destination");
    return this.destinations.get(0).getAddress();
  }
  
  /**
   * Set the amount of a single-destination configuration.
   * 
   * @param amount - the amount to set for the single destination
   * @return ZephyrTxConfig this configuration for chaining
   */
  public ZephyrTxConfig setAmount(BigInteger amount) {
    if (this.destinations != null && this.destinations.size() > 1) throw new ZephyrError("Cannot set amount because ZephyrTxConfig already has multiple destinations");
    if (this.destinations == null || this.destinations.isEmpty()) addDestination(new ZephyrDestination(null, amount));
    else this.destinations.get(0).setAmount(amount);
    return this;
  }
  
  /**
   * Set the amount of a single-destination configuration.
   * 
   * @param amount - the amount to set for the single destination in atomic units as a string
   * @return ZephyrTxConfig this configuration for chaining
   */
  public ZephyrTxConfig setAmount(String amount) {
    return setAmount(new BigInteger(amount));
  }
  
  /**
   * Get the amount of a single-destination configuration.
   * 
   * @return BigInteger the amount of the single destination
   */
  @JsonIgnore
  public BigInteger getAmount() {
    if (this.destinations == null || this.destinations.size() != 1) throw new ZephyrError("Cannot get amount because ZephyrTxConfig does not have exactly one destination");
    return this.destinations.get(0).getAmount();
  }
  
  /**
   * Adds a destination. Several destinations in one transaction are inherited from monero-java; not
   * tested against Zephyr.
   *
   * @param address is the recipient's address
   * @param amount is the amount in atomic units of the source currency
   * @return this configuration for chaining
   */
  public ZephyrTxConfig addDestination(String address, BigInteger amount) {
    return addDestination(new ZephyrDestination(address, amount));
  }
  
  /**
   * Adds a destination. Its asset type does not select anything: the currency pair of this
   * configuration holds for every destination. Several destinations in one transaction are
   * inherited from monero-java; not tested against Zephyr.
   *
   * @param destination is the destination, its amount in atomic units of the source currency
   * @return this configuration for chaining
   */
  public ZephyrTxConfig addDestination(ZephyrDestination destination) {
    if (this.destinations == null) this.destinations = new ArrayList<ZephyrDestination>();
    this.destinations.add(destination);
    return this;
  }
  
  /**
   * Returns the destinations; {@link #setAddress(String)} and {@link #setAmount(BigInteger)} edit
   * the first one.
   *
   * @return the destinations, null if none was set
   */
  public List<ZephyrDestination> getDestinations() {
    return destinations;
  }
  
  /**
   * Replaces all destinations. Several destinations in one transaction are inherited from
   * monero-java; not tested against Zephyr.
   *
   * @param destinations are the destinations, their amounts in atomic units of the source currency
   * @return this configuration for chaining
   */
  @JsonProperty("destinations")
  public ZephyrTxConfig setDestinations(List<ZephyrDestination> destinations) {
    this.destinations = destinations;
    return this;
  }
  
  /**
   * Replaces all destinations, as {@link #setDestinations(List)} does.
   *
   * @param destinations are the destinations
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setDestinations(ZephyrDestination... destinations) {
    this.destinations = GenUtils.arrayToList(destinations);
    return this;
  }
  
  /**
   * Returns the payment ID, see {@link #setPaymentId(String)}.
   *
   * @return the payment ID, null if none
   */
  public String getPaymentId() {
    return paymentId;
  }
  
  /**
   * Sets a payment ID. In {@code ZephyrWalletFull} the core rejects every standalone payment ID:
   * {@code createTxs} throws "Standalone payment IDs are obsolete. Use subaddresses or integrated
   * addresses instead", and the sweeps fail on the same check (read in the core's code). Send to an
   * integrated address instead. A payment URI ({@code getPaymentUri}) carries it.
   *
   * @param paymentId is the payment ID as hex
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setPaymentId(String paymentId) {
    this.paymentId = paymentId;
    return this;
  }
  
  /**
   * Returns the fee priority.
   *
   * @return the priority, null for the default
   */
  public ZephyrTxPriority getPriority() {
    return priority;
  }
  
  /**
   * Sets the fee priority. Null or {@link ZephyrTxPriority#DEFAULT} lets the core choose; the other
   * priorities are inherited from monero-java; not tested against Zephyr. See the user guide,
   * {@code docs/guide/07-sending.md}, section "Fees and priorities".
   *
   * @param priority is the priority, null for the default
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setPriority(ZephyrTxPriority priority) {
    this.priority = priority;
    return this;
  }
  
  /**
   * Returns the fee set on this configuration, see {@link #setFee(BigInteger)}.
   *
   * @return the fee, null if none
   */
  public BigInteger getFee() {
    return fee;
  }
  
  /**
   * Sets a fee. {@code ZephyrWalletFull} does not use it: the core never reads it and computes the
   * fee itself (read in the core's code). The fee of a built transaction is its {@code getFee()}.
   *
   * @param fee is the fee in atomic units
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setFee(BigInteger fee) {
    this.fee = fee;
    return this;
  }
  
  /**
   * Returns the index of the account to send from.
   *
   * @return the account index, null if not set
   */
  public Integer getAccountIndex() {
    return accountIndex;
  }
  
  /**
   * Sets the index of the account to send from. {@code createTxs} requires it ("Must specify
   * account index to send from"). {@code sweepUnlocked} without it sweeps every account that has an
   * unlocked balance in the source asset, so always set it. See the user guide,
   * {@code docs/guide/07-sending.md}, section "Sweeping".
   *
   * @param accountIndex is the account index
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setAccountIndex(Integer accountIndex) {
    this.accountIndex = accountIndex;
    return this;
  }
  
  /**
   * Returns the subaddresses of the account to spend from.
   *
   * @return the subaddress indices, null to let the core choose within the whole account
   */
  public List<Integer> getSubaddressIndices() {
    return subaddressIndices;
  }
  
  /**
   * Sets one subaddress of the account to spend from, as a list of one.
   *
   * @param subaddressIndex is the subaddress index
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setSubaddressIndex(int subaddressIndex) {
    setSubaddressIndices(subaddressIndex);
    return this;
  }
  
  /**
   * Sets the subaddresses of the account to spend from.
   *
   * @param subaddressIndices are the subaddress indices, null to let the core choose within the
   * whole account
   * @return this configuration for chaining
   */
  @JsonProperty("subaddressIndices")
  public ZephyrTxConfig setSubaddressIndices(List<Integer> subaddressIndices) {
    this.subaddressIndices = subaddressIndices;
    return this;
  }
  
  /**
   * Sets the subaddresses of the account to spend from, as {@link #setSubaddressIndices(List)}
   * does.
   *
   * @param subaddressIndices are the subaddress indices
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setSubaddressIndices(Integer... subaddressIndices) {
    this.subaddressIndices = GenUtils.arrayToList(subaddressIndices);
    return this;
  }
  
  /**
   * Get the minimum height for the transaction to unlock.  The transaction
   * will unlock on the maximum of the returned unlock height and the
   * confirmation height + 10.
   * 
   * @return the minimum height for the transaction to unlock
   */
  public Long getUnlockHeight() {
    return unlockHeight;
  }

  /**
   * Set the minimum height for the transaction to unlock.  The transaction
   * will unlock on the maximum of the given unlock height and the confirmation
   * height + 10.
   * 
   * @param unlockHeight the minimum height for the transaction to unlock
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setUnlockHeight(Long unlockHeight) {
    this.unlockHeight = unlockHeight;
    return this;
  }

  /**
   * Returns whether the core may split the send into several transactions.
   *
   * @return the setting, null if not set (splitting allowed)
   */
  public Boolean getCanSplit() {
    return canSplit;
  }
  
  /**
   * Sets whether the core may split the send into several transactions. With false,
   * {@code createTxs} throws when one transaction is not enough; {@code createTx} sets false
   * itself. Null allows splitting. This setting is inherited from monero-java; not tested against
   * Zephyr.
   *
   * @param canSplit is false to forbid splitting
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setCanSplit(Boolean canSplit) {
    this.canSplit = canSplit;
    return this;
  }
  
  /**
   * Returns whether the transactions are relayed when they are built.
   *
   * @return the setting, null if not set (not relayed)
   */
  public Boolean getRelay() {
    return relay;
  }
  
  /**
   * Sets whether {@code createTxs} and the sweeps relay the transactions right away. Null or false
   * builds them without relaying, so the app can show the fee and then call {@code relayTxs}; true
   * builds and relays in one call, without a review. See the user guide,
   * {@code docs/guide/07-sending.md}, section "Create, then relay".
   *
   * @param relay is true to relay at once
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setRelay(Boolean relay) {
    this.relay = relay;
    return this;
  }
  
  /**
   * Returns the note of a payment URI, see {@link #setNote(String)}.
   *
   * @return the note, null if none
   */
  public String getNote() {
    return note;
  }
  
  /**
   * Sets the note of a payment URI ({@code getPaymentUri}; {@code parsePaymentUri} reads it back).
   * {@code createTxs} does not store it as a transaction note: the core reads it only for payment
   * URIs (read in the core's code). Use {@code setTxNote} after the send.
   *
   * @param note is the note
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setNote(String note) {
    this.note = note;
    return this;
  }
  
  /**
   * Returns the recipient name of a payment URI.
   *
   * @return the recipient name, null if none
   */
  public String getRecipientName() {
    return recipientName;
  }
  
  /**
   * Sets the recipient name of a payment URI ({@code getPaymentUri}; {@code parsePaymentUri} reads
   * it back). The core does not use it when sending (read in the core's code).
   *
   * @param recipientName is the recipient name
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setRecipientName(String recipientName) {
    this.recipientName = recipientName;
    return this;
  }
  
  /**
   * Returns the bound of a sweep, see {@link #setBelowAmount(BigInteger)}.
   *
   * @return the bound, null for none
   */
  public BigInteger getBelowAmount() {
    return belowAmount;
  }
  
  /**
   * Sets a bound for {@code sweepUnlocked}: only unlocked outputs with an amount strictly below it
   * are swept (read in the core's code).
   *
   * @param belowAmount is the bound in atomic units of the source asset, null or 0 for no bound
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setBelowAmount(BigInteger belowAmount) {
    this.belowAmount = belowAmount;
    return this;
  }
  
  /**
   * Returns the sweep-each-subaddress setting, see {@link #setSweepEachSubaddress(Boolean)}.
   *
   * @return the setting, null if not set
   */
  public Boolean getSweepEachSubaddress() {
    return sweepEachSubaddress;
  }
  
  /**
   * Sets whether {@code sweepUnlocked} sweeps each subaddress in transactions of its own.
   * {@code ZephyrWalletFull} ignores it: the core sets it to false before it sweeps (read in the
   * core's code).
   *
   * @param sweepEachSubaddress is true to sweep each subaddress on its own
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setSweepEachSubaddress(Boolean sweepEachSubaddress) {
    this.sweepEachSubaddress = sweepEachSubaddress;
    return this;
  }
  
  /**
   * Returns the key image of the output to sweep, see {@link #setKeyImage(String)}.
   *
   * @return the key image, null if none
   */
  public String getKeyImage() {
    return keyImage;
  }
  
  /**
   * Sets the key image of the output that {@code sweepOutput} sweeps; {@code sweepUnlocked} refuses
   * it. {@code sweepOutput} validates with {@code ZEPH} and is most likely unusable on mainnet. See
   * the user guide, {@code docs/guide/07-sending.md}, section "Sweeping".
   *
   * @param keyImage is the key image as hex
   * @return this configuration for chaining
   */
  public ZephyrTxConfig setKeyImage(String keyImage) {
    this.keyImage = keyImage;
    return this;
  }
  
  /**
   * Get the asset that the transaction spends.
   * 
   * @return String the source currency (see {@link ZephyrAssets})
   */
  public String getSourceCurrency() {
    return sourceCurrency;
  }
  
  /**
   * Set the asset that the transaction spends.
   * 
   * Required for every send, together with the destination currency: a plain transfer sets
   * both to the same asset, a conversion (mint or redeem) sets the pair of its transaction type,
   * for example ZPH to ZSD to mint the stable asset. The pair holds for the whole transaction and
   * all its destinations. No check is made here; the core and the chain decide which pairs are
   * valid.
   * 
   * @param sourceCurrency is the asset that the transaction spends (see {@link ZephyrAssets})
   * @return ZephyrTxConfig this configuration for chaining
   */
  public ZephyrTxConfig setSourceCurrency(String sourceCurrency) {
    this.sourceCurrency = sourceCurrency;
    return this;
  }
  
  /**
   * Get the asset that the destinations receive.
   * 
   * @return String the destination currency (see {@link ZephyrAssets})
   */
  public String getDestinationCurrency() {
    return destinationCurrency;
  }
  
  /**
   * Set the asset that the destinations receive.
   * 
   * Required for every send, together with the source currency: a plain transfer sets both to
   * the same asset, a conversion (mint or redeem) sets the pair of its transaction type, for
   * example ZPH to ZSD to mint the stable asset. The pair holds for the whole transaction and all
   * its destinations; the asset type of a destination does not override it. No check is made
   * here; the core and the chain decide which pairs are valid.
   * 
   * @param destinationCurrency is the asset that the destinations receive (see {@link ZephyrAssets})
   * @return ZephyrTxConfig this configuration for chaining
   */
  public ZephyrTxConfig setDestinationCurrency(String destinationCurrency) {
    this.destinationCurrency = destinationCurrency;
    return this;
  }
  
  @Override
  public String toString() {
    return JsonUtils.serialize(this);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((accountIndex == null) ? 0 : accountIndex.hashCode());
    result = prime * result + ((belowAmount == null) ? 0 : belowAmount.hashCode());
    result = prime * result + ((canSplit == null) ? 0 : canSplit.hashCode());
    result = prime * result + ((destinationCurrency == null) ? 0 : destinationCurrency.hashCode());
    result = prime * result + ((destinations == null) ? 0 : destinations.hashCode());
    result = prime * result + ((relay == null) ? 0 : relay.hashCode());
    result = prime * result + ((fee == null) ? 0 : fee.hashCode());
    result = prime * result + ((keyImage == null) ? 0 : keyImage.hashCode());
    result = prime * result + ((note == null) ? 0 : note.hashCode());
    result = prime * result + ((paymentId == null) ? 0 : paymentId.hashCode());
    result = prime * result + ((priority == null) ? 0 : priority.hashCode());
    result = prime * result + ((recipientName == null) ? 0 : recipientName.hashCode());
    result = prime * result + ((sourceCurrency == null) ? 0 : sourceCurrency.hashCode());
    result = prime * result + ((subaddressIndices == null) ? 0 : subaddressIndices.hashCode());
    result = prime * result + ((sweepEachSubaddress == null) ? 0 : sweepEachSubaddress.hashCode());
    result = prime * result + ((unlockHeight == null) ? 0 : unlockHeight.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrTxConfig other = (ZephyrTxConfig) obj;
    if (accountIndex == null) {
      if (other.accountIndex != null) return false;
    } else if (!accountIndex.equals(other.accountIndex)) return false;
    if (belowAmount == null) {
      if (other.belowAmount != null) return false;
    } else if (!belowAmount.equals(other.belowAmount)) return false;
    if (canSplit == null) {
      if (other.canSplit != null) return false;
    } else if (!canSplit.equals(other.canSplit)) return false;
    if (destinationCurrency == null) {
      if (other.destinationCurrency != null) return false;
    } else if (!destinationCurrency.equals(other.destinationCurrency)) return false;
    if (destinations == null) {
      if (other.destinations != null) return false;
    } else if (!destinations.equals(other.destinations)) return false;
    if (relay == null) {
      if (other.relay != null) return false;
    } else if (!relay.equals(other.relay)) return false;
    if (fee == null) {
      if (other.fee != null) return false;
    } else if (!fee.equals(other.fee)) return false;
    if (keyImage == null) {
      if (other.keyImage != null) return false;
    } else if (!keyImage.equals(other.keyImage)) return false;
    if (note == null) {
      if (other.note != null) return false;
    } else if (!note.equals(other.note)) return false;
    if (paymentId == null) {
      if (other.paymentId != null) return false;
    } else if (!paymentId.equals(other.paymentId)) return false;
    if (priority != other.priority) return false;
    if (recipientName == null) {
      if (other.recipientName != null) return false;
    } else if (!recipientName.equals(other.recipientName)) return false;
    if (sourceCurrency == null) {
      if (other.sourceCurrency != null) return false;
    } else if (!sourceCurrency.equals(other.sourceCurrency)) return false;
    if (subaddressIndices == null) {
      if (other.subaddressIndices != null) return false;
    } else if (!subaddressIndices.equals(other.subaddressIndices)) return false;
    if (sweepEachSubaddress == null) {
      if (other.sweepEachSubaddress != null) return false;
    } else if (!sweepEachSubaddress.equals(other.sweepEachSubaddress)) return false;
    if (unlockHeight == null) {
      if (other.unlockHeight != null) return false;
    } else if (!unlockHeight.equals(other.unlockHeight)) return false;
    return true;
  }
}
