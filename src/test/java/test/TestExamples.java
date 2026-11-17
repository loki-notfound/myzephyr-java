package test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  /** A code block of the guide whose info string is "java examples/<Class>.java". */
  private static final Pattern NAMED_BLOCK = Pattern.compile("^```java examples/([A-Za-z0-9]+)\\.java\\n(.*?)\\n```$", Pattern.MULTILINE | Pattern.DOTALL);

  /**
   * Every Java block of the guide (docs/guide/*.md) whose info string names a compiled example,
   * "java examples/<Class>.java", is found verbatim in src/test/java/examples/<Class>.java
   * (compared with every run of whitespace reduced to one space). Blocks without a source name are
   * not checked.
   */
  @Test
  public void guideQuotesItsCompiledExamples() throws IOException {
    List<Path> chapters = new ArrayList<>();
    try (DirectoryStream<Path> dir = Files.newDirectoryStream(projectFile("docs/guide"), "*.md")) {
      for (Path chapter : dir) chapters.add(chapter);
    }
    int checked = 0;
    List<String> drifted = new ArrayList<>();
    for (Path chapter : chapters) {
      String text = new String(Files.readAllBytes(chapter), StandardCharsets.UTF_8).replace("\r\n", "\n");
      Matcher block = NAMED_BLOCK.matcher(text);
      while (block.find()) {
        Path source = projectFile("src/test/java/examples/" + block.group(1) + ".java");
        String line = chapter.getFileName() + ":" + (text.substring(0, block.start()).split("\n", -1).length);
        if (!Files.isRegularFile(source)) {
          drifted.add(line + " names " + source + ", which does not exist");
        } else if (!normalise(new String(Files.readAllBytes(source), StandardCharsets.UTF_8)).contains(normalise(block.group(2)))) {
          drifted.add(line + " is not verbatim in " + source.getFileName());
        }
        checked++;
      }
    }
    assertTrue(checked > 0, "no Java block of the guide names its compiled example");
    assertTrue(drifted.isEmpty(), "guide code that differs from its compiled example: " + drifted);
  }
}
