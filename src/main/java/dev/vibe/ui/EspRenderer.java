package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.EspModule;
import dev.vibe.module.impl.TargetsModule;
import dev.vibe.module.impl.QolModule;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Captures player bounds in world render and draws the 2D variant in the HUD pass. */
public final class EspRenderer {

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final List<ScreenBox> screenBoxes = new ArrayList<ScreenBox>();
    private final Esp2DRenderer overlay = new Esp2DRenderer();

    /** GTA7 owns a separate instance, so its camera never overwrites Minecraft's captured ESP. */
    public void beginLocalFrame() {
        screenBoxes.clear();
        captureMatrices();
    }

    /** Render local actors through the exact same 3D box and tactical HUD paths as Minecraft. */
    public void captureLocalActor(AxisAlignedBB box, String name, float health, float maximumHealth,
                                  float distance, String heldItem, EspModule esp) {
        captureLocalActor(box, name, health, maximumHealth, 0, distance, heldItem, null, new ItemStack[0], esp);
    }

    public void captureLocalActor(AxisAlignedBB box, String name, float health, float maximumHealth,
                                  float armor, float distance, String heldItem, ItemStack item, ItemStack[] equipment, EspModule esp) {
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
                Esp2DRenderer.Actor actor = new Esp2DRenderer.Actor(name, health, maximumHealth, armor, distance, heldItem, item, equipment, 1);
                if(item==null && heldItem!=null && !heldItem.isEmpty()) actor.nativeItem=heldItem.toLowerCase(java.util.Locale.ROOT).contains("pistol")?"Pistol":heldItem.toLowerCase(java.util.Locale.ROOT).contains("knife")?"Knife":"Rifle";
                screenBoxes.add(new ScreenBox(projected, actor));
            }
        }
    }

    public void renderWorld(RenderWorldLastEvent event) {
        EspModule esp = Vibe.getInstance().getModuleManager().getModule(EspModule.class);
        screenBoxes.clear();
        dev.vibe.module.impl.HypixelModule hypixel = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.HypixelModule.class);
        if (hypixel != null && hypixel.suppressVisuals()) return;
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
                    int profile=esp.resolvedProfile(esp.profileFor(player));
                    if(esp.getThroughWalls(profile).isEnabled())net.minecraft.client.renderer.GlStateManager.disableDepth();else net.minecraft.client.renderer.GlStateManager.enableDepth();
                    int outline = applyVisibilityAlpha(style.getOutline(), antiInvisible, qol);
                    int fill = applyVisibilityAlpha(style.getFill(), antiInvisible, qol);
                    WorldRenderUtils.box(box, outline, fill,
                            esp.getLineWidth(profile).getFloat());
                }
                if (capture2d) {
                    ProjectedBounds bounds = bounds(box);
                    if (bounds != null && bounds.width() > .25F && bounds.height() > .5F) {
                        ItemStack held = player.getHeldItem();
                        String displayName = player instanceof EntityPlayer
                                ? ((EntityPlayer) player).getDisplayName().getFormattedText() : player.getName();
                        dev.vibe.module.impl.NameProtectModule protect = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.NameProtectModule.class);
                        if (protect != null && protect.isEnabled()) displayName = protect.protectText(displayName);
                        ItemStack[] armor = new ItemStack[4];
                        for (int slot = 0; slot < 4; slot++) armor[3-slot] = player.getEquipmentInSlot(slot + 1);
                        Esp2DRenderer.Actor actor=new Esp2DRenderer.Actor(displayName,
                                player.getHealth() + player.getAbsorptionAmount(), player.getMaxHealth() + player.getAbsorptionAmount(),
                                player.getTotalArmorValue(), minecraft.thePlayer.getDistanceToEntity(player),
                                held == null ? "" : held.getDisplayName(), held, armor,
                                antiInvisible ? qol.getInvisibleAlpha().getFloat() / 255F : 1);
                        actor.profile=esp.resolvedProfile(esp.profileFor(player));actor.teamColor=esp.teamColor(player);actor.hurt=player.hurtTime>0;
                        actor.forcedColor=hypixel==null?0:hypixel.visualColor(player);
                        screenBoxes.add(new ScreenBox(bounds,actor));
                    }
                }
            }
        } finally {
            if (draw3d) {
                WorldRenderUtils.end(true);
            }
        }
    }

    public void renderOverlay() {
        EspModule esp = Vibe.getInstance().getModuleManager().getModule(EspModule.class);
        dev.vibe.module.impl.HypixelModule hypixel = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.HypixelModule.class);
        if (hypixel != null && hypixel.suppressVisuals()) return;
        if (esp == null || !esp.isEnabled() || !esp.getModes().isSelected("2D")) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft);
        for (ScreenBox box : screenBoxes) {
            overlay.draw(esp.get2D(box.actor.profile), box.actor, new EspLayout.Rect(box.bounds.left, box.bounds.top,
                    box.bounds.width(), box.bounds.height()), resolution.getScaledWidth(), resolution.getScaledHeight(), false);
        }
    }

    private AxisAlignedBB renderBox(EntityLivingBase player, float partialTicks) {
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - minecraft.getRenderManager().viewerPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - minecraft.getRenderManager().viewerPosY;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - minecraft.getRenderManager().viewerPosZ;
        AxisAlignedBB original = player.getEntityBoundingBox();
        return original.offset(-player.posX, -player.posY, -player.posZ).offset(x, y, z).expand(0.06D, 0.10D, 0.06D);
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
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        modelView.rewind();
        projection.rewind();
    }

    private ProjectedBounds bounds(AxisAlignedBB box) {
        float[] model = new float[16], camera = new float[16];
        for (int i = 0; i < 16; i++) { model[i] = modelView.get(i); camera[i] = projection.get(i); }
        ScaledResolution resolution = new ScaledResolution(minecraft);
        // Both cameras clip in homogeneous space. GTA7's supersampled viewport maps to the same GUI dimensions.
        double[] clipped = BoxProjection.bounds(new double[] {box.minX, box.minY, box.minZ},
                new double[] {box.maxX, box.maxY, box.maxZ}, model, camera,
                resolution.getScaledWidth_double(), resolution.getScaledHeight_double());
        if (clipped == null) return null;
        ProjectedBounds result = new ProjectedBounds();
        result.include((float)clipped[0], (float)clipped[1]); result.include((float)clipped[2], (float)clipped[3]);
        return result;
    }

    private static final class ScreenBox {
        private final ProjectedBounds bounds;
        private final Esp2DRenderer.Actor actor;
        private ScreenBox(ProjectedBounds bounds, Esp2DRenderer.Actor actor) { this.bounds = bounds; this.actor = actor; }
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
