package dev.vibe.statistics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;

/**
 * Local, authenticated statistics vault. The plaintext never reaches disk:
 * AES-GCM both encrypts the counters and rejects altered vault data.
 */
public final class StatisticsService {
    public enum Scope { GLOBAL, ACCOUNT, RANDOM }

    public static final class RankedAccount {
        public final String name;
        public final long playtime;
        RankedAccount(String name, long playtime) { this.name = name; this.playtime = playtime; }
    }

    public static final class Snapshot {
        public long playtime, kills, bedsBroken, joins, jumps, blocksWalked, blocksPlaced, blocksBroken;
        public long launches, gtaKills, battlefrontKills, gtaPlaytime, battlefrontPlaytime;
        public long configsLoaded, configsCreated, configsDeleted, modulesToggled;
        public String account = "Unknown", lastServer = "Offline";
        public final Map<String, Long> servers = new LinkedHashMap<String, Long>();
        public final List<Long> activity = new ArrayList<Long>();
        public final List<RankedAccount> favourites = new ArrayList<RankedAccount>();
    }

    private static final String GLOBAL = "g.";
    private static final String RANDOM = "r.";
    private final Minecraft minecraft;
    private final Path directory;
    private final Path vault;
    private final Path keyFile;
    private final Properties values = new Properties();
    private final Map<EntityLivingBase, Long> attacked = new IdentityHashMap<EntityLivingBase, Long>();
    private final Map<BlockPos, BlockWatch> watchedBlocks = new HashMap<BlockPos, BlockWatch>();
    /** Netty receives the final dig packet off-thread; tick consumes it on the client thread. */
    private final Queue<BlockPos> confirmedBreaks = new ConcurrentLinkedQueue<BlockPos>();
    private final SecureRandom random = new SecureRandom();
    private SecretKeySpec key;
    private String sampledAccount = "";
    private String server = "";
    private long lastTick, lastSaved;
    private int sessionKills;
    private boolean movementSampled;
    private boolean previousOnGround;
    private double lastX, lastY, lastZ, walkedRemainder;
    private boolean dirty;

    private static final class BlockWatch {
        private final Block original;
        private final boolean placement;
        private final long expires;
        private BlockWatch(Block original, boolean placement, long expires) {
            this.original = original;
            this.placement = placement;
            this.expires = expires;
        }
    }

    public StatisticsService(Minecraft minecraft, Path directory) {
        this.minecraft = minecraft;
        this.directory = directory;
        this.vault = directory.resolve("statistics.vault");
        this.keyFile = directory.resolve("statistics.key");
        load();
    }

    public synchronized void recordLaunch() { addGlobal("clientLaunches", 1L); saveNow(); }
    public synchronized void recordRandomCracked(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) return;
        values.setProperty("randomName." + encode(name), name);
        dirty = true; saveNow();
    }
    public synchronized void recordConfigLoad() { addGlobal("configsLoaded", 1L); }
    public synchronized void recordConfigCreated() { addGlobal("configsCreated", 1L); }
    public synchronized void recordConfigDeleted() { addGlobal("configsDeleted", 1L); }
    public synchronized void recordModuleToggle() { addGlobal("modulesToggled", 1L); }
    public synchronized void recordGtaKill() { addGlobal("gtaKills", 1L); }
    public synchronized void recordBattlefrontKill() { addGlobal("battlefrontKills", 1L); }
    public synchronized int getSessionKills() { return sessionKills; }

    /** Call from the normal client tick. It records only deltas, never packets. */
    public synchronized void tick() {
        long now = System.currentTimeMillis();
        updateProfileAndServer();
        collectDeaths(now);
        sampleMovementAndBlocks(now);
        if (lastTick == 0L) { lastTick = now; return; }
        long seconds = Math.min(15L, Math.max(0L, (now - lastTick) / 1000L));
        if (seconds > 0L) {
            lastTick += seconds * 1000L;
            addProfile("playtime", seconds);
            String screen = minecraft.currentScreen == null ? "" : minecraft.currentScreen.getClass().getName();
            if (screen.endsWith("Gta7Gui")) addGlobal("gtaPlaytime", seconds);
            if (screen.endsWith("Battlefront3Gui")) addGlobal("battlefrontPlaytime", seconds);
        }
        if (dirty && now - lastSaved >= 10000L) saveNow();
    }

    public synchronized void recordAttack(EntityLivingBase target) {
        if (target != null) attacked.put(target, System.currentTimeMillis() + 15000L);
    }

    /** Records a client initiated break and confirms it only once the block disappears. */
    public synchronized void watchBlockBreak(BlockPos position) { watchBlock(position, false); }

    /** Records a client initiated placement and confirms it only once the world changes. */
    public synchronized void watchBlockPlacement(BlockPos position) { watchBlock(position, true); }

    /**
     * C07 STOP_DESTROY_BLOCK is emitted only once vanilla has completed the
     * local destroy action. It is the reliable counterpart to the initial
     * left-click event, which may occur seconds before the block is gone.
     */
    public void recordDigging(C07PacketPlayerDigging packet) {
        if (packet != null && packet.getStatus() == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK && packet.getPosition() != null) {
            confirmedBreaks.offer(packet.getPosition());
        }
    }

    private void watchBlock(BlockPos position, boolean placement) {
        if (position == null || minecraft.theWorld == null) return;
        Block original = minecraft.theWorld.getBlockState(position).getBlock();
        BlockWatch existing = watchedBlocks.get(position);
        // PlayerInteract fires while mining is held. Replacing the watch on
        // every click used to push its timeout forward forever, so the final
        // air state was never credited as a break.
        if (existing != null && existing.placement == placement && existing.original == original) return;
        watchedBlocks.put(position, new BlockWatch(original, placement, System.currentTimeMillis() + 20000L));
    }

    public synchronized Snapshot snapshot(Scope scope) { return snapshot(scope, currentAccount()); }

    /** Accounts are retained independently, so the dashboard can inspect a past login. */
    public synchronized Snapshot snapshot(Scope scope, String account) {
        String selected = account == null || account.trim().isEmpty() ? currentAccount() : account.trim();
        String prefix = scope == Scope.GLOBAL ? GLOBAL : scope == Scope.RANDOM ? RANDOM : accountPrefix(selected);
        Snapshot result = new Snapshot();
        result.account = scope == Scope.GLOBAL ? "All accounts" : scope == Scope.RANDOM ? "Random cracked accounts" : selected;
        result.playtime = get(prefix, "playtime"); result.kills = get(prefix, "kills");
        result.bedsBroken = get(prefix, "bedsBroken"); result.joins = get(prefix, "joins");
        result.jumps = get(prefix, "jumps"); result.blocksWalked = get(prefix, "blocksWalked");
        result.blocksPlaced = get(prefix, "blocksPlaced"); result.blocksBroken = get(prefix, "blocksBroken");
        result.lastServer = values.getProperty(prefix + "lastServer", "Offline");
        if (scope == Scope.GLOBAL) {
            result.launches = get(GLOBAL, "clientLaunches"); result.gtaKills = get(GLOBAL, "gtaKills");
            result.battlefrontKills = get(GLOBAL, "battlefrontKills"); result.gtaPlaytime = get(GLOBAL, "gtaPlaytime");
            result.battlefrontPlaytime = get(GLOBAL, "battlefrontPlaytime"); result.configsLoaded = get(GLOBAL, "configsLoaded");
            result.configsCreated = get(GLOBAL, "configsCreated"); result.configsDeleted = get(GLOBAL, "configsDeleted");
            result.modulesToggled = get(GLOBAL, "modulesToggled"); addFavourites(result);
        }
        for (String property : values.stringPropertyNames()) {
            if (property.startsWith(prefix + "server.")) result.servers.put(property.substring((prefix + "server.").length()), parse(values.getProperty(property)));
        }
        Collections.sort(new ArrayList<String>(result.servers.keySet()));
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat day = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
        for (int i = 6; i >= 0; i--) { calendar.setTime(new Date()); calendar.add(Calendar.DATE, -i); result.activity.add(get(prefix, "day." + day.format(calendar.getTime()))); }
        return result;
    }

    public synchronized List<String> getAccounts() {
        List<String> accounts = new ArrayList<String>();
        for (String property : values.stringPropertyNames()) {
            if (!property.startsWith("accountName.")) continue;
            String name = values.getProperty(property);
            if (name != null && !name.trim().isEmpty() && !accounts.contains(name)) accounts.add(name);
        }
        String current = currentAccount();
        if (!accounts.contains(current)) accounts.add(current);
        Collections.sort(accounts, String.CASE_INSENSITIVE_ORDER);
        return accounts;
    }

    private void collectDeaths(long now) {
        Iterator<Map.Entry<EntityLivingBase, Long>> iterator = attacked.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<EntityLivingBase, Long> entry = iterator.next();
            EntityLivingBase target = entry.getKey();
            if (target == null || now > entry.getValue()) { iterator.remove(); continue; }
            if (target.isDead || !target.isEntityAlive()) { addProfile("kills", 1L); sessionKills++; iterator.remove(); }
        }
    }

    private void updateProfileAndServer() {
        String account = currentAccount();
        if (!account.equals(sampledAccount)) { sampledAccount = account; server = ""; resetSamples(); }
        String nextServer = serverName();
        if (!nextServer.equals(server)) {
            server = nextServer;
            String prefix = accountPrefix(account);
            values.setProperty(prefix + "lastServer", server.isEmpty() ? "Offline" : server);
            values.setProperty(GLOBAL + "lastServer", server.isEmpty() ? "Offline" : server);
            if (!server.isEmpty()) addProfile("joins", 1L);
            dirty = true;
        }
    }

    /**
     * Vanilla StatFileWriter is server supplied and often deliberately absent
     * on multiplayer servers. Track confirmed local state changes instead.
     */
    private void sampleMovementAndBlocks(long now) {
        if (minecraft.thePlayer == null || minecraft.theWorld == null) {
            movementSampled = false;
            watchedBlocks.clear();
            return;
        }
        if (!movementSampled) {
            movementSampled = true;
            previousOnGround = minecraft.thePlayer.onGround;
            lastX = minecraft.thePlayer.posX;
            lastY = minecraft.thePlayer.posY;
            lastZ = minecraft.thePlayer.posZ;
        } else {
            double dx = minecraft.thePlayer.posX - lastX;
            double dy = minecraft.thePlayer.posY - lastY;
            double dz = minecraft.thePlayer.posZ - lastZ;
            double walked = Math.sqrt(dx * dx + dz * dz);
            // Ignore teleports, respawns and server corrections.
            if (walked <= 4.0D && Math.abs(dy) <= 6.0D) {
                walkedRemainder += walked;
                long wholeBlocks = (long) Math.floor(walkedRemainder);
                if (wholeBlocks > 0L) {
                    addProfile("blocksWalked", wholeBlocks);
                    walkedRemainder -= wholeBlocks;
                }
            }
            boolean jumping = previousOnGround && !minecraft.thePlayer.onGround && minecraft.thePlayer.motionY > .08D
                    && minecraft.thePlayer.movementInput != null && minecraft.thePlayer.movementInput.jump;
            if (jumping) addProfile("jumps", 1L);
            previousOnGround = minecraft.thePlayer.onGround;
            lastX = minecraft.thePlayer.posX;
            lastY = minecraft.thePlayer.posY;
            lastZ = minecraft.thePlayer.posZ;
        }
        confirmCompletedBreaks();
        Iterator<Map.Entry<BlockPos, BlockWatch>> iterator = watchedBlocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, BlockWatch> entry = iterator.next();
            BlockWatch watch = entry.getValue();
            Block current = minecraft.theWorld.getBlockState(entry.getKey()).getBlock();
            if (watch.placement) {
                if (current != watch.original && current != Blocks.air) {
                    addProfile("blocksPlaced", 1L);
                    iterator.remove();
                } else if (now > watch.expires) iterator.remove();
            } else {
                if (current == Blocks.air && watch.original != Blocks.air) {
                    addProfile("blocksBroken", 1L);
                    if (watch.original == Blocks.bed) addProfile("bedsBroken", 1L);
                    iterator.remove();
                } else if (now > watch.expires) iterator.remove();
            }
        }
    }
    /** Consume one final destroy acknowledgement per position and remove its old watch
     * before the fallback state sampler can count it a second time. */
    private void confirmCompletedBreaks() {
        BlockPos position;
        while ((position = confirmedBreaks.poll()) != null) {
            BlockWatch watch = watchedBlocks.remove(position);
            Block broken = watch == null ? minecraft.theWorld.getBlockState(position).getBlock() : watch.original;
            addProfile("blocksBroken", 1L);
            if (broken == Blocks.bed) addProfile("bedsBroken", 1L);
        }
    }
    private void addProfile(String metric, long amount) {
        add(GLOBAL, metric, amount); String profile = accountPrefix(currentAccount()); add(profile, metric, amount);
        if (isRandom(currentAccount())) add(RANDOM, metric, amount);
        if ("playtime".equals(metric) && !server.isEmpty()) {
            add(GLOBAL, "server." + server, amount); add(profile, "server." + server, amount);
            if (isRandom(currentAccount())) add(RANDOM, "server." + server, amount);
            String day = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
            add(GLOBAL, "day." + day, amount); add(profile, "day." + day, amount);
            if (isRandom(currentAccount())) add(RANDOM, "day." + day, amount);
        }
    }

    private void addGlobal(String metric, long amount) { add(GLOBAL, metric, amount); }
    private void add(String prefix, String metric, long amount) { if (amount <= 0L) return; values.setProperty(prefix + metric, Long.toString(get(prefix, metric) + amount)); dirty = true; }
    private long get(String prefix, String metric) { return parse(values.getProperty(prefix + metric)); }
    private long parse(String value) { try { return Math.max(0L, Long.parseLong(value)); } catch (Exception ignored) { return 0L; } }

    private String currentAccount() {
        try { String name = minecraft.getSession() == null ? null : minecraft.getSession().getUsername(); return name == null || name.trim().isEmpty() ? "Offline" : name.trim(); }
        catch (Exception ignored) { return "Offline"; }
    }
    private String accountPrefix(String account) {
        String encoded = encode(account); values.setProperty("accountName." + encoded, account); return "a." + encoded + ".";
    }
    private boolean isRandom(String account) { return values.containsKey("randomName." + encode(account)); }
    private String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }

    private String serverName() {
        if (minecraft.theWorld == null || minecraft.isIntegratedServerRunning()) return "";
        ServerData data = minecraft.getCurrentServerData();
        if (data == null || data.serverIP == null) return "";
        String host = data.serverIP.trim().toLowerCase(Locale.ROOT).replaceFirst(":\\d+$", "");
        return host.matches("[^.]+\\.[^.]+\\.liquidproxy\\.net") ? "liquidproxy" : host;
    }
    private void resetSamples() {
        lastTick = 0L;
        movementSampled = false;
        walkedRemainder = 0.0D;
        watchedBlocks.clear();
        confirmedBreaks.clear();
    }

    private void addFavourites(Snapshot snapshot) {
        for (String property : values.stringPropertyNames()) if (property.startsWith("accountName.")) {
            String encoded = property.substring("accountName.".length()); String name = values.getProperty(property);
            snapshot.favourites.add(new RankedAccount(name, get("a." + encoded + ".", "playtime")));
        }
        Collections.sort(snapshot.favourites, new Comparator<RankedAccount>() { public int compare(RankedAccount a, RankedAccount b) { return Long.compare(b.playtime, a.playtime); } });
        if (snapshot.favourites.size() > 100) snapshot.favourites.subList(100, snapshot.favourites.size()).clear();
    }

    private void load() {
        try {
            Files.createDirectories(directory);
            byte[] bytes;
            if (Files.isRegularFile(keyFile) && Files.size(keyFile) == 16L) bytes = Files.readAllBytes(keyFile);
            else { bytes = new byte[16]; random.nextBytes(bytes); Files.write(keyFile, bytes); }
            key = new SecretKeySpec(bytes, "AES");
            if (!Files.isRegularFile(vault)) return;
            byte[] encrypted = Files.readAllBytes(vault); if (encrypted.length < 16) return;
            byte[] iv = java.util.Arrays.copyOfRange(encrypted, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            values.load(new ByteArrayInputStream(cipher.doFinal(encrypted, 12, encrypted.length - 12)));
        } catch (Exception ignored) { values.clear(); }
    }

    private void saveNow() {
        if (!dirty || key == null) return;
        try {
            ByteArrayOutputStream plain = new ByteArrayOutputStream(); values.store(plain, "Vibe statistics vault");
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] payload = cipher.doFinal(plain.toByteArray()), combined = new byte[iv.length + payload.length];
            System.arraycopy(iv, 0, combined, 0, iv.length); System.arraycopy(payload, 0, combined, iv.length, payload.length);
            Path temp = directory.resolve(".statistics.vault.tmp"); Files.write(temp, combined);
            try { Files.move(temp, vault, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (Exception ignored) { Files.move(temp, vault, StandardCopyOption.REPLACE_EXISTING); }
            dirty = false; lastSaved = System.currentTimeMillis();
        } catch (Exception ignored) { }
    }
}
