package zephyr.common;

import zephyr.daemon.model.ZephyrNetworkType;

/**
 * Test-only access to the native address check; not part of the library.
 */
public final class NativeAddressCheck {

  private NativeAddressCheck() {}

  /**
   * Validates the given address with the native core.
   * 
   * @param address is the address to validate
   * @param networkType is the address's network type
   * @throws ZephyrError if the core rejects the address
   */
  public static void validate(String address, ZephyrNetworkType networkType) {
    ZephyrUtils.validateAddressNative(address, networkType);
  }
}
