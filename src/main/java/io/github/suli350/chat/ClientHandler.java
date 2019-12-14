package io.github.suli350.chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** One connected client: reads its lines and reacts to them. */
class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private PrintWriter writer;
    private volatile String nick;

    ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    String getNick() {
        return nick;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            String guest = server.nextGuestName();
            while (!server.register(guest, this)) {
                guest = server.nextGuestName();
            }
            nick = guest;
            send("SYS Welcome! You are " + nick + ". Type /nick NAME to change it, /help for commands.");
            server.broadcast("SYS " + nick + " joined the chat", this);
            server.log(nick + " connected from " + socket.getRemoteSocketAddress());

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (line.length() > Protocol.MAX_LINE) {
                    send("ERR Message too long (max " + Protocol.MAX_LINE + " characters)");
                    continue;
                }
                if (!handle(Protocol.parse(line))) {
                    break;
                }
            }
        } catch (IOException e) {
            // connection dropped; fall through to cleanup
        } finally {
            disconnect();
        }
    }

    /** Returns false when the client asked to quit. */
    private boolean handle(Protocol.Command cmd) {
        switch (cmd.name) {
            case "say":
                server.broadcast("MSG " + nick + " " + cmd.rest, null);
                return true;
            case "me":
                if (cmd.rest.isEmpty()) {
                    send("ERR Usage: /me ACTION");
                } else {
                    server.broadcast("ME " + nick + " " + cmd.rest, null);
                }
                return true;
            case "nick":
                changeNick(cmd.arg1);
                return true;
            case "msg":
                privateMessage(cmd.arg1, cmd.rest);
                return true;
            case "list":
                send("SYS Online (" + server.nicknames().size() + "): " + String.join(", ", server.nicknames()));
                return true;
            case "help":
                send("SYS Commands: /nick NAME · /msg NAME TEXT · /me ACTION · /list · /quit"
                        + " · start a message with // to send a literal '/'");
                return true;
            case "quit":
                send("SYS Bye!");
                return false;
            default:
                send("ERR Unknown command /" + cmd.name + " (try /help)");
                return true;
        }
    }

    private void changeNick(String requested) {
        if (!Protocol.isValidNick(requested)) {
            send("ERR Nicknames are 2-16 letters, digits, '_' or '-'");
            return;
        }
        String old = nick;
        if (!server.rename(old, requested, this)) {
            send("ERR The nickname " + requested + " is taken");
            return;
        }
        nick = requested;
        server.broadcast("SYS " + old + " is now known as " + nick, null);
    }

    private void privateMessage(String to, String text) {
        if (to == null || text.isBlank()) {
            send("ERR Usage: /msg NAME TEXT");
            return;
        }
        ClientHandler target = server.find(to);
        if (target == null) {
            send("ERR No user called " + to);
            return;
        }
        String line = "PM " + nick + " " + target.getNick() + " " + text;
        target.send(line);
        if (target != this) {
            send(line);
        }
    }

    synchronized void send(String line) {
        if (writer != null) {
            writer.println(line);
        }
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    private void disconnect() {
        if (nick != null) {
            server.unregister(nick, this);
            server.broadcast("SYS " + nick + " left the chat", this);
            server.log(nick + " disconnected");
        }
        close();
    }
}
