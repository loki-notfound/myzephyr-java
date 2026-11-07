package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import zephyr.common.NativeAddressCheck;
import zephyr.common.ZephyrError;
import zephyr.common.ZephyrUtils;
import zephyr.lib.utils.GenUtils;
import zephyr.lib.utils.Keccak256;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.wallet.ZephyrWallet;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrAddressType;
import zephyr.wallet.model.ZephyrDecodedAddress;
import zephyr.wallet.model.ZephyrIntegratedAddress;
import zephyr.wallet.model.ZephyrWalletConfig;
import org.junit.jupiter.api.Test;
import utils.TestUtils;

/**
 * Tests static Zephyr utilities.
 */
public class TestZephyrUtils {
  
  // Zephyr test vectors: fresh throwaway wallets of zephyr-wallet-cli v2.3.0 (67c5f53b8), generated
  // offline on 2026-11-06; subaddresses are account 0 index 1, integrated addresses embed the
  // payment id 0123456789abcdef and derive from the primary address of the same network
  private static final String MAINNET_PRIMARY = "ZEPHYR3RDMKbaFgugwWnfL9zNc3Avpx8EBNJvwbmkN7LiGHMVbU2YkiSMk2b8Pq9myVT3deTgidge9ANsW3vUz5wP3omiUv2y5J4V";
  private static final String MAINNET_SUBADDRESS = "ZEPHsCuTFRYfuPkNxAbu8f6RQkQtzwMv5GD6uzRGq7WZBF851LqPc4b2xXx6Lf2p9sWLQcCFJsc9FA9fEtJgWki365995PABC5J";
  private static final String MAINNET_INTEGRATED = "ZEPHiizqWKqgGviYBXsgfaazPfzAV4mnSUyTZuKnp6Fi5gHPbbTpCS9GKZRjYywEwgRCRhdduGzpQHbGqrDXKdQU87SyuC3JYEnCcJz1bwwh6Q6NB";
  private static final String TESTNET_PRIMARY = "ZPHTjeJQnpV8zJpwVBXoVE5RHkBDf4YBJSsvaj9mwn5d8MrASVrZCdzhwjroh6X2tkT79XyJVXnxVifz6HvhLZcA2QwG2Hx6Vug";
  private static final String TESTNET_SUBADDRESS = "ZPHtsBZjCLCBNbvC9fgdPBjDALDriEQsygi9sv5sqV75MsmXRJqhaxVG8P7tb37M7ZHFUWceyiUFXKgssD12kbH8JxbKFVaNS3B";
  private static final String TESTNET_INTEGRATED = "ZPHTiizJsGgXLVmUGP8w122csXQrHM7HMgpGCgTmGzRmguMyRFKpbVoJwP9hUN8c7TLP3SX474FRm3WBFUBjWepDEydD5PsGuRkCcJz1bwweTK3Zp";
  private static final String STAGENET_PRIMARY = "ZPHSjp3QtHUQxfT4k9bfCBgye5EVdTGxicgvwSwysedp3AU4UarjWufgafz9n9FesB2C6FYrQCAXVJfRiKCK7UK3PUnUEmrWXXE";
  private static final String STAGENET_SUBADDRESS = "ZPHss9WbJq5YK7Ji3WPFdbNqqtQTwtmAK3NkxVZuNAxvS1vSQbPQKDnC2wWWf55PKHGot7zwf73Fo2R9TQ88UAYaZLEvc9BnsAp";
  private static final String STAGENET_INTEGRATED = "ZPHSii2S3xqjkFNb84Y9MeEYA8UvnTkMeb6SWzwHyaAJde4jku2mc5oNu8ZNpq8ZP6eokg3DLefKdXBEB732nUd8cDMeTcLYUjEJKSspVLvsNX2A";

  // network type, address type, address
  private static final Object[][] ZEPHYR_ADDRESSES = {
    { ZephyrNetworkType.MAINNET, ZephyrAddressType.PRIMARY_ADDRESS, MAINNET_PRIMARY },
    { ZephyrNetworkType.MAINNET, ZephyrAddressType.SUBADDRESS, MAINNET_SUBADDRESS },
    { ZephyrNetworkType.MAINNET, ZephyrAddressType.INTEGRATED_ADDRESS, MAINNET_INTEGRATED },
    { ZephyrNetworkType.TESTNET, ZephyrAddressType.PRIMARY_ADDRESS, TESTNET_PRIMARY },
    { ZephyrNetworkType.TESTNET, ZephyrAddressType.SUBADDRESS, TESTNET_SUBADDRESS },
    { ZephyrNetworkType.TESTNET, ZephyrAddressType.INTEGRATED_ADDRESS, TESTNET_INTEGRATED },
    { ZephyrNetworkType.STAGENET, ZephyrAddressType.PRIMARY_ADDRESS, STAGENET_PRIMARY },
    { ZephyrNetworkType.STAGENET, ZephyrAddressType.SUBADDRESS, STAGENET_SUBADDRESS },
    { ZephyrNetworkType.STAGENET, ZephyrAddressType.INTEGRATED_ADDRESS, STAGENET_INTEGRATED },
  };

  // Monero addresses of upstream's tests, which must not validate on any Zephyr network
  private static final String[] MONERO_ADDRESSES = {
    "58qRVVjZ4KxMX57TH6yWqGcH5AswvZZS494hWHcHPt6cDkP7V8AqxFhi3RKXZueVRgUnk8niQGHSpY5Bm9DjuWn16GDKXpF",
    "7B9w2xieXjhDumgPX39h1CAYELpsZ7Pe8Wqtr3pVL9jJ5gGDqgxjWt55gTYUCAuhahhM85ajEp6VbQfLDPETt4oT2ZRXa6n",
    "42U9v3qs5CjZEePHBZHwuSckQXebuZu299NSmVEmQ41YJZQhKcPyujyMSzpDH4VMMVSBo3U3b54JaNvQLwAjqDhKS3rvM3L",
    "48ZxX3Y2y5s4nJ8fdz2w65TrTEp9PRsv5J8iHSShkHQcE2V31FhnWptioNst1K9oeDY4KpWZ7v8V2BZNVa4Wdky89iqmPz2",
    "48W972Fx1SQMCHVKENnPpM7tRcL5oWMgpMCqQDbhH8UrjDFg2H9i5AQWXuU1qacJgUUCVLTsgDmZKXGz1vPLXY8QB5ypYqG",
    "4CApvrfMgUFZEePHBZHwuSckQXebuZu299NSmVEmQ41YJZQhKcPyujyMSzpDH4VMMVSBo3U3b54JaNvQLwAjqDhKeGLQ9vfRBRKFKnBtVH",
    "4JGdXrMXaMP4nJ8fdz2w65TrTEp9PRsv5J8iHSShkHQcE2V31FhnWptioNst1K9oeDY4KpWZ7v8V2BZNVa4Wdky8DvDyXvDZXvE9jTQwom",
    "4JCp7q5SchvMCHVKENnPpM7tRcL5oWMgpMCqQDbhH8UrjDFg2H9i5AQWXuU1qacJgUUCVLTsgDmZKXGz1vPLXY8QFySJXARQWju8AuRN2z",
    "891TQPrWshJVpnBR4ZMhHiHpLx1PUnMqa3ccV5TJFBbqcJa3DWhjBh2QByCv3Su7WDPTGMHmCKkiVFN2fyGJKwbM1t6G7Ea",
    "88fyq3t8Gxn1QWMG189EufHtMHXZXkfJtJKFJXqeA4GpSiuyfjVwVyp47PeQJnD7Tc8iK8TDvvhcmEmfh8nx7Va2ToP8wAo",
    "88hnoBiX3TPjbFaQE8RxgyBcf3DtMKZWWQMoArBjQfn37JJwtm568mPX6ipcCuGKDnLCzgjmpLSqce4aBDyapJJAFtNxUMb",
    "9tUBnNCkC3UKGygHCwYvAB1FscpjUuq5e9MYJd2rXuiiTjjfVeSVjnbSG5VTnJgBgy9Y7GTLfxpZNMUwNZjGfdFr1z79eV1",
    "9xZmQa1kYakGoHcfXeBgcsLf622NCpChcACwXxfdgY9uAa9hXSPCV9cLvUsAShfDcFKDdPzCNJ1n5cFGKw5GVM722pjuGPd",
    "A2TXS6QFQ4wEsp8U7C2Y4B7wBtiML8aDG7mdCbRvDQmRaRNj1YSSgJE46fSzUkwgpMUCXFqscvrQuN7oKpP6eDyQ7XuYsuf",
    "A4AroB2EoJzKGygHCwYvAB1FscpjUuq5e9MYJd2rXuiiTjjfVeSVjnbSG5VTnJgBgy9Y7GTLfxpZNMUwNZjGfdFr2QY5Ba2aHhTEdQa2ra",
    "A8GSRNqF9rGGoHcfXeBgcsLf622NCpChcACwXxfdgY9uAa9hXSPCV9cLvUsAShfDcFKDdPzCNJ1n5cFGKw5GVM723iPoCEF1Fs9BcPYxTW",
    "ACACSuDk1LTEsp8U7C2Y4B7wBtiML8aDG7mdCbRvDQmRaRNj1YSSgJE46fSzUkwgpMUCXFqscvrQuN7oKpP6eDyQAdgDoT3UnMYKQz7SHC",
    "BgnKzHPJQDcg7xiP7bMN9MfPv9Z8ciT71iEMYnCdgBRBFETWgu9nKTr8fnzyGfU9h9gyNA8SFzYYzHfTS9KhqytSU943Nu1",
    "BZwiuKkoNP59zgPHTxpNw3PM4DW2xiAVQJWqfFRrGyeZ7afVdQqoiJg3E2dDL3Ja8BV4ov2LEoHx9UjzF3W4ihPBSZvWwTx",
    "Bhf1DEYrentcehUvNreLK5gxosnC2VStMXNCCs163RTxQq4jxFYvpw7LrQFmrMwWW2KsXLhMRtyho6Lq11ci3Fb246bxYmi",
    "5B8s3obCY2ETeQB3GNAGPK2zRGen5UeW1WzegSizVsmf6z5NvM2GLoN6zzk1vHyzGAAfA8pGhuYAeCFZjHAp59jRVQkunGS",
    "57VfotUbSZLG82UkKhWXDjS5ZEK9ZCDcmjdk4gpVq2fbKdEgwRCFrGTLZ2MMdSHphRWJDWVBi5qS8T7dz13JTCWtC228zyn",
    "52FysgWJYmAG73QUQZRULJj2Dv2C2mceUMB5zHqNzMn8WBtfPWQrSUFSQUKTX9r7bUMmVSGbrau976xYLynR8jTWLdA7rfp",
    "5LqY4cQh9HkTeQB3GNAGPK2zRGen5UeW1WzegSizVsmf6z5NvM2GLoN6zzk1vHyzGAAfA8pGhuYAeCFZjHAp59jRj6LZRFrjuGK8Whthg2",
    "5HCLphJ63prG82UkKhWXDjS5ZEK9ZCDcmjdk4gpVq2fbKdEgwRCFrGTLZ2MMdSHphRWJDWVBi5qS8T7dz13JTCWtHETX8zcUhDjVKcynf6",
    "5BxetVKoA2gG73QUQZRULJj2Dv2C2mceUMB5zHqNzMn8WBtfPWQrSUFSQUKTX9r7bUMmVSGbrau976xYLynR8jTWVwQwpHNg5fCLgtA2Dv",
    "778B5D2JmMh5TJVWFbygJR15dvio5Z5B24hfSrWDzeroM8j8Lqc9sMoFE6324xg2ReaAZqHJkgfGFRugRmYHugHZ4f17Gxo",
    "73U97wGEH9RCVUf6bopo45jSgoqjMzz4mTUsvWs5EusmYAmFcBYFm7wKMVmgtVKCBhMQqXrcMbHvwck2md63jMZSFJxUhQ2",
    "747wPpaPKrjDPZrF48jAfz9pRRUHLMCWfYu2UanP4ZfTG8NrmYrSEWNW8gYoadU8hTiwBjV14e6DLaC5xfhyEpX5154aMm6",
  };

  // Can get integrated addresses
  @Test
  public void testGetIntegratedAddresses() {
    String primaryAddress = MAINNET_PRIMARY;
    String subaddress = MAINNET_SUBADDRESS;
    String paymentId = "03284e41c342f036";
    ZephyrNetworkType networkType = ZephyrNetworkType.MAINNET;
    int integratedAddressLength = MAINNET_INTEGRATED.length();
    
    // get integrated address with randomly generated payment id
    ZephyrIntegratedAddress integratedAddress = ZephyrUtils.getIntegratedAddress(networkType, primaryAddress, null);
    assertEquals(primaryAddress, integratedAddress.getStandardAddress());
    assertEquals(16, integratedAddress.getPaymentId().length());
    assertEquals(integratedAddressLength, integratedAddress.getIntegratedAddress().length());
    
    // get integrated address with specific payment id
    integratedAddress = ZephyrUtils.getIntegratedAddress(networkType, primaryAddress, paymentId);
    assertEquals(primaryAddress, integratedAddress.getStandardAddress());
    assertEquals(paymentId, integratedAddress.getPaymentId());
    assertEquals(integratedAddressLength, integratedAddress.getIntegratedAddress().length());
    
    // get integrated address with subaddress
    integratedAddress = ZephyrUtils.getIntegratedAddress(networkType, subaddress, paymentId);
    assertEquals(subaddress, integratedAddress.getStandardAddress());
    assertEquals(paymentId, integratedAddress.getPaymentId());
    assertEquals(integratedAddressLength, integratedAddress.getIntegratedAddress().length());
    
    // get integrated address with invalid payment id
    try {
      ZephyrUtils.getIntegratedAddress(networkType, primaryAddress, "123");
      fail("Getting integrated address with invalid payment id should have failed");
    } catch (ZephyrError err) {
      assertEquals("Invalid payment id", err.getMessage());
    }
  }
  
  // Can decode Zephyr addresses of every network and type
  @Test
  public void testDecodeZephyrAddresses() {
    for (Object[] vector : ZEPHYR_ADDRESSES) {
      ZephyrDecodedAddress decoded = ZephyrUtils.decodeAddress((String) vector[2]);
      assertEquals(vector[2], decoded.getAddress());
      assertEquals(vector[0], decoded.getNetworkType(), (String) vector[2]);
      assertEquals(vector[1], decoded.getAddressType(), (String) vector[2]);
    }
  }
  
  // Rejects Monero addresses and broken Zephyr addresses
  @Test
  public void testRejectForeignAndBrokenAddresses() {
    
    // Monero addresses are invalid on every network
    for (String moneroAddress : MONERO_ADDRESSES) {
      for (ZephyrNetworkType networkType : ZephyrNetworkType.values()) {
        assertFalse(ZephyrUtils.isValidAddress(moneroAddress, networkType), moneroAddress + " on " + networkType);
      }
    }
    
    // changed last character (checksum)
    testInvalidAddress(changeChar(MAINNET_PRIMARY, MAINNET_PRIMARY.length() - 1), ZephyrNetworkType.MAINNET);
    
    // mainnet address on testnet
    testInvalidAddress(MAINNET_PRIMARY, ZephyrNetworkType.TESTNET);
    
    // truncated by one character: a ZephyrError for every vector
    for (Object[] vector : ZEPHYR_ADDRESSES) {
      String address = (String) vector[2];
      testInvalidAddress(address.substring(0, address.length() - 1), (ZephyrNetworkType) vector[0]);
    }
    
    // short strings and block overflows: a ZephyrError, not a decoder exception
    for (String malformed : new String[] { "11", "111", "11111", "zzzzzzzzzzz", "11111111111zz" }) {
      testInvalidAddress(malformed, ZephyrNetworkType.MAINNET);
    }
  }
  
  // The native core agrees with the pure-Java decoder on the Zephyr vectors
  @Test
  public void testNativeAddressCrossCheck() {
    for (Object[] vector : ZEPHYR_ADDRESSES) {
      String address = (String) vector[2];
      ZephyrNetworkType networkType = (ZephyrNetworkType) vector[0];
      ZephyrNetworkType otherNetworkType = ZephyrNetworkType.values()[(networkType.ordinal() + 1) % ZephyrNetworkType.values().length];
      NativeAddressCheck.validate(address, networkType);
      assertThrows(ZephyrError.class, () -> NativeAddressCheck.validate(address, otherNetworkType), address + " on " + otherNetworkType);
    }
    assertThrows(ZephyrError.class, () -> NativeAddressCheck.validate(changeChar(MAINNET_PRIMARY, MAINNET_PRIMARY.length() - 1), ZephyrNetworkType.MAINNET));
  }
  
  // The base58 decoder handles every last-block size without a NullPointerException
  @Test
  public void testBase58LastBlockSizes() {
    
    // last blocks of 9 and 10 characters (6 and 7 bytes) decode; the hash check then rejects them
    for (int length : new int[] { 20, 21 }) {
      try {
        ZephyrUtils.validateAddress(repeat('1', length), ZephyrNetworkType.MAINNET);
        fail("Should have thrown exception");
      } catch (ZephyrError e) {
        assertEquals("Address has invalid hash", e.getMessage());
      }
    }
    
    // last blocks of 1, 4 and 8 characters are invalid encodings
    for (int length : new int[] { 12, 15, 19 }) {
      try {
        ZephyrUtils.validateAddress(repeat('1', length), ZephyrNetworkType.MAINNET);
        fail("Should have thrown exception");
      } catch (ZephyrError e) {
        assertEquals("Address has invalid encoding", e.getMessage());
      }
    }
  }
  
  // Can serialize heights with small numbers
  @Test
  public void testSerializeHeightsSmall() {
    Map<String, Object> map = new HashMap<String, Object>();
    map.put("heights", Arrays.asList(111, 222, 333));
    byte[] binary = ZephyrUtils.mapToBinary(map);
    assertTrue(binary.length > 0);
    //for (int i = 0; i < binary.length; i++) System.out.println(binary[i]);
    Map<String, Object> map2 = ZephyrUtils.binaryToMap(binary);
    assertEquals(map, map2);
  };
  
  // Can serialize heights with big numbers
  @Test
  public void testSerializeHeightsBig() {
    Map<String, Object> map = new HashMap<String, Object>();
    map.put("heights", Arrays.asList(123456, 1234567, 870987));
    byte[] binary = ZephyrUtils.mapToBinary(map);
    assertTrue(binary.length > 0);
    Map<String, Object> map2 = ZephyrUtils.binaryToMap(binary);
    assertEquals(map, map2);
  }
  
  // Can serialize map with text
  @Test
  public void testSerializeTextShort() {
    Map<String, Object> map = new HashMap<String, Object>();
    map.put("msg", "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
    byte[] binary = ZephyrUtils.mapToBinary(map);
    assertTrue(binary.length > 0);
    Map<String, Object> map2 = ZephyrUtils.binaryToMap(binary);
    assertEquals(map, map2);
  }
  
  // Can serialize json with long text
  @Test
  public void testSerializeTextLong() {
    Map<String, Object> map = new HashMap<String, Object>();
    map.put("msg", "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "Hello there my good man lets make a nice long text to test with lots of exclamation marks!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n");
    byte[] binary = ZephyrUtils.mapToBinary(map);
    assertTrue(binary.length > 0);
    Map<String, Object> map2 = ZephyrUtils.binaryToMap(binary);
    assertEquals(map, map2);
  }
  
  // Can validate addresses
  @Test
  public void testAddressValidation() {
    
    // test mainnet address validation
    assertTrue(ZephyrUtils.isValidAddress(MAINNET_PRIMARY, ZephyrNetworkType.MAINNET));
    ZephyrUtils.validateAddress(MAINNET_PRIMARY, ZephyrNetworkType.MAINNET);
    ZephyrUtils.validateAddress(MAINNET_INTEGRATED, ZephyrNetworkType.MAINNET);
    ZephyrUtils.validateAddress(MAINNET_SUBADDRESS, ZephyrNetworkType.MAINNET);
    
    // test testnet address validation
    ZephyrUtils.validateAddress(TESTNET_PRIMARY, ZephyrNetworkType.TESTNET);
    ZephyrUtils.validateAddress(TESTNET_INTEGRATED, ZephyrNetworkType.TESTNET);
    ZephyrUtils.validateAddress(TESTNET_SUBADDRESS, ZephyrNetworkType.TESTNET);
    
    // test stagenet address validation
    ZephyrUtils.validateAddress(STAGENET_PRIMARY, ZephyrNetworkType.STAGENET);
    ZephyrUtils.validateAddress(STAGENET_INTEGRATED, ZephyrNetworkType.STAGENET);
    ZephyrUtils.validateAddress(STAGENET_SUBADDRESS, ZephyrNetworkType.STAGENET);
    
    // test invalid addresses on mainnet
    testInvalidAddress(null, ZephyrNetworkType.MAINNET);
    testInvalidAddress("", ZephyrNetworkType.MAINNET);
    testInvalidAddress(changeChar(MAINNET_PRIMARY, 1), ZephyrNetworkType.MAINNET);
    testInvalidAddress(changeChar(MAINNET_INTEGRATED, 1), ZephyrNetworkType.MAINNET);
    testInvalidAddress(changeChar(MAINNET_SUBADDRESS, 1), ZephyrNetworkType.MAINNET);
    
    // test invalid addresses on testnet
    testInvalidAddress(null, ZephyrNetworkType.TESTNET);
    testInvalidAddress("", ZephyrNetworkType.TESTNET);
    testInvalidAddress(changeChar(TESTNET_PRIMARY, 1), ZephyrNetworkType.TESTNET);
    testInvalidAddress(changeChar(TESTNET_INTEGRATED, 1), ZephyrNetworkType.TESTNET);
    testInvalidAddress(changeChar(TESTNET_SUBADDRESS, 1), ZephyrNetworkType.TESTNET);
    
    // test invalid addresses on stagenet
    testInvalidAddress(null, ZephyrNetworkType.STAGENET);
    testInvalidAddress("", ZephyrNetworkType.STAGENET);
    testInvalidAddress(changeChar(STAGENET_PRIMARY, 1), ZephyrNetworkType.STAGENET);
    testInvalidAddress(changeChar(STAGENET_INTEGRATED, 1), ZephyrNetworkType.STAGENET);
    testInvalidAddress(changeChar(STAGENET_SUBADDRESS, 1), ZephyrNetworkType.STAGENET);
  }
  
  // Keccak-256 with the original padding (not SHA3-256), as the address checksum uses it; the
  // address tests above exercise it through the checksum of every vector
  @Test
  public void testKeccak256Empty() {
    assertEquals("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470", GenUtils.binToHex(Keccak256.hash(new byte[0])));
  }
  
  @Test
  public void testKeccak256Abc() {
    assertEquals("4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45", GenUtils.binToHex(Keccak256.hash("abc".getBytes(StandardCharsets.US_ASCII))));
  }
  
  // Can validate keys
  @Test
  public void testKeyValidation() {

    // test private view key validation
    assertTrue(ZephyrUtils.isValidPrivateViewKey("86cf351d10894769feba29b9e201e12fb100b85bb52fc5825c864eef55c5840d"));
    testInvalidPrivateViewKey("");
    testInvalidPrivateViewKey(null);
    testInvalidPrivateViewKey("5B8s3obCY2ETeQB3GNAGPK2zRGen5UeW1WzegSizVsmf6z5NvM2GLoN6zzk1vHyzGAAfA8pGhuYAeCFZjHAp59jRVQkunGS");
    
    // test public view key validation
    assertTrue(ZephyrUtils.isValidPublicViewKey("99873d76ca874ff1aad676b835dd303abcb21c9911ca8a3d9130abc4544d8a0a"));
    testInvalidPublicViewKey("");
    testInvalidPublicViewKey(null);
    testInvalidPublicViewKey("z86cf351d10894769feba29b9e201e12fb100b85bb52fc5825c864eef55c5840d");
    
    // test private spend key validation
    assertTrue(ZephyrUtils.isValidPrivateSpendKey("e9ba887e93620ef9fafdfe0c6d3022949f1c5713cbd9ef631f18a0fb00421dee"));
    testInvalidPrivateSpendKey("");
    testInvalidPrivateSpendKey(null);
    testInvalidPrivateSpendKey("z86cf351d10894769feba29b9e201e12fb100b85bb52fc5825c864eef55c5840d");
    
    // test public spend key validation
    assertTrue(ZephyrUtils.isValidPublicSpendKey("3e48df9e9d8038dbf6f5382fac2becd8686273cda5bd87187e45dca7ec5af37b"));
    testInvalidPublicSpendKey("");
    testInvalidPublicSpendKey(null);
    testInvalidPublicSpendKey("z86cf351d10894769feba29b9e201e12fb100b85bb52fc5825c864eef55c5840d");
  }
  
  // Can convert between coins and atomic units
  @Test
  public void testAtomicUnitConversion() {
    assertEquals(new BigInteger("1000000000000").toString(), ZephyrUtils.toAtomicUnits(1).toString());
    assertEquals(1, ZephyrUtils.fromAtomicUnits(new BigInteger("1000000000000")));
    assertEquals(new BigInteger("1000000000").toString(), ZephyrUtils.toAtomicUnits(0.001).toString());
    assertEquals(.001, ZephyrUtils.fromAtomicUnits(new BigInteger("1000000000")));
    assertEquals(new BigInteger("250000000000").toString(), ZephyrUtils.toAtomicUnits(.25).toString());
    assertEquals(.25, ZephyrUtils.fromAtomicUnits(new BigInteger("250000000000")));
    assertEquals(new BigInteger("1250000000000").toString(), ZephyrUtils.toAtomicUnits(1.25).toString());
    assertEquals(1.25, ZephyrUtils.fromAtomicUnits(new BigInteger("1250000000000")));
  };
  
  // ---------------------------- PRIVATE HELPERS -----------------------------
  
  // replaces the character at the given index with another base58 character
  private static String changeChar(String address, int index) {
    char replacement = address.charAt(index) == '1' ? '2' : '1';
    return address.substring(0, index) + replacement + address.substring(index + 1);
  }
  
  private static String repeat(char c, int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) sb.append(c);
    return sb.toString();
  }
  
  private static void testInvalidAddress(String address, ZephyrNetworkType networkType) {
    assertFalse(ZephyrUtils.isValidAddress(address, networkType));
    try {
      ZephyrUtils.validateAddress(address, networkType);
      fail("Should have thrown exception");
    } catch (ZephyrError e) {
      assertFalse(e.getMessage().isEmpty());
    }
  }
  
  private static void testInvalidPrivateViewKey(String privateViewKey) {
    assertFalse(ZephyrUtils.isValidPrivateViewKey(privateViewKey));
    try {
      ZephyrUtils.validatePrivateViewKey(privateViewKey);
      fail("Should have thrown exception");
    } catch (ZephyrError e) {
      assertFalse(e.getMessage().isEmpty());
    }
  }
  
  private static void testInvalidPublicViewKey(String publicViewKey) {
    assertFalse(ZephyrUtils.isValidPublicViewKey(publicViewKey));
    try {
      ZephyrUtils.validatePublicViewKey(publicViewKey);
      fail("Should have thrown exception");
    } catch (ZephyrError e) {
      assertFalse(e.getMessage().isEmpty());
    }
  }
  
  private static void testInvalidPrivateSpendKey(String privateSpendKey) {
    try {
      assertFalse(ZephyrUtils.isValidPrivateSpendKey(privateSpendKey));
      ZephyrUtils.validatePrivateSpendKey(privateSpendKey);
      fail("Should have thrown exception");
    } catch (ZephyrError e) {
      assertFalse(e.getMessage().isEmpty());
    }
  }
  
  private static void testInvalidPublicSpendKey(String publicSpendKey) {
    assertFalse(ZephyrUtils.isValidPublicSpendKey(publicSpendKey));
    try {
      ZephyrUtils.validatePublicSpendKey(publicSpendKey);
      fail("Should have thrown exception");
    } catch (ZephyrError e) {
      assertFalse(e.getMessage().isEmpty());
    }
  }
  
  public static void main(String[] args) {
    printAddressValidationTestCode();
  }
  
  /**
   * Prints code to test address validation.
   */
  private static void printAddressValidationTestCode() {
    int repeat = 3;
    
    // test each network
    for (ZephyrNetworkType networkType : ZephyrNetworkType.values()) {
      
      // create test wallets
      List<ZephyrWallet> wallets = new ArrayList<ZephyrWallet>();
      for (int i = 0; i < repeat; i++) {
        wallets.add(ZephyrWalletFull.createWallet(new ZephyrWalletConfig().setPath(TestUtils.TEST_WALLETS_DIR + "/temp_" + UUID.randomUUID().toString() + "_" + i).setPassword(TestUtils.WALLET_PASSWORD).setNetworkType(networkType)));
      }
      
      // output primary address tests
      System.out.println("\n// test " + networkType.toString().toLowerCase() + " primary address validation");
      for (int i = 0; i < repeat; i++) {
        System.out.println("ZephyrUtils.validateAddress(\"" + wallets.get(i).getPrimaryAddress() + "\", ZephyrNetworkType." + networkType + ");");
      }
      
      // output integrated address tests
      System.out.println("\n// test " + networkType.toString().toLowerCase() + " integrated address validation");
      for (int i = 0; i < repeat; i++) {
        System.out.println("ZephyrUtils.validateAddress(\"" + wallets.get(i).getIntegratedAddress() + "\", ZephyrNetworkType." + networkType + ");");
      }
      
      // output subaddress tests
      System.out.println("\n// test " + networkType.toString().toLowerCase() + " subaddress validation");
      for (int i = 0; i < repeat; i++) {
        System.out.println("ZephyrUtils.validateAddress(\"" + wallets.get(i).getAddress(0, 1) + "\", ZephyrNetworkType." + networkType + ");");
      }
    }
  }
}
