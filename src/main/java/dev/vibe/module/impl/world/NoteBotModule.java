package dev.vibe.module.impl.world;

import dev.vibe.Vibe;
import dev.vibe.chat.ClientChat;
import dev.vibe.combat.RotationMath;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.AutoBuffModule;
import dev.vibe.module.impl.movement.FreecamModule;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.notebot.NoteBlockPlan;
import dev.vibe.notebot.NoteSong;
import dev.vibe.notebot.NoteSong.Instrument;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.StringSetting;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityNote;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/** Meteor NoteBot's scan, tune and play workflow adapted to Forge 1.8.9. */
public final class NoteBotModule extends Module {
    public final StringSetting filename = addSetting(new StringSetting("Song", "", 255, null));
    public final ModeSetting instrumentMode = addSetting(new ModeSetting("Instruments", "Exact", "Exact", "Any"));
    public final ModeSetting unsupported = addSetting(new ModeSetting("New Instruments", "Use Harp", "Use Harp", "Skip"));
    public final BooleanSetting fitOctaves = addSetting(new BooleanSetting("Fit Octaves", true));
    public final NumberSetting speed = addSetting(new NumberSetting("Playback Speed", 1, .25, 2, .05));
    public final BooleanSetting loop = addSetting(new BooleanSetting("Loop", false));
    public final BooleanSetting polyphonic = addSetting(new BooleanSetting("Polyphonic", true));
    public final NumberSetting range = addSetting(new NumberSetting("Range", 4.25, 1, 4.5, .05));
    public final BooleanSetting autoTune = addSetting(new BooleanSetting("Auto Tune", true));
    public final BooleanSetting skipMissing = addSetting(new BooleanSetting("Skip Missing Notes", true));
    public final NumberSetting tuneDelay = addSetting(new NumberSetting("Tune Delay (ticks)", 1, 1, 20, 1, autoTune::isEnabled));
    public final NumberSetting responseTimeout = addSetting(new NumberSetting("Response Timeout (ticks)", 30, 5, 100, 1));
    public final BooleanSetting rotate = addSetting(new BooleanSetting("Rotate", true));
    public final NumberSetting rotationSpeed = addSetting(new NumberSetting("Rotation Speed", 180, 5, 180, 1, rotate::isEnabled));
    public final NumberSetting chordSize = addSetting(new NumberSetting("Notes per Tick", 4, 1, 8, 1,
            () -> polyphonic.isEnabled() && !rotate.isEnabled()));
    public final BooleanSetting swing = addSetting(new BooleanSetting("Swing", true));
    public final NumberSetting previewVolume = addSetting(new NumberSetting("Preview Volume", 70, 0, 100, 1));

    private enum Stage { LOADING, SCANNING, TUNING, PLAYING }
    private enum Action { PROBE, TUNE, PLAY }
    private static final Instrument[] INSTRUMENTS = Instrument.values();
    private static final Instrument[] BLOCK_INSTRUMENTS = {
            Instrument.HARP, Instrument.BASS_DRUM, Instrument.SNARE, Instrument.HAT, Instrument.BASS
    };

    private final Map<BlockPos, Reading> readings = new LinkedHashMap<>();
    private final Set<Integer> requirements = new LinkedHashSet<>();
    private final ArrayDeque<BlockPos> probes = new ArrayDeque<>();
    private final ArrayDeque<BlockPos> due = new ArrayDeque<>();
    private Map<Integer, BlockPos> blocks = Collections.emptyMap();
    private Iterator<Map.Entry<Integer, BlockPos>> tuneQueue;
    private Map.Entry<Integer, BlockPos> tuneTarget;
    private CompletableFuture<NoteSong> loading;
    private NoteSong song;
    private Stage stage;
    private WorldClient world;
    private EntityPlayerSP player;
    private boolean preview, anyInstrument, autoStart;
    private int clock, songTick, tuneAttempts, nextTune;
    private Action planned;
    private BlockPos target, awaiting;
    private int deadline;

    public NoteBotModule() {
        super("NoteBot", "Tunes and plays songs on nearby note blocks", Category.WORLD, Keyboard.KEY_NONE);
    }

    public File folder() {
        return new File(new File(Minecraft.getMinecraft().mcDataDir, "vibe"), "notebot");
    }

    public List<String> songs() {
        File[] files = folder().listFiles(file -> file.getName().toLowerCase(Locale.ROOT).endsWith(".nbs")
                && Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS));
        if (files == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (File file : files) names.add(file.getName());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public void start(String name, boolean preview) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || (!preview && !canPlayInWorld(mc))) {
            message(preview ? "Join a world to preview a song." : "World playback requires a survival player who can edit blocks.");
            return;
        }
        String wanted = name.trim();
        String match = null;
        for (String file : songs()) {
            if (file.equalsIgnoreCase(wanted) || file.equalsIgnoreCase(wanted + ".nbs")) {
                match = file;
                break;
            }
        }
        if (match == null) {
            message("Song not found. Put an .nbs file in vibe/notebot and use .notebot list.");
            return;
        }
        reset();
        setEnabled(true);
        autoStart = false;
        filename.setValue(match);
        this.preview = preview;
        anyInstrument = !preview && instrumentMode.is("Any");
        world = mc.theWorld;
        player = mc.thePlayer;
        File file = new File(folder(), match);
        double playbackSpeed = speed.getDouble();
        boolean fold = fitOctaves.isEnabled();
        boolean fallback = anyInstrument || unsupported.is("Use Harp");
        loading = CompletableFuture.supplyAsync(() -> {
            try { return NoteSong.read(file.toPath(), playbackSpeed, fold, fallback); }
            catch (IOException error) { throw new CompletionException(error); }
        });
        stage = Stage.LOADING;
        message("Loading " + match);
    }

    @Override protected void onEnable() { autoStart = !isConfigLoading(); }
    @Override protected void onDisable() { reset(); }

    public void stop() {
        if (isEnabled()) finish("Stopped.");
    }

    private void finish(String reason) {
        message(reason);
        setEnabled(false);
    }

    private void reset() {
        MoveFixModule fix = moveFix();
        if (fix != null) fix.discardOwnedState(getId());
        if (loading != null) loading.cancel(false);
        loading = null;
        song = null;
        stage = null;
        world = null;
        player = null;
        planned = null;
        target = awaiting = null;
        blocks = Collections.emptyMap();
        tuneQueue = null;
        tuneTarget = null;
        probes.clear();
        due.clear();
        readings.clear();
        requirements.clear();
        clock = songTick = tuneAttempts = nextTune = 0;
        autoStart = false;
    }

    public void tickStart() {
        if (!isEnabled()) return;
        planned = null;
        MoveFixModule fix = moveFix();
        if (fix != null) fix.clearFakeRotation(getId());
        if (autoStart) {
            autoStart = false;
            if (filename.getValue().trim().isEmpty()) {
                finish("Choose a song with .notebot play <song> or set Song in ClickGUI.");
                return;
            }
            start(filename.getValue(), false);
            if (stage == null) setEnabled(false);
        }
        if (stage == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.theWorld || player != mc.thePlayer || !player.isEntityAlive()) {
            finish("Playback ended when the player or world changed.");
            return;
        }
        if (stage == Stage.LOADING) {
            if (!loading.isDone()) return;
            try { song = loading.join(); }
            catch (CompletionException error) {
                String detail = error.getCause().getMessage();
                finish("Could not read song. " + (detail == null ? "The file is incomplete or unreadable." : detail));
                return;
            }
            loading = null;
            if (song.skippedNotes > 0) message("Skipped " + song.skippedNotes + " notes outside the current 1.8.9 settings.");
            if (preview) {
                stage = Stage.PLAYING;
                message("Previewing " + song.name);
            } else {
                for (List<NoteSong.Note> beat : song.beats().values()) {
                    for (NoteSong.Note note : beat) requirements.add(note.key(anyInstrument));
                }
                scan();
                stage = Stage.SCANNING;
            }
        }
        if (mc.isGamePaused()) return;
        if (preview) {
            previewTick(mc);
            return;
        }
        if (!canPlayInWorld(mc)) {
            finish("World playback requires a survival player who can edit blocks.");
            return;
        }
        clock++;
        if (!available(mc) || fix == null || fix.hasOtherRotationOwner(getId())) return;

        if (awaiting != null) {
            if (clock < deadline) return;
            BlockPos failed = awaiting;
            awaiting = null;
            if (stage == Stage.TUNING) {
                finish("No tuning response at " + coordinates(failed) + ".");
                return;
            }
        }

        switch (stage) {
            case SCANNING:
                while (!probes.isEmpty() && (reading(probes.peek()) != null || hit(probes.peek()) == null)) probes.remove();
                if (!probes.isEmpty()) {
                    prepare(Action.PROBE, probes.peek(), fix);
                    return;
                }
                assignBlocks();
                break;
            case TUNING:
                if (clock >= nextTune) tune(fix);
                break;
            case PLAYING:
                if (due.isEmpty()) queueBeat();
                if (!due.isEmpty()) prepare(Action.PLAY, due.peek(), fix);
                break;
            default:
                break;
        }
    }

    private void scan() {
        int radius = MathHelper.ceiling_double_int(reach());
        BlockPos origin = new BlockPos(player.getPositionEyes(1));
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.getAllInBox(origin.add(-radius, -radius, -radius), origin.add(radius, radius, radius))) {
            if (!validBlock(pos)) continue;
            Instrument instrument = instrument(pos);
            if (!anyInstrument && requirements.stream().noneMatch(key -> key / 25 == instrument.ordinal())) continue;
            if (hit(pos) != null) candidates.add(pos);
        }
        Vec3 eye = player.getPositionEyes(1);
        candidates.sort(Comparator.comparingDouble(pos -> eye.squareDistanceTo(center(pos))));
        probes.addAll(candidates);
        message("Checking " + candidates.size() + " reachable note blocks.");
    }

    private void assignBlocks() {
        List<NoteBlockPlan.Block<BlockPos>> candidates = new ArrayList<>();
        for (Map.Entry<BlockPos, Reading> entry : readings.entrySet()) {
            BlockPos pos = entry.getKey();
            Reading reading = reading(pos);
            if (reading != null && hit(pos) != null) {
                candidates.add(new NoteBlockPlan.Block<>(pos, reading.instrument, reading.pitch));
            }
        }
        blocks = NoteBlockPlan.assign(requirements, candidates, anyInstrument, autoTune.isEnabled());
        if (blocks.size() != requirements.size()) {
            StringBuilder missing = new StringBuilder();
            int shown = 0;
            for (int key : requirements) {
                if (blocks.containsKey(key)) continue;
                if (shown++ == 5) break;
                if (missing.length() != 0) missing.append(", ");
                if (!anyInstrument) missing.append(INSTRUMENTS[key / 25].name().toLowerCase(Locale.ROOT).replace('_', ' ')).append(' ');
                missing.append(key % 25);
            }
            String warning = "Missing " + (requirements.size() - blocks.size()) + " note blocks. Need " + missing
                    + (shown > 5 ? " and more." : ".");
            if (blocks.isEmpty() || !skipMissing.isEnabled()) {
                finish(warning);
                return;
            }
            message(warning + " Playing the available notes.");
        }
        tuneQueue = blocks.entrySet().iterator();
        stage = Stage.TUNING;
    }

    private void tune(MoveFixModule fix) {
        while (tuneTarget != null || tuneQueue.hasNext()) {
            if (tuneTarget == null) {
                tuneTarget = tuneQueue.next();
                tuneAttempts = 0;
            }
            BlockPos pos = tuneTarget.getValue();
            Reading reading = reading(pos);
            if (reading == null || (!anyInstrument && reading.instrument.ordinal() != tuneTarget.getKey() / 25)) {
                finish("A note block changed at " + coordinates(pos) + ".");
                return;
            }
            if (reading.pitch == tuneTarget.getKey() % 25) {
                tuneTarget = null;
                continue;
            }
            if (!autoTune.isEnabled() || tuneAttempts >= 25) {
                finish("Could not tune the note block at " + coordinates(pos) + ".");
                return;
            }
            if (!player.isSneaking() || player.getHeldItem() == null) prepare(Action.TUNE, pos, fix);
            return;
        }
        stage = Stage.PLAYING;
        message("Playing " + song.name);
    }

    private void queueBeat() {
        if (!advanceLoop()) return;
        List<NoteSong.Note> beat = song.beats().get(songTick++);
        if (beat == null) return;
        Set<BlockPos> chord = new LinkedHashSet<>();
        for (NoteSong.Note note : beat) {
            BlockPos pos = blocks.get(note.key(anyInstrument));
            if (pos == null) continue;
            Reading reading = reading(pos);
            if (reading == null || reading.pitch != note.pitch || (!anyInstrument && reading.instrument != note.instrument)) {
                finish("A tuned block changed at " + coordinates(pos) + ".");
                return;
            }
            chord.add(pos);
            if (!polyphonic.isEnabled()) break;
        }
        due.addAll(chord);
    }

    private boolean advanceLoop() {
        if (songTick <= song.lastTick) return true;
        if (loop.isEnabled()) {
            songTick = 0;
            return true;
        }
        finish("Finished " + song.name + ".");
        return false;
    }

    private void previewTick(Minecraft mc) {
        if (!advanceLoop()) return;
        List<NoteSong.Note> beat = song.beats().get(songTick++);
        if (beat == null) return;
        for (NoteSong.Note note : beat) {
            mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(note.instrument.sound),
                    previewVolume.getFloat() * note.velocity / 10_000F, note.soundPitch(),
                    (float) player.posX, (float) (player.posY + player.getEyeHeight()), (float) player.posZ));
            if (!polyphonic.isEnabled()) break;
        }
    }

    private void prepare(Action action, BlockPos pos, MoveFixModule fix) {
        MovingObjectPosition hit = hit(pos);
        if (hit == null) {
            if (action == Action.PROBE) probes.remove();
            else finish("A note block is out of reach at " + coordinates(pos) + ".");
            return;
        }
        planned = action;
        target = pos;
        if (!rotate.isEnabled()) return;
        Vec3 eye = player.getPositionEyes(1), point = center(pos);
        double x = point.xCoord - eye.xCoord, y = point.yCoord - eye.yCoord, z = point.zCoord - eye.zCoord;
        float yaw = (float) Math.toDegrees(Math.atan2(z, x)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(y, Math.hypot(x, z)));
        float currentYaw = fix.getRotationYaw(), currentPitch = fix.getRotationPitch(), step = rotationSpeed.getFloat();
        fix.setFakeRotation(getId(), currentYaw + RotationMath.clamp(RotationMath.difference(yaw, currentYaw), -step, step),
                currentPitch + RotationMath.clamp(pitch - currentPitch, -step, step));
    }

    /** The vanilla walking update has already published this tick's MoveFix rotation. */
    public void afterWalking(Object entity, MoveFixModule fix) {
        if (planned == null || entity != player || !isEnabled()) return;
        Action action = planned;
        planned = null;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != player || mc.theWorld != world || !canPlayInWorld(mc) || !available(mc)
                || fix.hasOtherRotationOwner(getId()) || (rotate.isEnabled() && !fix.ownsRotation(getId()))) return;
        MovingObjectPosition hit = rotate.isEnabled() ? aimedHit(target, fix) : hit(target);
        if (hit == null) return;
        if (action == Action.PLAY) {
            int limit = rotate.isEnabled() || !polyphonic.isEnabled() ? 1 : chordSize.getInt();
            for (int i = 0; i < limit && !due.isEmpty(); i++) {
                if (i != 0) hit = hit(due.peek());
                if (hit == null || !tap(mc, hit)) {
                    finish("Cannot safely play the note block at " + coordinates(due.peek()) + ".");
                    return;
                }
                due.remove();
            }
            return;
        }
        if (action == Action.TUNE) {
            if (player.isSneaking() && player.getHeldItem() != null) return;
            if (!mc.playerController.onPlayerRightClick(player, world, player.getHeldItem(), target, hit.sideHit, hit.hitVec)) {
                finish("Could not tune the note block at " + coordinates(target) + ".");
                return;
            }
            if (swing.isEnabled()) player.swingItem();
            tuneAttempts++;
            nextTune = clock + tuneDelay.getInt();
        } else {
            probes.remove();
            if (!tap(mc, hit)) return;
        }
        awaiting = target;
        deadline = clock + responseTimeout.getInt();
    }

    private boolean tap(Minecraft mc, MovingObjectPosition hit) {
        BlockPos pos = hit.getBlockPos();
        // A survival tap is the controller's start/abort pair. Refuse tools or
        // effects that would make the start itself break the note block.
        if (!validBlock(pos) || Blocks.noteblock.getPlayerRelativeBlockHardness(player, world, pos) >= 1.0F) return false;
        if (swing.isEnabled()) player.swingItem();
        try { return mc.playerController.clickBlock(pos, hit.sideHit); }
        finally { mc.playerController.resetBlockRemoving(); }
    }

    /** Forge raises Play on the client when it handles the server's S24 block action. */
    public void observe(WorldClient source, BlockPos pos, int instrument, int pitch) {
        if (world != source || preview || stage == null || instrument < 0 || instrument >= BLOCK_INSTRUMENTS.length
                || pitch < 0 || pitch > 24) return;
        if (!pos.equals(awaiting) && !readings.containsKey(pos)) return;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityNote)) return;
        // S24 instrument IDs differ from the NBS instrument order.
        readings.put(pos, new Reading(tile, BLOCK_INSTRUMENTS[instrument], pitch));
        if (pos.equals(awaiting)) awaiting = null;
    }

    private Reading reading(BlockPos pos) {
        Reading reading = readings.get(pos);
        return reading != null && world.getTileEntity(pos) == reading.tile ? reading : null;
    }

    private MovingObjectPosition hit(BlockPos pos) {
        if (!validBlock(pos)) return null;
        Vec3 eye = player.getPositionEyes(1), point = center(pos);
        return validHit(pos, eye, world.rayTraceBlocks(eye, point, false, true, false));
    }

    private MovingObjectPosition aimedHit(BlockPos pos, MoveFixModule fix) {
        if (!validBlock(pos)) return null;
        Vec3 eye = player.getPositionEyes(1);
        double yaw = Math.toRadians(fix.getRotationYaw()), pitch = Math.toRadians(fix.getRotationPitch()), reach = reach();
        Vec3 end = eye.addVector(-Math.sin(yaw) * Math.cos(pitch) * reach, -Math.sin(pitch) * reach,
                Math.cos(yaw) * Math.cos(pitch) * reach);
        return validHit(pos, eye, world.rayTraceBlocks(eye, end, false, true, false));
    }

    private MovingObjectPosition validHit(BlockPos pos, Vec3 eye, MovingObjectPosition hit) {
        double reach = reach();
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && pos.equals(hit.getBlockPos())
                && hit.sideHit != null && eye.squareDistanceTo(hit.hitVec) <= reach * reach ? hit : null;
    }

    private boolean validBlock(BlockPos pos) {
        return world.isBlockLoaded(pos) && world.getWorldBorder().contains(pos)
                && world.getBlockState(pos).getBlock() == Blocks.noteblock
                && world.getBlockState(pos.up()).getBlock().getMaterial() == Material.air;
    }

    private Instrument instrument(BlockPos pos) {
        Material base = world.getBlockState(pos.down()).getBlock().getMaterial();
        if (base == Material.wood) return Instrument.BASS;
        if (base == Material.rock) return Instrument.BASS_DRUM;
        if (base == Material.sand) return Instrument.SNARE;
        if (base == Material.glass) return Instrument.HAT;
        return Instrument.HARP;
    }

    private double reach() { return Math.min(range.getDouble(), Minecraft.getMinecraft().playerController.getBlockReachDistance()); }
    private static Vec3 center(BlockPos pos) { return new Vec3(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5); }
    private static String coordinates(BlockPos pos) { return pos.getX() + ", " + pos.getY() + ", " + pos.getZ(); }
    private static void message(String text) { ClientChat.send("NoteBot · " + text); }

    private static boolean canPlayInWorld(Minecraft mc) {
        return mc.playerController != null && mc.playerController.gameIsSurvivalOrAdventure()
                && mc.thePlayer.isAllowEdit() && !mc.thePlayer.capabilities.isCreativeMode && !mc.thePlayer.isSpectator();
    }

    private static boolean available(Minecraft mc) {
        return mc.currentScreen == null && mc.inGameHasFocus && !mc.thePlayer.isRiding() && !mc.thePlayer.isUsingItem()
                && !mc.gameSettings.keyBindAttack.isKeyDown() && !mc.gameSettings.keyBindUseItem.isKeyDown()
                && !mc.playerController.getIsHittingBlock() && !FreecamModule.cameraActiveHook()
                && !ScaffoldModule.isActive() && !BedAuraModule.ownsMiningInput() && !AutoBuffModule.ownsUseInput()
                && !AutoToolModule.hasHotbarAction()
                && AutoToolModule.serverSlotHook(mc.thePlayer.inventory.currentItem) == mc.thePlayer.inventory.currentItem;
    }

    private static MoveFixModule moveFix() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getModuleManager() == null ? null : vibe.getModuleManager().getModule(MoveFixModule.class);
    }

    private static final class Reading {
        private final TileEntity tile;
        private final Instrument instrument;
        private final int pitch;

        private Reading(TileEntity tile, Instrument instrument, int pitch) {
            this.tile = tile;
            this.instrument = instrument;
            this.pitch = pitch;
        }
    }
}
