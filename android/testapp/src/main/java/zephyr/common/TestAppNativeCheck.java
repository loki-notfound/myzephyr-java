package zephyr.common;

import zephyr.daemon.model.ZephyrNetworkType;

/**
 * The test app's access to the native address check, the counterpart of the test tree's
 * NativeAddressCheck: a class of the same package, so it reaches the package-private
 * ZephyrUtils.validateAddressNative (one class loader for the whole APK). Not part of the library.
 */
public final class TestAppNativeCheck {

  private TestAppNativeCheck() {}

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
