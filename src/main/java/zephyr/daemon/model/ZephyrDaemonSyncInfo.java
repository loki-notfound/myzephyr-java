package zephyr.daemon.model;

import java.math.BigInteger;
import java.util.List;

/**
 * Models daemon synchronization information.
 */
public class ZephyrDaemonSyncInfo {

  private Long height;
  private List<ZephyrPeer> peers;
  private List<ZephyrConnectionSpan> spans;
  private Long targetHeight;
  private Integer nextNeededPruningSeed;
  private String overview;
  private BigInteger credits;
  private String topBlockHash;
  
  public Long getHeight() {
    return height;
  }
  
  public void setHeight(Long height) {
    this.height = height;
  }
  
  public List<ZephyrPeer> getPeers() {
    return peers;
  }
  
  public void setPeers(List<ZephyrPeer> peers) {
    this.peers = peers;
  }
  
  public List<ZephyrConnectionSpan> getSpans() {
    return spans;
  }
  
  public void setSpans(List<ZephyrConnectionSpan> spans) {
    this.spans = spans;
  }
  
  public Long getTargetHeight() {
    return targetHeight;
  }
  
  public void setTargetHeight(Long targetHeight) {
    this.targetHeight = targetHeight;
  }
  
  public Integer getNextNeededPruningSeed() {
    return nextNeededPruningSeed;
  }
  
  public void setNextNeededPruningSeed(Integer nextNeededPruningSeed) {
    this.nextNeededPruningSeed = nextNeededPruningSeed;
  }
  
  public String getOverview() {
    return overview;
  }
  
  public void setOverview(String overview) {
    this.overview = overview;
  }

  public BigInteger getCredits() {
    return credits;
  }

  public void setCredits(BigInteger credits) {
    this.credits = credits;
  }

  public String getTopBlockHash() {
    return topBlockHash;
  }

  public void setTopBlockHash(String topBlockHash) {
    this.topBlockHash = topBlockHash;
  }
}
