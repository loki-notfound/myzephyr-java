package zephyr.daemon.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Zephyr banhammer.
 */
public class ZephyrBan {
  
  private String host;  // e.g. 192.168.1.100
  private Integer ip;   // integer formatted IP
  private Boolean isBanned;
  private Long seconds;
  
  public String getHost() {
    return host;
  }
  
  public ZephyrBan setHost(String host) {
    this.host = host;
    return this;
  }
  
  public Integer getIp() {
    return ip;
  }
  
  public ZephyrBan setIp(Integer ip) {
    this.ip = ip;
    return this;
  }
  
  @JsonProperty("isBanned")
  public Boolean isBanned() {
    return isBanned;
  }
  
  public ZephyrBan setIsBanned(Boolean isBanned) {
    this.isBanned = isBanned;
    return this;
  }
  
  public Long getSeconds() {
    return seconds;
  }
  
  public ZephyrBan setSeconds(Long seconds) {
    this.seconds = seconds;
    return this;
  }
}
