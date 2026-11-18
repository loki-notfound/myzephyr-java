package zephyr.wallet.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Base class for results from checking a transaction or reserve proof.
 */
public class ZephyrCheck {

  /**
   * Whether the check succeeded; see {@link #isGood()}.
   */
  public Boolean isGood;

  /**
   * Creates an empty check result.
   */
  public ZephyrCheck() { }

  /**
   * Returns whether the check succeeded. For {@code checkTxProof} and {@code checkReserveProof} it
   * is the result of the signature check. For {@code checkTxKey} in {@code ZephyrWalletFull} it is
   * always true once the call returns: the core sets it before it looks at the outputs, and throws
   * instead for a malformed hash, key or address (read in the core's code). It does not tell
   * whether the transaction paid the address.
   *
   * @return true if the check succeeded
   */
  @JsonProperty("isGood")
  public Boolean isGood() {
    return isGood;
  }

  /**
   * Sets whether the check succeeded.
   *
   * @param isGood is true if the check succeeded
   */
  public void setIsGood(Boolean isGood) {
    this.isGood = isGood;
  }
}
