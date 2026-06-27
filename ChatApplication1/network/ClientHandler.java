package network;

import exception.InvalidMessageException;
import model.Message;
import network.ChatServer;
import service.ChatService;
import util.FileManager;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Map;

/**
 * ClientHandler - A dedicated thread that manages one connected client socket.
 *
 * <p>Each time a client connects to {@link ChatServer}, the server spawns a
 * new {@code ClientHandler} thread to handle that client's I/O independently,
 * enabling true multi-client concurrency.</p>
 *
 * <p><b>Protocol (line-based text over socket):</b>
 * <pre>
 *   LOGIN:&lt;username&gt;:&lt;password&gt;
 *   MSG:ALL:&lt;text&gt;             → broadcast
 *   MSG:&lt;receiver&gt;:&lt;text&gt;    → private message
 *   ONLINE                     → list online users
 *   HISTORY                    → last 20 messages
 *   QUIT                       → disconnect
 *   ADMIN:REMOVE:&lt;username&gt;  → admin removes user
 *   ADMIN:LOGS                 → admin views logs
 * </pre></p>
 *
 * <p><b>Concepts Used:</b> Multithreading (extends Thread), Socket Programming,
 * BufferedReader, PrintWriter, Synchronization, Collections, Exception Handling,
 * Polymorphism, OOP</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class ClientHandler extends Thread {

    // ─── Dependencies ─────────────────────────────────────────────────────────
    /** The raw socket for this connection. */
    private final Socket socket;

    /**
     * Shared map of connected clients — key = username, value = handler.
     * Passed in from {@link ChatServer} so all handlers share the same view.
     */
    private final Map<String, ClientHandler> onlineUsers;

    /** Business logic for messaging. */
    private final ChatService chatService;

    // ─── Per-client State ─────────────────────────────────────────────────────
    private BufferedReader reader;
    private PrintWriter    writer;

    /** Username of the authenticated client (null until LOGIN succeeds). */
    private String username;

    /** Role of the authenticated client ("USER" or "ADMIN"). */
    private String role;

    /** Whether this handler's client is still connected. */
    private volatile boolean running = true;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Creates a ClientHandler for the given socket.
     *
     * @param socket      the accepted client socket
     * @param onlineUsers shared map of username → ClientHandler
     * @param chatService the shared ChatService instance
     */
    public ClientHandler(Socket socket,
                         Map<String, ClientHandler> onlineUsers,
                         ChatService chatService) {
        this.socket      = socket;
        this.onlineUsers = onlineUsers;
        this.chatService = chatService;

        // Name the thread for easier debugging in thread-dumps
        setName("ClientHandler-" + socket.getRemoteSocketAddress());
    }

    // ─── Thread Entry Point ───────────────────────────────────────────────────

    /**
     * Main loop: authenticate the client, then process messages until disconnect.
     *
     * <p>The {@code try-with-resources} and {@code finally} block guarantee the
     * socket is always closed and the user is removed from the online map.</p>
     */
    @Override
    public void run() {
        try {
            // Set up I/O streams
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            send("Welcome to Multi-Threaded Chat! Send LOGIN:<username>:<password> to authenticate.");

            // ── Authentication phase ─────────────────────────────────────────
            if (!authenticate()) {
                send("ERROR:Authentication failed. Closing connection.");
                return;
            }

            // ── Deliver any queued offline messages ──────────────────────────
            deliverPendingMessages();

            // ── Message processing loop ──────────────────────────────────────
            String line;
            while (running && (line = reader.readLine()) != null) {
                processCommand(line.trim());
            }

        } catch (IOException e) {
            System.out.println("[ClientHandler] Connection lost: "
                    + (username != null ? username : "unauthenticated")
                    + " — " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    // ─── Command Processing ───────────────────────────────────────────────────

    /**
     * Parses and dispatches a single command line received from the client.
     *
     * @param line the raw line from the socket
     */
    private void processCommand(String line) {
        if (line.isEmpty()) return;

        // ── MSG command ──────────────────────────────────────────────────────
        if (line.startsWith("MSG:")) {
            handleMessageCommand(line);

        // ── ONLINE command ───────────────────────────────────────────────────
        } else if (line.equals("ONLINE")) {
            handleOnlineUsers();

        // ── HISTORY command ──────────────────────────────────────────────────
        } else if (line.equals("HISTORY")) {
            handleHistory();

        // ── QUIT command ─────────────────────────────────────────────────────
        } else if (line.equals("QUIT")) {
            running = false;

        // ── ADMIN commands ───────────────────────────────────────────────────
        } else if (line.startsWith("ADMIN:")) {
            handleAdminCommand(line);

        } else {
            send("ERROR:Unknown command. Use MSG:ALL:<text>, MSG:<user>:<text>, "
                    + "ONLINE, HISTORY, QUIT.");
        }
    }

    /**
     * Handles {@code MSG:ALL:<text>} and {@code MSG:<receiver>:<text>} commands.
     *
     * @param line the full command line
     */
    private void handleMessageCommand(String line) {
        // Format: MSG:<receiver>:<content>
        // Split on first two colons only (content may contain colons)
        String[] parts = line.split(":", 3);
        if (parts.length < 3) {
            send("ERROR:Bad MSG format. Use MSG:<receiver>:<text>");
            return;
        }

        String receiver = parts[1];
        String content  = parts[2];

        try {
            if (Message.BROADCAST.equalsIgnoreCase(receiver)) {
                // ── Broadcast ────────────────────────────────────────────────
                Message msg = chatService.sendBroadcast(username, content);
                broadcastToAll("[" + username + " → ALL]: " + content, username);

            } else {
                // ── Private ──────────────────────────────────────────────────
                boolean online = onlineUsers.containsKey(receiver);
                Message msg = chatService.sendPrivateMessage(
                        username, receiver, content, online);

                if (online) {
                    ClientHandler targetHandler = onlineUsers.get(receiver);
                    targetHandler.send("[PM from " + username + "]: " + content);
                    send("[PM to " + receiver + "]: " + content);
                } else {
                    send("INFO:" + receiver + " is offline. Message saved for later delivery.");
                }
            }

        } catch (InvalidMessageException e) {
            send("ERROR:" + e.getMessage());
        }
    }

    /**
     * Lists all currently online users to the requesting client.
     */
    private void handleOnlineUsers() {
        StringBuilder sb = new StringBuilder("ONLINE_USERS:");
        synchronized (onlineUsers) {
            onlineUsers.keySet().forEach(u -> sb.append(u).append(","));
        }
        send(sb.toString());
    }

    /**
     * Sends the last 20 messages from the database to the requesting client.
     */
    private void handleHistory() {
        List<Message> recent = chatService.getRecentMessages(20);
        send("--- Chat History (last " + recent.size() + " messages) ---");
        recent.forEach(m -> send(m.toDisplayString()));
        send("--- End of History ---");
    }

    /**
     * Handles admin-only commands: {@code ADMIN:REMOVE:<user>} and {@code ADMIN:LOGS}.
     *
     * @param line the full admin command line
     */
    private void handleAdminCommand(String line) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            send("ERROR:Admin privileges required.");
            return;
        }

        if (line.startsWith("ADMIN:REMOVE:")) {
            String target = line.substring("ADMIN:REMOVE:".length()).trim();
            removeUser(target);

        } else if (line.equals("ADMIN:LOGS")) {
            List<String> logs = FileManager.readChatHistory();
            send("--- Chat Logs (" + logs.size() + " entries) ---");
            logs.forEach(this::send);
            send("--- End of Logs ---");

        } else {
            send("ERROR:Unknown admin command. Use ADMIN:REMOVE:<user> or ADMIN:LOGS");
        }
    }

    /**
     * Admin action: removes a connected user forcibly.
     *
     * @param targetUser the username to remove
     */
    private void removeUser(String targetUser) {
        ClientHandler target = onlineUsers.get(targetUser);
        if (target == null) {
            send("INFO:User '" + targetUser + "' is not currently online.");
        } else {
            target.send("INFO:You have been removed by an administrator.");
            target.disconnect();
            send("INFO:User '" + targetUser + "' has been removed.");
            FileManager.logAuditEvent("ADMIN_REMOVE | admin=" + username
                    + " | removed=" + targetUser);
        }
    }

    // ─── Authentication ───────────────────────────────────────────────────────

    /**
     * Reads LOGIN lines until a valid {@code LOGIN:<user>:<pass>} is received
     * or the client disconnects.
     *
     * @return {@code true} if authentication succeeded
     * @throws IOException if the socket stream is broken
     */
    private boolean authenticate() throws IOException {
        int attempts = 0;
        String line;

        while (attempts < 3 && (line = reader.readLine()) != null) {
            if (line.startsWith("LOGIN:")) {
                String[] parts = line.split(":", 3);
                if (parts.length == 3) {
                    String user = parts[1];
                    String pass = parts[2];

                    // Delegate authentication to service layer
                    try {
                        model.User u = new service.UserService().login(user, pass);
                        this.username = u.getUsername();
                        this.role     = u.getRole();

                        // Register in the online-users map
                        synchronized (onlineUsers) {
                            onlineUsers.put(username, this);
                        }

                        send("LOGIN_OK:" + username + ":" + role);
                        System.out.println("[Server] User connected: " + username);
                        FileManager.logAuditEvent("CONNECT | user=" + username);

                        // Notify others
                        broadcastToAll("SERVER: " + username + " joined the chat.", username);
                        return true;

                    } catch (exception.UserNotFoundException e) {
                        send("ERROR:Invalid credentials. Attempt " + (++attempts) + "/3");
                    }
                } else {
                    send("ERROR:Bad format. Use LOGIN:<username>:<password>");
                    attempts++;
                }
            } else {
                send("ERROR:Please log in first. Use LOGIN:<username>:<password>");
                attempts++;
            }
        }
        return false;
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    /**
     * Delivers any messages queued while this user was offline.
     */
    private void deliverPendingMessages() {
        if (chatService.hasPendingMessages(username)) {
            List<Message> pending = chatService.drainPendingMessages(username);
            send("INFO:You have " + pending.size() + " unread message(s):");
            pending.forEach(m -> send(m.toDisplayString()));
        }
    }

    /**
     * Broadcasts a message to all connected clients except the sender.
     *
     * <p>{@code synchronized} on {@link #onlineUsers} prevents a
     * ConcurrentModificationException when iterating while another thread
     * adds/removes users.</p>
     *
     * @param message the text to broadcast
     * @param exclude username to skip (the sender)
     */
    void broadcastToAll(String message, String exclude) {
        synchronized (onlineUsers) {
            for (Map.Entry<String, ClientHandler> entry : onlineUsers.entrySet()) {
                if (!entry.getKey().equals(exclude)) {
                    entry.getValue().send(message);
                }
            }
        }
    }

    /**
     * Sends a single line to this handler's client.
     *
     * @param message the line to send (auto-flushed by PrintWriter)
     */
    public void send(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }

    /**
     * Cleanly disconnects the client: removes from online map, closes socket,
     * notifies other users.
     */
    public void disconnect() {
        running = false;
        if (username != null) {
            synchronized (onlineUsers) {
                onlineUsers.remove(username);
            }
            FileManager.logAuditEvent("DISCONNECT | user=" + username);
            broadcastToAll("SERVER: " + username + " has left the chat.", username);
            System.out.println("[Server] User disconnected: " + username);
        }
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[ClientHandler] Error closing socket: " + e.getMessage());
        }
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns the authenticated username for this connection.
     *
     * @return username, or {@code null} if not yet authenticated
     */
    public String getClientUsername() { return username; }
}
