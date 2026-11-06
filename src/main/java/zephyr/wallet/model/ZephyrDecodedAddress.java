package zephyr.wallet.model;

import zephyr.daemon.model.ZephyrNetworkType;

/**
 * Maintains metadata for a decoded address.
 */
public class ZephyrDecodedAddress {
  
  private String address;
  private ZephyrAddressType addressType;
  private ZephyrNetworkType networkType;
  
  public ZephyrDecodedAddress(String address, ZephyrAddressType addressType, ZephyrNetworkType networkType) {
    this.address = address;
    this.addressType = addressType;
    this.networkType = networkType;
  }
  
  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public ZephyrAddressType getAddressType() {
    return addressType;
  }
  
  public void setAddressType(ZephyrAddressType addressType) {
    this.addressType = addressType;
  }
  
  public ZephyrNetworkType getNetworkType() {
    return networkType;
  }
  
  public void setNetworkType(ZephyrNetworkType networkType) {
    this.networkType = networkType;
  }
}
