package test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import zephyr.wallet.ZephyrWalletFull;

/**
 * The default port ZephyrWalletFull gives a daemon URI without one before it reaches the native
 * core (a private helper, reached by reflection): 80 for http, 443 for https, everything else as
 * it is.
 */
public class TestDefaultPort {

  private static String withDefaultPort(String uri) throws Exception {
    Method method = ZephyrWalletFull.class.getDeclaredMethod("withDefaultPort", String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, uri);
  }

  @Test
  public void testDefaultPort() throws Exception {
    String[][] table = {
        // input, expected
        { "https://node.example:443", "https://node.example:443" },
        { "http://node.example:17767", "http://node.example:17767" },
        { "https://node.example", "https://node.example:443" },
        { "http://node.example", "http://node.example:80" },
        { "HTTPS://node.example", "HTTPS://node.example:443" },
        { "https://[2001:db8::1]:8443", "https://[2001:db8::1]:8443" },
        { "https://[2001:db8::1]", "https://[2001:db8::1]:443" },
        { "http://[::1]/json_rpc", "http://[::1]:80/json_rpc" },
        { "https://node.example/zephyr/path?x=1#f", "https://node.example:443/zephyr/path?x=1#f" },
        { "https://node.example:443/zephyr/path", "https://node.example:443/zephyr/path" },
        { "https://user:secret@node.example", "https://user:secret@node.example:443" },
        { "https://user:secret@node.example:18089/", "https://user:secret@node.example:18089/" },
        { "http://node.example:", "http://node.example:80" },
        { "socks5://127.0.0.1", "socks5://127.0.0.1" },
        { "node.example:17767", "node.example:17767" },
        { "https://", "https://" },
        { "https://bad host", "https://bad host" },
        { "::::", "::::" },
        { "", "" },
        { null, null },
    };
    for (String[] row : table) assertEquals(row[1], withDefaultPort(row[0]), "withDefaultPort(" + row[0] + ")");
  }
}
