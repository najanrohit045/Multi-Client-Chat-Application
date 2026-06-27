package exception;

/**
 * InvalidMessageException - Checked exception thrown for malformed messages.
 *
 * <p>Raised when message validation fails — e.g. an empty body, a message
 * that exceeds the maximum length, or a null sender/receiver.</p>
 *
 * <p><b>Concepts Used:</b> Custom Exception, Exception Hierarchy, OOP</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class InvalidMessageException extends Exception {

    private static final long serialVersionUID = 11L;

    /** Maximum allowed message length (characters). */
    public static final int MAX_LENGTH = 1000;

    /** The invalid message content, kept for diagnostic purposes. */
    private final String invalidContent;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /**
     * Constructs the exception with a plain message.
     *
     * @param message human-readable description of the validation failure
     */
    public InvalidMessageException(String message) {
        super(message);
        this.invalidContent = null;
    }

    /**
     * Constructs the exception preserving the offending content.
     *
     * @param message        human-readable description
     * @param invalidContent the message text that failed validation
     */
    public InvalidMessageException(String message, String invalidContent) {
        super(message);
        this.invalidContent = invalidContent;
    }

    /**
     * Constructs the exception wrapping a lower-level cause.
     *
     * @param message human-readable description
     * @param cause   the underlying {@link Throwable}
     */
    public InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
        this.invalidContent = null;
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /**
     * Returns the raw message content that failed validation, or {@code null}.
     *
     * @return the invalid content string
     */
    public String getInvalidContent() {
        return invalidContent;
    }
}
