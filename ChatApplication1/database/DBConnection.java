package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DBConnection - Factory for MySQL database connections.
 *
 * <p>Each call creates a new JDBC connection. DAO methods use
 * try-with-resources, so sharing one connection would cause later operations
 * to receive a connection that a previous DAO call had already closed.</p>
 *
 * <p><b>Design Pattern:</b> Utility connection factory</p>
 * <p><b>Concepts Used:</b> JDBC, Singleton Pattern, Thread Safety, Encapsulation</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class DBConnection {

    // ─── Configuration ────────────────────────────────────────────────────────
    private static final String URL      = "jdbc:mysql://localhost:3306/chat_app";
    private static final String USER     = "root";
    private static final String PASSWORD = "Rohit@321";  // Change to your MySQL password
    private static final String DRIVER   = "com.mysql.cj.jdbc.Driver";

    /** Ensures the JDBC driver is loaded exactly once. */
    private static volatile boolean driverLoaded = false;

    // ─── Private Constructor ──────────────────────────────────────────────────
    /**
     * Private constructor prevents direct instantiation.
     * Use {@link #getConnection()} instead.
     */
    private DBConnection() { }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Creates a new {@link Connection} for the caller.
     *
     * <p>The returned connection belongs to the caller and should be closed
     * with try-with-resources. This keeps concurrent DAO operations isolated.</p>
     *
     * @return a live {@link Connection} to the chat_app MySQL database
     * @throws RuntimeException if the JDBC driver is missing or the DB is unreachable
     */
    public static Connection getConnection() {
        if (!driverLoaded) {
            synchronized (DBConnection.class) {
                if (!driverLoaded) {
                    try {
                        Class.forName(DRIVER);
                        driverLoaded = true;
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("[DB] MySQL JDBC driver not found.", e);
                    }
                }
            }
        }
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            throw new RuntimeException("[DB] Failed to connect to database: " + e.getMessage(), e);
        }
    }

    /**
     * Retained for source compatibility. Connections are owned and closed by
     * their DAO callers, so there is no shared connection to close.
     */
    public static void closeConnection() {
        // No shared connection is held by this class.
    }
}
