package zephyr.daemon.model;

import java.math.BigInteger;
import java.util.List;

/**
 * Zephyr output distribution entry.
 */
public class ZephyrOutputDistributionEntry {

  private BigInteger amount;
  private Integer base;
  private List<Integer> distribution;
  private Long startHeight;
  
  public BigInteger getAmount() {
    return amount;
  }
  
  public void setAmount(BigInteger amount) {
    this.amount = amount;
  }
  
  public Integer getBase() {
    return base;
  }
  
  public void setBase(Integer base) {
    this.base = base;
  }
  
  public List<Integer> getDistribution() {
    return distribution;
  }
  
  public void setDistribution(List<Integer> distribution) {
    this.distribution = distribution;
  }
  
  public Long getStartHeight() {
    return startHeight;
  }
  
  public void setStartHeight(Long startHeight) {
    this.startHeight = startHeight;
  }
}
