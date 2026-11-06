package zephyr.wallet.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import zephyr.lib.types.Filter;
import zephyr.lib.utils.GenUtils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import zephyr.daemon.model.ZephyrKeyImage;
import zephyr.daemon.model.ZephyrTx;

/**
 * Configures a query to retrieve wallet outputs (i.e. outputs that the wallet has or had the
 * ability to spend).
 * 
 * All outputs are returned except those that do not meet the criteria defined in this query.
 */
public class ZephyrOutputQuery extends ZephyrOutputWallet implements Filter<ZephyrOutputWallet> {

  protected ZephyrTxQuery txQuery;
  private List<Integer> subaddressIndices;
  private BigInteger minAmount;
  private BigInteger maxAmount;
  
  public ZephyrOutputQuery() {
    super();
  }
  
  public ZephyrOutputQuery(final ZephyrOutputQuery query) {
    super(query);
    if (query.getMinAmount() != null) this.minAmount = query.getMinAmount();
    if (query.getMaxAmount() != null) this.maxAmount = query.getMaxAmount();
    if (query.subaddressIndices != null) this.subaddressIndices = new ArrayList<Integer>(query.subaddressIndices);
    this.txQuery = query.txQuery;  // reference original by default, ZephyrTxQuery's deep copy will set this to itself
  }
  
  @Override
  public ZephyrOutputQuery copy() {
    return new ZephyrOutputQuery(this);
  }
  
  public BigInteger getMinAmount() {
    return minAmount;
  }

  public ZephyrOutputQuery setMinAmount(BigInteger minAmount) {
    this.minAmount = minAmount;
    return this;
  }

  public BigInteger getMaxAmount() {
    return maxAmount;
  }

  public ZephyrOutputQuery setMaxAmount(BigInteger maxAmount) {
    this.maxAmount = maxAmount;
    return this;
  }

  @JsonIgnore
  public ZephyrTxQuery getTxQuery() {
    return txQuery;
  }

  public ZephyrOutputQuery setTxQuery(ZephyrTxQuery txQuery) {
    this.txQuery = txQuery;
    if (txQuery != null) txQuery.outputQuery = this;
    return this;
  }
  
  public List<Integer> getSubaddressIndices() {
    return subaddressIndices;
  }

  public ZephyrOutputQuery setSubaddressIndices(List<Integer> subaddressIndices) {
    this.subaddressIndices = subaddressIndices;
    return this;
  }
  
  public ZephyrOutputQuery setSubaddressIndices(Integer... subaddressIndices) {
    this.subaddressIndices = GenUtils.arrayToList(subaddressIndices);
    return this;
  }
  
  @Override
  public boolean meetsCriteria(ZephyrOutputWallet output) {
    return meetsCriteria(output, true);
  }
    
  protected boolean meetsCriteria(ZephyrOutputWallet output, boolean queryParent) {

    if (!(output instanceof ZephyrOutputWallet)) return false;
    
    // filter on output
    if (this.getAccountIndex() != null && !this.getAccountIndex().equals(output.getAccountIndex())) return false;
    if (this.getSubaddressIndex() != null && !this.getSubaddressIndex().equals(output.getSubaddressIndex())) return false;
    if (this.getAmount() != null && this.getAmount().compareTo(output.getAmount()) != 0) return false;
    if (this.isSpent() != null && !this.isSpent().equals(output.isSpent())) return false;
    if (this.isFrozen() != null && !this.isFrozen().equals(output.isFrozen())) return false;
    
    // filter on output key image
    if (this.getKeyImage() != null) {
      if (output.getKeyImage() == null) return false;
      if (this.getKeyImage().getHex() != null && !this.getKeyImage().getHex().equals(output.getKeyImage().getHex())) return false;
      if (this.getKeyImage().getSignature() != null && !this.getKeyImage().getSignature().equals(output.getKeyImage().getSignature())) return false;
    }
    
    // filter on extensions
    if (this.getSubaddressIndices() != null && !this.getSubaddressIndices().contains(output.getSubaddressIndex())) return false;
    
    // filter with tx query
    if (this.getTxQuery() != null && !this.getTxQuery().meetsCriteria(output.getTx(), false)) return false;
    
    // filter on remaining fields
    if (this.getMinAmount() != null && (output.getAmount() == null || output.getAmount().compareTo(this.getMinAmount()) < 0)) return false;
    if (this.getMaxAmount() != null && (output.getAmount() == null || output.getAmount().compareTo(this.getMaxAmount()) > 0)) return false;
    
    // output meets query
    return true;
  }
  
  // ------------------- OVERRIDE CO-VARIANT RETURN TYPES ---------------------

  @Override
  public ZephyrOutputQuery setTx(ZephyrTx tx) {
    super.setTx(tx);
    return this;
  }

  @Override
  public ZephyrOutputQuery setTx(ZephyrTxWallet tx) {
    super.setTx(tx);
    return this;
  }

  @Override
  public ZephyrOutputQuery setAccountIndex(Integer accountIndex) {
    super.setAccountIndex(accountIndex);
    return this;
  }

  @Override
  public ZephyrOutputQuery setSubaddressIndex(Integer subaddressIndex) {
    super.setSubaddressIndex(subaddressIndex);
    return this;
  }

  @Override
  public ZephyrOutputQuery setIsSpent(Boolean isSpent) {
    super.setIsSpent(isSpent);
    return this;
  }
  
  @Override
  public ZephyrOutputQuery setIsFrozen(Boolean isFrozen) {
    super.setIsFrozen(isFrozen);
    return this;
  }
  
  @Override
  public ZephyrOutputQuery setKeyImage(ZephyrKeyImage keyImage) {
    super.setKeyImage(keyImage);
    return this;
  }

  @Override
  public ZephyrOutputQuery setAmount(BigInteger amount) {
    super.setAmount(amount);
    return this;
  }

  @Override
  public ZephyrOutputQuery setIndex(Long index) {
    super.setIndex(index);
    return this;
  }

  @Override
  public ZephyrOutputQuery setRingOutputIndices(List<Long> ringOutputIndices) {
    super.setRingOutputIndices(ringOutputIndices);
    return this;
  }

  @Override
  public ZephyrOutputQuery setStealthPublicKey(String stealthPublicKey) {
    super.setStealthPublicKey(stealthPublicKey);
    return this;
  }
}
