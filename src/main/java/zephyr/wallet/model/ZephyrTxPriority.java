package zephyr.wallet.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumerates transaction priorities.
 */
public enum ZephyrTxPriority {
  DEFAULT,
  UNIMPORTANT,
  NORMAL,
  ELEVATED;
  
  @JsonValue
  @Override
  public String toString() {
    return String.valueOf(ordinal());
  }
}
