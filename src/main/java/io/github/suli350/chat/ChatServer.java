package io.github.suli350.chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Accepts clients and hands each one to a {@link ClientHandler} on its own thread. */
public class ChatServer {

    private final int requestedPort;
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "chat-client");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger guestCounter = new AtomicInteger();
    private final Consumer<String> log;
    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public ChatServer(int port, Consumer<String> log) {
        this.requestedPort = port;
        this.log = log;
    }

    /** Binds the port and accepts clients on a background thread. */
    public void start() throws IOException {
        serverSocket = new ServerSocket(requestedPort);
        running = true;
        Thread acceptor = new Thread(this::acceptLoop, "chat-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
        log.accept("Chat server listening on port " + getPort());
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                pool.execute(new ClientHandler(socket, this));
            } catch (SocketException e) {
                if (running) {
                    log.accept("Accept failed: " + e.getMessage());
                }
            } catch (IOException e) {
                log.accept("Accept failed: " + e.getMessage());
            }
        }
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void stop() {
        running = false;
        broadcast("SYS Server is shutting down", null);
        for (ClientHandler c : clients.values()) {
            c.close();
        }
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        pool.shutdownNow();
        log.accept("Server stopped");
    }

    // ------------------------------------------------------------------ used by handlers
    String nextGuestName() {
        String name;
        do {
            name = "guest" + guestCounter.incrementAndGet();
        } while (clients.containsKey(key(name)));
        return name;
    }

    /** Atomically claim a nickname. Returns false if someone else already has it. */
    boolean register(String nick, ClientHandler handler) {
        return clients.putIfAbsent(key(nick), handler) == null;
    }

    boolean rename(String oldNick, String newNick, ClientHandler handler) {
        if (key(oldNick).equals(key(newNick))) {
            clients.put(key(newNick), handler);
            return true;
        }
        if (clients.putIfAbsent(key(newNick), handler) != null) {
            return false;
        }
        clients.remove(key(oldNick), handler);
        return true;
    }

    void unregister(String nick, ClientHandler handler) {
        if (nick != null) {
            clients.remove(key(nick), handler);
        }
    }

    ClientHandler find(String nick) {
        return nick == null ? null : clients.get(key(nick));
    }

    void broadcast(String line, ClientHandler except) {
        for (ClientHandler c : clients.values()) {
            if (c != except) {
                c.send(line);
            }
        }
    }

    List<String> nicknames() {
        List<String> names = new ArrayList<>();
        for (ClientHandler c : clients.values()) {
            names.add(c.getNick());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    Collection<ClientHandler> handlers() {
        return clients.values();
    }

    void log(String message) {
        log.accept(message);
    }

    private static String key(String nick) {
        return nick.toLowerCase(Locale.ROOT);
    }
}
