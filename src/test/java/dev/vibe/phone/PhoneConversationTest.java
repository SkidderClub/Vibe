package dev.vibe.phone;

import java.nio.file.Path;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PhoneConversationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void preservesPresetMessagesAndAttachmentAcrossReloads() throws Exception {
        Path file = temporary.newFolder("phone").toPath().resolve("messages.json");
        PhoneConversation conversation = new PhoneConversation(file);
        for (int i = 0; i < 7; i++) conversation.sendPreset(i);
        assertEquals("", conversation.error());
        PhoneConversation reloaded = new PhoneConversation(file);
        assertEquals(7, reloaded.messages().size());
        assertEquals("I want augustus key", reloaded.messages().get(0).text);
        assertTrue(reloaded.messages().get(3).attachment);
        assertEquals("@everyone *4 fake discord scam images*", reloaded.messages().get(3).text);
        assertEquals("i hase dich", reloaded.messages().get(6).text);
        assertEquals(conversation.messages().get(3).sentAt, reloaded.messages().get(3).sentAt);
    }

    @Test public void boundsHistoryAndDoesNotRepeatConsecutiveRandomMessages() throws Exception {
        Path file = temporary.newFolder("phone").toPath().resolve("messages.json");
        PhoneConversation conversation = new PhoneConversation(file);
        String previous = "";
        for (int i = 0; i < 80; i++) {
            conversation.send();
            String current = conversation.messages().get(conversation.messages().size() - 1).text;
            assertNotEquals(previous, current); previous = current;
        }
        assertEquals(64, conversation.messages().size());
        assertEquals(64, new PhoneConversation(file).messages().size());
    }
}
