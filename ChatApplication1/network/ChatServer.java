package network;

import service.ChatService;
import util.FileManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatServer - Multi-threaded TCP server for the chat application.
 *
 * <p>Listens on a configurable port, accepts client connections in an infinite
 * loop, and delegates each connection to a new {@link ClientHandler} thread.
 * A {@link ConcurrentHashMap} maintains the registry of online users, which is
 * shared (by reference) with every ClientHandler so broadcasts work correctly.</p>
 *
 * <p><b>Thread Model:</b>
 * <pre>
 *   Main thread → accept loop
 *   Per-client  → ClientHandler extends Thread
 * </pre></p>
 *
 * <p><b>Concepts Used:</b> ServerSocket, Socket, Multithreading, ConcurrentHashMap,
 * Collections, Exception Handling, Shutdown Hook, OOP, SOLID</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class ChatServer {

    // ─── Configuration ────────────────────────────────────────────────────────
    /** TCP port the server listens on. Change here or pass via constructor. */
    public static final int DEFAULT_PORT = 9090;

    /** Maximum backlog of pending connections in the OS queue. */
    private static final int BACKLOG = 50;

    // ─── State ────────────────────────────────────────────────────────────────
    private final int port;

    /**
     * Registry of currently connected users.
     * Key   = authenticated username.
     * Value = the {@link ClientHandler} managing that connection.
     *
     * <p>Uses {@link ConcurrentHashMap} for thread-safe reads; writes are
     * also wrapped in {@code synchronized} blocks inside ClientHandler for
     * atomic check-and-update sequences.</p>
     */
    private final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();

    /** Shared business-logic layer — one instance for the entire server. */
    private final ChatService chatService;

    /** The server socket; held as a field so the shutdown hook can close it. */
    private ServerSocket serverSocket;

    /** Flag controlling the accept loop. */
    private volatile boolean running = false;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs the server on the default port ({@value #DEFAULT_PORT}).
     */
    public ChatServer() {
        this(DEFAULT_PORT);
    }

    /**
     * Constructs the server on a custom port.
     *
     * @param port the TCP port to listen on (1024–65535)
     */
    public ChatServer(int port) {
        this.port        = port;
        this.chatService = new ChatService();
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Starts the server: opens the {@link ServerSocket}, registers a JVM
     * shutdown hook for graceful cleanup, then enters the accept loop.
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port, BACKLOG);
            running = true;

            registerShutdownHook();

            System.out.println("╔══════════════════════════════════════╗");
            System.out.println("║   Multi-Threaded Chat Server          ║");
            System.out.println("║   Listening on port " + port + "             ║");
            System.out.println("╚══════════════════════════════════════╝");

            FileManager.logAuditEvent("SERVER_START | port=" + port);

            acceptLoop();

        } catch (IOException e) {
            System.err.println("[Server] Failed to start: " + e.getMessage());
        }
    }

    /**
     * Stops the server gracefully: closes the ServerSocket and disconnects
     * all active client handlers.
     */
    public void stop() {
        running = false;
        System.out.println("[Server] Shutting down...");

        // Disconnect all active clients
        new ArrayList<>(onlineUsers.values()).forEach(ClientHandler::disconnect);
        onlineUsers.clear();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[Server] Error during shutdown: " + e.getMessage());
        }

        FileManager.logAuditEvent("SERVER_STOP");
        System.out.println("[Server] Shutdown complete.");
    }

    // ─── Accept Loop ──────────────────────────────────────────────────────────

    /**
     * Blocks on {@link ServerSocket#accept()} until a client connects, then
     * hands the socket to a new {@link ClientHandler} thread and loops.
     */
    private void acceptLoop() {
        System.out.println("[Server] Waiting for connections...");

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();   // blocks here
                System.out.println("[Server] New connection from: "
                        + clientSocket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(
                        clientSocket, onlineUsers, chatService);

                handler.setDaemon(false);   // keep JVM alive while clients are connected
                handler.start();            // launches the thread → runs handler.run()

            } catch (IOException e) {
                if (running) {
                    System.err.println("[Server] Accept error: " + e.getMessage());
                }
                // If running == false, the socket was intentionally closed → exit loop
            }
        }
    }

    // ─── Admin Helpers ────────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable snapshot of the online-user map keys.
     *
     * @return set of currently connected usernames
     */
    public Set<String> getOnlineUsernames() {
        return Collections.unmodifiableSet(onlineUsers.keySet());
    }

    /**
     * Returns the total count of connected clients.
     *
     * @return number of online users
     */
    public int getOnlineCount() {
        return onlineUsers.size();
    }

    /**
     * Broadcasts a server-originated message to all connected clients.
     *
     * @param message the text to broadcast
     */
    public void broadcastServerMessage(String message) {
        onlineUsers.values().forEach(h -> h.send("SERVER: " + message));
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Registers a JVM shutdown hook that calls {@link #stop()} when the
     * process receives SIGTERM / SIGINT or {@code System.exit()} is called.
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Shutdown signal received.");
            stop();
        }, "ShutdownHook"));
    }
}
