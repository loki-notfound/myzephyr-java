/**
 * Copyright (c) woodser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package zephyr.daemon;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import zephyr.daemon.model.ZephyrBan;
import zephyr.daemon.model.ZephyrBlock;
import zephyr.daemon.model.ZephyrBlockTemplate;
import zephyr.daemon.model.ZephyrDaemonUpdateDownloadResult;
import zephyr.daemon.model.ZephyrFeeEstimate;
import zephyr.daemon.model.ZephyrKeyImageSpentStatus;
import zephyr.daemon.model.ZephyrOutputDistributionEntry;
import zephyr.daemon.model.ZephyrSubmitTxResult;
import zephyr.daemon.model.ZephyrTx;

/**
 * Abstract default implementation of a Zephyr daemon.
 */
public abstract class ZephyrDaemonDefault implements ZephyrDaemon {
  
  @Override
  public ZephyrBlockTemplate getBlockTemplate(String walletAddress) {
    return getBlockTemplate(walletAddress, null);
  }
  
  @Override
  public List<ZephyrBlock> getBlocksByRangeChunked(Long startHeight, Long endHeight) {
    return getBlocksByRangeChunked(startHeight, endHeight, null);
  }
  
  @Override
  public ZephyrTx getTx(String txHash) {
    return getTx(txHash, null);
  }
  
  @Override
  public ZephyrTx getTx(String txHash, Boolean prune) {
    List<ZephyrTx> txs = getTxs(Arrays.asList(txHash), prune);
    return txs.size() == 0 ? null : txs.get(0);
  }
  
  @Override
  public List<ZephyrTx> getTxs(Collection<String> txHashes) {
    return getTxs(txHashes, null);
  }
  
  @Override
  public String getTxHex(String txHash) {
    return getTxHex(txHash, false);
  }
  
  @Override
  public String getTxHex(String txHash, Boolean prune) {
    return getTxHexes(Arrays.asList(txHash), prune).get(0);
  }
  
  @Override
  public List<String> getTxHexes(Collection<String> txHashes) {
    return getTxHexes(txHashes, null);
  }
  
  @Override
  public ZephyrFeeEstimate getFeeEstimate() {
    return getFeeEstimate(null);
  }
  
  @Override
  public ZephyrSubmitTxResult submitTxHex(String txHex) {
    return submitTxHex(txHex, false);
  }
  
  @Override
  public void relayTxByHash(String txHash) {
    relayTxsByHash(Arrays.asList(txHash));
  }
  
  @Override
  public ZephyrKeyImageSpentStatus getKeyImageSpentStatus(String keyImage) {
    return getKeyImageSpentStatuses(Arrays.asList(keyImage)).get(0);
  }
  
  @Override
  public List<ZephyrOutputDistributionEntry> getOutputDistribution(Collection<BigInteger> amounts) {
    return getOutputDistribution(amounts, null, null, null);
  }
  
  @Override
  public void setPeerBan(ZephyrBan ban) {
    setPeerBans(Arrays.asList(ban));
  }
  
  @Override
  public void submitBlock(String blockBlob) {
    submitBlocks(Arrays.asList(blockBlob));
  }
  
  @Override
  public ZephyrDaemonUpdateDownloadResult downloadUpdate() {
    return downloadUpdate(null);
  }
}
