package test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * Keeps the documentation and the compiled examples together. Pure: no native library, no node.
 */
public class TestExamples {

  private static Path projectFile(String relative) {
    return Paths.get(System.getProperty("basedir", ".")).resolve(relative);
  }

  private static String normalise(String text) {
    return text.replace("\r\n", "\n").replaceAll("\\s+", " ").trim();
  }

  /**
   * The README's "Quick start" quotes src/test/java/examples/QuickStart.java verbatim (compared
   * with every run of whitespace reduced to one space), so the two cannot drift apart.
   */
  @Test
  public void readmeQuotesQuickStart() throws IOException {
    String example = new String(Files.readAllBytes(projectFile("src/test/java/examples/QuickStart.java")), StandardCharsets.UTF_8);
    String readme = new String(Files.readAllBytes(projectFile("README.md")), StandardCharsets.UTF_8);
    assertTrue(normalise(readme).contains(normalise(example)), "README.md does not quote src/test/java/examples/QuickStart.java verbatim");
  }
}
