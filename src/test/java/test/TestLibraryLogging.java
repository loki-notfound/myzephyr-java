package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zephyr.common.ZephyrError;
import zephyr.common.ZephyrRpcConnection;
import zephyr.common.ZephyrRpcError;
import zephyr.common.ZephyrUtils;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrTxConfig;
import zephyr.wallet.model.ZephyrWalletConfig;

/**
 * The library's own logging prints none of a set of planted secret values: ZephyrWalletFull at
 * java.util.logging FINE, and ZephyrRpcConnection at ZephyrUtils log levels 2 and 3 (System.out)
 * with its debug stack traces switched on (System.err). No node: an in-memory wallet and a local
 * HTTP server that answers with planted secrets.
 */
public class TestLibraryLogging {

  // planted values: none may appear in anything the library prints
  private static final String PASSWORD = "planted-password-71c3";
  private static final String SEED = "planted seed words 5d2e";
  private static final String SEED_OFFSET = "planted-offset-0b9f";
  private static final String SPEND_KEY = "planted-spendkey-a6e1";
  private static final String VIEW_KEY = "planted-viewkey-3f04";
  private static final String TX_KEY = "planted-txkey-c812";
  private static final String QUERY_KEY = "planted-mnemonic-from-query-key-94aa";
  private static final String TX_METADATA = "planted-txmetadata-e17d";
  private static final String SIGNED_TXSET = "planted-signedtxset-2b6c";
  private static final String MULTISIG_INFO = "planted-multisiginfo-8d30";
  private static final String URI_SECRET = "planted-uri-password-4c57";
  private static final String DESTINATION = "planted-destination-address-60fe";
  private static final String NOTE = "planted-note-d9a2";
  private static final List<String> PLANTED = Arrays.asList(PASSWORD, SEED, SEED_OFFSET, SPEND_KEY, VIEW_KEY, TX_KEY, QUERY_KEY, TX_METADATA, SIGNED_TXSET, MULTISIG_INFO, URI_SECRET, DESTINATION, NOTE);

  private final List<LogRecord> records = new CopyOnWriteArrayList<LogRecord>();
  private final Handler capture = new Handler() {
    @Override public void publish(LogRecord record) { records.add(record); }
    @Override public void flush() { }
    @Override public void close() { }
  };
  private Logger walletLogger;
  private Level walletLevel;
  private PrintStream out;
  private PrintStream err;
  private final ByteArrayOutputStream printed = new ByteArrayOutputStream();

  @BeforeEach
  public void capture() {
    walletLogger = Logger.getLogger(ZephyrWalletFull.class.getName());
    walletLevel = walletLogger.getLevel();
    walletLogger.setLevel(Level.ALL);
    capture.setLevel(Level.ALL);
    walletLogger.addHandler(capture);
    out = System.out;
    err = System.err;
    PrintStream both = new PrintStream(new OutputStream() {
      @Override public synchronized void write(int b) { printed.write(b); }
      @Override public synchronized void write(byte[] b, int off, int len) { printed.write(b, off, len); }
    }, true);
    System.setOut(both);
    System.setErr(both);
  }

  @AfterEach
  public void release() {
    System.setOut(out);
    System.setErr(err);
    walletLogger.removeHandler(capture);
    walletLogger.setLevel(walletLevel);
    ZephyrUtils.setLogLevel(0);
  }

  /** Everything the library printed or logged, as one text. */
  private String everything() {
    StringBuilder text = new StringBuilder(new String(printed.toByteArray(), StandardCharsets.UTF_8));
    for (LogRecord record : records) {
      text.append('\n').append(record.getMessage());
      if (record.getParameters() != null) for (Object parameter : record.getParameters()) text.append('\n').append(parameter);
      if (record.getThrown() != null) text.append('\n').append(record.getThrown());
    }
    return text.toString();
  }

  private void assertNothingPlanted() {
    String text = everything();
    List<String> found = new ArrayList<String>();
    for (String planted : PLANTED) if (text.contains(planted)) found.add(planted);
    assertEquals(new ArrayList<String>(), found, "planted values in the library's output:\n" + text);
  }

  /**
   * ZephyrWalletFull at FINE: createTxs and sweepUnlocked with a config that carries a planted
   * destination and note (both fail on the offline wallet, after the point where java.4 logged the
   * config).
   */
  @Test
  public void walletAtFineLogsNoTransactionConfig() {
    ZephyrWalletFull wallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig().setPassword(PASSWORD).setNetworkType(ZephyrNetworkType.MAINNET));
    try {
      ZephyrTxConfig config = new ZephyrTxConfig().setAccountIndex(0).setAddress(DESTINATION).setAmount(BigInteger.ONE).setNote(NOTE).setRelay(false);
      assertThrows(ZephyrError.class, () -> wallet.createTxs(config));
      assertThrows(ZephyrError.class, () -> wallet.sweepUnlocked(new ZephyrTxConfig().setAccountIndex(0).setAddress(DESTINATION).setNote(NOTE).setRelay(false)));
    } finally {
      wallet.close();
    }
    assertNothingPlanted();
  }

  /**
   * ZephyrRpcConnection at log level 3 with debug stack traces: JSON, path and binary requests
   * whose bodies carry planted secrets, answered by a server whose responses carry planted
   * secrets (nested as well), and a URI with a planted password in its user information.
   */
  @Test
  public void rpcConnectionAtLevels2And3PrintsNoSecrets() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      byte[] body;
      if (exchange.getRequestURI().getPath().endsWith(".bin")) {
        body = ZephyrUtils.mapToBinary(new HashMap<String, Object>());
      } else {
        body = ("{\"jsonrpc\":\"2.0\",\"id\":\"0\",\"result\":{\"key\":\"" + QUERY_KEY + "\",\"seed\":\"" + SEED + "\",\"tx_key\":\"" + TX_KEY + "\","
            + "\"tx_metadata_list\":[\"" + TX_METADATA + "\"],\"signed_txset\":\"" + SIGNED_TXSET + "\",\"multisig_info\":\"" + MULTISIG_INFO + "\","
            + "\"nested\":[{\"spendkey\":\"" + SPEND_KEY + "\",\"viewkey\":\"" + VIEW_KEY + "\"}],\"status\":\"OK\"}}").getBytes(StandardCharsets.UTF_8);
      }
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String uri = "http://127.0.0.1:" + server.getAddress().getPort();
      ZephyrUtils.setLogLevel(3);
      ZephyrRpcConnection connection = new ZephyrRpcConnection(uri);
      connection.setPrintStackTrace(true);
      Map<String, Object> params = new LinkedHashMap<String, Object>();
      params.put("password", PASSWORD);
      params.put("seed", SEED);
      params.put("seed_offset", SEED_OFFSET);
      params.put("Spendkey", SPEND_KEY); // names match without regard to case
      params.put("nested", Arrays.asList(new HashMap<String, Object>() {{ put("viewkey", VIEW_KEY); put("tx_key", TX_KEY); }}));
      Map<String, Object> response = connection.sendJsonRequest("restore_deterministic_wallet", params);
      assertEquals(QUERY_KEY, ((Map<?, ?>) response.get("result")).get("key"), "the caller gets the response unchanged");
      connection.sendPathRequest("some_path", params);
      Map<String, Object> binaryParams = new HashMap<String, Object>();
      binaryParams.put("password", PASSWORD);
      connection.sendBinaryRequest("get_blocks_by_height.bin", binaryParams);

      // a URI with credentials in it: the request may fail, the lines before it are printed
      ZephyrRpcConnection withUserInfo = new ZephyrRpcConnection("http://user:" + URI_SECRET + "@127.0.0.1:" + server.getAddress().getPort());
      try {
        withUserInfo.sendJsonRequest("get_version");
      } catch (ZephyrError e) {
        // the request's outcome does not matter here
      }
      System.out.println(withUserInfo); // toString()
      ZephyrUtils.setLogLevel(0);
    } finally {
      server.stop(0);
    }
    String text = everything();
    assertTrue(text.contains("restore_deterministic_wallet") && text.contains("Received json response") && text.contains("Sending binary request"), "the requests were logged:\n" + text);
    assertTrue(text.contains("<redacted>"), "redacted values are marked:\n" + text);
    assertNothingPlanted();
  }

  /** ZephyrRpcError.toString() redacts the same names as the logging, in nested values too. */
  @Test
  public void rpcErrorToStringRedactsTheSameNames() {
    Map<String, Object> params = new LinkedHashMap<String, Object>();
    params.put("key", QUERY_KEY);
    params.put("tx_metadata", TX_METADATA);
    params.put("list", Arrays.asList(new HashMap<String, Object>() {{ put("multisig_info", MULTISIG_INFO); put("password", PASSWORD); }}));
    System.out.println(new ZephyrRpcError("failed", -1, "method", params));
    assertNothingPlanted();
  }
}
