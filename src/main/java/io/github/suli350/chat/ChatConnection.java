package io.github.suli350.chat;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Client side of a connection. Incoming lines go to a listener on a background thread. */
public class ChatConnection implements Closeable {

    private final Socket socket;
    private final PrintWriter writer;

    public ChatConnection(String host, int port, Consumer<String> onLine, Runnable onClosed) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    onLine.accept(line);
                }
            } catch (IOException ignored) {
                // socket closed
            } finally {
                onClosed.run();
            }
        }, "chat-reader");
        t.setDaemon(true);
        t.start();
    }

    public void send(String line) {
        writer.println(line);
    }

    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }

    /** Turn a protocol line into something readable. */
    public static String pretty(String line, String self) {
        String[] p = line.split(" ", 2);
        String body = p.length > 1 ? p[1] : "";
        switch (p[0]) {
            case "MSG": {
                String[] m = body.split(" ", 2);
                return m[0] + ": " + (m.length > 1 ? m[1] : "");
            }
            case "ME": {
                return "* " + body;
            }
            case "PM": {
                String[] m = body.split(" ", 3);
                if (m.length < 3) {
                    return line;
                }
                return m[0].equalsIgnoreCase(self)
                        ? "[to " + m[1] + "] " + m[2]
                        : "[from " + m[0] + "] " + m[2];
            }
            case "SYS": return "-- " + body;
            case "ERR": return "!! " + body;
            default: return line;
        }
    }
}
