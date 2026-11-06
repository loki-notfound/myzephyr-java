package zephyr.wallet.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.utils.GenUtils;
import zephyr.common.ZephyrUtils;
import zephyr.daemon.model.ZephyrTx;

/**
 * Groups transactions who share common hex data which is needed in order to
 * sign and submit the transactions.
 * 
 * For example, multisig transactions created from createTxs() share a common
 * hex string which is needed in order to sign and submit the multisig
 * transactions.
 */
public class ZephyrTxSet {

  private List<ZephyrTxWallet> txs;
  private String multisigTxHex;
  private String unsignedTxHex;
  private String signedTxHex;
  
  @JsonManagedReference("tx_set")
  public List<ZephyrTxWallet> getTxs() {
    return txs;
  }

  @JsonProperty("txs")
  public ZephyrTxSet setTxs(List<ZephyrTxWallet> txs) {
    this.txs = txs;
    return this;
  }
  
  @JsonIgnore
  public ZephyrTxSet setTxs(ZephyrTxWallet... txs) {
    this.txs = GenUtils.arrayToList(txs);
    return this;
  }

  public String getMultisigTxHex() {
    return multisigTxHex;
  }
  
  public ZephyrTxSet setMultisigTxHex(String multisigTxHex) {
    this.multisigTxHex = multisigTxHex;
    return this;
  }
  
  public String getUnsignedTxHex() {
    return unsignedTxHex;
  }
  
  public ZephyrTxSet setUnsignedTxHex(String unsignedTxHex) {
    this.unsignedTxHex = unsignedTxHex;
    return this;
  }
  
  public String getSignedTxHex() {
    return signedTxHex;
  }
  
  public ZephyrTxSet setSignedTxHex(String signedTxHex) {
    this.signedTxHex = signedTxHex;
    return this;
  }
  
  public ZephyrTxSet merge(ZephyrTxSet txSet) {
    GenUtils.assertNotNull(txSet);
    if (this == txSet) return this;
    
    // merge sets
    this.setMultisigTxHex(GenUtils.reconcile(this.getMultisigTxHex(), txSet.getMultisigTxHex()));
    this.setUnsignedTxHex(GenUtils.reconcile(this.getUnsignedTxHex(), txSet.getUnsignedTxHex()));
    this.setSignedTxHex(GenUtils.reconcile(this.getSignedTxHex(), txSet.getSignedTxHex()));
    
    // merge txs
    if (txSet.getTxs() != null) {
      for (ZephyrTxWallet tx : txSet.getTxs()) {
        tx.setTxSet(this);
        ZephyrUtils.mergeTx(txs, tx);
      }
    }

    return this;
  }
  
  @Override
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(GenUtils.kvLine("Multisig tx hex: ", getMultisigTxHex(), indent));
    sb.append(GenUtils.kvLine("Unsigned tx hex: ", getUnsignedTxHex(), indent));
    sb.append(GenUtils.kvLine("Signed tx hex: ", getSignedTxHex(), indent));
    if (getTxs() != null) {
      sb.append(GenUtils.kvLine("Txs", "", indent));
      for (ZephyrTx tx : getTxs()) {
        sb.append(tx.toString(indent + 1) + "\n");
      }
    }
    return sb.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((multisigTxHex == null) ? 0 : multisigTxHex.hashCode());
    result = prime * result + ((signedTxHex == null) ? 0 : signedTxHex.hashCode());
    result = prime * result + ((unsignedTxHex == null) ? 0 : unsignedTxHex.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrTxSet other = (ZephyrTxSet) obj;
    if (multisigTxHex == null) {
      if (other.multisigTxHex != null) return false;
    } else if (!multisigTxHex.equals(other.multisigTxHex)) return false;
    if (signedTxHex == null) {
      if (other.signedTxHex != null) return false;
    } else if (!signedTxHex.equals(other.signedTxHex)) return false;
    if (unsignedTxHex == null) {
      if (other.unsignedTxHex != null) return false;
    } else if (!unsignedTxHex.equals(other.unsignedTxHex)) return false;
    return true;
  }
}
