package util;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Audit metadata only: never records passwords or private message content. */
public final class FileManager {
  private static final Path FILE = Path.of("audit.log");

  public static synchronized void audit(long user, String action) {
    try {
      Files.writeString(
          FILE,
          Instant.now() + " user=" + user + " action=" + action + System.lineSeparator(),
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      System.err.println("Could not write audit metadata");
    }
  }

  public static synchronized List<String> recent() throws IOException {
    if (!Files.exists(FILE)) return List.of();
    try (var lines = Files.lines(FILE)) {
      ArrayDeque<String> tail = new ArrayDeque<>();
      lines.forEach(
          line -> {
            if (tail.size() == 200) tail.removeFirst();
            tail.addLast(line);
          });
      return List.copyOf(tail);
    }
  }
}
