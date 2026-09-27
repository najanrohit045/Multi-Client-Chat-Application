package service;

import static org.junit.jupiter.api.Assertions.*;

import app.MigrateLegacy;
import database.*;
import org.junit.jupiter.api.*;
import support.TestDatabase;

class MigrationTest {
  @BeforeEach
  void setup() throws Exception {
    TestDatabase.reset();
    try (var c = DBConnection.getConnection()) {
      Sql.update(c, "CREATE SCHEMA chat_app");
      Sql.update(
          c,
          "CREATE TABLE chat_app.users(id INT PRIMARY KEY,username VARCHAR(50),password"
              + " VARCHAR(255),role VARCHAR(10))");
      Sql.update(
          c,
          "CREATE TABLE chat_app.messages(id INT PRIMARY KEY,sender VARCHAR(50),receiver"
              + " VARCHAR(50),message TEXT,timestamp TIMESTAMP)");
      Sql.update(
          c,
          "INSERT INTO chat_app.users"
              + " VALUES(1,'Alice','short','ADMIN'),(2,'bob','old-password','USER')");
      Sql.update(
          c,
          "INSERT INTO chat_app.messages VALUES(1,'Alice','bob','original"
              + " private',CURRENT_TIMESTAMP),(2,'bob','ALL','original"
              + " broadcast',CURRENT_TIMESTAMP)");
    }
  }

  @Test
  void importPreservesHistoryAndHashesLegacyPasswords() throws Exception {
    MigrateLegacy.main(new String[] {"--import-and-hash-legacy"});
    var a = new UserService().login("alice", "short");
    var b = new UserService().login("bob", "old-password");
    assertEquals("ADMIN", a.role());
    assertEquals(
        "original private", new ChatService().history(a.id(), b.id(), false, "", 0).get(0).text());
    assertEquals(
        "original broadcast", new ChatService().history(a.id(), 1, true, "", 0).get(0).text());
    try (var c = DBConnection.getConnection()) {
      assertTrue(
          Sql.query(c, "SELECT password FROM chat_app.users WHERE id=1", r -> r.getString(1))
              .get(0)
              .startsWith("$2a$"));
    }
  }

  @Test
  void orphanedHistoryRollsBackUsersAndPasswordChanges() throws Exception {
    try (var c = DBConnection.getConnection()) {
      Sql.update(
          c, "INSERT INTO chat_app.messages VALUES(3,'missing','bob','orphan',CURRENT_TIMESTAMP)");
    }
    assertThrows(
        IllegalStateException.class,
        () -> MigrateLegacy.main(new String[] {"--import-and-hash-legacy"}));
    assertTrue(new UserService().search("").isEmpty());
    try (var c = DBConnection.getConnection()) {
      assertEquals(
          "short",
          Sql.query(c, "SELECT password FROM chat_app.users WHERE id=1", r -> r.getString(1))
              .get(0));
    }
  }
}
