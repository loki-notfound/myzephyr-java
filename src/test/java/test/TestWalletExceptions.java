package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import zephyr.common.ZephyrError;
import zephyr.common.ZephyrRpcConnection;
import zephyr.common.ZephyrUtils;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.daemon.model.ZephyrTx;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrOutputQuery;
import zephyr.wallet.model.ZephyrOutputWallet;
import zephyr.wallet.model.ZephyrTransferQuery;
import zephyr.wallet.model.ZephyrTxWallet;
import zephyr.wallet.model.ZephyrTxQuery;
import zephyr.wallet.model.ZephyrWalletConfig;
import zephyr.wallet.model.ZephyrWalletListener;
import zephyr.wallet.model.ZephyrWalletListenerI;

/**
 * Every failure of a call of {@link ZephyrWalletFull} and of {@link ZephyrUtils} is a
 * {@link ZephyrError}: on a closed wallet, for every public method by reflection, and for the
 * library's own checks, which threw {@code AssertionError}, {@code Error} or a plain
 * {@code RuntimeException} up to java.4. No node: in-memory wallets only.
 */
public class TestWalletExceptions {

  /**
   * The public methods of a closed wallet that do not throw: close() of a closed wallet has no
   * effect (the wallet is closed already) and isClosed() reports it. Every other method throws
   * ZephyrError("Wallet is closed").
   */
  private static final Set<String> RETURN_ON_A_CLOSED_WALLET = new HashSet<String>(Arrays.asList(
      "close()", "close(boolean)", "isClosed()"));

  private final List<ZephyrWalletFull> opened = new ArrayList<ZephyrWalletFull>();

  @AfterEach
  public void closeWallets() {
    for (ZephyrWalletFull wallet : opened) {
      try {
        wallet.close();
      } catch (RuntimeException e) {
        // the test's own outcome counts
      }
    }
  }

  private ZephyrWalletFull inMemoryWallet() {
    ZephyrWalletFull wallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig().setPassword("exceptions-test").setNetworkType(ZephyrNetworkType.MAINNET));
    opened.add(wallet);
    return wallet;
  }

  // ------------------------------ closed wallet ------------------------------

  /**
   * Calls every public instance method of ZephyrWalletFull (its own, the inherited ones and the
   * interface's default method) on a closed wallet, with plain non-null arguments: each throws
   * ZephyrError("Wallet is closed") except the methods of {@link #RETURN_ON_A_CLOSED_WALLET}.
   */
  @Test
  public void everyPublicMethodOfAClosedWalletThrowsZephyrError() throws Exception {
    ZephyrWalletFull wallet = inMemoryWallet();
    wallet.close();
    List<String> failures = new ArrayList<String>();
    Set<String> returned = new HashSet<String>();
    int called = 0;
    for (Method method : publicInstanceMethods()) {
      String name = signature(method);
      Throwable thrown = null;
      try {
        method.invoke(wallet, arguments(method));
      } catch (InvocationTargetException e) {
        thrown = e.getCause();
      }
      called++;
      if (RETURN_ON_A_CLOSED_WALLET.contains(name)) {
        if (thrown != null) failures.add(name + ": threw " + thrown.getClass().getName() + ": " + thrown.getMessage());
        else returned.add(name);
      } else if (thrown == null) {
        failures.add(name + ": returned normally");
      } else if (!(thrown instanceof ZephyrError)) {
        failures.add(name + ": " + thrown.getClass().getName() + ": " + thrown.getMessage());
      } else if (!"Wallet is closed".equals(thrown.getMessage())) {
        failures.add(name + ": ZephyrError(\"" + thrown.getMessage() + "\")");
      }
    }
    assertEquals(Collections.emptyList(), failures, "of " + called + " public methods");
    assertEquals(RETURN_ON_A_CLOSED_WALLET, returned);
    assertTrue(called > 150, "only " + called + " public methods found");
  }

  /** The public instance methods of ZephyrWalletFull without those of Object, in a stable order. */
  private static List<Method> publicInstanceMethods() {
    Map<String, Method> methods = new TreeMap<String, Method>();
    for (Method method : ZephyrWalletFull.class.getMethods()) {
      if (Modifier.isStatic(method.getModifiers()) || method.getDeclaringClass() == Object.class) continue;
      methods.put(signature(method), method);
    }
    return new ArrayList<Method>(methods.values());
  }

  private static String signature(Method method) {
    StringBuilder signature = new StringBuilder(method.getName()).append('(');
    Class<?>[] types = method.getParameterTypes();
    for (int i = 0; i < types.length; i++) signature.append(i == 0 ? "" : ", ").append(types[i].getSimpleName());
    return signature.append(')').toString();
  }

  /** Plain non-null arguments: zero, false, "", empty collections and arrays, empty model objects. */
  private static Object[] arguments(Method method) throws Exception {
    Class<?>[] types = method.getParameterTypes();
    Object[] args = new Object[types.length];
    for (int i = 0; i < types.length; i++) args[i] = argument(types[i]);
    return args;
  }

  private static Object argument(Class<?> type) throws Exception {
    if (type == boolean.class || type == Boolean.class) return false;
    if (type == int.class || type == Integer.class) return 0;
    if (type == long.class || type == Long.class) return 0L;
    if (type == double.class || type == Double.class) return 0.0;
    if (type == String.class) return "";
    if (type == BigInteger.class) return BigInteger.ONE;
    if (type.isArray()) return java.lang.reflect.Array.newInstance(type.getComponentType(), 0);
    if (type.isAssignableFrom(ArrayList.class)) return new ArrayList<Object>(); // List, Collection
    if (type.isEnum()) return type.getEnumConstants()[0];
    if (type == ZephyrWalletListenerI.class) return new ZephyrWalletListener();
    if (type == ZephyrRpcConnection.class) return new ZephyrRpcConnection("http://127.0.0.1:1");
    return type.getConstructor().newInstance();
  }

  // ------------------------- the library's own checks ------------------------

  /**
   * Checks of the wallet that threw AssertionError or a plain RuntimeException up to java.4, and
   * nulls that reached the caller as NullPointerException: each a ZephyrError now, with the message
   * it had. Every case runs; the failures are reported together.
   */
  @Test
  public void walletChecksThrowZephyrError() {
    ZephyrWalletFull wallet = inMemoryWallet();
    List<String> failures = new ArrayList<String>();
    check(failures, "ZephyrWalletFull.getNewKeyImagesFromLastImport() not implemented", wallet::getNewKeyImagesFromLastImport);
    check(failures, "ZephyrWalletFull.tagAccounts() not implemented", () -> wallet.tagAccounts("tag", Arrays.asList(0)));
    check(failures, "ZephyrWalletFull.untagAccounts() not implemented", () -> wallet.untagAccounts(Arrays.asList(0)));
    check(failures, "ZephyrWalletFull.getAccountTags() not implemented", wallet::getAccountTags);
    check(failures, "ZephyrWalletFull.setAccountTagLabel() not implemented", () -> wallet.setAccountTagLabel("tag", "label"));

    // a query whose tx query belongs to another query (each setter links both ways, so the second
    // link replaces the first one's back reference)
    ZephyrOutputQuery outputQuery = new ZephyrOutputQuery();
    outputQuery.setTxQuery(new ZephyrTxQuery());
    outputQuery.getTxQuery().setOutputQuery(new ZephyrOutputQuery());
    check(failures, "Output query's tx query must be circular reference or null", () -> wallet.getOutputs(outputQuery));
    ZephyrTransferQuery transferQuery = new ZephyrTransferQuery();
    transferQuery.setTxQuery(new ZephyrTxQuery());
    transferQuery.getTxQuery().setTransferQuery(new ZephyrTransferQuery());
    check(failures, "Transfer query's tx query must be circular reference or null", () -> wallet.getTransfers(transferQuery));
    check(failures, "Transfer query's tx query must be circular reference or null", () -> wallet.getIncomingTransfers(transferQuery));

    // a null where the call needs a value (the message is the runtime's)
    check(failures, null, () -> wallet.signMessage("message", null, 0, 0));
    check(failures, null, () -> wallet.scanTxs(null));
    check(failures, null, () -> wallet.getTxNotes(null));
    check(failures, null, () -> wallet.getOutputAssetType(new ZephyrOutputWallet()));
    check(failures, "Must provide tx hashes", () -> wallet.getTxs((String[]) null));
    check(failures, "Must provide a transaction to relay", () -> wallet.relayTx((ZephyrTxWallet) null));
    check(failures, "Must provide transactions to relay", () -> wallet.relayTxs((List<ZephyrTxWallet>) null));
    check(failures, "Must provide multisig hexes", () -> wallet.importMultisigHex((String[]) null));
    assertEquals(Collections.emptyList(), failures);
  }

  /**
   * A null attribute key or value: up to java.4 the native side aborted the process
   * ({@code std::logic_error} outside its try block); now a ZephyrError before the native call. On
   * the code before the change this test ends the test JVM.
   */
  @Test
  public void nullAttributeIsRefused() {
    ZephyrWalletFull wallet = inMemoryWallet();
    List<String> failures = new ArrayList<String>();
    check(failures, "Must provide attribute key", () -> wallet.getAttribute(null));
    check(failures, "Must provide attribute key", () -> wallet.setAttribute(null, "value"));
    check(failures, "Must provide attribute value", () -> wallet.setAttribute("key", null));
    assertEquals(Collections.emptyList(), failures);
  }

  /**
   * Checks of ZephyrUtils that threw AssertionError or Error up to java.4: the same messages. Every
   * case runs; the failures are reported together.
   */
  @Test
  public void utilityChecksThrowZephyrError() {
    List<String> failures = new ArrayList<String>();
    check(failures, "Mnemonic phrase is not initialized", () -> ZephyrUtils.validateMnemonic(null));
    check(failures, "Mnemonic phrase is empty", () -> ZephyrUtils.validateMnemonic(""));
    check(failures, "Mnemonic phrase is 2 words but must be 25", () -> ZephyrUtils.validateMnemonic("two words"));
    check(failures, "Address is null", () -> ZephyrUtils.decodeAddress(null));
    check(failures, "value asserted as true but was false", () -> ZephyrUtils.validatePaymentId("123"));
    check(failures, "Log level must be an integer >= 0", () -> ZephyrUtils.setLogLevel(-1));
    check(failures, "Log level must be an integer >= 0", () -> ZephyrUtils.log(-1, "message"));

    // an address with one character changed: the checksum no longer matches
    String address = inMemoryWallet().getPrimaryAddress();
    String changed = address.substring(0, 20) + (address.charAt(20) == '2' ? '3' : '2') + address.substring(21);
    check(failures, "Address has invalid hash", () -> ZephyrUtils.decodeAddress(changed));

    // two versions of a transaction that disagree
    List<ZephyrTx> txs = new ArrayList<ZephyrTx>();
    txs.add(new ZephyrTx().setHash("tx").setFee(BigInteger.ONE));
    check(failures, "Cannot reconcile values 1 and 10 with config: [null, null, null]", () -> ZephyrUtils.mergeTx(txs, new ZephyrTx().setHash("tx").setFee(BigInteger.TEN)));

    // a null where the method needs a value (the message is the runtime's)
    check(failures, null, () -> ZephyrUtils.walletExists(null));
    check(failures, null, () -> ZephyrUtils.validateHex(null));
    check(failures, null, () -> ZephyrUtils.fromAtomicUnits(null));
    assertEquals(Collections.emptyList(), failures);
  }

  /** Runs the call; adds a line to the failures unless it throws ZephyrError with the message (any message for null). */
  private static void check(List<String> failures, String message, Executable call) {
    Throwable thrown = null;
    try {
      call.execute();
    } catch (Throwable t) {
      thrown = t;
    }
    String expected = "ZephyrError" + (message == null ? "" : "(\"" + message + "\")");
    if (!(thrown instanceof ZephyrError)) {
      failures.add("expected " + expected + ", got " + (thrown == null ? "no exception" : thrown.getClass().getName() + ": " + thrown.getMessage()));
    } else if (message != null && !message.equals(thrown.getMessage())) {
      failures.add("expected " + expected + ", got ZephyrError(\"" + thrown.getMessage() + "\")");
    }
  }
}
