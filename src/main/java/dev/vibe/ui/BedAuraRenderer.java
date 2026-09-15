package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.BedAuraModule;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/** Renders the active BedAura target using the shared world-box renderer. */
public final class BedAuraRenderer {
    private final Minecraft minecraft = Minecraft.getMinecraft();

    public void render(RenderWorldLastEvent event) {
        BedAuraModule module = Vibe.getInstance().getModuleManager().getModule(BedAuraModule.class);
        if (module == null || !module.shouldRenderTarget() || minecraft.theWorld == null) return;
        AxisAlignedBB box = minecraft.theWorld.getBlockState(module.getTarget()).getBlock()
                .getSelectedBoundingBox(minecraft.theWorld, module.getTarget());
        if (box == null) return;
        double x = minecraft.getRenderManager().viewerPosX;
        double y = minecraft.getRenderManager().viewerPosY;
        double z = minecraft.getRenderManager().viewerPosZ;
        WorldRenderUtils.begin(false);
        try {
            WorldRenderUtils.box(box.offset(-x, -y, -z).expand(0.004D, 0.004D, 0.004D),
                    module.getTargetColor().getArgb(), module.getTargetColor().getArgb(), 2.0F);
        } finally {
            WorldRenderUtils.end(false);
        }
    }
}
