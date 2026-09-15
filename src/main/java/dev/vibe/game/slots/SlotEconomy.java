package dev.vibe.game.slots;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Shared local-only virtual economy for the Meme arcade.  Tokens have no
 * real-world value and never cross the client boundary.  All balance changes
 * are serialized here, persisted atomically, and recorded as one transaction.
 */
public final class SlotEconomy {
    private static final int VERSION = 1;
    private static final int MAX_HISTORY = 80;
    private final Path file;
    private final List<Transaction> history = new ArrayList<Transaction>();
    private long balance = SlotConfig.STARTING_BALANCE;
    private long lastAllowanceAt;
    private long lastObservedClock;
    private boolean adultVerified;
    private PendingRound pendingRound;
    private boolean writeBlocked;
    private String saveError;

    public static final class Transaction {
        private final String id, game, result, roundId;
        private final long bet, payout, net, timestamp;
        private Transaction(String id, String game, long bet, String result, long payout, long timestamp, String roundId) {
            this.id = id; this.game = game; this.bet = bet; this.result = result; this.payout = payout;
            this.net = payout - bet; this.timestamp = timestamp; this.roundId = roundId;
        }
        public String getId() { return id; }
        public String getGame() { return game; }
        public long getBet() { return bet; }
        public String getResult() { return result; }
        public long getPayout() { return payout; }
        public long getNet() { return net; }
        public long getTimestamp() { return timestamp; }
        public String getRoundId() { return roundId; }
    }

    /** A wager that has been durably deducted but whose visual round is still playing. */
    public static final class PendingRound {
        private final String id, game, result;
        private final long bet, payout, startedAt;
        private PendingRound(String id, String game, long bet, String result, long payout, long startedAt) {
            this.id = id; this.game = game; this.bet = bet; this.result = result; this.payout = payout; this.startedAt = startedAt;
        }
        public String getId() { return id; }
        public String getGame() { return game; }
        public long getBet() { return bet; }
        public String getResult() { return result; }
        public long getPayout() { return payout; }
        public long getStartedAt() { return startedAt; }
    }

    private SlotEconomy(Path file) { this.file = file; }

    public static SlotEconomy load(Path file) {
        SlotConfig.validate();
        SlotEconomy economy = new SlotEconomy(file);
        if (!Files.exists(file) && !Files.exists(backup(file))) {
            economy.save();
            return economy;
        }
        try {
            economy.read(file);
        } catch (Exception primary) {
            try {
                economy.read(backup(file));
                economy.saveError = "Recovered encrypted currency backup";
                if (Files.exists(file)) Files.copy(file, corrupt(file), StandardCopyOption.REPLACE_EXISTING);
                atomicWrite(file, Files.readAllBytes(backup(file)));
            } catch (Exception backupFailure) {
                economy.writeBlocked = true;
                economy.saveError = "Currency profile is unreadable; it was left untouched";
            }
        }
        return economy;
    }

    public synchronized long getBalance() { return balance; }
    public synchronized boolean isAdultVerified() { return adultVerified; }
    public synchronized String getSaveError() { return saveError; }
    public synchronized List<Transaction> getHistory() { return Collections.unmodifiableList(new ArrayList<Transaction>(history)); }
    public synchronized PendingRound getPendingRound() { return pendingRound; }

    /** Stores only the verified-adult flag, never the person's entered age. */
    public synchronized boolean confirmAdultAge(int age) {
        if (age < 18 || age > 120) return false;
        boolean previous = adultVerified;
        adultVerified = true;
        if (save()) return true;
        adultVerified = previous;
        return false;
    }

    public synchronized boolean canClaimAllowance(long now) {
        return adultVerified && clockIsValid(now) && (lastAllowanceAt == 0L || now - lastAllowanceAt >= SlotConfig.FREE_ALLOWANCE_COOLDOWN_MS);
    }

    public synchronized long nextAllowanceAt() { return lastAllowanceAt == 0L ? 0L : lastAllowanceAt + SlotConfig.FREE_ALLOWANCE_COOLDOWN_MS; }

    public synchronized Transaction claimAllowance(long now) {
        if (!canClaimAllowance(now)) return null;
        long oldBalance = balance, oldAllowance = lastAllowanceAt, oldClock = lastObservedClock;
        lastAllowanceAt = now; lastObservedClock = Math.max(lastObservedClock, now);
        balance = safeAdd(balance, SlotConfig.FREE_ALLOWANCE);
        Transaction transaction = transaction("Allowance", 0L, "Periodic virtual-token allowance", SlotConfig.FREE_ALLOWANCE, now);
        add(transaction);
        if (save()) return transaction;
        balance = oldBalance; lastAllowanceAt = oldAllowance; lastObservedClock = oldClock; history.remove(0);
        return null;
    }

    /** Locks a predetermined round, deducting only the wager until animation finishes. */
    public synchronized PendingRound beginRound(String game, int bet, String result, long payout, long now) {
        if (!adultVerified || pendingRound != null || game == null || game.length() > 48 || result == null || result.length() > 160
                || bet <= 0 || payout < 0L || balance < bet || !clockIsValid(now)) return null;
        long oldBalance = balance, oldClock = lastObservedClock;
        balance -= bet;
        lastObservedClock = Math.max(lastObservedClock, now);
        pendingRound = new PendingRound(UUID.randomUUID().toString(), game, bet, result, payout, now);
        if (save()) return pendingRound;
        balance = oldBalance; lastObservedClock = oldClock; pendingRound = null;
        return null;
    }

    /** Pays a previously locked outcome once, after the presentation completes. */
    public synchronized Transaction completeRound(String roundId, long now) {
        if (pendingRound == null || roundId == null || !roundId.equals(pendingRound.id) || !clockIsValid(now)) return null;
        PendingRound completed = pendingRound;
        long oldBalance = balance, oldClock = lastObservedClock;
        balance = safeAdd(balance, completed.payout);
        lastObservedClock = Math.max(lastObservedClock, now);
        pendingRound = null;
        Transaction transaction = new Transaction(UUID.randomUUID().toString(), completed.game, completed.bet, completed.result,
                completed.payout, now, completed.id);
        add(transaction);
        if (save()) return transaction;
        balance = oldBalance; lastObservedClock = oldClock; pendingRound = completed; history.remove(0);
        return null;
    }

    /** Updates the locked result for an interactive round before it is paid once. */
    public synchronized boolean updatePendingRound(String roundId, String result, long payout, long now) {
        if (pendingRound == null || roundId == null || !roundId.equals(pendingRound.id) || result == null || result.length() > 160
                || payout < 0L || !clockIsValid(now)) return false;
        PendingRound previous = pendingRound; long oldClock = lastObservedClock;
        pendingRound = new PendingRound(previous.id, previous.game, previous.bet, result, payout, previous.startedAt);
        lastObservedClock = Math.max(lastObservedClock, now);
        if (save()) return true;
        pendingRound = previous; lastObservedClock = oldClock;
        return false;
    }

    /** Adds an allowed blackjack double/split stake to the already locked virtual round. */
    public synchronized boolean increasePendingWager(String roundId, long additionalBet, long now) {
        if (pendingRound == null || roundId == null || !roundId.equals(pendingRound.id) || additionalBet <= 0L
                || balance < additionalBet || !clockIsValid(now)) return false;
        long oldBalance = balance, oldClock = lastObservedClock; PendingRound previous = pendingRound;
        balance -= additionalBet; lastObservedClock = Math.max(lastObservedClock, now);
        pendingRound = new PendingRound(previous.id, previous.game, Math.addExact(previous.bet, additionalBet), previous.result, previous.payout, previous.startedAt);
        if (save()) return true;
        balance = oldBalance; lastObservedClock = oldClock; pendingRound = previous;
        return false;
    }

    /** Compatibility convenience for non-animated callers and tests. */
    public synchronized Transaction resolveBookOfVibeSpin(int bet, BookOfVibeSlots.Outcome outcome, long now) {
        if (outcome == null) return null;
        PendingRound round = beginRound("Book of Vibe", bet, outcome.resultLabel(), outcome.getPayout(), now);
        return round == null ? null : completeRound(round.id, now);
    }

    private boolean clockIsValid(long now) {
        // A local client has no trustworthy server clock. Reject backwards
        // jumps so a clock rollback cannot bypass the persisted cooldown.
        return now >= lastObservedClock;
    }

    private Transaction transaction(String game, long bet, String result, long payout, long timestamp) {
        return new Transaction(UUID.randomUUID().toString(), game, bet, result, payout, timestamp, UUID.randomUUID().toString());
    }
    private void add(Transaction transaction) {
        history.add(0, transaction);
        while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
    }
    private static long safeAdd(long first, long second) {
        if (second < 0L || first > Long.MAX_VALUE - second) throw new IllegalStateException("Invalid virtual currency amount");
        return first + second;
    }

    private void read(Path candidate) throws IOException {
        byte[] bytes = Files.readAllBytes(candidate);
        if (!SlotSaveCodec.encrypted(bytes)) throw new IOException("Slots currency is not encrypted");
        JsonObject root = new JsonParser().parse(new String(SlotSaveCodec.decrypt(file, bytes), StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.get("version").getAsInt() != VERSION) throw new IOException("Unsupported Slots currency version");
        long loadedBalance = positive(root, "balance");
        long loadedAllowance = nonnegative(root, "lastAllowanceAt");
        long loadedClock = nonnegative(root, "lastObservedClock");
        if (loadedClock < loadedAllowance) throw new IOException("Invalid allowance timestamp");
        List<Transaction> loaded = new ArrayList<Transaction>();
        JsonArray entries = root.getAsJsonArray("history");
        if (entries != null) for (JsonElement element : entries) {
            JsonObject item = element.getAsJsonObject();
            long bet = nonnegative(item, "bet"), payout = nonnegative(item, "payout"), timestamp = nonnegative(item, "timestamp");
            String id = item.get("id").getAsString(), game = item.get("game").getAsString(), result = item.get("result").getAsString(), round = item.get("roundId").getAsString();
            if (id.length() > 64 || game.length() > 48 || result.length() > 160 || round.length() > 64) throw new IOException("Invalid transaction data");
            loaded.add(new Transaction(id, game, bet, result, payout, timestamp, round));
            if (loaded.size() > MAX_HISTORY) throw new IOException("Too much transaction history");
        }
        PendingRound loadedPending = null;
        if (root.has("pending")) {
            JsonObject item = root.getAsJsonObject("pending");
            long bet = nonnegative(item, "bet"), payout = nonnegative(item, "payout"), startedAt = nonnegative(item, "startedAt");
            String id = item.get("id").getAsString(), game = item.get("game").getAsString(), result = item.get("result").getAsString();
            if (id.length() > 64 || game.length() > 48 || result.length() > 160) throw new IOException("Invalid pending round");
            loadedPending = new PendingRound(id, game, bet, result, payout, startedAt);
        }
        balance = loadedBalance; lastAllowanceAt = loadedAllowance; lastObservedClock = loadedClock;
        adultVerified = root.get("adultVerified").getAsBoolean(); pendingRound = loadedPending; history.clear(); history.addAll(loaded);
    }

    private static long positive(JsonObject root, String key) throws IOException { long value = nonnegative(root, key); if (value < 0L) throw new IOException("Negative balance"); return value; }
    private static long nonnegative(JsonObject root, String key) throws IOException {
        try { long value = root.get(key).getAsLong(); if (value < 0L) throw new IOException("Negative " + key); return value; }
        catch (NumberFormatException | NullPointerException failure) { throw new IOException("Invalid " + key, failure); }
    }

    private boolean save() {
        if (writeBlocked) return false;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION); root.addProperty("balance", balance); root.addProperty("adultVerified", adultVerified);
            root.addProperty("lastAllowanceAt", lastAllowanceAt); root.addProperty("lastObservedClock", lastObservedClock);
            if (pendingRound != null) {
                JsonObject pending = new JsonObject();
                pending.addProperty("id", pendingRound.id); pending.addProperty("game", pendingRound.game); pending.addProperty("bet", pendingRound.bet);
                pending.addProperty("result", pendingRound.result); pending.addProperty("payout", pendingRound.payout); pending.addProperty("startedAt", pendingRound.startedAt);
                root.add("pending", pending);
            }
            JsonArray entries = new JsonArray();
            for (Transaction transaction : history) {
                JsonObject item = new JsonObject();
                item.addProperty("id", transaction.id); item.addProperty("game", transaction.game); item.addProperty("bet", transaction.bet);
                item.addProperty("result", transaction.result); item.addProperty("payout", transaction.payout); item.addProperty("timestamp", transaction.timestamp); item.addProperty("roundId", transaction.roundId);
                entries.add(item);
            }
            root.add("history", entries);
            byte[] previous = Files.exists(file) ? Files.readAllBytes(file) : null;
            if (previous != null) SlotSaveCodec.decrypt(file, previous);
            byte[] encrypted = SlotSaveCodec.encrypt(file, root.toString().getBytes(StandardCharsets.UTF_8));
            atomicWrite(backup(file), previous == null ? encrypted : previous);
            atomicWrite(file, encrypted);
            saveError = null;
            return true;
        } catch (Exception failure) {
            saveError = "Currency not saved: " + failure.getMessage();
            return false;
        }
    }

    static boolean encryptedFile(Path file) throws IOException { return SlotSaveCodec.encrypted(Files.readAllBytes(file)); }
    private static Path backup(Path file) { return file.resolveSibling(file.getFileName() + ".bak"); }
    private static Path corrupt(Path file) { return file.resolveSibling(file.getFileName() + ".corrupt"); }
    private static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer data = ByteBuffer.wrap(bytes); while (data.hasRemaining()) channel.write(data); channel.force(true);
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
