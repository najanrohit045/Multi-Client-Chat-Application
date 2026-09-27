package network;

import exception.ChatException;
import java.io.*;
import java.net.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import model.*;

public final class ClientHandler implements Runnable, AutoCloseable {
  private final ChatServer server;
  private final Socket socket;
  private final BlockingQueue<FrameCodec.Frame> outbound = new ArrayBlockingQueue<>(64);
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicLong queuedBinaryBytes = new AtomicLong();
  private User user;
  private int attempts;
  private long lastTyping;

  ClientHandler(ChatServer server, Socket socket) {
    this.server = server;
    this.socket = socket;
  }

  void send(Packet p) {
    send(new FrameCodec.Frame(p));
  }

  void send(FrameCodec.Frame f) {
    if (closed.get()) return;
    if (queuedBinaryBytes.addAndGet(f.binary().length) > 12 * 1024 * 1024 || !outbound.offer(f))
      close();
  }

  void writeLoop() {
    try {
      while (!closed.get()) {
        var f = outbound.poll(1, TimeUnit.SECONDS);
        if (f != null) {
          FrameCodec.write(socket.getOutputStream(), f);
          queuedBinaryBytes.addAndGet(-f.binary().length);
        }
      }
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      close();
    }
  }

  @Override
  public void run() {
    try {
      while (!closed.get()) {
        var frame = FrameCodec.read(socket.getInputStream());
        Packet p = frame.packet();
        try {
          Object result = handle(p, frame.binary());
          if (result != null) send(new Packet(p.id(), "OK", Packet.object(result)));
        } catch (ChatException | IllegalArgumentException | IllegalStateException e) {
          send(
              new Packet(
                  p.id(),
                  "ERROR",
                  Packet.object(
                      Map.of(
                          "message",
                          e instanceof ChatException ? e.getMessage() : "Invalid request."))));
        } catch (SQLException e) {
          System.err.println("Database operation failed (SQL state " + e.getSQLState() + ")");
          send(
              new Packet(
                  p.id(),
                  "ERROR",
                  Packet.object(Map.of("message", "Database unavailable. Please retry."))));
        } catch (Exception e) {
          send(
              new Packet(
                  p.id(),
                  "ERROR",
                  Packet.object(Map.of("message", "Operation failed. Please retry."))));
        }
      }
    } catch (IOException e) {
      /* EOF and heartbeat expiry both disconnect. */
    } finally {
      close();
    }
  }

  private Object handle(Packet p, byte[] binary) throws Exception {
    if (p.type().equals("PING")) {
      synchronized (server.online) {
        if (user != null && server.online.get(user.id()) == this)
          server.users.presence(user.id(), true);
      }
      return Map.of();
    }
    if (user == null) {
      if (!p.type().equals("LOGIN") && !p.type().equals("REGISTER"))
        throw new ChatException("Please log in first.");
      if (++attempts > 10) {
        close();
        return null;
      }
      if (p.type().equals("REGISTER")) {
        server.users.register(p.text("username"), p.text("password"));
        return Map.of("registered", true);
      }
      User candidate = server.users.login(p.text("username"), p.text("password"));
      synchronized (server.online) {
        if (closed.get()) return null;
        if (server.online.containsKey(candidate.id()))
          throw new ChatException("This account is already connected.");
        server.users.presence(candidate.id(), true);
        user = candidate;
        server.online.put(user.id(), this);
        util.FileManager.audit(user.id(), "LOGIN");
      }
      send(new Packet(p.id(), "OK", Packet.object(Map.of("user", user))));
      server.broadcast(Packet.event("PRESENCE", Map.of("userId", user.id(), "online", true)));
      for (Message m : server.chat.pending(user.id()))
        send(Packet.event("MESSAGE", Map.of("message", m)));
      return null;
    }
    long target = p.number("target");
    boolean group = p.flag("group");
    return switch (p.type()) {
      case "PENDING" -> Map.of("messages", server.chat.pending(user.id(), p.number("after")));
      case "SUMMARIES" -> Map.of("messages", server.chat.summaries(user.id()));
      case "USERS" -> Map.of("users", server.users.search(p.text("query")));
      case "GROUPS" -> Map.of("groups", server.groups.list(user.id()));
      case "HISTORY", "SEARCH" ->
          Map.of(
              "messages",
              server.chat.history(user.id(), target, group, p.text("query"), p.number("after")));
      case "SEND", "UPLOAD" -> {
        Message m =
            p.type().equals("SEND")
                ? server.chat.send(user.id(), target, group, p.text("text"))
                : server.files.upload(user.id(), target, group, p.text("name"), binary);
        server.route(m);
        yield Map.of("message", m);
      }
      case "DOWNLOAD" -> {
        byte[] bytes = server.files.download(user.id(), p.number("attachment"));
        send(new FrameCodec.Frame(new Packet(p.id(), "OK", Packet.object(Map.of())), bytes));
        yield null;
      }
      case "DELIVERED", "SEEN" -> {
        Message m = server.chat.receipt(user.id(), p.number("messageId"), p.type().equals("SEEN"));
        server.send(m.senderId(), Packet.event("RECEIPT", Map.of("message", m)));
        yield Map.of();
      }
      case "TYPING", "STOP_TYPING" -> {
        if (System.currentTimeMillis() - lastTyping < 700 && p.type().equals("TYPING"))
          yield Map.of();
        lastTyping = System.currentTimeMillis();
        Packet event =
            Packet.event(
                p.type(),
                Map.of(
                    "userId",
                    user.id(),
                    "username",
                    user.username(),
                    "target",
                    target,
                    "group",
                    group));
        if (group) {
          for (Member member : server.groups.members(user.id(), target))
            if (member.id() != user.id()) server.send(member.id(), event);
        } else if (target != user.id()) server.send(target, event);
        yield Map.of();
      }
      case "CREATE_GROUP" -> {
        List<Long> ids = new ArrayList<>();
        if (p.data().has("members"))
          p.data().getAsJsonArray("members").forEach(e -> ids.add(e.getAsLong()));
        long id = server.groups.create(user.id(), p.text("name"), ids);
        changed();
        yield Map.of("id", id);
      }
      case "MEMBERS" -> Map.of("members", server.groups.members(user.id(), target));
      case "ADD_MEMBER", "REMOVE_MEMBER" -> {
        server.groups.change(user.id(), target, p.number("userId"), p.type().equals("ADD_MEMBER"));
        changed();
        yield Map.of();
      }
      case "LEAVE_GROUP" -> {
        server.groups.leave(user.id(), target);
        changed();
        yield Map.of();
      }
      case "PASSWORD" -> {
        server.users.changePassword(user, p.text("current"), p.text("replacement"));
        yield Map.of();
      }
      case "AUDIT" -> {
        if (!user.role().equals("ADMIN")) throw new ChatException("Admin privileges required.");
        yield Map.of("lines", util.FileManager.recent());
      }
      case "KICK" -> {
        if (!user.role().equals("ADMIN")) throw new ChatException("Admin privileges required.");
        ClientHandler h = server.online.get(target);
        if (h != null) h.close();
        util.FileManager.audit(user.id(), "KICK:" + target);
        yield Map.of();
      }
      case "LOGOUT" -> {
        close();
        yield null;
      }
      default -> throw new ChatException("Unknown command.");
    };
  }

  private void changed() {
    server.broadcast(Packet.event("GROUPS_CHANGED", Map.of()));
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    try {
      socket.close();
    } catch (IOException ignored) {
    }
    outbound.clear();
    synchronized (server.online) {
      if (user != null) {
        if (server.online.remove(user.id(), this)) {
          try {
            server.users.presence(user.id(), false);
          } catch (SQLException ignored) {
          }
          server.broadcast(Packet.event("PRESENCE", Map.of("userId", user.id(), "online", false)));
        }
      }
    }
    server.release(this);
  }
}
