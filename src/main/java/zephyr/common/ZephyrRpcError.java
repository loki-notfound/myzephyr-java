package zephyr.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import zephyr.lib.utils.JsonUtils;

/**
 * Exception when interacting with the Zephyr daemon or wallet RPC API.
 */
public class ZephyrRpcError extends ZephyrError {

  private static final long serialVersionUID = -6282368684634114151L;

  /**
   * Request parameters whose values {@link #toString()} replaces with {@link #REDACTED}: passwords,
   * the seed and its offset, and private keys. Keys match case-insensitively, at any depth.
   */
  private static final Set<String> SECRET_PARAMS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
      "password", "old_password", "new_password", "seed", "seed_offset", "spendkey", "viewkey", "tx_key")));

  /** The text {@link #toString()} prints instead of a secret parameter's value. */
  public static final String REDACTED = "<redacted>";
  
  private String rpcMethod;
  private Object rpcParams;
  
  public ZephyrRpcError(String rpcDescription, Integer rpcCode, String rpcMethod, Object rpcParams) {
    super(rpcDescription, rpcCode);
    this.rpcMethod = rpcMethod;
    this.rpcParams = rpcParams;
  }

  public String getRpcMethod() {
    return rpcMethod;
  }

  /**
   * The request's parameters as they were sent, secrets included.
   *
   * @return the request's parameters, or null for an HTTP error
   */
  public Object getRpcParams() {
    return rpcParams;
  }
  
  /**
   * The code and the message, then the RPC method and the request's parameters as JSON. The values
   * of password, old_password, new_password, seed, seed_offset, spendkey, viewkey and tx_key are
   * printed as {@value #REDACTED}, in nested maps and lists as well; {@link #getRpcParams()} keeps
   * them.
   */
  public String toString() {
    String str = super.toString();
    if (rpcMethod != null || rpcParams != null) str += "\nRPC request: '" + rpcMethod + "' with params: " + JsonUtils.serialize(redact(rpcParams));
    return str;
  }

  /** A copy of the value with the values of secret keys replaced, in the same order. */
  private static Object redact(Object value) {
    if (value instanceof Map) {
      Map<Object, Object> copy = new LinkedHashMap<Object, Object>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        Object key = entry.getKey();
        boolean secret = key instanceof String && SECRET_PARAMS.contains(((String) key).toLowerCase(Locale.ROOT));
        copy.put(key, secret ? REDACTED : redact(entry.getValue()));
      }
      return copy;
    }
    if (value instanceof Collection || value instanceof Object[]) {
      List<Object> copy = new ArrayList<Object>();
      for (Object element : value instanceof Object[] ? Arrays.asList((Object[]) value) : (Collection<?>) value) copy.add(redact(element));
      return copy;
    }
    return value;
  }
}
