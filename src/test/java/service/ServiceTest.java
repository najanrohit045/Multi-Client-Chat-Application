package service;

import static org.junit.jupiter.api.Assertions.*;

import dao.*;
import database.*;
import exception.ChatException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import model.*;
import org.junit.jupiter.api.*;
import org.mindrot.jbcrypt.BCrypt;
import support.TestDatabase;

class ServiceTest {
  UserService users;
  ChatService chat;
  GroupService groups;
  User alice, bob, eve;

  @BeforeEach
  void setup() throws Exception {
    TestDatabase.reset();
    users = new UserService();
    chat = new ChatService();
    groups = new GroupService();
    alice = users.register("alice", "strong-pass");
    bob = users.register("bob", "strong-pass");
    eve = users.register("eve", "strong-pass");
  }

  @Test
  void historyAndPendingPagesRetainEveryMessage() throws Exception {
    for (int i = 0; i < 85; i++) chat.send(alice.id(), bob.id(), false, "message " + i);
    long after = 0;
    int count = 0;
    while (true) {
      var page = chat.history(bob.id(), alice.id(), false, "", after);
      if (page.isEmpty()) break;
      count += page.size();
      after = page.get(page.size() - 1).id();
    }
    assertEquals(85, count);
    after = 0;
    count = 0;
    while (true) {
      var page = chat.pending(bob.id(), after);
      if (page.isEmpty()) break;
      count += page.size();
      after = page.get(page.size() - 1).id();
    }
    assertEquals(85, count);
  }

  @Test
  void registrationHashesAndNormalizes() throws Exception {
    assertEquals(alice.id(), users.login("ALICE", "strong-pass").id());
    try (var c = DBConnection.getConnection()) {
      var credentials = new UserDAO().credentials(c, "alice");
      assertNotEquals("strong-pass", credentials.hash());
      assertTrue(BCrypt.checkpw("strong-pass", credentials.hash()));
      assertTrue(credentials.hash().startsWith("$2a$12$"));
    }
  }

  @Test
  void duplicateUsernameRejected() {
    assertThrows(ChatException.class, () -> users.register("ALICE", "another-pass"));
  }

  @Test
  void shortPasswordRejected() {
    assertThrows(ChatException.class, () -> users.register("dave", "123"));
  }

  @Test
  void bcryptByteLimitValidated() {
    assertThrows(ChatException.class, () -> UserService.validatePassword("😊".repeat(20)));
  }

  @Test
  void loginRejectsWrongPassword() {
    assertThrows(ChatException.class, () -> users.login("alice", "incorrect"));
  }

  @Test
  void passwordChangeRequiresCurrent() throws Exception {
    assertThrows(ChatException.class, () -> users.changePassword(alice, "wrong", "new-password"));
    users.changePassword(alice, "strong-pass", "new-password");
    assertEquals(alice.id(), users.login("alice", "new-password").id());
    assertThrows(ChatException.class, () -> users.login("alice", "strong-pass"));
  }

  @Test
  void privateHistoryCannotLeak() throws Exception {
    chat.send(alice.id(), bob.id(), false, "secret");
    assertEquals(1, chat.history(bob.id(), alice.id(), false, "", 0).size());
    assertTrue(chat.history(eve.id(), alice.id(), false, "", 0).isEmpty());
  }

  @Test
  void invalidMessagesRejected() {
    assertThrows(ChatException.class, () -> chat.send(alice.id(), bob.id(), false, " "));
    assertThrows(ChatException.class, () -> chat.send(alice.id(), alice.id(), false, "hello"));
    assertThrows(ChatException.class, () -> chat.send(alice.id(), 999, false, "hello"));
    assertThrows(ChatException.class, () -> ChatService.validateText("x".repeat(4001)));
  }

  @Test
  void searchIsPartialLiteralAndScoped() throws Exception {
    chat.send(alice.id(), bob.id(), false, "Interview 100% ready");
    chat.send(alice.id(), eve.id(), false, "Interview private");
    assertEquals(1, chat.history(bob.id(), alice.id(), false, "INTERVIEW", 0).size());
    assertEquals(1, chat.history(bob.id(), alice.id(), false, "%", 0).size());
    assertTrue(chat.history(bob.id(), alice.id(), false, "' OR 1=1 --", 0).isEmpty());
  }

  @Test
  void offlineMessagesPersistAndReceiptsNeverRegress() throws Exception {
    Message m = chat.send(alice.id(), bob.id(), false, "offline");
    assertEquals(m.id(), new ChatService().pending(bob.id()).get(0).id());
    assertThrows(ChatException.class, () -> chat.receipt(eve.id(), m.id(), true));
    assertEquals("DELIVERED", chat.receipt(bob.id(), m.id(), false).status());
    assertTrue(chat.pending(bob.id()).isEmpty());
    assertEquals("SEEN", chat.receipt(bob.id(), m.id(), true).status());
    assertEquals("SEEN", chat.receipt(bob.id(), m.id(), false).status());
  }

  @Test
  void groupCreationIsAtomic() throws Exception {
    assertThrows(ChatException.class, () -> groups.create(alice.id(), "", List.of()));
    assertThrows(ChatException.class, () -> groups.create(alice.id(), "broken", List.of(999L)));
    assertEquals(1, groups.list(alice.id()).size());
  }

  @Test
  void adminCanAddAndRemoveMembers() throws Exception {
    long g = groups.create(alice.id(), "Team", List.of(bob.id()));
    assertThrows(ChatException.class, () -> groups.change(bob.id(), g, eve.id(), true));
    groups.change(alice.id(), g, eve.id(), true);
    assertEquals(3, groups.members(eve.id(), g).size());
    groups.change(alice.id(), g, eve.id(), false);
    assertThrows(ChatException.class, () -> groups.members(eve.id(), g));
  }

  @Test
  void nonmembersCannotReadOrSend() throws Exception {
    long g = groups.create(alice.id(), "Team", List.of(bob.id()));
    chat.send(alice.id(), g, true, "team secret");
    assertThrows(ChatException.class, () -> chat.history(eve.id(), g, true, "", 0));
    assertThrows(ChatException.class, () -> chat.send(eve.id(), g, true, "intrusion"));
    assertEquals(1, chat.history(bob.id(), g, true, "", 0).size());
  }

  @Test
  void leavingAdminTransfersRole() throws Exception {
    long g = groups.create(alice.id(), "Team", List.of(bob.id()));
    groups.leave(alice.id(), g);
    assertEquals("ADMIN", groups.members(bob.id(), g).get(0).role());
    groups.leave(bob.id(), g);
    assertEquals(1, groups.list(bob.id()).size());
  }

  @Test
  void broadcastCommunityIncludesAllUsers() throws Exception {
    chat.send(alice.id(), 1, true, "hello everyone");
    assertEquals(1, chat.history(eve.id(), 1, true, "", 0).size());
    assertThrows(ChatException.class, () -> groups.leave(eve.id(), 1));
  }

  @Test
  void filesRoundTripAndEnforceAuthorization() throws Exception {
    FileService files = new FileService();
    byte[] bytes = "hello binary ✓".getBytes(StandardCharsets.UTF_8);
    Message m = files.upload(alice.id(), bob.id(), false, "../../report.txt", bytes);
    assertEquals("report.txt", m.attachment().fileName());
    assertArrayEquals(bytes, files.download(bob.id(), m.attachment().id()));
    assertThrows(ChatException.class, () -> files.download(eve.id(), m.attachment().id()));
  }

  @Test
  void removedMemberCannotDownloadGroupFile() throws Exception {
    long g = groups.create(alice.id(), "Team", List.of(bob.id()));
    FileService files = new FileService();
    Message m = files.upload(alice.id(), g, true, "file.pdf", new byte[] {1, 2, 3});
    groups.change(alice.id(), g, bob.id(), false);
    assertThrows(ChatException.class, () -> files.download(bob.id(), m.attachment().id()));
  }

  @Test
  void invalidFilesRejected() {
    FileService files = new FileService();
    assertThrows(
        ChatException.class,
        () -> files.upload(alice.id(), bob.id(), false, "malware.exe", new byte[] {1}));
    assertThrows(
        ChatException.class,
        () -> files.upload(alice.id(), bob.id(), false, "empty.txt", new byte[0]));
    assertThrows(ChatException.class, () -> FileService.safeName(".."));
    assertEquals("safe.txt", FileService.safeName("C:\\temp\\safe.txt"));
  }
}
