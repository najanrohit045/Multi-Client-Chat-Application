package service;

import dao.UserDAO;
import exception.UserNotFoundException;
import model.User;
import util.FileManager;

import java.util.List;

/**
 * UserService - Business logic layer for user management.
 *
 * <p>Acts as the intermediary between the network/UI layer and the DAO layer,
 * applying business rules (e.g. duplicate-username checks, role assignment)
 * before delegating persistence to {@link UserDAO}.</p>
 *
 * <p><b>SOLID:</b> Single Responsibility (user-lifecycle concerns only),
 * Dependency Inversion (depends on DAO abstraction, not direct SQL).</p>
 *
 * <p><b>Concepts Used:</b> Service Layer, OOP, Exception Handling,
 * Collections, Encapsulation, Layered Architecture</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class UserService {

    /** Minimum required password length. */
    private static final int MIN_PASSWORD_LEN = 4;

    // ─── Dependencies ─────────────────────────────────────────────────────────
    private final UserDAO userDAO;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /**
     * Constructs a UserService with its required DAO dependency.
     */
    public UserService() {
        this.userDAO = new UserDAO();
    }

    // ─── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers a new regular user after applying business-rule validations.
     *
     * <p>Checks:
     * <ul>
     *   <li>Username and password are non-blank</li>
     *   <li>Password meets minimum length</li>
     *   <li>Username is not already taken</li>
     * </ul></p>
     *
     * @param username  desired login handle
     * @param password  desired password
     * @return the newly created {@link User} with its generated id
     * @throws IllegalArgumentException if validation fails
     */
    public User registerUser(String username, String password) {
        // ── Validate input ──────────────────────────────────────────────────
        validateCredentials(username, password);

        // ── Check uniqueness ────────────────────────────────────────────────
        if (usernameExists(username)) {
            throw new IllegalArgumentException(
                    "Username '" + username + "' is already taken.");
        }

        // ── Persist ─────────────────────────────────────────────────────────
        User user = new User(username, password, "USER");
        boolean saved = userDAO.registerUser(user);

        if (!saved) {
            throw new RuntimeException("Failed to register user — database error.");
        }

        FileManager.logAuditEvent("REGISTER | user=" + username);
        System.out.println("[UserService] New user registered: " + username);
        return user;
    }

    /**
     * Registers an admin user.
     *
     * @param username admin login handle
     * @param password admin password
     * @return the newly created admin {@link User}
     */
    public User registerAdmin(String username, String password) {
        validateCredentials(username, password);

        if (usernameExists(username)) {
            throw new IllegalArgumentException(
                    "Username '" + username + "' is already taken.");
        }

        User admin = new User(username, password, "ADMIN");
        userDAO.registerUser(admin);

        FileManager.logAuditEvent("REGISTER_ADMIN | user=" + username);
        return admin;
    }

    // ─── Authentication ───────────────────────────────────────────────────────

    /**
     * Authenticates a user and returns their profile on success.
     *
     * @param username login handle
     * @param password submitted password
     * @return the authenticated {@link User}
     * @throws UserNotFoundException if credentials do not match any record
     */
    public User login(String username, String password) throws UserNotFoundException {
        if (username == null || username.trim().isEmpty()
                || password == null || password.trim().isEmpty()) {
            throw new UserNotFoundException("Username and password are required.");
        }

        User user = userDAO.authenticateUser(username, password);
        if (user == null) {
            throw new UserNotFoundException(
                    "Invalid credentials for user: " + username, username);
        }

        FileManager.logAuditEvent("LOGIN | user=" + username);
        System.out.println("[UserService] Login: " + username + " [" + user.getRole() + "]");
        return user;
    }

    // ─── Query Operations ─────────────────────────────────────────────────────

    /**
     * Retrieves a user by their username.
     *
     * @param username the handle to look up
     * @return the matching {@link User}
     * @throws UserNotFoundException if the username does not exist
     */
    public User getUserByUsername(String username) throws UserNotFoundException {
        return userDAO.findByUsername(username);
    }

    /**
     * Retrieves all registered users.
     *
     * @return {@link List} of every {@link User}
     */
    public List<User> getAllUsers() {
        return userDAO.getAllUsers();
    }

    // ─── Admin Operations ─────────────────────────────────────────────────────

    /**
     * Removes a user from the system (admin action).
     *
     * @param username the user to remove
     * @return {@code true} if the user was found and deleted
     * @throws UserNotFoundException if the username does not exist
     */
    public boolean removeUser(String username) throws UserNotFoundException {
        // Confirm user exists before deleting
        userDAO.findByUsername(username);   // throws if absent

        boolean deleted = userDAO.deleteUser(username);
        if (deleted) {
            FileManager.logAuditEvent("REMOVE_USER | target=" + username);
            System.out.println("[UserService] Removed user: " + username);
        }
        return deleted;
    }

    /**
     * Updates a user's password.
     *
     * @param username    the user whose password to change
     * @param newPassword the new password value
     * @return {@code true} if the update succeeded
     */
    public boolean updatePassword(String username, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LEN) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_PASSWORD_LEN + " characters.");
        }
        return userDAO.updatePassword(username, newPassword);
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Returns {@code true} if a user with the given username already exists.
     *
     * @param username the handle to check
     * @return whether the username is taken
     */
    private boolean usernameExists(String username) {
        try {
            userDAO.findByUsername(username);
            return true;                         // no exception → user found
        } catch (UserNotFoundException e) {
            return false;                        // exception → user absent
        }
    }

    /**
     * Validates that credentials are non-blank and meet length requirements.
     *
     * @param username the username to validate
     * @param password the password to validate
     * @throws IllegalArgumentException on validation failure
     */
    private void validateCredentials(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be blank.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LEN) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_PASSWORD_LEN + " characters.");
        }
        if (username.length() > 50) {
            throw new IllegalArgumentException("Username too long (max 50 chars).");
        }
    }
}
