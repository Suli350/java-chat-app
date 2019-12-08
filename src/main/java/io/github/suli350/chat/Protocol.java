package io.github.suli350.chat;

import java.util.regex.Pattern;

/**
 * The line-based text protocol.
 *
 * Client to server: a plain line is a public message; lines starting with '/'
 * are commands: /nick NAME, /msg NAME TEXT, /me ACTION, /list, /help, /quit.
 *
 * Server to client: every line starts with a tag so clients can style it:
 *   SYS  text              system notice
 *   MSG  nick text         public message
 *   ME   nick text         action (/me)
 *   PM   from to text      private message (sent to both sides)
 *   ERR  text              something the client did was rejected
 */
public final class Protocol {

    public static final int DEFAULT_PORT = 5050;
    public static final int MAX_LINE = 500;
    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_-]{2,16}");

    private Protocol() {
    }

    public static boolean isValidNick(String nick) {
        return nick != null && NICK.matcher(nick).matches();
    }

    /** Parsed client input. */
    public static final class Command {
        public final String name;   // "say" for plain messages
        public final String arg1;
        public final String rest;

        Command(String name, String arg1, String rest) {
            this.name = name;
            this.arg1 = arg1;
            this.rest = rest;
        }
    }

    public static Command parse(String line) {
        String text = line.strip();
        if (!text.startsWith("/") || text.startsWith("//")) {
            // "//" escapes a message that should start with a slash
            return new Command("say", null, text.startsWith("//") ? text.substring(1) : text);
        }
        String[] parts = text.substring(1).split("\\s+", 3);
        String name = parts[0].toLowerCase();
        switch (name) {
            case "me":
                return new Command(name, null, text.length() > 3 ? text.substring(3).strip() : "");
            case "msg":
            case "nick":
                return new Command(name, parts.length > 1 ? parts[1] : null, parts.length > 2 ? parts[2] : "");
            default:
                return new Command(name, parts.length > 1 ? parts[1] : null,
                        parts.length > 2 ? parts[2] : "");
        }
    }
}
