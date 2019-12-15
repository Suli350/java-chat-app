package io.github.suli350.chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Terminal client: prints incoming messages, sends what you type. */
public final class ConsoleClient {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static volatile String nick = "";

    private ConsoleClient() {
    }

    public static void run(String host, int port, String wantedNick) throws IOException {
        ChatConnection conn = new ChatConnection(host, port, line -> {
            if (line.startsWith("SYS Welcome! You are ")) {
                nick = line.substring(21, line.indexOf('.', 21));
            } else if (line.startsWith("SYS ") && line.contains(" is now known as ")) {
                String[] p = line.substring(4).split(" is now known as ");
                if (p[0].equalsIgnoreCase(nick)) {
                    nick = p[1];
                }
            }
            System.out.println("[" + LocalTime.now().format(TIME) + "] " + ChatConnection.pretty(line, nick));
        }, () -> {
            System.out.println("Disconnected.");
            System.exit(0);
        });
        if (wantedNick != null) {
            conn.send("/nick " + wantedNick);
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            conn.send(line);
            if (line.trim().equalsIgnoreCase("/quit")) {
                break;
            }
        }
        try {
            Thread.sleep(200); // let the "Bye" arrive
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        conn.close();
    }
}
