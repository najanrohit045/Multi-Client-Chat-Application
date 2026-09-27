package network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Async request correlation plus independent push events; never blocks the FX thread. */
public final class ChatClient implements AutoCloseable {
  private final Socket socket = new Socket();
  private final ExecutorService reader = Executors.newSingleThreadExecutor();
  private final ExecutorService writer =
      new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(256));
  private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
  private final ConcurrentHashMap<String, CompletableFuture<FrameCodec.Frame>> pending =
      new ConcurrentHashMap<>();
  private final Consumer<Packet> events;
  private volatile boolean closed;

  public ChatClient(String host, int port, Consumer<Packet> events) throws IOException {
    this.events = events;
    socket.connect(new InetSocketAddress(host, port), 5000);
    socket.setTcpNoDelay(true);
    socket.setSoTimeout(70000);
    reader.execute(this::read);
    timer.scheduleAtFixedRate(() -> request("PING", Map.of()), 20, 20, TimeUnit.SECONDS);
  }

  public CompletableFuture<FrameCodec.Frame> request(String type, Object data) {
    return request(type, data, new byte[0]);
  }

  public CompletableFuture<FrameCodec.Frame> request(String type, Object data, byte[] binary) {
    Packet p = Packet.request(type, data);
    var future = new CompletableFuture<FrameCodec.Frame>();
    if (closed) {
      future.completeExceptionally(new IOException("Disconnected. Please sign in again."));
      return future;
    }
    pending.put(p.id(), future);
    future.orTimeout(30, TimeUnit.SECONDS).whenComplete((r, e) -> pending.remove(p.id()));
    try {
      writer.execute(
          () -> {
            try {
              FrameCodec.write(socket.getOutputStream(), new FrameCodec.Frame(p, binary));
            } catch (IOException e) {
              future.completeExceptionally(e);
              close();
            }
          });
    } catch (RejectedExecutionException e) {
      future.completeExceptionally(new IOException("Connection busy. Please retry."));
    }
    return future;
  }

  private void read() {
    try {
      while (!closed) {
        var f = FrameCodec.read(socket.getInputStream());
        var future = f.packet().id() == null ? null : pending.remove(f.packet().id());
        if (future != null) {
          if (f.packet().type().equals("ERROR"))
            future.completeExceptionally(new IOException(f.packet().text("message")));
          else future.complete(f);
        } else events.accept(f.packet());
      }
    } catch (IOException e) {
      if (!closed) events.accept(Packet.event("DISCONNECTED", Map.of()));
    } finally {
      close();
    }
  }

  public boolean isClosed() {
    return closed;
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    try {
      socket.close();
    } catch (IOException ignored) {
    }
    reader.shutdownNow();
    writer.shutdownNow();
    timer.shutdownNow();
    pending
        .values()
        .forEach(
            f -> f.completeExceptionally(new IOException("Disconnected. Please sign in again.")));
    pending.clear();
  }
}
