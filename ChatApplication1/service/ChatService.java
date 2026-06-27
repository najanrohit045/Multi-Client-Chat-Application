package service;

import dao.MessageDAO;
import exception.InvalidMessageException;
import model.Message;
import network.ChatServer;
import util.FileManager;

import java.util.*;

/**
 * ChatService - Business logic layer for sending and retrieving messages.
 *
 * <p>Validates message content, routes to the correct delivery path
 * (broadcast vs private), persists to the database via {@link MessageDAO},
 * and logs to the file system via {@link FileManager}.</p>
 *
 * <p>An in-memory {@link Queue} buffers undelivered messages so the server
 * can process them sequentially when the target client reconnects.</p>
 *
 * <p><b>Concepts Used:</b> Service Layer, Collections (Queue, HashMap, List),
 * Exception Handling, Synchronization, OOP, SOLID</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class ChatService {

    // ─── Dependencies ─────────────────────────────────────────────────────────
    private final MessageDAO messageDAO;

    /**
     * Per-user pending-message queues for offline delivery.
     * Synchronized because multiple threads may enqueue / dequeue simultaneously.
     */
    private final Map<String, Queue<Message>> pendingMessages;

    // ─── Constructor ──────────────────────────────────────────────────────────

    /** Constructs the ChatService, initialising its DAO and in-memory queue. */
    public ChatService() {
        this.messageDAO     = new MessageDAO();
        this.pendingMessages = Collections.synchronizedMap(new HashMap<>());
    }

    // ─── Send Operations ──────────────────────────────────────────────────────

    /**
     * Validates and persists a broadcast message (sender → everyone).
     *
     * <p>The message is saved to the DB and appended to the file log. The
     * actual network broadcast is handled by {@link network.ChatServer}.</p>
     *
     * @param sender  username of the sender
     * @param content message text
     * @return the persisted {@link Message} with a generated id
     * @throws InvalidMessageException if the content fails validation
     */
    public Message sendBroadcast(String sender, String content)
            throws InvalidMessageException {

        validateContent(content);

        Message msg = new Message(sender, Message.BROADCAST, content);
        messageDAO.saveMessage(msg);
        FileManager.logMessage(msg);

        System.out.println("[ChatService] Broadcast from " + sender
                + ": " + content);
        return msg;
    }

    /**
     * Validates and persists a private message (sender → specific receiver).
     *
     * <p>If the receiver is not currently connected, the message is enqueued
     * in {@link #pendingMessages} for later delivery.</p>
     *
     * @param sender   username of the sender
     * @param receiver username of the intended recipient
     * @param content  message text
     * @param isOnline {@code true} if the receiver is currently connected
     * @return the persisted {@link Message}
     * @throws InvalidMessageException if the content fails validation
     */
    public Message sendPrivateMessage(String sender, String receiver,
                                      String content, boolean isOnline)
            throws InvalidMessageException {

        validateContent(content);

        if (sender.equals(receiver)) {
            throw new InvalidMessageException("Cannot send a message to yourself.", content);
        }

        Message msg = new Message(sender, receiver, content);
        messageDAO.saveMessage(msg);
        FileManager.logMessage(msg);

        if (!isOnline) {
            enqueuePending(receiver, msg);
            System.out.println("[ChatService] Queued private message for offline user: "
                    + receiver);
        } else {
            System.out.println("[ChatService] Private: " + sender + " → " + receiver);
        }
        return msg;
    }

    // ─── Retrieval Operations ─────────────────────────────────────────────────

    /**
     * Returns the full message history from the database.
     *
     * @return ordered {@link List} of all {@link Message} objects
     */
    public List<Message> getAllMessages() {
        return messageDAO.getAllMessages();
    }

    /**
     * Returns the conversation between two specific users.
     *
     * @param user1 one participant
     * @param user2 the other participant
     * @return ordered list of messages exchanged between them
     */
    public List<Message> getConversation(String user1, String user2) {
        return messageDAO.getConversation(user1, user2);
    }

    /**
     * Returns all broadcast messages.
     *
     * @return list of group messages
     */
    public List<Message> getBroadcastMessages() {
        return messageDAO.getBroadcastMessages();
    }

    /**
     * Returns the {@code n} most recent messages.
     *
     * @param n the desired count
     * @return list of up to {@code n} recent messages
     */
    public List<Message> getRecentMessages(int n) {
        return messageDAO.getRecentMessages(n);
    }

    /**
     * Loads chat history into a formatted list of strings (for admin view / logs).
     *
     * @return each message formatted by {@link Message#toDisplayString()}
     */
    public List<String> getChatHistoryAsStrings() {
        List<String> result = new ArrayList<>();
        for (Message m : messageDAO.getAllMessages()) {
            result.add(m.toDisplayString());
        }
        return result;
    }

    // ─── Pending Queue ────────────────────────────────────────────────────────

    /**
     * Drains all pending (offline-queued) messages for a user who just connected.
     *
     * @param username the user who came online
     * @return list of queued messages (may be empty)
     */
    public List<Message> drainPendingMessages(String username) {
        List<Message> delivered = new ArrayList<>();
        Queue<Message> queue = pendingMessages.get(username);

        if (queue != null) {
            synchronized (queue) {
                while (!queue.isEmpty()) {
                    delivered.add(queue.poll());
                }
            }
            pendingMessages.remove(username);
        }
        return delivered;
    }

    /**
     * Returns {@code true} if there are queued messages waiting for the user.
     *
     * @param username the user to check
     * @return whether there are pending messages
     */
    public boolean hasPendingMessages(String username) {
        Queue<Message> q = pendingMessages.get(username);
        return q != null && !q.isEmpty();
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Validates a message's text content against business rules.
     *
     * @param content the message text
     * @throws InvalidMessageException if content is null, blank, or too long
     */
    private void validateContent(String content) throws InvalidMessageException {
        if (content == null || content.trim().isEmpty()) {
            throw new InvalidMessageException("Message content cannot be empty.", content);
        }
        if (content.length() > InvalidMessageException.MAX_LENGTH) {
            throw new InvalidMessageException(
                    "Message exceeds maximum length of "
                            + InvalidMessageException.MAX_LENGTH + " characters.", content);
        }
    }

    /**
     * Adds a message to the pending queue for an offline recipient.
     *
     * @param recipient the offline user
     * @param msg       the message to queue
     */
    private void enqueuePending(String recipient, Message msg) {
        pendingMessages.computeIfAbsent(
                recipient, k -> new LinkedList<>()
        ).offer(msg);
    }
}
