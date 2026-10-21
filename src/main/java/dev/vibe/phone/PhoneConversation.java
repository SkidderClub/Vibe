package dev.vibe.phone;

import com.google.gson.*;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;

/** A local VibeOS conversation. No telephone, chat-server or Minecraft-chat transport. */
public final class PhoneConversation {
    public static final String CONTACT = "ESound", NUMBER = "+67 1874/2069";
    private static final String[] PRESETS = {
        "I want augustus key", "please augustus update", "fix polar online config",
        "@everyone *4 fake discord scam images*", "help augustus is not launching",
        "give me media bro, i pay you 10 rubles", "i hase dich"
    };
    private static final int LIMIT = 64;
    private static final int MAX_PENDING = 8;
    private static final String[] CONTEXT_REPLIES = {
        "A key? Let me finish the school run and I'll consider this very generous proposal.",
        "Yes, an update. I had almost managed a whole grocery trip without that reminder.",
        "I'm at the checkout. The Polar config will have to wait behind milk and bread.",
        "Wonderful. Another ping. Exactly what the school pickup queue was missing.",
        "It worked before I left the house. Try giving it the same patience you give me.",
        "Ten rubles? That should cover half a grocery bag. Tempting offer.",
        "Noted. I'll file that under things to care about after the laundry."
    };
    private static final String[] BUSY_REPLIES = {
        "I have to take the kids to school. Apparently they expect that every day.",
        "Buying groceries. Unless Augustus can carry the bags, it can wait.",
        "The washing machine finished. Unlike this conversation, that needs my attention.",
        "I'm cooking dinner. Please submit your next urgent request to the toaster.",
        "I have a dentist appointment. Somehow this chat is still the more painful option.",
        "The kids need collecting. Your config is not currently one of my children.",
        "Taking the bins out. Yes, even developers have a thrilling private life."
    };
    private static final String[] LUCKY_REPLIES = {
        "Lucky patched Polar again. Fantastic. I was hoping to spend my grocery trip debugging.",
        "Blame Lucky. I've got the school run and apparently his changelog to deal with.",
        "Lucky has apparently decided I don't need an evening off. I'm naming the next bug after him.",
        "I'm making dinner. Lucky can explain to the pasta why the Augustus update is late.",
        "Lucky again. He's getting my entire grocery receipt in the next changelog.",
        "Tell Lucky he's on my list. The list of people getting absolutely no early access.",
        "Another Polar patch from Lucky. I'm saving my most irritating bug reports just for him.",
        "Lucky gets no sneak peek at the next update. Not even at my shopping list.",
        "Lucky found another way to ruin my afternoon. I'll return the favor with a very petty changelog.",
        "The config was fine until Lucky got productive. I'm revoking his imaginary employee-of-the-month award.",
        "Ask Lucky. I've got laundry to fold and a completely reasonable grudge to maintain."
    };
    private static final String[] CALL_REPLIES = {
        "Saw your call. I'm buying groceries. Texting exists for exactly this reason.",
        "I can't answer calls during the school run. Shocking, I know.",
        "Thanks for calling while I'm cooking. The smoke alarm already had the audio slot booked.",
        "Missed your call. I was doing laundry, not waiting beside a telephone for you.",
        "Your call ended. Blame Lucky; I'm blaming him for the entire afternoon anyway.",
        "Saw the call. Lucky patched Polar, the kids need collecting, and somehow this is all my problem.",
        "Can't talk. I'm writing Lucky the least enthusiastic changelog he's ever received.",
        "I was in the grocery queue. Lucky gets the blame for the missed call too. Efficient.",
        "Please text. Lucky already used up my daily allowance of unnecessary interruptions.",
        "Missed your call. Lucky is getting zero early access and you are getting this message."
    };
    public static final class Message {
        public final String text;
        public final long sentAt;
        public final boolean attachment, incoming;
        private final int preset;
        private boolean read;
        private Message(int preset, long sentAt) {
            this.text = PRESETS[preset]; this.sentAt = sentAt; this.attachment = preset == 3;
            this.preset = preset; incoming = false; read = true;
        }
        private Message(String text, long sentAt, boolean read) {
            this.text = text; this.sentAt = sentAt; this.read = read;
            incoming = true; attachment = false; preset = -1;
        }
    }
    private static final class PendingReply {
        final String text;
        long typingAt, deliverAt;
        PendingReply(String text) { this.text = text; }
    }
    private final Path file;
    private final List<Message> messages = new ArrayList<Message>();
    private final Deque<PendingReply> pending = new ArrayDeque<PendingReply>();
    private final Random random;
    private final LongSupplier clock;
    private String error = "";
    private String lastReply = "";
    private int lastPreset = -1;

    public PhoneConversation(Path file) {
        this(file, () -> System.nanoTime() / 1000000L, new Random());
    }
    PhoneConversation(Path file, LongSupplier clock, Random random) {
        this.file = file.toAbsolutePath().normalize();
        this.clock = clock; this.random = random;
        if (!Files.isRegularFile(this.file)) return;
        try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            JsonArray saved = new JsonParser().parse(reader).getAsJsonArray();
            for (int i = Math.max(0, saved.size() - LIMIT); i < saved.size(); i++) {
                JsonObject entry = saved.get(i).getAsJsonObject();
                if (entry.has("incoming") && entry.get("incoming").getAsBoolean()) {
                    String text = entry.get("text").getAsString();
                    if (text.isEmpty() || text.length() > 512) continue;
                    messages.add(new Message(text, entry.get("sentAt").getAsLong(), entry.has("read") && entry.get("read").getAsBoolean()));
                    lastReply = text;
                } else {
                    int preset = entry.get("preset").getAsInt();
                    if (preset < 0 || preset >= PRESETS.length) continue;
                    messages.add(new Message(preset, entry.get("sentAt").getAsLong())); lastPreset = preset;
                }
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
        String reply = random.nextInt(100) < 65 ? choose(LUCKY_REPLIES)
                : random.nextBoolean() ? CONTEXT_REPLIES[preset] : choose(BUSY_REPLIES);
        enqueue(reply); trim(); save();
    }
    public void afterCall() {
        String text = choose(CALL_REPLIES); lastReply = text;
        // Let an existing typing bubble finish, then prioritize the missed-call reply.
        PendingReply active = pending.pollFirst();
        pending.addFirst(new PendingReply(text));
        if (active != null) pending.addFirst(active); else startTyping(clock.getAsLong());
        while (pending.size() > MAX_PENDING) pending.removeLast();
    }

    private String choose(String[] replies) {
        int index = random.nextInt(replies.length);
        if (replies[index].equals(lastReply)) index = (index + 1) % replies.length;
        return replies[index];
    }
    private void enqueue(String text) {
        if (text.equals(lastReply)) text = choose(BUSY_REPLIES);
        lastReply = text;
        if (pending.size() == MAX_PENDING) pending.removeLast();
        pending.addLast(new PendingReply(text));
        if (pending.size() == 1) startTyping(clock.getAsLong());
    }
    private void startTyping(long now) {
        PendingReply reply = pending.peekFirst();
        if (reply == null) return;
        reply.typingAt = now + 650 + random.nextInt(750);
        reply.deliverAt = reply.typingAt + Math.min(4800, 1100 + reply.text.length() * 24) + random.nextInt(500);
    }
    /** Client-tick delivery: no sleeping threads, network requests or per-frame file reads. */
    public Message advance() {
        PendingReply reply = pending.peekFirst();
        long now = clock.getAsLong();
        if (reply == null || now < reply.deliverAt) return null;
        pending.removeFirst();
        Message message = new Message(reply.text, System.currentTimeMillis(), false);
        messages.add(message); trim(); save(); startTyping(now);
        return message;
    }
    public boolean isTyping() { return !pending.isEmpty() && clock.getAsLong() >= pending.peekFirst().typingAt; }
    public long typingElapsed() { return pending.isEmpty() ? 0 : Math.max(0, clock.getAsLong() - pending.peekFirst().typingAt); }
    public int unreadCount() { int count = 0; for (Message message : messages) if (message.incoming && !message.read) count++; return count; }
    public void markRead() {
        if (unreadCount() == 0) return;
        for (Message message : messages) message.read = true;
        save();
    }
    public void cancelPending() { pending.clear(); }
    int pendingCount() { return pending.size(); }
    private void trim() {
        if (messages.size() > LIMIT) messages.remove(0);
    }
    private void save() {
        JsonArray saved = new JsonArray();
        for (Message message : messages) {
            JsonObject entry = new JsonObject(); entry.addProperty("sentAt", message.sentAt);
            if (message.incoming) {
                entry.addProperty("incoming", true); entry.addProperty("text", message.text); entry.addProperty("read", message.read);
            } else entry.addProperty("preset", message.preset);
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

    public List<Message> messages() { return Collections.unmodifiableList(messages); }
    public String error() { return error; }
}
