package zephyr.daemon.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.utils.GenUtils;
import zephyr.common.ZephyrUtils;

/**
 * Models a Zephyr block in the blockchain.
 */
public class ZephyrBlock extends ZephyrBlockHeader {

  private String hex;
  private ZephyrTx minerTx;
  private List<ZephyrTx> txs;
  private List<String> txHashes;
  
  public ZephyrBlock() {
    super();
  }
  
  public ZephyrBlock(ZephyrBlockHeader header) {
    super(header);
  }
  
  public ZephyrBlock(ZephyrBlock block) {
    super(block);
    this.hex = block.getHex();
    if (block.minerTx != null) this.minerTx = block.minerTx.copy().setBlock(this);
    if (block.txs != null) {
      this.txs = new ArrayList<ZephyrTx>();
      for (ZephyrTx tx : block.txs) txs.add(tx.copy().setBlock(this));
    }
    if (block.getTxHashes() != null) this.txHashes = new ArrayList<String>(block.getTxHashes());
  }
  
  public String getHex() {
    return hex;
  }
  
  public ZephyrBlock setHex(String hex) {
    this.hex = hex;
    return this;
  }
  
  public ZephyrTx getMinerTx() {
    return minerTx;
  }
  
  public ZephyrBlock setMinerTx(ZephyrTx minerTx) {
    this.minerTx = minerTx;
    return this;
  }
  
  @JsonManagedReference("block_txs")
  public List<ZephyrTx> getTxs() {
    return txs;
  }
  
  @JsonProperty("txs")
  public ZephyrBlock setTxs(List<ZephyrTx> txs) {
    this.txs = txs;
    return this;
  }
  
  @JsonIgnore
  public ZephyrBlock setTxs(ZephyrTx... txs) {
    this.txs = GenUtils.arrayToList(txs);
    return this;
  }
  
  public List<String> getTxHashes() {
    return txHashes;
  }
  
  public ZephyrBlock setTxHashes(List<String> txHashes) {
    this.txHashes = txHashes;
    return this;
  }
  
  public ZephyrBlock copy() {
    return new ZephyrBlock(this);
  }
  
  public ZephyrBlock merge(ZephyrBlock block) {
    GenUtils.assertNotNull(block);
    if (this == block) return this;
    
    // merge header fields
    super.merge(block);
    
    // merge reconcilable block extensions
    this.setHex(GenUtils.reconcile(this.getHex(), block.getHex()));
    this.setTxHashes(GenUtils.reconcile(this.getTxHashes(), block.getTxHashes()));
    
    // merge miner tx
    if (this.getMinerTx() == null) this.setMinerTx(block.getMinerTx());
    if (block.getMinerTx() != null) {
      block.getMinerTx().setBlock(this);
      minerTx.merge(block.getMinerTx());
    }
    
    // merge non-miner txs
    if (block.getTxs() != null) {
      for (ZephyrTx tx : block.getTxs()) {
        tx.setBlock(this);
        ZephyrUtils.mergeTx(txs, tx);
      }
    }

    return this;
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString(indent));
    sb.append("\n");
    sb.append(GenUtils.kvLine("Hex", getHex(), indent));
    sb.append(GenUtils.kvLine("Txs hashes", getTxHashes(), indent));
    if (getMinerTx() != null) {
      sb.append(GenUtils.kvLine("Miner tx", "", indent));
      sb.append(getMinerTx().toString(indent + 1) + "\n");
    }
    if (getTxs() != null) {
      sb.append(GenUtils.kvLine("Txs", "", indent));
      for (ZephyrTx tx : getTxs()) {
        sb.append(tx.toString(indent + 1) + "\n");
      }
    }
    String str = sb.toString();
    return str.charAt(str.length() - 1) == '\n' ? str.substring(0, str.length() - 1) : str; // strip newline
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((minerTx == null) ? 0 : minerTx.hashCode());
    result = prime * result + ((hex == null) ? 0 : hex.hashCode());
    result = prime * result + ((txHashes == null) ? 0 : txHashes.hashCode());
    result = prime * result + ((txs == null) ? 0 : txs.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrBlock other = (ZephyrBlock) obj;
    if (minerTx == null) {
      if (other.minerTx != null) return false;
    } else if (!minerTx.equals(other.minerTx)) return false;
    if (hex == null) {
      if (other.hex != null) return false;
    } else if (!hex.equals(other.hex)) return false;
    if (txHashes == null) {
      if (other.txHashes != null) return false;
    } else if (!txHashes.equals(other.txHashes)) return false;
    if (txs == null) {
      if (other.txs != null) return false;
    } else if (!txs.equals(other.txs)) return false;
    return true;
  }
  
  // ------------------- OVERRIDE CO-VARIANT RETURN TYPES ---------------------
  
  public ZephyrBlock setHash(String hash) {
    super.setHash(hash);
    return this;
  }
  
  @Override
  public ZephyrBlock setHeight(Long height) {
    super.setHeight(height);
    return this;
  }
  
  @Override
  public ZephyrBlock setTimestamp(Long timestamp) {
    super.setTimestamp(timestamp);
    return this;
  }
  
  @Override
  public ZephyrBlock setSize(Long size) {
    super.setSize(size);
    return this;
  }
  
  @Override
  public ZephyrBlock setWeight(Long weight) {
    super.setWeight(weight);
    return this;
  }
  
  @Override
  public ZephyrBlock setLongTermWeight(Long longTermWeight) {
    super.setLongTermWeight(longTermWeight);
    return this;
  }
  
  @Override
  public ZephyrBlock setDepth(Long depth) {
    super.setDepth(depth);
    return this;
  }
  
  @Override
  public ZephyrBlock setDifficulty(BigInteger difficulty) {
    super.setDifficulty(difficulty);
    return this;
  }
  
  @Override
  public ZephyrBlock setCumulativeDifficulty(BigInteger cumulativeDifficulty) {
    super.setCumulativeDifficulty(cumulativeDifficulty);
    return this;
  }
  
  @Override
  public ZephyrBlock setMajorVersion(Integer majorVersion) {
    super.setMajorVersion(majorVersion);
    return this;
  }
  
  @Override
  public ZephyrBlock setMinorVersion(Integer minorVersion) {
    super.setMinorVersion(minorVersion);
    return this;
  }
  
  @Override
  public ZephyrBlock setNonce(Long nonce) {
    super.setNonce(nonce);
    return this;
  }
  
  @Override
  public ZephyrBlock setNumTxs(Integer numTxs) {
    super.setNumTxs(numTxs);
    return this;
  }
  
  @Override
  public ZephyrBlock setOrphanStatus(Boolean orphanStatus) {
    super.setOrphanStatus(orphanStatus);
    return this;
  }
  
  @Override
  public ZephyrBlock setPrevHash(String prevHash) {
    super.setPrevHash(prevHash);
    return this;
  }
  
  @Override
  public ZephyrBlock setReward(BigInteger reward) {
    super.setReward(reward);
    return this;
  }
  
  @Override
  public ZephyrBlock setPowHash(String powHash) {
    super.setPowHash(powHash);
    return this;
  }
}
