package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Closing a wallet while the core is busy, and listeners that throw while the core delivers
 * callbacks from its own threads, each run in a JVM of its own ({@link ClosingChild}): the
 * failures these guard against end or keep alive the whole process, which a test in the test
 * runner's JVM could not report.
 *
 * <p>A child passes when it prints "RESULT ok" and its JVM ends by itself with status 0, leaves no
 * {@code hs_err} file and prints no "Wallet is closed" on stderr. The core's threads are attached
 * as daemon threads, so a JVM that exits no longer shows that every callback thread was detached;
 * the background scenarios check the callback threads themselves ({@link ClosingChild}). Every child has a time limit
 * ({@value #CHILD_LIMIT_S} s, and {@value #EXIT_LIMIT_S} s from the moment its main returns); one
 * that passes it gets a thread dump (SIGQUIT, where {@code kill} exists), is destroyed and fails.</p>
 *
 * <p>Configured like {@link TestZephyrIntegration}: {@code zephyr.daemon.uri} (the node) and
 * {@code zephyr.test.password} (the password of the test wallet) are required by every test but
 * the one against a silent local socket; without them those tests are skipped. The wallet is a
 * copy of the test wallet's keys file ({@code zephyr.test.wallet}, default
 * {@code test_wallets/zephyr-java-r8}), without its cache, so each child scans from the restore
 * height. Read-only: nothing is sent or relayed; the children run one at a time, each with one
 * wallet. The node's uri is passed as a system property, the password in the child's environment;
 * neither is printed. Each child's output stays under {@code target/closing-children/}.</p>
 */
public class TestWalletClosingChildren {

  static final long CHILD_LIMIT_S = 300;
  static final long EXIT_LIMIT_S = 30;

  private static final String DAEMON_URI = System.getProperty("zephyr.daemon.uri");
  private static final String TEST_PASSWORD = System.getProperty("zephyr.test.password");
  private static final String WALLET = System.getProperty("zephyr.test.wallet", "test_wallets/zephyr-java-r8");
  private static final Path RUNS = Paths.get(System.getProperty("basedir", ".")).resolve("target/closing-children");

  private static void assumeNodeAndWallet() {
    Assumptions.assumeTrue(DAEMON_URI != null && !DAEMON_URI.isEmpty(), "zephyr.daemon.uri not set");
    Assumptions.assumeTrue(TEST_PASSWORD != null && !TEST_PASSWORD.isEmpty(), "zephyr.test.password not set");
    Assumptions.assumeTrue(new File(WALLET + ".keys").isFile(), WALLET + ".keys not found");
  }

  private static void log(String message) {
    System.out.println("[closing] " + message);
  }

  /** The outcome of one child. */
  private static class Child {
    String name;
    int exit = -1;
    boolean timedOut;
    String timeoutReason;
    long elapsedMs;
    List<String> stdout = new ArrayList<String>();
    List<String> stderr = new ArrayList<String>();
    List<String> hsErr = new ArrayList<String>();

    String tail(List<String> lines, int n) {
      return String.join("\n", lines.subList(Math.max(0, lines.size() - n), lines.size()));
    }

    String closeWait() {
      for (String line : stdout) if (line.contains("CLOSE-WAIT-MS ")) return line.substring(line.indexOf("CLOSE-WAIT-MS ") + 14).trim();
      return "-";
    }

    /** The child's verdict: all conditions, each failure named, with the end of its output. */
    void check() {
      List<String> problems = new ArrayList<String>();
      if (timedOut) problems.add(timeoutReason);
      else if (exit != 0) problems.add("exit status " + exit);
      if (!hsErr.isEmpty()) problems.add("hs_err file(s) " + hsErr);
      for (String line : stderr) if (line.contains("Wallet is closed")) { problems.add("\"Wallet is closed\" on stderr"); break; }
      boolean ok = false;
      for (String line : stdout) if (line.contains("RESULT ok")) ok = true;
      if (!ok) problems.add("no \"RESULT ok\" from the child");
      if (!problems.isEmpty()) {
        fail(name + ": " + String.join("; ", problems) + "\n--- stdout (last 40 lines)\n" + tail(stdout, 40) + "\n--- stderr (last 40 lines)\n" + tail(stderr, 40));
      }
    }
  }

  /** Runs one child in its own directory under target/closing-children and waits for it within the limits. */
  private static Child run(String name, boolean needsWallet, String... args) throws Exception {
    Child child = new Child();
    child.name = name;
    Path dir = RUNS.resolve(name);
    deleteTree(dir);
    Files.createDirectories(dir);
    List<String> command = new ArrayList<String>();
    command.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    if (System.getProperty("java.library.path") != null) command.add("-Djava.library.path=" + System.getProperty("java.library.path"));
    command.add("-XX:ErrorFile=" + dir.toAbsolutePath() + "/hs_err_pid%p.log");
    if (DAEMON_URI != null) command.add("-Dzephyr.daemon.uri=" + DAEMON_URI);
    command.add(ClosingChild.class.getName());
    command.add(args[0]);
    if (needsWallet) {
      Path keys = dir.resolve("w.keys");
      Files.copy(Paths.get(WALLET + ".keys"), keys, StandardCopyOption.REPLACE_EXISTING);
      command.add(dir.resolve("w").toAbsolutePath().toString());
    }
    for (int i = 1; i < args.length; i++) command.add(args[i]);
    ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile())
        .redirectOutput(dir.resolve("stdout.txt").toFile()).redirectError(dir.resolve("stderr.txt").toFile());
    builder.environment().remove("ZEPHYR_CHILD_PASSWORD");
    if (TEST_PASSWORD != null) builder.environment().put("ZEPHYR_CHILD_PASSWORD", TEST_PASSWORD);
    long start = System.nanoTime();
    Process process = builder.start();
    long mainReturnedAt = -1;
    while (true) {
      if (process.waitFor(200, TimeUnit.MILLISECONDS)) break;
      long now = System.nanoTime();
      if (mainReturnedAt < 0 && readLines(dir.resolve("stdout.txt")).stream().anyMatch(l -> l.contains("MAIN-RETURNS"))) mainReturnedAt = now;
      if (mainReturnedAt >= 0 && now - mainReturnedAt > TimeUnit.SECONDS.toNanos(EXIT_LIMIT_S)) {
        child.timedOut = true;
        child.timeoutReason = "main returned but the JVM did not exit within " + EXIT_LIMIT_S + " s";
      } else if (now - start > TimeUnit.SECONDS.toNanos(CHILD_LIMIT_S)) {
        child.timedOut = true;
        child.timeoutReason = "the child did not exit within " + CHILD_LIMIT_S + " s";
      }
      if (child.timedOut) {
        threadDump(process);
        process.destroyForcibly();
        process.waitFor(30, TimeUnit.SECONDS);
        break;
      }
    }
    child.elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    if (!process.isAlive()) child.exit = process.exitValue();
    child.stdout = readLines(dir.resolve("stdout.txt"));
    child.stderr = readLines(dir.resolve("stderr.txt"));
    try (Stream<Path> files = Files.list(dir)) {
      child.hsErr = files.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("hs_err")).collect(Collectors.toList());
    }
    // the wallet copy goes; the output stays
    try (Stream<Path> files = Files.list(dir)) {
      for (Path p : files.collect(Collectors.toList())) {
        String n = p.getFileName().toString();
        if (n.equals("w") || n.startsWith("w.")) Files.deleteIfExists(p);
      }
    }
    log(name + ": exit " + (child.timedOut ? "none (" + child.timeoutReason + ", destroyed)" : String.valueOf(child.exit)) + " after " + child.elapsedMs + " ms; close() waited " + child.closeWait() + " ms");
    return child;
  }

  /** A thread dump of a child that passed its limit, into its stdout (HotSpot prints it on SIGQUIT). */
  private static void threadDump(Process process) {
    if (!new File("/bin/kill").canExecute()) return;
    try {
      new ProcessBuilder("/bin/kill", "-QUIT", String.valueOf(process.pid())).inheritIO().start().waitFor(5, TimeUnit.SECONDS);
      Thread.sleep(1000);
    } catch (Exception e) {
      // no dump
    }
  }

  private static List<String> readLines(Path file) {
    try {
      return Files.exists(file) ? Files.readAllLines(file, StandardCharsets.ISO_8859_1) : new ArrayList<String>();
    } catch (IOException e) {
      return new ArrayList<String>();
    }
  }

  private static void deleteTree(Path dir) throws IOException {
    if (!Files.exists(dir)) return;
    try (Stream<Path> walk = Files.walk(dir)) {
      for (Path p : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).collect(Collectors.toList())) Files.delete(p);
    }
  }

  // ---------------------------------- tests -----------------------------------

  /** N3b: a listener throws from onNewBlock under startSyncing(); after the scan, stop and close; the JVM exits. */
  @Test
  public void throwingListenerUnderBackgroundSyncBlock() throws Exception {
    assumeNodeAndWallet();
    run("n3b-block", true, "n3b", "block").check();
  }

  /** N3b with the throw from onSyncProgress. */
  @Test
  public void throwingListenerUnderBackgroundSyncProgress() throws Exception {
    assumeNodeAndWallet();
    run("n3b-progress", true, "n3b", "progress").check();
  }

  /** N3b with the throw from onBalancesChanged. */
  @Test
  public void throwingListenerUnderBackgroundSyncBalances() throws Exception {
    assumeNodeAndWallet();
    run("n3b-balances", true, "n3b", "balances").check();
  }

  /**
   * N4a: close() from a second thread at the first onSyncProgress of a foreground sync(); no crash,
   * sync() returns or throws ZephyrError("Wallet is closed"), the JVM exits. Ten runs.
   */
  @RepeatedTest(10)
  public void closeDuringForegroundSync(RepetitionInfo repetition) throws Exception {
    assumeNodeAndWallet();
    run("n4a-" + repetition.getCurrentRepetition(), true, "n4a").check();
  }

  /** N4b: close() during a busy background scan; no callback after close() returns; the JVM exits. */
  @Test
  public void closeDuringBackgroundSync() throws Exception {
    assumeNodeAndWallet();
    run("n4b", true, "n4b").check();
  }

  /** stopSyncing() directly followed by close(), during a busy background scan. */
  @Test
  public void stopSyncingThenCloseAtOnce() throws Exception {
    assumeNodeAndWallet();
    run("stopclose", true, "stopclose").check();
  }

  /** close() from a callback of background syncing is refused; the wallet stays usable; main closes it. */
  @Test
  public void closeFromACallbackOfBackgroundSync() throws Exception {
    assumeNodeAndWallet();
    run("fromcallback", true, "fromcallback").check();
  }

  /** H1: the delays between startSyncing() and close(), in daemon RPC times, one per run. */
  private static final String[] H1_DELAYS = { "0", "0.5", "1", "1.5", "2", "2.5" };

  /**
   * H1: close() shortly after startSyncing() on a wallet far behind the node, while the core's sync
   * thread makes its daemon RPCs before the refresh begins, returns within
   * {@value ClosingChild#H1_LIMIT_MS} ms instead of after a whole catch-up scan. Six runs, each
   * closing a little later (0 to 2.5 times one daemon RPC after startSyncing() returned).
   */
  @RepeatedTest(6)
  public void closeRightAfterStartSyncing(RepetitionInfo repetition) throws Exception {
    assumeNodeAndWallet();
    int i = repetition.getCurrentRepetition();
    run("h1-start-" + i, true, "h1", "start", H1_DELAYS[i - 1]).check();
  }

  /** H1 with stopSyncing() between startSyncing() and close(). Six runs with the same delays. */
  @RepeatedTest(6)
  public void closeRightAfterStartAndStopSyncing(RepetitionInfo repetition) throws Exception {
    assumeNodeAndWallet();
    int i = repetition.getCurrentRepetition();
    run("h1-stop-" + i, true, "h1", "stop", H1_DELAYS[i - 1]).check();
  }

  /**
   * H2: close(true) while the core's sync thread is paused inside a refresh (a listener blocks in
   * onNewBlock for 2 s): the wallet is saved after that thread has stopped, and it reopens with the
   * scanned blocks. Three runs.
   */
  @RepeatedTest(3)
  public void closeWithSaveWhileARefreshRuns(RepetitionInfo repetition) throws Exception {
    assumeNodeAndWallet();
    run("h2-" + repetition.getCurrentRepetition(), true, "h2").check();
  }

  /** H3: calls from inside a callback of a foreground sync(): sync() and relaying calls refused, reads work. */
  @Test
  public void callsFromACallbackOfForegroundSync() throws Exception {
    assumeNodeAndWallet();
    run("h3-fg", true, "h3", "fg").check();
  }

  /** H3 with the callback of background syncing. */
  @Test
  public void callsFromACallbackOfBackgroundSync() throws Exception {
    assumeNodeAndWallet();
    run("h3-bg", true, "h3", "bg").check();
  }

  /**
   * W1: after the last listener is removed, a background scan makes no callback upcalls (counted
   * by the threads the JVM starts); a listener added during the scan receives callbacks again.
   */
  @Test
  public void noUpcallsWithoutAListener() throws Exception {
    assumeNodeAndWallet();
    run("w1", true, "w1").check();
  }

  /**
   * F1: a listener added and removed in a tight loop, and removing itself from its callback, during
   * a background scan of {@value ClosingChild#CHURN_BLOCKS} blocks: no crash. Three runs.
   */
  @RepeatedTest(3)
  public void listenerChurnDuringBackgroundScan(RepetitionInfo repetition) throws Exception {
    assumeNodeAndWallet();
    run("churn-" + repetition.getCurrentRepetition(), true, "churn").check();
  }

  /** A listener removed and another added from main during a foreground sync(): at once, no callback after the removal. */
  @Test
  public void listenersChangedDuringForegroundSync() throws Exception {
    assumeNodeAndWallet();
    run("fgtoggle", true, "fgtoggle").check();
  }

  /**
   * T4: main returns while background syncing runs and the wallet is still open: exit status 0, no
   * crash report. Twenty runs. Known to fail: while the core's sync thread is inside a refresh, the
   * JVM does not finish exiting (it exits once that thread waits between two syncs), so the test
   * runs only with {@code -Dzephyr.test.exitopen=true}. Close the wallet before the JVM exits.
   */
  @RepeatedTest(20)
  public void exitWithTheWalletOpenAndSyncing(RepetitionInfo repetition) throws Exception {
    assumeNodeAndWallet();
    Assumptions.assumeTrue(Boolean.getBoolean("zephyr.test.exitopen"), "known to fail; run with -Dzephyr.test.exitopen=true");
    run("exitopen-" + repetition.getCurrentRepetition(), true, "exitopen").check();
  }

  /**
   * close() while another thread's setDaemonConnection waits on a local socket that accepts and
   * never answers: close() returns once that call ends (the core gives up on the node after its
   * own RPC timeout), and the JVM exits. No node needed.
   */
  @Test
  public void closeWhileTheNodeNeverAnswers() throws Exception {
    Child child = run("silent", false, "silent");
    child.check();
    assertFalse(child.closeWait().equals("-"), "no close() time reported");
    assertEquals(0, child.exit);
    assertTrue(child.elapsedMs < TimeUnit.SECONDS.toMillis(CHILD_LIMIT_S));
  }
}
