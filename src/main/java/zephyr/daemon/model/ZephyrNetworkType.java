package zephyr.daemon.model;

import zephyr.common.ZephyrError;

/**
 * Enumerates daemon networks.
 */
public enum ZephyrNetworkType {
  MAINNET(18, 19, 42),
  TESTNET(53, 54, 63),
  STAGENET(24, 25, 36);

  private final int primaryAddressCode;
  private final int integratedAddressCode;
  private final int subaddressCode;

  private ZephyrNetworkType(int primaryAddressCode, int integratedAddressCode, int subaddressCode) {
    this.primaryAddressCode = primaryAddressCode;
    this.integratedAddressCode = integratedAddressCode;
    this.subaddressCode = subaddressCode;
  }

  public int getPrimaryAddressCode() {
    return primaryAddressCode;
  }

  public int getIntegratedAddressCode() {
    return integratedAddressCode;
  }
  
  public int getSubaddressCode() {
    return subaddressCode;
  }
  
  public static ZephyrNetworkType parse(String networkTypeStr) {
    if (networkTypeStr == null) throw new ZephyrError("Cannot parse null network type");
    switch (networkTypeStr.toLowerCase()) {
      case "mainnet": return ZephyrNetworkType.MAINNET;
      case "testnet": return ZephyrNetworkType.TESTNET;
      case "stagenet": return ZephyrNetworkType.STAGENET;
      default: throw new ZephyrError("Invalid network type to parse: " + networkTypeStr);
    }
  }
}
