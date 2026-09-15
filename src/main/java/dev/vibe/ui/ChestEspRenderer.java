package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.ChestEspModule;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;

public final class ChestEspRenderer {

    private final Minecraft minecraft = Minecraft.getMinecraft();

    public void render(RenderWorldLastEvent event) {
        ChestEspModule module = Vibe.getInstance().getModuleManager().getModule(ChestEspModule.class);
        if (module == null || !module.isEnabled() || minecraft.theWorld == null) {
            return;
        }
        WorldRenderUtils.begin(true);
        try {
            for (Object object : minecraft.theWorld.loadedTileEntityList) {
                if (!(object instanceof TileEntityChest) && !(object instanceof TileEntityEnderChest)) {
                    continue;
                }
                TileEntity tile = (TileEntity) object;
                BlockPos pos = tile.getPos();
                double chestX = pos.getX() + 0.5D;
                double chestY = pos.getY() + 0.5D;
                double chestZ = pos.getZ() + 0.5D;
                double distance = minecraft.thePlayer.getDistance(chestX, chestY, chestZ);
                double maxDistance = module.getMaxDistance().getDouble();
                if (distance > maxDistance) {
                    continue;
                }
                int outline;
                int fill;
                if (minecraft.theWorld.getBlockState(pos).getBlock() == Blocks.chest && module.getNormal().isEnabled()) {
                    outline = module.getNormalOutline().getArgb();
                    fill = module.getNormalFill().getArgb();
                } else if (minecraft.theWorld.getBlockState(pos).getBlock() == Blocks.trapped_chest && module.getRedstone().isEnabled()) {
                    outline = module.getRedstoneOutline().getArgb();
                    fill = module.getRedstoneFill().getArgb();
                } else if (minecraft.theWorld.getBlockState(pos).getBlock() == Blocks.ender_chest && module.getEnder().isEnabled()) {
                    outline = module.getEnderOutline().getArgb();
                    fill = module.getEnderFill().getArgb();
                } else {
                    continue;
                }
                float distanceProgress = (float) Math.max(0.0D, Math.min(1.0D, distance / maxDistance));
                int fadeColor = module.getColorFade().isEnabled()
                        ? RenderUtils.blend(module.getNearColor().getArgb(), module.getFarColor().getArgb(), distanceProgress)
                        : 0;
                float alphaMultiplier = module.getFadeAlpha().isEnabled() ? 1.0F - distanceProgress : 1.0F;
                outline = applyDistanceStyle(outline, fadeColor, module.getColorFade().isEnabled(), alphaMultiplier);
                fill = applyDistanceStyle(fill, fadeColor, module.getColorFade().isEnabled(), alphaMultiplier);
                double x = pos.getX() - minecraft.getRenderManager().viewerPosX;
                double y = pos.getY() - minecraft.getRenderManager().viewerPosY;
                double z = pos.getZ() - minecraft.getRenderManager().viewerPosZ;
                AxisAlignedBB box = new AxisAlignedBB(x + 0.03D, y + 0.03D, z + 0.03D, x + 0.97D, y + 0.97D, z + 0.97D);
                WorldRenderUtils.box(box, outline, fill, module.getLineWidth().getFloat());
            }
        } finally {
            WorldRenderUtils.end(true);
        }
    }

    private int applyDistanceStyle(int base, int fadeColor, boolean useFadeColor, float alphaMultiplier) {
        int rgb = useFadeColor ? fadeColor & 0x00FFFFFF : base & 0x00FFFFFF;
        int baseAlpha = (base >>> 24) & 255;
        int colorAlpha = useFadeColor ? (fadeColor >>> 24) & 255 : 255;
        int alpha = Math.round(baseAlpha * (colorAlpha / 255.0F) * alphaMultiplier);
        return (Math.max(0, Math.min(255, alpha)) << 24) | rgb;
    }
}
