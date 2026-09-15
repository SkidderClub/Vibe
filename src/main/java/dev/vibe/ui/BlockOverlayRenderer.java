package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.BlockOverlayModule;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

/** Animated per-edge block overlay, with directional rainbow and fade paths. */
public final class BlockOverlayRenderer {
    private static final int[][] EDGES = {
            {0, 1}, {0, 2}, {0, 4},
            {1, 3}, {1, 5}, {2, 3}, {2, 6}, {4, 5}, {4, 6},
            {3, 7}, {5, 7}, {6, 7}
    };
    private static final int[][] FACES = {
            {0, 2, 3, 1}, {4, 5, 7, 6}, {0, 4, 6, 2},
            {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}
    };
    private final Minecraft minecraft = Minecraft.getMinecraft();

    public void render(RenderWorldLastEvent event) {
        BlockOverlayModule module = Vibe.getInstance().getModuleManager().getModule(BlockOverlayModule.class);
        if (module == null || !module.isEnabled() || minecraft.theWorld == null || module.getTarget() == null) return;
        AxisAlignedBB worldBox = selectedBox(module.getTarget());
        if (worldBox == null) return;
        double x = minecraft.getRenderManager().viewerPosX;
        double y = minecraft.getRenderManager().viewerPosY;
        double z = minecraft.getRenderManager().viewerPosZ;
        // Keep the wire a few thousandths outside the selected model. This
        // prevents z-fighting against opaque block textures while preserving
        // the exact square corners of the block outline.
        AxisAlignedBB box = worldBox.offset(-x, -y, -z).expand(0.004D, 0.004D, 0.004D);
        WorldRenderUtils.begin(module.getThroughWalls().isEnabled());
        int shadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        try {
            float progress = module.getBreakProgress();
            long now = System.currentTimeMillis();
            if (module.getFill().isEnabled()) drawAnimatedFill(box, module, progress, now);
            if (module.getOutline().isEnabled()) drawOutline(box, module, now);
        } finally {
            GL11.glShadeModel(shadeModel);
            WorldRenderUtils.end(module.getThroughWalls().isEnabled());
        }
    }

    private AxisAlignedBB selectedBox(BlockPos pos) {
        Block block = minecraft.theWorld.getBlockState(pos).getBlock();
        AxisAlignedBB box = block.getSelectedBoundingBox(minecraft.theWorld, pos);
        return box == null ? new AxisAlignedBB(pos, pos.add(1, 1, 1)) : box;
    }

    private void drawOutline(AxisAlignedBB box, BlockOverlayModule module, long now) {
        double[][] corners = corners(box);
        boolean smooth = GL11.glIsEnabled(GL11.GL_LINE_SMOOTH);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(module.getOutlineWidth().getFloat());
        GL11.glBegin(GL11.GL_LINES);
        for (int index = 0; index < EDGES.length; index++) {
            double[] first = corners[EDGES[index][0]], second = corners[EDGES[index][1]];
            float start = cornerPhase(EDGES[index][0]), end = cornerPhase(EDGES[index][1]);
            // Opposite corners share a colour, giving the cyan/pink diagonal
            // gradient in the reference. Subdivision follows hue through the
            // edge instead of interpolating a muddy RGB midpoint.
            for (int segment = 0; segment < 16; segment++) for (int point = 0; point < 2; point++) {
                float t = (segment + point) / 16F;
                WorldRenderUtils.color(color(module.getOutlineMode().getValue(), module.getOutlinePrimary().getArgb(),
                        module.getOutlineSecondary().getArgb(), start + (end - start) * t, now, module.getAnimationSpeed().getFloat()));
                GL11.glVertex3d(first[0] + (second[0]-first[0])*t, first[1] + (second[1]-first[1])*t, first[2] + (second[2]-first[2])*t);
            }
        }
        GL11.glEnd();
        if (!smooth) GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    private void drawAnimatedFill(AxisAlignedBB box, BlockOverlayModule module, float progress, long now) {
        if (progress <= 0.001F) {
            drawFillFaces(box, module, now, 0.33F);
            return;
        }
        if (module.getBreakAnimation().is("Inward Fill")) {
            // Six coloured sheets grow from the outlined faces toward the
            // centre. At full break progress they meet into a solid volume.
            drawInwardFill(box, module, now, progress);
        } else if (module.getBreakAnimation().is("Dissolve")) {
            drawDissolve(box, module, now, progress);
        } else {
            drawFillFaces(box, module, now, 0.18F + progress * 0.30F);
            drawPulseShatter(box, module, now, progress);
        }
    }

    private void drawInwardFill(AxisAlignedBB box, BlockOverlayModule module, long now, float progress) {
        double depth = Math.max(.008D, progress * .5D);
        AxisAlignedBB[] sheets = {
                new AxisAlignedBB(box.minX, box.minY, box.minZ, box.minX + depth, box.maxY, box.maxZ),
                new AxisAlignedBB(box.maxX - depth, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ),
                new AxisAlignedBB(box.minX, box.minY, box.minZ, box.maxX, box.minY + depth, box.maxZ),
                new AxisAlignedBB(box.minX, box.maxY - depth, box.minZ, box.maxX, box.maxY, box.maxZ),
                new AxisAlignedBB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ + depth),
                new AxisAlignedBB(box.minX, box.minY, box.maxZ - depth, box.maxX, box.maxY, box.maxZ)
        };
        for (int index = 0; index < sheets.length; index++) {
            drawFillFaces(sheets[index], module, now + index * 37L, 0.28F + progress * .64F);
        }
    }

    private void drawFillFaces(AxisAlignedBB box, BlockOverlayModule module, long now, float strength) {
        double[][] corners = corners(box);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer world = tessellator.getWorldRenderer();
        world.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int face = 0; face < FACES.length; face++) {
            for (int vertex : FACES[face]) {
                int color = alpha(color(module.getFillMode().getValue(), module.getFillPrimary().getArgb(), module.getFillSecondary().getArgb(),
                        cornerPhase(vertex), now, module.getAnimationSpeed().getFloat()), strength);
                vertex(world, corners[vertex], color);
            }
        }
        tessellator.draw();
    }

    /** Checkerboard panels disappear in a travelling diagonal wave. */
    private void drawDissolve(AxisAlignedBB box, BlockOverlayModule module, long now, float progress) {
        double step = 0.25D;
        for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) {
            float wave = (x + z) / 6.0F;
            if (progress + 0.20F < wave) continue;
            double minX = box.minX + x * step, minZ = box.minZ + z * step;
            AxisAlignedBB tile = new AxisAlignedBB(minX, box.minY + 0.002D, minZ,
                    minX + step - 0.006D, box.maxY - 0.002D, minZ + step - 0.006D);
            drawFillFaces(tile, module, now + (x * 29L + z * 41L), 0.42F * (1.0F - progress * .35F));
        }
    }

    /** Three expanding fragments burst outward from the block as it breaks. */
    private void drawPulseShatter(AxisAlignedBB box, BlockOverlayModule module, long now, float progress) {
        float base = (float) ((now % 900L) / 900.0D);
        for (int index = 0; index < 3; index++) {
            float phase = (base + index / 3.0F) % 1.0F;
            double expand = .005D + phase * .10D * (0.35D + progress);
            int color = alpha(color(module.getFillMode().getValue(), module.getFillPrimary().getArgb(), module.getFillSecondary().getArgb(),
                    phase, now, module.getAnimationSpeed().getFloat()), (1.0F - phase) * .7F);
            WorldRenderUtils.box(box.expand(expand, expand, expand), color, 0x00000000, 1.0F);
        }
    }

    static int color(String mode, int primary, int secondary, float phase, long now, float speed) {
        double time = now / 1200.0D * speed;
        if ("Rainbow".equalsIgnoreCase(mode)) {
            return hsb((float) ((.48D + time * .10D + phase * .36D) % 1D), primary >>> 24);
        }
        if ("Fade".equalsIgnoreCase(mode)) {
            return RenderUtils.blend(primary, secondary, (float) ((1 - Math.cos(time + phase * Math.PI)) * .5));
        }
        return primary;
    }

    static float cornerPhase(int corner) { return Integer.bitCount(corner) % 2; }

    private static int hsb(float hue, int alpha) {
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.82F, 1.0F) & 0x00FFFFFF;
        return (alpha << 24) | rgb;
    }

    private int alpha(int color, float multiplier) {
        return (Math.max(0, Math.min(255, Math.round(((color >>> 24) & 255) * multiplier))) << 24) | (color & 0x00FFFFFF);
    }

    private double[][] corners(AxisAlignedBB box) {
        return new double[][] {
                {box.minX, box.minY, box.minZ}, {box.maxX, box.minY, box.minZ},
                {box.minX, box.maxY, box.minZ}, {box.maxX, box.maxY, box.minZ},
                {box.minX, box.minY, box.maxZ}, {box.maxX, box.minY, box.maxZ},
                {box.minX, box.maxY, box.maxZ}, {box.maxX, box.maxY, box.maxZ}
        };
    }

    private void vertex(WorldRenderer world, double[] point, int color) {
        world.pos(point[0], point[1], point[2]).color((color >>> 16) & 255, (color >>> 8) & 255,
                color & 255, (color >>> 24) & 255).endVertex();
    }
}
