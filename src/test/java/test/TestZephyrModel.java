package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import zephyr.common.ZephyrRpcConnection;
import zephyr.lib.utils.JsonUtils;
import zephyr.wallet.model.ZephyrAssets;
import zephyr.wallet.model.ZephyrBalance;
import zephyr.wallet.model.ZephyrReserveInfo;

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
}
