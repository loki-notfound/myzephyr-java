package zephyr.wallet.model;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonProperty;

import zephyr.lib.utils.GenUtils;

/**
 * The reserve info of the Zephyr network at the current height, as the core's get_reserve_info
 * reports it.
 * 
 * Amounts are in atomic units and arrive from the native bridge as decimal strings (the core
 * computes them as 128-bit integers); the two ratios are dimensionless and keep the full double
 * precision of the core. The JSON keys are snake_case, exactly as the bridge emits them.
 */
public class ZephyrReserveInfo {

  private BigInteger zephReserve;
  private BigInteger numStables;
  private BigInteger numReserves;
  private BigInteger assets;
  private BigInteger assetsMa;
  private BigInteger liabilities;
  private BigInteger equity;
  private BigInteger equityMa;
  private BigDecimal reserveRatio;
  private BigDecimal reserveRatioMa;
  private Integer hfVersion;
  private BigInteger zyieldCirc;
  private BigInteger zyieldRsv;
  
  public ZephyrReserveInfo() {
    // nothing to construct
  }
  
  /**
   * Returns the amount of ZPH held in the reserve, in atomic units.
   * 
   * @return the amount of ZPH held in the reserve, in atomic units
   */
  @JsonProperty("zeph_reserve")
  public BigInteger getZephReserve() {
    return zephReserve;
  }
  
  /**
   * Sets the amount of ZPH held in the reserve, in atomic units.
   * 
   * @param zephReserve is the amount of ZPH held in the reserve, in atomic units
   */
  @JsonProperty("zeph_reserve")
  public void setZephReserve(BigInteger zephReserve) {
    this.zephReserve = zephReserve;
  }
  
  /**
   * Returns the circulating amount of the stable asset, in atomic units.
   * 
   * @return the circulating amount of the stable asset, in atomic units
   */
  @JsonProperty("num_stables")
  public BigInteger getNumStables() {
    return numStables;
  }
  
  /**
   * Sets the circulating amount of the stable asset, in atomic units.
   * 
   * @param numStables is the circulating amount of the stable asset, in atomic units
   */
  @JsonProperty("num_stables")
  public void setNumStables(BigInteger numStables) {
    this.numStables = numStables;
  }
  
  /**
   * Returns the circulating amount of the reserve asset, in atomic units.
   * 
   * @return the circulating amount of the reserve asset, in atomic units
   */
  @JsonProperty("num_reserves")
  public BigInteger getNumReserves() {
    return numReserves;
  }
  
  /**
   * Sets the circulating amount of the reserve asset, in atomic units.
   * 
   * @param numReserves is the circulating amount of the reserve asset, in atomic units
   */
  @JsonProperty("num_reserves")
  public void setNumReserves(BigInteger numReserves) {
    this.numReserves = numReserves;
  }
  
  /**
   * Returns the value of the reserve assets, in atomic units.
   * 
   * @return the value of the reserve assets, in atomic units
   */
  @JsonProperty("assets")
  public BigInteger getAssets() {
    return assets;
  }
  
  /**
   * Sets the value of the reserve assets, in atomic units.
   * 
   * @param assets is the value of the reserve assets, in atomic units
   */
  @JsonProperty("assets")
  public void setAssets(BigInteger assets) {
    this.assets = assets;
  }
  
  /**
   * Returns the moving average of the reserve assets, in atomic units.
   * 
   * @return the moving average of the reserve assets, in atomic units
   */
  @JsonProperty("assets_ma")
  public BigInteger getAssetsMa() {
    return assetsMa;
  }
  
  /**
   * Sets the moving average of the reserve assets, in atomic units.
   * 
   * @param assetsMa is the moving average of the reserve assets, in atomic units
   */
  @JsonProperty("assets_ma")
  public void setAssetsMa(BigInteger assetsMa) {
    this.assetsMa = assetsMa;
  }
  
  /**
   * Returns the liabilities of the reserve, in atomic units.
   * 
   * @return the liabilities of the reserve, in atomic units
   */
  @JsonProperty("liabilities")
  public BigInteger getLiabilities() {
    return liabilities;
  }
  
  /**
   * Sets the liabilities of the reserve, in atomic units.
   * 
   * @param liabilities is the liabilities of the reserve, in atomic units
   */
  @JsonProperty("liabilities")
  public void setLiabilities(BigInteger liabilities) {
    this.liabilities = liabilities;
  }
  
  /**
   * Returns the equity of the reserve, in atomic units.
   * 
   * @return the equity of the reserve, in atomic units
   */
  @JsonProperty("equity")
  public BigInteger getEquity() {
    return equity;
  }
  
  /**
   * Sets the equity of the reserve, in atomic units.
   * 
   * @param equity is the equity of the reserve, in atomic units
   */
  @JsonProperty("equity")
  public void setEquity(BigInteger equity) {
    this.equity = equity;
  }
  
  /**
   * Returns the moving average of the equity, in atomic units.
   * 
   * @return the moving average of the equity, in atomic units
   */
  @JsonProperty("equity_ma")
  public BigInteger getEquityMa() {
    return equityMa;
  }
  
  /**
   * Sets the moving average of the equity, in atomic units.
   * 
   * @param equityMa is the moving average of the equity, in atomic units
   */
  @JsonProperty("equity_ma")
  public void setEquityMa(BigInteger equityMa) {
    this.equityMa = equityMa;
  }
  
  /**
   * Returns the reserve ratio (dimensionless).
   * 
   * @return the reserve ratio (dimensionless)
   */
  @JsonProperty("reserve_ratio")
  public BigDecimal getReserveRatio() {
    return reserveRatio;
  }
  
  /**
   * Sets the reserve ratio (dimensionless).
   * 
   * @param reserveRatio is the reserve ratio (dimensionless)
   */
  @JsonProperty("reserve_ratio")
  public void setReserveRatio(BigDecimal reserveRatio) {
    this.reserveRatio = reserveRatio;
  }
  
  /**
   * Returns the moving average of the reserve ratio (dimensionless).
   * 
   * @return the moving average of the reserve ratio (dimensionless)
   */
  @JsonProperty("reserve_ratio_ma")
  public BigDecimal getReserveRatioMa() {
    return reserveRatioMa;
  }
  
  /**
   * Sets the moving average of the reserve ratio (dimensionless).
   * 
   * @param reserveRatioMa is the moving average of the reserve ratio (dimensionless)
   */
  @JsonProperty("reserve_ratio_ma")
  public void setReserveRatioMa(BigDecimal reserveRatioMa) {
    this.reserveRatioMa = reserveRatioMa;
  }
  
  /**
   * Returns the hard fork version at which the reserve info was computed.
   * 
   * @return the hard fork version at which the reserve info was computed
   */
  @JsonProperty("hf_version")
  public Integer getHfVersion() {
    return hfVersion;
  }
  
  /**
   * Sets the hard fork version at which the reserve info was computed.
   * 
   * @param hfVersion is the hard fork version at which the reserve info was computed
   */
  @JsonProperty("hf_version")
  public void setHfVersion(Integer hfVersion) {
    this.hfVersion = hfVersion;
  }
  
  /**
   * Returns the circulating amount of the yield asset, in atomic units.
   * 
   * @return the circulating amount of the yield asset, in atomic units
   */
  @JsonProperty("zyield_circ")
  public BigInteger getZyieldCirc() {
    return zyieldCirc;
  }
  
  /**
   * Sets the circulating amount of the yield asset, in atomic units.
   * 
   * @param zyieldCirc is the circulating amount of the yield asset, in atomic units
   */
  @JsonProperty("zyield_circ")
  public void setZyieldCirc(BigInteger zyieldCirc) {
    this.zyieldCirc = zyieldCirc;
  }
  
  /**
   * Returns the amount held in the yield reserve, in atomic units.
   * 
   * @return the amount held in the yield reserve, in atomic units
   */
  @JsonProperty("zyield_rsv")
  public BigInteger getZyieldRsv() {
    return zyieldRsv;
  }
  
  /**
   * Sets the amount held in the yield reserve, in atomic units.
   * 
   * @param zyieldRsv is the amount held in the yield reserve, in atomic units
   */
  @JsonProperty("zyield_rsv")
  public void setZyieldRsv(BigInteger zyieldRsv) {
    this.zyieldRsv = zyieldRsv;
  }
  
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(GenUtils.kvLine("ZPH reserve", this.getZephReserve(), indent));
    sb.append(GenUtils.kvLine("Num stables", this.getNumStables(), indent));
    sb.append(GenUtils.kvLine("Num reserves", this.getNumReserves(), indent));
    sb.append(GenUtils.kvLine("Assets", this.getAssets(), indent));
    sb.append(GenUtils.kvLine("Assets MA", this.getAssetsMa(), indent));
    sb.append(GenUtils.kvLine("Liabilities", this.getLiabilities(), indent));
    sb.append(GenUtils.kvLine("Equity", this.getEquity(), indent));
    sb.append(GenUtils.kvLine("Equity MA", this.getEquityMa(), indent));
    sb.append(GenUtils.kvLine("Reserve ratio", this.getReserveRatio(), indent));
    sb.append(GenUtils.kvLine("Reserve ratio MA", this.getReserveRatioMa(), indent));
    sb.append(GenUtils.kvLine("HF version", this.getHfVersion(), indent));
    sb.append(GenUtils.kvLine("Yield circulating", this.getZyieldCirc(), indent));
    sb.append(GenUtils.kvLine("Yield reserve", this.getZyieldRsv(), indent));
    String str = sb.toString();
    return str.isEmpty() ? str : str.substring(0, str.length() - 1);  // strip last newline
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((assets == null) ? 0 : assets.hashCode());
    result = prime * result + ((assetsMa == null) ? 0 : assetsMa.hashCode());
    result = prime * result + ((equity == null) ? 0 : equity.hashCode());
    result = prime * result + ((equityMa == null) ? 0 : equityMa.hashCode());
    result = prime * result + ((hfVersion == null) ? 0 : hfVersion.hashCode());
    result = prime * result + ((liabilities == null) ? 0 : liabilities.hashCode());
    result = prime * result + ((numReserves == null) ? 0 : numReserves.hashCode());
    result = prime * result + ((numStables == null) ? 0 : numStables.hashCode());
    result = prime * result + ((reserveRatio == null) ? 0 : reserveRatio.hashCode());
    result = prime * result + ((reserveRatioMa == null) ? 0 : reserveRatioMa.hashCode());
    result = prime * result + ((zephReserve == null) ? 0 : zephReserve.hashCode());
    result = prime * result + ((zyieldCirc == null) ? 0 : zyieldCirc.hashCode());
    result = prime * result + ((zyieldRsv == null) ? 0 : zyieldRsv.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ZephyrReserveInfo other = (ZephyrReserveInfo) obj;
    if (assets == null) {
      if (other.assets != null) return false;
    } else if (!assets.equals(other.assets)) return false;
    if (assetsMa == null) {
      if (other.assetsMa != null) return false;
    } else if (!assetsMa.equals(other.assetsMa)) return false;
    if (equity == null) {
      if (other.equity != null) return false;
    } else if (!equity.equals(other.equity)) return false;
    if (equityMa == null) {
      if (other.equityMa != null) return false;
    } else if (!equityMa.equals(other.equityMa)) return false;
    if (hfVersion == null) {
      if (other.hfVersion != null) return false;
    } else if (!hfVersion.equals(other.hfVersion)) return false;
    if (liabilities == null) {
      if (other.liabilities != null) return false;
    } else if (!liabilities.equals(other.liabilities)) return false;
    if (numReserves == null) {
      if (other.numReserves != null) return false;
    } else if (!numReserves.equals(other.numReserves)) return false;
    if (numStables == null) {
      if (other.numStables != null) return false;
    } else if (!numStables.equals(other.numStables)) return false;
    if (reserveRatio == null) {
      if (other.reserveRatio != null) return false;
    } else if (!reserveRatio.equals(other.reserveRatio)) return false;
    if (reserveRatioMa == null) {
      if (other.reserveRatioMa != null) return false;
    } else if (!reserveRatioMa.equals(other.reserveRatioMa)) return false;
    if (zephReserve == null) {
      if (other.zephReserve != null) return false;
    } else if (!zephReserve.equals(other.zephReserve)) return false;
    if (zyieldCirc == null) {
      if (other.zyieldCirc != null) return false;
    } else if (!zyieldCirc.equals(other.zyieldCirc)) return false;
    if (zyieldRsv == null) {
      if (other.zyieldRsv != null) return false;
    } else if (!zyieldRsv.equals(other.zyieldRsv)) return false;
    return true;
  }
}
