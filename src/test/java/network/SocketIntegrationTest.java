package network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import service.UserService;
import support.TestDatabase;

class SocketIntegrationTest {
  ChatServer server;
  ExecutorService executor;
  ChatClient alice, bob;
  BlockingQueue<Packet> aEvents, bEvents;

  @BeforeEach
  void setup() throws Exception {
    TestDatabase.reset();
    server = new ChatServer();
    executor = Executors.newSingleThreadExecutor();
    executor.submit(
        () -> {
          try {
            server.start(0);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (server.port() == 0 && System.nanoTime() < end) Thread.sleep(10);
    assertTrue(server.port() > 0);
    aEvents = new LinkedBlockingQueue<>();
    bEvents = new LinkedBlockingQueue<>();
    alice = new ChatClient("127.0.0.1", server.port(), aEvents::add);
    bob = new ChatClient("127.0.0.1", server.port(), bEvents::add);
  }

  FrameCodec.Frame call(ChatClient c, String type, Object data) throws Exception {
    return c.request(type, data).get(10, TimeUnit.SECONDS);
  }

  long login(ChatClient c, String name) throws Exception {
    call(c, "REGISTER", Map.of("username", name, "password", "strong-pass"));
    return call(c, "LOGIN", Map.of("username", name, "password", "strong-pass"))
        .packet()
        .data()
        .getAsJsonObject("user")
        .get("id")
        .getAsLong();
  }

  Packet event(BlockingQueue<Packet> q, String type) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < end) {
      Packet p = q.poll(100, TimeUnit.MILLISECONDS);
      if (p != null && p.type().equals(type)) return p;
    }
    fail("Missing event: " + type);
    return null;
  }

  @AfterEach
  void stop() throws Exception {
    if (alice != null) alice.close();
    if (bob != null) bob.close();
    server.close();
    executor.shutdownNow();
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
  }

  @Test
  void twoClientsChatTypeAcknowledgeAndShareFiles() throws Exception {
    long a = login(alice, "alice"), b = login(bob, "bob");
    call(alice, "SEND", Map.of("target", b, "text", "hello", "group", false));
    Packet message = event(bEvents, "MESSAGE");
    long id = message.data().getAsJsonObject("message").get("id").getAsLong();
    call(bob, "DELIVERED", Map.of("messageId", id));
    assertEquals(
        "DELIVERED",
        event(aEvents, "RECEIPT").data().getAsJsonObject("message").get("status").getAsString());
    call(bob, "SEEN", Map.of("messageId", id));
    assertEquals(
        "SEEN",
        event(aEvents, "RECEIPT").data().getAsJsonObject("message").get("status").getAsString());
    call(alice, "TYPING", Map.of("target", b));
    assertEquals(a, event(bEvents, "TYPING").number("userId"));
    call(alice, "STOP_TYPING", Map.of("target", b));
    event(bEvents, "STOP_TYPING");
    byte[] bytes = {0, 1, 2, 3};
    var upload =
        alice
            .request("UPLOAD", Map.of("target", b, "name", "test.txt"), bytes)
            .get(10, TimeUnit.SECONDS);
    long attachment =
        upload
            .packet()
            .data()
            .getAsJsonObject("message")
            .getAsJsonObject("attachment")
            .get("id")
            .getAsLong();
    assertArrayEquals(bytes, call(bob, "DOWNLOAD", Map.of("attachment", attachment)).binary());
  }

  @Test
  void offlineDeliveryAndDuplicateSessionProtection() throws Exception {
    long a = login(alice, "alice");
    long b = new UserService().register("bob", "strong-pass").id();
    call(alice, "SEND", Map.of("target", b, "text", "saved offline"));
    call(bob, "LOGIN", Map.of("username", "bob", "password", "strong-pass"));
    assertEquals(
        "saved offline",
        event(bEvents, "MESSAGE").data().getAsJsonObject("message").get("text").getAsString());
    try (ChatClient duplicate = new ChatClient("127.0.0.1", server.port(), p -> {})) {
      assertThrows(
          ExecutionException.class,
          () -> call(duplicate, "LOGIN", Map.of("username", "alice", "password", "strong-pass")));
    }
    bob.close();
    Packet offline;
    do {
      offline = event(aEvents, "PRESENCE");
    } while (offline.flag("online"));
    assertEquals(b, offline.number("userId"));
  }

  @Test
  void groupRoutingAndMembershipGuard() throws Exception {
    long a = login(alice, "alice"), b = login(bob, "bob");
    long g =
        call(alice, "CREATE_GROUP", Map.of("name", "Team", "members", List.of(b)))
            .packet()
            .number("id");
    call(bob, "SEND", Map.of("target", g, "group", true, "text", "team message"));
    assertTrue(
        event(aEvents, "MESSAGE").data().getAsJsonObject("message").get("group").getAsBoolean());
    call(alice, "REMOVE_MEMBER", Map.of("target", g, "userId", b));
    assertThrows(
        ExecutionException.class, () -> call(bob, "HISTORY", Map.of("target", g, "group", true)));
  }
}
