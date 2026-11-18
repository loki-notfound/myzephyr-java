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

  /**
   * The transaction query that the transaction of a matching output meets, or null.
   */
  protected ZephyrTxQuery txQuery;
  private List<Integer> subaddressIndices;
  private BigInteger minAmount;
  private BigInteger maxAmount;
  
  /**
   * Creates a query that every wallet output meets.
   */
  public ZephyrOutputQuery() {
    super();
  }
  
  /**
   * Creates a copy of the given query; the copy refers to the same transaction query.
   *
   * @param query is the query to copy
   */
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
  
  /**
   * Returns the smallest output amount to match, in atomic units, inclusive.
   *
   * @return the amount, null for no lower bound
   */
  public BigInteger getMinAmount() {
    return minAmount;
  }

  /**
   * Sets the smallest output amount to match, in atomic units, inclusive. The outputs carry no
   * asset in {@code ZephyrWalletFull}, so the bound applies to outputs of every asset alike.
   *
   * @param minAmount is the amount, null for no lower bound
   * @return this query for chaining
   */
  public ZephyrOutputQuery setMinAmount(BigInteger minAmount) {
    this.minAmount = minAmount;
    return this;
  }

  /**
   * Returns the largest output amount to match, in atomic units, inclusive.
   *
   * @return the amount, null for no upper bound
   */
  public BigInteger getMaxAmount() {
    return maxAmount;
  }

  /**
   * Sets the largest output amount to match, in atomic units, inclusive. The outputs carry no asset
   * in {@code ZephyrWalletFull}, so the bound applies to outputs of every asset alike.
   *
   * @param maxAmount is the amount, null for no upper bound
   * @return this query for chaining
   */
  public ZephyrOutputQuery setMaxAmount(BigInteger maxAmount) {
    this.maxAmount = maxAmount;
    return this;
  }

  /**
   * Returns the transaction query that the transaction of a matching output meets.
   *
   * @return the transaction query, null if none
   */
  @JsonIgnore
  public ZephyrTxQuery getTxQuery() {
    return txQuery;
  }

  /**
   * Sets the transaction query that the transaction of a matching output meets, and makes this
   * query its output query.
   *
   * @param txQuery is the transaction query, null for none
   * @return this query for chaining
   */
  public ZephyrOutputQuery setTxQuery(ZephyrTxQuery txQuery) {
    this.txQuery = txQuery;
    if (txQuery != null) txQuery.outputQuery = this;
    return this;
  }
  
  /**
   * Returns the subaddress indices to match: an output matches if it was received at one of them.
   *
   * @return the subaddress indices, null for any
   */
  public List<Integer> getSubaddressIndices() {
    return subaddressIndices;
  }

  /**
   * Sets the subaddress indices to match: an output matches if it was received at one of them.
   *
   * @param subaddressIndices are the subaddress indices, null for any
   * @return this query for chaining
   */
  public ZephyrOutputQuery setSubaddressIndices(List<Integer> subaddressIndices) {
    this.subaddressIndices = subaddressIndices;
    return this;
  }
  
  /**
   * Sets the subaddress indices to match, as {@link #setSubaddressIndices(List)} does.
   *
   * @param subaddressIndices are the subaddress indices
   * @return this query for chaining
   */
  public ZephyrOutputQuery setSubaddressIndices(Integer... subaddressIndices) {
    this.subaddressIndices = GenUtils.arrayToList(subaddressIndices);
    return this;
  }
  
  @Override
  public boolean meetsCriteria(ZephyrOutputWallet output) {
    return meetsCriteria(output, true);
  }
    
  /**
   * Returns whether the given output meets this query, evaluated in Java.
   *
   * @param output is the output to test
   * @param queryParent is not used: the transaction query, if there is one, is always applied
   * @return true if the output meets the query
   */
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
