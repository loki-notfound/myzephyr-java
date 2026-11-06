package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import zephyr.common.ZephyrError;
import zephyr.common.ZephyrUtils;
import zephyr.daemon.model.ZephyrNetworkType;
import zephyr.wallet.ZephyrWallet;
import zephyr.wallet.ZephyrWalletFull;
import zephyr.wallet.model.ZephyrIntegratedAddress;
import zephyr.wallet.model.ZephyrWalletConfig;
import org.junit.jupiter.api.Test;
import utils.TestUtils;

/**
 * Tests static Zephyr utilities.
 */
public class TestZephyrUtils {
  
  // Can get integrated addresses
  @Test
  public void testGetIntegratedAddresses() {
    String primaryAddress = "58qRVVjZ4KxMX57TH6yWqGcH5AswvZZS494hWHcHPt6cDkP7V8AqxFhi3RKXZueVRgUnk8niQGHSpY5Bm9DjuWn16GDKXpF";
    String subaddress = "7B9w2xieXjhDumgPX39h1CAYELpsZ7Pe8Wqtr3pVL9jJ5gGDqgxjWt55gTYUCAuhahhM85ajEp6VbQfLDPETt4oT2ZRXa6n";
    String paymentId = "03284e41c342f036";
    ZephyrNetworkType networkType = ZephyrNetworkType.STAGENET;
    
    // get integrated address with randomly generated payment id
    ZephyrIntegratedAddress integratedAddress = ZephyrUtils.getIntegratedAddress(networkType, primaryAddress, null);
    assertEquals(primaryAddress, integratedAddress.getStandardAddress());
    assertEquals(16, integratedAddress.getPaymentId().length());
    assertEquals(106, integratedAddress.getIntegratedAddress().length());
    
    // get integrated address with specific payment id
    integratedAddress = ZephyrUtils.getIntegratedAddress(networkType, primaryAddress, paymentId);
    assertEquals(primaryAddress, integratedAddress.getStandardAddress());
    assertEquals(paymentId, integratedAddress.getPaymentId());
    assertEquals(106, integratedAddress.getIntegratedAddress().length());
    
    // get integrated address with subaddress
    integratedAddress = ZephyrUtils.getIntegratedAddress(networkType, subaddress, paymentId);
    assertEquals(subaddress, integratedAddress.getStandardAddress());
    assertEquals(paymentId, integratedAddress.getPaymentId());
    assertEquals(106, integratedAddress.getIntegratedAddress().length());
    
    // get integrated address with invalid payment id
    try {
      ZephyrUtils.getIntegratedAddress(networkType, primaryAddress, "123");
      fail("Getting integrated address with invalid payment id should have failed");
    } catch (ZephyrError err) {
      assertEquals("Invalid payment id", err.getMessage());
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
    
    // test mainnet primary address validation
    assertTrue(ZephyrUtils.isValidAddress("42U9v3qs5CjZEePHBZHwuSckQXebuZu299NSmVEmQ41YJZQhKcPyujyMSzpDH4VMMVSBo3U3b54JaNvQLwAjqDhKS3rvM3L", ZephyrNetworkType.MAINNET));
    assertTrue(ZephyrUtils.isValidAddress("48ZxX3Y2y5s4nJ8fdz2w65TrTEp9PRsv5J8iHSShkHQcE2V31FhnWptioNst1K9oeDY4KpWZ7v8V2BZNVa4Wdky89iqmPz2", ZephyrNetworkType.MAINNET));
    assertTrue(ZephyrUtils.isValidAddress("48W972Fx1SQMCHVKENnPpM7tRcL5oWMgpMCqQDbhH8UrjDFg2H9i5AQWXuU1qacJgUUCVLTsgDmZKXGz1vPLXY8QB5ypYqG", ZephyrNetworkType.MAINNET));
    
    // test mainnet integrated address validation
    ZephyrUtils.validateAddress("4CApvrfMgUFZEePHBZHwuSckQXebuZu299NSmVEmQ41YJZQhKcPyujyMSzpDH4VMMVSBo3U3b54JaNvQLwAjqDhKeGLQ9vfRBRKFKnBtVH", ZephyrNetworkType.MAINNET);
    ZephyrUtils.validateAddress("4JGdXrMXaMP4nJ8fdz2w65TrTEp9PRsv5J8iHSShkHQcE2V31FhnWptioNst1K9oeDY4KpWZ7v8V2BZNVa4Wdky8DvDyXvDZXvE9jTQwom", ZephyrNetworkType.MAINNET);
    ZephyrUtils.validateAddress("4JCp7q5SchvMCHVKENnPpM7tRcL5oWMgpMCqQDbhH8UrjDFg2H9i5AQWXuU1qacJgUUCVLTsgDmZKXGz1vPLXY8QFySJXARQWju8AuRN2z", ZephyrNetworkType.MAINNET);
    
    // test mainnet subaddress validation
    ZephyrUtils.validateAddress("891TQPrWshJVpnBR4ZMhHiHpLx1PUnMqa3ccV5TJFBbqcJa3DWhjBh2QByCv3Su7WDPTGMHmCKkiVFN2fyGJKwbM1t6G7Ea", ZephyrNetworkType.MAINNET);
    ZephyrUtils.validateAddress("88fyq3t8Gxn1QWMG189EufHtMHXZXkfJtJKFJXqeA4GpSiuyfjVwVyp47PeQJnD7Tc8iK8TDvvhcmEmfh8nx7Va2ToP8wAo", ZephyrNetworkType.MAINNET);
    ZephyrUtils.validateAddress("88hnoBiX3TPjbFaQE8RxgyBcf3DtMKZWWQMoArBjQfn37JJwtm568mPX6ipcCuGKDnLCzgjmpLSqce4aBDyapJJAFtNxUMb", ZephyrNetworkType.MAINNET);
    
    // test testnet primary address validation
    ZephyrUtils.validateAddress("9tUBnNCkC3UKGygHCwYvAB1FscpjUuq5e9MYJd2rXuiiTjjfVeSVjnbSG5VTnJgBgy9Y7GTLfxpZNMUwNZjGfdFr1z79eV1", ZephyrNetworkType.TESTNET);
    ZephyrUtils.validateAddress("9xZmQa1kYakGoHcfXeBgcsLf622NCpChcACwXxfdgY9uAa9hXSPCV9cLvUsAShfDcFKDdPzCNJ1n5cFGKw5GVM722pjuGPd", ZephyrNetworkType.TESTNET);
    ZephyrUtils.validateAddress("A2TXS6QFQ4wEsp8U7C2Y4B7wBtiML8aDG7mdCbRvDQmRaRNj1YSSgJE46fSzUkwgpMUCXFqscvrQuN7oKpP6eDyQ7XuYsuf", ZephyrNetworkType.TESTNET);
    
    // test testnet integrated address validation
    ZephyrUtils.validateAddress("A4AroB2EoJzKGygHCwYvAB1FscpjUuq5e9MYJd2rXuiiTjjfVeSVjnbSG5VTnJgBgy9Y7GTLfxpZNMUwNZjGfdFr2QY5Ba2aHhTEdQa2ra", ZephyrNetworkType.TESTNET);
    ZephyrUtils.validateAddress("A8GSRNqF9rGGoHcfXeBgcsLf622NCpChcACwXxfdgY9uAa9hXSPCV9cLvUsAShfDcFKDdPzCNJ1n5cFGKw5GVM723iPoCEF1Fs9BcPYxTW", ZephyrNetworkType.TESTNET);
    ZephyrUtils.validateAddress("ACACSuDk1LTEsp8U7C2Y4B7wBtiML8aDG7mdCbRvDQmRaRNj1YSSgJE46fSzUkwgpMUCXFqscvrQuN7oKpP6eDyQAdgDoT3UnMYKQz7SHC", ZephyrNetworkType.TESTNET);
    
    // test testnet subaddress validation
    ZephyrUtils.validateAddress("BgnKzHPJQDcg7xiP7bMN9MfPv9Z8ciT71iEMYnCdgBRBFETWgu9nKTr8fnzyGfU9h9gyNA8SFzYYzHfTS9KhqytSU943Nu1", ZephyrNetworkType.TESTNET);
    ZephyrUtils.validateAddress("BZwiuKkoNP59zgPHTxpNw3PM4DW2xiAVQJWqfFRrGyeZ7afVdQqoiJg3E2dDL3Ja8BV4ov2LEoHx9UjzF3W4ihPBSZvWwTx", ZephyrNetworkType.TESTNET);
    ZephyrUtils.validateAddress("Bhf1DEYrentcehUvNreLK5gxosnC2VStMXNCCs163RTxQq4jxFYvpw7LrQFmrMwWW2KsXLhMRtyho6Lq11ci3Fb246bxYmi", ZephyrNetworkType.TESTNET);
    
    // test stagenet primary address validation
    ZephyrUtils.validateAddress("5B8s3obCY2ETeQB3GNAGPK2zRGen5UeW1WzegSizVsmf6z5NvM2GLoN6zzk1vHyzGAAfA8pGhuYAeCFZjHAp59jRVQkunGS", ZephyrNetworkType.STAGENET);
    ZephyrUtils.validateAddress("57VfotUbSZLG82UkKhWXDjS5ZEK9ZCDcmjdk4gpVq2fbKdEgwRCFrGTLZ2MMdSHphRWJDWVBi5qS8T7dz13JTCWtC228zyn", ZephyrNetworkType.STAGENET);
    ZephyrUtils.validateAddress("52FysgWJYmAG73QUQZRULJj2Dv2C2mceUMB5zHqNzMn8WBtfPWQrSUFSQUKTX9r7bUMmVSGbrau976xYLynR8jTWLdA7rfp", ZephyrNetworkType.STAGENET);
    
    // test stagenet integrated address validation
    ZephyrUtils.validateAddress("5LqY4cQh9HkTeQB3GNAGPK2zRGen5UeW1WzegSizVsmf6z5NvM2GLoN6zzk1vHyzGAAfA8pGhuYAeCFZjHAp59jRj6LZRFrjuGK8Whthg2", ZephyrNetworkType.STAGENET);
    ZephyrUtils.validateAddress("5HCLphJ63prG82UkKhWXDjS5ZEK9ZCDcmjdk4gpVq2fbKdEgwRCFrGTLZ2MMdSHphRWJDWVBi5qS8T7dz13JTCWtHETX8zcUhDjVKcynf6", ZephyrNetworkType.STAGENET);
    ZephyrUtils.validateAddress("5BxetVKoA2gG73QUQZRULJj2Dv2C2mceUMB5zHqNzMn8WBtfPWQrSUFSQUKTX9r7bUMmVSGbrau976xYLynR8jTWVwQwpHNg5fCLgtA2Dv", ZephyrNetworkType.STAGENET);
    
    // test stagenet subaddress validation
    ZephyrUtils.validateAddress("778B5D2JmMh5TJVWFbygJR15dvio5Z5B24hfSrWDzeroM8j8Lqc9sMoFE6324xg2ReaAZqHJkgfGFRugRmYHugHZ4f17Gxo", ZephyrNetworkType.STAGENET);
    ZephyrUtils.validateAddress("73U97wGEH9RCVUf6bopo45jSgoqjMzz4mTUsvWs5EusmYAmFcBYFm7wKMVmgtVKCBhMQqXrcMbHvwck2md63jMZSFJxUhQ2", ZephyrNetworkType.STAGENET);
    ZephyrUtils.validateAddress("747wPpaPKrjDPZrF48jAfz9pRRUHLMCWfYu2UanP4ZfTG8NrmYrSEWNW8gYoadU8hTiwBjV14e6DLaC5xfhyEpX5154aMm6", ZephyrNetworkType.STAGENET);
    
    // test invalid addresses on mainnet
    testInvalidAddress(null, ZephyrNetworkType.MAINNET);
    testInvalidAddress("", ZephyrNetworkType.MAINNET);
    testInvalidAddress("42ZxX3Y2y5s4nJ8fdz2w65TrTEp9PRsv5J8iHSShkHQcE2V31FhnWptioNst1K9oeDY4KpWZ7v8V2BZNVa4Wdky89iqmPz2", ZephyrNetworkType.MAINNET);
    testInvalidAddress("41ApvrfMgUFZEePHBZHwuSckQXebuZu299NSmVEmQ41YJZQhKcPyujyMSzpDH4VMMVSBo3U3b54JaNvQLwAjqDhKeGLQ9vfRBRKFKnBtVH", ZephyrNetworkType.MAINNET);
    testInvalidAddress("81fyq3t8Gxn1QWMG189EufHtMHXZXkfJtJKFJXqeA4GpSiuyfjVwVyp47PeQJnD7Tc8iK8TDvvhcmEmfh8nx7Va2ToP8wAo", ZephyrNetworkType.MAINNET);
    
    // test invalid addresses on testnet
    testInvalidAddress(null, ZephyrNetworkType.TESTNET);
    testInvalidAddress("", ZephyrNetworkType.TESTNET);
    testInvalidAddress("91UBnNCkC3UKGygHCwYvAB1FscpjUuq5e9MYJd2rXuiiTjjfVeSVjnbSG5VTnJgBgy9Y7GTLfxpZNMUwNZjGfdFr1z79eV1", ZephyrNetworkType.TESTNET);
    testInvalidAddress("A1AroB2EoJzKGygHCwYvAB1FscpjUuq5e9MYJd2rXuiiTjjfVeSVjnbSG5VTnJgBgy9Y7GTLfxpZNMUwNZjGfdFr2QY5Ba2aHhTEdQa2ra", ZephyrNetworkType.TESTNET);
    testInvalidAddress("B1nKzHPJQDcg7xiP7bMN9MfPv9Z8ciT71iEMYnCdgBRBFETWgu9nKTr8fnzyGfU9h9gyNA8SFzYYzHfTS9KhqytSU943Nu1", ZephyrNetworkType.TESTNET);
    
    // test invalid addresses on stagenet
    testInvalidAddress(null, ZephyrNetworkType.STAGENET);
    testInvalidAddress("", ZephyrNetworkType.STAGENET);
    testInvalidAddress("518s3obCY2ETeQB3GNAGPK2zRGen5UeW1WzegSizVsmf6z5NvM2GLoN6zzk1vHyzGAAfA8pGhuYAeCFZjHAp59jRVQkunGS", ZephyrNetworkType.STAGENET);
    testInvalidAddress("51qY4cQh9HkTeQB3GNAGPK2zRGen5UeW1WzegSizVsmf6z5NvM2GLoN6zzk1vHyzGAAfA8pGhuYAeCFZjHAp59jRj6LZRFrjuGK8Whthg2", ZephyrNetworkType.STAGENET);
    testInvalidAddress("718B5D2JmMh5TJVWFbygJR15dvio5Z5B24hfSrWDzeroM8j8Lqc9sMoFE6324xg2ReaAZqHJkgfGFRugRmYHugHZ4f17Gxo", ZephyrNetworkType.STAGENET);
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
