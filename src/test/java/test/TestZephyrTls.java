package test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import zephyr.common.ZephyrError;
import zephyr.common.ZephyrTls;

/**
 * ZephyrTls offline: the PEM reader and writer (with their own base64) against the shipped root
 * certificates, duplicates, refusals, the exact file, the date, and the bundle each configuration
 * produces. The configuration is per process and added certificates cannot be removed, so the
 * tests run in a fixed order and set every switch they depend on. That the native side sees the
 * environment is shown by the integration tests (TestZephyrIntegration, zephyr.daemon.uri.tls).
 */
@TestMethodOrder(OrderAnnotation.class)
public class TestZephyrTls {

  /** A self-signed test CA (prime256v1), not among the shipped root certificates. */
  private static final String TEST_CA =
      "-----BEGIN CERTIFICATE-----\n"
      + "MIIBgDCCASagAwIBAgIBATAKBggqhkjOPQQDAjAeMRwwGgYDVQQDDBN6ZXBoeXIt\n"
      + "amF2YSB0ZXN0IENBMCAXDTI2MTExMDE3MTk0OVoYDzIxMjYxMDE3MTcxOTQ5WjAe\n"
      + "MRwwGgYDVQQDDBN6ZXBoeXItamF2YSB0ZXN0IENBMFkwEwYHKoZIzj0CAQYIKoZI\n"
      + "zj0DAQcDQgAEO1bYLTzMg9VfDdtf/uYUVYeQTwL6hecdx1+ZHc56nVkRuCNcOXBl\n"
      + "svcAfOyll9Nkd+UK2Dcfs3fKCJIKjHk18aNTMFEwHQYDVR0OBBYEFLpM5oAQzKMi\n"
      + "vByh+AHNN1BEXbCAMB8GA1UdIwQYMBaAFLpM5oAQzKMivByh+AHNN1BEXbCAMA8G\n"
      + "A1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIhAOWp7axPQXYRH3jJuIxU\n"
      + "4icyNc85DkDS0cELDD7img1fAiB7fFZGg+YUbwo0Qc5SsTvxzmr0kjOvQ5h8pVm5\n"
      + "PovdiA==\n"
      + "-----END CERTIFICATE-----\n";

  /** A block that is base64 but not a certificate. */
  private static final String NOT_A_CERTIFICATE = "-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----\n";

  private static final int SHIPPED_COUNT = 118;

  /** The certificates of a PEM stream as the JDK reads them, DER, in their order. */
  private static List<byte[]> jdkParse(InputStream in) throws Exception {
    List<byte[]> out = new ArrayList<byte[]>();
    for (Certificate cert : CertificateFactory.getInstance("X.509").generateCertificates(in)) out.add(cert.getEncoded());
    return out;
  }

  private static List<byte[]> jdkParse(File file) throws Exception {
    try (InputStream in = new FileInputStream(file)) {
      return jdkParse(in);
    }
  }

  private static List<byte[]> shipped() throws Exception {
    try (InputStream in = ZephyrTls.class.getResourceAsStream("/zephyr/common/cacert.pem")) {
      return jdkParse(in);
    }
  }

  private static InputStream stream(String text) {
    return new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII));
  }

  private static Set<String> set(Collection<byte[]> ders) {
    Set<String> out = new LinkedHashSet<String>();
    for (byte[] der : ders) out.add(java.util.Base64.getEncoder().encodeToString(der));
    return out;
  }

  /** The defaults of a fresh process, except for certificates added earlier. */
  private static void defaults() {
    ZephyrTls.setCaFile(null);
    ZephyrTls.setUseSystemCertificates(false);
    ZephyrTls.setUseUserCertificates(false);
    ZephyrTls.setUseShippedCertificates(true);
  }

  @Test
  @Order(1)
  public void testShippedCertificatesDate() throws Exception {
    String date = null;
    for (String line : Files.readAllLines(new File("bin/ca-bundle.env").toPath(), StandardCharsets.UTF_8)) {
      if (line.startsWith("CA_BUNDLE_DATE=")) date = line.substring("CA_BUNDLE_DATE=".length());
    }
    assertEquals(date, ZephyrTls.getShippedCertificatesDate());
    assertTrue(ZephyrTls.getShippedCertificatesDate().matches("\\d{4}-\\d{2}-\\d{2}"));
  }

  @Test
  @Order(2)
  public void testShippedRoundTrip() throws Exception {
    defaults();
    List<byte[]> expected = shipped();
    assertEquals(SHIPPED_COUNT, expected.size(), "certificates in the shipped file");
    File bundle = ZephyrTls.apply();
    assertEquals("ca-bundle.pem", bundle.getName());
    List<byte[]> written = jdkParse(bundle);
    assertEquals(SHIPPED_COUNT, written.size(), "certificates in the bundle");
    for (int i = 0; i < expected.size(); i++) assertArrayEquals(expected.get(i), written.get(i), "certificate " + (i + 1));
    List<String> lines = Files.readAllLines(bundle.toPath(), StandardCharsets.US_ASCII);
    int body = 0;
    for (String line : lines) {
      assertTrue(line.length() <= 64, "line longer than 64 characters: " + line);
      if (!line.startsWith("-----")) body++;
    }
    assertTrue(body > 0);
    assertTrue(new File(bundle.getParentFile(), "empty").isDirectory(), "the empty directory beside the bundle");
    assertEquals(0, new File(bundle.getParentFile(), "empty").list().length);
  }

  @Test
  @Order(3)
  public void testNonCertificateAddsNothing() throws Exception {
    defaults();
    ZephyrTls.setUseShippedCertificates(false);
    ZephyrError e = assertThrows(ZephyrError.class, () -> ZephyrTls.addTrustedCertificates(stream(TEST_CA + NOT_A_CERTIFICATE)));
    assertTrue(e.getMessage().contains("Certificate 2"), e.getMessage());
    assertThrows(ZephyrError.class, () -> ZephyrTls.addTrustedCertificates(stream("no certificate here\n")));
    assertThrows(ZephyrError.class, () -> ZephyrTls.addTrustedCertificates(stream("-----BEGIN CERTIFICATE-----\nMIIB\n")));
    assertThrows(ZephyrError.class, () -> ZephyrTls.addTrustedCertificates(stream("-----BEGIN CERTIFICATE-----\nMII*\n-----END CERTIFICATE-----\n")));
    assertThrows(ZephyrError.class, () -> ZephyrTls.addTrustedCertificates((InputStream) null));
    assertThrows(ZephyrError.class, () -> ZephyrTls.addTrustedCertificates(new File("does-not-exist.pem")));
    File bundle = ZephyrTls.apply();
    assertEquals(0, bundle.length(), "nothing was added, and the shipped certificates are off");
  }

  @Test
  @Order(4)
  public void testCaFile() throws Exception {
    defaults();
    File file = File.createTempFile("zephyr-java-test-ca", ".pem");
    file.deleteOnExit();
    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(TEST_CA.getBytes(StandardCharsets.US_ASCII));
    }
    ZephyrTls.setCaFile(file);
    assertEquals(file.getAbsoluteFile(), ZephyrTls.apply());
    assertEquals(TEST_CA, new String(Files.readAllBytes(file.toPath()), StandardCharsets.US_ASCII), "the CA file is not rewritten");
    ZephyrTls.setCaFile(new File("does-not-exist.pem"));
    assertThrows(ZephyrError.class, ZephyrTls::apply);
    ZephyrTls.setCaFile(null);
    File bundle = ZephyrTls.apply();
    assertEquals("ca-bundle.pem", bundle.getName());
    assertEquals(SHIPPED_COUNT, jdkParse(bundle).size());
  }

  @Test
  @Order(5)
  public void testSystemCertificates() throws Exception {
    List<byte[]> system = new ArrayList<byte[]>();
    TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init((KeyStore) null);
    for (TrustManager manager : factory.getTrustManagers()) {
      if (manager instanceof X509TrustManager) for (X509Certificate cert : ((X509TrustManager) manager).getAcceptedIssuers()) system.add(cert.getEncoded());
    }
    assertFalse(system.isEmpty(), "the JVM's default trust manager accepts no issuer");
    defaults();
    ZephyrTls.setUseShippedCertificates(false);
    ZephyrTls.setUseSystemCertificates(true);
    ZephyrTls.setUseUserCertificates(true); // no meaning outside Android: ignored
    assertEquals(set(system), set(jdkParse(ZephyrTls.apply())), "the system certificates only");
    ZephyrTls.setUseShippedCertificates(true);
    Set<String> union = set(system);
    union.addAll(set(shipped()));
    List<byte[]> written = jdkParse(ZephyrTls.apply());
    assertEquals(union.size(), written.size(), "system and shipped certificates without duplicates");
    assertEquals(union, set(written));
  }

  @Test
  @Order(6)
  public void testBeforeNativeConnection() throws Exception {
    defaults(); // nothing has been added so far
    ZephyrTls.setUseShippedCertificates(false);
    File bundle = ZephyrTls.apply();
    assertEquals(0, bundle.length());
    ZephyrTls.setUseShippedCertificates(true); // configured and changed
    ZephyrTls.beforeNativeConnection(null);
    ZephyrTls.beforeNativeConnection("http://127.0.0.1:17767");
    assertEquals(0, bundle.length(), "no https URI: nothing applied");
    ZephyrTls.beforeNativeConnection("https://127.0.0.1:443");
    assertEquals(SHIPPED_COUNT, jdkParse(bundle).size(), "an https URI applies the changed configuration");
  }

  @Test
  @Order(7)
  public void testAddedCertificatesAndDuplicates() throws Exception {
    defaults();
    ZephyrTls.setUseShippedCertificates(false);
    ZephyrTls.addTrustedCertificates(stream("text before\n" + TEST_CA + "text between\n" + TEST_CA));
    List<byte[]> written = jdkParse(ZephyrTls.apply());
    assertEquals(1, written.size(), "the test CA once");
    ZephyrTls.setUseShippedCertificates(true);
    try (InputStream in = ZephyrTls.class.getResourceAsStream("/zephyr/common/cacert.pem")) {
      ZephyrTls.addTrustedCertificates(in); // all of them are in the bundle already
    }
    written = jdkParse(ZephyrTls.apply());
    assertEquals(SHIPPED_COUNT + 1, written.size(), "shipped certificates and the test CA, without duplicates");
    assertEquals(set(jdkParse(stream(TEST_CA))).iterator().next(), set(written).toArray()[SHIPPED_COUNT], "added certificates come last");
  }

  @Test
  @Order(8)
  public void testDownloadRefusals() throws Exception {
    File target = File.createTempFile("zephyr-java-download", ".pem");
    target.deleteOnExit();
    ZephyrError e = assertThrows(ZephyrError.class, () -> ZephyrTls.downloadCertificates(new URL("http://curl.se/ca/cacert.pem"), target, null));
    assertTrue(e.getMessage().contains("https only"), e.getMessage());
    assertThrows(ZephyrError.class, () -> ZephyrTls.downloadCertificates(new URL("ftp://curl.se/ca/cacert.pem"), target, null));
    assertThrows(ZephyrError.class, () -> ZephyrTls.downloadCertificates(new URL("https://curl.se/ca/cacert.pem"), null, null));
    assertThrows(ZephyrError.class, () -> ZephyrTls.downloadCertificates(null));
    assertThrows(ZephyrError.class, () -> ZephyrTls.downloadCertificates(null, target, null));
    assertThrows(ZephyrError.class, () -> ZephyrTls.downloadCertificates(new URL("https://curl.se/ca/cacert.pem"), target, "not a hash"));
    assertEquals(0, target.length(), "the target is untouched");
  }

  /**
   * A download does not hold the ZephyrTls lock while it connects: a local server accepts and never
   * answers, so the download blocks in the TLS handshake; meanwhile the lock is free. Offline, no
   * key material.
   */
  @Test
  @Order(9)
  public void testDownloadDoesNotHoldTheLock() throws Exception {
    File target = new File(System.getProperty("java.io.tmpdir"), "zephyr-java-download-lock-" + System.nanoTime() + ".pem");
    target.deleteOnExit();
    try (java.net.ServerSocket server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(20000);
      URL url = new URL("https://127.0.0.1:" + server.getLocalPort() + "/");
      Throwable[] outcome = new Throwable[1];
      Thread download = new Thread(() -> {
        try {
          ZephyrTls.downloadCertificates(url, target, null);
        } catch (Throwable t) {
          outcome[0] = t;
        }
      });
      download.start();
      try (java.net.Socket accepted = server.accept()) { // the download is connected and waits in the handshake
        Thread.sleep(500);
        assertTrue(download.isAlive(), "the download ended before the test");
        long start = System.nanoTime();
        assertEquals(ZephyrTls.getShippedCertificatesDate(), ZephyrTls.getShippedCertificatesDate());
        ZephyrTls.beforeNativeConnection("http://x");
        long ms = (System.nanoTime() - start) / 1000000;
        assertTrue(ms < 2000, "the ZephyrTls calls took " + ms + " ms while a download was connecting");
        assertTrue(download.isAlive(), "the download ended before the socket was closed");
      }
      download.join(20000);
      assertFalse(download.isAlive(), "the download did not end after the socket was closed");
      assertTrue(outcome[0] instanceof ZephyrError, "the download ended with " + outcome[0]);
      assertFalse(target.exists(), "a target was written");
    }
  }

  /**
   * The directory of the bundle and the empty directory beside it are writable by their owner
   * only, whatever the umask (run this suite under "umask 000" to see that the umask plays no part).
   */
  @Test
  @Order(10)
  public void testDirectoriesWritableByOwnerOnly() throws Exception {
    defaults();
    File bundle = ZephyrTls.apply();
    for (File dir : new File[] { bundle.getParentFile(), new File(bundle.getParentFile(), "empty") }) {
      Set<java.nio.file.attribute.PosixFilePermission> permissions = Files.getPosixFilePermissions(dir.toPath());
      System.out.println("TestZephyrTls: " + dir.getName() + " " + java.nio.file.attribute.PosixFilePermissions.toString(permissions));
      assertTrue(permissions.contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE), dir + " " + permissions);
      assertFalse(permissions.contains(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE), dir + " " + permissions);
      assertFalse(permissions.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE), dir + " " + permissions);
    }
  }
}
