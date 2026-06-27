-- =============================================================================
-- Multi-Threaded Chat Application — MySQL Schema
-- =============================================================================
-- Run this file once before starting the application:
--   mysql -u root -p < schema.sql
-- =============================================================================

-- ── Database ─────────────────────────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS chat_app
    CHARACTER SET utf8mb4
    COLLATE      utf8mb4_unicode_ci;

USE chat_app;

-- ── users ────────────────────────────────────────────────────────────────────
-- Stores every registered account.
-- role  VARCHAR(10)  — either 'USER' or 'ADMIN'
-- password stores plain text in this demo; hash with BCrypt in production.

CREATE TABLE IF NOT EXISTS users (
    id         INT          NOT NULL AUTO_INCREMENT,
    username   VARCHAR(50)  NOT NULL,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(10)  NOT NULL DEFAULT 'USER',

    PRIMARY KEY (id),
    UNIQUE KEY uq_username (username),

    CONSTRAINT chk_role CHECK (role IN ('USER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── messages ─────────────────────────────────────────────────────────────────
-- Records every chat message (broadcast and private).
-- receiver = 'ALL'  → broadcast / group message
-- receiver = <username> → private message

CREATE TABLE IF NOT EXISTS messages (
    id         INT           NOT NULL AUTO_INCREMENT,
    sender     VARCHAR(50)   NOT NULL,
    receiver   VARCHAR(50)   NOT NULL,
    message    TEXT          NOT NULL,
    timestamp  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_sender   (sender),
    INDEX idx_receiver (receiver),
    INDEX idx_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Seed Data ─────────────────────────────────────────────────────────────────
-- Optional: uncomment to pre-populate demo accounts.
-- Passwords are plain text for demo purposes only.

-- INSERT IGNORE INTO users (username, password, role) VALUES
--     ('alice', 'pass1234', 'USER'),
--     ('bob',   'pass1234', 'USER'),
--     ('admin', 'admin123', 'ADMIN');

-- ── Useful Queries ────────────────────────────────────────────────────────────
-- List all users:
--   SELECT * FROM users;
--
-- Full chat history (newest first):
--   SELECT * FROM messages ORDER BY timestamp DESC;
--
-- Conversation between alice and bob:
--   SELECT * FROM messages
--   WHERE (sender='alice' AND receiver='bob')
--      OR (sender='bob'   AND receiver='alice')
--   ORDER BY timestamp;
--
-- All broadcasts:
--   SELECT * FROM messages WHERE receiver='ALL' ORDER BY timestamp;
