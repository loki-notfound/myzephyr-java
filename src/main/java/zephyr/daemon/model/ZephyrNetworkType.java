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
  /** Zephyr mainnet: primary addresses start with "ZEPHYR". */
  MAINNET(0x6241d18c0L, 0xc4f7d18c0L, 0x8dd58c0L),
  /** Zephyr testnet (its hard-fork table in the pinned core stops before the V2 assets). */
  TESTNET(0x334e41L, 0xa8f330e41L, 0xf1fce41L),
  /** Zephyr stagenet (its hard-fork table in the pinned core stops before the V2 assets). */
  STAGENET(0x32ce41L, 0x6cef28e41L, 0xedf4e41L);

  private final long primaryAddressTag;
  private final long integratedAddressTag;
  private final long subaddressTag;

  private ZephyrNetworkType(long primaryAddressTag, long integratedAddressTag, long subaddressTag) {
    this.primaryAddressTag = primaryAddressTag;
    this.integratedAddressTag = integratedAddressTag;
    this.subaddressTag = subaddressTag;
  }

  /**
   * Returns the varint tag that starts a decoded primary (standard) address on this network.
   *
   * @return the CRYPTONOTE_PUBLIC_ADDRESS_BASE58_PREFIX of this network
   */
  public long getPrimaryAddressTag() {
    return primaryAddressTag;
  }

  /**
   * Returns the varint tag that starts a decoded integrated address on this network.
   *
   * @return the CRYPTONOTE_PUBLIC_INTEGRATED_ADDRESS_BASE58_PREFIX of this network
   */
  public long getIntegratedAddressTag() {
    return integratedAddressTag;
  }
  
  /**
   * Returns the varint tag that starts a decoded subaddress on this network.
   *
   * @return the CRYPTONOTE_PUBLIC_SUBADDRESS_BASE58_PREFIX of this network
   */
  public long getSubaddressTag() {
    return subaddressTag;
  }
  
  /**
   * Parses a network type from its name, ignoring case.
   *
   * @param networkTypeStr is "mainnet", "testnet" or "stagenet"
   * @return the network type
   * @throws zephyr.common.ZephyrError if the name is null or not one of the three
   */
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
