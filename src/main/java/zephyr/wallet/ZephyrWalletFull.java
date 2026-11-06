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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import zephyr.common.ZephyrError;
import zephyr.common.ZephyrRpcConnection;
import zephyr.common.ZephyrUtils;
import zephyr.daemon.model.ZephyrBlock;
import zephyr.daemon.model.ZephyrKeyImage;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.daemon.model.ZephyrTx;
import zephyr.daemon.model.ZephyrVersion;
import zephyr.wallet.model.ZephyrAccount;
import zephyr.wallet.model.ZephyrAccountTag;
import zephyr.wallet.model.ZephyrAddressBookEntry;
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
  private long jniWalletHandle;                 // memory address of the wallet in c++; this variable is read directly by name in c++
  private long jniListenerHandle;               // memory address of the wallet listener in c++; this variable is read directly by name in c++
  private WalletJniListener jniListener;        // receives notifications from jni c++
  private String password;
  private boolean isClosed;                     // whether or not wallet is closed
  
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
    return walletExistsJni(path);
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
    if (!walletExistsJni(path)) throw new ZephyrError("Wallet does not exist at path: " + path);
    if (networkType == null) throw new ZephyrError("Must provide a network type");
    long jniWalletHandle = openWalletJni(path, password, networkType.ordinal());
    ZephyrWalletFull wallet = new ZephyrWalletFull(jniWalletHandle, password);
    if (daemonConnection != null) wallet.setDaemonConnection(daemonConnection);
    return wallet;
  }
  public static ZephyrWalletFull openWallet(String path, String password, ZephyrNetworkType networkType) { return openWallet(path, password, networkType, (ZephyrRpcConnection) null); }
  public static ZephyrWalletFull openWallet(String path, String password, ZephyrNetworkType networkType, String daemonUri) { return openWallet(path, password, networkType, daemonUri == null ? null : new ZephyrRpcConnection(daemonUri)); }

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
    if (networkType == null) throw new ZephyrError("Must provide a network type");
    long jniWalletHandle = openWalletDataJni(password, networkType.ordinal(), keysData, cacheData);
    ZephyrWalletFull wallet = new ZephyrWalletFull(jniWalletHandle, password);
    if (daemonConnection != null) wallet.setDaemonConnection(daemonConnection);
    return wallet;
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
    
    // validate config
    if (config == null) throw new ZephyrError("Must specify config to open wallet");
    if (config.getNetworkType() == null) throw new ZephyrError("Must specify a network type: 'mainnet', 'testnet' or 'stagenet'");
    if (config.getPath() != null && !config.getPath().isEmpty() && ZephyrWalletFull.walletExists(config.getPath())) throw new ZephyrError("Wallet already exists: " + config.getPath());
    if (config.getMnemonic() != null && (config.getPrimaryAddress() != null || config.getPrivateViewKey() != null || config.getPrivateSpendKey() != null)) {
      throw new ZephyrError("Wallet may be initialized with a mnemonic or keys but not both");
    }
    if (Boolean.TRUE.equals(config.getSaveCurrent() != null)) throw new ZephyrError("Cannot save current wallet when creating full wallet");
    
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
  }
  
  private static ZephyrWalletFull createWalletFromMnemonic(ZephyrWalletConfig config) {
    if (config.getRestoreHeight() == null) config.setRestoreHeight(0l);
    long jniWalletHandle = createWalletJni(serializeWalletConfig(config));
    ZephyrWalletFull wallet = new ZephyrWalletFull(jniWalletHandle, config.getPassword());
    wallet.setDaemonConnection(config.getServer());
    return wallet;
  }
  
  private static ZephyrWalletFull createWalletFromKeys(ZephyrWalletConfig config) {
    if (config.getRestoreHeight() == null) config.setRestoreHeight(0l);
    if (config.getLanguage() == null) config.setLanguage(DEFAULT_LANGUAGE);
    try {
      long jniWalletHandle = createWalletJni(serializeWalletConfig(config));
      ZephyrWalletFull wallet = new ZephyrWalletFull(jniWalletHandle, config.getPassword());
      wallet.setDaemonConnection(config.getServer());
      return wallet;
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  private static ZephyrWalletFull createWalletRandom(ZephyrWalletConfig config) {
    if (config.getLanguage() == null) config.setLanguage(DEFAULT_LANGUAGE);
    long jniWalletHandle = createWalletJni(serializeWalletConfig(config));
    return new ZephyrWalletFull(jniWalletHandle, config.getPassword());
  }
  
  private static String serializeWalletConfig(ZephyrWalletConfig config) {
    Map<String, Object> configMap = JsonUtils.toMap(config);
    configMap.put("networkType", config.getNetworkType().ordinal());
    return JsonUtils.serialize(configMap);
  }
  
  /**
   * Get a list of available languages for the wallet's mnemonic phrase.
   * 
   * @return the available languages for the wallet's mnemonic phrase
   */
  public static List<String> getMnemonicLanguages() {
    return Arrays.asList(getMnemonicLanguagesJni());
  }
  
  // ------------ WALLET METHODS SPECIFIC TO JNI IMPLEMENTATION ---------------
  
  /**
   * Get the maximum height of the peers the wallet's daemon is connected to.
   *
   * @return the maximum height of the peers the wallet's daemon is connected to
   */
  public long getDaemonMaxPeerHeight() {
    assertNotClosed();
    try {
      return getDaemonMaxPeerHeightJni();
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  /**
   * Indicates if the wallet's daemon is synced with the network.
   * 
   * @return true if the daemon is synced with the network, false otherwise
   */
  public boolean isDaemonSynced() {
    assertNotClosed();
    try {
      return isDaemonSyncedJni();
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  /**
   * Indicates if the wallet is synced with the daemon.
   * 
   * @return true if the wallet is synced with the daemon, false otherwise
   */
  public boolean isSynced() {
    assertNotClosed();
    try {
      return isSyncedJni();
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  /**
   * Get the wallet's network type (mainnet, testnet, or stagenet).
   * 
   * @return the wallet's network type
   */
  public ZephyrNetworkType getNetworkType() {
    assertNotClosed();
    return ZephyrNetworkType.values()[getNetworkTypeJni()];
  }
  
  /**
   * Get the height of the first block that the wallet scans.
   * 
   * @return the height of the first block that the wallet scans
   */
  public long getRestoreHeight() {
    assertNotClosed();
    return getRestoreHeightJni();
  }
  
  /**
   * Set the height of the first block that the wallet scans.
   * 
   * @param syncHeight is the height of the first block that the wallet scans
   */
  public void setRestoreHeight(long syncHeight) {
    assertNotClosed();
    setRestoreHeightJni(syncHeight);
  }

  /**
   * Move the wallet from its current path to the given path.
   * 
   * @param path is the new wallet's path
   */
  public void moveTo(String path) {
    assertNotClosed();
    moveToJni(path, password);
  }
  
  // -------------------------- COMMON WALLET METHODS -------------------------
  
  @Override
  public void addListener(ZephyrWalletListenerI listener) {
    assertNotClosed();
    super.addListener(listener);
    refreshListening();
  }
  
  @Override
  public void removeListener(ZephyrWalletListenerI listener) {
    assertNotClosed();
    super.removeListener(listener);
    refreshListening();
  }
  
  @Override
  public Set<ZephyrWalletListenerI> getListeners() {
    assertNotClosed();
    return super.getListeners();
  }
  
  @Override
  public boolean isViewOnly() {
    assertNotClosed();
    return isViewOnlyJni();
  }
  
  @Override
  public void setDaemonConnection(ZephyrRpcConnection daemonConnection) {
    assertNotClosed();
    if (daemonConnection == null) setDaemonConnectionJni("", "", "");
    else {
      try {
        setDaemonConnectionJni(daemonConnection.getUri() == null ? "" : daemonConnection.getUri().toString(), daemonConnection.getUsername(), daemonConnection.getPassword());
      } catch (Exception e) {
        throw new ZephyrError(e.getMessage());
      }
    }
  }
  
  @Override
  public void setProxyUri(String uri) {
    assertNotClosed();
    if (uri == null) uri = "";
    try {
      setProxyJni(uri);
    } catch (Exception e) {
      e.printStackTrace();
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public ZephyrRpcConnection getDaemonConnection() {
    assertNotClosed();
    try {
      String[] vals = getDaemonConnectionJni();
      return vals == null ? null : new ZephyrRpcConnection(vals[0], vals[1], vals[2]);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public boolean isConnectedToDaemon() {
    assertNotClosed();
    try {
      return isConnectedToDaemonJni();
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public ZephyrVersion getVersion() {
    assertNotClosed();
    try {
      String versionJson = getVersionJni();
      return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, versionJson, ZephyrVersion.class);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public String getPath() {
    assertNotClosed();
    String path = getPathJni();
    return path.isEmpty() ? null : path;
  }

  @Override
  public String getMnemonic() {
    assertNotClosed();
    String mnemonic = getMnemonicJni();
    if ("".equals(mnemonic)) return null;
    return mnemonic;
  }
  
  @Override
  public String getMnemonicLanguage() {
    assertNotClosed();
    String mnemonicLanguage = getMnemonicLanguageJni();
    if ("".equals(mnemonicLanguage)) return null;
    return mnemonicLanguage;
  }

  @Override
  public String getPrivateViewKey() {
    assertNotClosed();
    return getPrivateViewKeyJni();
  }
  
  @Override
  public String getPrivateSpendKey() {
    assertNotClosed();
    String privateSpendKey = getPrivateSpendKeyJni();
    if ("".equals(privateSpendKey)) return null;
    return privateSpendKey;
  }
  
  @Override
  public String getPublicViewKey() {
    assertNotClosed();
    return getPublicViewKeyJni();
  }
  
  @Override
  public String getPublicSpendKey() {
    assertNotClosed();
    return getPublicSpendKeyJni();
  }

  @Override
  public ZephyrIntegratedAddress getIntegratedAddress(String standardAddress, String paymentId) {
    assertNotClosed();
    try {
      String integratedAddressJson = getIntegratedAddressJni(standardAddress, paymentId);
      return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, integratedAddressJson, ZephyrIntegratedAddress.class);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public ZephyrIntegratedAddress decodeIntegratedAddress(String integratedAddress) {
    assertNotClosed();
    try {
      String integratedAddressJson = decodeIntegratedAddressJni(integratedAddress);
      return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, integratedAddressJson, ZephyrIntegratedAddress.class);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public long getHeight() {
    assertNotClosed();
    return getHeightJni();
  }

  @Override
  public long getDaemonHeight() {
    assertNotClosed();
    try {
      return getDaemonHeightJni();
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public long getHeightByDate(int year, int month, int day) {
    assertNotClosed();
    try {
      return getHeightByDateJni(year, month ,day);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public ZephyrSyncResult sync(Long startHeight, ZephyrWalletListenerI listener) {
    assertNotClosed();
    if (startHeight == null) startHeight = Math.max(getHeight(), getRestoreHeight());
    
    // register listener if given
    if (listener != null) addListener(listener);
    
    // sync wallet and handle exception
    try {
      Object[] results = syncJni(startHeight);
      return new ZephyrSyncResult((long) results[0], (boolean) results[1]);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    } finally {
      if (listener != null) removeListener(listener); // unregister listener
    }
  }
  
  @Override
  public void startSyncing(Long syncPeriodInMs) {
    assertNotClosed();
    try {
      startSyncingJni(syncPeriodInMs == null ? DEFAULT_SYNC_PERIOD_IN_MS : syncPeriodInMs);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public void stopSyncing() {
    assertNotClosed();
    try {
      stopSyncingJni();
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public void scanTxs(Collection<String> txHashes) {
    assertNotClosed();
    String[] txMetadatasArr = txHashes.toArray(new String[txHashes.size()]); // convert to array for jni
    try {
      scanTxsJni(txMetadatasArr);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public void rescanSpent() {
    assertNotClosed();
    try {
      rescanSpentJni();
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public void rescanBlockchain() {
    assertNotClosed();
    try {
      rescanBlockchainJni();
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public List<ZephyrAccount> getAccounts(boolean includeSubaddresses, String tag) {
    assertNotClosed();
    String accountsJson = getAccountsJni(includeSubaddresses, tag);
    List<ZephyrAccount> accounts = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, accountsJson, AccountsContainer.class).accounts;
    for (ZephyrAccount account : accounts) sanitizeAccount(account);
    return accounts;
  }
  
  @Override
  public ZephyrAccount getAccount(int accountIdx, boolean includeSubaddresses) {
    assertNotClosed();
    String accountJson = getAccountJni(accountIdx, includeSubaddresses);
    ZephyrAccount account = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, accountJson, ZephyrAccount.class);
    sanitizeAccount(account);
    return account;
  }

  @Override
  public ZephyrAccount createAccount(String label) {
    assertNotClosed();
    String accountJson = createAccountJni(label);
    ZephyrAccount account = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, accountJson, ZephyrAccount.class);
    sanitizeAccount(account);
    return account;
  }

  @Override
  public List<ZephyrSubaddress> getSubaddresses(int accountIdx, List<Integer> subaddressIndices) {
    assertNotClosed();
    String subaddresses_json = getSubaddressesJni(accountIdx, GenUtils.listToIntArray(subaddressIndices));
    List<ZephyrSubaddress> subaddresses = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, subaddresses_json, SubaddressesContainer.class).subaddresses;
    for (ZephyrSubaddress subaddress : subaddresses) sanitizeSubaddress(subaddress);
    return subaddresses;
  }

  @Override
  public ZephyrSubaddress createSubaddress(int accountIdx, String label) {
    assertNotClosed();
    String subaddressJson = createSubaddressJni(accountIdx, label);
    ZephyrSubaddress subaddress = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, subaddressJson, ZephyrSubaddress.class);
    sanitizeSubaddress(subaddress);
    return subaddress;
  }

  @Override
  public void setSubaddressLabel(int accountIdx, int subaddressIdx, String label) {
    assertNotClosed();
    if (label == null) label = "";
    setSubaddressLabelJni(accountIdx, subaddressIdx, label);
  }
  
  @Override
  public String getAddress(int accountIdx, int subaddressIdx) {
    assertNotClosed();
    return getAddressJni(accountIdx, subaddressIdx);
  }

  @Override
  public ZephyrSubaddress getAddressIndex(String address) {
    assertNotClosed();
    try {
      String subaddressJson = getAddressIndexJni(address);
      ZephyrSubaddress subaddress = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, subaddressJson, ZephyrSubaddress.class);
      return sanitizeSubaddress(subaddress);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public BigInteger getBalance(Integer accountIdx, Integer subaddressIdx) {
    assertNotClosed();
    try {
      if (accountIdx == null) {
        if (subaddressIdx != null) throw new ZephyrError("Must provide account index with subaddress index");
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, getBalanceWalletJni(), new TypeReference<Map<String, BigInteger>>(){}).getOrDefault("ZPH", BigInteger.ZERO); // temporary until R5
      } else {
        if (subaddressIdx == null) return new BigInteger(getBalanceAssetAccountJni("ZPH", accountIdx)); // temporary until R5
        else return new BigInteger(getBalanceAssetSubaddressJni("ZPH", accountIdx, subaddressIdx)); // temporary until R5
      }
    } catch (ZephyrError e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public BigInteger getUnlockedBalance(Integer accountIdx, Integer subaddressIdx) {
    assertNotClosed();
    try {
      if (accountIdx == null) {
        if (subaddressIdx != null) throw new ZephyrError("Must provide account index with subaddress index");
        return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, getUnlockedBalanceWalletJni(), new TypeReference<Map<String, BigInteger>>(){}).getOrDefault("ZPH", BigInteger.ZERO); // temporary until R5
      } else {
        if (subaddressIdx == null) return new BigInteger(getUnlockedBalanceAssetAccountJni("ZPH", accountIdx)); // temporary until R5
        else return new BigInteger(getUnlockedBalanceAssetSubaddressJni("ZPH", accountIdx, subaddressIdx)); // temporary until R5
      }
    } catch (ZephyrError e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public List<ZephyrTxWallet> getTxs(ZephyrTxQuery query, Collection<String> missingTxHashes) {
    assertNotClosed();
    
    // copy and normalize tx query up to block
    query = query == null ? new ZephyrTxQuery() : query.copy();
    if (query.getBlock() == null) query.setBlock(new ZephyrBlock().setTxs(query));
    
    // serialize query from block and fetch txs from jni
    String blocksJson;
    try {
      blocksJson = getTxsJni(JsonUtils.serialize(query.getBlock()));
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
    
    // deserialize and return txs
    return deserializeTxs(query, blocksJson, missingTxHashes);
  }

  @Override
  public List<ZephyrTransfer> getTransfers(ZephyrTransferQuery query) {
    assertNotClosed();
    
    // copy and normalize query up to block
    query = normalizeTransferQuery(query);
    
    // serialize query from block and fetch transfers from jni
    String blocksJson;
    try {
      blocksJson = getTransfersJni(JsonUtils.serialize(query.getTxQuery().getBlock()));
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
    
    // deserialize and return transfers
    return deserializeTransfers(query, blocksJson);
  }
  
  @Override
  public List<ZephyrOutputWallet> getOutputs(ZephyrOutputQuery query) {
    assertNotClosed();
    
    // copy and normalize query up to block
    if (query == null) query = new ZephyrOutputQuery();
    else {
      if (query.getTxQuery() == null) query = query.copy();
      else {
        ZephyrTxQuery txQuery = query.getTxQuery().copy();
        if (query.getTxQuery().getOutputQuery() == query) query = txQuery.getOutputQuery();
        else {
          GenUtils.assertNull("Output query's tx query must be circular reference or null", query.getTxQuery().getOutputQuery());
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
  }
  
  @Override
  public String exportOutputs(boolean all) {
    assertNotClosed();
    String outputsHex = exportOutputsJni(all);
    return outputsHex.isEmpty() ? null : outputsHex;
  }

  @Override
  public int importOutputs(String outputsHex) {
    assertNotClosed();
    return importOutputsJni(outputsHex);
  }

  @Override
  public List<ZephyrKeyImage> exportKeyImages(boolean all) {
    assertNotClosed();
    String keyImagesJson = exportKeyImagesJni(all);
    List<ZephyrKeyImage> keyImages = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, keyImagesJson, KeyImagesContainer.class).keyImages;
    return keyImages;
  }

  @Override
  public ZephyrKeyImageImportResult importKeyImages(List<ZephyrKeyImage> keyImages) {
    assertNotClosed();
    
    // wrap and serialize key images in container for jni
    KeyImagesContainer keyImageContainer = new KeyImagesContainer(keyImages);
    String importResultJson = importKeyImagesJni(JsonUtils.serialize(keyImageContainer));
    
    // deserialize response
    return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, importResultJson, ZephyrKeyImageImportResult.class);
  }

  @Override
  public List<ZephyrKeyImage> getNewKeyImagesFromLastImport() {
    assertNotClosed();
    throw new RuntimeException("ZephyrWalletFull.getNewKeyImagesFromLastImport() not implemented");
  }
  
  @Override
  public void freezeOutput(String keyImage) {
    assertNotClosed();
    try {
      freezeOutputJni(keyImage);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public void thawOutput(String keyImage) {
    assertNotClosed();
    try {
      thawOutputJni(keyImage);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public boolean isOutputFrozen(String keyImage) {
    assertNotClosed();
    try {
      return isOutputFrozenJni(keyImage);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public List<ZephyrTxWallet> createTxs(ZephyrTxConfig config) {
    assertNotClosed();
    LOGGER.fine("java createTxs(request)");
    LOGGER.fine("Tx config: " + JsonUtils.serialize(config));
    
    // validate request
    if (config == null) throw new ZephyrError("Tx config cannot be null");
    
    // submit tx config to JNI and get response as json rooted at tx set
    String txSetJson;
    try {
      txSetJson = createTxsJni(JsonUtils.serialize(config));
      LOGGER.fine("Received createTxs() response from JNI: " + txSetJson.substring(0, Math.min(5000, txSetJson.length())) + "...");
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
    
    // deserialize and return txs
    ZephyrTxSet txSet = JsonUtils.deserialize(txSetJson, ZephyrTxSet.class);
    return txSet.getTxs();
  }
  
  @Override
  public ZephyrTxWallet sweepOutput(ZephyrTxConfig config) {
    assertNotClosed();
    try {
      String txSetJson = sweepOutputJni(JsonUtils.serialize(config));
      ZephyrTxSet txSet = JsonUtils.deserialize(txSetJson, ZephyrTxSet.class);
      return txSet.getTxs().get(0);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public List<ZephyrTxWallet> sweepUnlocked(ZephyrTxConfig config) {
    assertNotClosed();
    
    // validate request
    if (config == null) throw new ZephyrError("Send request cannot be null");
    
    // submit send request to JNI and get response as json rooted at tx set
    String txSetsJson;
    try {
      txSetsJson = sweepUnlockedJni(JsonUtils.serialize(config));
      LOGGER.fine("Received sweepUnlocked() response from JNI: " + txSetsJson.substring(0, Math.min(5000, txSetsJson.length())) + "...");
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
    
    // deserialize tx sets
    List<ZephyrTxSet> txSets = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, txSetsJson, TxSetsContainer.class).txSets;
    
    // return txs
    List<ZephyrTxWallet> txs = new ArrayList<ZephyrTxWallet>();
    for (ZephyrTxSet txSet : txSets) txs.addAll(txSet.getTxs());
    return txs;
  }

  @Override
  public List<ZephyrTxWallet> sweepDust(boolean relay) {
    assertNotClosed();
    try {
      String txSetJson = sweepDustJni(relay);
      ZephyrTxSet txSet = JsonUtils.deserialize(txSetJson, ZephyrTxSet.class);
      if (txSet.getTxs() == null) txSet.setTxs(new ArrayList<ZephyrTxWallet>());
      return txSet.getTxs();
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public List<String> relayTxs(Collection<String> txMetadatas) {
    assertNotClosed();
    String[] txMetadatasArr = txMetadatas.toArray(new String[txMetadatas.size()]); // convert to array for jni
    try {
      return Arrays.asList(relayTxsJni(txMetadatasArr));
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public ZephyrTxSet describeTxSet(ZephyrTxSet txSet) {
    assertNotClosed();
    txSet = new ZephyrTxSet()
            .setUnsignedTxHex(txSet.getUnsignedTxHex())
            .setSignedTxHex(txSet.getSignedTxHex())
            .setMultisigTxHex(txSet.getMultisigTxHex());
    String describedTxSetJson;
    try {
      describedTxSetJson = describeTxSetJni(JsonUtils.serialize(txSet));
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
    return JsonUtils.deserialize(describedTxSetJson, ZephyrTxSet.class);
  }
  
  @Override
  public String signTxs(String unsignedTxHex) {
    assertNotClosed();
    try {
      return signTxsJni(unsignedTxHex);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public List<String> submitTxs(String signedTxHex) {
    assertNotClosed();
    try {
      return Arrays.asList(submitTxsJni(signedTxHex));
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public ZephyrCheckTx checkTxKey(String txHash, String txKey, String address) {
    assertNotClosed();
    try {
      String checkStr = checkTxKeyJni(txHash, txKey, address);
      return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, checkStr, ZephyrCheckTx.class);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public String getTxProof(String txHash, String address, String message) {
    assertNotClosed();
    try {
      return getTxProofJni(txHash, address, message);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public ZephyrCheckTx checkTxProof(String txHash, String address, String message, String signature) {
    assertNotClosed();
    try {
      String checkStr = checkTxProofJni(txHash, address, message, signature);
      return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, checkStr, ZephyrCheckTx.class);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public String getSpendProof(String txHash, String message) {
    assertNotClosed();
    try {
      return getSpendProofJni(txHash, message);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public boolean checkSpendProof(String txHash, String message, String signature) {
    assertNotClosed();
    try {
      return checkSpendProofJni(txHash, message, signature);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public String getReserveProofWallet(String message) {
    try {
      return getReserveProofWalletJni(message);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public String getReserveProofAccount(int accountIdx, BigInteger amount, String message) {
    assertNotClosed();
    try {
      return getReserveProofAccountJni(accountIdx, amount.toString(), message);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage(), -1);
    }
  }

  @Override
  public ZephyrCheckReserve checkReserveProof(String address, String message, String signature) {
    assertNotClosed();
    try {
      String checkStr = checkReserveProofJni(address, message, signature);
      return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, checkStr, ZephyrCheckReserve.class);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage(), -1);
    }
  }

  @Override
  public String signMessage(String msg, ZephyrMessageSignatureType signatureType, int accountIdx, int subaddressIdx) {
    assertNotClosed();
    return signMessageJni(msg, signatureType.ordinal(), accountIdx, subaddressIdx);
  }

  @Override
  public ZephyrMessageSignatureResult verifyMessage(String msg, String address, String signature) {
    assertNotClosed();
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
  }

  @Override
  public String getTxKey(String txHash) {
    assertNotClosed();
    try {
      return getTxKeyJni(txHash);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public List<String> getTxNotes(List<String> txHashes) {
    assertNotClosed();
    return Arrays.asList(getTxNotesJni(txHashes.toArray(new String[txHashes.size()])));  // convert to array for jni
  }

  @Override
  public void setTxNotes(List<String> txHashes, List<String> notes) {
    assertNotClosed();
    setTxNotesJni(txHashes.toArray(new String[txHashes.size()]), notes.toArray(new String[notes.size()]));
  }

  @Override
  public List<ZephyrAddressBookEntry> getAddressBookEntries(List<Integer> entryIndices) {
    assertNotClosed();
    if (entryIndices == null) entryIndices = new ArrayList<Integer>();
    String entriesJson = getAddressBookEntriesJni(GenUtils.listToIntArray(entryIndices));
    List<ZephyrAddressBookEntry> entries = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, entriesJson, AddressBookEntriesContainer.class).entries;
    if (entries == null) entries = new ArrayList<ZephyrAddressBookEntry>();
    return entries;
  }

  @Override
  public int addAddressBookEntry(String address, String description) {
    assertNotClosed();
    return addAddressBookEntryJni(address, description);
  }

  @Override
  public void editAddressBookEntry(int index, boolean setAddress, String address, boolean setDescription, String description) {
    assertNotClosed();
    editAddressBookEntryJni(index, setAddress, address, setDescription, description);
  }

  @Override
  public void deleteAddressBookEntry(int entryIdx) {
    assertNotClosed();
    deleteAddressBookEntryJni(entryIdx);
  }

  @Override
  public void tagAccounts(String tag, Collection<Integer> accountIndices) {
    assertNotClosed();
    throw new RuntimeException("ZephyrWalletFull.tagAccounts() not implemented");
  }

  @Override
  public void untagAccounts(Collection<Integer> accountIndices) {
    assertNotClosed();
    throw new RuntimeException("ZephyrWalletFull.untagAccounts() not implemented");
  }

  @Override
  public List<ZephyrAccountTag> getAccountTags() {
    assertNotClosed();
    throw new RuntimeException("ZephyrWalletFull.getAccountTags() not implemented");
  }

  @Override
  public void setAccountTagLabel(String tag, String label) {
    assertNotClosed();
    throw new RuntimeException("ZephyrWalletFull.setAccountTagLabel() not implemented");
  }

  @Override
  public String getPaymentUri(ZephyrTxConfig request) {
    assertNotClosed();
    try {
      return getPaymentUriJni(JsonUtils.serialize(request));
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public ZephyrTxConfig parsePaymentUri(String uri) {
    assertNotClosed();
    try {
      String sendRequestJson = parsePaymentUriJni(uri);
      return JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, sendRequestJson, ZephyrTxConfig.class);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public String getAttribute(String key) {
    assertNotClosed();
    return getAttributeJni(key);
  }

  @Override
  public void setAttribute(String key, String val) {
    assertNotClosed();
    setAttributeJni(key, val);
  }

  @Override
  public void startMining(Long numThreads, Boolean backgroundMining, Boolean ignoreBattery) {
    assertNotClosed();
    try {
      startMiningJni(numThreads == null ? 0l : (long) numThreads, Boolean.TRUE.equals(backgroundMining), Boolean.TRUE.equals(ignoreBattery));
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public void stopMining() {
    assertNotClosed();
    try {
      stopMiningJni();
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  
  @Override
  public boolean isMultisigImportNeeded() {
    assertNotClosed();
    return isMultisigImportNeededJni();
  }
  
  @Override
  public ZephyrMultisigInfo getMultisigInfo() {
    assertNotClosed();
    try {
      String multisigInfoJson = getMultisigInfoJni();
      return JsonUtils.deserialize(multisigInfoJson, ZephyrMultisigInfo.class);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public String prepareMultisig() {
    assertNotClosed();
    return prepareMultisigJni();
  }

  @Override
  public String makeMultisig(List<String> multisigHexes, int threshold, String password) {
    assertNotClosed();
    try {
      return makeMultisigJni(multisigHexes.toArray(new String[multisigHexes.size()]), threshold, password);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public ZephyrMultisigInitResult exchangeMultisigKeys(List<String> multisigHexes, String password) {
    assertNotClosed();
    try {
      String initMultisigResultJson = exchangeMultisigKeysJni(multisigHexes.toArray(new String[multisigHexes.size()]), password);
      return JsonUtils.deserialize(initMultisigResultJson, ZephyrMultisigInitResult.class);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public String exportMultisigHex() {
    assertNotClosed();
    try {
      return exportMultisigHexJni();
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public int importMultisigHex(List<String> multisigHexes) {
    assertNotClosed();
    try {
      return importMultisigHexJni(multisigHexes.toArray(new String[multisigHexes.size()]));
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public ZephyrMultisigSignResult signMultisigTxHex(String multisigTxHex) {
    assertNotClosed();
    try {
      String signMultisigResultJson = signMultisigTxHexJni(multisigTxHex);
      return JsonUtils.deserialize(signMultisigResultJson, ZephyrMultisigSignResult.class);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  @Override
  public List<String> submitMultisigTxHex(String signedMultisigTxHex) {
    assertNotClosed();
    try {
      return Arrays.asList(submitMultisigTxHexJni(signedMultisigTxHex));
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  /**
   * Get the wallet's keys and cache data.
   * 
   * @return the keys and cache data, respectively
   */
  public synchronized byte[][] getData() {
    return new byte[][] { getKeysFileBufferJni(password, isViewOnly()), getCacheFileBufferJni() };
  }
  
  @Override
  public void changePassword(String oldPassword, String newPassword) {
    try {
      if (!password.equals(oldPassword)) throw new RuntimeException("Invalid original password.");
      changePasswordJni(oldPassword, newPassword);
      password = newPassword;
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
    }
  }
  
  @Override
  public void save() {
    assertNotClosed();
    saveJni();
  }
  
  @Override
  public void close(boolean save) {
    if (isClosed) return; // closing a closed wallet has no effect
    isClosed = true;
    password = null;
    refreshListening();
    try {
      closeJni(save);
    } catch (Exception e) {
      throw new ZephyrError(e.getMessage());
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
  
  private native long setListenerJni(WalletJniListener listener);
  
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
   * Receives notifications directly from jni c++.
   */
  @SuppressWarnings("unused") // called directly from jni c++
  private class WalletJniListener {
    
    public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) {
      for (ZephyrWalletListenerI listener : getListeners()) listener.onSyncProgress(height, startHeight, endHeight, percentDone, message);
    }
    
    public void onNewBlock(long height) {
      for (ZephyrWalletListenerI listener : getListeners()) listener.onNewBlock(height);
    }
    
    public void onBalancesChanged(String newBalanceStr, String newUnlockedBalanceStr, String assetType) {
      if (!"ZPH".equals(assetType)) return; // temporary until R5, which gives the listener interface the asset
      for (ZephyrWalletListenerI listener : getListeners()) listener.onBalancesChanged(new BigInteger(newBalanceStr), new BigInteger(newUnlockedBalanceStr));
    }
    
    public void onOutputReceived(long height, String txHash, String amountStr, String assetType, int accountIdx, int subaddressIdx, int version, long unlockHeight, boolean isLocked) {
      
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
      for (ZephyrWalletListenerI listener : getListeners()) listener.onOutputReceived((ZephyrOutputWallet) tx.getOutputs().get(0));
    }
    
    public void onOutputSpent(long height, String txHash, String amountStr, String assetType, String accountIdxStr, String subaddressIdxStr, int version, long unlockHeight, boolean isLocked) {
      
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
      for (ZephyrWalletListenerI listener : getListeners()) listener.onOutputSpent((ZephyrOutputWallet) tx.getInputs().get(0));
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
    if (!deserializedBlocks.missingTxHashes.isEmpty()) throw new RuntimeException("Wallet missing requested tx hashes: " + deserializedBlocks.missingTxHashes);
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
    if (!deserializedBlocks.missingTxHashes.isEmpty()) throw new RuntimeException("Wallet missing requested tx hashes: " + deserializedBlocks.missingTxHashes);
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
   * Enables or disables listening in the c++ wallet.
   */
  private void refreshListening() {
    boolean isEnabled = listeners.size() > 0;
    if (jniListenerHandle == 0 && !isEnabled || jniListenerHandle > 0 && isEnabled) return; // no difference
    jniListenerHandle = setListenerJni(isEnabled ? jniListener : null);
  }
  
  private void assertNotClosed() {
    if (isClosed) throw new ZephyrError("Wallet is closed");
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
