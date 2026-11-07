package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import zephyr.wallet.ZephyrWallet;
import zephyr.wallet.model.ZephyrAccount;
import zephyr.wallet.model.ZephyrAssets;
import zephyr.wallet.model.ZephyrSubaddress;

// the balance checks read the base coin ZPH only; the tests do not cover the other assets yet

/**
 * Prints the balances of the wallet.
 */
public class PrintBalances {

  public static void main(String[] args) {
    printBalances();
  }
  
  public static void printBalances() {
    printBalances(null);
  }
  
  public static void printBalances(ZephyrWallet wallet) {
    
    // collect info about subaddresses
    List<Pair<String, List<Object>>> pairs = new ArrayList<Pair<String, List<Object>>>();
    if (wallet == null) wallet = TestUtils.getWalletRpc();
    //if (wallet == null) wallet = TestUtils.getWalletFull();
    BigInteger balance = wallet.getBalance().get(ZephyrAssets.ZPH);
    BigInteger unlockedBalance = wallet.getUnlockedBalance().get(ZephyrAssets.ZPH);
    List<ZephyrAccount> accounts = wallet.getAccounts(true);
    System.out.println("Wallet balance: " + balance);
    System.out.println("Wallet unlocked balance: " + unlockedBalance);
    for (ZephyrAccount account : accounts) {
      add(pairs, "ACCOUNT", account.getIndex());
      add(pairs, "SUBADDRESS", "");
      add(pairs, "LABEL", "");
      add(pairs, "ADDRESS", "");
      add(pairs, "BALANCE", account.getBalance());
      add(pairs, "UNLOCKED", account.getUnlockedBalance());
      for (ZephyrSubaddress subaddress : account.getSubaddresses()) {
        add(pairs, "ACCOUNT", account.getIndex());
        add(pairs, "SUBADDRESS", subaddress.getIndex());
        add(pairs, "LABEL", subaddress.getLabel());
        add(pairs, "ADDRESS", subaddress.getAddress());
        add(pairs, "BALANCE", subaddress.getBalance());
        add(pairs, "UNLOCKED", subaddress.getUnlockedBalance());
      }
    }
    
    // convert info to csv
    Integer length = null;
    for (Pair<String, List<Object>> pair : pairs) {
      if (length == null) length = pair.getSecond().size();
      else assertEquals((int) length, (int) pair.getSecond().size());
    }
    
    System.out.println(pairsToCsv(pairs));
  }
  
  private static void add(List<Pair<String, List<Object>>> pairs, String header, Object value) {
    if (value == null) value = "";
    Pair<String, List<Object>> pair = null;
    for (Pair<String, List<Object>> aPair : pairs) {
      if (aPair.getFirst().equals(header)) {
        pair = aPair;
        break;
      }
    }
    if (pair == null) {
      List<Object> vals = new ArrayList<Object>();
      pair = new Pair<String, List<Object>>(header, vals);
      pairs.add(pair);
    }
    assertNotNull(value);
    pair.getSecond().add(value);
  }
  
  private static String pairsToCsv(List<Pair<String, List<Object>>> pairs) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < pairs.size(); i++) {
      sb.append(pairs.get(i).getFirst());
      if (i < pairs.size() - 1) sb.append(',');
      else sb.append('\n');
    }
    for (int i = 0; i < pairs.get(0).getSecond().size(); i++) {
      for (int j = 0; j < pairs.size(); j++) {
        sb.append(pairs.get(j).getSecond().get(i));
        if (j < pairs.size() - 1) sb.append(',');
        else sb.append('\n');
      }
    }
    return sb.toString();
  }
}
