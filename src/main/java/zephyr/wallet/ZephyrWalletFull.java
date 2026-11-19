/**
 * Copyright (c) woodser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package zephyr.wallet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import zephyr.lib.utils.GenUtils;
import zephyr.lib.utils.JsonUtils;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import zephyr.common.ZephyrError;
import zephyr.common.ZephyrRpcConnection;
import zephyr.common.ZephyrTls;
import zephyr.common.ZephyrUtils;
import zephyr.daemon.model.ZephyrBlock;
import zephyr.daemon.model.ZephyrKeyImage;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.daemon.model.ZephyrTx;
import zephyr.daemon.model.ZephyrVersion;
import zephyr.wallet.model.ZephyrAccount;
import zephyr.wallet.model.ZephyrAccountTag;
import zephyr.wallet.model.ZephyrAddressBookEntry;
import zephyr.wallet.model.ZephyrBalance;
import zephyr.wallet.model.ZephyrCheckReserve;
import zephyr.wallet.model.ZephyrCheckTx;
import zephyr.wallet.model.ZephyrIncomingTransfer;
import zephyr.wallet.model.ZephyrIntegratedAddress;
import zephyr.wallet.model.ZephyrKeyImageImportResult;
import zephyr.wallet.model.ZephyrMessageSignatureResult;
import zephyr.wallet.model.ZephyrMessageSignatureType;
import zephyr.wallet.model.ZephyrMultisigInfo;
import zephyr.wallet.model.ZephyrMultisigInitResult;
import zephyr.wallet.model.ZephyrMultisigSignResult;
import zephyr.wallet.model.ZephyrOutputQuery;
import zephyr.wallet.model.ZephyrOutputWallet;
import zephyr.wallet.model.ZephyrReserveInfo;
import zephyr.wallet.model.ZephyrSubaddress;
import zephyr.wallet.model.ZephyrSyncResult;
import zephyr.wallet.model.ZephyrTransfer;
import zephyr.wallet.model.ZephyrTransferQuery;
import zephyr.wallet.model.ZephyrTxConfig;
import zephyr.wallet.model.ZephyrTxQuery;
import zephyr.wallet.model.ZephyrTxSet;
import zephyr.wallet.model.ZephyrTxWallet;
import zephyr.wallet.model.ZephyrWalletConfig;
import zephyr.wallet.model.ZephyrWalletListenerI;

/**
 * Implements a Zephyr wallet using fully client-side JNI bindings to zephyr's wallet2 in C++.
 */
public class ZephyrWalletFull extends ZephyrWalletDefault {
  
  // ----------------------------- PRIVATE SETUP ------------------------------

  // load the zephyr core C++ as a dynamic library
  static {
    ZephyrUtils.loadNativeLibrary();
  }
  
  // class variables
  private static final Logger LOGGER = Logger.getLogger(ZephyrWalletFull.class.getName());
  private static final long DEFAULT_SYNC_PERIOD_IN_MS = 10000; // default period betweeen syncs in ms
  
  // instance variables
  private long jniWalletHandle;                 // memory address of the wallet in c++; read and written by name in c++ (kept by the R8 consumer rules), zeroed by c++ once freed
  private long jniListenerHandle;               // memory address of the wallet listener in c++; set by name in c++ only (kept by the R8 consumer rules)
  private WalletJniListener jniListener;        // receives notifications from jni c++
  private volatile String password;             // written by changePassword(), read by other calls on other threads
  private volatile boolean isClosed;            // whether or not wallet is closed; read by the callbacks on the core's threads
  private boolean isFreed;                      // whether the native wallet is freed; guarded by closeLock
  private final ReentrantReadWriteLock callLock = new ReentrantReadWriteLock(); // native calls hold the read side, close() the write side
  private final Object closeLock = new Object(); // one close() at a time
  private final Object listening = new Object(); // a change of the listener set and the native listener's delivery switch, together
  private boolean listenerDelivery;              // whether the native listener passes callbacks to Java; guarded by listening
  private int coreListenerCalls;                 // calls of the core's listener set by the native listener, as setListenerJni reports them; read by the tests only; guarded by listening

  /**
   * Private constructor with a handle to the memory address of the wallet in c++.
   * 
   * @param jniWalletHandle memory address of the wallet in c++
   * @param password password of the wallet instance
   */
  private ZephyrWalletFull(long jniWalletHandle, String password) {
    this.jniWalletHandle = jniWalletHandle;
    this.jniListener = new WalletJniListener();
    this.password = password;
    this.isClosed = false;
  }
  
  // --------------------- WALLET MANAGEMENT UTILITIES ------------------------
  
  /**
   * Indicates if a wallet exists at the given path.
   * 
   * @param path is the path to check for a wallet
   * @return true if a wallet exists at the given path, false otherwise
   */
  public static boolean walletExists(String path) {
    try {
      return walletExistsJni(path);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    }
  }
  
  /**
   * Open an existing wallet using JNI bindings to wallet2.h.
   * 
   * @param path is the path to the wallet file to open
   * @param password is the password of the wallet file to open
   * @param networkType is the wallet's network type
   * @param daemonConnection is connection configuration to a daemon (default = an unconnected wallet)
   * @return the opened wallet
   */
  public static ZephyrWalletFull openWallet(String path, String password, ZephyrNetworkType networkType, ZephyrRpcConnection daemonConnection) {
    try {
      if (!walletExistsJni(path)) throw new ZephyrError("Wallet does not exist at path: " + path);
      if (networkType == null) throw new ZephyrError("Must provide a network type");
      if (daemonConnection != null) beforeDaemonUri(daemonConnection.getUri()); // before the wallet exists: a failure leaves nothing open
      long jniWalletHandle = openWalletJni(path, password, networkType.ordinal());
      ZephyrWalletFull wallet = new ZephyrWalletFull(jniWalletHandle, password);
      if (daemonConnection != null) connectOrClose(wallet, daemonConnection);
      return wallet;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    }
  }
  /**
   * Open an existing wallet from its files, without a daemon connection.
   *
   * @param path is the path to the wallet file to open
   * @param password is the password of the wallet file to open
   * @param networkType is the wallet's network type
   * @return the opened wallet
   */
  public static ZephyrWalletFull openWallet(String path, String password, ZephyrNetworkType networkType) { return openWallet(path, password, networkType, (ZephyrRpcConnection) null); }
  
  /**
   * Open an existing wallet from its files and connect it to a daemon.
   *
   * @param path is the path to the wallet file to open
   * @param password is the password of the wallet file to open
   * @param networkType is the wallet's network type
   * @param daemonUri is the daemon's URI (null for an unconnected wallet)
   * @return the opened wallet
   */
  public static ZephyrWalletFull openWallet(String path, String password, ZephyrNetworkType networkType, String daemonUri) {
    ZephyrRpcConnection daemonConnection;
    try {
      daemonConnection = daemonUri == null ? null : new ZephyrRpcConnection(daemonUri);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    }
    return openWallet(path, password, networkType, daemonConnection);
  }

  /**
   * Open an existing wallet from byte[] data using JNI bindings to wallet2.h
   * 
   * @param password the password of the wallet file to open
   * @param networkType the wallet's network type
   * @param keysData the wallet's keys data
   * @param cacheData the wallet's cache data
   * @param daemonConnection connection configuration to a daemon (default = an unconnected wallet)
   * @return the opened wallet
   */
  public static ZephyrWalletFull openWalletData(String password, ZephyrNetworkType networkType, byte[] keysData, byte[] cacheData, ZephyrRpcConnection daemonConnection) {
    try {
      if (networkType == null) throw new ZephyrError("Must provide a network type");
      if (daemonConnection != null) beforeDaemonUri(daemonConnection.getUri()); // before the wallet exists: a failure leaves nothing open
      long jniWalletHandle = openWalletDataJni(password, networkType.ordinal(), keysData, cacheData);
      ZephyrWalletFull wallet = new ZephyrWalletFull(jniWalletHandle, password);
      if (daemonConnection != null) connectOrClose(wallet, daemonConnection);
      return wallet;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    }
  }
  
  /**
   * <p>Open an existing wallet using JNI bindings to wallet2.h.</p>
   * 
   * <p>Example:</p>
   * 
   * <code>
   * ZephyrWallet wallet = ZephyrWalletFull.openWallet(new ZephyrWalletConfig()<br>
   * &nbsp;&nbsp; .setPath("mywallet")<br>
   * &nbsp;&nbsp; .setPassword("supersecretpassword")<br>
   * &nbsp;&nbsp; .setNetworkType(ZephyrNetworkType.STAGENET)<br>
   * &nbsp;&nbsp; .setServerUri("http://localhost:38083"));<br>
   * </code>
   * 
   * <p>
   * All supported configuration:<br>
   * &nbsp;&nbsp; path - path of the wallet to open<br>
   * &nbsp;&nbsp; password - password of the wallet to open<br>
   * &nbsp;&nbsp; networkType - network type of the wallet to open (one of ZephyrNetworkType.MAINNET|TESTNET|STAGENET)<br>
   * &nbsp;&nbsp; serverUri - uri of the wallet's daemon (optional)<br>
   * &nbsp;&nbsp; serverUsername - username to authenticate with the daemon (optional)<br>
   * &nbsp;&nbsp; serverPassword - password to authenticate with the daemon (optional)<br>
   * &nbsp;&nbsp; server - ZephyrRpcConnection providing server configuration (optional)<br>
   * </p>
   * 
   * @param config configures the wallet to open
   * @return the wallet instance
   */
  public static ZephyrWalletFull openWallet(ZephyrWalletConfig config) {
    try {
    
      // validate config
      if (config == null) throw new ZephyrError("Must specify config to open wallet");
      if (config.getPassword() == null) throw new ZephyrError("Must specify password to decrypt wallet");
      if (config.getNetworkType() == null) throw new ZephyrError("Must specify a network type: 'mainnet', 'testnet' or 'stagenet'");
      if (config.getMnemonic() != null) throw new ZephyrError("Cannot specify mnemonic when opening wallet");
      if (config.getSeedOffset() != null) throw new ZephyrError("Cannot specify seed offset when opening wallet");
      if (config.getPrimaryAddress() != null) throw new ZephyrError("Cannot specify primary address when opening wallet");
      if (config.getPrivateViewKey() != null) throw new ZephyrError("Cannot specify private view key when opening wallet");
      if (config.getPrivateSpendKey() != null) throw new ZephyrError("Cannot specify private spend key when opening wallet");
      if (config.getRestoreHeight() != null) throw new ZephyrError("Cannot specify restore height when opening wallet");
      if (config.getLanguage() != null) throw new ZephyrError("Cannot specify language when opening wallet");
      if (Boolean.TRUE.equals(config.getSaveCurrent())) throw new ZephyrError("Cannot save current wallet when opening full wallet");

      // read wallet data from disk if not provided
      if (config.getKeysData() == null) {
        return openWallet(config.getPath(), config.getPassword(), config.getNetworkType(), config.getServer());
      } else {
        return openWalletData(config.getPassword(), config.getNetworkType(), config.getKeysData(), config.getCacheData(), config.getServer());
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    }
  }
  
  /**
   * <p>Create a wallet using JNI bindings to wallet2.h.</p>
   * 
   * <p>Examples:</p>
   * 
   * <code>
   * // create stagenet wallet with randomly generated mnemonic<br>
   * ZephyrWallet wallet1 = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()<br>
   * &nbsp;&nbsp; .setPath("/mywallets/wallet1")<br>
   * &nbsp;&nbsp; .setPassword("supersecretpassword")<br>
   * &nbsp;&nbsp; .setNetworkType(ZephyrNetworkType.STAGENET)<br>
   * &nbsp;&nbsp; .setServerUri("http://localhost:38081") // leave blank for offline wallet<br>
   * &nbsp;&nbsp; .setServerUsername("superuser")<br>
   * &nbsp;&nbsp; .setServerPassword("abctesting123"));<br><br>
   * 
   * // restore mainnet wallet from mnemonic<br>
   * ZephyrWallet wallet2 = ZephyrWalletFull.createWallet(new ZephyrWalletConfig()<br>
   * &nbsp;&nbsp; .setPath("/mywallets/wallet2")  // leave blank for in-memory wallet<br>
   * &nbsp;&nbsp; .setPassword("abctesting123")<br>
   * &nbsp;&nbsp; .setNetworkType("mainnet")<br>
   * &nbsp;&nbsp; .setServerUri("http://localhost:18081")<br>
   * &nbsp;&nbsp; .setServerUsername("superuser")<br>
   * &nbsp;&nbsp; .setServerPassword("abctesting123")<br>
   * &nbsp;&nbsp; .setMnemonic("biggest duets beware eskimos coexist igloo...")<br>
   * &nbsp;&nbsp; .setRestoreHeight(573800l));<br>
   * </code>
   * 
   * <p>
   * All supported configuration:<br>
   * &nbsp;&nbsp; path - path of the wallet to create (optional, in-memory wallet if not given)<br>
   * &nbsp;&nbsp; password - password of the wallet to create<br>
   * &nbsp;&nbsp; networkType - network type of the wallet to create (one of ZephyrNetworkType.MAINNET|TESTNET|STAGENET)<br>
   * &nbsp;&nbsp; mnemonic - mnemonic of the wallet to create (optional, random wallet created if neither mnemonic nor keys given)<br>
   * &nbsp;&nbsp; seedOffset - the offset used to derive a new seed from the given mnemonic to recover a secret wallet from the mnemonic phrase<br>
   * &nbsp;&nbsp; primaryAddress - primary address of the wallet to create (only provide if restoring from keys)<br>
   * &nbsp;&nbsp; privateViewKey - private view key of the wallet to create (optional)<br>
   * &nbsp;&nbsp; privateSpendKey - private spend key of the wallet to create (optional)<br>
   * &nbsp;&nbsp; restoreHeight - block height to start scanning from (defaults to 0 unless generating random wallet)<br>
   * &nbsp;&nbsp; language - language of the wallet's mnemonic phrase (defaults to "English" or auto-detected)<br>
   * &nbsp;&nbsp; serverUri - uri of the wallet's daemon (optional)<br>
   * &nbsp;&nbsp; serverUsername - username to authenticate with the daemon (optional)<br>
   * &nbsp;&nbsp; serverPassword - password to authenticate with the daemon (optional)<br>
   * &nbsp;&nbsp; server - ZephyrRpcConnection providing server configuration (optional)<br>
   * &nbsp;&nbsp; accountLookahead - number of accounts to scan (optional)<br>
   * &nbsp;&nbsp; subaddressLookahead - number of subaddresses per account to scan (optional)<br>
   * </p>
   * 
   * @param config configures the wallet to create
   * @return the wallet instance
   */
  public static ZephyrWalletFull createWallet(ZephyrWalletConfig config) {
    try {
    
      // validate config
      if (config == null) throw new ZephyrError("Must specify config to open wallet");
      if (config.getNetworkType() == null) throw new ZephyrError("Must specify a network type: 'mainnet', 'testnet' or 'stagenet'");
      if (config.getPath() != null && !config.getPath().isEmpty() && ZephyrWalletFull.walletExists(config.getPath())) throw new ZephyrError("Wallet already exists: " + config.getPath());
      if (config.getMnemonic() != null && (config.getPrimaryAddress() != null || config.getPrivateViewKey() != null || config.getPrivateSpendKey() != null)) {
        throw new ZephyrError("Wallet may be initialized with a mnemonic or keys but not both");
      }
      if (Boolean.TRUE.equals(config.getSaveCurrent() != null)) throw new ZephyrError("Cannot save current wallet when creating full wallet");
    
      // the native side receives the server URI with the config (createWalletJni)
      beforeDaemonUri(daemonUriForCore(config.getServerUri()));
    
      // create wallet
      if (config.getMnemonic() != null) {
        if (config.getLanguage() != null) throw new ZephyrError("Cannot specify language when creating wallet from mnemonic");
        return createWalletFromMnemonic(config);
      } else if (config.getPrimaryAddress() != null || config.getPrivateSpendKey() != null) {
        if (config.getSeedOffset() != null) throw new ZephyrError("Cannot specify seed offset when creating wallet from keys");
        return createWalletFromKeys(config);
      } else {
        if (config.getSeedOffset() != null) throw new ZephyrError("Cannot specify seed offset when creating random wallet");
        if (config.getRestoreHeight() != null) throw new ZephyrError("Cannot specify restore height when creating random wallet");
        return createWalletRandom(config);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    }
  }
  
  private static ZephyrWalletFull createWalletFromMnemonic(ZephyrWalletConfig config) {
    if (config.getRestoreHeight() == null) config.setRestoreHeight(0l);
    long jniWalletHandle = createWalletJni(serializeWalletConfig(config));
    ZephyrWalletFull wallet = new ZephyrWalletFull(jniWalletHandle, config.getPassword());
    connectOrClose(wallet, config.getServer());
    return wallet;
  }
  
  private static ZephyrWalletFull createWalletFromKeys(ZephyrWalletConfig config) {
    if (config.getRestoreHeight() == null) config.setRestoreHeight(0l);
    if (config.getLanguage() == null) config.setLanguage(DEFAULT_LANGUAGE);
    try {
      long jniWalletHandle = createWalletJni(serializeWalletConfig(config));
      ZephyrWalletFull wallet = new ZephyrWalletFull(jniWalletHandle, config.getPassword());
      connectOrClose(wallet, config.getServer());
      return wallet;
    } catch (Exception e) {
      throw toZephyrError(e);
    }
  }
  
  private static ZephyrWalletFull createWalletRandom(ZephyrWalletConfig config) {
    if (config.getLanguage() == null) config.setLanguage(DEFAULT_LANGUAGE);
    long jniWalletHandle = createWalletJni(serializeWalletConfig(config));
    return new ZephyrWalletFull(jniWalletHandle, config.getPassword());
  }
  
  /**
   * Sets the daemon connection of a wallet that was just opened or created, and closes the wallet
   * again when that fails: the caller never receives it, so nothing else would free it.
   */
  private static void connectOrClose(ZephyrWalletFull wallet, ZephyrRpcConnection daemonConnection) {
    try {
      wallet.setDaemonConnection(daemonConnection);
    } catch (RuntimeException e) {
      try {
        wallet.close();
      } catch (RuntimeException closeFailure) {
        e.addSuppressed(closeFailure);
      }
      throw e;
    }
  }
  
  /**
   * Runs before every hand-over of a daemon URI to the native side (create, open from a path, open
   * from data, setDaemonConnection): ZephyrTls applies its root certificates for an https URI
   * when they are due.
   */
  private static void beforeDaemonUri(String uri) {
    ZephyrTls.beforeNativeConnection(uri);
  }
  
  /**
   * A daemon URI as it is handed to the native core: an http or https scheme in lower case (the
   * core switches TLS on only for a URI that starts with lowercase "https", so "HTTPS://" would
   * be spoken in clear text), then the scheme's default port when there is none.
   */
  private static String daemonUriForCore(String uri) {
    return withDefaultPort(withLowerCaseScheme(uri));
  }
  
  /**
   * An http or https scheme written in any other case, in lower case; nothing else of the URI
   * changes, and anything else goes through unchanged.
   */
  private static String withLowerCaseScheme(String uri) {
    if (uri == null) return null;
    int colon = uri.indexOf("://");
    if (colon != 4 && colon != 5) return uri;
    String scheme = uri.substring(0, colon);
    if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) return uri;
    return scheme.toLowerCase(java.util.Locale.ROOT) + uri.substring(colon);
  }
  
  /**
   * The scheme's default port (80 for http, 443 for https) for a URI without one: the core
   * connects to port 0 otherwise and blocks for minutes. Everything else (another scheme, a port,
   * no host, a URI that does not parse, null) goes through unchanged; user info, IPv6 literals,
   * paths, queries and fragments are kept as they are.
   */
  private static String withDefaultPort(String uri) {
    if (uri == null || uri.isEmpty()) return uri;
    try {
      URI parsed = new URI(uri);
      String scheme = parsed.getScheme();
      String authority = parsed.getRawAuthority();
      if (scheme == null || authority == null || parsed.getHost() == null || parsed.getPort() != -1) return uri;
      int port;
      if (scheme.equalsIgnoreCase("http")) port = 80;
      else if (scheme.equalsIgnoreCase("https")) port = 443;
      else return uri;
      int start = scheme.length() + 3; // "<scheme>://"
      if (!uri.startsWith("://", scheme.length()) || !uri.startsWith(authority, start)) return uri;
      int end = start + authority.length();
      return uri.substring(0, end) + (authority.endsWith(":") ? "" : ":") + port + uri.substring(end);
    } catch (URISyntaxException e) {
      return uri;
    }
  }
  
  private static final String NOT_CONNECTED = "Wallet is not connected to daemon";
  private static final String TLS_HINT = ": the node is an https node; its certificate may not be trusted (see ZephyrTls and the user guide, \"TLS to a node\")";
  
  /**
   * The error of a call that needs the daemon: the core's "Wallet is not connected to daemon" gets
   * a hint at TLS when the wallet's daemon URI is https.
   */
  private ZephyrError daemonError(Exception e) {
    String message = messageOf(e);
    if (NOT_CONNECTED.equals(message)) {
      try {
        String[] connection = getDaemonConnectionJni();
        if (connection != null && connection[0] != null && connection[0].regionMatches(true, 0, "https://", 0, 8)) message += TLS_HINT;
      } catch (Exception ignored) {
        // the plain message
      }
    }
    return new ZephyrError(message);
  }
  
  private static String serializeWalletConfig(ZephyrWalletConfig config) {
    Map<String, Object> configMap = JsonUtils.toMap(config);
    if (config.getServerUri() != null) configMap.put("serverUri", daemonUriForCore(config.getServerUri())); // the caller's config is not changed
    configMap.put("networkType", config.getNetworkType().ordinal());
    return JsonUtils.serialize(configMap);
  }
  
  /**
   * Get a list of available languages for the wallet's mnemonic phrase.
   * 
   * @return the available languages for the wallet's mnemonic phrase
   */
  public static List<String> getMnemonicLanguages() {
    try {
      return Arrays.asList(getMnemonicLanguagesJni());
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    }
  }
  
  // ------------ WALLET METHODS SPECIFIC TO JNI IMPLEMENTATION ---------------
  
  /**
   * Get the maximum height of the peers the wallet's daemon is connected to.
   *
   * @return the maximum height of the peers the wallet's daemon is connected to
   */
  public long getDaemonMaxPeerHeight() {
    beginCall();
    try {
      try {
        return getDaemonMaxPeerHeightJni();
      } catch (Exception e) {
        throw daemonError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  /**
   * Indicates if the wallet's daemon is synced with the network.
   * 
   * @return true if the daemon is synced with the network, false otherwise
   */
  public boolean isDaemonSynced() {
    beginCall();
    try {
      try {
        return isDaemonSyncedJni();
      } catch (Exception e) {
        throw daemonError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  /**
   * Indicates if the wallet is synced with the daemon.
   * 
   * @return true if the wallet is synced with the daemon, false otherwise
   */
  public boolean isSynced() {
    beginCall();
    try {
      try {
        return isSyncedJni();
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  /**
   * Get the wallet's network type (mainnet, testnet, or stagenet).
   * 
   * @return the wallet's network type
   */
  public ZephyrNetworkType getNetworkType() {
    beginCall();
    try {
      return ZephyrNetworkType.values()[getNetworkTypeJni()];
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  /**
   * Get the height of the first block that the wallet scans.
   * 
   * @return the height of the first block that the wallet scans
   */
  public long getRestoreHeight() {
    beginCall();
    try {
      return getRestoreHeightJni();
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  /**
   * Set the height of the first block that the wallet scans.
   * 
   * @param syncHeight is the height of the first block that the wallet scans
   */
  public void setRestoreHeight(long syncHeight) {
    beginCall();
    try {
      setRestoreHeightJni(syncHeight);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  /**
   * Move the wallet from its current path to the given path.
   * 
   * @param path is the new wallet's path
   */
  public void moveTo(String path) {
    beginCall();
    try {
      moveToJni(path, password);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  // -------------------------- COMMON WALLET METHODS -------------------------
  
  /**
   * {@inheritDoc}
   *
   * <p>The wallet's first listener adds the wallet's native listener to the core's listeners, once:
   * it stays there until {@code close()}, and every later addition or removal of a Java listener
   * only switches whether its callbacks reach Java, without calling into the core. The core's
   * listener set is not synchronized and the core's sync thread copies it several times per block,
   * so add the wallet's first listener before {@code startSyncing()} (and not from another thread
   * during a {@code sync()}): added while a sync runs, it changes that set under the copy once.
   * Once the native listener is in place, the core does its per-block listener work (a balance
   * check) also while no Java listener is registered; the callbacks then return in the native
   * layer, before they attach a thread or call into Java. On which threads callbacks run, and that
   * they can run on several threads at once, is described at {@link ZephyrWalletListenerI}.</p>
   *
   * <p>From inside a callback the wallet can be read, listeners added or removed, and the other
   * calls made, except those that cannot run there, which throw ZephyrError before they reach the
   * core. The rule follows from the core function each call ends in:</p>
   * <ul>
   * <li>every {@code sync}, {@code scanTxs}, {@code rescanBlockchain}, {@code makeMultisig} and
   * {@code exchangeMultisigKeys}: the core function ({@code lock_and_sync}, {@code scan_txs},
   * {@code make_multisig}, {@code exchange_multisig_keys}) locks the core's sync mutex, which the
   * refresh that delivers the callback holds (the thread would wait for itself);</li>
   * <li>every call that relays ({@code createTx}, {@code createTxs}, {@code sweepUnlocked},
   * {@code sweepOutput} and {@code sweepDust} with relay; {@code relayTx}, {@code relayTxs},
   * {@code submitTxs}) and {@code importKeyImages}: the core notifies the listeners again, from
   * inside the notification that is running;</li>
   * <li>{@code submitMultisigTxHex}: it relays as well ({@code submit_multisig_tx_hex} commits each
   * transaction with wallet2's {@code commit_tx}) and marks the outputs it spends as spent, without
   * a notification;</li>
   * <li>{@code rescanSpent}, {@code importOutputs}, {@code importKeyImages} and
   * {@code importMultisigHex}: they change the wallet's outputs while the refresh that delivers
   * the callback stands between two of its steps ({@code importMultisigHex} also runs a refresh of
   * its own);</li>
   * <li>{@code startSyncing}: it would start the core's sync thread from inside a sync;</li>
   * <li>{@code close}: it would wait for the callback that calls it.</li>
   * </ul>
   *
   * <p>Not refused, as read in the core: {@code signMultisigTxHex} ({@code sign_multisig_tx} takes
   * no lock, notifies nothing and changes only the signed outputs' multisig nonces and the stored
   * transaction keys, which a refresh does not use); {@code changePassword}
   * ({@code change_password} saves the wallet as {@code save()} does and rewrites its keys file;
   * the core keeps the keys unencrypted in memory, so nothing is re-encrypted under the refresh);
   * {@code setDaemonConnection} ({@code set_daemon_connection} waits for a node request under way,
   * then points the core at the new node, from which the paused refresh goes on).</p>
   *
   * <p>A read is never refused, but it is not always free of callbacks: a read that can include
   * pool transactions ({@code getTxs}, {@code getTransfers} and the like) first refreshes the
   * core's view of the node's pool, and a transaction to the wallet found there is notified at
   * once, on the same thread, so the listeners can be called again from inside the callback that
   * made the read.</p>
   */
  @Override
  public void addListener(ZephyrWalletListenerI listener) {
    beginCall();
    try {
      synchronized (listening) {
        super.addListener(listener);
        try {
          updateDelivery();
        } catch (RuntimeException e) {
          listeners.remove(listener); // not registered: the wallet does not keep it
          throw e;
        }
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public void removeListener(ZephyrWalletListenerI listener) {
    beginCall();
    try {
      synchronized (listening) {
        super.removeListener(listener);
        updateDelivery(); // the last one: the native listener stops delivering, and stays in the core's listeners until close()
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public Set<ZephyrWalletListenerI> getListeners() {
    assertNotClosed();
    return super.getListeners();
  }
  
  @Override
  public boolean isViewOnly() {
    beginCall();
    try {
      return isViewOnlyJni();
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  /**
   * Set the wallet's daemon connection. An {@code http} or {@code https} URI without a port gets
   * the scheme's default port (80 or 443) before it reaches the native core, which would otherwise
   * connect to port 0; {@link #getDaemonConnection()} then reports the URI with that port. An
   * {@code http} or {@code https} scheme in another case ({@code HTTPS://}) reaches the core in
   * lower case, since the core switches TLS on only for a lowercase {@code https}. For an
   * {@code https} URI, {@link ZephyrTls} applies its root certificates first when they are due.
   *
   * @param daemonConnection the daemon connection, or null to disconnect
   */
  @Override
  public void setDaemonConnection(ZephyrRpcConnection daemonConnection) {
    beginCall();
    try {
      if (daemonConnection == null) setDaemonConnectionJni("", "", "");
      else {
        String uri = daemonUriForCore(daemonConnection.getUri() == null ? "" : daemonConnection.getUri());
        beforeDaemonUri(uri);
        try {
          setDaemonConnectionJni(uri, daemonConnection.getUsername(), daemonConnection.getPassword());
        } catch (Exception e) {
          throw toZephyrError(e);
        }
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public void setProxyUri(String uri) {
    beginCall();
    try {
      if (uri == null) uri = "";
      try {
        setProxyJni(uri);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public ZephyrRpcConnection getDaemonConnection() {
    beginCall();
    try {
      try {
        String[] vals = getDaemonConnectionJni();
        return vals == null ? null : new ZephyrRpcConnection(vals[0], vals[1], vals[2]);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public boolean isConnectedToDaemon() {
    beginCall();
    try {
      try {
        return isConnectedToDaemonJni();
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public ZephyrVersion getVersion() {
    beginCall();
    try {
      try {
        String versionJson = getVersionJni();
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, versionJson, ZephyrVersion.class);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public String getPath() {
    beginCall();
    try {
      String path = getPathJni();
      return path.isEmpty() ? null : path;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public String getMnemonic() {
    beginCall();
    try {
      String mnemonic = getMnemonicJni();
      if ("".equals(mnemonic)) return null;
      return mnemonic;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public String getMnemonicLanguage() {
    beginCall();
    try {
      String mnemonicLanguage = getMnemonicLanguageJni();
      if ("".equals(mnemonicLanguage)) return null;
      return mnemonicLanguage;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public String getPrivateViewKey() {
    beginCall();
    try {
      return getPrivateViewKeyJni();
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public String getPrivateSpendKey() {
    beginCall();
    try {
      String privateSpendKey = getPrivateSpendKeyJni();
      if ("".equals(privateSpendKey)) return null;
      return privateSpendKey;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public String getPublicViewKey() {
    beginCall();
    try {
      return getPublicViewKeyJni();
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public String getPublicSpendKey() {
    beginCall();
    try {
      return getPublicSpendKeyJni();
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public ZephyrIntegratedAddress getIntegratedAddress(String standardAddress, String paymentId) {
    beginCall();
    try {
      try {
        String integratedAddressJson = getIntegratedAddressJni(standardAddress, paymentId);
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, integratedAddressJson, ZephyrIntegratedAddress.class);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public ZephyrIntegratedAddress decodeIntegratedAddress(String integratedAddress) {
    beginCall();
    try {
      try {
        String integratedAddressJson = decodeIntegratedAddressJni(integratedAddress);
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, integratedAddressJson, ZephyrIntegratedAddress.class);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public long getHeight() {
    beginCall();
    try {
      return getHeightJni();
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public long getDaemonHeight() {
    beginCall();
    try {
      try {
        return getDaemonHeightJni();
      } catch (Exception e) {
        throw daemonError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public long getHeightByDate(int year, int month, int day) {
    beginCall();
    try {
      try {
        return getHeightByDateJni(year, month ,day);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  /**
   * {@inheritDoc}
   *
   * <p>A sync that {@code close()} cuts short (it ends the core's refresh at its next batch of
   * blocks) throws ZephyrError("Wallet is closed"), never a result that looks complete. Throws
   * ZephyrError when called from inside a listener callback.</p>
   */
  @Override
  public ZephyrSyncResult sync(Long startHeight, ZephyrWalletListenerI listener) {
    refuseInCallback("sync()");
    beginCall();
    try {
      if (startHeight == null) startHeight = Math.max(getHeight(), getRestoreHeight());
    
      // register listener if given
      if (listener != null) addListener(listener);
    
      // sync wallet and handle exception; close() from another thread ends the sync at the core's
      // next batch of blocks, and the sync then ends in this one "Wallet is closed"
      try {
        Object[] results = syncJni(startHeight);
        if (isClosed) throw new ZephyrError("Wallet is closed"); // cut short: not a complete result
        return new ZephyrSyncResult((long) results[0], (boolean) results[1]);
      } catch (Exception e) {
        if (isClosed) throw new ZephyrError("Wallet is closed");
        throw daemonError(e);
      } finally {
        if (listener != null) removeListenerQuietly(listener); // throws nothing, also when closed meanwhile or removed by itself
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public void startSyncing(Long syncPeriodInMs) {
    refuseInCallback("startSyncing()");
    beginCall();
    try {
      try {
        startSyncingJni(syncPeriodInMs == null ? DEFAULT_SYNC_PERIOD_IN_MS : syncPeriodInMs);
      } catch (Exception e) {
        throw daemonError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public void stopSyncing() {
    beginCall();
    try {
      try {
        stopSyncingJni();
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  /**
   * {@inheritDoc}
   *
   * <p>A scan that {@code close()} cuts short (it ends the core's refresh after the transactions,
   * at its next batch of blocks) throws ZephyrError("Wallet is closed"), never as if it had
   * completed. Throws ZephyrError when called from inside a listener callback.</p>
   */
  @Override
  public void scanTxs(Collection<String> txHashes) {
    refuseInCallback("scanTxs()");
    beginCall();
    try {
      String[] txMetadatasArr = txHashes.toArray(new String[txHashes.size()]); // convert to array for jni
      try {
        scanTxsJni(txMetadatasArr);
        if (isClosed) throw new ZephyrError("Wallet is closed"); // cut short: not complete
      } catch (Exception e) {
        if (isClosed) throw new ZephyrError("Wallet is closed");
        throw daemonError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public void rescanSpent() {
    refuseInCallback("rescanSpent()");
    beginCall();
    try {
      try {
        rescanSpentJni();
      } catch (Exception e) {
        throw daemonError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>A rescan that {@code close()} cuts short (it ends the core's refresh at its next batch of
   * blocks) throws ZephyrError("Wallet is closed"), never as if it had completed. Throws
   * ZephyrError when called from inside a listener callback.</p>
   */
  @Override
  public void rescanBlockchain() {
    refuseInCallback("rescanBlockchain()");
    beginCall();
    try {
      try {
        rescanBlockchainJni();
        if (isClosed) throw new ZephyrError("Wallet is closed"); // cut short: not complete
      } catch (Exception e) {
        if (isClosed) throw new ZephyrError("Wallet is closed");
        throw daemonError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public List<ZephyrAccount> getAccounts(boolean includeSubaddresses, String tag) {
    beginCall();
    try {
      String accountsJson = getAccountsJni(includeSubaddresses, tag);
      List<ZephyrAccount> accounts = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, accountsJson, AccountsContainer.class).accounts;
      for (ZephyrAccount account : accounts) sanitizeAccount(account);
      return accounts;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public ZephyrAccount getAccount(int accountIdx, boolean includeSubaddresses) {
    beginCall();
    try {
      String accountJson = getAccountJni(accountIdx, includeSubaddresses);
      ZephyrAccount account = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, accountJson, ZephyrAccount.class);
      sanitizeAccount(account);
      return account;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public ZephyrAccount createAccount(String label) {
    beginCall();
    try {
      String accountJson = createAccountJni(label);
      ZephyrAccount account = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, accountJson, ZephyrAccount.class);
      sanitizeAccount(account);
      return account;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public List<ZephyrSubaddress> getSubaddresses(int accountIdx, List<Integer> subaddressIndices) {
    beginCall();
    try {
      String subaddresses_json = getSubaddressesJni(accountIdx, GenUtils.listToIntArray(subaddressIndices));
      List<ZephyrSubaddress> subaddresses = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, subaddresses_json, SubaddressesContainer.class).subaddresses;
      for (ZephyrSubaddress subaddress : subaddresses) sanitizeSubaddress(subaddress);
      return subaddresses;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public ZephyrSubaddress createSubaddress(int accountIdx, String label) {
    beginCall();
    try {
      String subaddressJson = createSubaddressJni(accountIdx, label);
      ZephyrSubaddress subaddress = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, subaddressJson, ZephyrSubaddress.class);
      sanitizeSubaddress(subaddress);
      return subaddress;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public void setSubaddressLabel(int accountIdx, int subaddressIdx, String label) {
    beginCall();
    try {
      if (label == null) label = "";
      setSubaddressLabelJni(accountIdx, subaddressIdx, label);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public String getAddress(int accountIdx, int subaddressIdx) {
    beginCall();
    try {
      return getAddressJni(accountIdx, subaddressIdx);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public ZephyrSubaddress getAddressIndex(String address) {
    beginCall();
    try {
      try {
        String subaddressJson = getAddressIndexJni(address);
        ZephyrSubaddress subaddress = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, subaddressJson, ZephyrSubaddress.class);
        return sanitizeSubaddress(subaddress);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public ZephyrBalance getBalance() {
    beginCall();
    try {
      try {
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, getBalanceWalletJni(), ZephyrBalance.class);
      } catch (ZephyrError e) {
        throw new ZephyrError(e.getMessage());
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public Map<Integer, ZephyrBalance> getBalance(Integer accountIdx) {
    beginCall();
    try {
      try {
        if (accountIdx == null) throw new ZephyrError("Must provide account index");
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, getBalanceAccountJni(accountIdx), new TypeReference<Map<Integer, ZephyrBalance>>(){});
      } catch (ZephyrError e) {
        throw new ZephyrError(e.getMessage());
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public BigInteger getBalance(String assetType, Integer accountIdx, Integer subaddressIdx) {
    beginCall();
    try {
      try {
        if (assetType == null) throw new ZephyrError("Must provide asset type");
        if (accountIdx == null) throw new ZephyrError("Must provide account index");
        if (subaddressIdx == null) return new BigInteger(getBalanceAssetAccountJni(assetType, accountIdx));
        else return new BigInteger(getBalanceAssetSubaddressJni(assetType, accountIdx, subaddressIdx));
      } catch (ZephyrError e) {
        throw new ZephyrError(e.getMessage());
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public ZephyrBalance getUnlockedBalance() {
    beginCall();
    try {
      try {
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, getUnlockedBalanceWalletJni(), ZephyrBalance.class);
      } catch (ZephyrError e) {
        throw new ZephyrError(e.getMessage());
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public Map<Integer, ZephyrBalance> getUnlockedBalance(Integer accountIdx) {
    beginCall();
    try {
      try {
        if (accountIdx == null) throw new ZephyrError("Must provide account index");
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, getUnlockedBalanceAccountJni(accountIdx), new TypeReference<Map<Integer, ZephyrBalance>>(){});
      } catch (ZephyrError e) {
        throw new ZephyrError(e.getMessage());
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public BigInteger getUnlockedBalance(String assetType, Integer accountIdx, Integer subaddressIdx) {
    beginCall();
    try {
      try {
        if (assetType == null) throw new ZephyrError("Must provide asset type");
        if (accountIdx == null) throw new ZephyrError("Must provide account index");
        if (subaddressIdx == null) return new BigInteger(getUnlockedBalanceAssetAccountJni(assetType, accountIdx));
        else return new BigInteger(getUnlockedBalanceAssetSubaddressJni(assetType, accountIdx, subaddressIdx));
      } catch (ZephyrError e) {
        throw new ZephyrError(e.getMessage());
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public ZephyrReserveInfo getReserveInfo() {
    beginCall();
    try {
      try {
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, getReserveInfoJni(), ZephyrReserveInfo.class);
      } catch (ZephyrError e) {
        throw new ZephyrError(e.getMessage());
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public Map<String, BigInteger> getCirculatingSupply() {
    beginCall();
    try {
      try {
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, getCirculatingSupplyJni(), new TypeReference<Map<String, BigInteger>>(){});
      } catch (ZephyrError e) {
        throw new ZephyrError(e.getMessage());
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public List<ZephyrTxWallet> getTxs(ZephyrTxQuery query, Collection<String> missingTxHashes) {
    beginCall();
    try {
    
      // copy and normalize tx query up to block
      query = query == null ? new ZephyrTxQuery() : query.copy();
      if (query.getBlock() == null) query.setBlock(new ZephyrBlock().setTxs(query));
    
      // serialize query from block and fetch txs from jni
      String blocksJson;
      try {
        blocksJson = getTxsJni(JsonUtils.serialize(query.getBlock()));
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    
      // deserialize and return txs
      return deserializeTxs(query, blocksJson, missingTxHashes);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public List<ZephyrTransfer> getTransfers(ZephyrTransferQuery query) {
    beginCall();
    try {
    
      // copy and normalize query up to block
      query = normalizeTransferQuery(query);
    
      // serialize query from block and fetch transfers from jni
      String blocksJson;
      try {
        blocksJson = getTransfersJni(JsonUtils.serialize(query.getTxQuery().getBlock()));
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    
      // deserialize and return transfers
      return deserializeTransfers(query, blocksJson);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public List<ZephyrOutputWallet> getOutputs(ZephyrOutputQuery query) {
    beginCall();
    try {
    
      // copy and normalize query up to block
      if (query == null) query = new ZephyrOutputQuery();
      else {
        if (query.getTxQuery() == null) query = query.copy();
        else {
          ZephyrTxQuery txQuery = query.getTxQuery().copy();
          if (query.getTxQuery().getOutputQuery() == query) query = txQuery.getOutputQuery();
          else {
            if (query.getTxQuery().getOutputQuery() != null) throw new ZephyrError("Output query's tx query must be circular reference or null");
            query = query.copy();
            query.setTxQuery(txQuery);
          }
        }
      }
      if (query.getTxQuery() == null) query.setTxQuery(new ZephyrTxQuery());
      query.getTxQuery().setOutputQuery(query);
      if (query.getTxQuery().getBlock() == null) query.getTxQuery().setBlock(new ZephyrBlock().setTxs(query.getTxQuery()));
    
      // serialize query from block and fetch outputs from jni
      String blocksJson = getOutputsJni(JsonUtils.serialize(query.getTxQuery().getBlock()));
    
      // deserialize and return outputs
      return deserializeOutputs(query, blocksJson);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  /**
   * {@inheritDoc}
   *
   * <p>In this wallet every failure is a ZephyrError, as for its other calls.</p>
   */
  @Override
  public String getOutputAssetType(ZephyrOutputWallet output) {
    assertNotClosed();
    try {
      return super.getOutputAssetType(output);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    }
  }
  
  @Override
  public String exportOutputs(boolean all) {
    beginCall();
    try {
      String outputsHex = exportOutputsJni(all);
      return outputsHex.isEmpty() ? null : outputsHex;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public int importOutputs(String outputsHex) {
    refuseInCallback("importOutputs()");
    beginCall();
    try {
      return importOutputsJni(outputsHex);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public List<ZephyrKeyImage> exportKeyImages(boolean all) {
    beginCall();
    try {
      String keyImagesJson = exportKeyImagesJni(all);
      List<ZephyrKeyImage> keyImages = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, keyImagesJson, KeyImagesContainer.class).keyImages;
      return keyImages;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public ZephyrKeyImageImportResult importKeyImages(List<ZephyrKeyImage> keyImages) {
    refuseInCallback("importKeyImages()");
    beginCall();
    try {
    
      // wrap and serialize key images in container for jni
      KeyImagesContainer keyImageContainer = new KeyImagesContainer(keyImages);
      String importResultJson = importKeyImagesJni(JsonUtils.serialize(keyImageContainer));
    
      // deserialize response
      return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, importResultJson, ZephyrKeyImageImportResult.class);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public List<ZephyrKeyImage> getNewKeyImagesFromLastImport() {
    assertNotClosed();
    throw new ZephyrError("ZephyrWalletFull.getNewKeyImagesFromLastImport() not implemented");
  }
  
  @Override
  public void freezeOutput(String keyImage) {
    beginCall();
    try {
      try {
        freezeOutputJni(keyImage);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public void thawOutput(String keyImage) {
    beginCall();
    try {
      try {
        thawOutputJni(keyImage);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public boolean isOutputFrozen(String keyImage) {
    beginCall();
    try {
      try {
        return isOutputFrozenJni(keyImage);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public List<ZephyrTxWallet> createTxs(ZephyrTxConfig config) {
    if (config != null && Boolean.TRUE.equals(config.getRelay())) refuseInCallback("createTx() or createTxs() with relay");
    beginCall();
    try {
      // validate request
      if (config == null) throw new ZephyrError("Tx config cannot be null");
    
      // submit tx config to JNI and get response as json rooted at tx set
      String txSetJson;
      try {
        txSetJson = createTxsJni(JsonUtils.serialize(config));
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    
      // deserialize and return txs; the log names the hashes only, as the transactions carry their
      // secret keys and the config the destinations
      ZephyrTxSet txSet = JsonUtils.deserialize(txSetJson, ZephyrTxSet.class);
      logCreated("createTxs", txSet.getTxs());
      return txSet.getTxs();
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public ZephyrTxWallet sweepOutput(ZephyrTxConfig config) {
    if (config != null && Boolean.TRUE.equals(config.getRelay())) refuseInCallback("sweepOutput() with relay");
    beginCall();
    try {
      try {
        String txSetJson = sweepOutputJni(JsonUtils.serialize(config));
        ZephyrTxSet txSet = JsonUtils.deserialize(txSetJson, ZephyrTxSet.class);
        return txSet.getTxs().get(0);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public List<ZephyrTxWallet> sweepUnlocked(ZephyrTxConfig config) {
    if (config != null && Boolean.TRUE.equals(config.getRelay())) refuseInCallback("sweepUnlocked() with relay");
    beginCall();
    try {
    
      // validate request
      if (config == null) throw new ZephyrError("Send request cannot be null");
    
      // submit send request to JNI and get response as json rooted at tx set
      String txSetsJson;
      try {
        txSetsJson = sweepUnlockedJni(JsonUtils.serialize(config));
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    
      // deserialize tx sets
      List<ZephyrTxSet> txSets = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, txSetsJson, TxSetsContainer.class).txSets;
    
      // return txs
      List<ZephyrTxWallet> txs = new ArrayList<ZephyrTxWallet>();
      for (ZephyrTxSet txSet : txSets) txs.addAll(txSet.getTxs());
      logCreated("sweepUnlocked", txs);
      return txs;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public List<ZephyrTxWallet> sweepDust(boolean relay) {
    if (relay) refuseInCallback("sweepDust() with relay");
    beginCall();
    try {
      try {
        String txSetJson = sweepDustJni(relay);
        ZephyrTxSet txSet = JsonUtils.deserialize(txSetJson, ZephyrTxSet.class);
        if (txSet.getTxs() == null) txSet.setTxs(new ArrayList<ZephyrTxWallet>());
        return txSet.getTxs();
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public List<String> relayTxs(Collection<String> txMetadatas) {
    refuseInCallback("relayTx() or relayTxs()");
    beginCall();
    try {
      String[] txMetadatasArr = txMetadatas.toArray(new String[txMetadatas.size()]); // convert to array for jni
      try {
        return Arrays.asList(relayTxsJni(txMetadatasArr));
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public ZephyrTxSet describeTxSet(ZephyrTxSet txSet) {
    beginCall();
    try {
      txSet = new ZephyrTxSet()
              .setUnsignedTxHex(txSet.getUnsignedTxHex())
              .setSignedTxHex(txSet.getSignedTxHex())
              .setMultisigTxHex(txSet.getMultisigTxHex());
      String describedTxSetJson;
      try {
        describedTxSetJson = describeTxSetJni(JsonUtils.serialize(txSet));
      } catch (Exception e) {
        throw toZephyrError(e);
      }
      return JsonUtils.deserialize(describedTxSetJson, ZephyrTxSet.class);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public String signTxs(String unsignedTxHex) {
    beginCall();
    try {
      try {
        return signTxsJni(unsignedTxHex);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public List<String> submitTxs(String signedTxHex) {
    refuseInCallback("submitTxs()");
    beginCall();
    try {
      try {
        return Arrays.asList(submitTxsJni(signedTxHex));
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public ZephyrCheckTx checkTxKey(String txHash, String txKey, String address) {
    beginCall();
    try {
      try {
        String checkStr = checkTxKeyJni(txHash, txKey, address);
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, checkStr, ZephyrCheckTx.class);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public String getTxProof(String txHash, String address, String message) {
    beginCall();
    try {
      try {
        return getTxProofJni(txHash, address, message);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public ZephyrCheckTx checkTxProof(String txHash, String address, String message, String signature) {
    beginCall();
    try {
      try {
        String checkStr = checkTxProofJni(txHash, address, message, signature);
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, checkStr, ZephyrCheckTx.class);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public String getSpendProof(String txHash, String message) {
    beginCall();
    try {
      try {
        return getSpendProofJni(txHash, message);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public boolean checkSpendProof(String txHash, String message, String signature) {
    beginCall();
    try {
      try {
        return checkSpendProofJni(txHash, message, signature);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public String getReserveProofWallet(String message) {
    beginCall();
    try {
      try {
        return getReserveProofWalletJni(message);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public String getReserveProofAccount(int accountIdx, BigInteger amount, String message) {
    beginCall();
    try {
      try {
        return getReserveProofAccountJni(accountIdx, amount.toString(), message);
      } catch (Exception e) {
        throw new ZephyrError(messageOf(e), -1);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public ZephyrCheckReserve checkReserveProof(String address, String message, String signature) {
    beginCall();
    try {
      try {
        String checkStr = checkReserveProofJni(address, message, signature);
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, checkStr, ZephyrCheckReserve.class);
      } catch (Exception e) {
        throw new ZephyrError(messageOf(e), -1);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public String signMessage(String msg, ZephyrMessageSignatureType signatureType, int accountIdx, int subaddressIdx) {
    beginCall();
    try {
      return signMessageJni(msg, signatureType.ordinal(), accountIdx, subaddressIdx);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public ZephyrMessageSignatureResult verifyMessage(String msg, String address, String signature) {
    beginCall();
    try {
      try {
        String resultJson = verifyMessageJni(msg, address, signature);
        Map<String, Object> result = JsonUtils.deserialize(resultJson, new TypeReference<Map<String, Object>>(){});
        boolean isGood = (boolean) result.get("isGood");
        return new ZephyrMessageSignatureResult(
            isGood,
            !isGood ? null : (Boolean) result.get("isOld"),
            !isGood ? null : "spend".equals(result.get("signatureType")) ? ZephyrMessageSignatureType.SIGN_WITH_SPEND_KEY : ZephyrMessageSignatureType.SIGN_WITH_VIEW_KEY,
            !isGood ? null : (Integer) result.get("version"));
      } catch (Exception e) {
        return new ZephyrMessageSignatureResult(false, null, null, null); // jni can differentiate incorrect from invalid address, but rpc returns -2 for both, so returning bad result for consistency
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public String getTxKey(String txHash) {
    beginCall();
    try {
      try {
        return getTxKeyJni(txHash);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public List<String> getTxNotes(List<String> txHashes) {
    beginCall();
    try {
      return Arrays.asList(getTxNotesJni(txHashes.toArray(new String[txHashes.size()])));  // convert to array for jni
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public void setTxNotes(List<String> txHashes, List<String> notes) {
    beginCall();
    try {
      setTxNotesJni(txHashes.toArray(new String[txHashes.size()]), notes.toArray(new String[notes.size()]));
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public List<ZephyrAddressBookEntry> getAddressBookEntries(List<Integer> entryIndices) {
    beginCall();
    try {
      if (entryIndices == null) entryIndices = new ArrayList<Integer>();
      String entriesJson = getAddressBookEntriesJni(GenUtils.listToIntArray(entryIndices));
      List<ZephyrAddressBookEntry> entries = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, entriesJson, AddressBookEntriesContainer.class).entries;
      if (entries == null) entries = new ArrayList<ZephyrAddressBookEntry>();
      return entries;
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public int addAddressBookEntry(String address, String description) {
    beginCall();
    try {
      return addAddressBookEntryJni(address, description);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public void editAddressBookEntry(int index, boolean setAddress, String address, boolean setDescription, String description) {
    beginCall();
    try {
      editAddressBookEntryJni(index, setAddress, address, setDescription, description);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public void deleteAddressBookEntry(int entryIdx) {
    beginCall();
    try {
      deleteAddressBookEntryJni(entryIdx);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public void tagAccounts(String tag, Collection<Integer> accountIndices) {
    assertNotClosed();
    throw new ZephyrError("ZephyrWalletFull.tagAccounts() not implemented");
  }

  @Override
  public void untagAccounts(Collection<Integer> accountIndices) {
    assertNotClosed();
    throw new ZephyrError("ZephyrWalletFull.untagAccounts() not implemented");
  }

  @Override
  public List<ZephyrAccountTag> getAccountTags() {
    assertNotClosed();
    throw new ZephyrError("ZephyrWalletFull.getAccountTags() not implemented");
  }

  @Override
  public void setAccountTagLabel(String tag, String label) {
    assertNotClosed();
    throw new ZephyrError("ZephyrWalletFull.setAccountTagLabel() not implemented");
  }

  @Override
  public String getPaymentUri(ZephyrTxConfig request) {
    beginCall();
    try {
      try {
        return getPaymentUriJni(JsonUtils.serialize(request));
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public ZephyrTxConfig parsePaymentUri(String uri) {
    beginCall();
    try {
      try {
        String sendRequestJson = parsePaymentUriJni(uri);
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, sendRequestJson, ZephyrTxConfig.class);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public String getAttribute(String key) {
    beginCall();
    try {
      if (key == null) throw new ZephyrError("Must provide attribute key"); // the native side cannot take null
      return getAttributeJni(key);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public void setAttribute(String key, String val) {
    beginCall();
    try {
      if (key == null) throw new ZephyrError("Must provide attribute key"); // the native side cannot take null
      if (val == null) throw new ZephyrError("Must provide attribute value");
      setAttributeJni(key, val);
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public void startMining(Long numThreads, Boolean backgroundMining, Boolean ignoreBattery) {
    beginCall();
    try {
      try {
        startMiningJni(numThreads == null ? 0l : (long) numThreads, Boolean.TRUE.equals(backgroundMining), Boolean.TRUE.equals(ignoreBattery));
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public void stopMining() {
    beginCall();
    try {
      try {
        stopMiningJni();
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  
  @Override
  public boolean isMultisigImportNeeded() {
    beginCall();
    try {
      return isMultisigImportNeededJni();
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public ZephyrMultisigInfo getMultisigInfo() {
    beginCall();
    try {
      try {
        String multisigInfoJson = getMultisigInfoJni();
        return JsonUtils.deserialize(multisigInfoJson, ZephyrMultisigInfo.class);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public String prepareMultisig() {
    beginCall();
    try {
      return prepareMultisigJni();
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public String makeMultisig(List<String> multisigHexes, int threshold, String password) {
    refuseInCallback("makeMultisig()");
    beginCall();
    try {
      try {
        return makeMultisigJni(multisigHexes.toArray(new String[multisigHexes.size()]), threshold, password);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public ZephyrMultisigInitResult exchangeMultisigKeys(List<String> multisigHexes, String password) {
    refuseInCallback("exchangeMultisigKeys()");
    beginCall();
    try {
      try {
        String initMultisigResultJson = exchangeMultisigKeysJni(multisigHexes.toArray(new String[multisigHexes.size()]), password);
        return JsonUtils.deserialize(initMultisigResultJson, ZephyrMultisigInitResult.class);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public String exportMultisigHex() {
    beginCall();
    try {
      try {
        return exportMultisigHexJni();
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public int importMultisigHex(List<String> multisigHexes) {
    refuseInCallback("importMultisigHex()");
    beginCall();
    try {
      try {
        return importMultisigHexJni(multisigHexes.toArray(new String[multisigHexes.size()]));
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  @Override
  public ZephyrMultisigSignResult signMultisigTxHex(String multisigTxHex) {
    beginCall();
    try {
      try {
        String signMultisigResultJson = signMultisigTxHexJni(multisigTxHex);
        return JsonUtils.deserialize(signMultisigResultJson, ZephyrMultisigSignResult.class);
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Throws ZephyrError when called from inside a listener callback: the core's
   * {@code submit_multisig_tx_hex} relays each transaction (wallet2's {@code commit_tx}) and marks
   * the outputs it spends as spent, under the refresh that delivers the callback.</p>
   */
  @Override
  public List<String> submitMultisigTxHex(String signedMultisigTxHex) {
    refuseInCallback("submitMultisigTxHex()");
    beginCall();
    try {
      try {
        return Arrays.asList(submitMultisigTxHexJni(signedMultisigTxHex));
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }

  /**
   * Get the wallet's keys and cache data.
   * 
   * @return the keys and cache data, respectively
   */
  public synchronized byte[][] getData() {
    beginCall();
    try {
      return new byte[][] { getKeysFileBufferJni(password, isViewOnly()), getCacheFileBufferJni() };
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  @Override
  public void changePassword(String oldPassword, String newPassword) {
    beginCall();
    try {
      try {
        if (!password.equals(oldPassword)) throw new ZephyrError("Invalid original password.");
        changePasswordJni(oldPassword, newPassword);
        password = newPassword;
      } catch (Exception e) {
        throw toZephyrError(e);
      }
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  /**
   * {@inheritDoc}
   *
   * <p>Not exclusive with background syncing: while {@code startSyncing()} runs (and after
   * {@code stopSyncing()}, until the core's sync thread has finished its current sync) the core may
   * be inside a refresh of the wallet that this save reads. To save a wallet that syncs in the
   * background, use {@code close(true)}, which saves after that thread has ended.</p>
   */
  @Override
  public void save() {
    beginCall();
    try {
      saveJni();
    } catch (RuntimeException | AssertionError e) {
      throw toZephyrError(e);
    } finally {
      endCall();
    }
  }
  
  /**
   * Optionally save, then close the wallet. Returns only when the wallet is closed: new calls fail
   * with "Wallet is closed" from the start, the listeners receive no more callbacks, the core is
   * asked to stop syncing, and close() waits, without a time limit, until no other thread is
   * inside a call of this wallet and the core's background sync thread (of {@code startSyncing()})
   * has ended. An interrupt does not end the wait; the thread's interrupt status is restored
   * afterwards. Closing a closed wallet has no effect; a second thread that closes the wallet
   * meanwhile waits for the first.
   *
   * <p>How long close() waits: a sync, scan or rescan that runs is stopped, and the core's sync
   * thread is asked to stop again and again until it has ended (a stop that comes before its
   * refresh begins is not lost); a refresh normally ends within its current batch of blocks. It
   * can take longer: a request to the node that is under way is waited for, up to the core's
   * timeout (3 min 30 s against a node that does not answer), and so is the one with which the
   * core saves the ring members after its refresh; a listener callback that blocks is waited for;
   * and, read in the core's code and not seen in a run, when the core's sync thread is just about
   * to wait for its next sync period as close() begins, close() can wait for the rest of that
   * period.</p>
   *
   * <p>With save, the wallet's cache file is written after the core's background sync thread has
   * ended and while no other call runs, so the save never meets a refresh. A wallet without a file
   * (created without a path, or opened with {@code openWalletData()}) is not written, and nothing
   * says so. While close(true) saves, the wallet's keys file is no longer locked: do not open the
   * same wallet elsewhere before close(true) has returned. When the save fails (a full disk, a
   * directory that cannot be written), the wallet is closed and freed all the same, and close()
   * throws ZephyrError("Wallet was closed without saving: " and the core's reason): a save never
   * writes the keys file, so what changed since the last save is lost and nothing else.</p>
   *
   * <p>Throws ZephyrError when called from inside one of the wallet's own calls or listener
   * callbacks, which close() would wait for (the wallet stays open). Should the core's own close
   * step fail, close() throws with the wallet closed for calls but not freed, and can be
   * repeated. Once the native wallet is freed, whatever close() threw, a later close() returns at
   * once.</p>
   *
   * @param save specifies if the wallet should be saved before being closed
   */
  @Override
  public void close(boolean save) {
    if (callLock.getReadHoldCount() > 0 || jniListener.inCallback()) {
      throw new ZephyrError("Cannot close the wallet from inside one of its own calls or listener callbacks");
    }
    synchronized (closeLock) {
      // closed already; a zero handle is a wallet the native close freed (it zeroes the handle on
      // this lock's thread), also when that close threw before isFreed was set
      if (isFreed || jniWalletHandle == 0) {
        markFreed();
        return;
      }
      isClosed = true;     // new calls fail, callbacks return at once
      boolean interrupted = false;
      try {
        stopSyncingQuietly(); // a running refresh ends at its next batch of blocks
        while (true) {
          try {
            if (callLock.writeLock().tryLock(100, TimeUnit.MILLISECONDS)) break;
          } catch (InterruptedException e) {
            interrupted = true; // remembered; the wait goes on
          }
          stopSyncingQuietly(); // again: a stop that comes before a refresh starts is lost
        }
        ZephyrError saveFailure = null;
        try {
          closeJni(save);
        } catch (Exception e) {
          if (jniWalletHandle != 0) throw toZephyrError(e); // the core's close failed: nothing freed, close() can be repeated
          saveFailure = new ZephyrError("Wallet was closed without saving: " + messageOf(e)); // freed all the same
        } finally {
          callLock.writeLock().unlock();
        }
        if (saveFailure != null) throw saveFailure;
      } finally {
        // whatever closeJni threw (an OutOfMemoryError from a failed save included): once it has
        // freed the wallet, the wallet is recorded as freed, so no later close() reaches a native call
        if (jniWalletHandle == 0) markFreed();
        if (interrupted) Thread.currentThread().interrupt();
      }
    }
  }

  /** Records that the native wallet is freed; called holding closeLock. */
  private void markFreed() {
    isClosed = true;
    isFreed = true;
    password = null;
    listeners.clear();
  }
  
  /**
   * Asks the core to stop syncing, from close() only: without the call lock, which close() is
   * waiting for, and safe because only close() frees the native wallet. A failure is ignored, as
   * close() goes on regardless.
   */
  private void stopSyncingQuietly() {
    try {
      stopSyncingJni();
    } catch (Exception e) {
      LOGGER.fine("stopSyncing while closing failed: " + e.getMessage());
    }
  }
  
  @Override
  public boolean isClosed() {
    return isClosed;
  }
  
  // ------------------------------ NATIVE METHODS ----------------------------
  
  private native static boolean walletExistsJni(String path);
  
  private native static long openWalletJni(String path, String password, int networkType);

  private native static long openWalletDataJni(String password, int networkType, byte[] keysData, byte[] cacheData);
  
  private native static long createWalletJni(String walletConfigJson);
  
  private native long getHeightJni();
  
  private native long getRestoreHeightJni();
  
  private native void setRestoreHeightJni(long height);
  
  private native long getDaemonHeightJni();
  
  private native long getDaemonMaxPeerHeightJni();
  
  private native long getHeightByDateJni(int year, int month, int day);
  
  private native boolean isViewOnlyJni();
  
  private native void setDaemonConnectionJni(String uri, String username, String password);
  
  private native void setProxyJni(String uri);
  
  private native String[] getDaemonConnectionJni(); // returns [uri, username, password]
  
  private native boolean isConnectedToDaemonJni();
  
  private native boolean isDaemonSyncedJni();
  
  private native boolean isSyncedJni();
  
  private native int getNetworkTypeJni();
  
  private native String getVersionJni();
  
  private native String getPathJni();
  
  private native String getMnemonicJni();
  
  private native String getMnemonicLanguageJni();
  
  private static native String[] getMnemonicLanguagesJni();
  
  private native String getPublicViewKeyJni();
  
  private native String getPrivateViewKeyJni();
  
  private native String getPublicSpendKeyJni();
  
  private native String getPrivateSpendKeyJni();
  
  private native String getAddressJni(int accountIdx, int subaddressIdx);
  
  private native String getAddressIndexJni(String address);
  
  private native String getIntegratedAddressJni(String standardAddress, String paymentId);
  
  private native String decodeIntegratedAddressJni(String integratedAddress);
  
  private native int setListenerJni(WalletJniListener listener, boolean delivering); // returns the native listener's calls of the core's listener set
  
  private native Object[] syncJni(long startHeight);
  
  private native void startSyncingJni(long syncPeriodInMs);
  
  private native void stopSyncingJni();
  
  private native void scanTxsJni(String[] txHashes);
  
  private native void rescanSpentJni();
  
  private native void rescanBlockchainJni();
  
  private native String getBalanceWalletJni();
  
  private native String getBalanceAccountJni(int accountIdx);
  
  private native String getBalanceAssetAccountJni(String assetType, int accountIdx);
  
  private native String getBalanceAssetSubaddressJni(String assetType, int accountIdx, int subaddressIdx);
  
  private native String getUnlockedBalanceWalletJni();
  
  private native String getUnlockedBalanceAccountJni(int accountIdx);
  
  private native String getUnlockedBalanceAssetAccountJni(String assetType, int accountIdx);
  
  private native String getUnlockedBalanceAssetSubaddressJni(String assetType, int accountIdx, int subaddressIdx);
  
  private native String getReserveInfoJni();
  
  private native String getCirculatingSupplyJni();
  
  private native String getAccountsJni(boolean includeSubaddresses, String tag);
  
  private native String getAccountJni(int accountIdx, boolean includeSubaddresses);
  
  private native String createAccountJni(String label);
  
  private native String getSubaddressesJni(int accountIdx, int[] subaddressIndices);
  
  private native String createSubaddressJni(int accountIdx, String label);

  private native void setSubaddressLabelJni(int accountIdx, int subaddressIdx, String label);
  
  private native String getTxsJni(String txQueryJson);
  
  private native String getTransfersJni(String transferQueryJson);
  
  private native String getOutputsJni(String outputQueryJson);
  
  private native String exportOutputsJni(boolean all);
  
  private native int importOutputsJni(String outputsHex);
  
  private native String exportKeyImagesJni(boolean all);
  
  private native String importKeyImagesJni(String keyImagesJson);
  
  private native String[] relayTxsJni(String[] txMetadatas);
  
  private native void freezeOutputJni(String KeyImage);
  
  private native void thawOutputJni(String keyImage);
  
  private native boolean isOutputFrozenJni(String keyImage);
  
  private native String createTxsJni(String txConfigJson);
  
  private native String sweepUnlockedJni(String txConfigJson);
  
  private native String sweepOutputJni(String txConfigJson);
  
  private native String sweepDustJni(boolean doNotRelay);
  
  private native String describeTxSetJni(String txSetJson);
  
  private native String signTxsJni(String unsignedTxHex);
  
  private native String[] submitTxsJni(String signedTxHex);
  
  private native String[] getTxNotesJni(String[] txHashes);
  
  private native void setTxNotesJni(String[] txHashes, String[] notes);
  
  private native String signMessageJni(String msg, int signatureType, int accountIdx, int subaddressIdx);
  
  private native String verifyMessageJni(String msg, String address, String signature);
  
  private native String getTxKeyJni(String txHash);
  
  private native String checkTxKeyJni(String txHash, String txKey, String address);
  
  private native String getTxProofJni(String txHash, String address, String message);
  
  private native String checkTxProofJni(String txHash, String address, String message, String signature);
  
  private native String getSpendProofJni(String txHash, String message);
  
  private native boolean checkSpendProofJni(String txHash, String message, String signature);
  
  private native String getReserveProofWalletJni(String message);
  
  private native String getReserveProofAccountJni(int accountIdx, String amount, String message);
  
  private native String checkReserveProofJni(String address, String message, String signature);
  
  private native String getAddressBookEntriesJni(int[] indices);
  
  private native int addAddressBookEntryJni(String address, String description);
  
  private native void editAddressBookEntryJni(int index, boolean setAddress, String address, boolean setDescription, String description);
  
  private native void deleteAddressBookEntryJni(int entryIdx);
  
  private native String getPaymentUriJni(String sendRequestJson);
  
  private native String parsePaymentUriJni(String uri);
  
  private native String getAttributeJni(String key);
  
  private native void setAttributeJni(String key, String val);

  private native void startMiningJni(long numThreads, boolean backgroundMining, boolean ignoreBattery);
  
  private native void stopMiningJni();
  
  private native boolean isMultisigImportNeededJni();
  
  private native String getMultisigInfoJni();
  
  private native String prepareMultisigJni();
  
  private native String makeMultisigJni(String[] multisigHexes, int threshold, String password);
  
  private native String exchangeMultisigKeysJni(String[] multisigHexes, String password);
  
  private native String exportMultisigHexJni();
  
  private native int importMultisigHexJni(String[] multisigHexes);
  
  private native String signMultisigTxHexJni(String multisigTxHex);
  
  private native String[] submitMultisigTxHexJni(String signedMultisigTxHex);

  private native byte[] getKeysFileBufferJni(String password, boolean viewOnly);

  private native byte[] getCacheFileBufferJni();
  
  private native void changePasswordJni(String oldPassword, String newPassword);
  
  private native void moveToJni(String path, String password);
  
  private native void saveJni();
  
  private native void closeJni(boolean save);
  
  // -------------------------------- LISTENER --------------------------------
  
  /**
   * Receives notifications directly from jni c++, on the thread that runs the core's sync (the
   * caller's thread for {@code sync()}, the core's own thread for {@code startSyncing()}) or the
   * thread of a call that relays. The bridge holds no lock while it calls in here, so callbacks of
   * different threads can overlap: the state used here is thread-safe (a copy-on-write listener
   * set, a thread-local flag, a volatile closed flag).
   *
   * <p>Nothing escapes to the native caller: a listener's Throwable is caught and logged at
   * WARNING (with the listener's class and the callback's name, not the callback's data; at FINE
   * for the "Wallet is closed" of a listener that calls the wallet while it closes), and the other
   * listeners are still called; callback data that cannot be read is logged and dropped the same
   * way. Once the wallet is closed (or closing), every callback returns at once.</p>
   */
  @SuppressWarnings("unused") // called directly from jni c++
  private class WalletJniListener {
    
    /** Set while this thread delivers a callback of this wallet: close() from there is refused. */
    private final ThreadLocal<Boolean> inCallback = new ThreadLocal<Boolean>();
    
    boolean inCallback() {
      return Boolean.TRUE.equals(inCallback.get());
    }
    
    /** Runs one notification: reads its data and announces it; nothing escapes. */
    private void deliver(String callback, Runnable notification) {
      if (isClosed) return; // during or after close(): the notification is dropped
      boolean outermost = !inCallback();
      inCallback.set(Boolean.TRUE);
      try {
        notification.run();
      } catch (Throwable t) {
        // building the callback's objects failed (data from the core that does not parse); the
        // exception's message would carry that data, so only its class is logged
        LOGGER.log(Level.WARNING, "Could not read the data of " + callback + " (" + t.getClass().getName() + "); the notification is dropped");
      } finally {
        if (outermost) inCallback.remove();
      }
    }
    
    /** Calls every listener registered now, each in its own protection. */
    private void announce(String callback, Consumer<ZephyrWalletListenerI> call) {
      for (ZephyrWalletListenerI listener : listeners) { // a snapshot of the copy-on-write set
        if (isClosed) return;
        try {
          call.accept(listener);
        } catch (Throwable t) {
          // a listener that calls the wallet while close() runs gets "Wallet is closed": expected then
          Level level = isClosed && t instanceof ZephyrError ? Level.FINE : Level.WARNING;
          LOGGER.log(level, "Wallet listener " + listener.getClass().getName() + " threw from " + callback + "; ignored", t);
        }
      }
    }
    
    public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
      deliver("onSyncProgress", () -> announce("onSyncProgress", listener -> listener.onSyncProgress(height, startHeight, endHeight, percentDone, message)));
    }
    
    public void onNewBlock(long height) {
      deliver("onNewBlock", () -> announce("onNewBlock", listener -> listener.onNewBlock(height)));
    }
    
    public void onBalancesChanged(String newBalanceStr, String newUnlockedBalanceStr, String assetType) {
      deliver("onBalancesChanged", () -> {
        BigInteger newBalance = new BigInteger(newBalanceStr);
        BigInteger newUnlockedBalance = new BigInteger(newUnlockedBalanceStr);
        announce("onBalancesChanged", listener -> listener.onBalancesChanged(newBalance, newUnlockedBalance, assetType));
      });
    }
    
    public void onOutputReceived(long height, String txHash, String amountStr, String assetType, int accountIdx, int subaddressIdx, int version, long unlockHeight, boolean isLocked) {
      deliver("onOutputReceived", () -> {
        
        // build output to announce
        ZephyrOutputWallet output = new ZephyrOutputWallet();
        output.setAmount(new BigInteger(amountStr));
        output.setAssetType(assetType);
        output.setAccountIndex(accountIdx);
        output.setSubaddressIndex(subaddressIdx);
        ZephyrTxWallet tx = new ZephyrTxWallet();
        tx.setHash(txHash);
        tx.setVersion(version);
        tx.setUnlockHeight(unlockHeight);
        output.setTx(tx);
        tx.setOutputs(Arrays.asList(output));
        tx.setIsIncoming(true);
        tx.setIsLocked(isLocked);
        if (height > 0) {
          ZephyrBlock block = new ZephyrBlock().setHeight(height);
          block.setTxs(Arrays.asList(tx));
          tx.setBlock(block);
          tx.setIsConfirmed(true);
          tx.setInTxPool(false);
          tx.setIsFailed(false);
        } else {
          tx.setIsConfirmed(false);
          tx.setInTxPool(true);
        }
        
        // announce output
        ZephyrOutputWallet announced = (ZephyrOutputWallet) tx.getOutputs().get(0);
        announce("onOutputReceived", listener -> listener.onOutputReceived(announced));
      });
    }
    
    public void onOutputSpent(long height, String txHash, String amountStr, String assetType, String accountIdxStr, String subaddressIdxStr, int version, long unlockHeight, boolean isLocked) {
      deliver("onOutputSpent", () -> {
        
        // build spent output
        ZephyrOutputWallet output = new ZephyrOutputWallet();
        output.setAmount(new BigInteger(amountStr));
        output.setAssetType(assetType);
        if (accountIdxStr.length() > 0) output.setAccountIndex(Integer.parseInt(accountIdxStr));
        if (subaddressIdxStr.length() > 0) output.setSubaddressIndex(Integer.parseInt(subaddressIdxStr));
        ZephyrTxWallet tx = new ZephyrTxWallet();
        tx.setHash(txHash);
        tx.setVersion(version);
        tx.setUnlockHeight(unlockHeight);
        tx.setIsLocked(isLocked);
        output.setTx(tx);
        tx.setInputs(Arrays.asList(output));
        tx.setIsIncoming(false);
        if (height > 0) {
          ZephyrBlock block = new ZephyrBlock().setHeight(height);
          block.setTxs(Arrays.asList(tx));
          tx.setBlock(block);
          tx.setIsConfirmed(true);
          tx.setInTxPool(false);
          tx.setIsFailed(false);
        } else {
          tx.setIsConfirmed(false);
          tx.setInTxPool(true);
        }
        
        // announce output
        ZephyrOutputWallet announced = (ZephyrOutputWallet) tx.getInputs().get(0);
        announce("onOutputSpent", listener -> listener.onOutputSpent(announced));
      });
    }
  }
  
  // ------------------------ RESPONSE DESERIALIZATION ------------------------
  
  /**
   * Override ZephyrBlock with wallet types for polymorphic deserialization.
   */
  private static class ZephyrBlockWallet extends ZephyrBlock {
    
    // default constructor necessary for serialization
    @SuppressWarnings("unused")
    public ZephyrBlockWallet() {
      super();
    }
    
    @JsonProperty("txs")
    public ZephyrBlockWallet setTxWallets(List<ZephyrTxWallet> txs) {
      super.setTxs(new ArrayList<ZephyrTx>(txs));
      return this;
    }
    
    /**
     * Initializes a new ZephyrBlock with direct references to this block.
     * 
     * TODO: more efficient way to deserialize directly into ZephyrBlock?
     * 
     * @return ZephyrBlock is the newly initialized block with direct references to this block
     */
    public ZephyrBlock toBlock() {
      ZephyrBlock block = new ZephyrBlock();
      block.setHash(getHash());
      block.setHeight(getHeight());
      block.setTimestamp(getTimestamp());
      block.setSize(getSize());
      block.setWeight(getWeight());
      block.setLongTermWeight(getLongTermWeight());
      block.setDepth(getDepth());
      block.setDifficulty(getDifficulty());
      block.setCumulativeDifficulty(getCumulativeDifficulty());
      block.setMajorVersion(getMajorVersion());
      block.setMinorVersion(getMinorVersion());
      block.setNonce(getNonce());
      block.setMinerTxHash(getMinerTxHash());
      block.setNumTxs(getNumTxs());
      block.setOrphanStatus(getOrphanStatus());
      block.setPrevHash(getPrevHash());
      block.setReward(getReward());
      block.setPowHash(getPowHash());
      block.setHex(getHex());
      block.setMinerTx(getMinerTx());
      block.setTxs(getTxs());
      block.setTxHashes(getTxHashes());
      for (ZephyrTx tx : getTxs()) tx.setBlock(block);  // re-assign tx block references
      return block;
    }
  }
  
  private static class AccountsContainer {
    public List<ZephyrAccount> accounts;
  };
  
  private static class SubaddressesContainer {
    public List<ZephyrSubaddress> subaddresses;
  };
  
  private static class BlocksWalletContainer {
    public List<ZephyrBlockWallet> blocks;
    public List<String> missingTxHashes;
  }
  
  private static class DeserializedBlocksContainer {
    public List<ZephyrBlock> blocks;
    public List<String> missingTxHashes;
  }
  
  private static class TxSetsContainer {
    public List<ZephyrTxSet> txSets;
  }
  
  private static class KeyImagesContainer {
    public List<ZephyrKeyImage> keyImages;
    @SuppressWarnings("unused") public KeyImagesContainer() { } // necessary for serialization
    public KeyImagesContainer(List<ZephyrKeyImage> keyImages) { this.keyImages = keyImages; };
  }
  
  private static DeserializedBlocksContainer deserializeBlocks(String blocksJson) {
    DeserializedBlocksContainer deserializedBlocksContainer = new DeserializedBlocksContainer();
    deserializedBlocksContainer.blocks = new ArrayList<ZephyrBlock>();
    deserializedBlocksContainer.missingTxHashes = new ArrayList<String>();
    BlocksWalletContainer blocksWalletContainer = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, blocksJson, BlocksWalletContainer.class);
    if (blocksWalletContainer.blocks != null) for (ZephyrBlockWallet blockWallet : blocksWalletContainer.blocks) deserializedBlocksContainer.blocks.add(blockWallet.toBlock());
    if (blocksWalletContainer.missingTxHashes != null) for (String missingTxHash : blocksWalletContainer.missingTxHashes) deserializedBlocksContainer.missingTxHashes.add(missingTxHash);
    return deserializedBlocksContainer;
  }
  
  private static List<ZephyrTxWallet> deserializeTxs(ZephyrTxQuery query, String blocksJson, Collection<String> missingTxHashes) {
    
    // deserialize blocks
    DeserializedBlocksContainer deserializedBlocks = deserializeBlocks(blocksJson);
    if (missingTxHashes == null && !deserializedBlocks.missingTxHashes.isEmpty()) throw new ZephyrError("Wallet missing requested tx hashes: " + deserializedBlocks.missingTxHashes);
    for (String missingTxHash : deserializedBlocks.missingTxHashes) missingTxHashes.add(missingTxHash);
    List<ZephyrBlock> blocks = deserializedBlocks.blocks;
    
    // collect txs
    List<ZephyrTxWallet> txs = new ArrayList<ZephyrTxWallet>();
    for (ZephyrBlock block : blocks) {
      sanitizeBlock(block);
      for (ZephyrTx tx : block.getTxs()) {
        if (block.getHeight() == null) tx.setBlock(null); // dereference placeholder block for unconfirmed txs
        txs.add((ZephyrTxWallet) tx);
      }
    }
    
    // re-sort txs which is lost over jni serialization
    if (query.getHashes() != null) {
      Map<String, ZephyrTxWallet> txMap = new HashMap<String, ZephyrTxWallet>();
      for (ZephyrTxWallet tx : txs) txMap.put(tx.getHash(), tx);
      List<ZephyrTxWallet> txsSorted = new ArrayList<ZephyrTxWallet>();
      for (String txHash : query.getHashes()) if (txMap.containsKey(txHash)) txsSorted.add(txMap.get(txHash));
      txs = txsSorted;
    }
    return txs;
  }
  
  private static List<ZephyrTransfer> deserializeTransfers(ZephyrTransferQuery query, String blocksJson) {
    
    // deserialize blocks
    DeserializedBlocksContainer deserializedBlocks = deserializeBlocks(blocksJson);
    if (!deserializedBlocks.missingTxHashes.isEmpty()) throw new ZephyrError("Wallet missing requested tx hashes: " + deserializedBlocks.missingTxHashes);
    List<ZephyrBlock> blocks = deserializedBlocks.blocks;
    
    // collect transfers
    List<ZephyrTransfer> transfers = new ArrayList<ZephyrTransfer>();
    for (ZephyrBlock block : blocks) {
      sanitizeBlock(block);
      for (ZephyrTx tx : block.getTxs()) {
        if (block.getHeight() == null) tx.setBlock(null); // dereference placeholder block for unconfirmed txs
        ZephyrTxWallet txWallet = (ZephyrTxWallet) tx;
        if (txWallet.getOutgoingTransfer() != null) transfers.add(txWallet.getOutgoingTransfer());
        if (txWallet.getIncomingTransfers() != null) {
          for (ZephyrIncomingTransfer transfer : txWallet.getIncomingTransfers()) transfers.add(transfer);
        }
      }
    }
    return transfers;
  }
  
  private static List<ZephyrOutputWallet> deserializeOutputs(ZephyrOutputQuery query, String blocksJson) {
    
    // deserialize blocks
    DeserializedBlocksContainer deserializedBlocks = deserializeBlocks(blocksJson);
    if (!deserializedBlocks.missingTxHashes.isEmpty()) throw new ZephyrError("Wallet missing requested tx hashes: " + deserializedBlocks.missingTxHashes);
    List<ZephyrBlock> blocks = deserializedBlocks.blocks;
    
    // collect outputs
    List<ZephyrOutputWallet> outputs = new ArrayList<ZephyrOutputWallet>();
    for (ZephyrBlock block : blocks) {
      sanitizeBlock(block);
      for (ZephyrTx tx : block.getTxs()) {
        outputs.addAll(((ZephyrTxWallet) tx).getOutputsWallet());
      }
    }
    return outputs;
  }
  
  private static class AddressBookEntriesContainer {
    public List<ZephyrAddressBookEntry> entries;
  }
  
  // ---------------------------- PRIVATE HELPERS -----------------------------
  
  /**
   * Switches the native listener's delivery to match the Java listener set: callbacks reach Java
   * while there is a Java listener; without one the native listener returns from every callback at
   * once, before it attaches the core's thread or calls into Java, so an empty set costs no upcall.
   * The first switch on creates the native listener and adds it to the core's listeners; it stays
   * there until close(), so the core's listener set, which its sync thread copies without a lock,
   * changes once per wallet at most (not on every change from no listener to one, and never by a
   * removal; a failed first addition is not undone, and is tried again by the next switch on, an
   * insertion that finds the listener in the set already when the failure came after it). Called
   * holding the listening lock, inside the call lock. A callback's thread that waits for the lock
   * waits only for a switch: callbacks reach Java only once the native listener was added and
   * switched on, so the one call into the core (that first addition) is over by then.
   */
  private void updateDelivery() {
    boolean wanted = !listeners.isEmpty();
    if (wanted == listenerDelivery) return;
    coreListenerCalls = setListenerJni(jniListener, wanted);
    listenerDelivery = wanted;
  }

  /** Removes a listener that sync(listener) added, inside the call lock; throws nothing. */
  private void removeListenerQuietly(ZephyrWalletListenerI listener) {
    synchronized (listening) {
      listeners.remove(listener);
      try {
        updateDelivery();
      } catch (RuntimeException e) {
        LOGGER.fine("Switching the native listener's delivery failed: " + e.getMessage());
      }
    }
  }

  /**
   * Refuses, on a thread that is delivering a listener callback, a call that cannot run there.
   * Called before anything else of the call. The rule, read from the core's functions: a call is
   * refused when its core function locks the core's sync mutex (sync, scan_txs, rescan_blockchain
   * through lock_and_sync, make_multisig, exchange_multisig_keys), which the refresh that delivers
   * the callback holds; when it notifies the listeners inline, from inside the running notification
   * (create_txs, sweep_unlocked, sweep_output and sweep_dust with relay, relay_txs and submit_txs
   * through on_spend_txs, import_key_images through wallet2's on_money_spent); when it relays
   * (submit_multisig_tx_hex through wallet2's commit_tx, which also marks outputs spent); or when
   * it changes the wallet's outputs between two steps of the refresh that delivers the callback
   * (rescan_spent, import_outputs, import_key_images, import_multisig_hex, which also refreshes).
   * startSyncing() and close() are refused for their own reasons (a sync thread started from inside
   * a sync; a close() that would wait for its own callback). Not refused: sign_multisig_tx_hex,
   * change_password and set_daemon_connection (none locks the sync mutex, notifies or changes the
   * outputs), and reads, although a read that refreshes the pool (get_txs and get_transfers with
   * pool transactions, through update_pool_state and process_pool_state) can notify inline. The
   * list is in the Javadoc of addListener().
   */
  private void refuseInCallback(String call) {
    if (jniListener.inCallback()) throw new ZephyrError("Cannot call " + call + " from inside a listener callback; call it after the callback has returned");
  }
  
  /**
   * Logs at FINE how many transactions a call built and their hashes: never the config, the
   * transactions' JSON or their keys.
   */
  private static void logCreated(String call, List<ZephyrTxWallet> txs) {
    if (!LOGGER.isLoggable(Level.FINE)) return;
    List<String> hashes = new ArrayList<String>();
    if (txs != null) for (ZephyrTxWallet tx : txs) hashes.add(tx.getHash());
    LOGGER.fine(call + " built " + hashes.size() + " transaction(s): " + hashes);
  }
  
  private void assertNotClosed() {
    if (isClosed) throw new ZephyrError("Wallet is closed");
  }
  
  /**
   * Starts a call that uses the native wallet: close() cannot free it before endCall(). Reads do
   * not block each other, and a thread that is inside a call already (a listener called from a
   * foreground sync) enters again. The read side is only tried, never waited for: it is
   * unavailable only while close() holds the write side, and the wallet is closed by then.
   */
  private void beginCall() {
    assertNotClosed();
    if (!callLock.readLock().tryLock()) throw new ZephyrError("Wallet is closed");
    if (isClosed) {
      callLock.readLock().unlock();
      throw new ZephyrError("Wallet is closed");
    }
  }
  
  private void endCall() {
    callLock.readLock().unlock();
  }
  
  private static ZephyrAccount sanitizeAccount(ZephyrAccount account) {
    if (account.getSubaddresses() != null) {
      for (ZephyrSubaddress subaddress : account.getSubaddresses()) sanitizeSubaddress(subaddress);
    }
    return account;
  }
  
  private static ZephyrSubaddress sanitizeSubaddress(ZephyrSubaddress subaddress) {
    if ("".equals(subaddress.getLabel())) subaddress.setLabel(null);
    return subaddress;
  }
  
  private static ZephyrBlock sanitizeBlock(ZephyrBlock block) {
    for (ZephyrTx tx : block.getTxs()) sanitizeTxWallet((ZephyrTxWallet) tx);
    return block;
  }
  
  private static ZephyrTxWallet sanitizeTxWallet(ZephyrTxWallet tx) {
    return tx;
  }
}
