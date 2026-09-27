package app;

import database.*;
import java.util.*;
import org.mindrot.jbcrypt.BCrypt;

/** One-shot, explicit import from the original chat_app schema on the same MySQL instance. */
public final class MigrateLegacy {
  public static void main(String[] args) throws Exception {
    if (args.length != 1 || !args[0].equals("--import-and-hash-legacy"))
      throw new IllegalArgumentException(
          "Stop both servers, back up the database, then pass --import-and-hash-legacy. Target"
              + " users/messages must be empty.");
    Sql.transaction(
        c -> {
          if (!Sql.query(c, "SELECT id FROM users", r -> r.getLong(1)).isEmpty())
            throw new IllegalStateException("Target already contains users. Import aborted.");
          record LegacyUser(long id, String name, String password, String role) {}
          var users =
              Sql.query(
                  c,
                  "SELECT id,username,password,role FROM chat_app.users",
                  r ->
                      new LegacyUser(r.getLong(1), r.getString(2), r.getString(3), r.getString(4)));
          Map<String, Long> ids = new HashMap<>();
          for (var u : users) {
            String name = service.UserService.username(u.name());
            String hash =
                u.password().matches("^\\$2[aby]\\$.*")
                    ? u.password()
                    : BCrypt.hashpw(u.password(), BCrypt.gensalt(12));
            long id =
                Sql.insert(
                    c,
                    "INSERT INTO users(username,password_hash,role) VALUES(?,?,?)",
                    name,
                    hash,
                    u.role());
            ids.put(u.name(), id);
            Sql.update(
                c, "INSERT INTO group_members(group_id,user_id,role) VALUES(1,?,'MEMBER')", id);
            // Disable plaintext authentication in the retired schema without deleting source rows.
            Sql.update(c, "UPDATE chat_app.users SET password=? WHERE id=?", hash, u.id());
          }
          record LegacyMessage(
              String sender, String receiver, String text, java.sql.Timestamp time) {}
          var messages =
              Sql.query(
                  c,
                  "SELECT sender,receiver,message,timestamp FROM chat_app.messages ORDER BY id",
                  r ->
                      new LegacyMessage(
                          r.getString(1), r.getString(2), r.getString(3), r.getTimestamp(4)));
          for (var m : messages) {
            Long sender = ids.get(m.sender());
            if (sender == null)
              throw new IllegalStateException(
                  "Legacy message has an unknown sender. Import rolled back.");
            if (m.receiver().equalsIgnoreCase("ALL"))
              Sql.insert(
                  c,
                  "INSERT INTO group_messages(group_id,sender_id,message,sent_at) VALUES(1,?,?,?)",
                  sender,
                  m.text(),
                  m.time());
            else {
              Long receiver = ids.get(m.receiver());
              if (receiver == null)
                throw new IllegalStateException(
                    "Legacy message has an unknown recipient. Import rolled back.");
              Sql.insert(
                  c,
                  "INSERT INTO messages(sender_id,receiver_id,message,sent_at) VALUES(?,?,?,?)",
                  sender,
                  receiver,
                  m.text(),
                  m.time());
            }
          }
          System.out.println(
              "Imported "
                  + users.size()
                  + " users and "
                  + messages.size()
                  + " messages. Legacy password values converted to BCrypt.");
          return null;
        });
  }
}
