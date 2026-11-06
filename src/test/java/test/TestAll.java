package test;

import org.junit.platform.runner.JUnitPlatform;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.runner.RunWith;

/**
 * Run all tests in a custom order.
 */
@RunWith(JUnitPlatform.class)
@SelectClasses({
  TestSampleCode.class,
  TestSerialization.class,
  TestZephyrUtils.class,
  TestZephyrDaemonRpc.class,
  TestZephyrWalletFull.class,
  TestZephyrWalletRpc.class,
  TestZephyrConnectionManager.class
})
public class TestAll {}