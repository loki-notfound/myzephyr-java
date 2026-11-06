package zephyr.common;

import zephyr.lib.utils.GenUtils;

/**
 * Exception when interacting with a Zephyr wallet or daemon.
 */
public class ZephyrError extends RuntimeException {

  private static final long serialVersionUID = -6282368684634114151L;
  
  private Integer code;
  
  /**
   * Construct the exception with an existing exception.
   * 
   * @param e is the existing exception
   */
  public ZephyrError(Throwable e) {
    super(e);
  }
  
  /**
   * Construct the exception.
   * 
   * @param message is a human-readable description of the error
   */
  public ZephyrError(String message) {
    this(message, null);
  }
  
  /**
   * Construct the exception.
   * 
   * @param message is a human-readable description of the error
   * @param code is the error code (optional)
   */
  public ZephyrError(String message, Integer code) {
    super(message);
    GenUtils.assertNotNull("Exeption message cannot be null", message);
    this.code = code;
  }

  public Integer getCode() {
    return code;
  }
  
  public String toString() {
    if (code == null) return getMessage();
    return code + ": " + getMessage();
  }
}
