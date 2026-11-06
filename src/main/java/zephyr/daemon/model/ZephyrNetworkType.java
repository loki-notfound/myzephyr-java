package zephyr.daemon.model;

import zephyr.common.ZephyrError;

/**
 * Enumerates daemon networks.
 * 
 * Each network carries the address tags of its primary addresses, integrated addresses and
 * subaddresses: the CRYPTONOTE_PUBLIC_*_BASE58_PREFIX values of zephyr's
 * src/cryptonote_config.h. The tags are varints (little-endian base-128) at the start of the
 * decoded address, not Monero's one-byte codes.
 */
public enum ZephyrNetworkType {
  MAINNET(0x6241d18c0L, 0xc4f7d18c0L, 0x8dd58c0L),
  TESTNET(0x334e41L, 0xa8f330e41L, 0xf1fce41L),
  STAGENET(0x32ce41L, 0x6cef28e41L, 0xedf4e41L);

  private final long primaryAddressTag;
  private final long integratedAddressTag;
  private final long subaddressTag;

  private ZephyrNetworkType(long primaryAddressTag, long integratedAddressTag, long subaddressTag) {
    this.primaryAddressTag = primaryAddressTag;
    this.integratedAddressTag = integratedAddressTag;
    this.subaddressTag = subaddressTag;
  }

  public long getPrimaryAddressTag() {
    return primaryAddressTag;
  }

  public long getIntegratedAddressTag() {
    return integratedAddressTag;
  }
  
  public long getSubaddressTag() {
    return subaddressTag;
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
