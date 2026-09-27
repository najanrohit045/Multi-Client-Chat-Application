package model;

public record Message(
    long id,
    long senderId,
    String sender,
    long targetId,
    boolean group,
    String text,
    String sentAt,
    String status,
    Attachment attachment) {}
