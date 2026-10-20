package dev.vibe.game.gta8;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Money, weapons, ammunition, armour and career statistics. Saved with authenticated AES-GCM next to a
 * local key; a previous copy is kept as a backup and used if the primary is damaged.
 */
public final class Gta8Progress {
    private static final byte[] HEADER = "GTA8SAVE1".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();
    public long money = 2500;
    public final Map<Gta8Weapon, Integer> ammo = new EnumMap<Gta8Weapon, Integer>(Gta8Weapon.class);
    public int armor;
    public long kills, copKills, deaths, arrests, shots, hits, headshots, robberies, missions, carsStolen, maxWanted, crashes;
    public double distanceDriven, distanceWalked, playSeconds, hours = 9.5;
    private final Path file;
    private boolean blocked;
    private String error;

    public Gta8Progress(Path file) {
        this.file = file;
        ammo.put(Gta8Weapon.FISTS, 0);
        ammo.put(Gta8Weapon.PISTOL, 60);
    }

    public static Gta8Progress load(Path file) {
        Gta8Progress p = new Gta8Progress(file);
        Path backup = file.resolveSibling(file.getFileName() + ".bak");
        if (!Files.exists(file) && !Files.exists(backup)) return p;
        try { p.read(file); }
        catch (Exception primary) {
            try {
                p.read(backup);
                p.error = "Recovered GTA8 progress from backup";
                if (Files.exists(file)) Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception both) {
                p = new Gta8Progress(file);
                p.blocked = true;
                p.error = "GTA8 progress unreadable; saving disabled to protect it";
            }
        }
        return p;
    }

    private void read(Path path) throws IOException {
        byte[] plain = decrypt(file, Files.readAllBytes(path));
        JsonObject o = new JsonParser().parse(new String(plain, StandardCharsets.UTF_8)).getAsJsonObject();
        if (o.get("version").getAsInt() != 1) throw new IOException("Unsupported GTA8 save version");
        money = Math.max(0, o.get("money").getAsLong());
        armor = (int) Gta8Math.clamp(o.get("armor").getAsInt(), 0, 100);
        ammo.clear();
        JsonObject weapons = o.getAsJsonObject("weapons");
        for (Gta8Weapon w : Gta8Weapon.values()) if (weapons.has(w.name())) ammo.put(w, Math.max(0, weapons.get(w.name()).getAsInt()));
        ammo.put(Gta8Weapon.FISTS, 0);
        JsonObject s = o.getAsJsonObject("stats");
        kills = s.get("kills").getAsLong(); copKills = s.get("copKills").getAsLong(); deaths = s.get("deaths").getAsLong();
        arrests = s.get("arrests").getAsLong(); shots = s.get("shots").getAsLong(); hits = s.get("hits").getAsLong();
        headshots = s.get("headshots").getAsLong(); robberies = s.get("robberies").getAsLong(); missions = s.get("missions").getAsLong();
        carsStolen = s.get("carsStolen").getAsLong(); maxWanted = s.get("maxWanted").getAsLong(); crashes = s.get("crashes").getAsLong();
        distanceDriven = s.get("distanceDriven").getAsDouble(); distanceWalked = s.get("distanceWalked").getAsDouble();
        playSeconds = s.get("playSeconds").getAsDouble();
        hours = Gta8Math.clamp(o.get("hours").getAsDouble(), 0, 24);
    }

    public boolean save() {
        if (blocked) return false;
        try {
            JsonObject o = new JsonObject();
            o.addProperty("version", 1);
            o.addProperty("money", money);
            o.addProperty("armor", armor);
            o.addProperty("hours", hours);
            JsonObject weapons = new JsonObject();
            for (Map.Entry<Gta8Weapon, Integer> e : ammo.entrySet()) weapons.addProperty(e.getKey().name(), e.getValue());
            o.add("weapons", weapons);
            JsonObject s = new JsonObject();
            s.addProperty("kills", kills); s.addProperty("copKills", copKills); s.addProperty("deaths", deaths); s.addProperty("arrests", arrests);
            s.addProperty("shots", shots); s.addProperty("hits", hits); s.addProperty("headshots", headshots); s.addProperty("robberies", robberies);
            s.addProperty("missions", missions); s.addProperty("carsStolen", carsStolen); s.addProperty("maxWanted", maxWanted); s.addProperty("crashes", crashes);
            s.addProperty("distanceDriven", distanceDriven); s.addProperty("distanceWalked", distanceWalked); s.addProperty("playSeconds", playSeconds);
            o.add("stats", s);
            Files.createDirectories(file.toAbsolutePath().getParent());
            byte[] previous = Files.exists(file) ? Files.readAllBytes(file) : null;
            if (previous != null) decrypt(file, previous);
            byte[] encrypted = encrypt(file, o.toString().getBytes(StandardCharsets.UTF_8));
            atomicWrite(file.resolveSibling(file.getFileName() + ".bak"), previous != null ? previous : encrypted);
            atomicWrite(file, encrypted);
            error = null;
            return true;
        } catch (IOException e) {
            error = "GTA8 progress not saved: " + e.getMessage();
            return false;
        }
    }
    public String error() { return error; }

    public boolean owns(Gta8Weapon w) { return ammo.containsKey(w); }
    public int ammo(Gta8Weapon w) { Integer a = ammo.get(w); return a == null ? 0 : a; }
    public boolean spend(long amount) { if (amount < 0 || money < amount) return false; money -= amount; return true; }

    // ------------------------------------------------------------------ authenticated encryption
    static Path keyFile(Path save) { return save.toAbsolutePath().resolveSibling(".gta8-save.key"); }
    private static byte[] key(Path save, boolean create) throws IOException {
        Path path = keyFile(save);
        if (!Files.exists(path)) {
            if (!create || Files.exists(save)) throw new IOException("Missing .gta8-save.key");
            byte[] k = new byte[16];
            RANDOM.nextBytes(k);
            Files.createDirectories(path.getParent());
            Files.write(path, k, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        byte[] k = Files.readAllBytes(path);
        if (k.length != 16) throw new IOException("Invalid GTA8 save key");
        return k;
    }
    static byte[] encrypt(Path save, byte[] plain) throws IOException {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(save, true), "AES"), new GCMParameterSpec(128, iv));
            c.updateAAD(HEADER);
            byte[] payload = c.doFinal(plain);
            return ByteBuffer.allocate(HEADER.length + 12 + payload.length).put(HEADER).put(iv).put(payload).array();
        } catch (GeneralSecurityException e) { throw new IOException("Could not encrypt GTA8 progress", e); }
    }
    static byte[] decrypt(Path save, byte[] bytes) throws IOException {
        if (bytes.length < HEADER.length + 28 || !Arrays.equals(HEADER, Arrays.copyOf(bytes, HEADER.length))) throw new IOException("Not a GTA8 save");
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(save, false), "AES"), new GCMParameterSpec(128, Arrays.copyOfRange(bytes, HEADER.length, HEADER.length + 12)));
            c.updateAAD(HEADER);
            return c.doFinal(bytes, HEADER.length + 12, bytes.length - HEADER.length - 12);
        } catch (GeneralSecurityException e) { throw new IOException("GTA8 save authentication failed", e); }
    }
    private static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.write(tmp, bytes);
            try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
