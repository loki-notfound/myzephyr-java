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
  
  public String getPath() {
    return path;
  }
  
  public ZephyrWalletConfig setPath(String path) {
    this.path = path;
    return this;
  }
  
  public String getPassword() {
    return password;
  }
  
  public ZephyrWalletConfig setPassword(String password) {
    this.password = password;
    return this;
  }
  
  public ZephyrNetworkType getNetworkType() {
    return networkType;
  }
  
  public ZephyrWalletConfig setNetworkType(ZephyrNetworkType networkType) {
    this.networkType = networkType;
    return this;
  }
  
  public ZephyrWalletConfig setNetworkType(String networkTypeStr) {
    return setNetworkType(ZephyrNetworkType.parse(networkTypeStr));
  }
  
  public ZephyrRpcConnection getServer() {
    return this.serverUri == null || this.serverUri.isEmpty() ? null : new ZephyrRpcConnection(this.serverUri, this.serverUsername, this.serverPassword);
  }
  
  public ZephyrWalletConfig setServer(ZephyrRpcConnection server) {
    this.serverUri = server == null ? null : server.getUri();
    this.serverUsername = server == null ? null : server.getUsername();
    this.serverPassword = server == null ? null : server.getPassword();
    return this;
  }
  
  public String getServerUri() {
    return serverUri;
  }
  
  public ZephyrWalletConfig setServerUri(String serverUri) {
    this.serverUri = serverUri;
    return this;
  }
  
  public String getServerUsername() {
    return serverUsername;
  }
  
  public ZephyrWalletConfig setServerUsername(String serverUsername) {
    this.serverUsername = serverUsername;
    return this;
  }
  
  public String getServerPassword() {
    return serverPassword;
  }
  
  public ZephyrWalletConfig setServerPassword(String serverPassword) {
    this.serverPassword = serverPassword;
    return this;
  }
  
  public String getMnemonic() {
    return mnemonic;
  }
  
  public ZephyrWalletConfig setMnemonic(String mnemonic) {
    this.mnemonic = mnemonic;
    return this;
  }
  
  public String getSeedOffset() {
    return seedOffset;
  }
  
  public ZephyrWalletConfig setSeedOffset(String seedOffset) {
    this.seedOffset = seedOffset;
    return this;
  }
  
  public String getPrimaryAddress() {
    return primaryAddress;
  }
  
  public ZephyrWalletConfig setPrimaryAddress(String primaryAddress) {
    this.primaryAddress = primaryAddress;
    return this;
  }
  
  public String getPrivateViewKey() {
    return privateViewKey;
  }
  
  public ZephyrWalletConfig setPrivateViewKey(String privateViewKey) {
    this.privateViewKey = privateViewKey;
    return this;
  }
  
  public String getPrivateSpendKey() {
    return privateSpendKey;
  }
  
  public ZephyrWalletConfig setPrivateSpendKey(String privateSpendKey) {
    this.privateSpendKey = privateSpendKey;
    return this;
  }
  
  public Long getRestoreHeight() {
    return restoreHeight;
  }
  
  public ZephyrWalletConfig setRestoreHeight(Long restoreHeight) {
    this.restoreHeight = restoreHeight;
    return this;
  }
  
  public String getLanguage() {
    return language;
  }
  
  public ZephyrWalletConfig setLanguage(String language) {
    this.language = language;
    return this;
  }
  
  public Boolean getSaveCurrent() {
    return saveCurrent;
  }
  
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
  
  public Integer getSubaddressLookahead() {
    return subaddressLookahead;
  }

  public byte[] getKeysData() {
    return keysData;
  }

  public ZephyrWalletConfig setKeysData(byte[] keysData) {
    this.keysData = keysData;
    return this;
  }

  public byte[] getCacheData() {
    return cacheData;
  }

  public ZephyrWalletConfig setCacheData(byte[] cacheData) {
    this.cacheData = cacheData;
    return this;
  }

  public String toString() {
    return JsonUtils.serialize(this);
  }
}