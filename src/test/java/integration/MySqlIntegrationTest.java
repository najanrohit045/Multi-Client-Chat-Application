package integration;

import static org.junit.jupiter.api.Assertions.*;

import database.*;
import java.util.*;
import java.util.concurrent.*;
import model.*;
import network.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import service.*;

@EnabledIfEnvironmentVariable(named = "CHAT_TEST_MYSQL", matches = "true")
class MySqlIntegrationTest {
  @Test
  void mysqlSchemaAndSocketWorkflows() throws Exception {
    UserService users = new UserService();
    ChatService chat = new ChatService();
    GroupService groups = new GroupService();
    String suffix = Long.toString(System.nanoTime());
    User a = users.register("a" + suffix, "test-password"),
        b = users.register("b" + suffix, "test-password");
    ChatServer server = new ChatServer();
    ExecutorService pool = Executors.newSingleThreadExecutor();
    Future<?> running =
        pool.submit(
            () -> {
              try {
                server.start(0);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    try {
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (server.port() == 0 && System.nanoTime() < end) Thread.sleep(10);
      try (ChatClient first = new ChatClient("127.0.0.1", server.port(), p -> {});
          ChatClient second = new ChatClient("127.0.0.1", server.port(), p -> {})) {
        first
            .request("LOGIN", Map.of("username", a.username(), "password", "test-password"))
            .get(10, TimeUnit.SECONDS);
        second
            .request("LOGIN", Map.of("username", b.username(), "password", "test-password"))
            .get(10, TimeUnit.SECONDS);
        var frame =
            first
                .request("SEND", Map.of("target", b.id(), "text", "MySQL interview 😊"))
                .get(10, TimeUnit.SECONDS);
        long message = frame.packet().data().getAsJsonObject("message").get("id").getAsLong();
        second.request("SEEN", Map.of("messageId", message)).get(10, TimeUnit.SECONDS);
        assertEquals("SEEN", chat.history(a.id(), b.id(), false, "interview", 0).get(0).status());
        assertEquals(1, chat.summaries(a.id()).size());
        long group = groups.create(a.id(), "MySQL team", List.of(b.id()));
        chat.send(a.id(), group, true, "persisted group");
        assertEquals(1, chat.history(b.id(), group, true, "persist", 0).size());
        FileService files = new FileService();
        Message file = files.upload(a.id(), b.id(), false, "mysql.txt", new byte[] {0, 1, 2});
        assertArrayEquals(new byte[] {0, 1, 2}, files.download(b.id(), file.attachment().id()));
        try (var c = DBConnection.getConnection()) {
          assertTrue(new dao.UserDAO().credentials(c, a.username()).hash().startsWith("$2a$12$"));
        }
      }
    } finally {
      server.close();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }
  }
}
