package model;

/** Public profile deliberately excludes password hashes. */
public record User(long id, String username, String role, boolean online, String lastSeen) {}
