# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Java client-server chat room (聊天室) with a Swing GUI. Supports public chat, private chat, and group chat. Zero external dependencies — pure Java standard library.

## Build & Run

Requires **JDK 17+**.

### Server

```bash
cd Server
javac -d bin src/*.java
java -cp bin Server
```

Listens on port 8888 by default. Modify the `PORT` constant in [Server.java](Server/src/Server.java) to change it.

### Client

```bash
cd Client
javac -d bin src/*.java
java -cp bin Client
```

Launch multiple client instances to test chat between different users.

### VS Code

Each module has `.vscode/settings.json` configured with `java.project.sourcePaths: ["src"]` and `java.project.outputPath: "bin"`. Use the Java Projects view to run `Server` or `Client` directly.

## Architecture

```
┌──────────┐    ┌──────────┐
│ Client A │    │ Client B │   ...  (Swing GUI, one per user)
└────┬─────┘    └────┬─────┘
     │ TCP Socket    │
     └───────┬───────┘
      ┌──────▼──────┐
      │   Server    │  (port 8888, one thread per client)
      └─────────────┘
```

### Server Module (`Server/src/`)

| File | Role |
|------|------|
| [Server.java](Server/src/Server.java) | Entry point. `ServerSocket.accept()` loop; spawns a `ClientHandler` thread per connection. |
| [ClientHandler.java](Server/src/ClientHandler.java) | Core logic. Message dispatch via `switch(msg.getType())`. Manages shared state: user accounts, online handlers, in-memory groups. |
| [UserInfo.java](Server/src/UserInfo.java) | `Serializable` value object: `passwordHash` + `transient Socket clientSocket`. |
| [Message.java](Server/src/Message.java) | Protocol DTO. Serialized over the wire. Must stay **identical** to the client copy. |

Shared state and locking:
- `users` (ConcurrentHashMap) — persisted to `users.dat` via Java serialization. Protected by `userLock`.
- `onlineHandlers` (ConcurrentHashMap) — maps username → `ClientHandler`. Protected by `onlineLock`.
- `groups` (ConcurrentHashMap of `groupName → Set<memberNames>`) — **memory only**, lost on server restart. Protected by `groupLock`.
- Three separate locks avoid deadlock. Locks are never nested across lock types.

Message types handled: `register`, `login`, `chat`, `private_chat`, `get_online_users`, `create_group`, `join_group`, `leave_group`, `list_groups`, `group_chat`, `logout`.

Password storage: SHA-256 hash → Base64, stored in `users.dat`.

### Client Module (`Client/src/`)

| File | Role |
|------|------|
| [Client.java](Client/src/Client.java) | Entry point. Launches `LoginFrame` on the EDT via `SwingUtilities.invokeLater`. |
| [LoginFrame.java](Client/src/LoginFrame.java) | Login/register window. Register uses a **short-lived** socket; login passes the socket + streams to `ChatFrame`. |
| [ChatFrame.java](Client/src/ChatFrame.java) | Main chat window. Right panel has `JTabbedPane` with "在线用户" and "群组" tabs. Spawns a dedicated receive thread (`startReceiving`). Dispatches incoming messages by type to update chat area, online list, or group list. |
| [Message.java](Client/src/Message.java) | **Must be identical** to `Server/src/Message.java` — same fields, same `serialVersionUID = 1L`. Java serialization will fail otherwise. |

Chat commands in the input field:
- `/private <user> <msg>` — private chat
- `/group <groupName> <msg>` — group chat

## Key Technical Notes

- **No automated tests** in this project.
- **`Message.java` duplication**: Both `Server/src/Message.java` and `Client/src/Message.java` must be kept in sync. Any field change must be applied to both copies.
- **Group data is ephemeral**: Stored only in server memory (`ConcurrentHashMap`). Server restart clears all groups.
- **User data is persistent**: Stored in `users.dat` (Java serialized `Map<String, UserInfo>`). The `users.dat` in the repo root and in `Server/` may be copies — the server writes relative to its working directory.
- **Thread safety**: The server uses `ConcurrentHashMap` + `synchronized` blocks on separate lock objects. The client uses `SwingUtilities.invokeLater` to update UI from the receive thread.
- **No build tools**: Plain `javac`/`java`. No Maven, Gradle, or external JARs.
- Detailed architecture walkthrough and sequence diagrams are in [README.md](README.md).
