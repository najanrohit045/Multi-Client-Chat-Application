package model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Message - Immutable-style entity representing a single chat message.
 *
 * <p>Models both <b>broadcast</b> (group) messages and <b>private</b> messages.
 * When {@link #receiver} is {@code "ALL"} the message is treated as a broadcast.</p>
 *
 * <p><b>Concepts Used:</b> OOP, Encapsulation, Serializable, Java 8 Date/Time API</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 3L;

    /** Sentinel value used for broadcast / group messages. */
    public static final String BROADCAST = "ALL";

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ─── Fields ───────────────────────────────────────────────────────────────
    /** Auto-incremented primary key from the database. */
    private int           id;

    /** Username of the sender. */
    private String        sender;

    /**
     * Username of the recipient, or {@link #BROADCAST} for group messages.
     */
    private String        receiver;

    /** The text content of the message. */
    private String        content;

    /** When the message was created / sent. */
    private LocalDateTime timestamp;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /** Default no-arg constructor. */
    public Message() {
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Constructor for new outgoing messages (id assigned by DB later).
     *
     * @param sender   username of the sender
     * @param receiver username of the recipient, or {@link #BROADCAST}
     * @param content  message text
     */
    public Message(String sender, String receiver, String content) {
        this.sender    = sender;
        this.receiver  = receiver;
        this.content   = content;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Full constructor used when re-hydrating a message from the database.
     *
     * @param id        database primary key
     * @param sender    username of the sender
     * @param receiver  username of the recipient, or {@link #BROADCAST}
     * @param content   message text
     * @param timestamp when the message was stored
     */
    public Message(int id, String sender, String receiver,
                   String content, LocalDateTime timestamp) {
        this.id        = id;
        this.sender    = sender;
        this.receiver  = receiver;
        this.content   = content;
        this.timestamp = timestamp;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public int           getId()        { return id; }
    public void          setId(int id)  { this.id = id; }

    public String        getSender()           { return sender; }
    public void          setSender(String s)   { this.sender = s; }

    public String        getReceiver()          { return receiver; }
    public void          setReceiver(String r)  { this.receiver = r; }

    public String        getContent()           { return content; }
    public void          setContent(String c)   { this.content = c; }

    public LocalDateTime getTimestamp()          { return timestamp; }
    public void          setTimestamp(LocalDateTime t) { this.timestamp = t; }

    // ─── Utility ──────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when this is a broadcast (group) message.
     *
     * @return whether the receiver is {@link #BROADCAST}
     */
    public boolean isBroadcast() {
        return BROADCAST.equalsIgnoreCase(receiver);
    }

    /**
     * Formats the message for display in the chat window.
     *
     * <p>Example: {@code [2024-01-15 10:30:00] alice → bob: hello!}</p>
     *
     * @return formatted display string
     */
    public String toDisplayString() {
        String ts  = (timestamp != null) ? timestamp.format(FORMATTER) : "N/A";
        String rec = isBroadcast() ? "Everyone" : receiver;
        return "[" + ts + "] " + sender + " → " + rec + ": " + content;
    }

    /**
     * Returns the timestamp as a plain string (for file logging and SQL).
     *
     * @return formatted timestamp string or {@code "N/A"}
     */
    public String getTimestampString() {
        return (timestamp != null) ? timestamp.format(FORMATTER) : "N/A";
    }

    @Override
    public String toString() {
        return toDisplayString();
    }
}
