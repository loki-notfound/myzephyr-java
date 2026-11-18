package zephyr.wallet.model;

import zephyr.daemon.model.ZephyrNetworkType;

/**
 * Maintains metadata for a decoded address.
 */
public class ZephyrDecodedAddress {
  
  private String address;
  private ZephyrAddressType addressType;
  private ZephyrNetworkType networkType;
  
  /**
   * Creates the result of decoding an address.
   *
   * @param address is the address that was decoded
   * @param addressType is the kind of address
   * @param networkType is the network the address belongs to
   */
  public ZephyrDecodedAddress(String address, ZephyrAddressType addressType, ZephyrNetworkType networkType) {
    this.address = address;
    this.addressType = addressType;
    this.networkType = networkType;
  }
  
  /**
   * Returns the address that was decoded.
   *
   * @return the address
   */
  public String getAddress() {
    return address;
  }

  /**
   * Sets the address that was decoded.
   *
   * @param address is the address
   */
  public void setAddress(String address) {
    this.address = address;
  }

  /**
   * Returns the kind of address: a primary address, an integrated address or a subaddress.
   *
   * @return the address type
   */
  public ZephyrAddressType getAddressType() {
    return addressType;
  }
  
  /**
   * Sets the kind of address.
   *
   * @param addressType is the address type
   */
  public void setAddressType(ZephyrAddressType addressType) {
    this.addressType = addressType;
  }
  
  /**
   * Returns the network the address belongs to.
   *
   * @return the network type
   */
  public ZephyrNetworkType getNetworkType() {
    return networkType;
  }
  
  /**
   * Sets the network the address belongs to.
   *
   * @param networkType is the network type
   */
  public void setNetworkType(ZephyrNetworkType networkType) {
    this.networkType = networkType;
  }
}
