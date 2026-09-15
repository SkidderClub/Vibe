package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.ScaffoldModule;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/** Renders the next Scaffold placement position. */
public final class ScaffoldRenderer {
    private final Minecraft minecraft = Minecraft.getMinecraft();

    public void render(RenderWorldLastEvent event) {
        ScaffoldModule module = Vibe.getInstance().getModuleManager().getModule(ScaffoldModule.class);
        if (module == null || !module.isEnabled() || module.getTarget() == null || minecraft.theWorld == null) return;
        AxisAlignedBB box = new AxisAlignedBB(module.getTarget(), module.getTarget().add(1, 1, 1));
        double x = minecraft.getRenderManager().viewerPosX;
        double y = minecraft.getRenderManager().viewerPosY;
        double z = minecraft.getRenderManager().viewerPosZ;
        WorldRenderUtils.begin(false);
        try {
            WorldRenderUtils.box(box.offset(-x, -y, -z).expand(0.004D, 0.004D, 0.004D),
                    0xFF2DE2C2, 0x302DE2C2, 1.5F);
        } finally {
            WorldRenderUtils.end(false);
        }
    }
}
