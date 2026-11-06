package zephyr.daemon.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Defines a Zephyr version.
 */
public class ZephyrVersion {
  
  public ZephyrVersion() {
    // necessary for deserialization
  }
  
  public ZephyrVersion(Integer number, Boolean isRelease) {
    this.number = number;
    this.isRelease = isRelease;
  }

  private Integer number;
  private Boolean isRelease;
  
  public Integer getNumber() {
    return number;
  }
  
  public void setNumber(Integer number) {
    this.number = number;
  }
  
  @JsonProperty("isRelease")
  public Boolean getIsRelease() {
    return isRelease;
  }
  
  public void setIsRelease(Boolean isRelease) {
    this.isRelease = isRelease;
  }
}
