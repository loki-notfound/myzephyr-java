package examples;

import java.math.BigInteger;
import zephyr.common.ZephyrRpcError;
import zephyr.daemon.ZephyrDaemonRpc;
import zephyr.daemon.model.ZephyrPricingRecord;

/**
 * The RPC classes of the user guide (docs/guide/11-the-rpc-classes.md), compiled with the tests so
 * that the guide's code keeps matching the API. The tests do not run it.
 */
public class GuideRpc {

  // the ZRS price of the last block's pricing record, read from the node's RPC
  static BigInteger reservePrice(String nodeUri) {
    ZephyrDaemonRpc daemon = new ZephyrDaemonRpc(nodeUri);
    ZephyrPricingRecord record = daemon.getLastBlockHeader().getPricingRecord();
    return record.getReserve();  // the price of one ZRS in ZPH, times 10^12
  }

  // an RPC error: log the code and the message, never toString(), which prints the parameters
  static String describe(ZephyrRpcError e) {
    return "RPC " + e.getRpcMethod() + " failed: " + e.getCode() + " " + e.getMessage();
  }
}
