package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.opentest4j.TestAbortedException;
import zephyr.common.ZephyrError;
import zephyr.common.ZephyrUtils;
import zephyr.daemon.ZephyrDaemonRpc;
import zephyr.daemon.model.ZephyrBlockHeader;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.daemon.model.ZephyrPricingRecord;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrAddressType;
import zephyr.wallet.model.ZephyrAssets;
import zephyr.wallet.model.ZephyrBalance;
import zephyr.wallet.model.ZephyrCheckTx;
import zephyr.wallet.model.ZephyrDecodedAddress;
import zephyr.wallet.model.ZephyrDestination;
import zephyr.wallet.model.ZephyrIncomingTransfer;
import zephyr.wallet.model.ZephyrOutgoingTransfer;
import zephyr.wallet.model.ZephyrOutputWallet;
import zephyr.wallet.model.ZephyrTxConfig;
import zephyr.wallet.model.ZephyrTxWallet;
import zephyr.wallet.model.ZephyrWalletConfig;
import zephyr.wallet.model.ZephyrWalletListener;

/**
 * Integration tests with real funds on mainnet: a transfer between two test wallets, a check of the
 * tx key, a reserve mint (ZPH to ZRS), sweeps, and the return of everything but the fees.
 *
 * <p>Runs only with {@code zephyr.funds=true} and the properties {@code zephyr.daemon.uri},
 * {@code zephyr.test.password} and {@code zephyr.return.address}; every test skips otherwise.
 * Nothing is hardcoded: the return address is checked through its SHA-256 and never printed in
 * full; neither the node's uri nor the password nor a mnemonic is printed.</p>
 *
 * <p>Wallet A is the funding wallet of {@link TestZephyrIntegration}; wallet B is created in test 2.
 * Every destination is one of three addresses: A's primary address, B's primary address, or the
 * return address. After any failure no later test builds or relays a transaction; test 8 only
 * reads and records the state. The class is resumable: tests 1 and 2 pick up wallet B and the
 * transfer of test 2 from the wallets' history instead of sending again.</p>
 */
@TestMethodOrder(OrderAnnotation.class)
public class TestZephyrIntegrationFunds {

  private static final String FUNDS = System.getProperty("zephyr.funds");
  private static final String DAEMON_URI = System.getProperty("zephyr.daemon.uri");
  private static final String TEST_PASSWORD = System.getProperty("zephyr.test.password");
  private static final String RETURN_ADDRESS = System.getProperty("zephyr.return.address");

  /** SHA-256 (lower-case hex) of the owner's return address; the address itself stays out of the repository. */
  private static final String RETURN_ADDRESS_SHA256 = "7107d33056195a365f29669d300d7bc89bd17c042b4d095bea67d1a2698e8664";

  private static final String WALLET_A_PATH = "test_wallets/zephyr-java-r8";    // gitignored
  private static final String WALLET_B_PATH = "test_wallets/zephyr-java-r8-b";  // gitignored
  private static final String FUNDING_TX_HASH = "6fc92ffdc66bab069c78c77106579a6aa4a008bfba11d9bfad44f3cc9112afee";

  private static final BigInteger COIN = BigInteger.TEN.pow(12);
  private static final BigInteger FUNDED = COIN.multiply(BigInteger.valueOf(101));
  private static final BigInteger TRANSFER_AMOUNT = COIN.multiply(BigInteger.valueOf(10));
  private static final BigInteger MINT_AMOUNT = COIN.multiply(BigInteger.valueOf(50));
  private static final BigInteger MAX_FEE = COIN.divide(BigInteger.valueOf(100));      // 0.01 ZPH
  private static final BigInteger DUST = COIN.divide(BigInteger.valueOf(1000));        // 0.001 ZPH
  private static final BigInteger CONVERSION_STEP = BigInteger.TEN.pow(8);              // at most four decimals
  private static final long POLL_MS = 30000;
  private static final long WAIT_CEILING_MS = 45 * 60 * 1000;
  private static final String ZPH = ZephyrAssets.ZPH;
  private static final String ZRS = ZephyrAssets.ZRS;

  // shared state
  private static ZephyrDaemonRpc daemon;
  private static ZephyrWalletFull walletA;
  private static ZephyrWalletFull walletB;
  private static String addressA;
  private static String addressB;
  private static boolean returnAddressChecked = false;
  private static boolean stopped = false;
  private static int walletsOpen = 0;
  private static int maxWalletsOpen = 0;
  private static long startDaemonHeight = -1;

  // what the round sent: hash -> {test, source, destination}, per sending wallet
  private static final Map<String, String[]> roundTxsA = new LinkedHashMap<String, String[]>();
  private static final Map<String, String[]> roundTxsB = new LinkedHashMap<String, String[]>();
  private static String transferHash;                                    // test 2 (or found by test 1)
  private static BigInteger transferFee;
  private static final Map<String, BigInteger> sentToOwner = new LinkedHashMap<String, BigInteger>();
  private static final Map<String, BigInteger> feesPaid = new LinkedHashMap<String, BigInteger>();

  // ------------------------------- helpers ----------------------------------

  private interface Body { void run() throws Exception; }
  private interface Condition { boolean holds() throws Exception; }

  /** Runs a test body; any failure sets {@code stopped}, so no later test builds or relays. */
  private static void guarded(String name, Body body) throws Exception {
    try {
      body.run();
    } catch (TestAbortedException e) {
      throw e;
    } catch (Exception | Error e) {
      stopped = true;
      log("STOPPED in " + name + ": " + e);
      throw e;
    }
  }

  private static void assumeFunds() {
    Assumptions.assumeTrue("true".equals(FUNDS), "zephyr.funds is not true");
    Assumptions.assumeTrue(DAEMON_URI != null && !DAEMON_URI.isEmpty(), "zephyr.daemon.uri not set");
    Assumptions.assumeTrue(TEST_PASSWORD != null && !TEST_PASSWORD.isEmpty(), "zephyr.test.password not set");
    Assumptions.assumeTrue(RETURN_ADDRESS != null && !RETURN_ADDRESS.isEmpty(), "zephyr.return.address not set");
  }

  /** For every test that builds or relays: the properties, no earlier failure, the return address checked. */
  private static void assumeSending() {
    assumeFunds();
    Assumptions.assumeFalse(stopped, "an earlier test failed; nothing more is built or relayed");
    Assumptions.assumeTrue(returnAddressChecked, "the return address was not checked");
    Assumptions.assumeTrue(walletA != null, "wallet A is not open");
  }

  private static ZephyrDaemonRpc getDaemon() {
    if (daemon == null) daemon = new ZephyrDaemonRpc(DAEMON_URI);
    return daemon;
  }

  private static void log(String msg) {
    System.out.println("[R8b] " + msg);
  }

  private static void walletOpened() {
    walletsOpen++;
    maxWalletsOpen = Math.max(maxWalletsOpen, walletsOpen);
    log("wallets open: " + walletsOpen + " (max so far " + maxWalletsOpen + ")");
    assertTrue(walletsOpen <= 2, "more than two wallets open against the node");
  }

  /** The return address is never shown in full. */
  private static String show(String address) {
    if (address == null) return "null";
    if (address.equals(RETURN_ADDRESS)) return "RETURN(" + address.substring(0, 8) + "..." + address.substring(address.length() - 6) + ")";
    return address;
  }

  private static String zph(BigInteger atomic) {
    if (atomic == null) return "null";
    return atomic + " (" + new BigDecimal(atomic).movePointLeft(12).toPlainString() + ")";
  }

  private static String sha256(String s) throws Exception {
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder();
    for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
    return sb.toString();
  }

  private static BigInteger bal(ZephyrWalletFull w, String asset) {
    return w.getBalance().get(asset);
  }

  private static BigInteger unl(ZephyrWalletFull w, String asset) {
    return w.getUnlockedBalance().get(asset);
  }

  private static void printBalances(String label) {
    StringBuilder sb = new StringBuilder("balances " + label + ":");
    for (Object[] entry : new Object[][] { { "A", walletA }, { "B", walletB } }) {
      ZephyrWalletFull w = (ZephyrWalletFull) entry[1];
      if (w == null) { sb.append("\n  ").append(entry[0]).append(": not open"); continue; }
      ZephyrBalance b = w.getBalance();
      ZephyrBalance u = w.getUnlockedBalance();
      sb.append("\n  ").append(entry[0]).append(" (height ").append(w.getHeight()).append(")");
      for (String asset : new String[] { ZPH, ZRS }) {
        sb.append("  ").append(asset).append(" ").append(zph(b.get(asset))).append(" unlocked ").append(zph(u.get(asset)));
      }
    }
    log(sb.toString());
  }

  /** Every destination is one of the three known addresses, and here the expected one. */
  private static void assertDestination(ZephyrTxConfig config, String expected) {
    assertTrue(returnAddressChecked, "the return address was not checked");
    assertTrue(expected.equals(addressA) || expected.equals(addressB) || expected.equals(RETURN_ADDRESS), "expected destination is not one of the three");
    assertEquals(Integer.valueOf(0), config.getAccountIndex(), "account index");
    assertEquals(Boolean.TRUE, config.getRelay(), "relay");
    List<ZephyrDestination> destinations = config.getDestinations();
    assertNotNull(destinations, "destinations");
    assertEquals(1, destinations.size(), "one destination");
    for (ZephyrDestination d : destinations) {
      String a = d.getAddress();
      boolean known = a != null && (a.equals(addressA) || (addressB != null && a.equals(addressB)) || a.equals(RETURN_ADDRESS));
      assertTrue(known, "destination is not one of the three known addresses: " + show(a));
      assertTrue(a.equals(expected), "destination " + show(a) + " is not the expected " + show(expected));
    }
    log("destination check passed: " + show(expected) + ", " + config.getSourceCurrency() + " -> " + config.getDestinationCurrency()
        + (destinations.get(0).getAmount() == null ? ", sweep" : ", amount " + zph(destinations.get(0).getAmount())));
  }

  /** Polls every 30 s, syncing the given wallets, until the condition holds; fails after 45 minutes. */
  private static long waitFor(String what, Condition condition, ZephyrWalletFull... toSync) throws Exception {
    long start = System.currentTimeMillis();
    while (true) {
      for (ZephyrWalletFull w : toSync) w.sync();
      long elapsed = System.currentTimeMillis() - start;
      if (condition.holds()) {
        log("wait '" + what + "': reached after " + (elapsed / 1000) + " s, daemon height " + getDaemon().getHeight());
        return elapsed;
      }
      if (elapsed >= WAIT_CEILING_MS) fail("wait '" + what + "': not reached within " + (elapsed / 1000) + " s, daemon height " + getDaemon().getHeight());
      log("wait '" + what + "': " + (elapsed / 1000) + " s, daemon height " + getDaemon().getHeight());
      Thread.sleep(POLL_MS);
    }
  }

  private static boolean confirmed(ZephyrWalletFull w, List<String> hashes) {
    List<String> missing = new ArrayList<String>();
    List<ZephyrTxWallet> txs = w.getTxs(hashes, missing);
    if (!missing.isEmpty() || txs.size() != hashes.size()) return false;
    for (ZephyrTxWallet tx : txs) if (!Boolean.TRUE.equals(tx.isConfirmed())) return false;
    return true;
  }

  /** The chain's zeph_to_zephrsv with the 1 % conversion fee (HF 5 and later). */
  private static BigInteger zephToZrs(BigInteger amount, ZephyrPricingRecord pr) {
    BigInteger price = pr.getReserve().max(pr.getReserveMa());
    BigInteger rate = BigInteger.TEN.pow(24).divide(price);
    rate = rate.subtract(rate.divide(BigInteger.valueOf(100)));
    rate = rate.subtract(rate.mod(BigInteger.TEN.pow(4)));
    return amount.multiply(rate).divide(COIN);
  }

  /** Expected ZRS per height of the range; prints the table. */
  private static Map<Long, BigInteger> formulaTable(BigInteger amount, long from, long to) {
    Map<Long, BigInteger> table = new LinkedHashMap<Long, BigInteger>();
    StringBuilder sb = new StringBuilder("formula table for " + zph(amount) + " ZPH, heights " + from + " to " + to + ":");
    for (long h = from; h <= to; h++) {
      ZephyrBlockHeader header = getDaemon().getBlockHeaderByHeight(h);
      ZephyrPricingRecord pr = header.getPricingRecord();
      if (pr == null || pr.getReserve() == null || pr.getReserveMa() == null) {
        sb.append("\n  ").append(h).append(": no pricing record");
        continue;
      }
      BigInteger zrs = zephToZrs(amount, pr);
      table.put(h, zrs);
      sb.append("\n  ").append(h).append(": reserve ").append(pr.getReserve()).append(", reserve_ma ").append(pr.getReserveMa())
          .append(" -> ZRS ").append(zph(zrs));
    }
    log(sb.toString());
    return table;
  }

  private static BigInteger incomingAmount(ZephyrTxWallet tx, String asset) {
    BigInteger sum = BigInteger.ZERO;
    boolean found = false;
    if (tx.getIncomingTransfers() != null) {
      for (ZephyrIncomingTransfer t : tx.getIncomingTransfers()) {
        if (asset.equals(t.getAssetType())) { sum = sum.add(t.getAmount()); found = true; }
      }
    }
    return found ? sum : null;
  }

  private static void addTo(Map<String, BigInteger> map, String asset, BigInteger amount) {
    map.put(asset, map.containsKey(asset) ? map.get(asset).add(amount) : amount);
  }

  private static String summary(ZephyrTxWallet tx) {
    StringBuilder sb = new StringBuilder();
    sb.append(tx.getHash()).append(" height ").append(tx.getHeight()).append(" confirmed ").append(tx.isConfirmed())
        .append(" confirmations ").append(tx.getNumConfirmations()).append(" fee ").append(zph(tx.getFee()));
    ZephyrOutgoingTransfer out = tx.getOutgoingTransfer();
    if (out != null) {
      sb.append("\n    outgoing ").append(out.getAssetType()).append(" ").append(zph(out.getAmount()));
      if (out.getDestinations() != null) for (ZephyrDestination d : out.getDestinations()) sb.append("\n      to ").append(show(d.getAddress())).append(" ").append(zph(d.getAmount()));
    }
    if (tx.getIncomingTransfers() != null) {
      for (ZephyrIncomingTransfer in : tx.getIncomingTransfers()) {
        sb.append("\n    incoming ").append(in.getAssetType()).append(" ").append(zph(in.getAmount()))
            .append(" subaddress ").append(in.getAccountIndex()).append("/").append(in.getSubaddressIndex());
      }
    }
    return sb.toString();
  }

  private static void logTxs(String label, List<ZephyrTxWallet> txs) {
    log(label + ": " + txs.size() + " txs");
    for (ZephyrTxWallet tx : txs) log("  " + summary(tx));
  }

  private static class FundsRecorder extends ZephyrWalletListener {
    final List<String> outputsReceived = new CopyOnWriteArrayList<String>();
    final List<String> outputAssets = new CopyOnWriteArrayList<String>();
    final List<String> balanceAssets = new CopyOnWriteArrayList<String>();
    final List<String> balanceAssetNames = new CopyOnWriteArrayList<String>();
    @Override
    public void onOutputReceived(ZephyrOutputWallet output) {
      String asset = output.getAssetType();
      outputAssets.add(asset == null ? "null" : asset);
      outputsReceived.add("asset " + (asset == null ? "null" : "'" + asset + "'") + " amount " + output.getAmount()
          + " confirmed " + (output.getTx() == null ? null : output.getTx().isConfirmed())
          + " locked " + (output.getTx() == null ? null : output.getTx().isLocked()));
    }
    @Override
    public void onBalancesChanged(BigInteger newBalance, BigInteger newUnlockedBalance, String assetType) {
      balanceAssetNames.add(assetType == null ? "null" : assetType);
      balanceAssets.add("asset " + (assetType == null ? "null" : "'" + assetType + "'") + " " + newBalance + "/" + newUnlockedBalance);
    }
  }

  // -------------------------------- tests -----------------------------------

  @Test
  @Order(1)
  public void testFundsArrived() throws Exception {
    assumeFunds();
    guarded("testFundsArrived", () -> {
      // the return address: a mainnet subaddress with the expected hash, before anything else
      ZephyrDecodedAddress decoded = ZephyrUtils.decodeAddress(RETURN_ADDRESS);
      assertEquals(ZephyrNetworkType.MAINNET, decoded.getNetworkType(), "return address network");
      assertEquals(ZephyrAddressType.SUBADDRESS, decoded.getAddressType(), "return address type");
      assertEquals(RETURN_ADDRESS_SHA256, sha256(RETURN_ADDRESS), "return address hash");
      returnAddressChecked = true;
      log("return address checked: MAINNET SUBADDRESS, SHA-256 matches; " + show(RETURN_ADDRESS));

      startDaemonHeight = getDaemon().getHeight();
      log("daemon height at start: " + startDaemonHeight);
      assertTrue(ZephyrWalletFull.walletExists(WALLET_A_PATH), "wallet A does not exist");
      walletA = ZephyrWalletFull.openWallet(WALLET_A_PATH, TEST_PASSWORD, ZephyrNetworkType.MAINNET, DAEMON_URI);
      walletOpened();
      addressA = walletA.getPrimaryAddress();
      log("wallet A: " + addressA);
      assertFalse(addressA.equals(RETURN_ADDRESS), "wallet A is the return address");
      long start = System.currentTimeMillis();
      walletA.sync();
      log("A synced in " + (System.currentTimeMillis() - start) + " ms to height " + walletA.getHeight());
      printBalances("test 1, after sync");

      // resumable: the transfer of test 2 may be in A's history already (an outgoing 10 ZPH to another address)
      List<ZephyrTxWallet> earlier = new ArrayList<ZephyrTxWallet>();
      for (ZephyrTxWallet tx : walletA.getTxs()) {
        ZephyrOutgoingTransfer out = tx.getOutgoingTransfer();
        if (out == null || out.getDestinations() == null) continue;
        for (ZephyrDestination d : out.getDestinations()) {
          if (!addressA.equals(d.getAddress()) && TRANSFER_AMOUNT.equals(d.getAmount())) { earlier.add(tx); break; }
        }
      }
      assertTrue(earlier.size() <= 1, "more than one earlier 10 ZPH transfer in A's history: " + earlier.size());
      if (earlier.isEmpty()) {
        log("no earlier transfer in A's history: a fresh start");
        assertEquals(FUNDED, bal(walletA, ZPH), "A ZPH balance");
      } else {
        ZephyrTxWallet t = earlier.get(0);
        transferHash = t.getHash();
        transferFee = t.getFee();
        log("resumed: the test-2 transfer is in A's history: " + summary(t));
        assertEquals(FUNDED.subtract(TRANSFER_AMOUNT).subtract(transferFee), bal(walletA, ZPH), "A ZPH balance after the earlier transfer");
      }
      assertEquals(BigInteger.ZERO, bal(walletA, ZRS), "A ZRS balance");
      List<ZephyrTxWallet> txs = walletA.getTxs(FUNDING_TX_HASH);
      assertEquals(1, txs.size(), "funding tx in getTxs");
      ZephyrTxWallet funding = txs.get(0);
      log("funding tx: " + summary(funding));
      assertEquals(Boolean.TRUE, funding.isConfirmed(), "funding tx confirmed");
      assertTrue(funding.getNumConfirmations() != null && funding.getNumConfirmations() > 0, "funding tx confirmations " + funding.getNumConfirmations());
      assertEquals(FUNDED, incomingAmount(funding, ZPH), "incoming ZPH of the funding tx");
      for (ZephyrIncomingTransfer t : funding.getIncomingTransfers()) assertEquals(ZPH, t.getAssetType(), "asset of the incoming transfer");

      waitFor("A unlocked ZPH equals the balance", () -> unl(walletA, ZPH).equals(bal(walletA, ZPH)), walletA);
      printBalances("test 1, end");
    });
  }

  @Test
  @Order(2)
  public void testTransferToSecondWallet() throws Exception {
    assumeSending();
    guarded("testTransferToSecondWallet", () -> {
      if (ZephyrWalletFull.walletExists(WALLET_B_PATH)) {
        walletB = ZephyrWalletFull.openWallet(WALLET_B_PATH, TEST_PASSWORD, ZephyrNetworkType.MAINNET, DAEMON_URI);
        walletOpened();
        addressB = walletB.getPrimaryAddress();
        log("wallet B opened: " + addressB + ", restore height " + walletB.getRestoreHeight());
      } else {
        new File(WALLET_B_PATH).getParentFile().mkdirs();
        walletB = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
            .setPath(WALLET_B_PATH)
            .setPassword(TEST_PASSWORD)
            .setNetworkType(ZephyrNetworkType.MAINNET)
            .setServerUri(DAEMON_URI));
        walletOpened();
        addressB = walletB.getPrimaryAddress();
        log("wallet B created: " + addressB + ", restore height " + walletB.getRestoreHeight());
      }
      ZephyrDecodedAddress decodedB = ZephyrUtils.decodeAddress(addressB);
      assertEquals(ZephyrNetworkType.MAINNET, decodedB.getNetworkType());
      assertEquals(ZephyrAddressType.PRIMARY_ADDRESS, decodedB.getAddressType());
      assertFalse(addressB.equals(addressA) || addressB.equals(RETURN_ADDRESS), "wallet B address collides");
      walletB.save();
      walletA.sync();
      long start = System.currentTimeMillis();
      walletB.sync();
      log("B synced in " + (System.currentTimeMillis() - start) + " ms to height " + walletB.getHeight());
      printBalances("test 2, before");

      if (TRANSFER_AMOUNT.equals(bal(walletB, ZPH))) {
        log("resumed: transfer already done");
        assertNotNull(transferHash, "B holds 10 ZPH but test 1 found no transfer in A's history");
        roundTxsA.put(transferHash, new String[] { "test 2 transfer A -> B", ZPH, ZPH });
        addTo(feesPaid, ZPH, transferFee);
        List<ZephyrTxWallet> inB = walletB.getTxs(transferHash);
        assertEquals(1, inB.size(), "the transfer in B's getTxs");
        log("the transfer in B: " + summary(inB.get(0)));
        assertEquals(TRANSFER_AMOUNT, incomingAmount(inB.get(0), ZPH), "incoming ZPH of the transfer in B");
        log("send and listener assertions skipped: covered by the first run (tx " + transferHash + ")");
        waitFor("B unlocked ZPH equals 10", () -> TRANSFER_AMOUNT.equals(unl(walletB, ZPH)), walletB);
      } else {
        assertEquals(BigInteger.ZERO, bal(walletB, ZPH), "B ZPH before the transfer");
        assertTrue(transferHash == null, "A's history has a transfer but B does not hold 10 ZPH: " + transferHash);
        FundsRecorder recorder = new FundsRecorder();
        walletB.addListener(recorder);
        BigInteger aBefore = bal(walletA, ZPH);
        assertEquals(FUNDED, aBefore, "A ZPH before the transfer");
        ZephyrTxConfig config = new ZephyrTxConfig()
            .addDestination(addressB, TRANSFER_AMOUNT)
            .setSourceCurrency(ZPH).setDestinationCurrency(ZPH)
            .setAccountIndex(0)
            .setRelay(true);
        assertDestination(config, addressB);
        List<ZephyrTxWallet> txs = walletA.createTxs(config);
        assertEquals(1, txs.size(), "one tx");
        ZephyrTxWallet tx = txs.get(0);
        transferHash = tx.getHash();
        transferFee = tx.getFee();
        roundTxsA.put(tx.getHash(), new String[] { "test 2 transfer A -> B", ZPH, ZPH });
        addTo(feesPaid, ZPH, tx.getFee());
        log("TX test 2 (transfer A -> B, 10 ZPH): " + summary(tx));
        assertTrue(tx.getFee().signum() > 0 && tx.getFee().compareTo(MAX_FEE) < 0, "fee " + tx.getFee());
        ZephyrOutgoingTransfer out = tx.getOutgoingTransfer();
        assertNotNull(out, "outgoing transfer");
        assertEquals(ZPH, out.getAssetType(), "outgoing asset");
        assertEquals(1, out.getDestinations().size(), "one destination");
        assertEquals(addressB, out.getDestinations().get(0).getAddress(), "destination address");
        assertEquals(TRANSFER_AMOUNT, out.getDestinations().get(0).getAmount(), "destination amount");

        waitFor("B balance shows 10 ZPH", () -> TRANSFER_AMOUNT.equals(bal(walletB, ZPH)), walletB);
        log("B listener after the pool payment: onOutputReceived " + recorder.outputsReceived + "; onBalancesChanged " + recorder.balanceAssets);
        String hash = transferHash;
        waitFor("the transfer is confirmed in B", () -> confirmed(walletB, Collections.singletonList(hash)), walletB);
        log("B listener after confirmation: onOutputReceived " + recorder.outputsReceived + "; onBalancesChanged " + recorder.balanceAssets);
        assertTrue(recorder.outputAssets.contains(ZPH), "no onOutputReceived with asset ZPH: " + recorder.outputsReceived);
        assertTrue(recorder.balanceAssetNames.contains(ZPH), "no onBalancesChanged with asset ZPH: " + recorder.balanceAssets);
        waitFor("B unlocked ZPH equals 10", () -> TRANSFER_AMOUNT.equals(unl(walletB, ZPH)), walletB);
        log("B listener after unlock: onOutputReceived " + recorder.outputsReceived + "; onBalancesChanged " + recorder.balanceAssets);
        walletB.removeListener(recorder);
      }
      walletA.sync();
      printBalances("test 2, after");
      assertEquals(FUNDED.subtract(TRANSFER_AMOUNT).subtract(transferFee), bal(walletA, ZPH), "A ZPH after the transfer");
      assertEquals(BigInteger.ZERO, bal(walletA, ZRS), "A ZRS after the transfer");
      assertEquals(TRANSFER_AMOUNT, bal(walletB, ZPH), "B ZPH after the transfer");
    });
  }

  @Test
  @Order(3)
  public void testCheckTxKeyOnOurSend() throws Exception {
    assumeSending();
    Assumptions.assumeTrue(transferHash != null && walletB != null, "no transfer of test 2");
    guarded("testCheckTxKeyOnOurSend", () -> {
      try {
        String key = walletA.getTxKey(transferHash);
        log("getTxKey(" + transferHash + "): " + (key == null ? "null" : key.length() + " hex characters"));
        try {
          ZephyrCheckTx check = walletA.checkTxKey(transferHash, key, addressB);
          log("checkTxKey to B: good " + check.isGood() + ", received amount " + zph(check.getReceivedAmount())
              + ", confirmations " + check.getNumConfirmations() + ", in pool " + check.inTxPool());
          log("FINDING checkTxKey: received amount " + (BigInteger.ZERO.equals(check.getReceivedAmount())
              ? "is 0 (the core reads received_amount[\"ZEPH\"] only)" : "is " + check.getReceivedAmount() + ", not 0"));
        } catch (Exception e) {
          log("FINDING checkTxKey threw " + e.getClass().getName() + ": " + e.getMessage());
        }
      } catch (Exception e) {
        log("FINDING getTxKey threw " + e.getClass().getName() + ": " + e.getMessage());
      }
    });
  }

  @Test
  @Order(4)
  public void testMintReserve() throws Exception {
    assumeSending();
    guarded("testMintReserve", () -> {
      walletA.sync();
      waitFor("A unlocked ZPH equals the balance", () -> unl(walletA, ZPH).equals(bal(walletA, ZPH)), walletA);
      if (walletB != null) walletB.sync();
      printBalances("test 4, before");
      BigInteger zphBefore = bal(walletA, ZPH);
      BigInteger zrsBefore = bal(walletA, ZRS);
      assertEquals(BigInteger.ZERO, zrsBefore, "A ZRS before the mint");

      ZephyrTxConfig config = new ZephyrTxConfig()
          .addDestination(addressA, MINT_AMOUNT)
          .setSourceCurrency(ZPH).setDestinationCurrency(ZRS)
          .setAccountIndex(0)
          .setRelay(true);
      assertDestination(config, addressA);
      long h0 = getDaemon().getHeight();
      log("h0 (daemon height right before createTxs): " + h0 + ", wallet A height " + walletA.getHeight());
      List<ZephyrTxWallet> txs = walletA.createTxs(config);
      assertEquals(1, txs.size(), "one tx");
      ZephyrTxWallet tx = txs.get(0);
      roundTxsA.put(tx.getHash(), new String[] { "test 4 mint reserve", ZPH, ZRS });
      addTo(feesPaid, ZPH, tx.getFee());
      log("TX test 4 (mint reserve, 50 ZPH -> ZRS, to A): " + summary(tx));
      assertTrue(tx.getFee().signum() > 0 && tx.getFee().compareTo(MAX_FEE) < 0, "fee " + tx.getFee());
      assertNotNull(tx.getOutgoingTransfer(), "outgoing transfer");
      assertEquals(ZPH, tx.getOutgoingTransfer().getAssetType(), "outgoing asset");
      BigInteger minted = incomingAmount(tx, ZRS);
      assertNotNull(minted, "no incoming ZRS transfer on the returned tx");
      log("minted (incoming ZRS on the returned tx): " + zph(minted));

      waitFor("A ZRS positive and unlocked", () -> {
        BigInteger b = bal(walletA, ZRS);
        return b.signum() > 0 && unl(walletA, ZRS).equals(b);
      }, walletA);
      if (walletB != null) walletB.sync();
      printBalances("test 4, after");
      List<ZephyrTxWallet> confirmedTx = walletA.getTxs(tx.getHash());
      assertEquals(1, confirmedTx.size(), "mint tx in getTxs");
      long h1 = confirmedTx.get(0).getHeight();
      log("h1 (block of the mint tx): " + h1 + "; " + summary(confirmedTx.get(0)));

      BigInteger zrsAfter = bal(walletA, ZRS);
      Map<Long, BigInteger> table = formulaTable(MINT_AMOUNT, h0 - 2, h1);
      List<Long> matches = new ArrayList<Long>();
      for (Map.Entry<Long, BigInteger> e : table.entrySet()) if (e.getValue().equals(minted)) matches.add(e.getKey());
      log("minted " + zph(minted) + ", ZRS increase " + zph(zrsAfter.subtract(zrsBefore)) + ", formula matches at heights " + matches
          + " (expected h0 - 1 = " + (h0 - 1) + ")");
      log("implied rate (ZRS per ZPH, atomic per 10^12): " + minted.multiply(COIN).divide(MINT_AMOUNT));
      assertEquals(minted, zrsAfter.subtract(zrsBefore), "ZRS increase equals the minted amount");
      assertFalse(matches.isEmpty(), "the minted amount matches no height of the formula table");
      assertEquals(zphBefore.subtract(MINT_AMOUNT).subtract(tx.getFee()), bal(walletA, ZPH), "A ZPH decreased by 50 ZPH plus the fee");
    });
  }

  @Test
  @Order(5)
  public void testSweepBackFromB() throws Exception {
    assumeSending();
    Assumptions.assumeTrue(walletB != null, "wallet B is not open");
    guarded("testSweepBackFromB", () -> {
      walletA.sync();
      walletB.sync();
      printBalances("test 5, before");
      BigInteger bBefore = bal(walletB, ZPH);
      assertEquals(TRANSFER_AMOUNT, bBefore, "B ZPH before the sweep");
      assertEquals(TRANSFER_AMOUNT, unl(walletB, ZPH), "B unlocked ZPH before the sweep");

      ZephyrTxConfig config = new ZephyrTxConfig()
          .setAddress(RETURN_ADDRESS)
          .setSourceCurrency(ZPH).setDestinationCurrency(ZPH)
          .setAccountIndex(0)
          .setRelay(true);
      assertDestination(config, RETURN_ADDRESS);
      List<ZephyrTxWallet> txs = walletB.sweepUnlocked(config);
      assertFalse(txs.isEmpty(), "no tx from the sweep");
      BigInteger sent = BigInteger.ZERO;
      BigInteger fees = BigInteger.ZERO;
      for (ZephyrTxWallet tx : txs) {
        roundTxsB.put(tx.getHash(), new String[] { "test 5 sweep B -> owner", ZPH, ZPH });
        log("TX test 5 (sweep B -> owner, ZPH): " + summary(tx));
        assertEquals(ZPH, tx.getOutgoingTransfer().getAssetType(), "outgoing asset");
        assertTrue(tx.getFee().signum() > 0 && tx.getFee().compareTo(MAX_FEE) < 0, "fee " + tx.getFee());
        sent = sent.add(tx.getOutgoingTransfer().getAmount());
        fees = fees.add(tx.getFee());
      }
      addTo(sentToOwner, ZPH, sent);
      addTo(feesPaid, ZPH, fees);
      log("test 5: sent to the owner " + zph(sent) + " ZPH, fee " + zph(fees));
      assertEquals(bBefore, sent.add(fees), "sent plus fee equals B's 10 ZPH");
      walletB.sync();
      printBalances("test 5, after");
      assertEquals(BigInteger.ZERO, bal(walletB, ZPH), "B ZPH after the sweep");
    });
  }

  @Test
  @Order(6)
  public void testMintRemainder() throws Exception {
    assumeSending();
    guarded("testMintRemainder", () -> {
      walletA.sync();
      waitFor("A unlocked ZPH equals the balance", () -> unl(walletA, ZPH).equals(bal(walletA, ZPH)), walletA);
      if (walletB != null) walletB.sync();
      printBalances("test 6, before");
      BigInteger zphBefore = bal(walletA, ZPH);
      BigInteger zrsBefore = bal(walletA, ZRS);
      long h0 = getDaemon().getHeight();
      log("h0 (daemon height right before the mint): " + h0 + ", wallet A height " + walletA.getHeight());

      List<ZephyrTxWallet> txs;
      boolean fallback = false;
      BigInteger fallbackAmount = null;
      ZephyrTxConfig sweep = new ZephyrTxConfig()
          .setAddress(addressA)
          .setSourceCurrency(ZPH).setDestinationCurrency(ZRS)
          .setAccountIndex(0)
          .setRelay(true);
      assertDestination(sweep, addressA);
      try {
        txs = walletA.sweepUnlocked(sweep);
      } catch (ZephyrError e) {
        log("FINDING: sweepUnlocked ZPH -> ZRS refused: " + e.getMessage() + "; question R8-9; fallback to createTxs");
        fallback = true;
        BigInteger unlocked = unl(walletA, ZPH);
        fallbackAmount = unlocked.subtract(MAX_FEE);
        fallbackAmount = fallbackAmount.subtract(fallbackAmount.mod(CONVERSION_STEP));
        log("fallback amount: unlocked " + zph(unlocked) + " minus 0.01 ZPH, rounded down to 10^8: " + zph(fallbackAmount));
        assertTrue(fallbackAmount.signum() > 0, "fallback amount " + fallbackAmount);
        ZephyrTxConfig config = new ZephyrTxConfig()
            .addDestination(addressA, fallbackAmount)
            .setSourceCurrency(ZPH).setDestinationCurrency(ZRS)
            .setAccountIndex(0)
            .setRelay(true);
        assertDestination(config, addressA);
        txs = walletA.createTxs(config);
      }
      assertFalse(txs.isEmpty(), "no tx from the mint");
      BigInteger minted = BigInteger.ZERO;
      BigInteger converted = BigInteger.ZERO;
      BigInteger fees = BigInteger.ZERO;
      List<String> hashes = new ArrayList<String>();
      for (ZephyrTxWallet tx : txs) {
        hashes.add(tx.getHash());
        roundTxsA.put(tx.getHash(), new String[] { "test 6 mint remainder" + (fallback ? " (fallback)" : ""), ZPH, ZRS });
        log("TX test 6 (mint remainder, ZPH -> ZRS, to A" + (fallback ? ", fallback createTxs" : ", sweepUnlocked") + "): " + summary(tx));
        assertEquals(ZPH, tx.getOutgoingTransfer().getAssetType(), "outgoing asset");
        assertTrue(tx.getFee().signum() > 0 && tx.getFee().compareTo(MAX_FEE) < 0, "fee " + tx.getFee());
        BigInteger in = incomingAmount(tx, ZRS);
        assertNotNull(in, "no incoming ZRS transfer on the returned tx");
        minted = minted.add(in);
        converted = converted.add(tx.getOutgoingTransfer().getAmount());
        fees = fees.add(tx.getFee());
      }
      addTo(feesPaid, ZPH, fees);
      log("test 6: converted " + zph(converted) + " ZPH, fee " + zph(fees) + ", minted " + zph(minted) + " ZRS");

      waitFor("A ZRS increased and unlocked", () -> {
        BigInteger b = bal(walletA, ZRS);
        return b.compareTo(zrsBefore) > 0 && unl(walletA, ZRS).equals(b) && confirmed(walletA, hashes);
      }, walletA);
      if (walletB != null) walletB.sync();
      printBalances("test 6, after");
      long h1 = -1;
      for (ZephyrTxWallet tx : walletA.getTxs(hashes)) { h1 = Math.max(h1, tx.getHeight()); log("confirmed: " + summary(tx)); }
      if (!fallback) {
        Map<Long, BigInteger> table = formulaTable(converted, h0 - 2, h1);  // recorded, not asserted
        List<Long> matches = new ArrayList<Long>();
        for (Map.Entry<Long, BigInteger> e : table.entrySet()) if (e.getValue().equals(minted)) matches.add(e.getKey());
        log("test 6 formula matches at heights " + matches + " (h0 - 1 = " + (h0 - 1) + ")");
      }
      BigInteger zphAfter = bal(walletA, ZPH);
      BigInteger zrsAfter = bal(walletA, ZRS);
      log("test 6 accounting: ZPH before " + zph(zphBefore) + " - converted - fee = " + zph(zphBefore.subtract(converted).subtract(fees))
          + ", ZPH now " + zph(zphAfter) + "; ZRS increase " + zph(zrsAfter.subtract(zrsBefore)));
      assertEquals(minted, zrsAfter.subtract(zrsBefore), "ZRS increase equals the minted amount");
      BigInteger accountingDiff = zphBefore.subtract(converted).subtract(fees).subtract(zphAfter);
      log("test 6 accounting difference (before - converted - fee - now): " + accountingDiff);
      assertTrue(accountingDiff.abs().compareTo(CONVERSION_STEP) <= 0, "A ZPH after the mint off by " + accountingDiff);
      BigInteger zphBound = fallback ? MAX_FEE.add(CONVERSION_STEP) : DUST;
      assertTrue(zphAfter.compareTo(zphBound) < 0, "A ZPH after the mint " + zphAfter + " not below " + zphBound);
    });
  }

  @Test
  @Order(7)
  public void testReturnEverything() throws Exception {
    assumeSending();
    guarded("testReturnEverything", () -> {
      walletA.sync();
      waitFor("A unlocked ZPH equals the balance", () -> unl(walletA, ZPH).equals(bal(walletA, ZPH)), walletA);
      waitFor("A unlocked ZRS equals the balance", () -> unl(walletA, ZRS).equals(bal(walletA, ZRS)), walletA);
      if (walletB != null) walletB.sync();
      printBalances("test 7, before");
      BigInteger zrsBefore = bal(walletA, ZRS);
      assertTrue(zrsBefore.signum() > 0, "A holds no ZRS");
      List<String> hashes = new ArrayList<String>();

      // the ZRS goes back as ZRS
      ZephyrTxConfig zrsSweep = new ZephyrTxConfig()
          .setAddress(RETURN_ADDRESS)
          .setSourceCurrency(ZRS).setDestinationCurrency(ZRS)
          .setAccountIndex(0)
          .setRelay(true);
      assertDestination(zrsSweep, RETURN_ADDRESS);
      List<ZephyrTxWallet> txs = walletA.sweepUnlocked(zrsSweep);
      assertFalse(txs.isEmpty(), "no tx from the ZRS sweep");
      BigInteger sentZrs = BigInteger.ZERO;
      BigInteger feeZrs = BigInteger.ZERO;
      for (ZephyrTxWallet tx : txs) {
        hashes.add(tx.getHash());
        roundTxsA.put(tx.getHash(), new String[] { "test 7 return ZRS", ZRS, ZRS });
        log("TX test 7 (sweep A -> owner, ZRS): " + summary(tx));
        assertEquals(ZRS, tx.getOutgoingTransfer().getAssetType(), "outgoing asset");
        sentZrs = sentZrs.add(tx.getOutgoingTransfer().getAmount());
        feeZrs = feeZrs.add(tx.getFee());
      }
      addTo(sentToOwner, ZRS, sentZrs);
      addTo(feesPaid, ZRS, feeZrs);
      log("test 7: sent to the owner " + zph(sentZrs) + " ZRS, fee " + zph(feeZrs) + " ZRS");
      assertEquals(zrsBefore, sentZrs.add(feeZrs), "sent plus fee equals A's ZRS");

      // the ZPH remainder, only above the dust bound
      walletA.sync();
      BigInteger zphUnlocked = unl(walletA, ZPH);
      if (zphUnlocked.compareTo(DUST) > 0) {
        ZephyrTxConfig zphSweep = new ZephyrTxConfig()
            .setAddress(RETURN_ADDRESS)
            .setSourceCurrency(ZPH).setDestinationCurrency(ZPH)
            .setAccountIndex(0)
            .setRelay(true);
        assertDestination(zphSweep, RETURN_ADDRESS);
        List<ZephyrTxWallet> zphTxs = walletA.sweepUnlocked(zphSweep);
        BigInteger sentZph = BigInteger.ZERO;
        BigInteger feeZph = BigInteger.ZERO;
        for (ZephyrTxWallet tx : zphTxs) {
          hashes.add(tx.getHash());
          roundTxsA.put(tx.getHash(), new String[] { "test 7 return ZPH", ZPH, ZPH });
          log("TX test 7 (sweep A -> owner, ZPH): " + summary(tx));
          sentZph = sentZph.add(tx.getOutgoingTransfer().getAmount());
          feeZph = feeZph.add(tx.getFee());
        }
        addTo(sentToOwner, ZPH, sentZph);
        addTo(feesPaid, ZPH, feeZph);
        log("test 7: sent to the owner " + zph(sentZph) + " ZPH, fee " + zph(feeZph));
      } else {
        log("test 7: A's unlocked ZPH " + zph(zphUnlocked) + " is not above the dust bound; no ZPH sweep");
      }

      waitFor("the return txs are confirmed", () -> confirmed(walletA, hashes), walletA);
      printBalances("test 7, after");
      BigInteger zphLeft = bal(walletA, ZPH);
      BigInteger zrsLeft = bal(walletA, ZRS);
      StringBuilder sb = new StringBuilder("final table:");
      for (String asset : new String[] { ZPH, ZRS }) {
        sb.append("\n  ").append(asset).append(": sent to the owner ").append(zph(sentToOwner.get(asset)))
            .append(", fees paid ").append(zph(feesPaid.get(asset)))
            .append(", dust left in A ").append(zph(bal(walletA, asset)))
            .append(", in B ").append(walletB == null ? "n/a" : zph(bal(walletB, asset)));
      }
      log(sb.toString());
      assertTrue(zphLeft.compareTo(DUST) < 0, "A ZPH left " + zphLeft);
      assertTrue(zrsLeft.compareTo(DUST) < 0, "A ZRS left " + zrsLeft);
    });
  }

  @Test
  @Order(8)
  public void testFinalState() throws Exception {
    assumeFunds();
    Assumptions.assumeTrue(walletA != null, "wallet A is not open");
    log("test 8 (read-only), stopped flag: " + stopped);
    walletA.sync();
    if (walletB != null) walletB.sync();
    printBalances("test 8");
    for (Object[] entry : new Object[][] { { "A", walletA }, { "B", walletB } }) {
      ZephyrWalletFull w = (ZephyrWalletFull) entry[1];
      if (w == null) continue;
      log(entry[0] + " balance " + w.getBalance().toMap() + ", unlocked " + w.getUnlockedBalance().toMap());
      logTxs(entry[0] + " getTxs()", w.getTxs());
    }
    List<String> problems = new ArrayList<String>();
    for (Object[] entry : new Object[][] { { "A", walletA }, { "B", walletB } }) {
      ZephyrWalletFull w = (ZephyrWalletFull) entry[1];
      if (w == null) continue;
      ZephyrBalance unlocked = w.getUnlockedBalance();
      for (String asset : new TreeSet<String>(unlocked.getAssetTypes())) {
        if (unlocked.get(asset).compareTo(DUST) > 0) problems.add(entry[0] + " holds unlocked " + asset + " " + unlocked.get(asset));
      }
    }
    for (Object[] entry : new Object[][] { { "A", walletA, roundTxsA }, { "B", walletB, roundTxsB } }) {
      ZephyrWalletFull w = (ZephyrWalletFull) entry[1];
      @SuppressWarnings("unchecked")
      Map<String, String[]> round = (Map<String, String[]>) entry[2];
      if (w == null) continue;
      for (Map.Entry<String, String[]> e : round.entrySet()) {
        List<String> missing = new ArrayList<String>();
        List<ZephyrTxWallet> found = w.getTxs(Collections.singletonList(e.getKey()), missing);
        if (found.size() != 1) { problems.add(entry[0] + ": " + e.getValue()[0] + " " + e.getKey() + " not in getTxs"); continue; }
        ZephyrTxWallet tx = found.get(0);
        String source = e.getValue()[1];
        String dest = e.getValue()[2];
        String outAsset = tx.getOutgoingTransfer() == null ? null : tx.getOutgoingTransfer().getAssetType();
        log(entry[0] + " " + e.getValue()[0] + ": outgoing asset " + outAsset + ", incoming " + (dest.equals(source) ? "-" : dest + " " + zph(incomingAmount(tx, dest))));
        if (!source.equals(outAsset)) problems.add(entry[0] + ": " + e.getValue()[0] + " outgoing asset " + outAsset + ", expected " + source);
        if (!dest.equals(source) && incomingAmount(tx, dest) == null) problems.add(entry[0] + ": " + e.getValue()[0] + " has no incoming " + dest + " transfer");
      }
    }
    if (walletB != null && transferHash != null) {
      List<ZephyrTxWallet> found = walletB.getTxs(Collections.singletonList(transferHash), new ArrayList<String>());
      if (found.size() != 1 || incomingAmount(found.get(0), ZPH) == null) problems.add("B: the transfer of test 2 has no incoming ZPH transfer");
      else log("B test 2 transfer: incoming ZPH " + zph(incomingAmount(found.get(0), ZPH)));
    }
    log("test 8 problems: " + problems);
    assertTrue(problems.isEmpty(), "final state: " + problems);
  }

  @AfterAll
  public static void afterAll() {
    List<Throwable> errors = new ArrayList<Throwable>();
    for (ZephyrWalletFull w : new ZephyrWalletFull[] { walletB, walletA }) {
      if (w == null) continue;
      try {
        if (!w.isClosed()) { w.close(true); walletsOpen--; }
      } catch (Throwable t) { errors.add(t); }
    }
    if ("true".equals(FUNDS) && DAEMON_URI != null && !DAEMON_URI.isEmpty()) {
      try { log("daemon height at start " + startDaemonHeight + ", at end " + getDaemon().getHeight()); } catch (Throwable t) { errors.add(t); }
    }
    log("maximum wallets open at the same time: " + maxWalletsOpen + "; stopped: " + stopped);
    for (Throwable t : errors) log("cleanup error: " + t);
  }
}
