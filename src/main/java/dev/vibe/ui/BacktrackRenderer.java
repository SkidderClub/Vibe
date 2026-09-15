package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.BacktrackModule;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/** Shows the newest delayed target position, separately from the old client model. */
public final class BacktrackRenderer {
    private final Minecraft minecraft = Minecraft.getMinecraft();

    public void render(RenderWorldLastEvent event) {
        BacktrackModule module = Vibe.getInstance().getModuleManager().getModule(BacktrackModule.class);
        if (module == null || !module.isEnabled() || !module.getRenderServerPosition().isEnabled()
                || !module.hasServerPosition() || minecraft.theWorld == null) return;
        Entity target = minecraft.theWorld.getEntityByID(module.getTargetId());
        if (target == null) return;
        double half = target.width * .5D;
        double x = module.getServerX() - minecraft.getRenderManager().viewerPosX;
        double y = module.getServerY() - minecraft.getRenderManager().viewerPosY;
        double z = module.getServerZ() - minecraft.getRenderManager().viewerPosZ;
        int outline = module.getRenderColor().getArgb() | 0xFF000000;
        int fill = ((module.getRenderColor().getAlpha() / 3) << 24) | (outline & 0x00FFFFFF);
        WorldRenderUtils.begin(true);
        try {
            WorldRenderUtils.box(new AxisAlignedBB(x - half, y, z - half, x + half, y + target.height, z + half), outline, fill, 1.3F);
        } finally {
            WorldRenderUtils.end(true);
        }
    }
}
