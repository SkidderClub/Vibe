package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.input.Keyboard;

/** Caches beds and the protected blocks immediately surrounding each bed. */
public final class BedEspModule extends Module {

    public static final String OBSIDIAN = "Obsidian";
    public static final String WOOD = "Wood";
    public static final String GLASS = "Glass";
    public static final String ENDSTONE = "Endstone";

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final ModeSetting colorMode = addSetting(new ModeSetting("Bed Color Mode", "Custom", "Custom", "Wool"));
    private final NumberSetting woolAlpha = addSetting(new NumberSetting("Wool Alpha", 69, 0, 255, 1, () -> colorMode.is("Wool")));
    private final BooleanSetting woolOutline = addSetting(new BooleanSetting("Wool Outline", true, () -> colorMode.is("Wool")));
    private final ColorSetting bedOutline = addSetting(new ColorSetting("Bed Outline", 0xFFFF4FA3, () -> colorMode.is("Custom")));
    private final ColorSetting bedFill = addSetting(new ColorSetting("Bed Fill", 0x45FF4FA3, () -> colorMode.is("Custom")));
    private final MultiSelectSetting highlights = addSetting(new MultiSelectSetting("Highlight Near Beds",
            Arrays.asList(OBSIDIAN, WOOD, GLASS, ENDSTONE), Arrays.asList(OBSIDIAN, WOOD, GLASS, ENDSTONE)));
    private final ColorSetting obsidianOutline = addSetting(new ColorSetting("Obsidian Outline", 0xFFFF4D4D,
            () -> highlights.isSelected(OBSIDIAN)));
    private final ColorSetting obsidianFill = addSetting(new ColorSetting("Obsidian Fill", 0x45FF4D4D,
            () -> highlights.isSelected(OBSIDIAN)));
    private final ColorSetting woodOutline = addSetting(new ColorSetting("Wood Outline", 0xFF9B6A3D,
            () -> highlights.isSelected(WOOD)));
    private final ColorSetting woodFill = addSetting(new ColorSetting("Wood Fill", 0x459B6A3D,
            () -> highlights.isSelected(WOOD)));
    private final ColorSetting glassOutline = addSetting(new ColorSetting("Glass Outline", 0xFFFFFFFF,
            () -> highlights.isSelected(GLASS)));
    private final ColorSetting glassFill = addSetting(new ColorSetting("Glass Fill", 0x40FFFFFF,
            () -> highlights.isSelected(GLASS)));
    private final ColorSetting endstoneOutline = addSetting(new ColorSetting("Endstone Outline", 0xFFFFE08A,
            () -> highlights.isSelected(ENDSTONE)));
    private final ColorSetting endstoneFill = addSetting(new ColorSetting("Endstone Fill", 0x45FFE08A,
            () -> highlights.isSelected(ENDSTONE)));
    private final NumberSetting viewDistance = addSetting(new NumberSetting("View Distance", 256.0D, 8.0D, 256.0D, 1.0D));
    private final BooleanSetting fadeAlpha = addSetting(new BooleanSetting("Fade Alpha At Max", true, () -> !isUnlimited()));
    private final NumberSetting lineWidth = addSetting(new NumberSetting("Line Width", 1.5D, 1.0D, 5.0D, 0.5D));
    private List<Bed> beds = Collections.emptyList();
    private List<MarkedBlock> nearby = Collections.emptyList();
    private long lastScan;
    private net.minecraft.world.World scannedWorld;
    private final Map<net.minecraft.world.chunk.Chunk, List<Bed>> chunkBeds = new LinkedHashMap<>();
    private final java.util.ArrayDeque<net.minecraft.world.chunk.Chunk> pendingChunks = new java.util.ArrayDeque<>();
    private java.lang.reflect.Field loadedChunksField;

    public NumberSetting getViewDistance() { return viewDistance; }
    public BooleanSetting getFadeAlpha() { return fadeAlpha; }
    public boolean isUnlimited() { return viewDistance.getDouble() >= viewDistance.getMaximum(); }
    public float distanceAlpha(double distance) {
        if (isUnlimited()) return 1;
        if (distance > viewDistance.getDouble()) return 0;
        return fadeAlpha.isEnabled() ? (float) Math.max(0, 1 - distance / viewDistance.getDouble()) : 1;
    }

    public BedEspModule() {
        super("BedESP", "Highlights beds and nearby defensive blocks", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public ModeSetting getColorMode() { return colorMode; }
    public NumberSetting getWoolAlpha() { return woolAlpha; }
    public BooleanSetting getWoolOutline() { return woolOutline; }
    public ColorSetting getBedOutline() { return bedOutline; }
    public ColorSetting getBedFill() { return bedFill; }
    public MultiSelectSetting getHighlights() { return highlights; }
    public ColorSetting getOutline(String type) {
        if (OBSIDIAN.equals(type)) return obsidianOutline;
        if (WOOD.equals(type)) return woodOutline;
        if (GLASS.equals(type)) return glassOutline;
        return endstoneOutline;
    }
    public ColorSetting getFill(String type) {
        if (OBSIDIAN.equals(type)) return obsidianFill;
        if (WOOD.equals(type)) return woodFill;
        if (GLASS.equals(type)) return glassFill;
        return endstoneFill;
    }
    public NumberSetting getLineWidth() { return lineWidth; }
    public List<Bed> getBeds() { return beds; }
    public List<MarkedBlock> getNearby() { return nearby; }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null) {
            clear();
            return;
        }
        if (scannedWorld != minecraft.theWorld) { clear(); scannedWorld = minecraft.theWorld; }
        long now = System.currentTimeMillis();
        scanLoadedChunks(now);
        if (now - lastScan < 650L) return;
        lastScan = now;
        List<Bed> foundBeds = new ArrayList<Bed>();
        for (List<Bed> cached : chunkBeds.values()) for (Bed bed : cached)
            if (minecraft.theWorld.getBlockState(bed.foot).getBlock() == Blocks.bed) foundBeds.add(bed);
        Map<BlockPos, String> blocks = new LinkedHashMap<BlockPos, String>();
        for (Bed bed : foundBeds) {
            if (distanceAlpha(minecraft.thePlayer.getDistance(bed.foot.getX() + .5, bed.foot.getY() + .5, bed.foot.getZ() + .5)) <= 0) continue;
            int bedY = bed.foot.getY();
            int minX = Math.min(bed.foot.getX(), bed.head.getX()) - 5;
            int maxX = Math.max(bed.foot.getX(), bed.head.getX()) + 5;
            int minZ = Math.min(bed.foot.getZ(), bed.head.getZ()) - 5;
            int maxZ = Math.max(bed.foot.getZ(), bed.head.getZ()) + 5;
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) for (int y = bedY; y <= Math.min(255, bedY + 6); y++) {
                BlockPos pos = new BlockPos(x, y, z);
                String type = classify(minecraft.theWorld.getBlockState(pos).getBlock());
                if (type != null && highlights.isSelected(type)) blocks.put(pos, type);
            }
        }
        List<MarkedBlock> marked = new ArrayList<MarkedBlock>();
        for (Map.Entry<BlockPos, String> entry : blocks.entrySet()) marked.add(new MarkedBlock(entry.getKey(), entry.getValue()));
        beds = foundBeds;
        nearby = marked;
    }

    @SuppressWarnings("unchecked")
    private void scanLoadedChunks(long now) {
        if (pendingChunks.isEmpty()) {
            try {
                Object provider = minecraft.theWorld.getChunkProvider();
                if (loadedChunksField == null) {
                    loadedChunksField = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
                            net.minecraft.client.multiplayer.ChunkProviderClient.class, "chunkListing", "field_73237_c");
                }
                List<net.minecraft.world.chunk.Chunk> loaded = new ArrayList<>((List<net.minecraft.world.chunk.Chunk>) loadedChunksField.get(provider));
                chunkBeds.keySet().retainAll(loaded);
                // Near chunks arrive first; the maximum includes every loaded chunk at every height.
                loaded.sort(java.util.Comparator.comparingDouble(chunk -> minecraft.thePlayer.getDistanceSq(chunk.xPosition * 16 + 8, minecraft.thePlayer.posY, chunk.zPosition * 16 + 8)));
                pendingChunks.addAll(loaded);
            } catch (ReflectiveOperationException failure) {
                return;
            }
        }
        long deadline = System.nanoTime() + 3_000_000L;
        for (int count = 0; count < 8 && !pendingChunks.isEmpty(); count++) {
            net.minecraft.world.chunk.Chunk chunk = pendingChunks.removeFirst();
            if (!minecraft.theWorld.getChunkProvider().chunkExists(chunk.xPosition, chunk.zPosition)) {
                chunkBeds.remove(chunk);
                continue;
            }
            List<Bed> found = new ArrayList<>();
            for (net.minecraft.world.chunk.storage.ExtendedBlockStorage section : chunk.getBlockStorageArray()) {
                if (section == null || section.isEmpty()) continue;
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    net.minecraft.block.state.IBlockState state = section.get(x, y, z);
                    if (state.getBlock() != Blocks.bed) continue;
                    int meta = Blocks.bed.getMetaFromState(state);
                    if ((meta & 8) != 0) continue;
                    BlockPos foot = new BlockPos(chunk.xPosition * 16 + x, section.getYLocation() + y, chunk.zPosition * 16 + z);
                    found.add(new Bed(foot, foot.offset(EnumFacing.getHorizontal(meta & 3))));
                }
            }
            chunkBeds.put(chunk, found);
            if (System.nanoTime() >= deadline) break;
        }
    }

    private String classify(Block block) {
        if (block == Blocks.obsidian) return OBSIDIAN;
        if (block == Blocks.end_stone) return ENDSTONE;
        if (block == Blocks.glass || block == Blocks.stained_glass || block == Blocks.glass_pane || block == Blocks.stained_glass_pane) return GLASS;
        return block != null && block.getMaterial() == Material.wood ? WOOD : null;
    }

    @Override protected void onDisable() {
        clear();
    }

    private void clear() {
        beds = Collections.emptyList();
        nearby = Collections.emptyList();
        chunkBeds.clear();
        pendingChunks.clear();
        scannedWorld = null;
        lastScan = 0;
    }

    public static final class Bed {
        public final BlockPos foot;
        public final BlockPos head;
        private Bed(BlockPos foot, BlockPos head) { this.foot = foot; this.head = head; }
    }

    public static final class MarkedBlock {
        public final BlockPos pos;
        public final String type;
        private MarkedBlock(BlockPos pos, String type) { this.pos = pos; this.type = type; }
    }
}
