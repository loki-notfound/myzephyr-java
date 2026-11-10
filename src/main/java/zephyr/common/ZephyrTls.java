package zephyr.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * The root certificates the native wallet trusts for {@code https://} nodes.
 *
 * <p>This class decides which root certificates {@link zephyr.wallet.ZephyrWalletFull} trusts when
 * its daemon URI is {@code https://}. The Java RPC classes ({@code ZephyrDaemonRpc},
 * {@code ZephyrWalletRpc}, {@code ZephyrRpcConnection}) are not affected: they use the trust store
 * of the Java runtime.</p>
 *
 * <p><b>The default</b> is the root certificates shipped in this library and nothing else: Mozilla's
 * list of root certificates as curl publishes it ({@link #getShippedCertificatesDate()}), the same
 * on every device and renewed with each release. A node whose certificate comes from a private CA,
 * or from a CA only the device knows, needs {@link #addTrustedCertificates(File)} or
 * {@link #setUseSystemCertificates(boolean) setUseSystemCertificates(true)}. Android 7.0's system
 * store lacks ISRG Root X1 (the root of Let's Encrypt), which the shipped certificates cover.</p>
 *
 * <p><b>When it applies.</b> On Android the defaults are applied automatically before the first
 * {@code https://} connection of a wallet, unless the process environment already names
 * {@code SSL_CERT_FILE} or {@code SSL_CERT_DIR} (then the app has chosen, and nothing is changed).
 * When the app calls any configuration method of this class, the configuration is applied before
 * the next {@code https://} connection of a wallet, on every platform. On a desktop JVM nothing
 * changes unless the application calls this class: OpenSSL's own default ({@code /etc/ssl}) and the
 * {@code SSL_CERT_FILE} and {@code SSL_CERT_DIR} of the environment stay in effect.</p>
 *
 * <p><b>How it works.</b> {@link #apply()} writes one PEM file with the chosen certificates and sets
 * {@code SSL_CERT_FILE} to it and {@code SSL_CERT_DIR} to an empty directory, in the process
 * environment, where the OpenSSL of the native wallet reads them. The environment belongs to the
 * whole process. The core reads the bundle whenever a wallet sets up its connection, so configure
 * this class before the wallet is created or its connection is set.</p>
 *
 * <p><b>What it cannot do.</b> There is no certificate pinning. A certificate whose host name does
 * not match the URI is refused: the core checks the name. The app's Network Security Configuration
 * does not apply to the native wallet.</p>
 *
 * <p>All members are static and synchronized.</p>
 */
public final class ZephyrTls {

  private static final String BEGIN = "-----BEGIN CERTIFICATE-----";
  private static final String END = "-----END CERTIFICATE-----";
  private static final String SHIPPED_RESOURCE = "cacert.pem"; // zephyr/common/cacert.pem, src/main/resources-raw
  private static final String BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  private static final int PEM_LINE = 64;

  // the configuration
  private static boolean useSystem = false;
  private static boolean useUser = false;
  private static boolean useShipped = true;
  private static final List<byte[]> added = new ArrayList<byte[]>(); // DER, no duplicates
  private static File caFile;

  // the state
  private static boolean configured = false; // a configuration method was called
  private static boolean changed = true;     // the configuration differs from the one applied (none yet)
  private static boolean applied = false;    // apply() succeeded in this process
  private static File directory;             // the directory of the bundle and the empty directory
  private static List<byte[]> shipped;       // the shipped certificates, DER

  private ZephyrTls() { }

  /**
   * Include the system's root certificates in the bundle: on Android those of the system store
   * ("AndroidCAStore", aliases {@code system:}), elsewhere the accepted issuers of the Java
   * runtime's default trust manager. Off by default.
   *
   * @param use true to include the system's root certificates
   */
  public static synchronized void setUseSystemCertificates(boolean use) {
    configured = true;
    if (useSystem != use) changed = true;
    useSystem = use;
  }

  /**
   * Include the certificates the user installed on the device (Android, aliases {@code user:} of
   * "AndroidCAStore"). Off by default; ignored outside Android.
   *
   * @param use true to include the user's certificates (Android)
   */
  public static synchronized void setUseUserCertificates(boolean use) {
    configured = true;
    if (useUser != use) changed = true;
    useUser = use;
  }

  /**
   * Include the root certificates shipped in this library ({@link #getShippedCertificatesDate()}).
   * On by default.
   *
   * @param use true to include the shipped root certificates
   */
  public static synchronized void setUseShippedCertificates(boolean use) {
    configured = true;
    if (useShipped != use) changed = true;
    useShipped = use;
  }

  /**
   * Add the certificates of a PEM file to the bundle, for example the CA of a private node or a
   * self-signed node certificate whose name matches the node's host name. Every block between
   * {@code -----BEGIN CERTIFICATE-----} and {@code -----END CERTIFICATE-----} is read; other text
   * is skipped. A certificate that is already in the list is not added twice.
   *
   * @param pemFile the PEM file
   * @throws ZephyrError if the file cannot be read, holds no certificate, or a block is not a
   *         certificate; then nothing is added
   */
  public static synchronized void addTrustedCertificates(File pemFile) {
    if (pemFile == null) throw new ZephyrError("No PEM file given");
    byte[] data;
    try {
      FileInputStream in = new FileInputStream(pemFile);
      try {
        data = readAll(in, -1);
      } finally {
        in.close();
      }
    } catch (IOException e) {
      throw error("Cannot read " + pemFile, e);
    }
    addCertificates(data, pemFile.toString());
  }

  /**
   * Add the certificates of a PEM stream to the bundle, as {@link #addTrustedCertificates(File)}.
   * The stream is read to its end and not closed.
   *
   * @param pem the PEM data
   * @throws ZephyrError if the stream cannot be read, holds no certificate, or a block is not a
   *         certificate; then nothing is added
   */
  public static synchronized void addTrustedCertificates(InputStream pem) {
    if (pem == null) throw new ZephyrError("No PEM stream given");
    byte[] data;
    try {
      data = readAll(pem, -1);
    } catch (IOException e) {
      throw error("Cannot read the PEM stream", e);
    }
    addCertificates(data, "the PEM stream");
  }

  /**
   * Trust exactly this file and nothing else: {@code SSL_CERT_FILE} names it, and the rules above
   * (system, user, shipped and added certificates) are not used; no bundle is written.
   *
   * @param pemFile the PEM file to trust, or null to go back to the rules above
   */
  public static synchronized void setCaFile(File pemFile) {
    configured = true;
    File file = pemFile == null ? null : pemFile.getAbsoluteFile();
    if (file == null ? caFile != null : !file.equals(caFile)) changed = true;
    caFile = file;
  }

  /**
   * Apply the configuration now: write the bundle (unless {@link #setCaFile(File)} names a file)
   * and set {@code SSL_CERT_FILE} and {@code SSL_CERT_DIR} in the process environment. The bundle
   * is written to a temporary name and renamed, so a half-written file is never in use. On Android
   * the files are under {@code java.io.tmpdir} (the app's cache directory), in
   * {@code zephyr-java-tls}; elsewhere in a directory this process creates for itself, deleted
   * when the JVM exits.
   *
   * @return the file {@code SSL_CERT_FILE} names
   * @throws ZephyrError with the cause if anything fails
   */
  public static synchronized File apply() {
    try {
      File dir = directory();
      File empty = emptyDirectory(dir);
      File inUse;
      if (caFile != null) {
        if (!caFile.isFile()) throw new ZephyrError("The CA file does not exist: " + caFile);
        inUse = caFile;
      } else {
        inUse = new File(dir, "ca-bundle.pem");
        writeAtomically(inUse, writePem(bundle()));
      }
      ZephyrUtils.loadNativeLibrary();
      setEnvJni("SSL_CERT_FILE", inUse.getAbsolutePath());
      setEnvJni("SSL_CERT_DIR", empty.getAbsolutePath());
      applied = true;
      changed = false;
      return inUse;
    } catch (ZephyrError e) {
      throw e;
    } catch (Exception | LinkageError e) {
      throw error("Cannot apply the TLS root certificates", e);
    }
  }

  /**
   * Get the date of the root certificates shipped in this library: the date of Mozilla's list as
   * curl published it.
   *
   * @return the date, for example "2026-11-05", or "unknown"
   */
  public static synchronized String getShippedCertificatesDate() {
    try {
      InputStream in = ZephyrTls.class.getResourceAsStream("/zephyr-java.properties");
      if (in == null) return "unknown";
      try {
        Properties props = new Properties();
        props.load(in);
        return props.getProperty("ca.bundle.date", "unknown");
      } finally {
        in.close();
      }
    } catch (IOException e) {
      return "unknown";
    }
  }

  /**
   * Internal: {@code ZephyrWalletFull} calls this before it hands a daemon URI to the native core.
   * Applications do not call it. For an {@code https://} URI it applies the configuration when the
   * app configured this class and the configuration changed since the last apply, or, on Android
   * without any configuration, the defaults once, unless the environment already names
   * {@code SSL_CERT_FILE} or {@code SSL_CERT_DIR}. Anything else does nothing.
   *
   * @param daemonUri the URI the wallet is about to use, may be null
   * @throws ZephyrError if applying fails
   */
  public static synchronized void beforeNativeConnection(String daemonUri) {
    if (daemonUri == null || !daemonUri.regionMatches(true, 0, "https://", 0, 8)) return;
    if (configured) {
      if (changed) apply();
      return;
    }
    if (!isAndroid() || applied) return;
    if (System.getenv("SSL_CERT_FILE") != null || System.getenv("SSL_CERT_DIR") != null) return;
    apply();
  }

  // ------------------------------- private ----------------------------------

  private static native void setEnvJni(String name, String value);

  private static synchronized boolean isAndroid() {
    return "Dalvik".equals(System.getProperty("java.vm.name"));
  }

  private static synchronized ZephyrError error(String message, Throwable cause) {
    ZephyrError err = new ZephyrError(message + ": " + cause);
    err.initCause(cause);
    return err;
  }

  private static synchronized void addCertificates(byte[] pem, String source) {
    List<byte[]> certs = parseCertificates(pem, source);
    if (certs.isEmpty()) throw new ZephyrError("No certificate in " + source);
    Set<ByteBuffer> known = new LinkedHashSet<ByteBuffer>();
    for (byte[] der : added) known.add(ByteBuffer.wrap(der));
    for (byte[] der : certs) {
      if (known.add(ByteBuffer.wrap(der))) {
        added.add(der);
        changed = true;
      }
    }
    configured = true;
  }

  /** The certificates of the bundle under the current configuration, without duplicates. */
  private static synchronized List<byte[]> bundle() throws Exception {
    Set<ByteBuffer> set = new LinkedHashSet<ByteBuffer>();
    if (useSystem || (useUser && isAndroid())) {
      for (byte[] der : systemCertificates(useSystem, useUser)) set.add(ByteBuffer.wrap(der));
    }
    if (useShipped) {
      for (byte[] der : shippedCertificates()) set.add(ByteBuffer.wrap(der));
    }
    for (byte[] der : added) set.add(ByteBuffer.wrap(der));
    List<byte[]> out = new ArrayList<byte[]>();
    for (ByteBuffer b : set) out.add(b.array());
    return out;
  }

  private static synchronized List<byte[]> systemCertificates(boolean system, boolean user) throws Exception {
    List<byte[]> out = new ArrayList<byte[]>();
    if (isAndroid()) {
      KeyStore store = KeyStore.getInstance("AndroidCAStore");
      store.load(null);
      for (Enumeration<String> aliases = store.aliases(); aliases.hasMoreElements();) {
        String alias = aliases.nextElement();
        if (!(system && alias.startsWith("system:")) && !(user && alias.startsWith("user:"))) continue;
        Certificate cert = store.getCertificate(alias);
        if (cert != null) out.add(cert.getEncoded());
      }
    } else if (system) {
      TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      factory.init((KeyStore) null);
      for (TrustManager manager : factory.getTrustManagers()) {
        if (!(manager instanceof X509TrustManager)) continue;
        for (X509Certificate cert : ((X509TrustManager) manager).getAcceptedIssuers()) out.add(cert.getEncoded());
      }
    }
    return out;
  }

  private static synchronized List<byte[]> shippedCertificates() throws IOException {
    if (shipped == null) {
      InputStream in = ZephyrTls.class.getResourceAsStream(SHIPPED_RESOURCE);
      if (in == null) throw new ZephyrError("The shipped root certificates are missing: zephyr/common/" + SHIPPED_RESOURCE);
      try {
        shipped = parseCertificates(readAll(in, -1), "the shipped root certificates");
      } finally {
        in.close();
      }
    }
    return shipped;
  }

  /** The certificates of a PEM text, DER, in their order; throws when a block is not a certificate. */
  private static synchronized List<byte[]> parseCertificates(byte[] pem, String source) {
    List<byte[]> out = new ArrayList<byte[]>();
    String text;
    try {
      text = new String(pem, "ISO-8859-1"); // one char per byte; the blocks are ASCII
    } catch (IOException e) {
      throw error("Cannot read " + source, e);
    }
    CertificateFactory factory;
    try {
      factory = CertificateFactory.getInstance("X.509");
    } catch (Exception e) {
      throw error("No X.509 certificate factory", e);
    }
    int pos = 0;
    while (true) {
      int begin = text.indexOf(BEGIN, pos);
      if (begin < 0) break;
      int end = text.indexOf(END, begin + BEGIN.length());
      if (end < 0) throw new ZephyrError("Certificate " + (out.size() + 1) + " of " + source + " has no end line");
      try {
        byte[] der = base64Decode(text, begin + BEGIN.length(), end);
        Certificate cert = factory.generateCertificate(new ByteArrayInputStream(der));
        out.add(cert.getEncoded());
      } catch (Exception e) {
        throw error("Certificate " + (out.size() + 1) + " of " + source + " is not a certificate", e);
      }
      pos = end + END.length();
    }
    return out;
  }

  /** Decodes base64 between from and to; whitespace is skipped, anything else outside the alphabet is an error. */
  private static synchronized byte[] base64Decode(String text, int from, int to) {
    StringBuilder chars = new StringBuilder(to - from);
    for (int i = from; i < to; i++) {
      char c = text.charAt(i);
      if (c == ' ' || c == '\t' || c == '\r' || c == '\n') continue;
      chars.append(c);
    }
    int n = chars.length();
    if (n == 0 || n % 4 != 0) throw new ZephyrError("Invalid base64 length " + n);
    ByteArrayOutputStream out = new ByteArrayOutputStream(n / 4 * 3);
    for (int i = 0; i < n; i += 4) {
      int[] v = new int[4];
      int pad = 0;
      for (int j = 0; j < 4; j++) {
        char c = chars.charAt(i + j);
        if (c == '=') {
          if (i + 4 != n || j < 2) throw new ZephyrError("Invalid base64 padding");
          pad++;
          v[j] = 0;
        } else {
          if (pad > 0) throw new ZephyrError("Invalid base64 padding");
          v[j] = BASE64.indexOf(c);
          if (v[j] < 0) throw new ZephyrError("Invalid base64 character");
        }
      }
      int triple = (v[0] << 18) | (v[1] << 12) | (v[2] << 6) | v[3];
      out.write(triple >> 16);
      if (pad < 2) out.write((triple >> 8) & 0xff);
      if (pad < 1) out.write(triple & 0xff);
    }
    return out.toByteArray();
  }

  /** Encodes base64 with lines of 64 characters, each ended by a newline. */
  private static synchronized String base64Encode(byte[] data) {
    StringBuilder out = new StringBuilder((data.length + 2) / 3 * 4 + data.length / 48 + 2);
    int line = 0;
    for (int i = 0; i < data.length; i += 3) {
      int b0 = data[i] & 0xff;
      int b1 = i + 1 < data.length ? data[i + 1] & 0xff : 0;
      int b2 = i + 2 < data.length ? data[i + 2] & 0xff : 0;
      int triple = (b0 << 16) | (b1 << 8) | b2;
      out.append(BASE64.charAt((triple >> 18) & 63));
      out.append(BASE64.charAt((triple >> 12) & 63));
      out.append(i + 1 < data.length ? BASE64.charAt((triple >> 6) & 63) : '=');
      out.append(i + 2 < data.length ? BASE64.charAt(triple & 63) : '=');
      line += 4;
      if (line == PEM_LINE) {
        out.append('\n');
        line = 0;
      }
    }
    if (line > 0) out.append('\n');
    return out.toString();
  }

  private static synchronized byte[] writePem(List<byte[]> certs) throws IOException {
    StringBuilder out = new StringBuilder();
    for (byte[] der : certs) {
      out.append(BEGIN).append('\n').append(base64Encode(der)).append(END).append('\n');
    }
    return out.toString().getBytes("US-ASCII");
  }

  /** Reads a stream to its end; with a limit of 0 or more, more bytes than the limit is an error. */
  private static synchronized byte[] readAll(InputStream in, int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
      out.write(buffer, 0, n);
      if (limit >= 0 && out.size() > limit) throw new IOException("more than " + limit + " bytes");
    }
    return out.toByteArray();
  }

  /** The directory of the bundle: on Android in java.io.tmpdir, elsewhere a new one of this process. */
  private static synchronized File directory() throws IOException {
    if (directory != null && directory.isDirectory()) return directory;
    File dir;
    if (isAndroid()) {
      dir = new File(System.getProperty("java.io.tmpdir"), "zephyr-java-tls");
      if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
    } else {
      dir = null;
      for (int i = 0; i < 100 && dir == null; i++) {
        File candidate = File.createTempFile("zephyr-java-tls-", "");
        if (!candidate.delete()) throw new IOException("cannot delete " + candidate);
        if (candidate.mkdir()) dir = candidate; // a name that existed meanwhile is never used
      }
      if (dir == null) throw new IOException("cannot create a directory in " + System.getProperty("java.io.tmpdir"));
      dir.deleteOnExit();
      new File(dir, "ca-bundle.pem").deleteOnExit(); // deleted before the directory (reverse order)
    }
    directory = dir;
    return dir;
  }

  /** The empty directory SSL_CERT_DIR names, so that OpenSSL adds no directory of its own. */
  private static synchronized File emptyDirectory(File dir) throws IOException {
    File empty = new File(dir, "empty");
    if (!empty.isDirectory()) {
      if (!empty.mkdir()) throw new IOException("cannot create " + empty);
      if (!isAndroid()) empty.deleteOnExit();
    }
    File[] entries = empty.listFiles();
    if (entries == null) throw new IOException("cannot list " + empty);
    for (File entry : entries) {
      if (!entry.delete()) throw new IOException(empty + " is not empty and " + entry + " cannot be deleted");
    }
    return empty;
  }

  private static synchronized void writeAtomically(File target, byte[] data) throws IOException {
    File tmp = File.createTempFile(target.getName() + ".", ".tmp", target.getParentFile());
    boolean done = false;
    try {
      FileOutputStream out = new FileOutputStream(tmp);
      try {
        out.write(data);
        out.getFD().sync();
      } finally {
        out.close();
      }
      if (!tmp.renameTo(target)) throw new IOException("cannot rename " + tmp + " to " + target);
      done = true;
    } finally {
      if (!done) tmp.delete();
    }
  }
}
