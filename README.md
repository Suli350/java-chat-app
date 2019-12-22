# Multi-user Chat (Java sockets)

A complete chat system built only on the Java standard library: a multi-threaded
TCP server, a terminal client and a Swing desktop client, all talking a simple
line-based protocol.

## Features

- Any number of clients, one thread each, from a cached thread pool
- Nicknames (unique, case-insensitive, validated) with guest names on join
- Public messages, `/msg` private messages, `/me` actions, `/list`, `/help`, `/quit`
- Join / leave / rename announcements
- Swing client with coloured messages, a live online list (click a name to PM) and timestamps
- Terminal client for quick testing or SSH sessions
- Integration tests that start a real server on a free port

## Run it

```bash
mvn package
java -jar target/chat-app-1.0.0.jar server 5050                 # terminal 1
java -jar target/chat-app-1.0.0.jar gui localhost 5050 ann      # terminal 2 (window)
java -jar target/chat-app-1.0.0.jar client localhost 5050 bob   # terminal 3 (console)
```

Other computers on your network can join with your machine's IP address instead of `localhost`.

## Protocol

Client → server: a plain line is a public message; commands start with `/`.

Server → client: every line starts with a tag.

| Tag | Example | Meaning |
|---|---|---|
| `SYS` | `SYS ann joined the chat` | system notice |
| `MSG` | `MSG ann hello` | public message |
| `ME`  | `ME ann waves` | action |
| `PM`  | `PM ann bob psst` | private message (sender, recipient, text) |
| `ERR` | `ERR The nickname bob is taken` | rejected request |

## Design

```
ChatServer ──accept──> ClientHandler (1 per client, own thread)
    │  ConcurrentHashMap<nick, handler>, broadcast(), atomic nick claims
    │
ChatConnection (client socket + reader thread) ──> ConsoleClient / ChatWindow
Protocol (parsing + validation, shared)
```

Nickname changes use `putIfAbsent`, so two clients can never claim the same name,
even at the same moment.

## Ideas for extending it

- Rooms / channels (`/join #java`)
- Message history for people who join late
- TLS with `SSLServerSocket`
