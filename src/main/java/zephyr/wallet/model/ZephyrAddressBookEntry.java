package zephyr.wallet.model;

/**
 * Zephyr address book entry model.
 */
public class ZephyrAddressBookEntry {
  
  private int index;
  private String address;
  private String paymentId;
  private String description;
  
  public ZephyrAddressBookEntry() {
    // necessary for deserialization
  }
  
  public ZephyrAddressBookEntry(int index, String address, String description, String paymentId) {
    super();
    this.index = index;
    this.address = address;
    this.paymentId = paymentId;
    this.description = description;
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getPaymentId() {
    return paymentId;
  }

  public void setPaymentId(String paymentId) {
    this.paymentId = paymentId;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
  
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Index: " + index + "\n");
    sb.append("Address: " + address + "\n");
    sb.append("Payment ID: " + paymentId + "\n");
    sb.append("Description: " + description);
    return sb.toString();
  }
}
