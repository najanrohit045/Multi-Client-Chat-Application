package dao;

import database.Sql;
import java.sql.*;

public class AttachmentDAO {
  public record Stored(
      long id, Long message, Long groupMessage, String path, String name, long size) {}

  public void create(
      Connection c, long message, boolean group, String name, String path, long size, String type)
      throws SQLException {
    Sql.insert(
        c,
        "INSERT INTO"
            + " attachments(message_id,group_message_id,file_name,file_path,file_size,content_type)"
            + " VALUES(?,?,?,?,?,?)",
        group ? null : message,
        group ? message : null,
        name,
        path,
        size,
        type);
  }

  public Stored find(Connection c, long id) throws SQLException {
    var rows =
        Sql.query(
            c,
            "SELECT * FROM attachments WHERE id=?",
            r ->
                new Stored(
                    r.getLong("id"),
                    r.getObject("message_id") == null ? null : r.getLong("message_id"),
                    r.getObject("group_message_id") == null ? null : r.getLong("group_message_id"),
                    r.getString("file_path"),
                    r.getString("file_name"),
                    r.getLong("file_size")),
            id);
    return rows.isEmpty() ? null : rows.get(0);
  }
}
