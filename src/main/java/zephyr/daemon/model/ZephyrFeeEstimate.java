package zephyr.daemon.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import zephyr.lib.utils.GenUtils;

/**
 * Models a Zephyr fee estimate.
 */
public class ZephyrFeeEstimate {

  private BigInteger fee;
  private List<BigInteger> fees;
  private BigInteger quantizationMask;
  
  public ZephyrFeeEstimate() {
    // nothing to construct
  }

  public ZephyrFeeEstimate(BigInteger fee, List<BigInteger> fees, BigInteger quantizationMask) {
    this.fee = fee;
    this.fees = fees;
    this.quantizationMask = quantizationMask;
  }

  public ZephyrFeeEstimate(ZephyrFeeEstimate feeEstimate) {
    this.fee = feeEstimate.fee;
    this.fees = new ArrayList<BigInteger>(feeEstimate.fees);
    this.quantizationMask = feeEstimate.quantizationMask;
  }
  
  public BigInteger getFee() {
    return fee;
  }
  
  public ZephyrFeeEstimate setFee(BigInteger fee) {
    this.fee = fee;
    return this;
  }
  
  public List<BigInteger> getFees() {
    return fees;
  }
  
  public ZephyrFeeEstimate setFees(List<BigInteger> fees) {
    this.fees = fees;
    return this;
  }
  
  public BigInteger getQuantizationMask() {
    return quantizationMask;
  }
  
  public ZephyrFeeEstimate setQuantizationMask(BigInteger quantizationMask) {
    this.quantizationMask = quantizationMask;
    return this;
  }
  
  public ZephyrFeeEstimate copy() {
    return new ZephyrFeeEstimate(this);
  }
  
  @Override
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(GenUtils.kvLine("Fee", getFee(), indent));
    sb.append(GenUtils.kvLine("Fees", getFees(), indent));
    sb.append(GenUtils.kvLine("Quantization mask", getQuantizationMask(), indent));
    String str = sb.toString();
    return str.substring(0, str.length() - 1);  // strip newline
  }
}
