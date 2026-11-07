package zephyr.daemon.model;

import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.utils.GenUtils;

/**
 * The oracle prices a Zephyr block was built with, as the daemon serializes them in the block
 * header's <code>pricing_record</code>.
 * 
 * The values are in the daemon's fixed-point units (10^12 per unit, like amounts). Per field:
 * spot and moving average are the price of ZPH in USD; stable and reserve are the rates of the
 * stable asset ZSD and the reserve asset ZRS (each with its moving average); reserve ratio and
 * its moving average are the reserve ratio; yield price is the price of the yield asset; the
 * timestamp is the oracle's timestamp; the signature is the oracle's signature.
 * 
 * {@link zephyr.wallet.model.ZephyrReserveInfo} is the wallet-side view; this is the per-block record.
 */
public class ZephyrPricingRecord {

  private BigInteger spot;
  private BigInteger movingAverage;
  private BigInteger stable;
  private BigInteger stableMa;
  private BigInteger reserve;
  private BigInteger reserveMa;
  private BigInteger reserveRatio;
  private BigInteger reserveRatioMa;
  private BigInteger yieldPrice;
  private BigInteger timestamp;
  private String signature;
  
  /**
   * Constructs an empty pricing record.
   */
  public ZephyrPricingRecord() {
    super();
  }
  
  /**
   * Constructs a copy of the given pricing record.
   *
   * @param record is the record to copy
   */
  public ZephyrPricingRecord(ZephyrPricingRecord record) {
    this.spot = record.spot;
    this.movingAverage = record.movingAverage;
    this.stable = record.stable;
    this.stableMa = record.stableMa;
    this.reserve = record.reserve;
    this.reserveMa = record.reserveMa;
    this.reserveRatio = record.reserveRatio;
    this.reserveRatioMa = record.reserveRatioMa;
    this.yieldPrice = record.yieldPrice;
    this.timestamp = record.timestamp;
    this.signature = record.signature;
  }
  
  /**
   * Returns a copy of this pricing record.
   *
   * @return the copy
   */
  public ZephyrPricingRecord copy() {
    return new ZephyrPricingRecord(this);
  }
  
  /**
   * Returns the spot price of ZPH in USD.
   * 
   * @return the spot price of ZPH in USD
   */
  @JsonProperty("spot")
  public BigInteger getSpot() {
    return spot;
  }
  
  /**
   * Sets the spot price of ZPH in USD.
   * 
   * @param spot is the spot price of ZPH in USD to set
   * @return a reference to this record for chaining
   */
  @JsonProperty("spot")
  public ZephyrPricingRecord setSpot(BigInteger spot) {
    this.spot = spot;
    return this;
  }
  
  /**
   * Returns the moving average of the ZPH price in USD.
   * 
   * @return the moving average of the ZPH price in USD
   */
  @JsonProperty("moving_average")
  public BigInteger getMovingAverage() {
    return movingAverage;
  }
  
  /**
   * Sets the moving average of the ZPH price in USD.
   * 
   * @param movingAverage is the moving average of the ZPH price in USD to set
   * @return a reference to this record for chaining
   */
  @JsonProperty("moving_average")
  public ZephyrPricingRecord setMovingAverage(BigInteger movingAverage) {
    this.movingAverage = movingAverage;
    return this;
  }
  
  /**
   * Returns the rate of the stable asset ZSD.
   * 
   * @return the rate of the stable asset ZSD
   */
  @JsonProperty("stable")
  public BigInteger getStable() {
    return stable;
  }
  
  /**
   * Sets the rate of the stable asset ZSD.
   * 
   * @param stable is the rate of the stable asset ZSD to set
   * @return a reference to this record for chaining
   */
  @JsonProperty("stable")
  public ZephyrPricingRecord setStable(BigInteger stable) {
    this.stable = stable;
    return this;
  }
  
  /**
   * Returns the moving average of the rate of the stable asset ZSD.
   * 
   * @return the moving average of the rate of the stable asset ZSD
   */
  @JsonProperty("stable_ma")
  public BigInteger getStableMa() {
    return stableMa;
  }
  
  /**
   * Sets the moving average of the rate of the stable asset ZSD.
   * 
   * @param stableMa is the moving average of the rate of the stable asset ZSD to set
   * @return a reference to this record for chaining
   */
  @JsonProperty("stable_ma")
  public ZephyrPricingRecord setStableMa(BigInteger stableMa) {
    this.stableMa = stableMa;
    return this;
  }
  
  /**
   * Returns the rate of the reserve asset ZRS.
   * 
   * @return the rate of the reserve asset ZRS
   */
  @JsonProperty("reserve")
  public BigInteger getReserve() {
    return reserve;
  }
  
  /**
   * Sets the rate of the reserve asset ZRS.
   * 
   * @param reserve is the rate of the reserve asset ZRS to set
   * @return a reference to this record for chaining
   */
  @JsonProperty("reserve")
  public ZephyrPricingRecord setReserve(BigInteger reserve) {
    this.reserve = reserve;
    return this;
  }
  
  /**
   * Returns the moving average of the rate of the reserve asset ZRS.
   * 
   * @return the moving average of the rate of the reserve asset ZRS
   */
  @JsonProperty("reserve_ma")
  public BigInteger getReserveMa() {
    return reserveMa;
  }
  
  /**
   * Sets the moving average of the rate of the reserve asset ZRS.
   * 
   * @param reserveMa is the moving average of the rate of the reserve asset ZRS to set
   * @return a reference to this record for chaining
   */
  @JsonProperty("reserve_ma")
  public ZephyrPricingRecord setReserveMa(BigInteger reserveMa) {
    this.reserveMa = reserveMa;
    return this;
  }
  
  /**
   * Returns the reserve ratio.
   * 
   * @return the reserve ratio
   */
  @JsonProperty("reserve_ratio")
  public BigInteger getReserveRatio() {
    return reserveRatio;
  }
  
  /**
   * Sets the reserve ratio.
   * 
   * @param reserveRatio is the reserve ratio to set
   * @return a reference to this record for chaining
   */
  @JsonProperty("reserve_ratio")
  public ZephyrPricingRecord setReserveRatio(BigInteger reserveRatio) {
    this.reserveRatio = reserveRatio;
    return this;
  }
  
  /**
   * Returns the moving average of the reserve ratio.
   * 
   * @return the moving average of the reserve ratio
   */
  @JsonProperty("reserve_ratio_ma")
  public BigInteger getReserveRatioMa() {
    return reserveRatioMa;
  }
  
  /**
   * Sets the moving average of the reserve ratio.
   * 
   * @param reserveRatioMa is the moving average of the reserve ratio to set
   * @return a reference to this record for chaining
   */
  @JsonProperty("reserve_ratio_ma")
  public ZephyrPricingRecord setReserveRatioMa(BigInteger reserveRatioMa) {
    this.reserveRatioMa = reserveRatioMa;
    return this;
  }
  
  /**
   * Returns the price of the yield asset ZYS.
   * 
   * @return the price of the yield asset ZYS
   */
  @JsonProperty("yield_price")
  public BigInteger getYieldPrice() {
    return yieldPrice;
  }
  
  /**
   * Sets the price of the yield asset ZYS.
   * 
   * @param yieldPrice is the price of the yield asset ZYS to set
   * @return a reference to this record for chaining
   */
  @JsonProperty("yield_price")
  public ZephyrPricingRecord setYieldPrice(BigInteger yieldPrice) {
    this.yieldPrice = yieldPrice;
    return this;
  }
  
  /**
   * Returns the timestamp of the oracle record.
   * 
   * @return the timestamp of the oracle record
   */
  @JsonProperty("timestamp")
  public BigInteger getTimestamp() {
    return timestamp;
  }
  
  /**
   * Sets the timestamp of the oracle record.
   * 
   * @param timestamp is the timestamp of the oracle record to set
   * @return a reference to this record for chaining
   */
  @JsonProperty("timestamp")
  public ZephyrPricingRecord setTimestamp(BigInteger timestamp) {
    this.timestamp = timestamp;
    return this;
  }
  
  /**
   * Returns the oracle's signature of the record, as the daemon serializes it.
   * 
   * @return the oracle's signature of the record, as the daemon serializes it
   */
  @JsonProperty("signature")
  public String getSignature() {
    return signature;
  }
  
  /**
   * Sets the oracle's signature of the record, as the daemon serializes it.
   * 
   * @param signature is the oracle's signature of the record, as the daemon serializes it to set
   * @return a reference to this record for chaining
   */
  @JsonProperty("signature")
  public ZephyrPricingRecord setSignature(String signature) {
    this.signature = signature;
    return this;
  }
  
  /**
   * Merges another record of the same block into this one: a field set in only one of them is
   * taken, a field set in both must be equal.
   *
   * @param record is the record to merge into this one
   * @return this record, merged
   * @throws zephyr.common.ZephyrError if a field is set in both records with different values
   */
  public ZephyrPricingRecord merge(ZephyrPricingRecord record) {
    GenUtils.assertNotNull(record);
    if (this == record) return this;
    this.setSpot(GenUtils.reconcile(this.getSpot(), record.getSpot()));
    this.setMovingAverage(GenUtils.reconcile(this.getMovingAverage(), record.getMovingAverage()));
    this.setStable(GenUtils.reconcile(this.getStable(), record.getStable()));
    this.setStableMa(GenUtils.reconcile(this.getStableMa(), record.getStableMa()));
    this.setReserve(GenUtils.reconcile(this.getReserve(), record.getReserve()));
    this.setReserveMa(GenUtils.reconcile(this.getReserveMa(), record.getReserveMa()));
    this.setReserveRatio(GenUtils.reconcile(this.getReserveRatio(), record.getReserveRatio()));
    this.setReserveRatioMa(GenUtils.reconcile(this.getReserveRatioMa(), record.getReserveRatioMa()));
    this.setYieldPrice(GenUtils.reconcile(this.getYieldPrice(), record.getYieldPrice()));
    this.setTimestamp(GenUtils.reconcile(this.getTimestamp(), record.getTimestamp()));
    this.setSignature(GenUtils.reconcile(this.getSignature(), record.getSignature()));
    return this;
  }
  
  public String toString() {
    return toString(0);
  }
  
  /**
   * Returns one line per field.
   *
   * @param indent indents the lines
   * @return the human-readable string
   */
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(GenUtils.kvLine("Spot", getSpot(), indent));
    sb.append(GenUtils.kvLine("Moving average", getMovingAverage(), indent));
    sb.append(GenUtils.kvLine("Stable", getStable(), indent));
    sb.append(GenUtils.kvLine("Stable moving average", getStableMa(), indent));
    sb.append(GenUtils.kvLine("Reserve", getReserve(), indent));
    sb.append(GenUtils.kvLine("Reserve moving average", getReserveMa(), indent));
    sb.append(GenUtils.kvLine("Reserve ratio", getReserveRatio(), indent));
    sb.append(GenUtils.kvLine("Reserve ratio moving average", getReserveRatioMa(), indent));
    sb.append(GenUtils.kvLine("Yield price", getYieldPrice(), indent));
    sb.append(GenUtils.kvLine("Timestamp", getTimestamp(), indent));
    sb.append(GenUtils.kvLine("Signature", getSignature(), indent));
    String str = sb.toString();
    if (str.isEmpty()) return "";
    return str.charAt(str.length() - 1) == '\n' ? str.substring(0, str.length() - 1) : str; // strip newline
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((spot == null) ? 0 : spot.hashCode());
    result = prime * result + ((movingAverage == null) ? 0 : movingAverage.hashCode());
    result = prime * result + ((stable == null) ? 0 : stable.hashCode());
    result = prime * result + ((stableMa == null) ? 0 : stableMa.hashCode());
    result = prime * result + ((reserve == null) ? 0 : reserve.hashCode());
    result = prime * result + ((reserveMa == null) ? 0 : reserveMa.hashCode());
    result = prime * result + ((reserveRatio == null) ? 0 : reserveRatio.hashCode());
    result = prime * result + ((reserveRatioMa == null) ? 0 : reserveRatioMa.hashCode());
    result = prime * result + ((yieldPrice == null) ? 0 : yieldPrice.hashCode());
    result = prime * result + ((timestamp == null) ? 0 : timestamp.hashCode());
    result = prime * result + ((signature == null) ? 0 : signature.hashCode());
    return result;
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrPricingRecord other = (ZephyrPricingRecord) obj;
    if (spot == null) {
      if (other.spot != null) return false;
    } else if (!spot.equals(other.spot)) return false;
    if (movingAverage == null) {
      if (other.movingAverage != null) return false;
    } else if (!movingAverage.equals(other.movingAverage)) return false;
    if (stable == null) {
      if (other.stable != null) return false;
    } else if (!stable.equals(other.stable)) return false;
    if (stableMa == null) {
      if (other.stableMa != null) return false;
    } else if (!stableMa.equals(other.stableMa)) return false;
    if (reserve == null) {
      if (other.reserve != null) return false;
    } else if (!reserve.equals(other.reserve)) return false;
    if (reserveMa == null) {
      if (other.reserveMa != null) return false;
    } else if (!reserveMa.equals(other.reserveMa)) return false;
    if (reserveRatio == null) {
      if (other.reserveRatio != null) return false;
    } else if (!reserveRatio.equals(other.reserveRatio)) return false;
    if (reserveRatioMa == null) {
      if (other.reserveRatioMa != null) return false;
    } else if (!reserveRatioMa.equals(other.reserveRatioMa)) return false;
    if (yieldPrice == null) {
      if (other.yieldPrice != null) return false;
    } else if (!yieldPrice.equals(other.yieldPrice)) return false;
    if (timestamp == null) {
      if (other.timestamp != null) return false;
    } else if (!timestamp.equals(other.timestamp)) return false;
    if (signature == null) {
      if (other.signature != null) return false;
    } else if (!signature.equals(other.signature)) return false;
    return true;
  }
}
