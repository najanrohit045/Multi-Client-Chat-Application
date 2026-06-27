package network;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * ChatClient - Console-based TCP client for the chat application.
 *
 * <p>Connects to {@link ChatServer}, authenticates, and then runs two
 * concurrent threads:
 * <ol>
 *   <li><b>Reader thread</b> — continuously reads lines from the server and
 *       prints them to {@code System.out}.</li>
 *   <li><b>Writer thread (main)</b> — reads user input from {@code System.in}
 *       and sends commands to the server.</li>
 * </ol></p>
 *
 * <p><b>Protocol quick-reference:</b>
 * <pre>
 *   LOGIN:&lt;username&gt;:&lt;password&gt;
 *   MSG:ALL:&lt;text&gt;
 *   MSG:&lt;receiver&gt;:&lt;text&gt;
 *   ONLINE
 *   HISTORY
 *   ADMIN:REMOVE:&lt;username&gt;
 *   ADMIN:LOGS
 *   QUIT
 * </pre></p>
 *
 * <p><b>Concepts Used:</b> Socket Programming, Multithreading (anonymous Thread),
 * BufferedReader, PrintWriter, Scanner, Exception Handling, Volatile</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class ChatClient {

    // ─── Configuration ────────────────────────────────────────────────────────
    private final String host;
    private final int    port;

    // ─── State ────────────────────────────────────────────────────────────────
    private Socket         socket;
    private BufferedReader reader;
    private PrintWriter    writer;
    private volatile boolean connected = false;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a client that will connect to the given host and port.
     *
     * @param host server hostname or IP (e.g. {@code "localhost"})
     * @param port server TCP port (e.g. {@code 9090})
     */
    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Connects to the server, starts the reader thread, and enters the
     * interactive send loop.
     */
    public void start() {
        try {
            socket    = new Socket(host, port);
            reader    = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer    = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            connected = true;

            System.out.println("╔══════════════════════════════════════╗");
            System.out.println("║  Connected to Chat Server             ║");
            System.out.println("║  Host: " + host + "  Port: " + port + "          ║");
            System.out.println("╠══════════════════════════════════════╣");
            System.out.println("║  Commands:                            ║");
            System.out.println("║  LOGIN:<user>:<pass>                  ║");
            System.out.println("║  MSG:ALL:<text>   (broadcast)         ║");
            System.out.println("║  MSG:<user>:<text>(private)           ║");
            System.out.println("║  ONLINE  HISTORY  QUIT                ║");
            System.out.println("╚══════════════════════════════════════╝");

            startReaderThread();   // background thread reads from server
            sendLoop();            // main thread reads from keyboard

        } catch (IOException e) {
            System.err.println("[Client] Cannot connect to " + host + ":" + port
                    + " — " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    // ─── Reader Thread ────────────────────────────────────────────────────────

    /**
     * Spawns a daemon thread that continuously reads lines from the server
     * and prints them to stdout.
     *
     * <p>Using a separate thread means the main thread is never blocked waiting
     * for server responses — it can keep reading keyboard input simultaneously.</p>
     */
    private void startReaderThread() {
        Thread readerThread = new Thread(() -> {
            try {
                String line;
                while (connected && (line = reader.readLine()) != null) {
                    System.out.println("[Server] " + line);
                }
            } catch (IOException e) {
                if (connected) {
                    System.err.println("[Client] Lost connection: " + e.getMessage());
                }
            } finally {
                connected = false;
            }
        }, "ClientReaderThread");

        readerThread.setDaemon(true);   // die when the main thread exits
        readerThread.start();
    }

    // ─── Send Loop ────────────────────────────────────────────────────────────

    /**
     * Reads commands from {@code System.in} and sends them to the server.
     *
     * <p>Exits when the user types {@code QUIT} or the connection drops.</p>
     */
    private void sendLoop() {
        Scanner scanner = new Scanner(System.in);

        while (connected) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            writer.println(input);   // send to server

            if (input.equalsIgnoreCase("QUIT")) {
                connected = false;
                System.out.println("[Client] Disconnecting…");
                break;
            }
        }
        scanner.close();
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    /**
     * Sends a single command programmatically (useful for automated testing).
     *
     * @param command the protocol command string
     */
    public void sendCommand(String command) {
        if (writer != null && connected) {
            writer.println(command);
        }
    }

    /**
     * Cleanly closes the socket and streams.
     */
    public void disconnect() {
        connected = false;
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("[Client] Disconnected.");
        } catch (IOException e) {
            System.err.println("[Client] Error during disconnect: " + e.getMessage());
        }
    }

    /**
     * Returns {@code true} if the client is currently connected to the server.
     *
     * @return connection status
     */
    public boolean isConnected() {
        return connected;
    }

    // ─── Entry Point ──────────────────────────────────────────────────────────

    /**
     * Standalone entry point — connect to localhost:9090 and start the CLI.
     *
     * @param args optional: args[0] = host, args[1] = port
     */
    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : "localhost";
        int    port = (args.length > 1) ? Integer.parseInt(args[1])
                                        : ChatServer.DEFAULT_PORT;

        new ChatClient(host, port).start();
    }
}
