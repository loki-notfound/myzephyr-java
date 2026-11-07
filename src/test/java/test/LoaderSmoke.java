package test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import zephyr.common.NativeAddressCheck;
import zephyr.common.ZephyrUtils;
import zephyr.daemon.model.ZephyrNetworkType;

/**
 * Smoke check of the JAR loader: run with a java.library.path that holds no zephyr-java, so the
 * library can only come from the JAR's resources, for example
 * {@code java -Djava.library.path=/nonexistent -cp <jar>:target/test-classes:<jackson jars> test.LoaderSmoke}.
 * Not a JUnit test (surefire runs Test*.java only).
 */
public class LoaderSmoke {

  // the mainnet primary address of TestZephyrUtils
  private static final String MAINNET_PRIMARY = "ZEPHYR3RDMKbaFgugwWnfL9zNc3Avpx8EBNJvwbmkN7LiGHMVbU2YkiSMk2b8Pq9myVT3deTgidge9ANsW3vUz5wP3omiUv2y5J4V";

  public static void main(String[] args) throws IOException {
    System.out.println("java.library.path=" + System.getProperty("java.library.path"));
    ZephyrUtils.loadNativeLibrary();
    if (!ZephyrUtils.isNativeLibraryLoaded()) throw new AssertionError("native library not loaded");

    // the mapped file shows where the library came from (Linux): a temporary copy from the JAR
    try (BufferedReader maps = new BufferedReader(new FileReader("/proc/self/maps"))) {
      String line;
      while ((line = maps.readLine()) != null) {
        if (line.contains("libzephyr-java")) {
          System.out.println("mapped: " + line.substring(line.indexOf('/')));
          break;
        }
      }
    }

    NativeAddressCheck.validate(MAINNET_PRIMARY, ZephyrNetworkType.MAINNET); // throws if the core rejects it
    System.out.println("native validate_address: " + MAINNET_PRIMARY.substring(0, 12) + "... MAINNET valid");
    System.out.println("loaded from jar: ok");
  }
}
