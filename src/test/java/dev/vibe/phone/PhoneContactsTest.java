package dev.vibe.phone;

import dev.vibe.module.impl.meme.PhoneModule;
import dev.vibe.module.impl.meme.PhoneModule.Action;
import dev.vibe.module.impl.meme.PhoneModule.App;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PhoneContactsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final AtomicLong time = new AtomicLong(1000);

    @Test public void everyImportedPortraitDecodesAndCountryLabelsAreDistinct() throws Exception {
        assertEquals(7, PhoneContact.values().length);
        java.util.Set<String> numbers = new java.util.HashSet<String>();
        for (PhoneContact contact : PhoneContact.values()) {
            assertTrue(numbers.add(contact.number));
            if (contact.avatar == null) { assertSame(PhoneContact.CARL, contact); continue; }
            try (InputStream input = getClass().getResourceAsStream("/assets/vibe/textures/phone/" + contact.avatar)) {
                assertNotNull(contact.name, input);
                assertNotNull(contact.name, ImageIO.read(input));
            }
        }
        assertEquals("911", PhoneContact.TRUMP.number);
        assertTrue(PhoneContact.LUCKY.number.startsWith("+33 "));
        assertTrue(PhoneContact.RICHY.number.startsWith("+49 "));
        assertTrue(PhoneContact.CARL.number.startsWith("+49 "));
        assertTrue(PhoneContact.BIPAS.number.startsWith("+221 "));
        assertTrue(PhoneContact.VOGEL.number.startsWith("+91 "));
        assertTrue(PhoneContact.LUCKY.number.matches("\\+33 [67](?: \\d{2}){4}"));
        assertTrue(PhoneContact.RICHY.number.matches("\\+49 1[567]\\d \\d{4} \\d{4}"));
        assertTrue(PhoneContact.CARL.number.matches("\\+49 1[567]\\d \\d{4} \\d{4}"));
        assertTrue(PhoneContact.BIPAS.number.matches("\\+221 7[678] \\d{3} \\d{2} \\d{2}"));
        assertTrue(PhoneContact.VOGEL.number.matches("\\+91 [6-9]\\d{4} \\d{5}"));
    }

    @Test public void newContactChoicesReplyInContextAndReloadIntoTheirOwnHistory() throws Exception {
        for (PhoneContact contact : PhoneContact.values()) {
            if (contact == PhoneContact.ESOUND || contact == PhoneContact.VOGEL) continue;
            Path file = temporary.newFolder(contact.id).toPath().resolve("messages.json");
            PhoneConversation chat = new PhoneConversation(file, contact, time::get, new Random(42));
            assertTrue(chat.presetCount() >= 6);
            for (int i = 0; i < chat.presetCount(); i++) {
                chat.sendPreset(i); time.addAndGet(10000);
                PhoneConversation.Message reply = chat.advance();
                assertNotNull(contact.name, reply); assertTrue(reply.incoming);
                assertFalse(reply.attachment);
                assertTrue(java.util.Arrays.asList(contact.replies(i)).contains(reply.text));
            }
            PhoneConversation reloaded = new PhoneConversation(file, contact);
            assertEquals(chat.messages().size(), reloaded.messages().size());
            for (int i = 0; i < reloaded.messages().size(); i += 2) {
                assertEquals(chat.presetText(i / 2), reloaded.messages().get(i).text);
                assertFalse(reloaded.messages().get(i).attachment);
                assertTrue(reloaded.messages().get(i + 1).incoming);
            }
            assertEquals(chat.presetCount(), reloaded.unreadCount());
        }
    }

    @Test public void bipasOnlyExposesPositiveVibeMessagesAndVogelOnlyTheKeyRequest() throws Exception {
        PhoneConversation bipas = new PhoneConversation(temporary.newFolder("bipas").toPath().resolve("messages.json"), PhoneContact.BIPAS);
        for (int i = 0; i < bipas.presetCount(); i++) {
            assertTrue(bipas.presetText(i).contains("Vibe"));
            assertTrue(bipas.presetText(i).matches(".*(love|favorite|Thank|amazing|happy|great).*"));
        }
        PhoneConversation vogel = new PhoneConversation(temporary.newFolder("vogel").toPath().resolve("messages.json"), PhoneContact.VOGEL);
        assertEquals(1, vogel.presetCount()); assertEquals("I want augustus key", vogel.presetText(0));
        vogel.send(); assertTrue(vogel.isBlocked()); assertEquals(1, vogel.messages().size());
        vogel.send(); vogel.sendPreset(0); vogel.afterCall();
        assertEquals(1, vogel.messages().size()); assertEquals(0, vogel.pendingCount());
        vogel.cancelPending(); assertTrue(vogel.isBlocked());
        vogel.resetSession(); assertFalse(vogel.isBlocked());
        vogel.send(); assertEquals(2, vogel.messages().size()); assertTrue(vogel.isBlocked());
    }

    @Test public void callsAndUnreadRepliesRemainWithTheirOriginalContactWhileBrowsing() throws Exception {
        PhoneModule phone = phone();
        FakeCallPlayer audio = (FakeCallPlayer) field("callAudio").get(phone);
        choose(phone, PhoneContact.LUCKY); phone.act(Action.SELECT);
        assertTrue(phone.isCalling()); assertSame(PhoneContact.LUCKY, phone.callContact());
        choose(phone, PhoneContact.RICHY);
        assertSame(PhoneContact.RICHY, phone.selectedContact());
        assertSame(PhoneContact.LUCKY, phone.callContact());
        audio.finished = true; phone.updateCommunications(false);
        assertFalse(phone.isCalling());
        time.addAndGet(10000); phone.updateCommunications(true);
        assertEquals(1, phone.conversation(PhoneContact.LUCKY).messages().size());
        assertTrue(java.util.Arrays.asList(PhoneContact.LUCKY.callReplies()).contains(phone.conversation(PhoneContact.LUCKY).messages().get(0).text));
        assertTrue(phone.conversation(PhoneContact.RICHY).messages().isEmpty());
        assertEquals(1, phone.unreadMessages());
        phone.conversation(PhoneContact.BIPAS).sendPreset(0);
        time.addAndGet(10000); phone.updateCommunications(true);
        assertEquals(2, phone.unreadMessages());
        choose(phone, PhoneContact.LUCKY); phone.openChat();
        assertEquals(1, phone.unreadMessages());
        assertEquals(1, phone.conversation(PhoneContact.BIPAS).unreadCount());
        for (PhoneContact contact : PhoneContact.values()) {
            choose(phone, contact); phone.act(Action.SELECT);
            assertTrue(contact.name, phone.isCalling()); assertSame(contact, phone.callContact());
            audio.finished = true; phone.updateCommunications(false);
            time.addAndGet(10000); phone.updateCommunications(false);
            assertTrue(contact.name, phone.conversation(contact).messages().get(phone.conversation(contact).messages().size() - 1).incoming);
        }
    }

    @Test public void vogelBlocksImmediatelyCancelsHisCallAndOnlyModuleReenableClearsTheBlock() throws Exception {
        PhoneModule phone = phone();
        FakeCallPlayer audio = (FakeCallPlayer) field("callAudio").get(phone);
        choose(phone, PhoneContact.VOGEL); phone.act(Action.SELECT); assertTrue(phone.isCalling());
        phone.openChat(); phone.act(Action.LEFT); phone.act(Action.RIGHT);
        phone.act(Action.SELECT);
        assertEquals("I want augustus key", phone.conversation().messages().get(0).text);
        assertTrue(phone.conversation().isBlocked()); assertFalse(phone.isCalling()); assertTrue(audio.closed);
        int starts = audio.starts;
        phone.act(Action.SELECT); assertEquals(1, phone.conversation().messages().size());
        phone.open(App.TELEPHONE); phone.act(Action.SELECT); assertEquals(starts, audio.starts);
        phone.stopCommunications(); phone.act(Action.HOME);
        choose(phone, PhoneContact.BIPAS); phone.act(Action.SELECT); assertTrue(phone.isCalling());
        phone.stopCommunications(); choose(phone, PhoneContact.VOGEL); phone.act(Action.SELECT);
        assertFalse(phone.isCalling()); assertTrue(phone.conversation().isBlocked());
        phone.setEnabled(false); phone.setEnabled(true);
        assertFalse(phone.conversation(PhoneContact.VOGEL).isBlocked());
        phone.open(App.TELEPHONE); phone.act(Action.SELECT); assertTrue(phone.isCalling());
        phone.stopCommunications();
    }

    @Test public void selectSendsRandomNonRepeatingMessagesFromEachContactsOwnPool() throws Exception {
        PhoneModule phone = phone();
        for (PhoneContact contact : PhoneContact.values()) {
            if (contact == PhoneContact.VOGEL) continue;
            choose(phone, contact); phone.openChat();
            PhoneConversation chat = phone.conversation();
            assertTrue(chat.messages().isEmpty());
            java.util.Set<String> allowed = new java.util.HashSet<String>();
            for (int i = 0; i < chat.presetCount(); i++) allowed.add(chat.presetText(i));
            String previous = "";
            for (int i = 0; i < 20; i++) {
                phone.act(Action.LEFT); phone.act(Action.RIGHT);
                assertEquals(i, chat.messages().size());
                phone.act(Action.SELECT);
                String sent = chat.messages().get(i).text;
                assertTrue(contact.name, allowed.contains(sent));
                assertNotEquals(contact.name, previous, sent);
                previous = sent;
            }
        }
        assertEquals(20, phone.conversation(PhoneContact.LUCKY).messages().size());
        assertEquals(20, phone.conversation(PhoneContact.BIPAS).messages().size());
    }

    @Test public void inboxSelectsChatsWithoutSendingMessagesOrReadingOtherConversations() throws Exception {
        PhoneModule phone = phone();
        phone.conversation(PhoneContact.LUCKY).sendPreset(0);
        phone.conversation(PhoneContact.BIPAS).sendPreset(0);
        time.addAndGet(10000); phone.updateCommunications(false);
        assertEquals(2, phone.unreadMessages());
        phone.open(App.MESSAGES);
        assertFalse(phone.isMessageOpen());
        phone.act(Action.DOWN); phone.act(Action.DOWN);
        assertSame(PhoneContact.LUCKY, phone.selectedContact());
        phone.updateCommunications(true);
        assertEquals(2, phone.unreadMessages());
        phone.act(Action.SELECT);
        assertTrue(phone.isMessageOpen()); assertEquals(1, phone.unreadMessages());
        assertEquals(2, phone.conversation().messages().size());
        phone.act(Action.SELECT); assertEquals(3, phone.conversation().messages().size());
        phone.act(Action.BACK); assertFalse(phone.isMessageOpen()); assertEquals(App.MESSAGES, phone.app());
        phone.act(Action.DOWN); phone.act(Action.SELECT);
        assertSame(PhoneContact.RICHY, phone.selectedContact()); assertTrue(phone.isMessageOpen());
        assertTrue(phone.conversation().messages().isEmpty());
        phone.act(Action.SELECT); assertEquals(1, phone.conversation().messages().size());
        assertEquals(3, phone.conversation(PhoneContact.LUCKY).messages().size());
        assertEquals(1, phone.conversation(PhoneContact.BIPAS).unreadCount());
        phone.act(Action.BACK); phone.act(Action.BACK); assertEquals(App.HOME, phone.app());
    }

    @Test public void contactsActionsAndTelephoneListWorkWithoutOpeningSingleContactProfiles() throws Exception {
        PhoneModule phone = phone();
        phone.open(App.CONTACTS); phone.act(Action.RIGHT);
        phone.act(Action.DOWN); phone.act(Action.DOWN);
        assertEquals(1, phone.contactAction()); assertSame(PhoneContact.LUCKY, phone.selectedContact());
        phone.act(Action.SELECT);
        assertEquals(App.MESSAGES, phone.app()); assertTrue(phone.isMessageOpen());
        assertTrue(phone.conversation().messages().isEmpty()); assertFalse(phone.isCalling());
        phone.open(App.TELEPHONE);
        for (PhoneContact contact : PhoneContact.values()) {
            while (phone.contactSelection() < contact.ordinal()) phone.act(Action.DOWN);
            while (phone.contactSelection() > contact.ordinal()) phone.act(Action.UP);
            assertSame(contact, phone.selectedContact());
            phone.act(Action.SELECT); assertTrue(phone.isCalling()); assertSame(contact, phone.callContact());
            phone.stopCommunications();
        }
    }

    @SuppressWarnings("unchecked") private PhoneModule phone() throws Exception {
        PhoneModule phone = new PhoneModule();
        EnumMap<PhoneContact, PhoneConversation> chats = (EnumMap<PhoneContact, PhoneConversation>) field("conversations").get(phone);
        Path root = temporary.newFolder().toPath();
        for (PhoneContact contact : PhoneContact.values())
            chats.put(contact, new PhoneConversation(root.resolve(contact.id + ".json"), contact, time::get, new Random(17)));
        field("callAudio").set(phone, new FakeCallPlayer());
        phone.messageSounds.setEnabled(false); phone.setEnabled(true);
        return phone;
    }
    private static void choose(PhoneModule phone, PhoneContact contact) {
        phone.open(App.CONTACTS);
        while (phone.contactSelection() < contact.ordinal()) phone.act(Action.DOWN);
        while (phone.contactSelection() > contact.ordinal()) phone.act(Action.UP);
    }
    private static Field field(String name) throws Exception {
        Field field = PhoneModule.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static final class FakeCallPlayer implements PhoneCallPlayer {
        boolean finished, closed; int starts;
        public void start(float volume) { starts++; finished = closed = false; }
        public void setVolume(float volume) { }
        public boolean isFinished() { return finished; }
        public String error() { return ""; }
        public void close() { closed = true; }
    }
}
