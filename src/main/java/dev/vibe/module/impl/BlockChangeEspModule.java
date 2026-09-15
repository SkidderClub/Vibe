package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.ColorSetting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import org.lwjgl.input.Keyboard;

/** Highlights only local placement/break changes; chunk loading never creates marks. */
public final class BlockChangeEspModule extends Module {
    private static final long WATCH_TIMEOUT = 1250L;
    private static final long FADE_TIME = 1700L;
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final ColorSetting color = addSetting(new ColorSetting("Color", 0xC52DE2C2));
    private final Map<BlockPos, Watch> watches = new HashMap<BlockPos, Watch>();
    private final Map<BlockPos, Long> changed = new HashMap<BlockPos, Long>();
    private Object world;

    public BlockChangeEspModule() {
        super("BlockChangeESP", "Shows blocks you place or break for a short time", Category.VISUAL, Keyboard.KEY_NONE);
    }

    public ColorSetting getColor() { return color; }

    public void watch(BlockPos pos) {
        if (!isEnabled() || pos == null || minecraft.theWorld == null) return;
        watches.put(pos, new Watch(minecraft.theWorld.getBlockState(pos).getBlock(), System.currentTimeMillis()));
    }

    public void tick() {
        if (!isEnabled() || minecraft.theWorld == null) {
            clear();
            return;
        }
        if (world != minecraft.theWorld) {
            clear();
            world = minecraft.theWorld;
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, Watch>> watching = watches.entrySet().iterator();
        while (watching.hasNext()) {
            Map.Entry<BlockPos, Watch> entry = watching.next();
            Watch watch = entry.getValue();
            if (minecraft.theWorld.getBlockState(entry.getKey()).getBlock() != watch.original) {
                changed.put(entry.getKey(), Long.valueOf(now));
                watching.remove();
            } else if (now - watch.startedAt > WATCH_TIMEOUT) {
                watching.remove();
            }
        }
        Iterator<Map.Entry<BlockPos, Long>> marks = changed.entrySet().iterator();
        while (marks.hasNext()) if (now - marks.next().getValue().longValue() > FADE_TIME) marks.remove();
    }

    public List<Mark> getMarks() {
        long now = System.currentTimeMillis();
        List<Mark> result = new ArrayList<Mark>();
        for (Map.Entry<BlockPos, Long> entry : changed.entrySet()) {
            float progress = Math.max(0.0F, Math.min(1.0F, (now - entry.getValue().longValue()) / (float) FADE_TIME));
            result.add(new Mark(entry.getKey(), progress));
        }
        return result;
    }

    private void clear() {
        watches.clear();
        changed.clear();
        world = minecraft.theWorld;
    }

    @Override protected void onDisable() { clear(); }

    private static final class Watch {
        private final Block original;
        private final long startedAt;
        private Watch(Block original, long startedAt) { this.original = original; this.startedAt = startedAt; }
    }

    public static final class Mark {
        public final BlockPos pos;
        public final float fade;
        private Mark(BlockPos pos, float fade) { this.pos = pos; this.fade = fade; }
    }
}
