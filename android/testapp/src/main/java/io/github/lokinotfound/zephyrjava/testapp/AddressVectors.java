package io.github.lokinotfound.zephyrjava.testapp;

import zephyr.common.TestAppNativeCheck;
import zephyr.common.ZephyrUtils;
import zephyr.daemon.model.ZephyrNetworkType;

/**
 * The primary-address vectors of TestZephyrUtils (one per network), validated by the native core.
 */
final class AddressVectors {

  static final Object[][] PRIMARY = {
    { ZephyrNetworkType.MAINNET, "ZEPHYR3RDMKbaFgugwWnfL9zNc3Avpx8EBNJvwbmkN7LiGHMVbU2YkiSMk2b8Pq9myVT3deTgidge9ANsW3vUz5wP3omiUv2y5J4V" },
    { ZephyrNetworkType.TESTNET, "ZPHTjeJQnpV8zJpwVBXoVE5RHkBDf4YBJSsvaj9mwn5d8MrASVrZCdzhwjroh6X2tkT79XyJVXnxVifz6HvhLZcA2QwG2Hx6Vug" },
    { ZephyrNetworkType.STAGENET, "ZPHSjp3QtHUQxfT4k9bfCBgye5EVdTGxicgvwSwysedp3AU4UarjWufgafz9n9FesB2C6FYrQCAXVJfRiKCK7UK3PUnUEmrWXXE" }
  };

  private AddressVectors() {}

  /**
   * Loads the native library and validates every vector natively.
   *
   * @return one line per step, "ok" or the error; the last line is "all ok" when nothing failed
   */
  static String run() {
    StringBuilder out = new StringBuilder();
    boolean ok = true;
    try {
      ZephyrUtils.loadNativeLibrary();
      out.append("native library: loaded\n");
    } catch (Throwable e) {
      return out.append("native library: ").append(e).append('\n').toString();
    }
    for (Object[] vector : PRIMARY) {
      ZephyrNetworkType networkType = (ZephyrNetworkType) vector[0];
      String address = (String) vector[1];
      out.append(networkType).append(' ').append(address.substring(0, 12)).append("...: ");
      try {
        TestAppNativeCheck.validate(address, networkType);
        out.append("valid\n");
      } catch (Throwable e) {
        ok = false;
        out.append(e).append('\n');
      }
    }
    return out.append(ok ? "all ok" : "FAILED").toString();
  }
}
