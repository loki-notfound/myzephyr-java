package zephyr.wallet.model;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import zephyr.lib.utils.GenUtils;

/**
 * Balances per asset: a map from asset name to amount in atomic units.
 *
 * The JSON form is the plain object {asset: amount} that the core emits. The asset is an open
 * string (see {@link ZephyrAssets}).
 */
public class ZephyrBalance {

  private final Map<String, BigInteger> balances;

  /**
   * Constructs an empty balance.
   */
  public ZephyrBalance() {
    this.balances = new TreeMap<String, BigInteger>();
  }

  /**
   * Constructs a balance from a map of asset to amount.
   *
   * @param balances maps asset name to amount in atomic units (copied)
   */
  @JsonCreator
  public ZephyrBalance(Map<String, BigInteger> balances) {
    this.balances = balances == null ? new TreeMap<String, BigInteger>() : new TreeMap<String, BigInteger>(balances);
  }

  /**
   * Returns the amount of one asset.
   *
   * @param assetType is the asset name
   * @return the amount in atomic units, {@link BigInteger#ZERO} when the asset is absent, never null
   */
  public BigInteger get(String assetType) {
    BigInteger amount = balances.get(assetType);
    return amount == null ? BigInteger.ZERO : amount;
  }

  /**
   * Sets the amount of one asset.
   *
   * @param assetType is the asset name
   * @param amount is the amount in atomic units
   * @return this balance for chaining
   */
  public ZephyrBalance set(String assetType, BigInteger amount) {
    balances.put(assetType, amount);
    return this;
  }

  /**
   * Returns the asset names present in this balance.
   *
   * @return the asset names, sorted (unmodifiable)
   */
  public Set<String> getAssetTypes() {
    return Collections.unmodifiableSet(balances.keySet());
  }

  /**
   * Returns the balances as a map; this is also the JSON form.
   *
   * @return the map of asset name to amount, sorted by asset (unmodifiable)
   */
  @JsonValue
  public Map<String, BigInteger> toMap() {
    return Collections.unmodifiableMap(balances);
  }

  /**
   * Indicates whether this balance has no asset entries.
   *
   * @return true if there are no asset entries, false otherwise
   */
  public boolean isEmpty() {
    return balances.isEmpty();
  }

  /**
   * Returns a copy of this balance.
   *
   * @return the copy
   */
  public ZephyrBalance copy() {
    return new ZephyrBalance(balances);
  }

  public String toString() {
    return toString(0);
  }

  /**
   * Returns one line per asset.
   *
   * @param indent indents the lines
   * @return the human-friendly string
   */
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, BigInteger> entry : balances.entrySet()) {
      sb.append(GenUtils.kvLine(entry.getKey(), entry.getValue(), indent));
    }
    String str = sb.toString();
    return str.isEmpty() ? str : str.substring(0, str.length() - 1);  // strip last newline
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((balances == null) ? 0 : balances.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrBalance other = (ZephyrBalance) obj;
    if (balances == null) {
      if (other.balances != null) return false;
    } else if (!balances.equals(other.balances)) return false;
    return true;
  }
}
