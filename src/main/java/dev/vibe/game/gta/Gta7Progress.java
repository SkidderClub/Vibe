package dev.vibe.game.gta;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.EnumSet;

/** Permanent bank and upgrades. Arbitrary precision keeps all uncapped upgrades and magazine sizes uncapped. */
public final class Gta7Progress {
    public enum Upgrade {
        KNIFE("Knife", "+15 damage", 30, false), AK47("AK47", "+8 damage", 45, false),
        HEALTH("Health", "+25 max HP", 40, false), SPEED("Walk speed", "+0.27 m/s", 60, true),
        FAST_SHOOTING("Fast shooting", "+10% fire rate", 75, true),
        PENETRATION("Bullet penetration", "+5% wall penetration chance", 85, true),
        MAX_AMMO("Max ammo", "+5 rounds per magazine", 55, false),
        JET_PACK("Jet pack", "+1 second of flight", 95, false);
        public final String title, bonus; public final int base; public final boolean limited;
        Upgrade(String title, String bonus, int base, boolean limited) { this.title=title; this.bonus=bonus; this.base=base; this.limited=limited; }
    }
    public enum Skin {
        STOCK("Original", 0, 0x986543, 0xD2E4DF), GOLD("Gold rush", 250, 0xD9AF4D, 0xF3D578),
        ARCTIC("Arctic", 350, 0xD7EAF3, 0x9FDCED), NEON("Neon circuit", 500, 0x59E0B3, 0xCD79FF),
        CRIMSON("Crimson", 650, 0xB7334D, 0xF06E7C);
        public final String title; public final int price, primary, secondary;
        Skin(String title, int price, int primary, int secondary) { this.title=title; this.price=price; this.primary=primary; this.secondary=secondary; }
    }
    public static final int MAX_SPEED_LEVEL = 10;
    private BigInteger xp = BigInteger.ZERO;
    private final BigInteger[] levels = new BigInteger[Upgrade.values().length];
    private final EnumSet<Skin> knifeSkins = EnumSet.of(Skin.STOCK), akSkins = EnumSet.of(Skin.STOCK);
    private final Skin[] equipped = {Skin.STOCK, Skin.STOCK};
    private final Path file;
    private boolean dirty;
    private String saveError;

    public Gta7Progress(Path file) { this.file = file; Arrays.fill(levels, BigInteger.ZERO); }

    public static Gta7Progress load(Path file) {
        Gta7Progress result = new Gta7Progress(file);
        if (!Files.exists(file) && !Files.exists(file.resolveSibling(file.getFileName() + ".bak"))) return result;
        try {
            result.read(file);
        } catch (Exception failure) {
            try {
                result.read(file.resolveSibling(file.getFileName() + ".bak"));
                result.saveError = "Recovered backup; damaged save retained as .corrupt";
                if (Files.exists(file)) Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
                // Never rotate a damaged primary over the known-good backup.
                atomicWrite(file, Files.readAllBytes(file.resolveSibling(file.getFileName() + ".bak")));
            } catch (Exception noBackup) {
                result = new Gta7Progress(file);
                result.saveError = "Progress unreadable; saving is disabled to protect your save and key";
                result.writeBlocked = true;
                try {
                    if(Files.exists(file))Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) { /* The original remains untouched. */ }

            }
        }
        if (result.dirty) result.save();
        return result;
    }

    private boolean writeBlocked, legacyMigration;

    private void read(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        boolean encrypted = Gta7SaveCodec.encrypted(bytes);
        if (encrypted) bytes = Gta7SaveCodec.decrypt(file, bytes);
        else if (Files.exists(Gta7SaveCodec.keyFile(file))) throw new IOException("Unencrypted saves are no longer accepted");
        JsonObject root = new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        int version = root.get("version").getAsInt();
        if (version != (encrypted ? 2 : 1)) throw new IOException("Unsupported GTA7 save version");
        BigInteger bank = nonnegative(root.get("xp").getAsString());
        BigInteger[] loaded = new BigInteger[levels.length];
        for (Upgrade upgrade : Upgrade.values()) {
            loaded[upgrade.ordinal()] = version == 1 && upgrade.ordinal() >= 4 ? BigInteger.ZERO : nonnegative(root.get(upgrade.name()).getAsString());
            if (upgrade.limited && loaded[upgrade.ordinal()].compareTo(BigInteger.TEN) > 0) throw new IOException("Invalid capped level");
        }
        EnumSet<Skin> knife = EnumSet.of(Skin.STOCK), ak = EnumSet.of(Skin.STOCK);
        Skin[] selected = {Skin.STOCK, Skin.STOCK};
        if (version == 2) for (int weapon = 0; weapon < 2; weapon++) {
            EnumSet<Skin> owned = weapon == 0 ? knife : ak;
            for (com.google.gson.JsonElement skin : root.getAsJsonArray("skins" + weapon)) owned.add(Skin.valueOf(skin.getAsString()));
            selected[weapon] = Skin.valueOf(root.get("equipped" + weapon).getAsString());
            if (!owned.contains(selected[weapon])) throw new IOException("Unowned equipped skin");
        }
        xp = bank;
        System.arraycopy(loaded, 0, levels, 0, levels.length);
        knifeSkins.clear(); knifeSkins.addAll(knife); akSkins.clear(); akSkins.addAll(ak);
        System.arraycopy(selected, 0, equipped, 0, 2);
        dirty = legacyMigration = !encrypted;
    }

    private static BigInteger nonnegative(String text) throws IOException {
        BigInteger value = new BigInteger(text);
        if (value.signum() < 0) throw new IOException("Negative progress value");
        return value;
    }

    public BigInteger xp() { return xp; }
    public BigInteger level(Upgrade upgrade) { return levels[upgrade.ordinal()]; }
    public boolean capped(Upgrade upgrade) { return upgrade.limited && level(upgrade).compareTo(BigInteger.TEN) >= 0; }
    public BigInteger cost(Upgrade upgrade) {
        return level(upgrade).add(BigInteger.ONE).pow(2).multiply(BigInteger.valueOf(upgrade.base));
    }

    public void award(BigInteger amount) {
        if (amount.signum() <= 0) return;
        xp = xp.add(amount);
        dirty = true;
    }

    public boolean purchase(Upgrade upgrade) {
        BigInteger price = cost(upgrade);
        if (capped(upgrade) || xp.compareTo(price) < 0) return false;
        xp = xp.subtract(price);
        levels[upgrade.ordinal()] = level(upgrade).add(BigInteger.ONE);
        dirty = true;
        save();
        return true;
    }

    public BigDecimal maxHealth() { return new BigDecimal(level(Upgrade.HEALTH).multiply(BigInteger.valueOf(25)).add(BigInteger.valueOf(100))); }
    public BigDecimal damage(int weapon) {
        return new BigDecimal(level(weapon == 0 ? Upgrade.KNIFE : Upgrade.AK47)
                .multiply(BigInteger.valueOf(weapon == 0 ? 15 : 8)).add(BigInteger.valueOf(weapon == 0 ? 45 : 28)));
    }
    public double walkSpeed() { return 4.3 + .27 * level(Upgrade.SPEED).intValue(); }
    public double fireInterval() { return .105 / (1 + .1 * level(Upgrade.FAST_SHOOTING).intValue()); }
    public double penetrationChance() { return .05 * level(Upgrade.PENETRATION).intValue(); }
    public BigInteger magazineSize() { return BigInteger.valueOf(30).add(level(Upgrade.MAX_AMMO).multiply(BigInteger.valueOf(5))); }
    public BigDecimal jetCapacity() { return new BigDecimal(level(Upgrade.JET_PACK)); }
    public Skin skin(int weapon) { return equipped[weapon]; }
    public boolean owns(int weapon, Skin skin) { return (weapon == 0 ? knifeSkins : akSkins).contains(skin); }
    public boolean purchaseSkin(int weapon, Skin skin) {
        if (weapon < 0 || weapon > 1) return false;
        if (!owns(weapon, skin)) {
            BigInteger price = BigInteger.valueOf(skin.price);
            if (xp.compareTo(price) < 0) return false;
            xp = xp.subtract(price); (weapon == 0 ? knifeSkins : akSkins).add(skin);
        }
        equipped[weapon] = skin; dirty = true; save(); return true;
    }
    public String saveError() { return saveError; }

    /** Write each pickup/purchase immediately, with atomic replacement and a previous-save backup. */
    public boolean save() {
        if (writeBlocked) return false;
        if (!dirty && Files.exists(file)) return true;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 2);
            root.addProperty("xp", xp.toString());
            for (Upgrade upgrade : Upgrade.values()) root.addProperty(upgrade.name(), level(upgrade).toString());
            for (int weapon = 0; weapon < 2; weapon++) {
                com.google.gson.JsonArray skins = new com.google.gson.JsonArray();
                for (Skin skin : weapon == 0 ? knifeSkins : akSkins) skins.add(new com.google.gson.JsonPrimitive(skin.name()));
                root.add("skins" + weapon, skins); root.addProperty("equipped" + weapon, equipped[weapon].name());
            }
            byte[] previous=Files.exists(file)?Files.readAllBytes(file):null;
            // Validate before touching either file. Missing/replaced keys and live edits must not erase a profile.
            if(previous!=null&&!legacyMigration)Gta7SaveCodec.decrypt(file,previous);
            byte[] encrypted = Gta7SaveCodec.encrypt(file, root.toString().getBytes(StandardCharsets.UTF_8));
            Path backup = file.resolveSibling(file.getFileName() + ".bak");
            atomicWrite(backup, previous!=null&&!legacyMigration?previous:encrypted);
            atomicWrite(file, encrypted);
            legacyMigration=false;
            dirty = false;
            saveError = null;
            return true;
        } catch (IOException failure) {
            saveError = "Progress not saved: " + failure.getMessage();
            return false;
        }
    }

    private static void atomicWrite(Path target,byte[] bytes) throws IOException {
        Path temporary=target.resolveSibling(target.getFileName()+".tmp");
        try {
            try(java.nio.channels.FileChannel channel=java.nio.channels.FileChannel.open(temporary,
                    java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,java.nio.file.StandardOpenOption.WRITE)){
                java.nio.ByteBuffer buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
            }
            try {Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
    }

    public static String format(BigInteger number) {
        String digits = number.toString();
        if (digits.length() <= 6) return digits;
        return digits.charAt(0) + "." + digits.substring(1, 3) + "e" + (digits.length() - 1);
    }
}
