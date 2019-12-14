package io.github.suli350.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Starts a real server on a free port and talks to it over sockets. */
class ChatServerTest {

    private ChatServer server;

    /** Minimal test client that reads with a timeout. */
    private static final class TestClient implements AutoCloseable {
        final Socket socket;
        final BufferedReader in;
        final PrintWriter out;

        TestClient(int port) throws IOException {
            socket = new Socket("localhost", port);
            socket.setSoTimeout(3000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        /** Skip lines until one starts with the prefix (fails on timeout). */
        String expect(String prefix) throws IOException {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith(prefix)) {
                    return line;
                }
            }
            throw new IOException("Connection closed while waiting for " + prefix);
        }

        void send(String line) {
            out.println(line);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @BeforeEach
    void start() throws IOException {
        server = new ChatServer(0, msg -> { });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private TestClient join(String nick) throws IOException {
        TestClient c = new TestClient(server.getPort());
        c.expect("SYS Welcome! You are guest");
        c.send("/nick " + nick);
        c.expect("SYS guest");   // "... is now known as nick"
        return c;
    }

    @Test
    void publicMessagesReachEveryone() throws IOException {
        try (TestClient ann = join("ann"); TestClient bob = join("bob")) {
            ann.send("hello everyone");
            assertEquals("MSG ann hello everyone", bob.expect("MSG"));
            assertEquals("MSG ann hello everyone", ann.expect("MSG"));
        }
    }

    @Test
    void privateMessagesOnlyReachTheTarget() throws IOException {
        try (TestClient ann = join("ann"); TestClient bob = join("bob"); TestClient cat = join("cat")) {
            ann.send("/msg BOB secret plan");
            assertEquals("PM ann bob secret plan", bob.expect("PM"));
            assertEquals("PM ann bob secret plan", ann.expect("PM"));
            cat.send("/list");
            String list = cat.expect("SYS Online");
            assertEquals("SYS Online (3): ann, bob, cat", list);  // cat never saw a PM line
        }
    }

    @Test
    void nicknamesMustBeUniqueAndValid() throws IOException {
        try (TestClient ann = join("ann"); TestClient other = new TestClient(server.getPort())) {
            other.expect("SYS Welcome!");
            other.send("/nick ANN");
            assertTrue(other.expect("ERR").contains("taken"));
            other.send("/nick x");
            assertTrue(other.expect("ERR").contains("2-16"));
        }
    }

    @Test
    void joinAndLeaveAreAnnounced() throws IOException {
        try (TestClient ann = join("ann")) {
            TestClient bob = join("bob");
            assertTrue(ann.expect("SYS guest").endsWith("joined the chat"));
            bob.send("/quit");
            assertEquals("SYS Bye!", bob.expect("SYS Bye"));
            assertEquals("SYS bob left the chat", ann.expect("SYS bob left"));
            bob.close();
        }
    }

    @Test
    void unknownCommandsAndLongLinesAreRejected() throws IOException {
        try (TestClient ann = join("ann")) {
            ann.send("/dance");
            assertTrue(ann.expect("ERR").contains("Unknown command /dance"));
            ann.send("x".repeat(Protocol.MAX_LINE + 1));
            assertTrue(ann.expect("ERR").contains("too long"));
        }
    }
}
