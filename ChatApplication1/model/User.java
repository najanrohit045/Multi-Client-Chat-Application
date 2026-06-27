package model;

import java.io.Serializable;

/**
 * User - Base entity model representing a registered chat user.
 *
 * <p>Demonstrates <b>Encapsulation</b> (private fields + getters/setters),
 * <b>OOP</b>, and acts as the parent class for {@link AdminUser} to
 * illustrate <b>Inheritance</b> and <b>Polymorphism</b>.</p>
 *
 * <p>Implements {@link Serializable} so User objects can be passed across
 * sockets if needed in future extensions.</p>
 *
 * <p><b>Concepts Used:</b> OOP, Encapsulation, Inheritance base class,
 * Serializable, JavaDoc</p>
 *
 * @author  ChatApplication
 * @version 1.0
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    // ─── Fields ───────────────────────────────────────────────────────────────
    /** Auto-incremented primary key from the database. */
    private int    id;

    /** Unique login handle chosen by the user. */
    private String username;

    /** Hashed (or plain for demo) password. */
    private String password;

    /**
     * Role of the user.  Allowed values: {@code "USER"} or {@code "ADMIN"}.
     */
    private String role;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /** Default no-arg constructor required by some frameworks and JDBC mapping. */
    public User() { }

    /**
     * Full constructor used when building a User from a JDBC {@link java.sql.ResultSet}.
     *
     * @param id       database primary key
     * @param username unique login handle
     * @param password user's password (store hashed in production)
     * @param role     {@code "USER"} or {@code "ADMIN"}
     */
    public User(int id, String username, String password, String role) {
        this.id       = id;
        this.username = username;
        this.password = password;
        this.role     = role;
    }

    /**
     * Constructor for new registrations where the DB id is not yet known.
     *
     * @param username unique login handle
     * @param password user's password
     * @param role     {@code "USER"} or {@code "ADMIN"}
     */
    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role     = role;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    /** @return database primary key */
    public int getId()                   { return id; }

    /** @param id database primary key */
    public void setId(int id)            { this.id = id; }

    /** @return unique login handle */
    public String getUsername()          { return username; }

    /** @param username unique login handle */
    public void setUsername(String u)    { this.username = u; }

    /** @return user's password */
    public String getPassword()          { return password; }

    /** @param password user's password */
    public void setPassword(String p)    { this.password = p; }

    /** @return role string, either {@code "USER"} or {@code "ADMIN"} */
    public String getRole()              { return role; }

    /** @param role role string */
    public void setRole(String role)     { this.role = role; }

    // ─── Utility ──────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if this user holds the ADMIN role.
     * Overridden in {@link AdminUser} for polymorphic behaviour.
     *
     * @return whether the user is an administrator
     */
    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    /**
     * Human-readable representation used in logs and debug output.
     *
     * @return string summary of this User
     */
    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', role='" + role + "'}";
    }
}
