package dao;

import database.*;
import java.sql.*;
import java.util.*;
import model.User;

public class UserDAO {
  public record Credentials(User user, String hash) {}

  private User map(ResultSet r) throws SQLException {
    return new User(
        r.getLong("id"),
        r.getString("username"),
        r.getString("role"),
        r.getBoolean("is_online"),
        Sql.time(r, "last_seen"));
  }

  public Credentials credentials(Connection c, String name) throws SQLException {
    var rows =
        Sql.query(
            c,
            "SELECT * FROM users WHERE username=?",
            r -> new Credentials(map(r), r.getString("password_hash")),
            name);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public User find(Connection c, long id) throws SQLException {
    var rows = Sql.query(c, "SELECT * FROM users WHERE id=?", this::map, id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public User create(Connection c, String name, String hash) throws SQLException {
    long id = Sql.insert(c, "INSERT INTO users(username,password_hash) VALUES(?,?)", name, hash);
    return find(c, id);
  }

  public List<User> search(Connection c, String term) throws SQLException {
    return Sql.query(
        c,
        "SELECT * FROM users WHERE LOCATE(LOWER(?),LOWER(username))>0 ORDER BY username",
        this::map,
        term);
  }

  public void presence(Connection c, long id, boolean online) throws SQLException {
    Sql.update(
        c, "UPDATE users SET is_online=?,last_seen=CURRENT_TIMESTAMP WHERE id=?", online, id);
  }

  public void resetPresence(Connection c) throws SQLException {
    Sql.update(c, "UPDATE users SET is_online=FALSE WHERE is_online=TRUE");
  }

  public void password(Connection c, long id, String hash) throws SQLException {
    Sql.update(c, "UPDATE users SET password_hash=? WHERE id=?", hash, id);
  }
}
