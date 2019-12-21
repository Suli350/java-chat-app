package io.github.suli350.chat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/** Swing chat client with coloured messages and an online-users list. */
public class ChatWindow extends JFrame {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final JTextPane messages = new JTextPane();
    private final JTextField input = new JTextField();
    private final DefaultListModel<String> users = new DefaultListModel<>();
    private ChatConnection connection;
    private String nick = "";

    public ChatWindow() {
        super("Chat");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        messages.setEditable(false);
        messages.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        JList<String> userList = new JList<>(users);
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && userList.getSelectedValue() != null) {
                input.setText("/msg " + userList.getSelectedValue() + " ");
                input.requestFocusInWindow();
            }
        });
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(150, 0));
        userScroll.setBorder(BorderFactory.createTitledBorder("Online"));

        JButton send = new JButton("Send");
        send.addActionListener(e -> sendInput());
        input.addActionListener(e -> sendInput());
        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);

        add(new JScrollPane(messages), BorderLayout.CENTER);
        add(userScroll, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        setSize(760, 520);
        setLocationRelativeTo(null);

        // refresh the user list every few seconds
        new Timer(5000, e -> {
            if (connection != null && connection.isOpen()) {
                connection.send("/list");
            }
        }).start();
    }

    public void connect(String host, int port, String wantedNick) {
        try {
            connection = new ChatConnection(host, port,
                    line -> SwingUtilities.invokeLater(() -> onLine(line)),
                    () -> SwingUtilities.invokeLater(() -> {
                        append("-- Disconnected from server", Color.RED);
                        input.setEnabled(false);
                    }));
            setTitle("Chat — " + host + ":" + port);
            if (wantedNick != null && !wantedNick.isBlank()) {
                connection.send("/nick " + wantedNick.trim());
            }
            connection.send("/list");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not connect to " + host + ":" + port
                    + "\n" + e.getMessage(), "Connection failed", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void sendInput() {
        String text = input.getText();
        if (text.isBlank() || connection == null) {
            return;
        }
        connection.send(text);
        input.setText("");
        if (text.trim().equalsIgnoreCase("/quit")) {
            connection.close();
        }
    }

    private void onLine(String line) {
        if (line.startsWith("SYS Welcome! You are ")) {
            nick = line.substring(21, line.indexOf('.', 21));
        }
        if (line.startsWith("SYS Online (")) {
            users.clear();
            for (String u : line.substring(line.indexOf(':') + 1).split(",")) {
                if (!u.isBlank()) {
                    users.addElement(u.trim());
                }
            }
            return; // silent refresh
        }
        if (line.startsWith("SYS ") && line.contains(" is now known as ")) {
            String[] p = line.substring(4).split(" is now known as ");
            if (p[0].equalsIgnoreCase(nick)) {
                nick = p[1];
                setTitle("Chat — " + nick);
            }
            connection.send("/list");
        } else if (line.startsWith("SYS ") && (line.endsWith(" joined the chat") || line.endsWith(" left the chat"))) {
            connection.send("/list");
        }
        Color color;
        if (line.startsWith("SYS")) {
            color = Color.GRAY;
        } else if (line.startsWith("ERR")) {
            color = new Color(0xC62828);
        } else if (line.startsWith("PM")) {
            color = new Color(0x6A1B9A);
        } else if (line.startsWith("ME")) {
            color = new Color(0x00695C);
        } else {
            color = Color.BLACK;
        }
        append(ChatConnection.pretty(line, nick), color);
    }

    private void append(String text, Color color) {
        StyledDocument doc = messages.getStyledDocument();
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, color);
        try {
            doc.insertString(doc.getLength(), "[" + LocalTime.now().format(TIME) + "] " + text + "\n", style);
        } catch (BadLocationException ignored) {
            // cannot happen when appending at the end
        }
        messages.setCaretPosition(doc.getLength());
    }
}
