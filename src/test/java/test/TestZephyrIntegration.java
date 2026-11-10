package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import zephyr.common.ZephyrError;
import zephyr.common.ZephyrRpcConnection;
import zephyr.common.ZephyrTls;
import zephyr.common.ZephyrUtils;
import zephyr.daemon.ZephyrDaemonRpc;
import zephyr.daemon.model.ZephyrBlockHeader;
import zephyr.daemon.model.ZephyrDaemonInfo;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.daemon.model.ZephyrPricingRecord;
import zephyr.wallet.ZephyrWallet;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.ZephyrWalletRpc;
import zephyr.wallet.model.ZephyrAccount;
import zephyr.wallet.model.ZephyrAddressType;
import zephyr.wallet.model.ZephyrAssets;
import zephyr.wallet.model.ZephyrBalance;
import zephyr.wallet.model.ZephyrDecodedAddress;
import zephyr.wallet.model.ZephyrOutgoingTransfer;
import zephyr.wallet.model.ZephyrOutputWallet;
import zephyr.wallet.model.ZephyrReserveInfo;
import zephyr.wallet.model.ZephyrSubaddress;
import zephyr.wallet.model.ZephyrTransfer;
import zephyr.wallet.model.ZephyrTxWallet;
import zephyr.wallet.model.ZephyrWalletConfig;
import zephyr.wallet.model.ZephyrWalletListener;

/**
 * Integration tests against a Zephyr mainnet node, read-only: nothing is sent or relayed.
 *
 * <p>Configured by system properties only; nothing is hardcoded:</p>
 * <ul>
 *   <li>{@code zephyr.daemon.uri}: the node's RPC uri, required by every test (all skip without it);</li>
 *   <li>{@code zephyr.test.password}: the password of the persistent test wallet (tests 4 to 9, 11 and 12);</li>
 *   <li>{@code zephyr.wallet.rpc.bin}: a zephyr-wallet-rpc binary (test 11);</li>
 *   <li>{@code zephyr.view.address}, {@code zephyr.view.key}, {@code zephyr.view.height}: a view-only
 *       wallet with history (test 10);</li>
 *   <li>{@code zephyr.daemon.uri.tls}: the node's {@code https://} uri, for the TLS tests 13 to 15.
 *       They change the process environment ({@link ZephyrTls}) and run in a JVM of their own:
 *       {@code -Dtest='TestZephyrIntegration#testTls*'}; in a run where another test has opened
 *       a wallet they skip.</li>
 * </ul>
 *
 * <p>The tests run one after another; at most two wallets are open against the node at any time.
 * Neither the node's uri nor the password nor the mnemonic is printed.</p>
 */
@TestMethodOrder(OrderAnnotation.class)
public class TestZephyrIntegration {

  private static final String DAEMON_URI = System.getProperty("zephyr.daemon.uri");
  private static final String TEST_PASSWORD = System.getProperty("zephyr.test.password");
  private static final String WALLET_RPC_BIN = System.getProperty("zephyr.wallet.rpc.bin");
  private static final String VIEW_ADDRESS = System.getProperty("zephyr.view.address");
  private static final String VIEW_KEY = System.getProperty("zephyr.view.key");
  private static final String VIEW_HEIGHT = System.getProperty("zephyr.view.height");
  private static final String TLS_URI = System.getProperty("zephyr.daemon.uri.tls");

  private static final String WALLET_PATH = "test_wallets/zephyr-java-r8";  // gitignored
  private static final String WALLET_RPC_DIR = System.getProperty("user.home") + "/tmp/zephyr-java-r8/wallets";
  private static final Duration SYNC_TIMEOUT = Duration.ofMinutes(20);
  private static final long NEW_BLOCK_WAIT_MS = 420000;

  // shared state
  private static ZephyrDaemonRpc daemon;
  private static ZephyrWalletFull wallet;        // the persistent test wallet (test 4)
  private static ZephyrWalletFull dataWallet;    // the in-memory copy (test 9)
  private static ZephyrWalletFull viewWallet;    // the view-only wallet (test 10)
  private static ZephyrWalletRpc walletRpc;      // the zephyr-wallet-rpc client (test 11)
  private static long startDaemonHeight = -1;
  private static int walletsOpen = 0;
  private static int maxWalletsOpen = 0;

  // ------------------------------- helpers ----------------------------------

  private static void assumeDaemon() {
    Assumptions.assumeTrue(DAEMON_URI != null && !DAEMON_URI.isEmpty(), "zephyr.daemon.uri not set");
  }

  private static void assumeWallet() {
    assumeDaemon();
    Assumptions.assumeTrue(TEST_PASSWORD != null && !TEST_PASSWORD.isEmpty(), "zephyr.test.password not set");
    Assumptions.assumeTrue(wallet != null, "the test wallet of test 4 is not open");
  }

  /** The TLS tests: the https uri is given and no other test of this run has opened a wallet. */
  private static void assumeTls() {
    Assumptions.assumeTrue(TLS_URI != null && !TLS_URI.isEmpty(), "zephyr.daemon.uri.tls not set");
    Assumptions.assumeTrue(walletsOpen == 0 && maxWalletsOpen == 0 || tlsRun,
        "the TLS tests change the process environment and run on their own: -Dtest='TestZephyrIntegration#testTls*'");
    tlsRun = true;
  }

  private static boolean tlsRun = false;

  /** A random wallet with the https uri, one at a time. */
  private static ZephyrWalletFull createTlsWallet() {
    ZephyrWalletFull w = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
        .setNetworkType(ZephyrNetworkType.MAINNET)
        .setPassword("tls-test")
        .setServerUri(TLS_URI));
    walletOpened();
    return w;
  }

  private static ZephyrDaemonRpc getDaemon() {
    if (daemon == null) daemon = new ZephyrDaemonRpc(DAEMON_URI);
    return daemon;
  }

  private static void walletOpened() {
    walletsOpen++;
    maxWalletsOpen = Math.max(maxWalletsOpen, walletsOpen);
    log("wallets open: " + walletsOpen + " (max so far " + maxWalletsOpen + ")");
    assertTrue(walletsOpen <= 2, "more than two wallets open against the node");
  }

  private static void walletClosed() {
    walletsOpen--;
    log("wallets open: " + walletsOpen);
  }

  private static String coins(BigInteger atomicUnits) {
    return new BigDecimal(atomicUnits, 12).toPlainString();
  }

  private static void log(String msg) {
    System.out.println("[integration] " + msg);
  }

  /** git status --short of the working directory (the fork), one entry per line. */
  private static List<String> gitStatus() {
    try {
      Process p = new ProcessBuilder("git", "status", "--short", "--untracked-files=all").redirectErrorStream(true).start();
      List<String> lines = new ArrayList<String>();
      try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = in.readLine()) != null) lines.add(line);
      }
      p.waitFor();
      return lines;
    } catch (Exception e) {
      return Collections.singletonList("git status failed: " + e.getMessage());
    }
  }

  private static class SyncRecorder extends ZephyrWalletListener {
    final List<long[]> progress = new CopyOnWriteArrayList<long[]>();
    @Override
    public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
      progress.add(new long[] { height, startHeight, endHeight });
    }
  }

  private static class BlockRecorder extends ZephyrWalletListener {
    final List<Long> heights = new CopyOnWriteArrayList<Long>();
    @Override
    public void onNewBlock(long height) {
      heights.add(height);
    }
  }

  // -------------------------------- tests -----------------------------------

  @Test
  @Order(1)
  public void testDaemonInfo() {
    assumeDaemon();
    ZephyrDaemonInfo info = getDaemon().getInfo();
    assertEquals(Boolean.TRUE, info.isSynchronized());
    assertTrue(info.getHeight() > 900000, "height " + info.getHeight());
    assertEquals(ZephyrNetworkType.MAINNET, info.getNetworkType());
    long height = getDaemon().getHeight();
    assertTrue(height == info.getHeight() || height == info.getHeight() + 1, "info height " + info.getHeight() + ", getHeight " + height);
    startDaemonHeight = height;
    log("daemon height at start: " + height + " (info " + info.getHeight() + "), network " + info.getNetworkType() + ", synchronized " + info.isSynchronized());
  }

  @Test
  @Order(2)
  public void testLastBlockHeaderPricingRecord() {
    assumeDaemon();
    ZephyrBlockHeader header = getDaemon().getLastBlockHeader();
    ZephyrPricingRecord pr = header.getPricingRecord();
    assertNotNull(pr, "pricing record");
    assertTrue(pr.getSpot().signum() > 0, "spot");
    assertTrue(pr.getMovingAverage().signum() > 0, "moving average");
    assertTrue(pr.getStable().signum() > 0, "stable");
    assertTrue(pr.getReserve().signum() > 0, "reserve");
    assertTrue(pr.getReserveRatio().signum() > 0, "reserve ratio");
    long diff = Math.abs(pr.getTimestamp().longValue() - header.getTimestamp());
    assertTrue(diff <= 24 * 3600, "pricing record timestamp " + pr.getTimestamp() + " vs header " + header.getTimestamp());
    assertNotNull(pr.getSignature(), "signature");
    assertTrue(pr.getSignature().matches("[0-9a-fA-F]{128}"), "signature " + pr.getSignature());
    assertEquals(Integer.valueOf(11), header.getMajorVersion());
    log("last block header: height " + header.getHeight() + ", major " + header.getMajorVersion() + ", timestamp " + header.getTimestamp());
    log("pricing record:\n" + pr);
  }

  @Test
  @Order(3)
  public void testBlockHeaderAtHardFork11() {
    assumeDaemon();
    ZephyrBlockHeader hf11 = getDaemon().getBlockHeaderByHeight(536000);
    assertEquals(Integer.valueOf(11), hf11.getMajorVersion());
    assertNotNull(hf11.getPricingRecord(), "pricing record at 536000");
    ZephyrBlockHeader hf10 = getDaemon().getBlockHeaderByHeight(535999);
    assertEquals(Integer.valueOf(10), hf10.getMajorVersion());
    log("536000: major " + hf11.getMajorVersion() + "; 535999: major " + hf10.getMajorVersion());
  }

  @Test
  @Order(4)
  public void testFreshWalletSync() {
    assumeDaemon();
    Assumptions.assumeTrue(TEST_PASSWORD != null && !TEST_PASSWORD.isEmpty(), "zephyr.test.password not set");
    assertTimeoutPreemptively(SYNC_TIMEOUT, () -> {
      long daemonHeight = getDaemon().getHeight();
      if (ZephyrWalletFull.walletExists(WALLET_PATH)) {
        wallet = ZephyrWalletFull.openWallet(WALLET_PATH, TEST_PASSWORD, ZephyrNetworkType.MAINNET, DAEMON_URI);
        walletOpened();
        log("opened the existing test wallet; restore height " + wallet.getRestoreHeight());
      } else {
        new File(WALLET_PATH).getParentFile().mkdirs();
        wallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
            .setPath(WALLET_PATH)
            .setPassword(TEST_PASSWORD)
            .setNetworkType(ZephyrNetworkType.MAINNET)
            .setServerUri(DAEMON_URI));
        walletOpened();
        long restoreHeight = wallet.getRestoreHeight();
        log("created the test wallet; daemon height " + daemonHeight + ", restore height " + restoreHeight);
        assertTrue(Math.abs(restoreHeight - daemonHeight) <= 20, "restore height " + restoreHeight + ", daemon height " + daemonHeight);
      }
      log("FUNDING ADDRESS: " + wallet.getPrimaryAddress());

      // the core reports onSyncProgress per fetched block, so a wallet at the daemon height gets none
      SyncRecorder recorder = new SyncRecorder();
      long heightBefore = wallet.getHeight();
      long daemonHeightBefore = getDaemon().getHeight();
      boolean blocksToFetch = heightBefore < daemonHeightBefore;
      log("before sync: wallet height " + heightBefore + ", daemon height " + daemonHeightBefore + "; case: "
          + (blocksToFetch ? "blocks to fetch, at least one onSyncProgress call expected" : "at the daemon height, no onSyncProgress call expected"));
      long start = System.currentTimeMillis();
      wallet.sync(recorder);
      long elapsed = System.currentTimeMillis() - start;
      long walletHeight = wallet.getHeight();
      long daemonHeightAfter = getDaemon().getHeight();
      int progressCalls = recorder.progress.size();
      log("sync: " + elapsed + " ms, " + progressCalls + " onSyncProgress calls, wallet height " + walletHeight + ", daemon height " + daemonHeightAfter);
      assertTrue(walletHeight == daemonHeightAfter || walletHeight == daemonHeightAfter - 1, "wallet height " + walletHeight + ", daemon height " + daemonHeightAfter);
      if (blocksToFetch) assertTrue(progressCalls > 0, "no onSyncProgress call although the wallet was at " + heightBefore + " and the daemon at " + daemonHeightBefore);
      else assertEquals(0, progressCalls, "onSyncProgress calls although the wallet was at the daemon height " + daemonHeightBefore);
      assertEquals(25, wallet.getMnemonic().trim().split("\\s+").length);  // never printed
      ZephyrDecodedAddress decoded = ZephyrUtils.decodeAddress(wallet.getPrimaryAddress());
      assertEquals(ZephyrNetworkType.MAINNET, decoded.getNetworkType());
      assertEquals(ZephyrAddressType.PRIMARY_ADDRESS, decoded.getAddressType());
    });
  }

  @Test
  @Order(5)
  public void testWalletBalanceConsistency() {
    assumeWallet();
    ZephyrBalance balance = wallet.getBalance();
    ZephyrBalance unlocked = wallet.getUnlockedBalance();
    Map<Integer, ZephyrBalance> perSubaddress = wallet.getBalance(0);
    Map<Integer, ZephyrBalance> perSubaddressUnlocked = wallet.getUnlockedBalance(0);
    List<ZephyrAccount> accounts = wallet.getAccounts(true);
    log("balance " + balance.toMap() + ", unlocked " + unlocked.toMap());
    log("account 0 per subaddress " + perSubaddress + ", unlocked " + perSubaddressUnlocked);
    for (ZephyrAccount account : accounts) {
      log("account " + account.getIndex() + ": balance " + (account.getBalance() == null ? null : account.getBalance().toMap())
          + ", unlocked " + (account.getUnlockedBalance() == null ? null : account.getUnlockedBalance().toMap())
          + ", subaddresses " + (account.getSubaddresses() == null ? null : account.getSubaddresses().size()));
    }
    assertNotNull(balance);
    assertNotNull(unlocked);
    assertNotNull(perSubaddress);
    assertNotNull(perSubaddressUnlocked);

    // wallet, account, subaddress and asset-specific calls agree per asset
    for (String asset : ZephyrAssets.V2) {
      checkConsistency(asset, balance.get(asset), wallet.getBalance(asset, 0), wallet.getBalance(asset, 0, 0), perSubaddress, "balance");
      checkConsistency(asset, unlocked.get(asset), wallet.getUnlockedBalance(asset, 0), wallet.getUnlockedBalance(asset, 0, 0), perSubaddressUnlocked, "unlocked");
      assertTrue(unlocked.get(asset).compareTo(balance.get(asset)) <= 0, asset + ": unlocked " + unlocked.get(asset) + " above balance " + balance.get(asset));
    }

    // the account from getAccounts(true): its balance is the wallet's, its subaddresses sum to it
    assertEquals(1, accounts.size());
    ZephyrAccount account = accounts.get(0);
    assertNotNull(account.getBalance(), "account balance");
    assertNotNull(account.getUnlockedBalance(), "account unlocked balance");
    assertNotNull(account.getSubaddresses(), "account subaddresses");
    log("account balance equals the wallet balance as objects (ZephyrBalance.equals): " + balance.equals(account.getBalance())
        + "; unlocked: " + unlocked.equals(account.getUnlockedBalance()));
    TreeSet<String> assets = new TreeSet<String>(ZephyrAssets.V2);  // compared per asset: an absent asset is zero
    assets.addAll(balance.getAssetTypes());
    assets.addAll(account.getBalance().getAssetTypes());
    assets.addAll(unlocked.getAssetTypes());
    assets.addAll(account.getUnlockedBalance().getAssetTypes());
    for (String asset : assets) {
      assertEquals(balance.get(asset), account.getBalance().get(asset), asset + ": account balance");
      assertEquals(unlocked.get(asset), account.getUnlockedBalance().get(asset), asset + ": account unlocked balance");
      BigInteger sum = BigInteger.ZERO;
      BigInteger sumUnlocked = BigInteger.ZERO;
      for (ZephyrSubaddress subaddress : account.getSubaddresses()) {
        if (subaddress.getBalance() != null) sum = sum.add(subaddress.getBalance().get(asset));
        if (subaddress.getUnlockedBalance() != null) sumUnlocked = sumUnlocked.add(subaddress.getUnlockedBalance().get(asset));
      }
      assertEquals(account.getBalance().get(asset), sum, asset + ": sum over the account's subaddresses");
      assertEquals(account.getUnlockedBalance().get(asset), sumUnlocked, asset + ": unlocked sum over the account's subaddresses");
    }
  }

  /** One asset: the wallet amount, the account amount, subaddress 0/0 and the per-subaddress map agree. */
  private static void checkConsistency(String asset, BigInteger walletAmount, BigInteger accountAmount, BigInteger subaddressAmount,
      Map<Integer, ZephyrBalance> perSubaddress, String kind) {
    assertNotNull(walletAmount, asset + " " + kind);
    assertTrue(walletAmount.signum() >= 0, asset + " " + kind + " " + walletAmount);
    assertEquals(walletAmount, accountAmount, asset + " " + kind + ": account 0");
    BigInteger sum = BigInteger.ZERO;
    for (ZephyrBalance b : perSubaddress.values()) sum = sum.add(b.get(asset));
    assertEquals(walletAmount, sum, asset + " " + kind + ": sum over the per-subaddress map");
    assertTrue(subaddressAmount.compareTo(accountAmount) <= 0, asset + " " + kind + ": subaddress 0/0 " + subaddressAmount + " above account " + accountAmount);
  }

  @Test
  @Order(6)
  public void testReserveInfo() {
    assumeWallet();
    ZephyrReserveInfo info = wallet.getReserveInfo();
    assertTrue(info.getZephReserve().signum() > 0, "zephReserve");
    assertTrue(info.getNumStables().signum() > 0, "numStables");
    assertTrue(info.getNumReserves().signum() > 0, "numReserves");
    assertTrue(info.getAssets().signum() > 0, "assets");
    assertTrue(info.getLiabilities().signum() > 0, "liabilities");
    assertTrue(info.getEquity().signum() > 0, "equity");
    BigDecimal limit = new BigDecimal(1000);
    assertTrue(info.getReserveRatio().signum() > 0 && info.getReserveRatio().compareTo(limit) < 0, "reserveRatio " + info.getReserveRatio());
    assertTrue(info.getReserveRatioMa().signum() > 0 && info.getReserveRatioMa().compareTo(limit) < 0, "reserveRatioMa " + info.getReserveRatioMa());
    assertEquals(Integer.valueOf(11), info.getHfVersion());
    assertNotNull(info.getZyieldCirc(), "zyieldCirc");
    assertNotNull(info.getZyieldRsv(), "zyieldRsv");
    log("reserve info at wallet height " + wallet.getHeight() + ":\n" + info);
  }

  @Test
  @Order(7)
  public void testCirculatingSupply() {
    assumeWallet();
    Map<String, BigInteger> supply = wallet.getCirculatingSupply();
    for (String asset : ZephyrAssets.V2) {
      assertTrue(supply.containsKey(asset), "missing " + asset + " in " + supply);
      assertTrue(supply.get(asset).signum() >= 0, asset + " " + supply.get(asset));
    }
    assertTrue(supply.get(ZephyrAssets.ZPH).signum() > 0, "ZPH " + supply.get(ZephyrAssets.ZPH));
    log("circulating supply at wallet height " + wallet.getHeight() + ": " + supply);
  }

  @Test
  @Order(8)
  public void testNewBlockListener() {
    assumeWallet();
    assertTimeoutPreemptively(SYNC_TIMEOUT, () -> {
      BlockRecorder recorder = new BlockRecorder();
      long lastSynced = wallet.getHeight() - 1;  // the index of the last synced block
      long daemonBefore = getDaemon().getHeight();
      wallet.addListener(recorder);
      long start = System.currentTimeMillis();
      try {
        wallet.startSyncing(5000L);
        while (System.currentTimeMillis() - start < NEW_BLOCK_WAIT_MS) {
          boolean found = false;
          for (Long h : recorder.heights) if (h >= lastSynced) found = true;
          if (found) break;
          Thread.sleep(1000);
        }
      } finally {
        wallet.stopSyncing();
        wallet.removeListener(recorder);
      }
      long elapsed = System.currentTimeMillis() - start;
      long daemonAfter = getDaemon().getHeight();
      log("new block wait: " + elapsed + " ms, notifications " + recorder.heights + ", last synced block " + lastSynced
          + ", daemon height before " + daemonBefore + ", after " + daemonAfter);
      boolean found = false;
      for (Long h : recorder.heights) if (h >= lastSynced) found = true;
      if (!found) fail("no onNewBlock at or above " + lastSynced + " within " + elapsed + " ms; daemon height before " + daemonBefore + ", after " + daemonAfter + "; notifications " + recorder.heights);
    });
  }

  @Test
  @Order(9)
  public void testWalletDataRoundTrip() {
    assumeWallet();
    byte[][] data = wallet.getData();
    assertNotNull(data[0], "keys data");
    assertNotNull(data[1], "cache data");
    log("keys data " + data[0].length + " bytes, cache data " + data[1].length + " bytes");
    List<String> created = new ArrayList<String>();
    try {
      dataWallet = ZephyrWalletFull.openWalletData(TEST_PASSWORD, ZephyrNetworkType.MAINNET, data[0], data[1], new ZephyrRpcConnection(DAEMON_URI));
      walletOpened();
      assertEquals(wallet.getPrimaryAddress(), dataWallet.getPrimaryAddress());

      // changePassword on the in-memory wallet: the outcome is recorded, not asserted
      List<String> before = gitStatus();
      String outcome;
      try {
        dataWallet.changePassword(TEST_PASSWORD, UUID.randomUUID().toString());
        outcome = "no exception";
      } catch (Exception e) {
        outcome = "threw " + e.getClass().getName() + ": " + e.getMessage();
      }
      List<String> after = gitStatus();
      log("changePassword on the in-memory wallet: " + outcome);
      log("git status --short before: " + before);
      log("git status --short after: " + after);
      TreeSet<String> added = new TreeSet<String>(after);
      added.removeAll(before);
      for (String entry : added) {
        log("FINDING: new entry in git status after changePassword: " + entry);
        if (entry.startsWith("?? ")) created.add(entry.substring(3));
      }
      if (added.isEmpty()) log("git status unchanged by changePassword");
    } finally {
      if (dataWallet != null) {
        dataWallet.close(false);
        dataWallet = null;
        walletClosed();
      }
      for (String path : created) {  // a stray keys file is removed, never committed
        File f = new File(path);
        if (f.isFile() && f.getName().endsWith(".keys")) log("removed " + path + ": " + f.delete());
        else log("left in place (not a keys file): " + path);
      }
    }
  }

  @Test
  @Order(10)
  public void testViewOnlyWallet() {
    assumeDaemon();
    Assumptions.assumeTrue(VIEW_ADDRESS != null && VIEW_KEY != null && VIEW_HEIGHT != null, "view-only properties not set");
    assertTimeoutPreemptively(Duration.ofMinutes(60), () -> {
      try {
        viewWallet = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()
            .setNetworkType(ZephyrNetworkType.MAINNET)
            .setServerUri(DAEMON_URI)
            .setPrimaryAddress(VIEW_ADDRESS)
            .setPrivateViewKey(VIEW_KEY)
            .setRestoreHeight(Long.parseLong(VIEW_HEIGHT)));
        walletOpened();
        long start = System.currentTimeMillis();
        viewWallet.sync();
        log("view-only sync: " + (System.currentTimeMillis() - start) + " ms, height " + viewWallet.getHeight());
        assertTrue(viewWallet.isViewOnly());
        ZephyrBalance balance = viewWallet.getBalance();
        log("view-only balance per asset: " + balance.toMap());
        boolean positive = false;
        for (String asset : balance.getAssetTypes()) if (balance.get(asset).signum() > 0) positive = true;
        assertTrue(positive, "no positive asset in " + balance.toMap());
        List<ZephyrTransfer> transfers = viewWallet.getTransfers();
        assertFalse(transfers.isEmpty(), "no transfers");
        for (ZephyrTransfer transfer : transfers) assertNotNull(transfer.getAssetType(), "transfer without asset type: " + transfer);
        List<ZephyrTxWallet> txs = viewWallet.getTxs();
        assertFalse(txs.isEmpty(), "no txs");
        for (int i = 0; i < Math.min(5, transfers.size()); i++) log("transfer " + i + ":\n" + transfers.get(i));
      } finally {
        if (viewWallet != null) {
          viewWallet.close(false);
          viewWallet = null;
          walletClosed();
        }
      }
    });
  }

  @Test
  @Order(11)
  public void testWalletRpc() throws Exception {
    assumeDaemon();
    Assumptions.assumeTrue(WALLET_RPC_BIN != null && !WALLET_RPC_BIN.isEmpty(), "zephyr.wallet.rpc.bin not set");
    Assumptions.assumeTrue(TEST_PASSWORD != null && !TEST_PASSWORD.isEmpty(), "zephyr.test.password not set");
    URI uri = new URI(DAEMON_URI);
    new File(WALLET_RPC_DIR).mkdirs();
    List<String> cmd = Arrays.asList(WALLET_RPC_BIN,
        "--daemon-address", uri.getHost() + ":" + uri.getPort(),
        "--rpc-bind-port", "28084",
        "--rpc-login", "rpc_user:abc123",
        "--wallet-dir", WALLET_RPC_DIR,
        "--log-level", "0",
        "--trusted-daemon");
    walletRpc = new ZephyrWalletRpc(cmd);
    String name = "r8-" + System.currentTimeMillis();
    walletRpc.createWallet(new ZephyrWalletConfig().setPath(name).setPassword(TEST_PASSWORD));
    walletOpened();
    try {
      String address = walletRpc.getPrimaryAddress();
      ZephyrDecodedAddress decoded = ZephyrUtils.decodeAddress(address);
      assertEquals(ZephyrNetworkType.MAINNET, decoded.getNetworkType());
      log("wallet-rpc wallet " + name + ": " + address);
      ZephyrBalance balance = walletRpc.getBalance();
      assertNotNull(balance);
      assertEquals(BigInteger.ZERO, balance.get(ZephyrAssets.ZPH));
      log("wallet-rpc balance " + balance.toMap());
      ZephyrError e1 = assertThrows(ZephyrError.class, () -> walletRpc.getBalance(ZephyrAssets.ZSD, 0));
      log("getBalance(ZSD, 0): " + e1.getMessage());
      ZephyrError e2 = assertThrows(ZephyrError.class, () -> walletRpc.getReserveInfo());
      log("getReserveInfo(): " + e2.getMessage());
    } finally {
      walletRpc.close();
      walletClosed();
    }
  }

  /**
   * getOutputs() reports no asset (the core does not serialize it); getOutputAssetType takes it from
   * the output's transaction. Every output of the funded test wallet must resolve to ZPH or ZRS, and
   * the unspent outputs must add up to the balance per asset. Uses the wallet of test 4 when it is
   * open, otherwise opens the test wallet itself (one wallet; synced, closed without saving).
   */
  @Test
  @Order(12)
  public void testOutputAssetType() {
    assumeDaemon();
    Assumptions.assumeTrue(TEST_PASSWORD != null && !TEST_PASSWORD.isEmpty(), "zephyr.test.password not set");
    Assumptions.assumeTrue(ZephyrWalletFull.walletExists(WALLET_PATH), "no test wallet at " + WALLET_PATH);
    assertTimeoutPreemptively(SYNC_TIMEOUT, () -> {
      boolean own = wallet == null || wallet.isClosed();
      ZephyrWalletFull w = own ? ZephyrWalletFull.openWallet(WALLET_PATH, TEST_PASSWORD, ZephyrNetworkType.MAINNET, DAEMON_URI) : wallet;
      if (own) walletOpened();
      try {
        if (own) {
          long before = w.getHeight();
          w.sync();
          log("opened the test wallet; synced from " + before + " to " + w.getHeight() + ", daemon height " + getDaemon().getHeight());
        }
        List<ZephyrOutputWallet> outputs = w.getOutputs();
        assertFalse(outputs.isEmpty(), "the test wallet has no outputs");
        Map<String, BigInteger> unspent = new TreeMap<String, BigInteger>();
        Map<String, Integer> counts = new TreeMap<String, Integer>();
        for (ZephyrOutputWallet output : outputs) {
          assertNull(output.getAssetType(), "getOutputs() reported an asset; the quirk is gone, update the guide");
          String asset = w.getOutputAssetType(output);
          ZephyrTxWallet tx = w.getTxs(output.getTx().getHash()).get(0);
          ZephyrOutgoingTransfer outgoing = tx.getOutgoingTransfer();
          boolean ownTx = outgoing != null && output.getAccountIndex().equals(outgoing.getAccountIndex());
          int incoming = tx.getIncomingTransfers() == null ? 0 : tx.getIncomingTransfers().size();
          String kind = !ownTx ? "received" : incoming == 0 ? "change" : "own tx with incoming";
          log("output " + output.getTx().getHash().substring(0, 8) + " (" + output.getAccountIndex() + "," + output.getSubaddressIndex() + ") "
              + coins(output.getAmount()) + (output.isSpent() ? " spent" : " unspent") + ": " + asset + " [" + kind
              + (ownTx ? ", source " + outgoing.getAssetType() : "") + ", " + incoming + " incoming transfer(s)]");
          assertNotNull(asset, "no asset for the output of tx " + output.getTx().getHash());
          assertTrue(ZephyrAssets.ZPH.equals(asset) || ZephyrAssets.ZRS.equals(asset), "asset " + asset + " for the output of tx " + output.getTx().getHash());
          counts.merge(asset + " " + kind, 1, Integer::sum);
          if (!output.isSpent()) unspent.merge(asset, output.getAmount(), BigInteger::add);
        }
        log(outputs.size() + " outputs: " + counts);
        ZephyrBalance balance = w.getBalance();
        TreeSet<String> assets = new TreeSet<String>(balance.getAssetTypes());
        assets.addAll(unspent.keySet());
        for (String asset : assets) {
          BigInteger expected = balance.get(asset) == null ? BigInteger.ZERO : balance.get(asset);
          BigInteger actual = unspent.getOrDefault(asset, BigInteger.ZERO);
          log(asset + ": balance " + coins(expected) + ", unspent outputs " + coins(actual));
          assertEquals(expected, actual, "the unspent " + asset + " outputs do not add up to the balance");
        }
      } finally {
        if (own) {
          w.close(false);
          walletClosed();
        }
      }
    });
  }

  /**
   * 13: TLS without any ZephyrTls call. On the JVM nothing is applied: the native wallet verifies
   * the node with OpenSSL's own default store (the host's) or the SSL_CERT_FILE/SSL_CERT_DIR of the
   * environment, as in java.3. Runs before every other ZephyrTls use in this JVM (test order).
   */
  @Test
  @Order(13)
  public void testTlsWithoutConfiguration() {
    assumeTls();
    log("TLS without configuration; environment SSL_CERT_FILE=" + System.getenv("SSL_CERT_FILE") + " SSL_CERT_DIR=" + System.getenv("SSL_CERT_DIR"));
    ZephyrWalletFull w = createTlsWallet();
    try {
      assertTrue(w.isConnectedToDaemon(), "not connected to the https node with the host's store");
      long height = w.getDaemonHeight();
      log("TLS without configuration: connected, daemon height " + height);
      assertTrue(height > 900000, "daemon height " + height);
    } finally {
      w.close();
      walletClosed();
    }
  }

  /** 14: TLS with the defaults (the shipped root certificates only) applied by an explicit apply(). */
  @Test
  @Order(14)
  public void testTlsShippedCertificates() throws Exception {
    assumeTls();
    File bundle = ZephyrTls.apply();
    int count = 0;
    for (String line : java.nio.file.Files.readAllLines(bundle.toPath(), StandardCharsets.US_ASCII)) {
      if (line.equals("-----BEGIN CERTIFICATE-----")) count++;
    }
    log("TLS with the shipped certificates (" + ZephyrTls.getShippedCertificatesDate() + "): bundle " + bundle + ", " + count + " certificates");
    ZephyrWalletFull w = createTlsWallet();
    try {
      assertTrue(w.isConnectedToDaemon(), "not connected to the https node with the shipped certificates");
      long height = w.getDaemonHeight();
      log("TLS with the shipped certificates: connected, daemon height " + height);
      assertTrue(height > 900000, "daemon height " + height);
    } finally {
      w.close();
      walletClosed();
    }
  }

  /** 15: setCaFile at an empty file (applied by the wallet's hook, no explicit apply()): no connection. */
  @Test
  @Order(15)
  public void testTlsEmptyCaFile() throws Exception {
    assumeTls();
    File empty = File.createTempFile("zephyr-java-empty", ".pem");
    empty.deleteOnExit();
    ZephyrTls.setCaFile(empty);
    try {
      ZephyrWalletFull w = createTlsWallet();
      try {
        boolean connected = w.isConnectedToDaemon();
        log("TLS with an empty CA file: isConnectedToDaemon() " + connected);
        assertFalse(connected, "connected to the https node with an empty CA file");
      } finally {
        w.close();
        walletClosed();
      }
    } finally {
      ZephyrTls.setCaFile(null);
    }
  }

  @AfterAll
  public static void afterAll() {
    List<Throwable> errors = new ArrayList<Throwable>();
    for (ZephyrWallet w : new ZephyrWallet[] { dataWallet, viewWallet }) {
      if (w == null || w.isClosed()) continue;
      try { w.close(false); walletClosed(); } catch (Throwable t) { errors.add(t); }
    }
    if (wallet != null && !wallet.isClosed()) {
      try { wallet.close(true); walletClosed(); } catch (Throwable t) { errors.add(t); }
    }
    if (walletRpc != null) {
      try { if (!walletRpc.isClosed()) walletRpc.close(); } catch (Throwable t) { errors.add(t); }
      try { log("wallet-rpc process stopped: exit " + walletRpc.stopProcess()); } catch (Throwable t) { errors.add(t); }
    }
    if (DAEMON_URI != null && !DAEMON_URI.isEmpty()) {
      try { log("daemon height at start " + startDaemonHeight + ", at end " + getDaemon().getHeight()); } catch (Throwable t) { errors.add(t); }
    }
    log("maximum wallets open at the same time: " + maxWalletsOpen);
    for (Throwable t : errors) log("cleanup error: " + t);
  }
}
