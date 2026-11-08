package zephyr.wallet.model;

import java.math.BigInteger;

/**
 * Results from checking a transaction key.
 */
public class ZephyrCheckTx extends ZephyrCheck {

  public Boolean inTxPool;
  public Long numConfirmations;
  public BigInteger receivedAmount;
  
  public Boolean inTxPool() {
    return inTxPool;
  }
  
  public void setInTxPool(Boolean inTxPool) {
    this.inTxPool = inTxPool;
  }
  
  public Long getNumConfirmations() {
    return numConfirmations;
  }
  
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
   * sender's outgoing destinations, each with its asset. Through {@code ZephyrWalletRpc} this is
   * {@code null} for now (the RPC client does not read the per-asset amounts yet). See the user
   * guide, {@code docs/guide/08-known-limitations.md}, section "checkTxKey returns amount 0".</p>
   * 
   * @return the received amount in atomic units, 0 for current transactions (see above)
   */
  public BigInteger getReceivedAmount() {
    return receivedAmount;
  }
  
  public void setReceivedAmount(BigInteger receivedAmount) {
    this.receivedAmount = receivedAmount;
  }
}
