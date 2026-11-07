package zephyr.common;

import com.fasterxml.jackson.core.type.TypeReference;
import zephyr.lib.utils.GenUtils;
import zephyr.lib.utils.JsonUtils;
import zephyr.lib.utils.Keccak256;
import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.daemon.model.ZephyrTx;
import zephyr.wallet.model.ZephyrAddressType;
import zephyr.wallet.model.ZephyrDecodedAddress;
import zephyr.wallet.model.ZephyrIntegratedAddress;

/**
 * Collection of Zephyr utilities.
 */
public class ZephyrUtils {
  
  // load JNI binding if available
  private static boolean JNI_LOADED = false;
  static {
    try { loadNativeLibrary(); }
    catch (UnsatisfiedLinkError e) { }
  }
  
  /**
   * Get the version of the zephyr-java library.
   * 
   * @return the version of this zephyr-java library
   */
  public static String getVersion() {
    try (java.io.InputStream in = ZephyrUtils.class.getResourceAsStream("/zephyr-java.properties")) {
      java.util.Properties props = new java.util.Properties();
      if (in != null) props.load(in);
      return props.getProperty("version", "unknown");
    } catch (java.io.IOException e) {
      return "unknown";
    }
  }
  
  /**
   * Load the native library.
   */
  public static void loadNativeLibrary() {
    String libName = (System.getProperty("os.name").toLowerCase().contains("windows") ? "lib" : "") + "zephyr-java";
    System.loadLibrary(libName);
    JNI_LOADED = true;
  }
  
  /**
   * Indicates if the native JNI library is loaded.
   * 
   * @return true if the native library is loaded, false otherwise
   */
  public static boolean isNativeLibraryLoaded() {
    return JNI_LOADED;
  }
  
  public static final int RING_SIZE = 16; // network-enforced ring size of the Zephyr core
  
  private static int LOG_LEVEL = 0;
  private static long AU_PER_COIN = 1000000000000l;
  private static final int NUM_MNEMONIC_WORDS = 25;
  private static final int VIEW_KEY_LENGTH = 64;
  private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
  private static final List<Character> CHARS = new ArrayList<Character>();
  static {
    for (char c : ALPHABET.toCharArray()) {
      CHARS.add(c);
    }
  }
  private final static BigDecimal ALPHABET_SIZE = new BigDecimal(ALPHABET.length());
  private final static Map<Integer, Integer> ENCODED_BLOCK_SIZE = new HashMap<Integer, Integer>();
  static {
      ENCODED_BLOCK_SIZE.put(0, 0);
      ENCODED_BLOCK_SIZE.put(2, 1);
      ENCODED_BLOCK_SIZE.put(3, 2);
      ENCODED_BLOCK_SIZE.put(5, 3);
      ENCODED_BLOCK_SIZE.put(6, 4);
      ENCODED_BLOCK_SIZE.put(7, 5);
      ENCODED_BLOCK_SIZE.put(9, 6);
      ENCODED_BLOCK_SIZE.put(10, 7);
      ENCODED_BLOCK_SIZE.put(11, 8);
  };
  private final static int FULL_BLOCK_SIZE = 8;
  private final static int FULL_ENCODED_BLOCK_SIZE = 11;
  private final static BigDecimal UINT64_MAX = new BigDecimal(Math.pow(2, 64));
  private final static Pattern BASE58_PATTERN = Pattern.compile("^[" + ALPHABET + "]+$");
  private final static int MIN_DECODED_LENGTH = 5; // bytes: one-byte tag plus checksum
  private final static int MAX_VARINT_LENGTH = 10; // bytes of a varint-encoded uint64
  private final static int STANDARD_ADDRESS_REMAINDER = 68; // after the tag: spend key, view key, checksum
  private final static int INTEGRATED_ADDRESS_REMAINDER = 76; // after the tag: spend key, view key, payment id, checksum
  
  /**
   * Indicates if a wallet keys file exists at the given path.
   * 
   * @param path is the path with wallet name to check for existence
   * @return true if a wallet keys file exists at the given path, false otherwise
   */
  public static boolean walletExists(String path) {
    String basePath = path.lastIndexOf('.') > path.lastIndexOf(File.separatorChar) ? path.substring(0, path.lastIndexOf('.')) : path;
    File keysFile = new File(basePath + ".keys");
    return keysFile.exists() && keysFile.isFile();
  }
  
  /**
   * Validates the given mnemonic phrase.
   * 
   * @param mnemonic is the mnemonic to validate
   * @throws ZephyrError if the given mnemonic is invalid
   */
  public static void validateMnemonic(String mnemonic) {
    GenUtils.assertNotNull("Mnemonic phrase is not initialized", mnemonic);
    GenUtils.assertFalse("Mnemonic phrase is empty", mnemonic.isEmpty());
    String[] words = mnemonic.split(" ");
    if (words.length != ZephyrUtils.NUM_MNEMONIC_WORDS) throw new Error("Mnemonic phrase is " + words.length + " words but must be " + ZephyrUtils.NUM_MNEMONIC_WORDS);
  }
  
  /**
   * Indicates if a private view key is valid.
   * 
   * @param privateViewKey is the private view key to validate
   * @return true if the private view key is valid, false otherwise
   */
  public static boolean isValidPrivateViewKey(String privateViewKey) {
    try {
      validatePrivateViewKey(privateViewKey);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
  
  /**
   * Indicates if a public view key is valid.
   * 
   * @param publicViewKey is the public view key to validate
   * @return true if the public view key is valid, false otherwise
   */
  public static boolean isValidPublicViewKey(String publicViewKey) {
    try {
      validatePublicViewKey(publicViewKey);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
  
  /**
   * Indicates if a private spend key is valid.
   * 
   * @param privateSpendKey is the private spend key to validate
   * @return true if the private spend key is valid, false otherwise
   */
  public static boolean isValidPrivateSpendKey(String privateSpendKey) {
    try {
      validatePrivateSpendKey(privateSpendKey);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
  
  /**
   * Indicates if a public spend key is valid.
   * 
   * @param publicSpendKey is the public spend key to validate
   * @return true if the public spend key is valid, false otherwise
   */
  public static boolean isValidPublicSpendKey(String publicSpendKey) {
    try {
      validatePublicSpendKey(publicSpendKey);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
  
  /**
   * Validate a private view key.
   * 
   * @param privateViewKey is the private view key to validate
   * @throws ZephyrError if the given private view key is invalid
   */
  public static void validatePrivateViewKey(String privateViewKey) {
    if (!isHex64(privateViewKey)) throw new ZephyrError("private view key expected to be 64 hex characters");
  }
  
  /**
   * Validate a public view key.
   * 
   * @param publicViewKey is the public view key to validate
   * @throws ZephyrError if the given public view key is invalid
   */
  public static void validatePublicViewKey(String publicViewKey) {
    if (!isHex64(publicViewKey)) throw new ZephyrError("public view key expected to be 64 hex characters");
  }
  
  /**
   * Validate a private spend key.
   * 
   * @param privateSpendKey is the private spend key to validate
   * @throws ZephyrError if the given private spend key is invalid
   */
  public static void validatePrivateSpendKey(String privateSpendKey) {
    if (!isHex64(privateSpendKey)) throw new ZephyrError("private spend key expected to be 64 hex characters");
  }
  
  /**
   * Validate a public spend key.
   * 
   * @param publicSpendKey is the public spend key to validate
   * @throws ZephyrError if the given public spend key is invalid
   */
  public static void validatePublicSpendKey(String publicSpendKey) {
    if (!isHex64(publicSpendKey)) throw new ZephyrError("public spend key expected to be 64 hex characters");
  }
  
  /**
   * Get an integrated address.
   * 
   * @param networkType is the network type of the integrated address
   * @param standardAddress is the address to derive the integrated address from
   * @param paymentId optionally specifies the integrated address's payment id (defaults to random payment id)
   * @return the integrated address
   */
  public static ZephyrIntegratedAddress getIntegratedAddress(ZephyrNetworkType networkType, String standardAddress, String paymentId) {
    try {
      return JsonUtils.deserialize(getIntegratedAddressJni(networkType.ordinal(), standardAddress, paymentId == null ? "" : paymentId), ZephyrIntegratedAddress.class);
    } catch (Exception err) {
      throw new ZephyrError(err.getMessage());
    }
  }
  
  /**
   * Decodes the given address.
   * 
   * @param address is the address to decode
   * @return the decoded address and network types
   */
  public static ZephyrDecodedAddress decodeAddress(String address) {
    GenUtils.assertNotNull("Address is null", address);
    
    // address must consist of base58 characters
    if (!BASE58_PATTERN.matcher(address).matches()) throw new ZephyrError("Address has invalid characters");
    
    // decode address to hex string; the base58 block scheme rejects invalid encodings
    String addressHex;
    try {
      addressHex = decodeAddressToHex(address);
    } catch (IllegalArgumentException e) {
      throw new ZephyrError("Address has invalid encoding");
    }
    
    // at least a one-byte tag and the four-byte checksum
    if (addressHex.length() < 2 * MIN_DECODED_LENGTH) throw new ZephyrError("Address has invalid length");
    
    // validate address hash
    GenUtils.assertTrue("Address has invalid hash", isValidAddressHash(addressHex));
    
    // read the varint tag (little-endian base-128)
    byte[] addressBytes = hexToBin(addressHex);
    long tag = 0;
    int tagLength = 0;
    while (true) {
      if (tagLength >= MAX_VARINT_LENGTH || tagLength >= addressBytes.length) throw new ZephyrError("Address has invalid prefix");
      int b = addressBytes[tagLength] & 0xff;
      tag |= ((long) (b & 0x7f)) << (7 * tagLength);
      tagLength++;
      if ((b & 0x80) == 0) break;
    }
    
    // classify by the length after the tag
    boolean isIntegrated;
    int remainder = addressBytes.length - tagLength;
    if (remainder == STANDARD_ADDRESS_REMAINDER) isIntegrated = false;
    else if (remainder == INTEGRATED_ADDRESS_REMAINDER) isIntegrated = true;
    else throw new ZephyrError("Address has invalid length");
    
    // determine network and address types
    ZephyrAddressType addressType = null;
    ZephyrNetworkType networkType = null;
    for (ZephyrNetworkType aNetworkType : ZephyrNetworkType.values()) {
      if (tag == aNetworkType.getPrimaryAddressTag()) {
        addressType = ZephyrAddressType.PRIMARY_ADDRESS;
        networkType = aNetworkType;
        break;
      } else if (tag == aNetworkType.getIntegratedAddressTag()) {
        addressType = ZephyrAddressType.INTEGRATED_ADDRESS;
        networkType = aNetworkType;
        break;
      } else if (tag == aNetworkType.getSubaddressTag()) {
        addressType = ZephyrAddressType.SUBADDRESS;
        networkType = aNetworkType;
        break;
      }
    }
    if (addressType == null) throw new ZephyrError("Address has invalid code: 0x" + Long.toHexString(tag));
    
    // the address type must agree with the length after the tag
    if ((addressType == ZephyrAddressType.INTEGRATED_ADDRESS) != isIntegrated) throw new ZephyrError("Address has invalid code");
    
    // return decoded address
    return new ZephyrDecodedAddress(address, addressType, networkType);
  }
  
  /**
   * Determine if the given address is valid.
   * 
   * @param address is the address to validate
   * @param networkType is the address's network type
   * @return true if the address is valid, false otherwise
   */
  public static boolean isValidAddress(String address, ZephyrNetworkType networkType) {
      try {
        validateAddress(address, networkType);
        return true;
      } catch (ZephyrError e) {
        return false;
      }
  }
  
  /**
   * Validates the given address.
   * 
   * @param address is the address to validate
   * @param networkType is the address's network type
   */
  public static void validateAddress(String address, ZephyrNetworkType networkType) {
    try {
      ZephyrDecodedAddress decodedAddress = decodeAddress(address);
      GenUtils.assertEquals("Address network type mismatch: " + networkType + " vs " + decodedAddress.getNetworkType(), networkType, decodedAddress.getNetworkType());
    } catch (AssertionError e) {
      throw new ZephyrError(e.getMessage());
    }
  }

  /**
   * Validates the given address with the native core (monero_utils::validate_address).
   * 
   * Test cross-check of the pure-Java decoder; validateAddress does not use it.
   * 
   * @param address is the address to validate
   * @param networkType is the address's network type
   * @throws ZephyrError if the core rejects the address
   */
  static void validateAddressNative(String address, ZephyrNetworkType networkType) {
    try {
      validateAddressJni(address, networkType.ordinal());
    } catch (Exception err) {
      throw new ZephyrError(err.getMessage());
    }
  }

  // TODO: improve validation
  public static void validatePaymentId(String paymentId) {
    GenUtils.assertTrue(paymentId.length() == 16 || paymentId.length() == 64);
  }
  
  /**
   * Validates the given view key.
   * 
   * @param viewKey is the view key to validate
   * @throws ZephyrError if the given view key is invalid
   */
  public static void validateViewKey(String viewKey) {
    if (viewKey == null) throw new ZephyrError("View key is null");
    if (viewKey.length() != VIEW_KEY_LENGTH) throw new ZephyrError("View key is " + viewKey.length() + " characters but must be " + VIEW_KEY_LENGTH);
  }
  
  /**
   * Converts the string to a URI.  Throws ZephyrException if exception.
   * 
   * @param uri is the string to convert to a URI
   * @return URI is the initialized object from the string endpoint
   */
  public static URI parseUri(String uri) {
    if (uri != null && uri.length() > 0 && !uri.toLowerCase().matches("^\\w+://.+")) uri = "http://" + uri; // assume http if protocol not given
    try {
      return new URI(uri);
    } catch (Exception e) {
      throw new ZephyrError(e);
    }
  }

  public static void validateHex(String str) {
    if (!str.matches("^([0-9A-Fa-f]{2})+$")) throw new ZephyrError("Invalid hex: " + str);
  }

  public static void validateBase58(String standardAddress) {
    for (char c : standardAddress.toCharArray()) {
      if (!CHARS.contains(c)) throw new ZephyrError("Invalid Base58 " + standardAddress);
    }
  }
  
  /**
   * Determines if two payment ids are functionally equal.
   * 
   * For example, 03284e41c342f032 and 03284e41c342f032000000000000000000000000000000000000000000000000 are considered equal.
   * 
   * @param paymentId1 is a payment id to compare
   * @param paymentId2 is a payment id to compare
   * @return true if the payment ids are equal, false otherwise
   */
  public static boolean paymentIdsEqual(String paymentId1, String paymentId2) {
    int maxLength = Math.max(paymentId1.length(), paymentId2.length());
    for (int i = 0; i < maxLength; i++) {
      if (i < paymentId1.length() && i < paymentId2.length() && paymentId1.charAt(i) != paymentId2.charAt(i)) return false;
      if (i >= paymentId1.length() && paymentId2.charAt(i) != '0') return false;
      if (i >= paymentId2.length() && paymentId1.charAt(i) != '0') return false;
    }
    return true;
  }
  
  /**
   * Merges a transaction into a list of existing transactions.
   * 
   * @param <T> is a ZephyrTx or subclasses like ZephyrTxWallet
   * @param txs are existing transactions to merge into
   * @param tx is the transaction to merge into the list
   */
  public static <T extends ZephyrTx> void mergeTx(List<T> txs, T tx) {
    for (ZephyrTx aTx : txs) {
      if (aTx.getHash().equals(tx.getHash())) {
        aTx.merge(tx);
        return;
      }
    }
    txs.add(tx);
  }
  
  public static byte[] mapToBinary(Map<String, Object> map) {
    return jsonToBinaryJni(JsonUtils.serialize(map));
  }
  
  public static Map<String, Object> binaryToMap(byte[] bin) {
    return JsonUtils.deserialize(binaryToJsonJni(bin), new TypeReference<Map<String, Object>>(){});
  }
  
  @SuppressWarnings("unchecked")
  public static Map<String, Object> binaryBlocksToMap(byte[] binBlocks) {
    
    // convert binary blocks to json then to map
    Map<String, Object> map = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, binaryBlocksToJsonJni(binBlocks), new TypeReference<Map<String, Object>>(){});
    
    // parse blocks to maps
    List<Map<String, Object>> blockMaps = new ArrayList<Map<String, Object>>();
    for (String blockStr : (List<String>) map.get("blocks")) {
      blockMaps.add(JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, blockStr, new TypeReference<Map<String, Object>>(){}));
    }
    map.put("blocks", blockMaps); // overwrite block strings
    
    // parse txs to maps, one array of txs per block
    List<List<Map<String, Object>>> allTxs = new ArrayList<List<Map<String, Object>>>();
    List<Object> rpcAllTxs = (List<Object>) map.get("txs");
    for (Object rpcTxs : rpcAllTxs) {
      if ("".equals(rpcTxs)) {
        allTxs.add(new ArrayList<Map<String, Object>>());
      } else {
        List<Map<String, Object>> txs = new ArrayList<Map<String, Object>>();
        allTxs.add(txs);
        for (String rpcTx : (List<String>) rpcTxs) {
          txs.add(JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, rpcTx.replaceFirst(",", "{") + "}", new TypeReference<Map<String, Object>>(){})); // modify tx string to proper json and parse // TODO: more efficient way than this json manipulation?
        }
      }
    }
    map.put("txs", allTxs); // overwrite tx strings

    // return map containing blocks and txs as maps
    return map;
  }
  
  /**
   * Log a message.
   *
   * @param level log level of the message
   * @param msg message to log
   */
  public static void log(int level, String msg) {
    GenUtils.assertTrue("Log level must be an integer >= 0", level >= 0);
    if (ZephyrUtils.LOG_LEVEL >= level) System.out.println(msg);
  }
  
  /**
   * Set the library's log level with 0 being least verbose.
   *
   * @param level - the library's log level
   */
  public static void setLogLevel(int level) {
    GenUtils.assertTrue("Log level must be an integer >= 0", level >= 0);
    ZephyrUtils.LOG_LEVEL = level;
    if (JNI_LOADED) setLogLevelJni(level);
  }
  
  /**
   * Get the library's log level.
   *
   * @return the library's log level
   */
  public static int getLogLevel() {
    return ZephyrUtils.LOG_LEVEL;
  }
  
  /**
   * Initialize JNI logging.
   * 
   * @param path the path to write logs to
   * @param console specifies whether or not to write to the console
   */
  public static void configureNativeLogging(String path, boolean console) {
    configureLoggingJni(path, console);
  }
  
  /**
   * Convert coins to atomic units.
   * 
   * @param amount amount in coins to convert to atomic units
   * @return amount in atomic units
   */
  public static BigInteger toAtomicUnits(double amount) {
    double decimalDivisor = 1;
    String amountStr = "" + amount;
    int decimalIdx = amountStr.indexOf('.');
    if (decimalIdx > -1) {
      decimalDivisor = Math.pow(10, amountStr.length() - decimalIdx - 1);
      amountStr = amountStr.substring(0, decimalIdx) + amountStr.substring(decimalIdx + 1);
    }
    return new BigInteger(amountStr).multiply(BigInteger.valueOf(ZephyrUtils.AU_PER_COIN)).divide(BigInteger.valueOf((long) decimalDivisor));
  }
  
  /**
   * Convert atomic units to coins.
   * 
   * @param amountAtomicUnits amount in atomic units to convert to coins
   * @return amount in coins
   */
  public static double fromAtomicUnits(BigInteger amountAtomicUnits) {
    BigInteger[] quotientAndRemainder = amountAtomicUnits.divideAndRemainder(BigInteger.valueOf(AU_PER_COIN));
    return quotientAndRemainder[0].doubleValue() + quotientAndRemainder[1].doubleValue() / ZephyrUtils.AU_PER_COIN;
  }
  
  // ---------------------------- NATIVE BINDINGS -----------------------------
  
  private native static String getIntegratedAddressJni(int networkType, String standardAddress, String paymentId);
  private native static void validateAddressJni(String address, int networkType);
  private native static byte[] jsonToBinaryJni(String json);
  private native static String binaryToJsonJni(byte[] bin);
  private native static String binaryBlocksToJsonJni(byte[] binBlocks);
  private native static void configureLoggingJni(String path, boolean console);
  private native static void setLogLevelJni(int level);
  
  // ---------------------------- PRIVATE HELPERS -----------------------------
  
  private static boolean isHex64(String str) {
    return str != null && str.length() == 64 && GenUtils.isHex(str);
  }

  private static boolean isValidAddressHash(String decodedAddrStr) {
    String checksumCheck = decodedAddrStr.substring(decodedAddrStr.length() - 8);
    String withoutChecksumStr = decodedAddrStr.substring(0, decodedAddrStr.length() - 8);
    byte[] withoutChecksumBytes = hexToBin(withoutChecksumStr);

    String encodedStr = GenUtils.binToHex(Keccak256.hash(withoutChecksumBytes));

    String hashChecksum = encodedStr.substring(0, 8);
    return hashChecksum != null && hashChecksum.equals(checksumCheck);
  }
  
  private static String decodeAddressToHex(String address) {
    int[] bin = new int[address.length()];
    for (int i = 0; i < address.length(); i++) {
      bin[i] = address.codePointAt(i);
    }

    int fullBlockCount = (int) Math.floor(bin.length / FULL_ENCODED_BLOCK_SIZE);
    int lastBlockSize = bin.length % FULL_ENCODED_BLOCK_SIZE;
    Integer lastBlockDecodedSize = ENCODED_BLOCK_SIZE.get(lastBlockSize);
    if (lastBlockDecodedSize == null) throw new IllegalArgumentException("Invalid encoded length: " + address.length());

    int dataSize = fullBlockCount * FULL_BLOCK_SIZE + lastBlockDecodedSize;
    int[] data = new int[dataSize];
    for (int i = 0; i < fullBlockCount; i++) {
      data = decodeBlock(GenUtils.subarray(bin, i * FULL_ENCODED_BLOCK_SIZE, i * FULL_ENCODED_BLOCK_SIZE + FULL_ENCODED_BLOCK_SIZE), data, i * FULL_BLOCK_SIZE);
    }
    if (lastBlockSize > 0) {
      int[] subarray = GenUtils.subarray(bin, fullBlockCount * FULL_ENCODED_BLOCK_SIZE, fullBlockCount * FULL_ENCODED_BLOCK_SIZE + lastBlockSize);
      data = decodeBlock(subarray, data, fullBlockCount * FULL_BLOCK_SIZE);
    }

    return binToHex(data);
  }

  private static int[] decodeBlock(int[] data, int[] buf, int index) {

    if (data.length < 1 || data.length > FULL_ENCODED_BLOCK_SIZE) {
      throw new IllegalArgumentException("Invalid block length: " + data.length);
    }

    int resSize = ENCODED_BLOCK_SIZE.get(data.length);
    if (resSize <= 0) {
      throw new IllegalArgumentException("Invalid block size");
    }
    BigDecimal resNum = BigDecimal.ZERO;
    BigDecimal order = BigDecimal.ONE;
    for (int i = data.length - 1; i >= 0; i--) {
      int digit = ALPHABET.indexOf(data[i]);
      if (digit < 0) {
        throw new IllegalArgumentException("Invalid symbol");
      }
      BigDecimal product = order.multiply(new BigDecimal(digit)).add(resNum);
      // if product > UINT64_MAX
      if (product.compareTo(UINT64_MAX) > 0) {
        throw new IllegalArgumentException("Overflow");
      }
      resNum = product;
      order = order.multiply(ALPHABET_SIZE);
    }
    if (resSize < FULL_BLOCK_SIZE && (new BigDecimal(2).pow(8 * resSize).compareTo(resNum) <= 0)) {
      throw new IllegalArgumentException("Overflow 2");
    }

    int[] tmpBuf = uint64To8be(resNum, resSize);
    for (int j = 0; j < tmpBuf.length; j++) {
      buf[j + index] = tmpBuf[j];
    }

    return buf;
  }

  private static int[] uint64To8be(BigDecimal num, int size) {
    int[] res = new int[size];
    if (size < 1 || size > 8) {
      throw new RuntimeException("Invalid input length");
    }
    BigDecimal twopow8 = new BigDecimal(2).pow(8);
    for (int i = size - 1; i >= 0; i--) {
      res[i] = num.remainder(twopow8).intValue();
      num = num.divide(twopow8);
    }
    return res;
  }

  private static byte[] hexToBin(String hexStr) {
    if (hexStr == null || hexStr.length() % 2 != 0) {
      return null;
    }
    byte[] res = new byte[hexStr.length() / 2];
    for (int i = 0; i < hexStr.length() / 2; ++i) {
      res[i] = (byte)Integer.parseInt(hexStr.substring(i * 2, i * 2 + 2), 16);
    }
    return res;
  }

  private static String binToHex(int[] data) {
    StringBuilder builder = new StringBuilder();
    for (int i : data) {
      builder.append(String.format("%02x", i));
    }
    return builder.toString();
  }
}
