package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.BlockChangeEspModule;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/** Fading, through-wall boxes for the local block interaction watch list. */
public final class BlockChangeEspRenderer {
    private final Minecraft minecraft = Minecraft.getMinecraft();

    public void render(RenderWorldLastEvent event) {
        BlockChangeEspModule module = Vibe.getInstance().getModuleManager().getModule(BlockChangeEspModule.class);
        if (module == null || !module.isEnabled() || minecraft.theWorld == null) return;
        WorldRenderUtils.begin(true);
        try {
            int base = module.getColor().getArgb();
            for (BlockChangeEspModule.Mark mark : module.getMarks()) {
                int alpha = Math.max(0, Math.round(((base >>> 24) & 255) * (1.0F - mark.fade)));
                int outline = (alpha << 24) | (base & 0x00FFFFFF);
                int fill = (Math.max(0, alpha / 4) << 24) | (base & 0x00FFFFFF);
                double x = mark.pos.getX() - minecraft.getRenderManager().viewerPosX;
                double y = mark.pos.getY() - minecraft.getRenderManager().viewerPosY;
                double z = mark.pos.getZ() - minecraft.getRenderManager().viewerPosZ;
                WorldRenderUtils.box(new AxisAlignedBB(x + .01D, y + .01D, z + .01D, x + .99D, y + .99D, z + .99D), outline, fill, 1.5F);
            }
        } finally {
            WorldRenderUtils.end(true);
        }
    }
}
