package dev.vibe.module.impl.meme;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.phone.FlappyGame;
import dev.vibe.phone.PhonePhotoLibrary;
import dev.vibe.phone.PhoneConversation;
import dev.vibe.setting.*;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** VibeOS controller. App state is independent of rendering and game movement. */
public final class PhoneModule extends Module {
    public enum App { HOME, SETTINGS, CAMERA, FLAPPY, PHOTOS, INTERNET, CONTACTS, TELEPHONE, MESSAGES }
    public enum Action { HOME, UP, DOWN, LEFT, RIGHT, SELECT, BACK, DELETE }
    public static final String INTERNET_URL = "https://www.youtube.com/@heisthacksjp";
    private static final String[] APP_NAMES = {"Settings", "Camera", "Flappy Bird", "Photos", "Internet", "Contacts", "Telephone", "Messages"};
    private static final App[] HOME_APPS = {App.SETTINGS, App.CAMERA, App.FLAPPY, App.PHOTOS, App.INTERNET, App.CONTACTS, App.TELEPHONE, App.MESSAGES};
    private static final int[] COLORS = {0xFFB5B1A8, 0xFF292B33, 0xFFEAE8E3, 0xFF5E95CC, 0xFFE6ACB6, 0xFF9778E5, 0xFF56C6B0};
    private static final String[] COLOR_NAMES = {"Titanium", "Midnight", "Starlight", "Ocean", "Rose", "Violet", "Mint"};
    private static final int[] BACKGROUNDS = {0xFF281446, 0xFF062D4E, 0xFF631E50, 0xFF102D30, 0xFF151722, 0xFF4330A1, 0xFF84254D, 0xFF0D6160};
    public final ColorSetting phoneColor = addSetting(new ColorSetting("Phone Color", COLORS[0]));
    public final ColorSetting backgroundTop = addSetting(new ColorSetting("Background Top", 0xFF281446));
    public final ColorSetting backgroundBottom = addSetting(new ColorSetting("Background Bottom", 0xFF4330A1));
    public final ModeSetting wallpaper = addSetting(new ModeSetting("Wallpaper", "Vibe", "Vibe", "Aurora", "Sunset", "Minimal"));
    public final NumberSetting size = addSetting(new NumberSetting("Phone Size", 1, .65, 1.35, .05));
    public final NumberSetting brightness = addSetting(new NumberSetting("Brightness", 100, 30, 100, 5));
    public final BooleanSetting hints = addSetting(new BooleanSetting("Control Hints", false));
    public final BooleanSetting sounds = addSetting(new BooleanSetting("Camera Sound", true));
    public final NumberSetting bestScore = addSetting(new NumberSetting("Flappy Best", 0, 0, 1000000, 1));
    private final StringSetting[] keys = new StringSetting[Action.values().length];
    private App app = App.HOME;
    private int homeSelection, settingSelection, photoSelection;
    private boolean photoOpen, confirmDelete, shutterRequested;
    private boolean waitingForSave;
    private int binding = -1, lastPhotoRevision = -1;
    private long openedAt, appAt, notificationUntil, flashAt;
    private String notification = "";
    private String reportedError = "";
    private PhonePhotoLibrary library;
    private PhoneConversation conversation;
    private boolean contactOpen, calling;
    private int contactAction, messageScroll;
    private long callStartedAt;
    private final FlappyGame flappy = new FlappyGame();

    public PhoneModule() {
        super("Phone", "Hold a phone running VibeOS", Category.MEME, Keyboard.KEY_NONE);
        String[] defaults = {"NUMPAD0", "NUMPAD8", "NUMPAD2", "NUMPAD4", "NUMPAD6", "NUMPAD5", "DECIMAL", "NUMPAD7"};
        String[] labels = {"Home", "Up", "Down", "Left", "Right", "Select", "Back", "Delete"};
        for (int i = 0; i < keys.length; i++) keys[i] = addSetting(new StringSetting(labels[i] + " Key", defaults[i], 24, null));
    }
    @Override protected void onEnable() {
        openedAt = System.currentTimeMillis(); appAt = openedAt;
        app = App.HOME; homeSelection = 0; binding = -1; shutterRequested = confirmDelete = false;
        contactOpen = calling = false; contactAction = messageScroll = 0;
    }
    @Override protected void onDisable() {
        shutterRequested = false; binding = -1; confirmDelete = calling = false;
    }
    public boolean handleKey(int key, boolean repeat) {
        if (!isEnabled()) return false;
        if (binding >= 0) {
            if (!repeat) rebind(key);
            return true;
        }
        for (Action action : Action.values()) if (key == keyCode(action)) {
            if (!repeat || action == Action.UP || action == Action.DOWN || action == Action.LEFT || action == Action.RIGHT)
                act(action);
            return true;
        }
        return false;
    }
    public void act(Action action) {
        if (action == Action.HOME) { open(App.HOME); return; }
        if (confirmDelete) {
            if (action == Action.SELECT) {
                PhonePhotoLibrary.Photo photo = selectedPhoto();
                if (photo != null) { photos().delete(photo); toast("Photo deleted"); }
                confirmDelete = false; photoOpen = false;
            } else if (action == Action.BACK || action == Action.DELETE) confirmDelete = false;
            return;
        }
        if (action == Action.BACK) {
            if (app == App.CONTACTS && contactOpen) contactOpen = false;
            else if (app == App.PHOTOS && photoOpen) photoOpen = false; else open(App.HOME);
            return;
        }
        switch (app) {
            case HOME:
                if (action == Action.LEFT) homeSelection = Math.max(0, homeSelection - 1);
                if (action == Action.RIGHT) homeSelection = Math.min(HOME_APPS.length - 1, homeSelection + 1);
                if (action == Action.UP) homeSelection = Math.max(0, homeSelection - 3);
                if (action == Action.DOWN) homeSelection = Math.min(HOME_APPS.length - 1, homeSelection + 3);
                if (action == Action.SELECT) {
                    App next = HOME_APPS[homeSelection];
                    open(next);
                    if (next == App.INTERNET) openInternet();
                }
                break;
            case SETTINGS:
                if (action == Action.UP) settingSelection = Math.max(0, settingSelection - 1);
                if (action == Action.DOWN) settingSelection = Math.min(settingsCount() - 1, settingSelection + 1);
                if (action == Action.LEFT || action == Action.RIGHT || action == Action.SELECT) {
                    if (settingSelection >= 8 && settingSelection < 16) {
                        if (action == Action.SELECT) { binding = settingSelection - 8; toast("Press a key; Esc cancels"); }
                    } else changeSetting(action == Action.LEFT ? -1 : 1);
                }
                break;
            case CAMERA:
                if (action == Action.SELECT) {
                    if (!photos().isSaving()) shutterRequested = true; else toast("Saving your photo...");
                }
                break;
            case FLAPPY:
                if (action == Action.SELECT || action == Action.UP) flappy.flap();
                break;
            case PHOTOS:
                int count = photos().photos().size();
                if (action == Action.DELETE && count > 0) confirmDelete = true;
                if (!photoOpen) {
                    int delta = action == Action.LEFT ? -1 : action == Action.RIGHT ? 1 : action == Action.UP ? -2 : action == Action.DOWN ? 2 : 0;
                    photoSelection = Math.max(0, Math.min(Math.max(0, count - 1), photoSelection + delta));
                    if (action == Action.SELECT && count > 0) photoOpen = true;
                } else {
                    if (action == Action.LEFT) photoSelection = Math.max(0, photoSelection - 1);
                    if (action == Action.RIGHT) photoSelection = Math.min(Math.max(0, count - 1), photoSelection + 1);
                }
                break;
            case INTERNET:
                if (action == Action.SELECT) openInternet();
                break;
            case CONTACTS:
                if (!contactOpen) {
                    if (action == Action.SELECT) { contactOpen = true; contactAction = 0; }
                } else {
                    if (action == Action.LEFT || action == Action.UP) contactAction = 0;
                    if (action == Action.RIGHT || action == Action.DOWN) contactAction = 1;
                    if (action == Action.SELECT) {
                        if (contactAction == 0) call(); else open(App.MESSAGES);
                    }
                }
                break;
            case TELEPHONE:
                if (action == Action.SELECT && !calling) call();
                break;
            case MESSAGES:
                if (action == Action.UP) messageScroll = Math.min(Math.max(0, conversation().messages().size() - 1), messageScroll + 1);
                if (action == Action.DOWN) messageScroll = Math.max(0, messageScroll - 1);
                if (action == Action.SELECT) {
                    conversation().send(); messageScroll = 0;
                    if (!conversation().error().isEmpty()) toast(conversation().error());
                }
                break;
            default: break;
        }
    }
    public void open(App next) {
        app = next; appAt = System.currentTimeMillis(); binding = -1; confirmDelete = false;
        if (next == App.PHOTOS) { photos().refresh(); photoOpen = false; }
        if (next == App.CONTACTS) { contactOpen = false; contactAction = 0; }
        if (next == App.MESSAGES) { conversation(); messageScroll = 0; }
        shutterRequested = false;
    }
    public void advance(double seconds) {
        if (library != null && !library.error().isEmpty() && !library.error().equals(reportedError)) {
            reportedError = library.error(); toast(library.error());
        }
        if (waitingForSave && library != null && !library.isSaving()) {
            waitingForSave = false;
            toast(library.error().isEmpty() ? "Saved to Photos" : "Photo could not be saved");
        }
        if (app == App.FLAPPY) {
            flappy.advance(seconds);
            if (flappy.getScore() > bestScore.getInt()) { bestScore.setValue((double) flappy.getScore()); persist(); }
        }
        if (library != null && library.revision() != lastPhotoRevision) {
            lastPhotoRevision = library.revision();
            photoSelection = Math.min(photoSelection, Math.max(0, library.photos().size() - 1));
        }
    }
    private void rebind(int key) {
        if (key == Keyboard.KEY_ESCAPE) { binding = -1; toast("Key binding cancelled"); return; }
        String name = Keyboard.getKeyName(key);
        if (name == null || key <= 0 || key > 255) { toast("Choose a keyboard key"); return; }
        for (int i = 0; i < keys.length; i++) if (i != binding && keyCode(Action.values()[i]) == key) {
            toast("Already used for " + Action.values()[i].name().toLowerCase(java.util.Locale.ROOT)); return;
        }
        if (key == getKey()) { toast("Choose a key other than Phone's toggle"); return; }
        keys[binding].setValue(name); binding = -1; persist(); toast("Control updated");
    }
    private void changeSetting(int direction) {
        switch (settingSelection) {
            case 0: phoneColor.setValue(cycle(phoneColor.getArgb(), COLORS, direction)); break;
            case 1: wallpaper.cycle(direction < 0); break;
            case 2: backgroundTop.setValue(cycle(backgroundTop.getArgb(), BACKGROUNDS, direction)); break;
            case 3: backgroundBottom.setValue(cycle(backgroundBottom.getArgb(), BACKGROUNDS, direction)); break;
            case 4: brightness.setValue(brightness.getDouble() + 5 * direction); break;
            case 5: size.setValue(size.getDouble() + .05 * direction); break;
            case 6: hints.toggle(); break;
            case 7: sounds.toggle(); break;
            case 16:
                for (StringSetting key : keys) key.resetToDefault();
                toast("Default controls restored"); break;
            default: break;
        }
        persist();
    }
    private static int cycle(int value, int[] values, int direction) {
        int index = 0;
        for (int i = 0; i < values.length; i++) if (value == values[i]) index = i;
        return values[(index + direction + values.length) % values.length];
    }
    public int settingsCount() { return 17; }
    public String settingLabel(int index) {
        String[] labels = {"Phone finish", "Wallpaper", "Background top", "Background bottom", "Brightness", "Phone size", "Show controls", "Shutter sound",
                "Home", "Up", "Down", "Left", "Right", "Select / shutter", "Back", "Delete photo", "Reset controls"};
        return labels[index];
    }
    public String settingValue(int index) {
        switch (index) {
            case 0:
                for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == phoneColor.getArgb()) return COLOR_NAMES[i];
                return "Custom";
            case 1: return wallpaper.getValue();
            case 2: return String.format("#%06X", backgroundTop.getArgb() & 0xFFFFFF);
            case 3: return String.format("#%06X", backgroundBottom.getArgb() & 0xFFFFFF);
            case 4: return brightness.getInt() + "%";
            case 5: return Math.round(size.getDouble() * 100) + "%";
            case 6: return hints.isEnabled() ? "On" : "Off";
            case 7: return sounds.isEnabled() ? "On" : "Off";
            case 16: return "Restore";
            default: return index >= 8 && index < 16 ? displayKey(Action.values()[index - 8]) : "";
        }
    }
    public int keyCode(Action action) {
        return Keyboard.getKeyIndex(keys[action.ordinal()].getValue().trim().toUpperCase(java.util.Locale.ROOT));
    }
    public String keyName(Action action) { return keys[action.ordinal()].getValue(); }
    public String shortKey(Action action) {
        String name = keyName(action).toUpperCase(java.util.Locale.ROOT);
        if (name.startsWith("NUMPAD")) return name.substring(6);
        return "DECIMAL".equals(name) ? "." : displayKey(action);
    }
    private String displayKey(Action action) {
        String name = keyName(action).toLowerCase(java.util.Locale.ROOT);
        if (name.startsWith("numpad")) return "Numpad " + name.substring(6);
        if (name.equals("decimal")) return "Numpad .";
        return name.isEmpty() ? "None" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    public StringSetting keySetting(Action action) { return keys[action.ordinal()]; }
    public PhonePhotoLibrary photos() {
        if (library == null) {
            Minecraft mc = Minecraft.getMinecraft();
            java.io.File directory = mc == null ? null : mc.mcDataDir;
            library = new PhonePhotoLibrary((directory == null ? Paths.get("run/client") : directory.toPath()).resolve("vibe/phone/photos"));
            library.installStarterPhotos();
            library.refresh();
        }
        return library;
    }
    public PhonePhotoLibrary.Photo selectedPhoto() {
        List<PhonePhotoLibrary.Photo> photos = photos().photos();
        return photos.isEmpty() ? null : photos.get(Math.min(photoSelection, photos.size() - 1));
    }
    public PhoneConversation conversation() {
        if (conversation == null) {
            Minecraft mc = Minecraft.getMinecraft();
            java.io.File directory = mc == null ? null : mc.mcDataDir;
            conversation = new PhoneConversation((directory == null ? Paths.get("run/client") : directory.toPath()).resolve("vibe/phone/messages.json"));
        }
        return conversation;
    }
    private void call() {
        open(App.TELEPHONE);
        if (!calling) { calling = true; callStartedAt = System.currentTimeMillis(); }
    }
    private void openInternet() {
        toast("Opening YouTube in your browser");
        Thread browser = new Thread(() -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                    throw new IllegalStateException("No browser available");
                Desktop.getDesktop().browse(URI.create(INTERNET_URL));
            } catch (Exception failure) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null) mc.addScheduledTask(() -> toast("Could not open your browser"));
            }
        }, "VibeOS Internet");
        browser.setDaemon(true); browser.start();
    }
    public void toast(String message) { notification = message; notificationUntil = System.currentTimeMillis() + 2800; }
    public void captured() {
        shutterRequested = false; waitingForSave = true; flashAt = System.currentTimeMillis(); toast("Saving photo...");
        Minecraft mc = Minecraft.getMinecraft();
        if (sounds.isEnabled() && mc != null && mc.thePlayer != null) mc.thePlayer.playSound("random.click", .35F, 1.65F);
    }
    public void captureFailed(String message) { shutterRequested = false; toast(message); }
    private static void persist() {
        Vibe vibe = Vibe.getInstance();
        if (vibe != null && vibe.getConfig() != null && !vibe.getConfig().isLoading()) vibe.getConfig().save(vibe.getModuleManager());
    }
    public App app() { return app; }
    public int homeSelection() { return homeSelection; }
    public int settingSelection() { return settingSelection; }
    public int photoSelection() { return photoSelection; }
    public boolean isPhotoOpen() { return photoOpen; }
    public boolean isConfirmDelete() { return confirmDelete; }
    public int binding() { return binding; }
    public void cancelBinding() { binding = -1; toast("Key binding cancelled"); }
    public boolean wantsCapture() { return shutterRequested; }
    public long openedAt() { return openedAt; }
    public long appAt() { return appAt; }
    public long flashAt() { return flashAt; }
    public FlappyGame game() { return flappy; }
    public String notification() { return System.currentTimeMillis() < notificationUntil ? notification : ""; }
    public static String appName(int index) { return APP_NAMES[index]; }
    public static int appCount() { return APP_NAMES.length; }
    public boolean isContactOpen() { return contactOpen; }
    public int contactAction() { return contactAction; }
    public boolean isCalling() { return calling; }
    public long callStartedAt() { return callStartedAt; }
    public int messageScroll() { return messageScroll; }
}
