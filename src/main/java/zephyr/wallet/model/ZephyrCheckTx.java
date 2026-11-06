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
  
  public BigInteger getReceivedAmount() {
    return receivedAmount;
  }
  
  public void setReceivedAmount(BigInteger receivedAmount) {
    this.receivedAmount = receivedAmount;
  }
}
