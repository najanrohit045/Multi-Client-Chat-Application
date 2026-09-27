package dao;

import database.Sql;
import java.sql.*;
import java.util.*;
import model.*;

public class MessageDAO {
  protected final boolean group;

  public MessageDAO() {
    this(false);
  }

  protected MessageDAO(boolean group) {
    this.group = group;
  }

  private String table() {
    return group ? "group_messages" : "messages";
  }

  private String target() {
    return group ? "group_id" : "receiver_id";
  }

  private String select() {
    return "SELECT m.*,u.username,a.id aid,a.file_name,a.file_size,a.content_type FROM "
        + table()
        + " m JOIN users u ON u.id=m.sender_id LEFT JOIN attachments a ON a."
        + (group ? "group_message_id" : "message_id")
        + "=m.id ";
  }

  private Message map(ResultSet r) throws SQLException {
    Attachment a =
        r.getObject("aid") == null
            ? null
            : new Attachment(
                r.getLong("aid"),
                r.getString("file_name"),
                r.getLong("file_size"),
                r.getString("content_type"));
    return new Message(
        r.getLong("id"),
        r.getLong("sender_id"),
        r.getString("username"),
        r.getLong(target()),
        group,
        r.getString("message"),
        Sql.time(r, "sent_at"),
        group ? "SENT" : r.getString("status"),
        a);
  }

  public long create(Connection c, long sender, long target, String text) throws SQLException {
    return Sql.insert(
        c,
        "INSERT INTO " + table() + "(sender_id," + target() + ",message) VALUES(?,?,?)",
        sender,
        target,
        text);
  }

  public Message find(Connection c, long id) throws SQLException {
    var rows = Sql.query(c, select() + "WHERE m.id=?", this::map, id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public List<Message> history(Connection c, long user, long target, String term, long after)
      throws SQLException {
    String predicate =
        group
            ? "m.group_id=?"
            : "((m.sender_id=? AND m.receiver_id=?) OR (m.sender_id=? AND m.receiver_id=?))";
    String sql =
        select()
            + "WHERE "
            + predicate
            + " AND LOCATE(LOWER(?),LOWER(m.message))>0 AND m.id>? ORDER BY m.id LIMIT 40";
    return group
        ? Sql.query(c, sql, this::map, target, term, after)
        : Sql.query(c, sql, this::map, user, target, target, user, term, after);
  }

  public List<Message> summaries(Connection c, long user) throws SQLException {
    if (group)
      return Sql.query(
          c,
          select()
              + "WHERE m.group_id IN (SELECT group_id FROM group_members WHERE user_id=?) AND"
              + " m.id=(SELECT MAX(n.id) FROM group_messages n WHERE n.group_id=m.group_id) ORDER"
              + " BY m.id",
          this::map,
          user);
    return Sql.query(
        c,
        select()
            + "WHERE (m.sender_id=? OR m.receiver_id=?) AND NOT EXISTS (SELECT 1 FROM messages n"
            + " WHERE n.id>m.id AND ((n.sender_id=m.sender_id AND n.receiver_id=m.receiver_id) OR"
            + " (n.sender_id=m.receiver_id AND n.receiver_id=m.sender_id))) ORDER BY m.id",
        this::map,
        user,
        user);
  }

  public List<Message> pending(Connection c, long user) throws SQLException {
    return pending(c, user, 0);
  }

  public List<Message> pending(Connection c, long user, long after) throws SQLException {
    return Sql.query(
        c,
        select() + "WHERE m.receiver_id=? AND m.status='SENT' AND m.id>? ORDER BY m.id LIMIT 40",
        this::map,
        user,
        after);
  }

  public void receipt(Connection c, long id, long receiver, boolean seen) throws SQLException {
    if (seen)
      Sql.update(
          c,
          "UPDATE messages SET"
              + " status='SEEN',delivered_at=COALESCE(delivered_at,CURRENT_TIMESTAMP),seen_at=COALESCE(seen_at,CURRENT_TIMESTAMP)"
              + " WHERE id=? AND receiver_id=?",
          id,
          receiver);
    else
      Sql.update(
          c,
          "UPDATE messages SET status='DELIVERED',delivered_at=CURRENT_TIMESTAMP WHERE id=? AND"
              + " receiver_id=? AND status='SENT'",
          id,
          receiver);
  }
}
