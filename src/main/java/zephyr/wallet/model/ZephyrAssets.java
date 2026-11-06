package zephyr.wallet.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The V2 asset names of Zephyr, in use since hard fork 11.
 *
 * The asset is an open string everywhere in this library. The V1 names (ZEPH, ZEPHUSD, ZEPHRSV,
 * ZYIELD) can still appear in the history of wallets restored from before that fork and as
 * leftover balance entries; they pass through unchanged and have no constants here.
 */
public final class ZephyrAssets {

  /** The base coin. */
  public static final String ZPH = "ZPH";

  /** The stable asset. */
  public static final String ZSD = "ZSD";

  /** The reserve asset. */
  public static final String ZRS = "ZRS";

  /** The yield asset. */
  public static final String ZYS = "ZYS";

  /** The four V2 asset names, in the order ZPH, ZSD, ZRS, ZYS (unmodifiable). */
  public static final List<String> V2 = Collections.unmodifiableList(Arrays.asList(ZPH, ZSD, ZRS, ZYS));

  private ZephyrAssets() {
    // no instances
  }
}
