# Validation evidence

The upgraded application was compiled with JDK 17 and Maven. Validation performed during implementation:

| Check | Result |
| --- | --- |
| Service / JDBC tests | Passed: registration, duplicate names, password validation, BCrypt, login, password change, privacy, search, receipts, group membership, files |
| Migration tests | Passed: imports legacy users/history and hashes old passwords; orphaned history rolls back the transaction |
| Protocol tests | Passed: Unicode and binary round trip, oversized header rejection, truncated-frame rejection |
| Real TCP integration | Passed: two clients, authentication, private/group routing, offline messages, typing, receipts, binary sharing and disconnects |
| MySQL 8.0.46 integration | Passed separately against the actual schema and MySQL Connector/J: registration, hashes, two sockets, messages, search, receipts, summaries, groups and attachments |
| JavaFX smoke test | Passed separately under Xvfb: application launch, sign-in, conversation selection, sending, dashboard rendering and theme switch |
| Visual inspection | Dashboard capture reviewed; see dashboard.png |

The ordinary `mvn test` suite skips the two environment-dependent integration tests unless enabled. They were also run explicitly and passed. H2 is used only for isolated default tests; the MySQL-specific test uses the actual server database.

The final default suite includes an additional 85-message pagination test to verify complete history and offline-page retrieval. Final Maven run: **29 discovered, 27 passed, 0 failures, 0 errors, 2 environment-dependent tests skipped**. The MySQL and JavaFX tests each passed in their separate explicit runs.

Not exhaustively verified: every operating system, all dialog actions through mouse automation, large-scale load, network partitions longer than the heartbeat window, antivirus behavior, Internet deployment, or multi-server operation. There is no claim of TLS or end-to-end encryption.
