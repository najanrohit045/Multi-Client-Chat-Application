package service;

import dao.*;
import database.*;
import exception.ChatException;
import java.sql.*;
import java.util.*;
import model.*;

public class ChatService {
  private final MessageDAO messages = new MessageDAO();
  private final GroupMessageDAO groupMessages = new GroupMessageDAO();
  private final GroupService groups = new GroupService();
  private final UserDAO users = new UserDAO();

  public static String validateText(String text) {
    if (text == null || text.isBlank() || text.length() > 4000)
      throw new ChatException("Message must contain 1–4000 characters.");
    return text;
  }

  public void authorize(Connection c, long user, long target, boolean group) throws SQLException {
    if (group) groups.requireMember(c, target, user);
    else if (user == target || users.find(c, target) == null)
      throw new ChatException("Choose another registered user.");
  }

  public MessageDAO dao(boolean group) {
    return group ? groupMessages : messages;
  }

  public Message send(long user, long target, boolean group, String text) throws SQLException {
    validateText(text);
    return Sql.transaction(
        c -> {
          authorize(c, user, target, group);
          long id = dao(group).create(c, user, target, text);
          return dao(group).find(c, id);
        });
  }

  public List<Message> history(long user, long target, boolean group, String term, long after)
      throws SQLException {
    if (term == null || term.length() > 100 || after < 0)
      throw new ChatException("Invalid search.");
    try (Connection c = DBConnection.getConnection()) {
      authorize(c, user, target, group);
      return dao(group).history(c, user, target, term, after);
    }
  }

  public List<Message> summaries(long user) throws SQLException {
    try (Connection c = DBConnection.getConnection()) {
      List<Message> out = new ArrayList<>(messages.summaries(c, user));
      out.addAll(groupMessages.summaries(c, user));
      return out;
    }
  }

  public List<Message> pending(long user, long after) throws SQLException {
    try (Connection c = DBConnection.getConnection()) {
      return messages.pending(c, user, after);
    }
  }

  public List<Message> pending(long user) throws SQLException {
    try (Connection c = DBConnection.getConnection()) {
      return messages.pending(c, user);
    }
  }

  public Message receipt(long user, long id, boolean seen) throws SQLException {
    return Sql.transaction(
        c -> {
          Message m = messages.find(c, id);
          if (m == null || m.targetId() != user)
            throw new ChatException("Only the recipient may acknowledge this message.");
          messages.receipt(c, id, user, seen);
          return messages.find(c, id);
        });
  }
}
