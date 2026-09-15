package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.EspModule;
import dev.vibe.module.impl.TargetsModule;
import dev.vibe.module.impl.QolModule;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

/** Captures player bounds in world render and draws the 2D variant in the HUD pass. */
public final class EspRenderer {

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer window = BufferUtils.createFloatBuffer(3);
    private final List<ScreenBox> screenBoxes = new ArrayList<ScreenBox>();
    private boolean localProjection;

    /** GTA7 owns a separate instance, so its camera never overwrites Minecraft's captured ESP. */
    public void beginLocalFrame() {
        localProjection = true;
        screenBoxes.clear();
        captureMatrices();
    }

    /** Render local actors through the exact same 3D box and tactical HUD paths as Minecraft. */
    public void captureLocalActor(AxisAlignedBB box, String name, float health, float maximumHealth,
                                  float distance, String heldItem, EspModule esp) {
        if (esp == null || !esp.isEnabled()) return;
        EspModule.Style style = esp.getPreviewStyle(0);
        if (esp.getModes().isSelected("3D")) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT
                    | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
            try {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_FOG);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glDepthMask(false);
                if (esp.getThroughWalls().isEnabled()) GL11.glDisable(GL11.GL_DEPTH_TEST);
                net.minecraft.client.renderer.GlStateManager.resetColor();
                WorldRenderUtils.box(box, style.getOutline(), style.getFill(), esp.getLineWidth().getFloat());
            } finally {
                GL11.glPopAttrib();
                net.minecraft.client.renderer.GlStateManager.resetColor();
            }
        }
        if (esp.getModes().isSelected("2D")) {
            ProjectedBounds projected = bounds(box);
            if (projected == null) return;
            ScaledResolution resolution = new ScaledResolution(minecraft);
            if (projected.right < 0 || projected.bottom < 0 || projected.left > resolution.getScaledWidth()
                    || projected.top > resolution.getScaledHeight()) return;
            projected.left = Math.max(-8, projected.left);
            projected.top = Math.max(-8, projected.top);
            projected.right = Math.min(resolution.getScaledWidth() + 8, projected.right);
            projected.bottom = Math.min(resolution.getScaledHeight() + 8, projected.bottom);
            if (projected.width() > 2 && projected.height() > 4) {
                float normalizedHealth = Math.max(0, Math.min(20, health / Math.max(1, maximumHealth) * 20));
                screenBoxes.add(new ScreenBox(projected, name, normalizedHealth, distance, heldItem,
                        style.getOutline(), style, 255));
            }
        }
    }

    public void renderWorld(RenderWorldLastEvent event) {
        localProjection = false;
        EspModule esp = Vibe.getInstance().getModuleManager().getModule(EspModule.class);
        screenBoxes.clear();
        if (esp == null || !esp.isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null) {
            return;
        }
        boolean draw3d = esp.getModes().isSelected("3D");
        boolean capture2d = esp.getModes().isSelected("2D");
        if (!draw3d && !capture2d) return;
        if (capture2d) {
            captureMatrices();
        }
        if (draw3d) {
            WorldRenderUtils.begin(esp.getThroughWalls().isEnabled());
        }
        try {
            TargetsModule targets = Vibe.getInstance().getModuleManager().getModule(TargetsModule.class);
            QolModule qol = Vibe.getInstance().getModuleManager().getModule(QolModule.class);
            for (Object object : minecraft.theWorld.loadedEntityList) {
                if (!(object instanceof EntityLivingBase)) {
                    continue;
                }
                EntityLivingBase player = (EntityLivingBase) object;
                boolean antiInvisible = isAntiInvisible(qol, player);
                boolean local = player == minecraft.thePlayer;
                if (local ? minecraft.gameSettings.thirdPersonView == 0 || !player.isEntityAlive()
                        : targets == null || (!targets.canVisualize(player) && !antiInvisible)) {
                    continue;
                }
                AxisAlignedBB box = renderBox(player, event.partialTicks);
                EspModule.Style style = esp.getStyleFor(player);
                if (draw3d) {
                    int outline = applyVisibilityAlpha(style.getOutline(), antiInvisible, qol);
                    int fill = applyVisibilityAlpha(style.getFill(), antiInvisible, qol);
                    WorldRenderUtils.box(box, outline, fill,
                            esp.getLineWidth().getFloat());
                }
                if (capture2d) {
                    ProjectedBounds bounds = bounds(box);
                    if (bounds != null && bounds.width() > .25F && bounds.height() > .5F) {
                        ItemStack held = player.getHeldItem();
                        String displayName = player instanceof EntityPlayer
                                ? ((EntityPlayer) player).getDisplayName().getFormattedText() : player.getName();
                        dev.vibe.module.impl.NameProtectModule protect = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.NameProtectModule.class);
                        if (protect != null && protect.isEnabled()) displayName = protect.protectText(displayName);
                        screenBoxes.add(new ScreenBox(bounds, displayName, player.getHealth() + player.getAbsorptionAmount(),
                                minecraft.thePlayer.getDistanceToEntity(player), held == null ? "" : held.getDisplayName(),
                                applyVisibilityAlpha(style.getOutline(), antiInvisible, qol),
                                style,
                                antiInvisible ? qol.getInvisibleAlpha().getInt() : 255));
                    }
                }
            }
        } finally {
            if (draw3d) {
                WorldRenderUtils.end(esp.getThroughWalls().isEnabled());
            }
        }
    }

    public void renderOverlay() {
        EspModule esp = Vibe.getInstance().getModuleManager().getModule(EspModule.class);
        if (esp == null || !esp.isEnabled() || !esp.getModes().isSelected("2D")) {
            return;
        }
        FontRenderer font = minecraft.fontRendererObj;
        for (ScreenBox box : screenBoxes) {
            drawTacticalBox(box, esp, font);
        }
    }

    private void drawTacticalBox(ScreenBox data, EspModule esp, FontRenderer font) {
        int left = (int) Math.floor(data.bounds.left);
        int top = (int) Math.floor(data.bounds.top);
        int right = Math.max(left + 1, (int) Math.ceil(data.bounds.right));
        int bottom = Math.max(top + 1, (int) Math.ceil(data.bounds.bottom));
        int accent = data.color;
        EspModule.Style style = data.style;
        int margin = style.hasDepth() ? 1 : 0;
        if (!DebugOverlay.overlaps(left - margin, top - margin, right + margin, bottom + margin)) {
            drawTransparentFill(left, top, right, bottom, scaleAlpha(style.getFill(), data.visibilityAlpha));
            int shortSide = Math.max(1, Math.min(right - left, bottom - top));
            int thickness = Math.max(1, Math.min(3, Math.round(shortSide * 0.018F)));
            int desiredLength = Math.round(shortSide * 0.24F);
            int maximumLength = Math.max(thickness, (shortSide - thickness) / 2);
            int cornerLength = Math.max(thickness, Math.min(maximumLength, desiredLength));
            if (style.hasDepth()) {
                RenderUtils.tacticalCorners(left - 1, top - 1, right + 1, bottom + 1, 0xB8000000,
                    thickness + 2, cornerLength + 2);
            }
            RenderUtils.tacticalCorners(left, top, right, bottom, accent, thickness, cornerLength);
        }
        if (style.hasHealth()) {
            float ratio = Math.max(0.0F, Math.min(1.0F, data.health / 20.0F));
            int barWidth = style.getHealthWidth();
            boolean healthRight = "Right".equalsIgnoreCase(style.getHealthPosition());
            int barLeft = healthRight ? right + 4 : left - 5 - barWidth + 2;
            if (!DebugOverlay.overlaps(barLeft - margin, top - margin, barLeft + barWidth + margin, bottom + margin)) {
                if (style.hasDepth()) {
                    Gui.drawRect(barLeft - 1, top - 1, barLeft + barWidth + 1, bottom + 1, 0xB8000000);
                }
                Gui.drawRect(barLeft, top, barLeft + barWidth, bottom, 0xA0000000);
                int healthTop = bottom - Math.round((bottom - top) * ratio);
                int healthColor = style.hasHealthFade()
                        ? RenderUtils.blend(style.getHealthStart(), style.getHealthEnd(), 1.0F - ratio)
                        : RenderUtils.healthColor(data.health);
                Gui.drawRect(barLeft, healthTop, barLeft + barWidth, bottom, healthColor);
            }
        }
        java.util.List<TacticalLabel> labels = new java.util.ArrayList<TacticalLabel>();
        if (style.hasNames()) {
            String label = data.name;
            if (style.hasDistance() && "Name".equalsIgnoreCase(style.getDistancePosition())) label += "  " + Math.round(data.distance) + "m";
            labels.add(new TacticalLabel(label, style.getNamePosition(), RenderUtils.TEXT));
        }
        if (style.hasDistance() && !"Name".equalsIgnoreCase(style.getDistancePosition())) {
            labels.add(new TacticalLabel(Math.round(data.distance) + "m", style.getDistancePosition(), RenderUtils.MUTED));
        }
        if (style.hasHeld() && !data.heldItem.isEmpty()) {
            labels.add(new TacticalLabel(data.heldItem, style.getHeldPosition(), RenderUtils.MUTED));
        }
        drawAlignedLabels(font, labels, left, top, right, bottom, style);
    }

    /** Keep labels on a shared side in a deterministic stack so they never overlap. */
    private void drawAlignedLabels(FontRenderer font, java.util.List<TacticalLabel> labels, int left, int top, int right, int bottom, EspModule.Style style) {
        int topCount = 0, bottomCount = 0, leftCount = 0, rightCount = 0;
        int healthOffset = style.hasHealth() && "Left".equalsIgnoreCase(style.getHealthPosition()) ? style.getHealthWidth() + 5 : 0;
        int healthRightOffset = style.hasHealth() && "Right".equalsIgnoreCase(style.getHealthPosition()) ? style.getHealthWidth() + 5 : 0;
        for (TacticalLabel label : labels) {
            if (label.text == null || label.text.isEmpty()) continue;
            int width = font.getStringWidth(label.text); int x; int y;
            if ("Bottom".equalsIgnoreCase(label.position)) { x = (left + right - width) / 2; y = bottom + 3 + bottomCount++ * (font.FONT_HEIGHT + 2); }
            else if ("Left".equalsIgnoreCase(label.position)) { x = left - healthOffset - width - 4; y = top + 4 + leftCount++ * (font.FONT_HEIGHT + 2); }
            else if ("Right".equalsIgnoreCase(label.position)) { x = right + healthRightOffset + 4; y = top + 4 + rightCount++ * (font.FONT_HEIGHT + 2); }
            else { x = (left + right - width) / 2; y = top - 10 - topCount++ * (font.FONT_HEIGHT + 2); }
            if (!DebugOverlay.overlaps(x, y, x + width + 1, y + font.FONT_HEIGHT)) {
                font.drawStringWithShadow(label.text, x, y, label.color);
            }
        }
    }

    /**
     * HUD rendering normally uses {@link Gui#drawRect}, but its blend state is
     * shared with every overlay drawn before ESP (blur, text glow, etc.).  A
     * changed blend function made the tactical fill render as opaque on some
     * frames.  Render this one quad with its own standard alpha state so the
     * alpha selected in the ESP fill colour is always honoured.
     */
    private void drawTransparentFill(int left, int top, int right, int bottom, int color) {
        if (right <= left || bottom <= top || ((color >>> 24) & 255) == 0) {
            return;
        }
        // This is an overlay, so make every state which can turn an alpha
        // colour into an opaque quad explicit.  In particular, renderers
        // before the HUD event may leave a depth write or additive blend mode
        // behind.  GL_CURRENT_BIT also restores the colour after the quad.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            // Use the conventional source-over mode for both colour and
            // alpha.  Keeping a separate alpha blend equation here can turn
            // the fill opaque with certain driver/state combinations.
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(((color >>> 16) & 255) / 255.0F, ((color >>> 8) & 255) / 255.0F,
                    (color & 255) / 255.0F, ((color >>> 24) & 255) / 255.0F);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2i(left, bottom);
            GL11.glVertex2i(right, bottom);
            GL11.glVertex2i(right, top);
            GL11.glVertex2i(left, top);
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private AxisAlignedBB renderBox(EntityLivingBase player, float partialTicks) {
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - minecraft.getRenderManager().viewerPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - minecraft.getRenderManager().viewerPosY;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - minecraft.getRenderManager().viewerPosZ;
        AxisAlignedBB original = player.getEntityBoundingBox();
        return original.offset(-player.posX, -player.posY, -player.posZ).offset(x, y, z).expand(0.06D, 0.10D, 0.06D);
    }

    private int outlineColor(EspModule esp, EntityLivingBase entity) {
        if (entity instanceof EntityPlayer && Vibe.getInstance().getTargetManager() != null
                && Vibe.getInstance().getTargetManager().isTarget((EntityPlayer) entity)) return esp.getTargetColor().getArgb();
        if (entity instanceof EntityPlayer && Vibe.getInstance().getFriendManager() != null
                && Vibe.getInstance().getFriendManager().isFriend((EntityPlayer) entity)) return esp.getFriendColor().getArgb();
        return esp.getOutlineColor().getArgb();
    }

    private boolean isAntiInvisible(QolModule qol, EntityLivingBase entity) {
        return entity instanceof EntityPlayer && entity.isInvisible() && qol != null && qol.isEnabled()
                && qol.getFeatures().isSelected("AntiInvisibility");
    }

    private int applyVisibilityAlpha(int color, boolean antiInvisible, QolModule qol) {
        return antiInvisible ? scaleAlpha(color, qol.getInvisibleAlpha().getInt()) : color;
    }

    private int scaleAlpha(int color, int alpha) {
        int original = color >>> 24 & 255;
        return RenderUtils.alpha(color, Math.round(original * Math.max(0, Math.min(255, alpha)) / 255.0F));
    }

    private void captureMatrices() {
        modelView.clear();
        projection.clear();
        viewport.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        modelView.rewind();
        projection.rewind();
        viewport.rewind();
    }

    private ProjectedBounds bounds(AxisAlignedBB box) {
        if (!localProjection) {
            float[] model = new float[16], camera = new float[16];
            for (int i = 0; i < 16; i++) { model[i] = modelView.get(i); camera[i] = projection.get(i); }
            ScaledResolution resolution = new ScaledResolution(minecraft);
            double[] clipped = BoxProjection.bounds(new double[] {box.minX, box.minY, box.minZ},
                    new double[] {box.maxX, box.maxY, box.maxZ}, model, camera,
                    resolution.getScaledWidth_double(), resolution.getScaledHeight_double());
            if (clipped == null) return null;
            ProjectedBounds result = new ProjectedBounds();
            result.include((float) clipped[0], (float) clipped[1]);
            result.include((float) clipped[2], (float) clipped[3]);
            return result;
        }
        double[][] corners = {
                {box.minX, box.minY, box.minZ}, {box.minX, box.minY, box.maxZ},
                {box.minX, box.maxY, box.minZ}, {box.minX, box.maxY, box.maxZ},
                {box.maxX, box.minY, box.minZ}, {box.maxX, box.minY, box.maxZ},
                {box.maxX, box.maxY, box.minZ}, {box.maxX, box.maxY, box.maxZ}
        };
        ProjectedBounds bounds = new ProjectedBounds();
        for (double[] corner : corners) {
            ProjectedPoint point = project(corner[0], corner[1], corner[2]);
            if (point != null && point.depth >= 0.0F && point.depth <= 1.0F) {
                bounds.include(point.x, point.y);
            }
        }
        return bounds.hasPoints ? bounds : null;
    }

    private ProjectedPoint project(double x, double y, double z) {
        modelView.rewind();
        projection.rewind();
        viewport.rewind();
        window.clear();
        if (!GLU.gluProject((float) x, (float) y, (float) z, modelView, projection, viewport, window)) {
            return null;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft);
        if (localProjection) {
            // GTA7 may supersample its framebuffer; project back into the GUI's own dimensions.
            float xScale=(float)resolution.getScaledWidth()/Math.max(1,viewport.get(2));
            float yScale=(float)resolution.getScaledHeight()/Math.max(1,viewport.get(3));
            return new ProjectedPoint((window.get(0)-viewport.get(0))*xScale,
                    (viewport.get(3)-(window.get(1)-viewport.get(1)))*yScale,window.get(2));
        }
        float scaleX = (float) resolution.getScaledWidth() / minecraft.displayWidth;
        float scaleY = (float) resolution.getScaledHeight() / minecraft.displayHeight;
        return new ProjectedPoint(window.get(0) * scaleX, (minecraft.displayHeight - window.get(1)) * scaleY, window.get(2));
    }

    private static final class ScreenBox {
        private final ProjectedBounds bounds;
        private final String name;
        private final float health;
        private final float distance;
        private final String heldItem;
        private final int color;
        private final EspModule.Style style;
        private final int visibilityAlpha;

        private ScreenBox(ProjectedBounds bounds, String name, float health, float distance, String heldItem, int color, EspModule.Style style, int visibilityAlpha) {
            this.bounds = bounds;
            this.name = name;
            this.health = health;
            this.distance = distance;
            this.heldItem = heldItem;
            this.color = color;
            this.style = style;
            this.visibilityAlpha = visibilityAlpha;
        }
    }

    private static final class TacticalLabel {
        private final String text;
        private final String position;
        private final int color;
        private TacticalLabel(String text, String position, int color) { this.text = text; this.position = position; this.color = color; }
    }

    private static final class ProjectedPoint {
        private final float x;
        private final float y;
        private final float depth;

        private ProjectedPoint(float x, float y, float depth) {
            this.x = x;
            this.y = y;
            this.depth = depth;
        }
    }

    private static final class ProjectedBounds {
        private float left = Float.MAX_VALUE;
        private float top = Float.MAX_VALUE;
        private float right = -Float.MAX_VALUE;
        private float bottom = -Float.MAX_VALUE;
        private boolean hasPoints;

        private void include(float x, float y) {
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x);
            bottom = Math.max(bottom, y);
            hasPoints = true;
        }

        private float width() {
            return right - left;
        }

        private float height() {
            return bottom - top;
        }
    }
}
