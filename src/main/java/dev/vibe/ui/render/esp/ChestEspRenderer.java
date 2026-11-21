package dev.vibe.ui.render.esp;

import dev.vibe.module.impl.visual.ChestEspModule;
import dev.vibe.module.impl.visual.ChestEspModule.ChestStyle;
import dev.vibe.module.impl.visual.ChestEspModule.Type;
import dev.vibe.ui.DebugOverlay;
import dev.vibe.ui.RenderUtils;
import dev.vibe.ui.WorldRenderUtils;
import dev.vibe.ui.effect.EffectState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public final class ChestEspRenderer {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final BoxProjection.Camera camera = new BoxProjection.Camera();
    private final List<ScreenChest> screenChests = new ArrayList<>();

    public void beginFrame() {
        screenChests.clear();
    }

    public boolean hasOverlay() {
        return !screenChests.isEmpty();
    }

    public void renderWorld(ChestEspModule module) {
        screenChests.clear();
        if (module == null || !module.isEnabled() || minecraft.theWorld == null || minecraft.thePlayer == null) return;

        RenderManager view = minecraft.getRenderManager();
        double range = module.getMaxDistance().getDouble();
        double rangeSquared = range * range;
        boolean projected = module.is2D();
        ScaledResolution resolution = projected ? new ScaledResolution(minecraft) : null;
        if (projected) camera.capture();
        else WorldRenderUtils.begin(true);
        try {
            for (TileEntity tile : minecraft.theWorld.loadedTileEntityList) {
                if (!(tile instanceof TileEntityChest) && !(tile instanceof TileEntityEnderChest)) continue;
                if (tile.isInvalid()) continue;
                BlockPos pos = tile.getPos();
                AxisAlignedBB bounds = projected ? projectedBox(tile) : SINGLE_3D;
                if (bounds == null) continue;
                double distanceSquared = minecraft.thePlayer.getDistanceSq(
                        pos.getX() + (bounds.minX + bounds.maxX) * .5,
                        pos.getY() + .5, pos.getZ() + (bounds.minZ + bounds.maxZ) * .5);
                if (distanceSquared > rangeSquared) continue;
                Type type = typeOf(minecraft.theWorld.getBlockState(pos).getBlock());
                if (type == null || !module.getStyle(type).enabled.isEnabled()) continue;

                bounds = bounds.offset(pos.getX() - view.viewerPosX, pos.getY() - view.viewerPosY, pos.getZ() - view.viewerPosZ);
                float distance = (float) (Math.sqrt(distanceSquared) / range);
                if (projected) {
                    EspLayout.Rect rect = camera.project(bounds, resolution.getScaledWidth_double(), resolution.getScaledHeight_double());
                    if (rect != null) screenChests.add(new ScreenChest(rect, type, distance));
                } else {
                    drawBox3D(module, type, distance, bounds);
                }
            }
        } finally {
            if (!projected) WorldRenderUtils.end(true);
        }
    }

    private static Type typeOf(Block block) {
        if (block == Blocks.chest) return Type.NORMAL;
        if (block == Blocks.trapped_chest) return Type.TRAPPED;
        if (block == Blocks.ender_chest) return Type.ENDER;
        return null;
    }

    private static final AxisAlignedBB SINGLE_3D = new AxisAlignedBB(.03, .03, .03, .97, .97, .97);
    private static final AxisAlignedBB SINGLE_2D = new AxisAlignedBB(.0625, 0, .0625, .9375, .875, .9375);

    public static AxisAlignedBB box3D() { return SINGLE_3D; }
    public static AxisAlignedBB box2D() { return SINGLE_2D; }

    private static AxisAlignedBB projectedBox(TileEntity tile) {
        if (!(tile instanceof TileEntityChest)) return SINGLE_2D;
        TileEntityChest chest = (TileEntityChest) tile;
        // Vanilla draws a double chest from its west/north half.
        if (valid(chest.adjacentChestXNeg) || valid(chest.adjacentChestZNeg)) return null;
        boolean east = valid(chest.adjacentChestXPos), south = valid(chest.adjacentChestZPos);
        return east || south ? new AxisAlignedBB(SINGLE_2D.minX, 0, SINGLE_2D.minZ,
                SINGLE_2D.maxX + (east ? 1 : 0), SINGLE_2D.maxY, SINGLE_2D.maxZ + (south ? 1 : 0)) : SINGLE_2D;
    }

    private static boolean valid(TileEntityChest chest) {
        return chest != null && !chest.isInvalid();
    }

    public void renderOverlay(ChestEspModule module) {
        if (!hasOverlay()) return;
        try {
            if (module == null || !module.isEnabled() || !module.is2D()) return;
            try (EffectState state = new EffectState()) {
                setup2D();
                for (ScreenChest chest : screenChests) {
                    EspLayout.Rect box = chest.rect;
                    if (!DebugOverlay.overlaps((int) box.x, (int) box.y, (int) Math.ceil(box.right()), (int) Math.ceil(box.bottom()))) {
                        drawBox2D(module, chest.type, chest.distance, box);
                    }
                }
            }
        } finally {
            screenChests.clear();
        }
    }

    public static void drawPreview2D(ChestEspModule module, Type type, float distance, EspLayout.Rect box) {
        if (box == null || !module.getStyle(type).enabled.isEnabled()) return;
        boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try (EffectState state = new EffectState()) {
            if (clipped) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            setup2D();
            drawBox2D(module, type, distance, box);
        }
    }

    private static void setup2D() {
        GL20.glUseProgram(0);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void drawBox2D(ChestEspModule module, Type type, float distance, EspLayout.Rect box) {
        ChestStyle style = module.getStyle(type);
        int fade = fadeColor(module, distance);
        float alpha = opacity(module, distance);
        float minSide = Math.min(box.w, box.h);
        float radius = Math.min(minSide / 2, module.getRounding().getFloat());
        float width = Math.min(minSide, module.getLineWidth().getFloat());
        Esp2DRenderer.drawSolidBox(box, styleColor(module, style.fill.getArgb(), fade, alpha),
                styleColor(module, style.outline.getArgb(), fade, alpha), radius, width,
                module.getBoxStyle().is("Corners"), minSide * module.getCornerLength().getFloat());
    }

    public static void drawBox3D(ChestEspModule module, Type type, float distance, AxisAlignedBB bounds) {
        ChestStyle style = module.getStyle(type);
        if (!style.enabled.isEnabled()) return;
        int fade = fadeColor(module, distance);
        float alpha = opacity(module, distance);
        WorldRenderUtils.box(bounds, styleColor(module, style.outline.getArgb(), fade, alpha),
                styleColor(module, style.fill.getArgb(), fade, alpha), module.getLineWidth().getFloat());
    }

    private static int fadeColor(ChestEspModule module, float distance) {
        return module.getColorFade().isEnabled()
                ? RenderUtils.blend(module.getNearColor().getArgb(), module.getFarColor().getArgb(), distance) : 0;
    }

    private static float opacity(ChestEspModule module, float distance) {
        return module.getFadeAlpha().isEnabled() ? 1 - distance : 1;
    }

    private static int styleColor(ChestEspModule module, int base, int fade, float opacity) {
        boolean useFade = module.getColorFade().isEnabled();
        int rgb = (useFade ? fade : base) & 0xFFFFFF;
        float fadeAlpha = useFade ? (fade >>> 24) / 255F : 1;
        return rgb | Math.round((base >>> 24) * fadeAlpha * opacity) << 24;
    }

    private static final class ScreenChest {
        private final EspLayout.Rect rect;
        private final Type type;
        private final float distance;

        private ScreenChest(EspLayout.Rect rect, Type type, float distance) {
            this.rect = rect;
            this.type = type;
            this.distance = distance;
        }
    }
}
