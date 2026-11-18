package zephyr.wallet.model;

import java.math.BigInteger;

/**
 * Results from checking a transaction key.
 */
public class ZephyrCheckTx extends ZephyrCheck {

  /**
   * Whether the checked transaction is in the pool; see {@link #inTxPool()}.
   */
  public Boolean inTxPool;
  /**
   * The confirmations of the checked transaction; see {@link #getNumConfirmations()}.
   */
  public Long numConfirmations;
  /**
   * The received amount; see {@link #getReceivedAmount()}.
   */
  public BigInteger receivedAmount;
  
  /**
   * Creates an empty transaction check result.
   */
  public ZephyrCheckTx() { }
  
  /**
   * Returns whether the checked transaction is in the node's pool, as the node reports it.
   *
   * @return true if in the pool; null from a {@code checkTxProof} whose signature check failed
   */
  public Boolean inTxPool() {
    return inTxPool;
  }
  
  /**
   * Sets whether the checked transaction is in the pool.
   *
   * @param inTxPool is true if in the pool
   */
  public void setInTxPool(Boolean inTxPool) {
    this.inTxPool = inTxPool;
  }
  
  /**
   * Returns the number of confirmations of the checked transaction, as the node reports it; 0 in
   * the pool.
   *
   * @return the number of confirmations; null from a {@code checkTxProof} whose signature check
   * failed
   */
  public Long getNumConfirmations() {
    return numConfirmations;
  }
  
  /**
   * Sets the number of confirmations.
   *
   * @param numConfirmations is the number of confirmations
   */
  public void setNumConfirmations(Long numConfirmations) {
    this.numConfirmations = numConfirmations;
  }
  
  /**
   * Returns the amount the checked transaction paid to the address.
   * 
   * <p>Zephyr: in the full wallet ({@code ZephyrWalletFull}) this is 0 for every current
   * transaction. The core reads the amount of the asset {@code ZEPH} only, and since hard fork 11
   * (height 536,000) no output is {@code ZEPH}; {@link #isGood()} and the confirmations are right.
   * Read the amount from the wallet's {@code getTxs(hash)}: the receiver's incoming transfers or the
   * sender's outgoing destinations, each with its asset. See the user guide,
   * {@code docs/guide/06-history.md}, section "checkTxKey returns amount 0".</p>
   * 
   * @return the received amount in atomic units, 0 for current transactions (see above)
   */
  public BigInteger getReceivedAmount() {
    return receivedAmount;
  }
  
  /**
   * Sets the received amount.
   *
   * @param receivedAmount is the amount in atomic units
   */
  public void setReceivedAmount(BigInteger receivedAmount) {
    this.receivedAmount = receivedAmount;
  }
}
