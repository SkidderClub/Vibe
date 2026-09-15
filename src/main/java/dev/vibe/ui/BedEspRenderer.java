package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.BedEspModule;
import java.util.Map;
import net.minecraft.block.Block;
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

    private int nearestWoolColor(BlockPos origin) {
        BlockPos nearest = null;
        int best = Integer.MAX_VALUE;
        for (int x = origin.getX() - 6; x <= origin.getX() + 6; x++) for (int y = origin.getY() - 3; y <= origin.getY() + 3; y++) for (int z = origin.getZ() - 6; z <= origin.getZ() + 6; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            Block block = minecraft.theWorld.getBlockState(pos).getBlock();
            if (block != Blocks.wool) continue;
            int distance = (x - origin.getX()) * (x - origin.getX()) + (y - origin.getY()) * (y - origin.getY()) + (z - origin.getZ()) * (z - origin.getZ());
            if (distance < best) { best = distance; nearest = pos; }
        }
        if (nearest == null) return 0xFF4FA3FF;
        int meta = Blocks.wool.getMetaFromState(minecraft.theWorld.getBlockState(nearest));
        int[] colors = {0xFFF9F9F9,0xFFF9801D,0xFFC74EBD,0xFF3AB3DA,0xFFFED83D,0xFF80C71F,0xFFF38BAA,0xFF474F52,0xFF9D9D97,0xFF169C9C,0xFF8932B8,0xFF3C44AA,0xFF835432,0xFF5E7C16,0xFFB02E26,0xFF1D1D21};
        return colors[Math.max(0, Math.min(colors.length - 1, meta))];
    }
}
