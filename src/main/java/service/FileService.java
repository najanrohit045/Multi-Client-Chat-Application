package service;

import dao.*;
import database.*;
import exception.ChatException;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import model.*;
import util.Config;

public class FileService {
  public static final int MAX_SIZE = 10 * 1024 * 1024;
  private final Path root =
      Path.of(Config.get("CHAT_FILES", "server_files")).toAbsolutePath().normalize();
  private final ChatService chat = new ChatService();
  private final AttachmentDAO attachments = new AttachmentDAO();

  public static String safeName(String name) {
    if (name == null) throw new ChatException("Missing file name.");
    String s = name.replace('\\', '/');
    s = s.substring(s.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._ -]", "_");
    if (s.isBlank() || s.equals(".") || s.equals(".."))
      throw new ChatException("Invalid file name.");
    return s.substring(0, Math.min(s.length(), 150));
  }

  public Message upload(long user, long target, boolean group, String name, byte[] bytes)
      throws SQLException, IOException {
    if (bytes.length == 0 || bytes.length > MAX_SIZE)
      throw new ChatException("File size must be between 1 byte and 10 MiB.");
    String safe = safeName(name);
    String ext =
        safe.contains(".") ? safe.substring(safe.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
    String mime =
        switch (ext) {
          case ".png" -> "image/png";
          case ".jpg", ".jpeg" -> "image/jpeg";
          case ".gif" -> "image/gif";
          case ".pdf" -> "application/pdf";
          case ".txt" -> "text/plain";
          case ".doc" -> "application/msword";
          case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
          default -> throw new ChatException("Supported: images, PDF, TXT, DOC and DOCX.");
        };
    try (Connection c = DBConnection.getConnection()) {
      chat.authorize(c, user, target, group);
    }
    Files.createDirectories(root);
    String key = UUID.randomUUID().toString();
    Path path = root.resolve(key);
    try {
      Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
      return Sql.transaction(
          c -> {
            chat.authorize(c, user, target, group);
            long id = chat.dao(group).create(c, user, target, safe);
            attachments.create(c, id, group, safe, key, bytes.length, mime);
            return chat.dao(group).find(c, id);
          });
    } catch (SQLException | RuntimeException | IOException e) {
      Files.deleteIfExists(path);
      throw e;
    }
  }

  public byte[] download(long user, long attachment) throws SQLException, IOException {
    try (Connection c = DBConnection.getConnection()) {
      var a = attachments.find(c, attachment);
      if (a == null) throw new ChatException("Attachment not found.");
      boolean group = a.groupMessage() != null;
      Message m = chat.dao(group).find(c, group ? a.groupMessage() : a.message());
      if (group) new GroupService().requireMember(c, m.targetId(), user);
      else if (m.senderId() != user && m.targetId() != user)
        throw new ChatException("Attachment access denied.");
      Path path = root.resolve(a.path()).normalize();
      if (!path.getParent().equals(root)) throw new ChatException("Invalid attachment path.");
      return Files.readAllBytes(path);
    }
  }
}
