package zephyr.wallet.model;

import zephyr.lib.utils.JsonUtils;
import zephyr.common.ZephyrRpcConnection;
import zephyr.daemon.model.ZephyrNetworkType;

/**
 * Configuration to create a Zephyr wallet.
 */
public class ZephyrWalletConfig {
  
  private String path;
  private String password;
  private ZephyrNetworkType networkType;
  private String serverUri;
  private String serverUsername;
  private String serverPassword;
  private String mnemonic;
  private String seedOffset;
  private String primaryAddress;
  private String privateViewKey;
  private String privateSpendKey;
  private Long restoreHeight;
  private String language;
  private Boolean saveCurrent;
  private Integer accountLookahead;     // number of accounts to scan
  private Integer subaddressLookahead;  // number of subaddresses to scan per account
  private byte[] keysData;
  private byte[] cacheData;
  
  /**
   * Creates an empty wallet configuration.
   */
  public ZephyrWalletConfig() { }
  
  /**
   * Returns the path of the wallet files.
   *
   * @return the path, null if not set
   */
  public String getPath() {
    return path;
  }
  
  /**
   * Sets the path of the wallet files: the cache is written at the path and the keys at the path
   * plus {@code ".keys"}. Without a path, {@code createWallet} makes a wallet that lives only in
   * memory, for which {@code save()} writes nothing. See the user guide,
   * {@code docs/guide/02-wallets.md}, section "Wallet bytes".
   *
   * @param path is the path
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setPath(String path) {
    this.path = path;
    return this;
  }
  
  /**
   * Returns the wallet password.
   *
   * @return the password, null if not set
   */
  public String getPassword() {
    return password;
  }
  
  /**
   * Sets the password that encrypts the wallet's keys and cache; {@code openWallet} requires it.
   * Treat it as a secret: do not log it.
   *
   * @param password is the password
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setPassword(String password) {
    this.password = password;
    return this;
  }
  
  /**
   * Returns the network type.
   *
   * @return the network type, null if not set
   */
  public ZephyrNetworkType getNetworkType() {
    return networkType;
  }
  
  /**
   * Sets the network type; {@code createWallet} and {@code openWallet} require it. Testnet and
   * stagenet never reach the rules of the current assets. See the user guide,
   * {@code docs/guide/02-wallets.md}, section "Network types".
   *
   * @param networkType is the network type
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setNetworkType(ZephyrNetworkType networkType) {
    this.networkType = networkType;
    return this;
  }
  
  /**
   * Sets the network type from its name, as {@link #setNetworkType(ZephyrNetworkType)} does.
   *
   * @param networkTypeStr is "mainnet", "testnet" or "stagenet" (in any case); another name throws
   * a {@code ZephyrError}
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setNetworkType(String networkTypeStr) {
    return setNetworkType(ZephyrNetworkType.parse(networkTypeStr));
  }
  
  /**
   * Returns the connection to the node, made of the server URI, username and password.
   *
   * @return the connection, null if no server URI is set
   */
  public ZephyrRpcConnection getServer() {
    return this.serverUri == null || this.serverUri.isEmpty() ? null : new ZephyrRpcConnection(this.serverUri, this.serverUsername, this.serverPassword);
  }
  
  /**
   * Sets the server URI, username and password from a connection.
   *
   * @param server is the connection to the node, null to clear all three
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setServer(ZephyrRpcConnection server) {
    this.serverUri = server == null ? null : server.getUri();
    this.serverUsername = server == null ? null : server.getUsername();
    this.serverPassword = server == null ? null : server.getPassword();
    return this;
  }
  
  /**
   * Returns the URI of the node.
   *
   * @return the URI, null if not set
   */
  public String getServerUri() {
    return serverUri;
  }
  
  /**
   * Sets the URI of the node ({@code zephyrd}) the wallet connects to, for example
   * {@code http://host:17767}. Without it the wallet is offline. See the user guide,
   * {@code docs/guide/03-connecting-to-a-node.md}, section "Setting the node".
   *
   * @param serverUri is the URI
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setServerUri(String serverUri) {
    this.serverUri = serverUri;
    return this;
  }
  
  /**
   * Returns the username for the node's RPC login.
   *
   * @return the username, null if not set
   */
  public String getServerUsername() {
    return serverUsername;
  }
  
  /**
   * Sets the username for the node's RPC login, if the node requires one.
   *
   * @param serverUsername is the username
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setServerUsername(String serverUsername) {
    this.serverUsername = serverUsername;
    return this;
  }
  
  /**
   * Returns the password for the node's RPC login.
   *
   * @return the password, null if not set
   */
  public String getServerPassword() {
    return serverPassword;
  }
  
  /**
   * Sets the password for the node's RPC login, if the node requires one. Treat it as a secret: do
   * not log it.
   *
   * @param serverPassword is the password
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setServerPassword(String serverPassword) {
    this.serverPassword = serverPassword;
    return this;
  }
  
  /**
   * Returns the mnemonic to restore from.
   *
   * @return the mnemonic, null if not set
   */
  public String getMnemonic() {
    return mnemonic;
  }
  
  /**
   * Sets the mnemonic (25 words) to restore a wallet from. {@code createWallet} refuses it together
   * with keys, {@code openWallet} refuses it at all. Anyone with the mnemonic can spend every asset
   * of the wallet. Treat it as a secret: do not log it.
   *
   * @param mnemonic is the mnemonic
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setMnemonic(String mnemonic) {
    this.mnemonic = mnemonic;
    return this;
  }
  
  /**
   * Returns the seed offset.
   *
   * @return the seed offset, null if not set
   */
  public String getSeedOffset() {
    return seedOffset;
  }
  
  /**
   * Sets the seed offset: a passphrase from which the core derives a different wallet from the same
   * mnemonic. Only together with a mnemonic. Seed offsets are inherited from monero-java; not
   * tested against Zephyr.
   *
   * @param seedOffset is the seed offset
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setSeedOffset(String seedOffset) {
    this.seedOffset = seedOffset;
    return this;
  }
  
  /**
   * Returns the primary address to restore from keys.
   *
   * @return the address, null if not set
   */
  public String getPrimaryAddress() {
    return primaryAddress;
  }
  
  /**
   * Sets the primary address to restore a wallet from keys: with the private view key alone it
   * makes a view-only wallet, with the private spend key a wallet that can spend. See the user
   * guide, {@code docs/guide/02-wallets.md}, section "View-only wallets".
   *
   * @param primaryAddress is the primary address
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setPrimaryAddress(String primaryAddress) {
    this.primaryAddress = primaryAddress;
    return this;
  }
  
  /**
   * Returns the private view key to restore from.
   *
   * @return the key, null if not set
   */
  public String getPrivateViewKey() {
    return privateViewKey;
  }
  
  /**
   * Sets the private view key (hex) to restore a wallet from keys. It reveals every incoming
   * payment of the wallet. Treat it as a secret: do not log it.
   *
   * @param privateViewKey is the private view key
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setPrivateViewKey(String privateViewKey) {
    this.privateViewKey = privateViewKey;
    return this;
  }
  
  /**
   * Returns the private spend key to restore from.
   *
   * @return the key, null if not set
   */
  public String getPrivateSpendKey() {
    return privateSpendKey;
  }
  
  /**
   * Sets the private spend key (hex) to restore a wallet from keys. Anyone with it can spend every
   * asset of the wallet. Treat it as a secret: do not log it.
   *
   * @param privateSpendKey is the private spend key
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setPrivateSpendKey(String privateSpendKey) {
    this.privateSpendKey = privateSpendKey;
    return this;
  }
  
  /**
   * Returns the restore height.
   *
   * @return the height, null if not set
   */
  public Long getRestoreHeight() {
    return restoreHeight;
  }
  
  /**
   * Sets the block height from which a restored wallet scans for its outputs; without it the
   * restore height is 0 and the first sync scans the whole chain. {@code createWallet} refuses it
   * for a new random wallet, whose restore height the core sets to the node's height when it is
   * connected. See the user guide, {@code docs/guide/02-wallets.md}, section "Restoring from a
   * mnemonic".
   *
   * @param restoreHeight is the height
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setRestoreHeight(Long restoreHeight) {
    this.restoreHeight = restoreHeight;
    return this;
  }
  
  /**
   * Returns the language of the mnemonic.
   *
   * @return the language, null if not set
   */
  public String getLanguage() {
    return language;
  }
  
  /**
   * Sets the language of the mnemonic of a new random wallet or a wallet from keys ("English" if
   * not set). {@code createWallet} refuses it with a mnemonic, whose language it detects, and
   * {@code openWallet} refuses it. Mnemonic languages are inherited from monero-java; not tested
   * against Zephyr.
   *
   * @param language is the language, one of {@code ZephyrWalletFull.getMnemonicLanguages()}
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setLanguage(String language) {
    this.language = language;
    return this;
  }
  
  /**
   * Returns the save-current setting, see {@link #setSaveCurrent(Boolean)}.
   *
   * @return the setting, null if not set
   */
  public Boolean getSaveCurrent() {
    return saveCurrent;
  }
  
  /**
   * Sets whether a wallet RPC server should save its current wallet before it opens or creates
   * another. Inherited from monero-java, where only the wallet RPC client used it; zephyr-java has
   * no such client. {@code ZephyrWalletFull.createWallet} refuses a configuration in which it is set
   * at all, {@code ZephyrWalletFull.openWallet} one in which it is true.
   *
   * @param saveCurrent is true to save the current wallet
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setSaveCurrent(Boolean saveCurrent) {
    this.saveCurrent = saveCurrent;
    return this;
  }
  
  /**
   * Set the number of accounts of scan.
   * 
   * @param accountLookahead the number of accounts to scan
   * @return this config for convenience
   */
  public ZephyrWalletConfig setAccountLookahead(Integer accountLookahead) {
    this.accountLookahead = accountLookahead;
    return this;
  }
  
  /**
   * Returns the number of accounts to scan, see {@link #setAccountLookahead(Integer)}. The core
   * needs it together with the subaddress lookahead, or neither.
   *
   * @return the number of accounts, null if not set
   */
  public Integer getAccountLookahead() {
    return accountLookahead;
  }
  
  /**
   * Set the number of subaddresses to scan per account.
   * 
   * @param subaddressLookahead the number of subaddresses to scan per account
   * @return this config for convenience
   */
  public ZephyrWalletConfig setSubaddressLookahead(Integer subaddressLookahead) {
    this.subaddressLookahead = subaddressLookahead;
    return this;
  }
  
  /**
   * Returns the number of subaddresses to scan per account, see
   * {@link #setSubaddressLookahead(Integer)}. The core needs it together with the account
   * lookahead, or neither.
   *
   * @return the number of subaddresses, null if not set
   */
  public Integer getSubaddressLookahead() {
    return subaddressLookahead;
  }

  /**
   * Returns the keys of the wallet as bytes.
   *
   * @return the keys, null if not set
   */
  public byte[] getKeysData() {
    return keysData;
  }

  /**
   * Sets the keys of the wallet as bytes, as {@code getData()[0]} returns them, encrypted with the
   * wallet password. With them {@code openWallet} opens the wallet from memory instead of a path.
   * See the user guide, {@code docs/guide/02-wallets.md}, section "Wallet bytes".
   *
   * @param keysData are the keys
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setKeysData(byte[] keysData) {
    this.keysData = keysData;
    return this;
  }

  /**
   * Returns the cache of the wallet as bytes.
   *
   * @return the cache, null if not set
   */
  public byte[] getCacheData() {
    return cacheData;
  }

  /**
   * Sets the cache of the wallet as bytes, as {@code getData()[1]} returns them: the synced
   * history, used together with {@link #setKeysData(byte[])}.
   *
   * @param cacheData is the cache
   * @return this configuration for chaining
   */
  public ZephyrWalletConfig setCacheData(byte[] cacheData) {
    this.cacheData = cacheData;
    return this;
  }

  public String toString() {
    return JsonUtils.serialize(this);
  }
}