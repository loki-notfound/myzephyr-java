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

/**
 * The one place where the library redacts secret values in what it prints: the request
 * parameters of {@link ZephyrRpcError#toString()}, and the request and response bodies and the URI
 * that {@link ZephyrRpcConnection} prints from {@link ZephyrUtils#setLogLevel(int)} 2 and 3.
 */
final class Redaction {

  /** The text printed instead of a secret value. */
  static final String REDACTED = "<redacted>";

  /**
   * The names whose values are secret, matched case-insensitively at any depth of maps and lists.
   * Request parameters: passwords, the seed and its offset, private keys and a transaction key.
   * Response fields of a wallet RPC server: {@code key} (query_key's mnemonic, private view key or
   * private spend key), {@code seed}, transaction keys ({@code tx_key}, {@code tx_key_list}),
   * transactions that carry their secret keys ({@code tx_metadata}, {@code tx_metadata_list},
   * {@code signed_txset}, {@code multisig_txset}) and multisig key material
   * ({@code multisig_info}, {@code info}).
   */
  static final Set<String> SECRET_NAMES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
      "password", "old_password", "new_password", "seed", "seed_offset", "spendkey", "viewkey", "tx_key",
      "key", "tx_key_list", "tx_metadata", "tx_metadata_list", "signed_txset", "multisig_txset",
      "multisig_info", "info")));

  private Redaction() { }

  /**
   * A copy of the value with the values of secret names replaced by {@link #REDACTED}, in the same
   * order; anything that is not a map, a collection or an array is returned as it is.
   *
   * @param value a request's parameters, a request body or a response
   * @return the copy
   */
  static Object redact(Object value) {
    if (value instanceof Map) {
      Map<Object, Object> copy = new LinkedHashMap<Object, Object>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        Object key = entry.getKey();
        boolean secret = key instanceof String && SECRET_NAMES.contains(((String) key).toLowerCase(Locale.ROOT));
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

  /**
   * The URI with the user information (credentials written into the URI) replaced by
   * {@link #REDACTED}; anything else unchanged.
   *
   * @param uri a URI, or null
   * @return the URI to print
   */
  static String uri(String uri) {
    if (uri == null) return null;
    int start = uri.indexOf("://");
    start = start < 0 ? 0 : start + 3;
    int end = uri.length();
    for (char c : new char[] { '/', '?', '#' }) {
      int i = uri.indexOf(c, start);
      if (i >= 0 && i < end) end = i;
    }
    int at = uri.lastIndexOf('@', end - 1);
    if (at < start) return uri;
    return uri.substring(0, start) + REDACTED + uri.substring(at);
  }
}
