package io.github.suli350.chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Usage:
 *   java -jar chat-app.jar server [port]
 *   java -jar chat-app.jar client [host] [port] [nick]
 *   java -jar chat-app.jar gui    [host] [port] [nick]
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        String mode = args.length > 0 ? args[0] : "gui";
        switch (mode) {
            case "server": {
                int port = args.length > 1 ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;
                ChatServer server = new ChatServer(port, System.out::println);
                server.start();
                System.out.println("Press Enter to stop.");
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
                server.stop();
                break;
            }
            case "client":
                ConsoleClient.run(arg(args, 1, "localhost"), Integer.parseInt(arg(args, 2, "" + Protocol.DEFAULT_PORT)),
                        args.length > 3 ? args[3] : null);
                break;
            case "gui": {
                String host = arg(args, 1, "localhost");
                int port = Integer.parseInt(arg(args, 2, "" + Protocol.DEFAULT_PORT));
                SwingUtilities.invokeLater(() -> {
                    String nick = args.length > 3 ? args[3]
                            : JOptionPane.showInputDialog(null, "Nickname (2-16 letters/digits):", "Join chat",
                                    JOptionPane.QUESTION_MESSAGE);
                    ChatWindow window = new ChatWindow();
                    window.setVisible(true);
                    window.connect(host, port, nick);
                });
                break;
            }
            default:
                System.err.println("Usage: server [port] | client [host] [port] [nick] | gui [host] [port] [nick]");
                System.exit(2);
        }
    }

    private static String arg(String[] args, int i, String fallback) {
        return args.length > i ? args[i] : fallback;
    }
}
