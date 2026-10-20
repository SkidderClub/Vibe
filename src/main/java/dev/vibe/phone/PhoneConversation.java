package dev.vibe.phone;

import com.google.gson.*;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** A local VibeOS conversation. No telephone, chat-server or Minecraft-chat transport. */
public final class PhoneConversation {
    public static final String CONTACT = "ESound", NUMBER = "+67 1874/2069";
    private static final String[] PRESETS = {
        "I want augustus key", "please augustus update", "fix polar online config",
        "@everyone *4 fake discord scam images*", "help augustus is not launching",
        "give me media bro, i pay you 10 rubles", "i hase dich"
    };
    private static final int LIMIT = 64;
    public static final class Message {
        public final String text;
        public final long sentAt;
        public final boolean attachment;
        private Message(int preset, long sentAt) {
            this.text = PRESETS[preset]; this.sentAt = sentAt; this.attachment = preset == 3;
        }
    }
    private final Path file;
    private final List<Message> messages = new ArrayList<Message>();
    private final Random random = new Random();
    private String error = "";
    private int lastPreset = -1;

    public PhoneConversation(Path file) {
        this.file = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(this.file)) return;
        try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            JsonArray saved = new JsonParser().parse(reader).getAsJsonArray();
            for (int i = Math.max(0, saved.size() - LIMIT); i < saved.size(); i++) {
                JsonObject entry = saved.get(i).getAsJsonObject();
                int preset = entry.get("preset").getAsInt();
                if (preset < 0 || preset >= PRESETS.length) continue;
                messages.add(new Message(preset, entry.get("sentAt").getAsLong())); lastPreset = preset;
            }
        } catch (IOException | RuntimeException failure) { error = "Could not load messages"; }
    }

    public void send() {
        int preset = random.nextInt(PRESETS.length - (lastPreset < 0 ? 0 : 1));
        if (preset >= lastPreset && lastPreset >= 0) preset++;
        sendPreset(preset);
    }

    public void sendPreset(int preset) {
        if (preset < 0 || preset >= PRESETS.length) throw new IllegalArgumentException("Unknown message");
        messages.add(new Message(preset, System.currentTimeMillis())); lastPreset = preset;
        if (messages.size() > LIMIT) messages.remove(0);
        JsonArray saved = new JsonArray();
        for (Message message : messages) {
            JsonObject entry = new JsonObject(); entry.addProperty("preset", presetIndex(message.text)); entry.addProperty("sentAt", message.sentAt);
            saved.add(entry);
        }
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent()); temporary = Files.createTempFile(file.getParent(), ".messages-", ".tmp");
            Files.write(temporary, saved.toString().getBytes(StandardCharsets.UTF_8));
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            error = "";
        } catch (IOException failure) { error = "Message saved for this session only"; }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
    }

    private static int presetIndex(String text) {
        for (int i = 0; i < PRESETS.length; i++) if (PRESETS[i].equals(text)) return i;
        return 0;
    }
    public List<Message> messages() { return Collections.unmodifiableList(messages); }
    public String error() { return error; }
}
