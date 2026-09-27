package network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import model.*;
import service.*;
import util.Config;

public class ChatServer implements AutoCloseable {
  final ConcurrentHashMap<Long, ClientHandler> online = new ConcurrentHashMap<>();
  private final Set<ClientHandler> connections = ConcurrentHashMap.newKeySet();
  final UserService users = new UserService();
  final ChatService chat = new ChatService();
  final GroupService groups = new GroupService();
  final FileService files = new FileService();
  private final int maximum = Config.number("CHAT_MAX_CLIENTS", 64);
  private final ExecutorService pool = Executors.newFixedThreadPool(maximum * 2);
  private final Semaphore slots = new Semaphore(maximum);
  private volatile ServerSocket listener;
  private volatile boolean closed;

  public void start(int port) throws Exception {
    listener = new ServerSocket();
    listener.bind(new InetSocketAddress(Config.get("CHAT_BIND", "127.0.0.1"), port));
    users.resetPresence();
    System.out.println("Chat server listening on " + listener.getLocalSocketAddress());
    try {
      while (!closed) {
        Socket socket = listener.accept();
        if (closed) {
          socket.close();
          break;
        }
        if (!slots.tryAcquire()) {
          socket.close();
          continue;
        }
        socket.setSoTimeout(70000);
        socket.setTcpNoDelay(true);
        ClientHandler handler = new ClientHandler(this, socket);
        connections.add(handler);
        pool.execute(handler);
        pool.execute(handler::writeLoop);
      }
    } catch (SocketException e) {
      if (!closed) throw e;
    } finally {
      close();
    }
  }

  public int port() {
    return listener == null ? 0 : listener.getLocalPort();
  }

  void release(ClientHandler handler) {
    if (connections.remove(handler)) slots.release();
  }

  void send(long user, Packet event) {
    ClientHandler h = online.get(user);
    if (h != null) h.send(event);
  }

  void broadcast(Packet event) {
    online.values().forEach(h -> h.send(event));
  }

  void route(Message m) throws Exception {
    Packet event = Packet.event("MESSAGE", Map.of("message", m));
    if (m.group()) {
      for (Member member : groups.members(m.senderId(), m.targetId())) send(member.id(), event);
    } else {
      send(m.senderId(), event);
      send(m.targetId(), event);
    }
  }

  @Override
  public void close() {
    closed = true;
    try {
      if (listener != null) listener.close();
    } catch (IOException ignored) {
    }
    connections.forEach(ClientHandler::close);
    pool.shutdownNow();
    try {
      pool.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public static void main(String[] args) throws Exception {
    ChatServer server = new ChatServer();
    Runtime.getRuntime().addShutdownHook(new Thread(server::close, "chat-shutdown"));
    server.start(Config.number("CHAT_PORT", 9090));
  }
}
