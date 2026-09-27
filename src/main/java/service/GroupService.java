package service;

import dao.*;
import database.*;
import exception.ChatException;
import java.sql.*;
import java.util.*;
import model.*;

public class GroupService {
  private final GroupDAO groups = new GroupDAO();
  private final UserDAO users = new UserDAO();

  public static String validateName(String name) {
    if (name == null || name.isBlank() || name.length() > 80)
      throw new ChatException("Group name must contain 1–80 characters.");
    return name.trim();
  }

  public void requireMember(Connection c, long group, long user) throws SQLException {
    if (groups.role(c, group, user) == null)
      throw new ChatException("You are not a member of this group.");
  }

  public long create(long creator, String name, List<Long> members) throws SQLException {
    String n = validateName(name);
    if (members.size() > 100) throw new ChatException("Maximum 100 selected members.");
    return Sql.transaction(
        c -> {
          long id = groups.create(c, n, creator);
          groups.add(c, id, creator, "ADMIN");
          for (long user : new HashSet<>(members)) {
            if (user == creator) continue;
            if (users.find(c, user) == null) throw new ChatException("User not found.");
            groups.add(c, id, user, "MEMBER");
          }
          return id;
        });
  }

  public List<ChatGroup> list(long user) throws SQLException {
    try (Connection c = DBConnection.getConnection()) {
      return groups.list(c, user);
    }
  }

  public List<Member> members(long user, long group) throws SQLException {
    try (Connection c = DBConnection.getConnection()) {
      requireMember(c, group, user);
      return groups.members(c, group);
    }
  }

  public void change(long actor, long group, long target, boolean add) throws SQLException {
    if (group == 1) throw new ChatException("Community includes every registered user.");
    Sql.transaction(
        c -> {
          groups.lock(c, group);
          requireMember(c, group, actor);
          if (!"ADMIN".equals(groups.role(c, group, actor)))
            throw new ChatException("Only group admins can manage members.");
          if (add) {
            if (users.find(c, target) == null) throw new ChatException("User not found.");
            if (groups.role(c, group, target) != null)
              throw new ChatException("User already belongs to the group.");
            groups.add(c, group, target, "MEMBER");
          } else {
            if (actor == target) throw new ChatException("Use Leave group to remove yourself.");
            groups.remove(c, group, target);
          }
          return null;
        });
  }

  public void leave(long user, long group) throws SQLException {
    if (group == 1) throw new ChatException("Community includes every registered user.");
    Sql.transaction(
        c -> {
          groups.lock(c, group);
          requireMember(c, group, user);
          boolean admin = "ADMIN".equals(groups.role(c, group, user));
          groups.remove(c, group, user);
          var remaining = groups.members(c, group);
          if (remaining.isEmpty()) groups.delete(c, group);
          else if (admin && remaining.stream().noneMatch(m -> m.role().equals("ADMIN")))
            groups.promote(c, group, remaining.get(0).id());
          return null;
        });
  }
}
