package dev.vibe.phone;

import dev.vibe.module.Category;
import dev.vibe.module.impl.meme.PhoneModule;
import dev.vibe.module.impl.meme.PhoneModule.Action;
import dev.vibe.module.impl.meme.PhoneModule.App;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.lwjgl.input.Keyboard;
import static org.junit.Assert.*;

public class PhoneModuleTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void visibilityPreferencesKeepExistingDefaultsAndCanBeChangedInThePhone() throws Exception {
        PhoneModule phone = enabled();
        assertTrue(phone.hideSometimes.isEnabled()); assertFalse(phone.hideThumb.isEnabled());
        assertTrue(phone.allowsContext(false, false, false));
        assertFalse(phone.allowsContext(true, false, false));
        assertFalse(phone.allowsContext(false, true, false));
        assertFalse(phone.allowsContext(false, false, true));
        phone.open(App.SETTINGS);
        for (int i = 0; i < 16; i++) phone.act(Action.DOWN);
        assertEquals("Hide sometimes", phone.settingLabel(phone.settingSelection()));
        phone.act(Action.SELECT); assertTrue(phone.allowsContext(true, true, true));
        phone.act(Action.DOWN); phone.act(Action.SELECT); assertTrue(phone.hideThumb.isEnabled());
        assertTrue(phone.interactionAt() > 0); assertEquals(Action.SELECT, phone.lastAction());
    }
    @Test public void contactsRouteToActiveCallsAndMessagesUseTheSelectBinding() throws Exception {
        PhoneModule phone = enabled();
        assertFalse(phone.hints.isEnabled());
        assertEquals(8, PhoneModule.appCount());
        phone.open(App.CONTACTS);
        phone.act(Action.SELECT); assertEquals(App.TELEPHONE, phone.app()); assertTrue(phone.isCalling());
        long started = phone.callStartedAt();
        phone.advance(3600); phone.act(Action.SELECT);
        assertTrue(phone.isCalling()); assertEquals(started, phone.callStartedAt());
        phone.act(Action.HOME); assertEquals(App.HOME, phone.app());
        phone.open(App.TELEPHONE); assertTrue(phone.isCalling());
        PhoneConversation conversation = new PhoneConversation(temporary.newFolder("messages").toPath().resolve("messages.json"));
        Field field = PhoneModule.class.getDeclaredField("conversation"); field.setAccessible(true); field.set(phone, conversation);
        phone.open(App.CONTACTS); phone.act(Action.RIGHT); phone.act(Action.SELECT);
        assertEquals(App.MESSAGES, phone.app());
        assertTrue(phone.handleKey(Keyboard.KEY_NUMPAD5, false)); assertEquals(1, conversation.messages().size());
        phone.keySetting(Action.SELECT).setValue("K");
        assertTrue(phone.handleKey(Keyboard.KEY_K, false)); assertEquals(2, conversation.messages().size());
        phone.handleKey(Keyboard.KEY_K, true); assertEquals(2, conversation.messages().size());
        phone.act(Action.UP); assertEquals(1, phone.messageScroll());
        phone.act(Action.SELECT); assertEquals(0, phone.messageScroll());
    }
    @Test public void navigatesHomeAndRebindsWithoutConflictingControls() throws Exception {
        PhoneModule phone = enabled();
        assertEquals(Category.MEME, phone.getCategory());
        assertTrue(phone.handleKey(Keyboard.KEY_NUMPAD6, false));
        assertEquals(1, phone.homeSelection());
        phone.handleKey(Keyboard.KEY_NUMPAD5, false);
        assertEquals(App.CAMERA, phone.app());
        phone.handleKey(Keyboard.KEY_NUMPAD0, false); assertEquals(App.HOME, phone.app());
        phone.open(App.SETTINGS);
        for (int i = 0; i < 8; i++) phone.act(Action.DOWN);
        phone.act(Action.SELECT); assertEquals(0, phone.binding());
        phone.handleKey(Keyboard.KEY_NUMPAD8, false);
        assertEquals(0, phone.binding()); assertEquals(Keyboard.KEY_NUMPAD0, phone.keyCode(Action.HOME));
        phone.handleKey(Keyboard.KEY_J, false);
        assertEquals(-1, phone.binding()); assertEquals(Keyboard.KEY_J, phone.keyCode(Action.HOME));
        phone.handleKey(Keyboard.KEY_J, false); assertEquals(App.HOME, phone.app());
        assertFalse(phone.handleKey(Keyboard.KEY_F11, false));
    }
    @Test public void appearanceChangesAndGamePausesOutsideItsApp() throws Exception {
        PhoneModule phone = enabled(); phone.open(App.SETTINGS);
        int previous = phone.phoneColor.getArgb(); phone.act(Action.RIGHT);
        assertNotEquals(previous, phone.phoneColor.getArgb());
        phone.open(App.FLAPPY); phone.act(Action.SELECT); phone.advance(.05);
        float y = phone.game().getBirdY();
        phone.open(App.HOME); phone.advance(.05); assertEquals(y, phone.game().getBirdY(), 0);
    }
    @Test public void audioCompletionEndsCallsSchedulesAReplyAndAllowsCallingAgain() throws Exception {
        PhoneModule phone = enabled();
        java.util.concurrent.atomic.AtomicLong time = new java.util.concurrent.atomic.AtomicLong(1000);
        PhoneConversation conversation = new PhoneConversation(temporary.newFolder("calls").toPath().resolve("messages.json"), time::get, new java.util.Random(4));
        Field library = PhoneModule.class.getDeclaredField("conversation"); library.setAccessible(true); library.set(phone, conversation);
        Field audio = PhoneModule.class.getDeclaredField("callAudio"); audio.setAccessible(true);
        FakeCallPlayer playback = (FakeCallPlayer) audio.get(phone);
        phone.open(App.TELEPHONE); phone.act(Action.SELECT); phone.act(Action.SELECT);
        assertEquals(1, playback.starts); assertTrue(phone.isCalling());
        phone.act(Action.HOME); phone.updateCommunications(false); assertTrue(phone.isCalling());
        playback.finished = true; phone.updateCommunications(false);
        assertFalse(phone.isCalling()); assertTrue(phone.hasCallEnded()); assertEquals(1, conversation.pendingCount());
        time.addAndGet(1500); assertTrue(conversation.isTyping());
        time.addAndGet(10000); phone.updateCommunications(false);
        assertEquals(1, conversation.messages().size()); assertTrue(conversation.messages().get(0).incoming);
        assertEquals(1, phone.unreadMessages());
        phone.open(App.MESSAGES); assertEquals(1, phone.unreadMessages());
        phone.act(Action.SELECT); assertTrue(phone.isMessageOpen()); assertEquals(0, phone.unreadMessages());
        phone.open(App.TELEPHONE); phone.act(Action.SELECT);
        assertTrue(phone.isCalling()); assertFalse(phone.hasCallEnded()); assertEquals(2, playback.starts);
        phone.stopCommunications(); assertFalse(phone.isCalling()); assertTrue(playback.closed);
        assertEquals(0, conversation.pendingCount());
    }
    @Test public void repliesCannotScrollPastTheOldestMessageWhenHistoryIsFull() throws Exception {
        PhoneModule phone = enabled();
        java.util.concurrent.atomic.AtomicLong time = new java.util.concurrent.atomic.AtomicLong(1000);
        PhoneConversation conversation = new PhoneConversation(temporary.newFolder("full-history").toPath().resolve("messages.json"), time::get, new java.util.Random(3));
        Field field = PhoneModule.class.getDeclaredField("conversation"); field.setAccessible(true); field.set(phone, conversation);
        for (int i = 0; i < 64; i++) conversation.sendPreset(i % 7);
        phone.openChat(); for (int i = 0; i < 64; i++) phone.act(Action.UP);
        assertEquals(63, phone.messageScroll());
        time.addAndGet(10000); phone.updateCommunications(false);
        assertEquals(64, conversation.messages().size()); assertEquals(63, phone.messageScroll());
    }
    @Test public void photoDeletionRequiresConfirmationAndHomeCancelsIt() throws Exception {
        PhoneModule phone = enabled();
        PhonePhotoLibrary library = new PhonePhotoLibrary(temporary.newFolder("photos").toPath());
        Field field = PhoneModule.class.getDeclaredField("library"); field.setAccessible(true); field.set(phone, library);
        try {
            library.save(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)); library.awaitIdle();
            phone.open(App.PHOTOS); library.awaitIdle();
            phone.act(Action.DELETE); assertTrue(phone.isConfirmDelete());
            phone.act(Action.HOME); assertFalse(phone.isConfirmDelete()); assertEquals(1, library.photos().size());
            phone.open(App.PHOTOS); library.awaitIdle(); phone.act(Action.DELETE); phone.act(Action.SELECT); library.awaitIdle();
            assertEquals(0, library.photos().size());
        } finally { library.close(); }
    }
    @Test public void phonePreferencesAndBestScoreSurviveAProfileReload() throws Exception {
        Field singleton = dev.vibe.Vibe.class.getDeclaredField("instance"); singleton.setAccessible(true);
        Object previous = singleton.get(null); singleton.set(null, null);
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field access = unsafeType.getDeclaredField("theUnsafe"); access.setAccessible(true);
            dev.vibe.module.ModuleManager manager = (dev.vibe.module.ModuleManager) unsafeType.getMethod("allocateInstance", Class.class)
                    .invoke(access.get(null), dev.vibe.module.ModuleManager.class);
            PhoneModule phone = enabled();
            Field modules = dev.vibe.module.ModuleManager.class.getDeclaredField("modules"); modules.setAccessible(true);
            modules.set(manager, new java.util.ArrayList<dev.vibe.module.Module>(java.util.Collections.singletonList(phone)));
            phone.phoneColor.setValue(0xFF336699); phone.backgroundTop.setValue(0xFF553366);
            phone.keySetting(Action.HOME).setValue("J"); phone.bestScore.setValue(19D);
            phone.hideSometimes.setEnabled(false); phone.hideThumb.setEnabled(true);
            phone.messageSounds.setEnabled(false); phone.callVolume.setValue(35D);
            dev.vibe.config.VibeConfig config = new dev.vibe.config.VibeConfig(temporary.newFolder("config"));
            assertTrue(config.save("phone", manager));
            phone.phoneColor.resetToDefault(); phone.backgroundTop.resetToDefault(); phone.keySetting(Action.HOME).resetToDefault(); phone.bestScore.setValue(0D);
            phone.hideSometimes.resetToDefault(); phone.hideThumb.resetToDefault();
            phone.messageSounds.resetToDefault(); phone.callVolume.resetToDefault();
            assertTrue(config.load("phone", manager));
            assertEquals(0xFF336699, phone.phoneColor.getArgb()); assertEquals(0xFF553366, phone.backgroundTop.getArgb());
            assertEquals(Keyboard.KEY_J, phone.keyCode(Action.HOME)); assertEquals(19, phone.bestScore.getInt());
            assertFalse(phone.hideSometimes.isEnabled()); assertTrue(phone.hideThumb.isEnabled());
            assertFalse(phone.messageSounds.isEnabled()); assertEquals(35, phone.callVolume.getInt());
        } finally { singleton.set(null, previous); }
    }
    private static PhoneModule enabled() throws Exception {
        PhoneModule phone = new PhoneModule();
        Field enabled = dev.vibe.module.Module.class.getDeclaredField("enabled"); enabled.setAccessible(true); enabled.setBoolean(phone, true);
        Field audio = PhoneModule.class.getDeclaredField("callAudio"); audio.setAccessible(true); audio.set(phone, new FakeCallPlayer());
        return phone;
    }
    private static final class FakeCallPlayer implements PhoneCallPlayer {
        boolean finished, closed; int starts;
        @Override public void start(float volume) { starts++; finished = closed = false; }
        @Override public void setVolume(float volume) { }
        @Override public boolean isFinished() { return finished; }
        @Override public String error() { return ""; }
        @Override public void close() { closed = true; }
    }
}
