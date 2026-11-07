package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.TreeSet;
import monero.common.MoneroUtils;
import monero.daemon.model.MoneroNetworkType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zephyr.common.NativeAddressCheck;
import zephyr.common.ZephyrUtils;
import zephyr.daemon.model.ZephyrNetworkType;

/**
 * zephyr-java and monero-java in one JVM (Maven profile "coexistence" only; the other profiles
 * exclude this class from the test compile).
 *
 * Both can live in one process: every JNI export carries its package in its name
 * (Java_zephyr_... versus Java_monero_...), and libzephyr-java links its core statically with
 * hidden visibility and a version script, so it exports nothing but its JNI functions. The JVM
 * loads each library with its own handle, so the one shared name, JNI_OnLoad, does not clash.
 */
public class TestCoexistence {

  // the mainnet primary address of TestZephyrUtils
  private static final String ZEPHYR_MAINNET = "ZEPHYR3RDMKbaFgugwWnfL9zNc3Avpx8EBNJvwbmkN7LiGHMVbU2YkiSMk2b8Pq9myVT3deTgidge9ANsW3vUz5wP3omiUv2y5J4V";
  // a mainnet address of monero-java's own TestMoneroUtils
  private static final String MONERO_MAINNET = "42U9v3qs5CjZEePHBZHwuSckQXebuZu299NSmVEmQ41YJZQhKcPyujyMSzpDH4VMMVSBo3U3b54JaNvQLwAjqDhKS3rvM3L";

  @BeforeAll
  public static void loadBoth() throws Exception {
    Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux")
        && System.getProperty("os.arch").equals("amd64"), "the check uses the Linux x86_64 libraries");
    ZephyrUtils.loadNativeLibrary();   // java.library.path (build/)
    MoneroUtils.loadNativeLibrary();   // not on java.library.path, so from the monero-java jar
    log("monero-java " + MoneroUtils.getVersion() + ", jar sha256 " + sha256(jarOf(MoneroUtils.class)));
    log("jackson-databind " + com.fasterxml.jackson.databind.cfg.PackageVersion.VERSION
        + ", jackson-core " + com.fasterxml.jackson.core.json.PackageVersion.VERSION);
  }

  // Both native libraries are loaded in this JVM and each validates its own addresses
  @Test
  public void testBothLibrariesInOneJvm() throws IOException {
    assertTrue(ZephyrUtils.isNativeLibraryLoaded(), "zephyr-java native");
    assertTrue(MoneroUtils.isNativeLibraryLoaded(), "monero-java native");
    log("mapped: " + mapped("libzephyr-java") + " | " + mapped("monero"));

    ZephyrUtils.validateAddress(ZEPHYR_MAINNET, ZephyrNetworkType.MAINNET);
    NativeAddressCheck.validate(ZEPHYR_MAINNET, ZephyrNetworkType.MAINNET); // throws if the core rejects it
    MoneroUtils.validateAddress(MONERO_MAINNET, MoneroNetworkType.MAINNET);
    log("Zephyr mainnet address valid (Java and native), Monero mainnet address valid");
  }

  // The dynamic symbol sets of the two libraries share exactly JNI_OnLoad and no Java_* name
  @Test
  public void testExportedSymbolsDoNotClash() throws Exception {
    Assumptions.assumeTrue(nmAvailable(), "nm not found: symbol check skipped");
    File zephyrLib = new File(System.getProperty("java.library.path"), "libzephyr-java.so");
    File moneroLib = File.createTempFile("libmonero-java", ".so");
    try {
      try (InputStream in = MoneroUtils.class.getResourceAsStream("/linux-x86_64/libmonero-java.so");
           OutputStream out = new FileOutputStream(moneroLib)) {
        assertTrue(in != null, "monero-java jar has no linux-x86_64/libmonero-java.so");
        copy(in, out);
      }
      TreeSet<String> zephyr = definedDynamicSymbols(zephyrLib);
      TreeSet<String> monero = definedDynamicSymbols(moneroLib);
      TreeSet<String> common = new TreeSet<String>(zephyr);
      common.retainAll(monero);
      log("dynamic symbols: zephyr-java " + zephyr.size() + ", monero-java " + monero.size() + ", common " + common);
      assertEquals(Collections.singleton("JNI_OnLoad"), common);
      for (String name : common) assertTrue(!name.startsWith("Java_"), name);
    } finally {
      moneroLib.delete();
    }
  }

  // ---------------------------------- helpers ----------------------------------

  private static void log(String line) {
    System.out.println("[coexistence] " + line);
  }

  private static boolean nmAvailable() {
    try {
      Process p = new ProcessBuilder("nm", "--version").redirectErrorStream(true).start();
      drain(p.getInputStream());
      return p.waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  private static TreeSet<String> definedDynamicSymbols(File lib) throws Exception {
    Process p = new ProcessBuilder("nm", "-D", "--defined-only", lib.getAbsolutePath()).redirectErrorStream(true).start();
    TreeSet<String> names = new TreeSet<String>();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        String[] fields = line.trim().split("\\s+");
        if (fields.length >= 3) names.add(fields[fields.length - 1]);
      }
    }
    assertEquals(0, p.waitFor(), "nm -D --defined-only " + lib);
    assertTrue(!names.isEmpty(), "no symbols in " + lib);
    return names;
  }

  private static String mapped(String fragment) throws IOException {
    try (BufferedReader maps = new BufferedReader(new FileReader("/proc/self/maps"))) {
      String line;
      while ((line = maps.readLine()) != null) {
        if (line.contains(fragment) && line.indexOf('/') >= 0) return line.substring(line.indexOf('/'));
      }
    }
    return "(not mapped)";
  }

  private static File jarOf(Class<?> c) throws Exception {
    return new File(c.getProtectionDomain().getCodeSource().getLocation().toURI());
  }

  private static String sha256(File file) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new FileInputStream(file)) {
      byte[] buf = new byte[65536];
      int n;
      while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
    }
    StringBuilder sb = new StringBuilder();
    for (byte b : md.digest()) sb.append(String.format("%02x", b));
    return sb.toString();
  }

  private static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] buf = new byte[65536];
    int n;
    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
  }

  private static void drain(InputStream in) throws IOException {
    byte[] buf = new byte[4096];
    while (in.read(buf) != -1) { }
  }
}
