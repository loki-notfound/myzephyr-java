package zephyr.wallet.model;

/**
 * Zephyr integrated address model.
 */
public class ZephyrIntegratedAddress {

  private String standardAddress;
  private String paymentId;
  private String integratedAddress;
  
  /**
   * Creates an empty integrated address model.
   */
  public ZephyrIntegratedAddress() {
    // necessary for deserialization
  }
  
  /**
   * Creates an integrated address model.
   *
   * @param standardAddress is the standard address it is built on
   * @param paymentId is the payment ID it carries, as hex
   * @param integratedAddress is the integrated address
   */
  public ZephyrIntegratedAddress(String standardAddress, String paymentId, String integratedAddress) {
    super();
    this.standardAddress = standardAddress;
    this.paymentId = paymentId;
    this.integratedAddress = integratedAddress;
  }

  /**
   * Returns the standard address the integrated address is built on.
   *
   * @return the standard address
   */
  public String getStandardAddress() {
    return standardAddress;
  }
  
  /**
   * Sets the standard address.
   *
   * @param standardAddress is the standard address
   */
  public void setStandardAddress(String standardAddress) {
    this.standardAddress = standardAddress;
  }
  
  /**
   * Returns the payment ID the integrated address carries: 16 hex characters.
   *
   * @return the payment ID
   */
  public String getPaymentId() {
    return paymentId;
  }
  
  /**
   * Sets the payment ID.
   *
   * @param paymentId is the payment ID as hex
   */
  public void setPaymentId(String paymentId) {
    this.paymentId = paymentId;
  }
  
  /**
   * Returns the integrated address: the standard address and the payment ID in one address.
   *
   * @return the integrated address
   */
  public String getIntegratedAddress() {
    return integratedAddress;
  }
  
  /**
   * Sets the integrated address.
   *
   * @param integratedAddress is the integrated address
   */
  public void setIntegratedAddress(String integratedAddress) {
    this.integratedAddress = integratedAddress;
  }
  
  public String toString() {
    return integratedAddress;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((standardAddress == null) ? 0 : standardAddress.hashCode());
    result = prime * result + ((integratedAddress == null) ? 0 : integratedAddress.hashCode());
    result = prime * result + ((paymentId == null) ? 0 : paymentId.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrIntegratedAddress other = (ZephyrIntegratedAddress) obj;
    if (standardAddress == null) {
      if (other.standardAddress != null) return false;
    } else if (!standardAddress.equals(other.standardAddress)) return false;
    if (integratedAddress == null) {
      if (other.integratedAddress != null) return false;
    } else if (!integratedAddress.equals(other.integratedAddress)) return false;
    if (paymentId == null) {
      if (other.paymentId != null) return false;
    } else if (!paymentId.equals(other.paymentId)) return false;
    return true;
  }
}
