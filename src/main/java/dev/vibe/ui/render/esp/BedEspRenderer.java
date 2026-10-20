package dev.vibe.ui.render.esp;

import dev.vibe.Vibe;
import dev.vibe.module.impl.visual.BedEspModule;
import dev.vibe.ui.RenderUtils;
import dev.vibe.ui.WorldRenderUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/** World renderer for the cached BedESP scan. */
public final class BedEspRenderer {
    private final Minecraft minecraft = Minecraft.getMinecraft();

    public void render(RenderWorldLastEvent event) {
        BedEspModule module = Vibe.getInstance().getModuleManager().getModule(BedEspModule.class);
        if (module == null || !module.isEnabled() || minecraft.theWorld == null || minecraft.thePlayer == null) return;
        WorldRenderUtils.begin(true);
        try {
            for (BedEspModule.Bed bed : module.getBeds()) drawBed(module, bed);
            for (BedEspModule.MarkedBlock block : module.getNearby()) drawBlock(module, block);
        } finally {
            WorldRenderUtils.end(true);
        }
    }

    private void drawBed(BedEspModule module, BedEspModule.Bed bed) {
        if (!minecraft.theWorld.isBlockLoaded(bed.foot) || minecraft.theWorld.getBlockState(bed.foot).getBlock() != Blocks.bed) return;
        float alpha = distanceAlpha(module, bed.foot);
        if (alpha <= 0) return;
        int outline;
        int fill;
        if (module.getColorMode().is("Wool")) {
            int wool = nearestWoolColor(bed.foot);
            // nearestWoolColor is ARGB. Masking its alpha is vital: OR-ing it
            // into a requested alpha always produced an opaque bed overlay.
            int rgb = wool & 0x00FFFFFF;
            outline = module.getWoolOutline().isEnabled() ? 0xFF000000 | rgb : 0;
            fill = (module.getWoolAlpha().getInt() << 24) | rgb;
        } else {
            outline = module.getBedOutline().getArgb();
            fill = module.getBedFill().getArgb();
        }
        double minX = Math.min(bed.foot.getX(), bed.head.getX()) - minecraft.getRenderManager().viewerPosX;
        double minY = bed.foot.getY() - minecraft.getRenderManager().viewerPosY;
        double minZ = Math.min(bed.foot.getZ(), bed.head.getZ()) - minecraft.getRenderManager().viewerPosZ;
        double maxX = Math.max(bed.foot.getX(), bed.head.getX()) + 1.0D - minecraft.getRenderManager().viewerPosX;
        double maxZ = Math.max(bed.foot.getZ(), bed.head.getZ()) + 1.0D - minecraft.getRenderManager().viewerPosZ;
        WorldRenderUtils.box(new AxisAlignedBB(minX + .02D, minY + .02D, minZ + .02D, maxX - .02D, minY + .58D, maxZ - .02D),
                fade(outline, alpha), fade(fill, alpha), module.getLineWidth().getFloat());
    }

    private void drawBlock(BedEspModule module, BedEspModule.MarkedBlock block) {
        BlockPos pos = block.pos;
        if (!minecraft.theWorld.isBlockLoaded(pos)) return;
        float alpha = distanceAlpha(module, pos);
        if (alpha <= 0) return;
        if (minecraft.theWorld.getBlockState(pos).getBlock() == Blocks.air) return;
        double x = pos.getX() - minecraft.getRenderManager().viewerPosX;
        double y = pos.getY() - minecraft.getRenderManager().viewerPosY;
        double z = pos.getZ() - minecraft.getRenderManager().viewerPosZ;
        WorldRenderUtils.box(new AxisAlignedBB(x + .02D, y + .02D, z + .02D, x + .98D, y + .98D, z + .98D),
                fade(module.getOutline(block.type).getArgb(), alpha), fade(module.getFill(block.type).getArgb(), alpha), module.getLineWidth().getFloat());
    }

    private float distanceAlpha(BedEspModule module, BlockPos pos) {
        return module.distanceAlpha(minecraft.thePlayer.getDistance(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5));
    }
    private int fade(int color, float alpha) { return RenderUtils.alpha(color, Math.round((color >>> 24) * alpha)); }

    /**
     * The closest wool block in a 13x7x13 box decides the colour; equal distances go to the
     * first block in x, then y, then z order. Visiting the offsets nearest-first in that same
     * tie order finds the identical block and stops at it, instead of reading all 1183 blocks
     * for every bed in every frame.
     */
    private int nearestWoolColor(BlockPos origin) {
        for (int i = 0; i < WOOL_SEARCH_X.length; i++) {
            woolCursor.set(origin.getX() + WOOL_SEARCH_X[i], origin.getY() + WOOL_SEARCH_Y[i], origin.getZ() + WOOL_SEARCH_Z[i]);
            IBlockState state = minecraft.theWorld.getBlockState(woolCursor);
            if (state.getBlock() != Blocks.wool) continue;
            int meta = Blocks.wool.getMetaFromState(state);
            return WOOL_COLORS[Math.max(0, Math.min(WOOL_COLORS.length - 1, meta))];
        }
        return 0xFF4FA3FF;
    }

    private static final int[] WOOL_COLORS = {0xFFF9F9F9,0xFFF9801D,0xFFC74EBD,0xFF3AB3DA,0xFFFED83D,0xFF80C71F,0xFFF38BAA,0xFF474F52,0xFF9D9D97,0xFF169C9C,0xFF8932B8,0xFF3C44AA,0xFF835432,0xFF5E7C16,0xFFB02E26,0xFF1D1D21};
    private static final int[] WOOL_SEARCH_X, WOOL_SEARCH_Y, WOOL_SEARCH_Z;
    private final BlockPos.MutableBlockPos woolCursor = new BlockPos.MutableBlockPos();

    static {
        List<int[]> offsets = new ArrayList<int[]>();
        for (int x = -6; x <= 6; x++) for (int y = -3; y <= 3; y++) for (int z = -6; z <= 6; z++)
            offsets.add(new int[] {x, y, z, x * x + y * y + z * z});
        // List.sort is stable, so blocks at an equal distance keep the x, y, z scan order.
        offsets.sort(Comparator.comparingInt(offset -> offset[3]));
        WOOL_SEARCH_X = new int[offsets.size()];
        WOOL_SEARCH_Y = new int[offsets.size()];
        WOOL_SEARCH_Z = new int[offsets.size()];
        for (int i = 0; i < offsets.size(); i++) {
            WOOL_SEARCH_X[i] = offsets.get(i)[0];
            WOOL_SEARCH_Y[i] = offsets.get(i)[1];
            WOOL_SEARCH_Z[i] = offsets.get(i)[2];
        }
    }
}
