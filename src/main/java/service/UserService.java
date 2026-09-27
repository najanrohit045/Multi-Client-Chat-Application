package service;

import dao.UserDAO;
import database.*;
import exception.ChatException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import model.User;
import org.mindrot.jbcrypt.BCrypt;

public class UserService {
  private final UserDAO users = new UserDAO();

  public static String username(String name) {
    if (name == null || !name.matches("[A-Za-z0-9_]{3,32}"))
      throw new ChatException("Username must contain 3–32 letters, digits or underscores.");
    return name.toLowerCase(Locale.ROOT);
  }

  public static void validatePassword(String p) {
    if (p == null || p.length() < 8 || p.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ChatException("Password must be at least 8 characters and at most 72 UTF-8 bytes.");
  }

  public User register(String name, String password) throws SQLException {
    String n = username(name);
    validatePassword(password);
    String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
    try {
      return Sql.transaction(
          c -> {
            if (users.credentials(c, n) != null)
              throw new ChatException("Username is already taken.");
            User u = users.create(c, n, hash);
            new dao.GroupDAO().add(c, 1, u.id(), "MEMBER");
            return u;
          });
    } catch (SQLException e) {
      if ("23000".equals(e.getSQLState()) || "23505".equals(e.getSQLState()))
        throw new ChatException("Username is already taken.");
      throw e;
    }
  }

  public User login(String name, String password) throws SQLException {
    if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ChatException("Invalid username or password.");
    try (Connection c = DBConnection.getConnection()) {
      var found = users.credentials(c, username(name));
      if (found == null || !BCrypt.checkpw(password, found.hash()))
        throw new ChatException("Invalid username or password.");
      return found.user();
    }
  }

  public void changePassword(User user, String current, String replacement) throws SQLException {
    login(user.username(), current);
    validatePassword(replacement);
    String hash = BCrypt.hashpw(replacement, BCrypt.gensalt(12));
    Sql.transaction(
        c -> {
          users.password(c, user.id(), hash);
          return null;
        });
  }

  public List<User> search(String term) throws SQLException {
    if (term.length() > 50) throw new ChatException("Search is too long.");
    try (Connection c = DBConnection.getConnection()) {
      return users.search(c, term);
    }
  }

  public void presence(long id, boolean online) throws SQLException {
    try (Connection c = DBConnection.getConnection()) {
      users.presence(c, id, online);
    }
  }

  public void resetPresence() throws SQLException {
    try (Connection c = DBConnection.getConnection()) {
      users.resetPresence(c);
    }
  }
}
