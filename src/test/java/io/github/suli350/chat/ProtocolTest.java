package io.github.suli350.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProtocolTest {

    @Test
    void plainTextIsASay() {
        Protocol.Command c = Protocol.parse("  hello there ");
        assertEquals("say", c.name);
        assertEquals("hello there", c.rest);
    }

    @Test
    void doubleSlashEscapes() {
        assertEquals("/shrug", Protocol.parse("//shrug").rest);
    }

    @Test
    void commandsAreParsed() {
        Protocol.Command msg = Protocol.parse("/MSG bob see you at 5");
        assertEquals("msg", msg.name);
        assertEquals("bob", msg.arg1);
        assertEquals("see you at 5", msg.rest);
        assertEquals("waves hello", Protocol.parse("/me waves hello").rest);
        assertNull(Protocol.parse("/nick").arg1);
        assertEquals("list", Protocol.parse("/list").name);
    }

    @Test
    void nicknameRules() {
        assertTrue(Protocol.isValidNick("ann_99"));
        assertTrue(Protocol.isValidNick("Jo"));
        assertFalse(Protocol.isValidNick("a"));
        assertFalse(Protocol.isValidNick("has space"));
        assertFalse(Protocol.isValidNick("waytoolongnickname"));
        assertFalse(Protocol.isValidNick(null));
    }

    @Test
    void prettyPrinting() {
        assertEquals("ann: hi all", ChatConnection.pretty("MSG ann hi all", "bob"));
        assertEquals("[from ann] psst", ChatConnection.pretty("PM ann bob psst", "bob"));
        assertEquals("[to bob] psst", ChatConnection.pretty("PM ann bob psst", "ann"));
        assertEquals("* ann waves", ChatConnection.pretty("ME ann waves", "bob"));
        assertEquals("-- server says hi", ChatConnection.pretty("SYS server says hi", "bob"));
    }
}
