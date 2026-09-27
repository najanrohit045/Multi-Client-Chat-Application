# Upgrade from version 1

The original `ChatApplication1` source at commit `f144e3e854ca0ea473c2403d77bf092e9423a75f` was inspected before restructuring.

| Original | Upgrade |
| --- | --- |
| Main console menu | `app.ChatApplication` JavaFX entry and `network.ChatServer` server entry |
| DBConnection | Environment-configured per-operation JDBC connections |
| UserDAO / UserService | BCrypt registration, login, password change, presence |
| MessageDAO / ChatService | ID-based foreign keys, scoped history, durable offline messages, receipts |
| ChatClient / ClientHandler / ChatServer | Framed TCP protocol, request correlation, bounded executors and queues |
| User / AdminUser / Message | Public profile and message records; server-side role checks |
| FileManager | Audit metadata without private messages, plus authorized binary attachments |
| ALL broadcast | Community group automatically includes every account |
| Admin kick / logs | Admin account screen: kick and metadata audit viewer |
| Global HISTORY | Removed privacy leak; only own conversations and joined groups are readable |

The old insecure protocol is intentionally incompatible. Start only v2 clients with the v2 server. There are no default credentials. Public registration always creates USER accounts.

## Existing data

Use the new **chat_app_v2** database. The schema does not drop or overwrite the original **chat_app**. A one-time migration is provided for the exact original schema on the same MySQL instance:

1. Stop the old and new chat servers. Back up both databases securely.
2. Create the v2 schema with `mysql -u root -p < schema.sql`.
3. Set server DB variables using an account with access to both schemas. The target users table must be empty.
4. Run:

```bash
mvn compile exec:java -Dexec.mainClass=app.MigrateLegacy -Dexec.args=--import-and-hash-legacy
```

The import preserves roles, message order/timestamps and broadcasts, normalizes valid usernames, hashes passwords and replaces the retired schema's plaintext password values with hashes in the same transaction. It aborts on invalid/colliding names or missing message participants so no history is silently discarded. Existing short passwords can sign in after migration but new registrations/password changes enforce 8 characters. Backups and old Git history may still contain old credentials; rotate the previously committed database password. Do not run the retired application after migrating.

The old `.class`, bundled driver JAR and runtime logs were removed. Original source remains recoverable in Git history. The new structure needs no IDE-specific configuration.
