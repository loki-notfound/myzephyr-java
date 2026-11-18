package zephyr.wallet.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumerates transaction priorities.
 */
public enum ZephyrTxPriority {
  /**
   * Lets the core choose the fee tier: the low tier when the pool has no backlog and the recent
   * blocks are not full, otherwise the normal tier (read in the core's code). See the user guide,
   * {@code docs/guide/07-sending.md}, section "Fees and priorities".
   */
  DEFAULT,
  /**
   * The low fee tier; inherited from monero-java; not tested against Zephyr.
   */
  UNIMPORTANT,
  /**
   * The normal fee tier; inherited from monero-java; not tested against Zephyr.
   */
  NORMAL,
  /**
   * A fee tier above the normal one; inherited from monero-java; not tested against Zephyr.
   */
  ELEVATED;
  
  @JsonValue
  @Override
  public String toString() {
    return String.valueOf(ordinal());
  }
}
