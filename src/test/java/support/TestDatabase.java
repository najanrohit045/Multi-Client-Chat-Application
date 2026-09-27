package support;

import database.*;
import java.nio.file.*;
import java.sql.*;
import java.util.UUID;

public final class TestDatabase {
  public static void reset() throws Exception {
    System.setProperty(
        "DB_URL",
        "jdbc:h2:mem:chat"
            + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    System.setProperty("DB_USERNAME", "sa");
    System.setProperty("DB_PASSWORD", "");
    System.setProperty("CHAT_FILES", Files.createTempDirectory("chat-test-files").toString());
    String schema =
        Files.readString(Path.of("schema.sql"))
            .replaceAll("(?m)^CREATE DATABASE.*$", "")
            .replaceAll("(?m)^USE .*$", "")
            .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
    try (Connection c = DBConnection.getConnection()) {
      for (String statement : schema.split(";")) {
        if (!statement.isBlank())
          try (PreparedStatement p = c.prepareStatement(statement)) {
            p.execute();
          }
      }
    }
  }
}
