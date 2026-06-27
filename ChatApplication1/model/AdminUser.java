package model;

import java.util.List;
import java.util.Map;

/**
 * AdminUser - Specialised {@link User} with elevated privileges.
 *
 * <p>Demonstrates <b>Inheritance</b> (extends {@link User}),
 * <b>Polymorphism</b> (overrides {@link #isAdmin()} and {@link #toString()}),
 * and <b>Single Responsibility</b> (admin-specific behaviour isolated here).</p>
 *
 * <p>An AdminUser can view online users, read the full chat log, and force-
 * remove other users from the server.</p>
 *
 * <p><b>Concepts Used:</b> Inheritance, Polymorphism, OOP, Encapsulation</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class AdminUser extends User {

    private static final long serialVersionUID = 2L;

    /** Timestamp of when the admin last logged in (informational). */
    private String lastLoginTime;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /** Default no-arg constructor. */
    public AdminUser() {
        super();
        setRole("ADMIN");
    }

    /**
     * Full constructor used when hydrating an admin from the database.
     *
     * @param id            database primary key
     * @param username      unique login handle
     * @param password      admin password
     * @param lastLoginTime ISO-8601 timestamp of the last login
     */
    public AdminUser(int id, String username, String password, String lastLoginTime) {
        super(id, username, password, "ADMIN");
        this.lastLoginTime = lastLoginTime;
    }

    // ─── Overrides ────────────────────────────────────────────────────────────

    /**
     * Polymorphic override — always returns {@code true} for AdminUser.
     *
     * @return {@code true}
     */
    @Override
    public boolean isAdmin() {
        return true;
    }

    /**
     * Displays all currently connected users.
     *
     * <p>This method accepts the live {@code onlineUsers} map held by the server
     * so it doesn't need to reach into the network layer directly.</p>
     *
     * @param onlineUsers snapshot of the server's online-user map
     *                    (username → socket/handler reference string)
     */
    public void viewOnlineUsers(Map<String, Object> onlineUsers) {
        System.out.println("\n===== Online Users (" + onlineUsers.size() + ") =====");
        if (onlineUsers.isEmpty()) {
            System.out.println("  (no users currently online)");
        } else {
            onlineUsers.keySet().forEach(u -> System.out.println("  • " + u));
        }
        System.out.println("================================\n");
    }

    /**
     * Displays a list of chat log lines read from a file or database.
     *
     * @param logs lines of the chat log
     */
    public void viewChatLogs(List<String> logs) {
        System.out.println("\n===== Chat Logs (" + logs.size() + " entries) =====");
        logs.forEach(line -> System.out.println("  " + line));
        System.out.println("===========================================\n");
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    /** @return last login timestamp */
    public String getLastLoginTime()              { return lastLoginTime; }

    /** @param lastLoginTime ISO-8601 login timestamp */
    public void setLastLoginTime(String t)        { this.lastLoginTime = t; }

    // ─── Utility ──────────────────────────────────────────────────────────────

    /**
     * Human-readable summary that includes the admin-specific last-login field.
     *
     * @return string summary
     */
    @Override
    public String toString() {
        return "AdminUser{id=" + getId() + ", username='" + getUsername()
                + "', lastLogin='" + lastLoginTime + "'}";
    }
}
