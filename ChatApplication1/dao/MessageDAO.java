package dao;

import database.DBConnection;
import model.Message;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MessageDAO - Data Access Object for all message-related database operations.
 *
 * <p>Handles storing, retrieving and deleting {@link Message} records in the
 * {@code messages} table. All writes use transactions to guarantee atomicity.</p>
 *
 * <p><b>Concepts Used:</b> DAO Pattern, JDBC, PreparedStatement, ResultSet,
 * Transactions, Java 8 LocalDateTime, Collections (ArrayList), Exception Handling</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class MessageDAO {

    // ─── SQL Constants ────────────────────────────────────────────────────────
    private static final String INSERT_MSG =
            "INSERT INTO messages (sender, receiver, message, timestamp) VALUES (?, ?, ?, ?)";

    private static final String SELECT_ALL =
            "SELECT id, sender, receiver, message, timestamp FROM messages ORDER BY timestamp ASC";

    private static final String SELECT_BY_USERS =
            "SELECT id, sender, receiver, message, timestamp FROM messages " +
            "WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) " +
            "ORDER BY timestamp ASC";

    private static final String SELECT_BROADCAST =
            "SELECT id, sender, receiver, message, timestamp FROM messages " +
            "WHERE receiver = 'ALL' ORDER BY timestamp ASC";

    private static final String DELETE_BY_USER =
            "DELETE FROM messages WHERE sender = ? OR receiver = ?";

    private static final String SELECT_RECENT =
            "SELECT id, sender, receiver, message, timestamp FROM messages " +
            "ORDER BY timestamp DESC LIMIT ?";

    // ─── Write Operations ─────────────────────────────────────────────────────

    /**
     * Persists a single {@link Message} inside an explicit transaction.
     *
     * <p>Disables auto-commit, executes the INSERT, then commits. Rolls back
     * on any {@link SQLException} to keep the database consistent.</p>
     *
     * @param message the message to store
     * @return {@code true} if the row was inserted successfully
     */
    public boolean saveMessage(Message message) {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);                         // begin transaction

            try (PreparedStatement ps = conn.prepareStatement(
                    INSERT_MSG, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, message.getSender());
                ps.setString(2, message.getReceiver());
                ps.setString(3, message.getContent());
                ps.setTimestamp(4, Timestamp.valueOf(
                        message.getTimestamp() != null
                                ? message.getTimestamp()
                                : LocalDateTime.now()));

                int rows = ps.executeUpdate();
                conn.commit();                                 // commit transaction

                if (rows > 0) {
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            message.setId(keys.getInt(1));
                        }
                    }
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("[MessageDAO] saveMessage error: " + e.getMessage());
            rollback(conn);
        } finally {
            resetAutoCommit(conn);
        }
        return false;
    }

    /**
     * Saves multiple messages in a single batched transaction.
     *
     * <p>Significantly more efficient than calling {@link #saveMessage(Message)}
     * in a loop when bulk-importing history.</p>
     *
     * @param messages the list of messages to batch-insert
     * @return number of rows successfully inserted
     */
    public int saveMessagesBatch(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return 0;

        Connection conn = null;
        int count = 0;

        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(INSERT_MSG)) {
                for (Message msg : messages) {
                    ps.setString(1, msg.getSender());
                    ps.setString(2, msg.getReceiver());
                    ps.setString(3, msg.getContent());
                    ps.setTimestamp(4, Timestamp.valueOf(
                            msg.getTimestamp() != null
                                    ? msg.getTimestamp()
                                    : LocalDateTime.now()));
                    ps.addBatch();
                }

                int[] results = ps.executeBatch();
                conn.commit();

                for (int r : results) {
                    if (r > 0) count++;
                }
            }
        } catch (SQLException e) {
            System.err.println("[MessageDAO] saveMessagesBatch error: " + e.getMessage());
            rollback(conn);
        } finally {
            resetAutoCommit(conn);
        }
        return count;
    }

    // ─── Read Operations ──────────────────────────────────────────────────────

    /**
     * Retrieves the full message history from the database.
     *
     * @return ordered {@link List} of all messages (ascending timestamp)
     */
    public List<Message> getAllMessages() {
        List<Message> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[MessageDAO] getAllMessages error: " + e.getMessage());
        }
        return list;
    }

    /**
     * Retrieves the conversation history between two specific users.
     *
     * @param user1 one participant's username
     * @param user2 the other participant's username
     * @return ordered {@link List} of messages exchanged between user1 and user2
     */
    public List<Message> getConversation(String user1, String user2) {
        List<Message> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_USERS)) {

            ps.setString(1, user1);
            ps.setString(2, user2);
            ps.setString(3, user2);
            ps.setString(4, user1);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[MessageDAO] getConversation error: " + e.getMessage());
        }
        return list;
    }

    /**
     * Retrieves all broadcast (group) messages.
     *
     * @return {@link List} of messages with receiver {@code "ALL"}
     */
    public List<Message> getBroadcastMessages() {
        List<Message> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BROADCAST);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[MessageDAO] getBroadcastMessages error: " + e.getMessage());
        }
        return list;
    }

    /**
     * Retrieves the {@code n} most recent messages across all conversations.
     *
     * @param limit maximum number of messages to return
     * @return {@link List} of recent messages (descending timestamp)
     */
    public List<Message> getRecentMessages(int limit) {
        List<Message> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_RECENT)) {

            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[MessageDAO] getRecentMessages error: " + e.getMessage());
        }
        return list;
    }

    // ─── Delete Operations ────────────────────────────────────────────────────

    /**
     * Deletes all messages sent by or addressed to a specific user.
     *
     * <p>Called when an admin removes a user from the system.</p>
     *
     * @param username the user whose messages should be purged
     * @return number of rows deleted
     */
    public int deleteMessagesForUser(String username) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_BY_USER)) {

            ps.setString(1, username);
            ps.setString(2, username);
            return ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[MessageDAO] deleteMessagesForUser error: " + e.getMessage());
        }
        return 0;
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Maps a {@link ResultSet} row to a {@link Message} object.
     *
     * @param rs a ResultSet positioned on the target row
     * @return the hydrated {@link Message}
     * @throws SQLException if a column cannot be read
     */
    private Message mapRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("timestamp");
        return new Message(
                rs.getInt("id"),
                rs.getString("sender"),
                rs.getString("receiver"),
                rs.getString("message"),
                ts != null ? ts.toLocalDateTime() : LocalDateTime.now()
        );
    }

    /**
     * Rolls back a connection quietly, suppressing any secondary exception.
     *
     * @param conn the connection to roll back (may be {@code null})
     */
    private void rollback(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
                System.err.println("[MessageDAO] Transaction rolled back.");
            } catch (SQLException ex) {
                System.err.println("[MessageDAO] Rollback failed: " + ex.getMessage());
            }
        }
    }

    /**
     * Restores auto-commit mode after a manual transaction.
     *
     * @param conn the connection to reset (may be {@code null})
     */
    private void resetAutoCommit(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ex) {
                System.err.println("[MessageDAO] resetAutoCommit error: " + ex.getMessage());
            }
        }
    }
}
