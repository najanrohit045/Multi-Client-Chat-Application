package dao;

import exception.UserNotFoundException;
import model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import database.DBConnection;

/**
 * UserDAO - Data Access Object for all user-related database operations.
 *
 * <p>Encapsulates every SQL interaction with the {@code users} table so that
 * the service layer never needs to write raw SQL.  All statements use
 * {@link PreparedStatement} to prevent SQL injection.</p>
 *
 * <p><b>Concepts Used:</b> DAO Pattern, JDBC, PreparedStatement, ResultSet,
 * Exception Handling, Collections (ArrayList), OOP, SOLID (SRP)</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class UserDAO {

    // ─── SQL Constants ────────────────────────────────────────────────────────
    private static final String INSERT_USER   =
            "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";

    private static final String FIND_BY_NAME  =
            "SELECT id, username, password, role FROM users WHERE username = ?";

    private static final String FIND_BY_ID    =
            "SELECT id, username, password, role FROM users WHERE id = ?";

    private static final String SELECT_ALL    =
            "SELECT id, username, password, role FROM users";

    private static final String DELETE_USER   =
            "DELETE FROM users WHERE username = ?";

    private static final String UPDATE_PASS   =
            "UPDATE users SET password = ? WHERE username = ?";

    private static final String AUTH_USER     =
            "SELECT id, username, password, role FROM users WHERE username = ? AND password = ?";

    // ─── CRUD Operations ──────────────────────────────────────────────────────

    /**
     * Persists a new {@link User} to the database.
     *
     * <p>Uses {@link PreparedStatement#RETURN_GENERATED_KEYS} so the
     * auto-incremented id is immediately available on the returned object.</p>
     *
     * @param user the user to register (id field will be set after insertion)
     * @return {@code true} if the row was inserted successfully
     */
    public boolean registerUser(User user) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_USER,
                     Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getRole());

            int rows = ps.executeUpdate();

            if (rows > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        user.setId(keys.getInt(1));   // inject the generated PK
                    }
                }
                System.out.println("[UserDAO] Registered: " + user.getUsername());
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] registerUser error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Authenticates a user by matching username AND password.
     *
     * @param username login handle
     * @param password plain-text password (hash before passing in production)
     * @return the matching {@link User}, or {@code null} if credentials are wrong
     */
    public User authenticateUser(String username, String password) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(AUTH_USER)) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] authenticateUser error: " + e.getMessage());
        }
        return null;  // null signals authentication failure
    }

    /**
     * Finds a user by username.
     *
     * @param username the handle to search for
     * @return the {@link User} object
     * @throws UserNotFoundException if no row exists with that username
     */
    public User findByUsername(String username) throws UserNotFoundException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_NAME)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] findByUsername error: " + e.getMessage());
        }
        throw new UserNotFoundException(
                "User not found: " + username, username);
    }

    /**
     * Finds a user by their database id.
     *
     * @param id the primary key to search for
     * @return the {@link User} object
     * @throws UserNotFoundException if no row exists with that id
     */
    public User findById(int id) throws UserNotFoundException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_ID)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] findById error: " + e.getMessage());
        }
        throw new UserNotFoundException("User not found for id: " + id);
    }

    /**
     * Retrieves every user from the database.
     *
     * @return {@link List} of all users (may be empty, never {@code null})
     */
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                users.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] getAllUsers error: " + e.getMessage());
        }
        return users;
    }

    /**
     * Removes a user from the database by username.
     *
     * @param username handle of the user to remove
     * @return {@code true} if a row was deleted
     */
    public boolean deleteUser(String username) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_USER)) {

            ps.setString(1, username);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[UserDAO] deleteUser error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Updates the password for an existing user.
     *
     * @param username    handle of the user to update
     * @param newPassword the new password value
     * @return {@code true} if the row was updated
     */
    public boolean updatePassword(String username, String newPassword) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_PASS)) {

            ps.setString(1, newPassword);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[UserDAO] updatePassword error: " + e.getMessage());
        }
        return false;
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Maps a single {@link ResultSet} row to a {@link User} object.
     *
     * @param rs a ResultSet positioned on the target row
     * @return the hydrated {@link User}
     * @throws SQLException if a column cannot be read
     */
    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("role")
        );
    }
}
