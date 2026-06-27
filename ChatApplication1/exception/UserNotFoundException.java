package exception;

/**
 * UserNotFoundException - Checked exception thrown when a user lookup fails.
 *
 * <p>Thrown by the service and DAO layers whenever a username or user ID
 * cannot be found in the database or the online-user registry.</p>
 *
 * <p><b>Concepts Used:</b> Custom Exception, Exception Hierarchy, OOP</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class UserNotFoundException extends Exception {

    private static final long serialVersionUID = 10L;

    /** The username (or id) that was not found. */
    private final String username;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /**
     * Constructs the exception with a descriptive message.
     *
     * @param message human-readable description of why the lookup failed
     */
    public UserNotFoundException(String message) {
        super(message);
        this.username = null;
    }

    /**
     * Constructs the exception with a message and the offending username.
     *
     * @param message  human-readable description
     * @param username the username that could not be located
     */
    public UserNotFoundException(String message, String username) {
        super(message);
        this.username = username;
    }

    /**
     * Constructs the exception wrapping a lower-level cause.
     *
     * @param message human-readable description
     * @param cause   the underlying {@link Throwable}
     */
    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.username = null;
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns the username that triggered this exception, or {@code null}
     * if the constructor that omits it was used.
     *
     * @return the missing username
     */
    public String getUsername() {
        return username;
    }
}
