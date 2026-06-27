# Multi-Threaded Chat Application

> A production-quality, resume-worthy Core Java chat system built with
> **Socket Programming**, **Multithreading**, **JDBC/MySQL**, and full
> **OOP/SOLID** design principles.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Technology Stack](#technology-stack)
3. [Architecture](#architecture)
4. [Project Structure](#project-structure)
5. [Database Schema](#database-schema)
6. [Setup — Ubuntu](#setup--ubuntu)
7. [Setup — Windows](#setup--windows)
8. [Running in VS Code](#running-in-vs-code)
9. [Running in IntelliJ IDEA](#running-in-intellij-idea)
10. [Chat Protocol Reference](#chat-protocol-reference)
11. [Core Java Concepts Used](#core-java-concepts-used)
12. [Interview Q&A](#interview-qa)

---

## Project Overview

A fully functional, multi-client chat server built entirely with **Core Java** (no Spring,
no Netty). Clients connect over raw TCP sockets; the server spawns one thread per client
and routes messages through a layered service → DAO → MySQL architecture.

**Features:**
- Multi-client simultaneous connections (each on its own thread)
- User registration and JDBC-authenticated login
- Group (broadcast) messages
- Private (direct) messages with offline queuing
- Full chat history stored in MySQL **and** a flat-file log
- Admin: view online users, forcibly remove users, view logs
- Custom exceptions, synchronised data structures, graceful shutdown

---

## Technology Stack

| Layer        | Technology                        |
|--------------|-----------------------------------|
| Language     | Java 8+                           |
| Database     | MySQL 8.x                         |
| Connectivity | JDBC (mysql-connector-java)       |
| Networking   | java.net (ServerSocket / Socket)  |
| Concurrency  | java.lang.Thread, synchronized    |
| Collections  | HashMap, ConcurrentHashMap, Queue |
| File I/O     | BufferedReader / BufferedWriter   |
| Build        | Manual javac / IntelliJ / VS Code |

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                 Presentation Layer               │
│         ChatClient (console UI / CLI)            │
└─────────────────────────┬───────────────────────┘
                          │  TCP Socket (port 9090)
┌─────────────────────────▼───────────────────────┐
│               Network Layer                      │
│   ChatServer (accept loop)                       │
│   ClientHandler (Thread per client)              │
└─────────────────────────┬───────────────────────┘
                          │
┌─────────────────────────▼───────────────────────┐
│               Service Layer                      │
│   UserService    ChatService                     │
└──────────┬───────────────────────┬──────────────┘
           │                       │
┌──────────▼──────────┐  ┌────────▼──────────────┐
│     DAO Layer        │  │    Utility Layer       │
│  UserDAO MessageDAO  │  │    FileManager         │
└──────────┬──────────┘  └────────────────────────┘
           │
┌──────────▼──────────────────────────────────────┐
│           Database Layer  (MySQL)                │
│   DBConnection (Singleton JDBC pool)             │
│   users table    messages table                  │
└─────────────────────────────────────────────────┘
```

---

## Project Structure

```
ChatApplication/
│
├── Main.java                    ← entry point (server / client / setup menu)
│
├── database/
│   └── DBConnection.java        ← Singleton JDBC connection
│
├── model/
│   ├── User.java                ← base user entity (encapsulation)
│   ├── AdminUser.java           ← admin entity (inheritance + polymorphism)
│   └── Message.java             ← message entity
│
├── exception/
│   ├── UserNotFoundException.java
│   └── InvalidMessageException.java
│
├── dao/
│   ├── UserDAO.java             ← CRUD for users table
│   └── MessageDAO.java          ← CRUD + batch for messages table
│
├── service/
│   ├── UserService.java         ← registration, auth, admin ops
│   └── ChatService.java         ← send/receive, offline queue
│
├── network/
│   ├── ChatServer.java          ← ServerSocket accept loop
│   ├── ClientHandler.java       ← Thread per client (protocol handler)
│   └── ChatClient.java          ← console TCP client
│
├── util/
│   └── FileManager.java         ← thread-safe file logging
│
├── schema.sql                   ← MySQL DDL
└── README.md
```

---

## Database Schema

```sql
-- users
CREATE TABLE users (
    id       INT         AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role     VARCHAR(10) NOT NULL DEFAULT 'USER'
);

-- messages
CREATE TABLE messages (
    id        INT      AUTO_INCREMENT PRIMARY KEY,
    sender    VARCHAR(50) NOT NULL,
    receiver  VARCHAR(50) NOT NULL,   -- 'ALL' for broadcast
    message   TEXT        NOT NULL,
    timestamp DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### ER Diagram (text)
```
users                        messages
─────────────────            ──────────────────────────
id          (PK)             id          (PK)
username    UNIQUE           sender      → users.username
password                     receiver    → users.username | 'ALL'
role                         message
                             timestamp
```

---

## Setup — Ubuntu

### 1. Install Java 11+
```bash
sudo apt update
sudo apt install openjdk-11-jdk -y
java -version
```

### 2. Install MySQL
```bash
sudo apt install mysql-server -y
sudo mysql_secure_installation
```

### 3. Download MySQL JDBC Driver
```bash
# Download mysql-connector-j-8.x.x.jar from:
# https://dev.mysql.com/downloads/connector/j/
# Place the JAR in ChatApplication/lib/
mkdir -p ChatApplication/lib
cp mysql-connector-j-*.jar ChatApplication/lib/
```

### 4. Create Database
```bash
mysql -u root -p < schema.sql
```

### 5. Configure DB credentials
Edit `database/DBConnection.java`:
```java
private static final String PASSWORD = "your_mysql_password";
```

### 6. Compile
```bash
cd ChatApplication
javac -cp .:lib/mysql-connector-j-*.jar \
    database/*.java model/*.java exception/*.java \
    dao/*.java service/*.java util/*.java \
    network/*.java Main.java
```

### 7. Run Server (terminal 1)
```bash
java -cp .:lib/mysql-connector-j-*.jar Main server
```

### 8. Run Client (terminal 2, 3, …)
```bash
java -cp .:lib/mysql-connector-j-*.jar Main client
```

### 9. Seed demo users (optional)
```bash
java -cp .:lib/mysql-connector-j-*.jar Main setup
```

---

## Setup — Windows

### 1. Install Java 11+
Download from https://adoptium.net/ and install.  
Add `JAVA_HOME` to System Environment Variables.

### 2. Install MySQL
Download MySQL Installer from https://dev.mysql.com/downloads/installer/  
Run and choose "Developer Default".

### 3. MySQL JDBC Driver
Download `mysql-connector-j-*.jar` from MySQL downloads.  
Create `ChatApplication\lib\` and copy the JAR there.

### 4. Create Database
```cmd
mysql -u root -p < schema.sql
```

### 5. Compile (PowerShell / CMD)
```cmd
cd ChatApplication
javac -cp ".;lib\mysql-connector-j-*.jar" ^
    database\*.java model\*.java exception\*.java ^
    dao\*.java service\*.java util\*.java ^
    network\*.java Main.java
```

### 6. Run Server
```cmd
java -cp ".;lib\mysql-connector-j-*.jar" Main server
```

### 7. Run Client (new CMD window)
```cmd
java -cp ".;lib\mysql-connector-j-*.jar" Main client
```

---

## Running in VS Code

1. Install extensions: **Extension Pack for Java** (Microsoft)
2. Open the `ChatApplication` folder
3. Add the MySQL JAR to `.vscode/settings.json`:
   ```json
   {
     "java.project.referencedLibraries": ["lib/**/*.jar"]
   }
   ```
4. Open `Main.java` → click **Run** (▷) above `main()`
5. Choose option 1 (Server) in the terminal, then open a second terminal and run the client

---

## Running in IntelliJ IDEA

1. **File → Open** → select the `ChatApplication` folder
2. **File → Project Structure → Libraries → +** → add the MySQL connector JAR
3. Create two Run Configurations:
   - `Server`: Main class = `Main`, Program arguments = `server`
   - `Client`: Main class = `Main`, Program arguments = `client`
4. Run `Server` first, then run `Client` (multiple instances supported)

---

## Chat Protocol Reference

| Command                    | Description                      |
|----------------------------|----------------------------------|
| `LOGIN:<user>:<pass>`      | Authenticate                     |
| `MSG:ALL:<text>`           | Broadcast to all users           |
| `MSG:<username>:<text>`    | Private message                  |
| `ONLINE`                   | List connected users             |
| `HISTORY`                  | Last 20 messages                 |
| `ADMIN:REMOVE:<username>`  | Admin: remove a user             |
| `ADMIN:LOGS`               | Admin: view full log file        |
| `QUIT`                     | Disconnect                       |

---

## Core Java Concepts Used

| Concept              | Where Used                                         |
|----------------------|----------------------------------------------------|
| OOP                  | All model, service, DAO classes                    |
| Encapsulation        | Private fields + getters/setters in User, Message  |
| Inheritance          | AdminUser extends User                             |
| Polymorphism         | isAdmin(), toString() overrides                    |
| Interfaces           | Serializable on User and Message                   |
| Multithreading       | ClientHandler extends Thread; reader thread        |
| Synchronization      | synchronized blocks in ChatServer, FileManager     |
| Sockets              | ServerSocket, Socket, BufferedReader, PrintWriter  |
| JDBC                 | DBConnection, UserDAO, MessageDAO                  |
| PreparedStatement    | All SQL queries (prevents SQL injection)           |
| Transactions         | saveMessage() with commit/rollback                 |
| Collections          | ConcurrentHashMap (online users), Queue (pending)  |
| File Handling        | FileManager (chat_history.txt, audit_log.txt)      |
| Custom Exceptions    | UserNotFoundException, InvalidMessageException     |
| Exception Handling   | try-catch-finally throughout                       |
| Design Patterns      | Singleton (DBConnection), DAO, Service Layer       |
| Shutdown Hook        | Runtime.getRuntime().addShutdownHook(...)          |
| Volatile             | running flags in ChatServer and ChatClient         |

---

## Interview Q&A

**Q1: Why does ClientHandler extend Thread instead of implementing Runnable?**  
A: For this project, `extends Thread` is simpler since we need to call `handler.disconnect()` from other threads — having the Thread reference is convenient. In production, implementing `Runnable` and using an `ExecutorService` thread pool is preferred for resource management and testability.

**Q2: How is thread safety achieved in the online-users map?**  
A: The map is a `ConcurrentHashMap` for thread-safe reads. Write operations (add/remove user) are additionally wrapped in `synchronized (onlineUsers)` blocks in ClientHandler to make the check-then-act sequence atomic.

**Q3: What is the Singleton pattern in DBConnection and why is volatile needed?**  
A: The Singleton ensures one shared `Connection` instance. `volatile` forces the JVM to read/write `connection` from main memory instead of a CPU cache, so double-checked locking works correctly across threads.

**Q4: Why use PreparedStatement instead of Statement?**  
A: `PreparedStatement` pre-compiles the SQL and safely escapes parameters, preventing SQL injection attacks. It also improves performance when the same query runs multiple times.

**Q5: How does the offline message queue work?**  
A: `ChatService` holds a `Map<String, Queue<Message>>`. When a private message targets an offline user, it's saved to MySQL (for persistence) and also enqueued in memory. When that user next connects, `ClientHandler` calls `chatService.drainPendingMessages(username)` to deliver them.

**Q6: Explain the layered architecture.**  
A: Network layer (ChatServer, ClientHandler) handles transport; Service layer (UserService, ChatService) enforces business rules; DAO layer (UserDAO, MessageDAO) isolates SQL; Model layer defines entities; Utility layer provides cross-cutting concerns (FileManager). This separation follows SOLID's Single Responsibility principle and makes each layer independently testable.

**Q7: How would you scale this to 10,000 concurrent users?**  
A: Replace `extends Thread` with a `CachedThreadPool` or `NioEventLoopGroup` (Netty), switch to non-blocking NIO channels, use a connection pool (HikariCP) instead of a singleton connection, and add a message broker (Redis/Kafka) for broadcast fanout.
