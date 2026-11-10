package test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import zephyr.wallet.ZephyrWalletFull;

/**
 * A daemon URI as ZephyrWalletFull hands it to the native core (a private helper, reached by
 * reflection): an http or https scheme in lower case, then the default port when there is none
 * (80 for http, 443 for https); everything else as it is.
 */
public class TestDefaultPort {

  private static String withDefaultPort(String uri) throws Exception {
    Method method = ZephyrWalletFull.class.getDeclaredMethod("daemonUriForCore", String.class);
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
        { "HTTPS://node.example", "https://node.example:443" },
        { "HTTPS://host", "https://host:443" },
        { "Https://host:8443", "https://host:8443" },
        { "HTTP://host/path", "http://host:80/path" },
        { "HtTpS://[2001:DB8::A]", "https://[2001:DB8::A]:443" },
        { "HTTPS://[2001:db8::1]:8443/Path?Q=1", "https://[2001:db8::1]:8443/Path?Q=1" },
        { "HTTPS://bad host", "https://bad host" },
        { "SOCKS5://127.0.0.1", "SOCKS5://127.0.0.1" },
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
    for (String[] row : table) assertEquals(row[1], withDefaultPort(row[0]), "daemonUriForCore(" + row[0] + ")");
  }
}
