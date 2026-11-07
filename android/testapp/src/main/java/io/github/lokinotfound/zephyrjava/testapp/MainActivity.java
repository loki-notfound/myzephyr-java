package io.github.lokinotfound.zephyrjava.testapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Loads zephyr-java's native library and shows the native validation of the address vectors.
 */
public class MainActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    TextView view = new TextView(this);
    view.setPadding(32, 96, 32, 32);
    view.setText(AddressVectors.run());
    setContentView(view);
  }
}
