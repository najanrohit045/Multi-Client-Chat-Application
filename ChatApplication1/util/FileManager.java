package util;

import model.Message;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * FileManager - Utility class for reading and writing chat log files.
 *
 * <p>All chat messages are appended to {@code chat_history.txt} so that logs
 * persist across server restarts. Admins can read the entire log at any time.</p>
 *
 * <p>File operations use {@link BufferedWriter} / {@link BufferedReader} for
 * efficiency and {@link synchronized} blocks for thread safety when multiple
 * {@link network.ClientHandler} threads write simultaneously.</p>
 *
 * <p><b>Concepts Used:</b> File Handling, BufferedWriter, BufferedReader,
 * Synchronization, Exception Handling, Java NIO, Collections</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class FileManager {

    // ─── Constants ────────────────────────────────────────────────────────────
    /** Path of the main chat log file relative to the working directory. */
    public static final String LOG_FILE    = "chat_history.txt";

    /** Path of the user activity log. */
    public static final String AUDIT_FILE  = "audit_log.txt";

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Shared lock object — all methods synchronize on this to serialise I/O. */
    private static final Object FILE_LOCK = new Object();

    // ─── Private Constructor ──────────────────────────────────────────────────
    /** Utility class — do not instantiate. */
    private FileManager() { }

    // ─── Write Operations ─────────────────────────────────────────────────────

    /**
     * Appends a single {@link Message} to the chat log file in a thread-safe manner.
     *
     * <p>Opens the file in append mode so existing history is never overwritten.</p>
     *
     * @param message the message to log
     */
    public static void logMessage(Message message) {
        synchronized (FILE_LOCK) {
            try (BufferedWriter writer = new BufferedWriter(
                    new FileWriter(LOG_FILE, true))) {  // append = true

                writer.write(message.toDisplayString());
                writer.newLine();
                writer.flush();

            } catch (IOException e) {
                System.err.println("[FileManager] Failed to log message: " + e.getMessage());
            }
        }
    }

    /**
     * Writes a raw string entry to the audit / activity log.
     *
     * <p>Used to record events like logins, logouts, and admin actions.</p>
     *
     * @param entry the text line to append
     */
    public static void logAuditEvent(String entry) {
        synchronized (FILE_LOCK) {
            try (BufferedWriter writer = new BufferedWriter(
                    new FileWriter(AUDIT_FILE, true))) {

                String ts = LocalDateTime.now().format(FMT);
                writer.write("[" + ts + "] " + entry);
                writer.newLine();
                writer.flush();

            } catch (IOException e) {
                System.err.println("[FileManager] Failed to write audit log: " + e.getMessage());
            }
        }
    }

    /**
     * Bulk-writes a list of messages to the chat log, replacing its content.
     *
     * <p>Useful for exporting the full DB history at startup.</p>
     *
     * @param messages the complete list of messages to write
     */
    public static void writeAllMessages(List<Message> messages) {
        synchronized (FILE_LOCK) {
            try (BufferedWriter writer = new BufferedWriter(
                    new FileWriter(LOG_FILE, false))) {  // overwrite mode

                writer.write("=== Chat History Export — "
                        + LocalDateTime.now().format(FMT) + " ===");
                writer.newLine();

                for (Message msg : messages) {
                    writer.write(msg.toDisplayString());
                    writer.newLine();
                }
                writer.flush();
                System.out.println("[FileManager] Wrote " + messages.size()
                        + " messages to " + LOG_FILE);

            } catch (IOException e) {
                System.err.println("[FileManager] writeAllMessages error: " + e.getMessage());
            }
        }
    }

    // ─── Read Operations ──────────────────────────────────────────────────────

    /**
     * Reads every line from the chat log file.
     *
     * @return {@link List} of log lines (empty list if the file does not exist)
     */
    public static List<String> readChatHistory() {
        List<String> lines = new ArrayList<>();
        synchronized (FILE_LOCK) {
            File f = new File(LOG_FILE);
            if (!f.exists()) {
                System.out.println("[FileManager] No log file found at: " + LOG_FILE);
                return lines;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException e) {
                System.err.println("[FileManager] readChatHistory error: " + e.getMessage());
            }
        }
        return lines;
    }

    /**
     * Reads the last {@code n} lines from the chat log using Java NIO.
     *
     * @param n number of tail lines to return
     * @return list of at most {@code n} lines (in order)
     */
    public static List<String> readLastNLines(int n) {
        List<String> all  = readChatHistory();
        List<String> tail = new ArrayList<>();

        int start = Math.max(0, all.size() - n);
        for (int i = start; i < all.size(); i++) {
            tail.add(all.get(i));
        }
        return tail;
    }

    /**
     * Reads every line from the audit log.
     *
     * @return {@link List} of audit entries
     */
    public static List<String> readAuditLog() {
        List<String> lines = new ArrayList<>();
        synchronized (FILE_LOCK) {
            File f = new File(AUDIT_FILE);
            if (!f.exists()) return lines;

            try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException e) {
                System.err.println("[FileManager] readAuditLog error: " + e.getMessage());
            }
        }
        return lines;
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    /**
     * Clears the chat log file (admin-only action).
     *
     * @return {@code true} if the file was successfully cleared
     */
    public static boolean clearChatHistory() {
        synchronized (FILE_LOCK) {
            try {
                Files.write(Paths.get(LOG_FILE), new byte[0],
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
                System.out.println("[FileManager] Chat history cleared.");
                return true;
            } catch (IOException e) {
                System.err.println("[FileManager] clearChatHistory error: " + e.getMessage());
                return false;
            }
        }
    }

    /**
     * Checks whether the chat log file exists on disk.
     *
     * @return {@code true} if {@link #LOG_FILE} exists
     */
    public static boolean logFileExists() {
        return new File(LOG_FILE).exists();
    }
}
