package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import zephyr.common.ZephyrRpcConnection;
import zephyr.daemon.ZephyrDaemonRpc;
import zephyr.daemon.model.ZephyrBlockHeader;
import zephyr.daemon.model.ZephyrOutput;
import zephyr.daemon.model.ZephyrPricingRecord;
import zephyr.lib.utils.JsonUtils;
import zephyr.wallet.model.ZephyrAccount;
import zephyr.wallet.model.ZephyrAssets;
import zephyr.wallet.model.ZephyrBalance;
import zephyr.wallet.model.ZephyrDestination;
import zephyr.wallet.model.ZephyrIncomingTransfer;
import zephyr.wallet.model.ZephyrOutgoingTransfer;
import zephyr.wallet.model.ZephyrOutputWallet;
import zephyr.wallet.model.ZephyrReserveInfo;
import zephyr.wallet.model.ZephyrSubaddress;
import zephyr.wallet.model.ZephyrTxConfig;

/**
 * Tests the Zephyr model classes against the exact JSON shapes of the core and the native bridge.
 *
 * No daemon, no wallet and no native library are needed.
 */
public class TestZephyrModel {

  // 1. a balance from the core's {asset: amount} object and back
  @Test
  public void testBalanceJson() {
    String json = "{\"ZSD\":0,\"ZPH\":123456789012}";
    ZephyrBalance balance = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, json, ZephyrBalance.class);
    assertEquals(new BigInteger("123456789012"), balance.get(ZephyrAssets.ZPH));
    assertEquals(BigInteger.ZERO, balance.get(ZephyrAssets.ZSD));
    assertEquals(BigInteger.ZERO, balance.get(ZephyrAssets.ZRS));  // absent
    assertEquals(Arrays.asList("ZPH", "ZSD"), Arrays.asList(balance.getAssetTypes().toArray()));
    assertEquals("{\"ZPH\":123456789012,\"ZSD\":0}", JsonUtils.serialize(ZephyrRpcConnection.MAPPER, balance));  // sorted by asset
    ZephyrBalance copy = balance.copy();
    assertNotSame(balance, copy);
    assertEquals(balance, copy);
    assertEquals(balance.hashCode(), copy.hashCode());
  }

  // 2. an account as the core emits it, with balance objects on the account and its subaddresses
  @Test
  public void testAccountJson() {
    String json = "{"
        + "\"index\":0,"
        + "\"balance\":{\"ZPH\":3000000000000,\"ZSD\":500000000000},"
        + "\"unlockedBalance\":{\"ZPH\":2000000000000,\"ZSD\":500000000000},"
        + "\"primaryAddress\":\"ZEPHYR_PRIMARY\","
        + "\"tag\":\"main\","
        + "\"subaddresses\":["
        + "{\"accountIndex\":0,\"index\":0,\"numUnspentOutputs\":2,\"numBlocksToUnlock\":0,"
        + "\"balance\":{\"ZPH\":1000000000000,\"ZSD\":500000000000},\"unlockedBalance\":{\"ZPH\":1000000000000,\"ZSD\":500000000000},"
        + "\"address\":\"ZEPHYR_PRIMARY\",\"label\":\"Primary account\",\"isUsed\":true},"
        + "{\"accountIndex\":0,\"index\":1,\"numUnspentOutputs\":1,\"numBlocksToUnlock\":7,"
        + "\"balance\":{\"ZPH\":2000000000000},\"unlockedBalance\":{\"ZPH\":1000000000000},"
        + "\"address\":\"ZEPHYR_SUB1\",\"label\":\"\",\"isUsed\":true}"
        + "]}";
    ZephyrAccount account = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, json, ZephyrAccount.class);
    assertEquals(Integer.valueOf(0), account.getIndex());
    assertEquals("ZEPHYR_PRIMARY", account.getPrimaryAddress());
    assertEquals(new BigInteger("3000000000000"), account.getBalance().get(ZephyrAssets.ZPH));
    assertEquals(new BigInteger("500000000000"), account.getBalance().get(ZephyrAssets.ZSD));
    assertEquals(new BigInteger("2000000000000"), account.getUnlockedBalance().get(ZephyrAssets.ZPH));
    assertEquals(new BigInteger("500000000000"), account.getUnlockedBalance().get(ZephyrAssets.ZSD));
    assertEquals(BigInteger.ZERO, account.getBalance().get(ZephyrAssets.ZRS));
    assertEquals(2, account.getSubaddresses().size());
    ZephyrSubaddress sub0 = account.getSubaddresses().get(0);
    ZephyrSubaddress sub1 = account.getSubaddresses().get(1);
    assertEquals(new BigInteger("1000000000000"), sub0.getBalance().get(ZephyrAssets.ZPH));
    assertEquals(new BigInteger("500000000000"), sub0.getBalance().get(ZephyrAssets.ZSD));
    assertEquals(new BigInteger("500000000000"), sub0.getUnlockedBalance().get(ZephyrAssets.ZSD));
    assertEquals(Integer.valueOf(1), sub1.getIndex());
    assertEquals(Integer.valueOf(0), sub1.getAccountIndex());
    assertEquals(new BigInteger("2000000000000"), sub1.getBalance().get(ZephyrAssets.ZPH));
    assertEquals(new BigInteger("1000000000000"), sub1.getUnlockedBalance().get(ZephyrAssets.ZPH));
    assertEquals(BigInteger.ZERO, sub1.getBalance().get(ZephyrAssets.ZSD));
    assertEquals(Long.valueOf(7), sub1.getNumBlocksToUnlock());

    // round trip through the mapper
    String json2 = JsonUtils.serialize(ZephyrRpcConnection.MAPPER, account);
    assertEquals(account, JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, json2, ZephyrAccount.class));
  }

  // 3. the per-account bridge shape, keyed by subaddress index
  @Test
  public void testBalancePerSubaddressJson() {
    String json = "{\"0\":{\"ZPH\":1},\"1\":{\"ZSD\":2}}";
    Map<Integer, ZephyrBalance> balances = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, json, new TypeReference<Map<Integer, ZephyrBalance>>(){});
    assertEquals(2, balances.size());
    assertEquals(BigInteger.ONE, balances.get(0).get(ZephyrAssets.ZPH));
    assertEquals(BigInteger.ZERO, balances.get(0).get(ZephyrAssets.ZSD));
    assertEquals(BigInteger.valueOf(2), balances.get(1).get(ZephyrAssets.ZSD));
    assertEquals(new ZephyrBalance().set(ZephyrAssets.ZSD, BigInteger.valueOf(2)), balances.get(1));
  }

  // 4. a destination with and without the core's asset_type key
  @Test
  public void testDestinationAssetType() {
    String json = "{\"amount\":250000000000,\"address\":\"ZEPHYR_DEST\",\"asset_type\":\"ZSD\"}";
    ZephyrDestination destination = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, json, ZephyrDestination.class);
    assertEquals("ZEPHYR_DEST", destination.getAddress());
    assertEquals(new BigInteger("250000000000"), destination.getAmount());
    assertEquals(ZephyrAssets.ZSD, destination.getAssetType());
    String json2 = JsonUtils.serialize(ZephyrRpcConnection.MAPPER, destination);
    assertTrue(json2.contains("\"asset_type\":\"ZSD\""));
    assertFalse(json2.contains("assetType"));
    assertEquals(destination, JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, json2, ZephyrDestination.class));
    assertEquals(destination, destination.copy());

    // without the key
    ZephyrDestination plain = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, "{\"amount\":1,\"address\":\"ZEPHYR_DEST\"}", ZephyrDestination.class);
    assertNull(plain.getAssetType());
    String json3 = JsonUtils.serialize(ZephyrRpcConnection.MAPPER, plain);
    assertFalse(json3.contains("asset_type"));
    assertFalse(json3.contains("assetType"));
  }

  // 5. an incoming transfer and an output with the asset; copy keeps it
  @Test
  public void testTransferAndOutputAssetType() {
    String json = "{\"amount\":1000000000000,\"accountIndex\":0,\"asset_type\":\"ZRS\",\"subaddressIndex\":1,\"address\":\"ZEPHYR_SUB1\",\"numSuggestedConfirmations\":1}";
    ZephyrIncomingTransfer transfer = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, json, ZephyrIncomingTransfer.class);
    assertEquals(ZephyrAssets.ZRS, transfer.getAssetType());
    assertEquals(new BigInteger("1000000000000"), transfer.getAmount());
    assertEquals(Integer.valueOf(1), transfer.getSubaddressIndex());
    ZephyrIncomingTransfer transferCopy = transfer.copy();
    assertNotSame(transfer, transferCopy);
    assertEquals(ZephyrAssets.ZRS, transferCopy.getAssetType());
    assertEquals(transfer, transferCopy);
    assertTrue(JsonUtils.serialize(ZephyrRpcConnection.MAPPER, transfer).contains("\"asset_type\":\"ZRS\""));

    ZephyrOutput output = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, "{\"amount\":5,\"index\":42,\"asset_type\":\"ZYS\"}", ZephyrOutput.class);
    assertEquals(ZephyrAssets.ZYS, output.getAssetType());
    assertEquals(Long.valueOf(42), output.getIndex());
    ZephyrOutput outputCopy = output.copy();
    assertNotSame(output, outputCopy);
    assertEquals(ZephyrAssets.ZYS, outputCopy.getAssetType());
    assertEquals(output, outputCopy);

    // fluent chains keep the subclass type
    ZephyrIncomingTransfer incoming = new ZephyrIncomingTransfer().setAssetType(ZephyrAssets.ZSD).setAmount(BigInteger.ONE).setSubaddressIndex(0);
    assertEquals(ZephyrAssets.ZSD, incoming.getAssetType());
    ZephyrOutgoingTransfer outgoing = new ZephyrOutgoingTransfer().setAssetType(ZephyrAssets.ZRS).setAmount(BigInteger.ONE).setSubaddressIndices(Arrays.asList(0));
    assertEquals(ZephyrAssets.ZRS, outgoing.getAssetType());
    ZephyrOutputWallet outputWallet = new ZephyrOutputWallet().setAssetType(ZephyrAssets.ZYS).setAmount(BigInteger.ONE).setSubaddressIndex(0);
    assertEquals(ZephyrAssets.ZYS, outputWallet.getAssetType());
  }

  // 6. the currency pair on the tx config, camelCase keys as the core deserializes them
  @Test
  public void testTxConfigCurrencies() {
    ZephyrTxConfig config = new ZephyrTxConfig()
        .setAccountIndex(0)
        .setAddress("ZEPHYR_DEST")
        .setAmount(new BigInteger("1000000000000"))
        .setSourceCurrency(ZephyrAssets.ZPH)
        .setDestinationCurrency(ZephyrAssets.ZSD);
    assertEquals(ZephyrAssets.ZPH, config.getSourceCurrency());
    assertEquals(ZephyrAssets.ZSD, config.getDestinationCurrency());
    String json = JsonUtils.serialize(ZephyrRpcConnection.MAPPER, config);
    assertTrue(json.contains("\"sourceCurrency\":\"ZPH\""));
    assertTrue(json.contains("\"destinationCurrency\":\"ZSD\""));
    ZephyrTxConfig config2 = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, json, ZephyrTxConfig.class);
    assertEquals(config, config2);
    assertEquals(config.hashCode(), config2.hashCode());
    ZephyrTxConfig copy = config.copy();
    assertNotSame(config, copy);
    assertEquals(config, copy);
    assertNotEquals(config, copy.setDestinationCurrency(ZephyrAssets.ZPH));

    // a config without the pair has no such keys
    String plain = JsonUtils.serialize(ZephyrRpcConnection.MAPPER, new ZephyrTxConfig().setAccountIndex(0).setAddress("ZEPHYR_DEST").setAmount(BigInteger.ONE));
    assertFalse(plain.contains("sourceCurrency"));
    assertFalse(plain.contains("destinationCurrency"));
  }

  // 7. the reserve info as the bridge emits it
  @Test
  public void testReserveInfoJson() {
    String json = "{"
        + "\"zeph_reserve\":\"36893488147419103232\","
        + "\"num_stables\":\"2000000000000\","
        + "\"num_reserves\":\"3000000000000\","
        + "\"assets\":\"4000000000000\","
        + "\"assets_ma\":\"5000000000000\","
        + "\"liabilities\":\"6000000000000\","
        + "\"equity\":\"7000000000000\","
        + "\"equity_ma\":\"8000000000000\","
        + "\"reserve_ratio\":\"4.1234567890123457\","
        + "\"reserve_ratio_ma\":\"0.5\","
        + "\"hf_version\":11,"
        + "\"zyield_circ\":\"9000000000000\","
        + "\"zyield_rsv\":\"10000000000000\""
        + "}";
    ZephyrReserveInfo info = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, json, ZephyrReserveInfo.class);
    assertEquals(new BigInteger("36893488147419103232"), info.getZephReserve());  // 2^65, above uint64
    assertEquals(new BigInteger("2000000000000"), info.getNumStables());
    assertEquals(new BigInteger("3000000000000"), info.getNumReserves());
    assertEquals(new BigInteger("4000000000000"), info.getAssets());
    assertEquals(new BigInteger("5000000000000"), info.getAssetsMa());
    assertEquals(new BigInteger("6000000000000"), info.getLiabilities());
    assertEquals(new BigInteger("7000000000000"), info.getEquity());
    assertEquals(new BigInteger("8000000000000"), info.getEquityMa());
    assertEquals(new BigDecimal("4.1234567890123457"), info.getReserveRatio());  // seventeen significant digits
    assertEquals(new BigDecimal("0.5"), info.getReserveRatioMa());
    assertEquals(Integer.valueOf(11), info.getHfVersion());
    assertEquals(new BigInteger("9000000000000"), info.getZyieldCirc());
    assertEquals(new BigInteger("10000000000000"), info.getZyieldRsv());

    // round trip through the mapper
    String json2 = JsonUtils.serialize(ZephyrRpcConnection.MAPPER, info);
    ZephyrReserveInfo info2 = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, json2, ZephyrReserveInfo.class);
    assertEquals(info, info2);
    assertEquals(info.hashCode(), info2.hashCode());
    assertEquals(13, JsonUtils.toMap(ZephyrRpcConnection.MAPPER, json2).size());
  }

  // 8. the V2 asset names
  @Test
  public void testAssetsV2() {
    assertEquals(Arrays.asList("ZPH", "ZSD", "ZRS", "ZYS"), ZephyrAssets.V2);
    assertThrows(UnsupportedOperationException.class, () -> ZephyrAssets.V2.add("ZEPH"));
    assertThrows(UnsupportedOperationException.class, () -> ZephyrAssets.V2.set(0, "ZEPH"));
  }

  // 9. the block header's pricing record
  @Test
  public void testPricingRecordJson() throws Exception {
    String sig = "0123456789abcdef".repeat(8);  // 128 hex characters
    assertEquals(128, sig.length());
    String json = "{\"spot\":1500000000000,\"moving_average\":1490000000000,\"stable\":1000000000000,"
        + "\"stable_ma\":1000000000001,\"reserve\":2000000000000,\"reserve_ma\":2000000000001,"
        + "\"reserve_ratio\":4000000000000,\"reserve_ratio_ma\":4100000000000,\"yield_price\":1000000000002,"
        + "\"timestamp\":1700000000,\"signature\":\"" + sig + "\"}";
    ZephyrPricingRecord record = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, json, ZephyrPricingRecord.class);
    assertEquals(new BigInteger("1500000000000"), record.getSpot());
    assertEquals(new BigInteger("1490000000000"), record.getMovingAverage());
    assertEquals(new BigInteger("1000000000000"), record.getStable());
    assertEquals(new BigInteger("1000000000001"), record.getStableMa());
    assertEquals(new BigInteger("2000000000000"), record.getReserve());
    assertEquals(new BigInteger("2000000000001"), record.getReserveMa());
    assertEquals(new BigInteger("4000000000000"), record.getReserveRatio());
    assertEquals(new BigInteger("4100000000000"), record.getReserveRatioMa());
    assertEquals(new BigInteger("1000000000002"), record.getYieldPrice());
    assertEquals(new BigInteger("1700000000"), record.getTimestamp());
    assertEquals(sig, record.getSignature());

    // round trip through the mapper
    String json2 = JsonUtils.serialize(ZephyrRpcConnection.MAPPER, record);
    ZephyrPricingRecord record2 = JsonUtils.deserialize(ZephyrRpcConnection.MAPPER, json2, ZephyrPricingRecord.class);
    assertEquals(record, record2);
    assertEquals(record.hashCode(), record2.hashCode());
    assertEquals(11, JsonUtils.toMap(ZephyrRpcConnection.MAPPER, json2).size());

    // copy
    ZephyrPricingRecord copy = record.copy();
    assertNotSame(record, copy);
    assertEquals(record, copy);

    // the daemon RPC client's block header conversion parses the nested record
    String headerJson = "{\"height\":100,\"hash\":\"ab\",\"pricing_record\":" + json + "}";
    Method convert = ZephyrDaemonRpc.class.getDeclaredMethod("convertRpcBlockHeader", Map.class);
    convert.setAccessible(true);
    ZephyrBlockHeader header = (ZephyrBlockHeader) convert.invoke(null, JsonUtils.toMap(ZephyrRpcConnection.MAPPER, headerJson));
    assertEquals(record, header.getPricingRecord());
    assertEquals(Long.valueOf(100), header.getHeight());
    assertEquals(header, new ZephyrBlockHeader(header));
  }

  // 10. the block header copy constructor copies every field, minerTxHash included
  @Test
  public void testBlockHeaderCopy() {
    ZephyrPricingRecord record = new ZephyrPricingRecord()
        .setSpot(new BigInteger("1500000000000"))
        .setMovingAverage(new BigInteger("1490000000000"))
        .setStable(new BigInteger("1000000000000"))
        .setStableMa(new BigInteger("1000000000001"))
        .setReserve(new BigInteger("2000000000000"))
        .setReserveMa(new BigInteger("2000000000001"))
        .setReserveRatio(new BigInteger("4000000000000"))
        .setReserveRatioMa(new BigInteger("4100000000000"))
        .setYieldPrice(new BigInteger("1000000000002"))
        .setTimestamp(new BigInteger("1700000000"))
        .setSignature("0123456789abcdef".repeat(8));
    ZephyrBlockHeader header = new ZephyrBlockHeader()
        .setHash("aa".repeat(32))
        .setHeight(536000L)
        .setTimestamp(1750000000L)
        .setSize(1234L)
        .setWeight(1235L)
        .setLongTermWeight(1236L)
        .setDepth(7L)
        .setDifficulty(new BigInteger("123456789"))
        .setCumulativeDifficulty(new BigInteger("987654321987654321"))
        .setMajorVersion(11)
        .setMinorVersion(11)
        .setNonce(42L)
        .setMinerTxHash("bb".repeat(32))
        .setNumTxs(3)
        .setOrphanStatus(false)
        .setPrevHash("cc".repeat(32))
        .setReward(new BigInteger("2000000000000"))
        .setPowHash("dd".repeat(32))
        .setPricingRecord(record);
    ZephyrBlockHeader copy = new ZephyrBlockHeader(header);
    assertNotSame(header, copy);
    assertEquals(header, copy);
    assertEquals(header.hashCode(), copy.hashCode());
    assertEquals("bb".repeat(32), copy.getMinerTxHash());
    assertNotSame(header.getPricingRecord(), copy.getPricingRecord());
  }
}
