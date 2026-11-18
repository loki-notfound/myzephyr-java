package zephyr.common;

import zephyr.lib.utils.JsonUtils;

/**
 * Exception when interacting with the Zephyr daemon or wallet RPC API.
 */
public class ZephyrRpcError extends ZephyrError {

  private static final long serialVersionUID = -6282368684634114151L;

  /** The text {@link #toString()} prints instead of a secret parameter's value. */
  public static final String REDACTED = Redaction.REDACTED;
  
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
   * of password, old_password, new_password, seed, seed_offset, spendkey, viewkey, tx_key, key,
   * tx_key_list, tx_metadata, tx_metadata_list, signed_txset, multisig_txset, multisig_info and info
   * are printed as {@value #REDACTED}, in nested maps and lists as well (names matched without
   * regard to case; the same list as the request and response logging of
   * {@link ZephyrRpcConnection}); {@link #getRpcParams()} keeps them.
   */
  public String toString() {
    String str = super.toString();
    if (rpcMethod != null || rpcParams != null) str += "\nRPC request: '" + rpcMethod + "' with params: " + JsonUtils.serialize(Redaction.redact(rpcParams));
    return str;
  }
}
