package dao;

import database.Sql;
import java.sql.*;
import java.util.*;
import model.*;

public class GroupDAO {
  public List<ChatGroup> list(Connection c, long user) throws SQLException {
    return Sql.query(
        c,
        "SELECT g.* FROM chat_groups g JOIN group_members m ON m.group_id=g.id WHERE m.user_id=?"
            + " ORDER BY g.name",
        r -> new ChatGroup(r.getLong("id"), r.getString("name"), r.getLong("created_by")),
        user);
  }

  public String role(Connection c, long group, long user) throws SQLException {
    var rows =
        Sql.query(
            c,
            "SELECT role FROM group_members WHERE group_id=? AND user_id=?",
            r -> r.getString(1),
            group,
            user);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public List<Member> members(Connection c, long group) throws SQLException {
    return Sql.query(
        c,
        "SELECT u.id,u.username,m.role FROM group_members m JOIN users u ON u.id=m.user_id WHERE"
            + " group_id=? ORDER BY u.id",
        r -> new Member(r.getLong(1), r.getString(2), r.getString(3)),
        group);
  }

  public long create(Connection c, String name, long user) throws SQLException {
    return Sql.insert(c, "INSERT INTO chat_groups(name,created_by) VALUES(?,?)", name, user);
  }

  public void add(Connection c, long group, long user, String role) throws SQLException {
    Sql.update(
        c, "INSERT INTO group_members(group_id,user_id,role) VALUES(?,?,?)", group, user, role);
  }

  public void remove(Connection c, long group, long user) throws SQLException {
    Sql.update(c, "DELETE FROM group_members WHERE group_id=? AND user_id=?", group, user);
  }

  public void lock(Connection c, long group) throws SQLException {
    Sql.query(c, "SELECT id FROM chat_groups WHERE id=? FOR UPDATE", r -> r.getLong(1), group);
  }

  public void promote(Connection c, long group, long user) throws SQLException {
    Sql.update(
        c, "UPDATE group_members SET role='ADMIN' WHERE group_id=? AND user_id=?", group, user);
  }

  public void delete(Connection c, long group) throws SQLException {
    Sql.update(c, "DELETE FROM chat_groups WHERE id=?", group);
  }
}
