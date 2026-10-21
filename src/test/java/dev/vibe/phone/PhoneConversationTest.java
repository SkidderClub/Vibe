package dev.vibe.phone;

import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
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

    @Test public void repliesTypeBeforeArrivingAndPreserveDirectionAndUnreadState() throws Exception {
        Path file = temporary.newFolder("replies").toPath().resolve("messages.json");
        AtomicLong time = new AtomicLong(1000);
        PhoneConversation conversation = new PhoneConversation(file, time::get, new Random(17));
        conversation.sendPreset(2);
        assertFalse(conversation.isTyping()); assertNull(conversation.advance());
        time.addAndGet(1500); assertTrue(conversation.isTyping()); assertNull(conversation.advance());
        time.addAndGet(10000); PhoneConversation.Message reply = conversation.advance();
        assertNotNull(reply); assertTrue(reply.incoming); assertFalse(reply.attachment);
        assertFalse(conversation.isTyping()); assertEquals(1, conversation.unreadCount());
        PhoneConversation reloaded = new PhoneConversation(file);
        assertEquals(2, reloaded.messages().size()); assertEquals(1, reloaded.unreadCount());
        assertFalse(reloaded.messages().get(0).incoming); assertTrue(reloaded.messages().get(1).incoming);
        assertEquals(reply.text, reloaded.messages().get(1).text);
        reloaded.markRead(); assertEquals(0, new PhoneConversation(file).unreadCount());
    }

    @Test public void burstsAreBoundedAndDoNotDeliverEveryQueuedReplyInOneFrame() throws Exception {
        AtomicLong time = new AtomicLong(1000);
        PhoneConversation conversation = new PhoneConversation(temporary.newFolder("burst").toPath().resolve("messages.json"), time::get, new Random(7));
        for (int i = 0; i < 50; i++) conversation.send();
        assertEquals(8, conversation.pendingCount());
        time.addAndGet(100000); assertNotNull(conversation.advance());
        assertNull(conversation.advance()); assertFalse(conversation.isTyping());
        conversation.afterCall(); assertEquals(8, conversation.pendingCount());
        time.addAndGet(10000); assertNotNull(conversation.advance());
        time.addAndGet(10000); PhoneConversation.Message callReply = conversation.advance();
        assertTrue(callReply.incoming);
        assertTrue(callReply.text.contains("call") || callReply.text.contains("Can't talk") || callReply.text.contains("Please text"));
        conversation.cancelPending(); assertEquals(0, conversation.pendingCount()); assertFalse(conversation.isTyping());
    }
}
