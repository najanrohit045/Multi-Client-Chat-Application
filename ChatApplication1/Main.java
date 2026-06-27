import network.ChatClient;
import network.ChatServer;
import service.UserService;

import java.util.Scanner;

/**
 * Main - Application entry point for the Multi-Threaded Chat Application.
 *
 * <p>Presents a simple text menu to start the server, connect as a client,
 * or set up seed data.  In a production deployment, the server and clients
 * would run as separate JVM processes (or even separate machines).</p>
 *
 * <p><b>Concepts Used:</b> OOP, Exception Handling, Scanner, Service Layer</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class Main {

    public static void main(String[] args) {
        printBanner();

        if (args.length > 0) {
            // Non-interactive mode: java Main server | client [host] [port]
            switch (args[0].toLowerCase()) {
                case "server":
                    startServer();
                    break;
                case "client":
                    String host = args.length > 1 ? args[1] : "localhost";
                    int    port = args.length > 2 ? Integer.parseInt(args[2])
                                                  : ChatServer.DEFAULT_PORT;
                    new ChatClient(host, port).start();
                    break;
                case "setup":
                    setupSeedData();
                    break;
                default:
                    System.err.println("Unknown argument: " + args[0]);
                    printUsage();
            }
            return;
        }

        // ── Interactive menu ─────────────────────────────────────────────────
        Scanner sc = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();
            System.out.print("Choose an option: ");

            String choice = sc.nextLine().trim();

            switch (choice) {
                case "1":
                    startServer();
                    break;

                case "2":
                    System.out.print("Server host [localhost]: ");
                    String host = sc.nextLine().trim();
                    if (host.isEmpty()) host = "localhost";

                    System.out.print("Server port [" + ChatServer.DEFAULT_PORT + "]: ");
                    String portStr = sc.nextLine().trim();
                    int port = portStr.isEmpty() ? ChatServer.DEFAULT_PORT
                                                 : Integer.parseInt(portStr);

                    new ChatClient(host, port).start();
                    break;

                case "3":
                    setupSeedData();
                    break;

                case "4":
                    running = false;
                    System.out.println("Goodbye!");
                    break;

                default:
                    System.out.println("Invalid option. Please enter 1–4.");
            }
        }
        sc.close();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Starts the chat server on the default port. */
    private static void startServer() {
        System.out.println("[Main] Starting Chat Server on port "
                + ChatServer.DEFAULT_PORT + "...");
        new ChatServer().start();   // blocks until server is stopped
    }

    /**
     * Seeds the database with two demo users: a regular user and an admin.
     *
     * <p>Safe to call multiple times — duplicate usernames produce a clear
     * error message rather than crashing.</p>
     */
    private static void setupSeedData() {
        UserService svc = new UserService();
        System.out.println("[Setup] Creating seed users...");

        try {
            svc.registerUser("alice", "pass1234");
            System.out.println("[Setup] Created user: alice / pass1234");
        } catch (IllegalArgumentException e) {
            System.out.println("[Setup] alice already exists — skipping.");
        }

        try {
            svc.registerUser("bob", "pass1234");
            System.out.println("[Setup] Created user: bob / pass1234");
        } catch (IllegalArgumentException e) {
            System.out.println("[Setup] bob already exists — skipping.");
        }

        try {
            svc.registerAdmin("admin", "admin123");
            System.out.println("[Setup] Created admin: admin / admin123");
        } catch (IllegalArgumentException e) {
            System.out.println("[Setup] admin already exists — skipping.");
        }

        System.out.println("[Setup] Seed data ready.");
    }

    // ─── Display ──────────────────────────────────────────────────────────────

    private static void printBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║    Multi-Threaded Chat Application v1.0      ║");
        System.out.println("║    Core Java · JDBC · MySQL · Sockets        ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("┌─────────────────────────┐");
        System.out.println("│  1. Start Server        │");
        System.out.println("│  2. Start Client        │");
        System.out.println("│  3. Setup Seed Data     │");
        System.out.println("│  4. Exit                │");
        System.out.println("└─────────────────────────┘");
    }

    private static void printUsage() {
        System.out.println("Usage: java Main [server | client [host] [port] | setup]");
    }
}
