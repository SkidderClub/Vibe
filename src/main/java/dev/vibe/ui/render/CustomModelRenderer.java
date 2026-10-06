package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.model.BodyPart;
import dev.vibe.model.CompiledModel;
import dev.vibe.model.SwordFitter;
import dev.vibe.module.impl.meme.HypixelModule;
import dev.vibe.module.impl.visual.CustomModelRendererModule;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Draws custom models in place of held swords and player bodies. Called from the RenderItem
 * hook installed by {@code dev.vibe.core.CustomModelTransformer} and from the ModelBase.render
 * hook in {@code ChamsRenderer}; both run inside vanilla's transforms, so models inherit every
 * vanilla pose, item camera transform, hurt tint and ESP chams pass.
 */
public final class CustomModelRenderer {

    private static final ResourceLocation GLINT = new ResourceLocation("textures/misc/enchanted_item_glint.png");
    private static final FloatBuffer COLOR = BufferUtils.createFloatBuffer(16);
    private static final float[] TINT = new float[4];
    private static final float[] GLINT_TINT = {1.0F, 1.0F, 1.0F, 1.0F};
    private static final float[] ROTATION = new float[3];
    private static final float[] OFFSET = new float[3];
    private static final float RADIANS_TO_DEGREES = (float) (180.0D / Math.PI);
    private static CustomModelRendererModule module;
    private static EntityLivingBase heldItemOwner;
    private static EntityPlayer scaledPlayer;
    private static boolean drawing;

    // Saved GL state, restored after every custom draw.
    private static boolean cull, blend, alpha;
    private static int alphaFunction, shadeModel, boundTexture;
    private static float alphaReference;

    private CustomModelRenderer() {
    }

    private static CustomModelRendererModule module() {
        if (module == null) {
            Vibe vibe = Vibe.getInstance();
            if (vibe == null || vibe.getModuleManager() == null) return null;
            module = vibe.getModuleManager().getModule(CustomModelRendererModule.class);
        }
        return module;
    }

    // ---- RenderItem hooks ----

    /** Hook at the start of RenderItem.renderItemModelForEntity: remembers who holds the item. */
    public static void holder(Object entity) {
        heldItemOwner = entity instanceof EntityLivingBase ? (EntityLivingBase) entity : null;
    }

    /** Replaces RenderItem.renderItem(stack, model) inside renderItemModelTransform. */
    public static void renderItem(Object renderer, Object stack, Object model, Object transform) {
        ItemStack item = (ItemStack) stack;
        if (!drawing && renderSword(item, (ItemCameraTransforms.TransformType) transform)) return;
        ((RenderItem) renderer).renderItem(item, (IBakedModel) model);
    }

    private static boolean renderSword(ItemStack stack, ItemCameraTransforms.TransformType transform) {
        if (stack == null || !(stack.getItem() instanceof ItemSword)) return false;
        CustomModelRendererModule settings = module();
        if (settings == null || !settings.isEnabled() || HypixelModule.visualsSuppressed()) return false;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (transform == ItemCameraTransforms.TransformType.FIRST_PERSON) {
            if (!settings.getSwordViews().isSelected("First Person")) return false;
        } else if (transform == ItemCameraTransforms.TransformType.THIRD_PERSON) {
            boolean self = heldItemOwner == null || heldItemOwner == minecraft.thePlayer;
            if (!settings.getSwordViews().isSelected(self ? "Third Person" : "Other Players")) return false;
        } else {
            return false;
        }
        CompiledModel compiled = settings.swordModel();
        if (compiled == null || compiled.getAnchors() == null) return false;
        drawing = true;
        try {
            ROTATION[0] = settings.getSwordRotateX().getFloat();
            ROTATION[1] = settings.getSwordRotateY().getFloat();
            ROTATION[2] = settings.getSwordRotateZ().getFloat();
            OFFSET[0] = settings.getSwordOffsetX().getFloat();
            OFFSET[1] = settings.getSwordOffsetY().getFloat();
            OFFSET[2] = settings.getSwordOffsetZ().getFloat();
            drawSword(compiled, settings.getSwordScale().getFloat(), ROTATION, OFFSET,
                    settings.getReverseBlade().isEnabled(), settings.getFlipBlade().isEnabled(),
                    stack.hasEffect() && settings.getEnchantGlint().isEnabled());
        } finally {
            drawing = false;
        }
        return true;
    }

    /**
     * Draws a {@link SwordFitter}-fitted model in the space RenderItem.renderItem draws an item's
     * quads in, so it lands where the sword sprite would. Rotation and scale act around the grip.
     */
    public static void drawSword(CompiledModel compiled, float scale, float[] rotation, float[] offset, boolean reverse,
            boolean flip, boolean glint) {
        float[] anchors = compiled.getAnchors();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.scale(0.5F, 0.5F, 0.5F);
            GlStateManager.translate(-0.5F, -0.5F, -0.5F);
            float gripX = anchors[0], gripY = anchors[1], gripZ = anchors[2];
            GlStateManager.translate(gripX + offset[0], gripY + offset[1], gripZ + offset[2]);
            GlStateManager.rotate(rotation[2], 0.0F, 0.0F, 1.0F);
            GlStateManager.rotate(rotation[1], 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(rotation[0], 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(scale, scale, scale);
            GlStateManager.translate(-gripX, -gripY, -gripZ);
            float baseX = (float) SwordFitter.ANCHOR[0], baseY = (float) SwordFitter.ANCHOR[1], baseZ = (float) SwordFitter.ANCHOR[2];
            if (reverse) {
                float middleX = (baseX + anchors[3]) * 0.5F, middleY = (baseY + anchors[4]) * 0.5F;
                GlStateManager.translate(middleX, middleY, baseZ);
                GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.translate(-middleX, -middleY, -baseZ);
            }
            if (flip) {
                GlStateManager.translate(baseX, baseY, baseZ);
                GlStateManager.rotate(180.0F, anchors[3] - baseX, anchors[4] - baseY, 0.0F);
                GlStateManager.translate(-baseX, -baseY, -baseZ);
            }
            begin();
            try {
                compiled.drawPart(0, 0, TINT, false);
                if (glint) drawGlint(compiled);
            } finally {
                end();
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /** Vanilla's two scrolling glint layers (RenderItem.renderEffect) over the custom geometry. */
    private static void drawGlint(CompiledModel compiled) {
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(GL11.GL_EQUAL);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
        Minecraft.getMinecraft().getTextureManager().bindTexture(GLINT);
        GlStateManager.color(0.5F, 0.25F, 0.8F, 1.0F);
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        for (int layer = 0; layer < 2; layer++) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5F, 0.5F, 0.5F);
            long period = layer == 0 ? 3000L : 4873L;
            float offset = (float) (Minecraft.getSystemTime() % period) / period / 8.0F;
            GlStateManager.translate(layer == 0 ? offset : -offset, 0.0F, 0.0F);
            GlStateManager.rotate(layer == 0 ? -50.0F : 10.0F, 0.0F, 0.0F, 1.0F);
            compiled.drawPart(0, 0, GLINT_TINT, true);
            GlStateManager.popMatrix();
        }
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableLighting();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
    }

    // ---- player body hooks (from ChamsRenderer) ----

    /**
     * Draws the custom player model instead of ModelBase.render. Returns false when vanilla
     * should render, e.g. while the model is still loading.
     */
    public static boolean renderBody(ModelBase model, Entity entity, float swing, float amount, float age,
            float yaw, float pitch, float scale) {
        if (drawing || !(model instanceof ModelBiped) || !(entity instanceof EntityPlayer)) return false;
        CustomModelRendererModule settings = module();
        if (settings == null || !settings.appliesToPlayer((EntityPlayer) entity) || HypixelModule.visualsSuppressed()) return false;
        CompiledModel compiled = settings.playerModel();
        if (compiled == null || compiled.getPivots() == null) return false;
        ModelBiped biped = (ModelBiped) model;
        // ModelBiped.render would compute the pose first; layers such as the held item rely on it.
        biped.setRotationAngles(swing, amount, age, yaw, pitch, scale, entity);
        int lod = 0;
        Entity viewer = Minecraft.getMinecraft().getRenderViewEntity();
        double lodDistance = settings.getLodDistance().getDouble();
        if (compiled.lodCount() > 1 && viewer != null && viewer != entity
                && entity.getDistanceSqToEntity(viewer) > lodDistance * lodDistance) lod = 1;
        drawing = true;
        try {
            drawPlayer(compiled, biped, lod, scale, entity.isSneaking(), settings.getAnimateLimbs().isEnabled());
        } finally {
            drawing = false;
        }
        return true;
    }

    /**
     * Scales a whole player render (model, held item, armor and name tag) around the feet for
     * Player Scale, so a larger or smaller character still holds its sword in hand. Called from
     * RenderPlayerEvent.Pre at the lowest priority; {@link #endPlayerScale} undoes it in Post.
     */
    public static void beginPlayerScale(EntityPlayer player, double x, double y, double z) {
        if (scaledPlayer != null || player == null) return;
        CustomModelRendererModule settings = module();
        if (settings == null || !settings.appliesToPlayer(player) || HypixelModule.visualsSuppressed()) return;
        float size = settings.getPlayerScale().getFloat();
        if (Math.abs(size - 1.0F) < 0.001F || settings.playerModel() == null) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.scale(size, size, size);
        GlStateManager.translate(-x, -y, -z);
        scaledPlayer = player;
    }

    public static void endPlayerScale(EntityPlayer player) {
        if (player == null || scaledPlayer != player) return;
        scaledPlayer = null;
        GlStateManager.popMatrix();
    }

    /**
     * Draws a {@link dev.vibe.model.PlayerFitter}-fitted model posed by {@code biped}, whose
     * rotation angles must already be set. Runs in ModelBase.render's space; {@code scale} is
     * the model pixel size vanilla passes (1/16).
     */
    public static void drawPlayer(CompiledModel compiled, ModelBiped biped, int lod, float scale, boolean sneaking, boolean animate) {
        GlStateManager.pushMatrix();
        try {
            if (sneaking) GlStateManager.translate(0.0F, 0.2F, 0.0F);
            GlStateManager.scale(scale, scale, scale);
            begin();
            try {
                float[][] pivots = compiled.getPivots();
                part(compiled, lod, pivots, BodyPart.BODY, biped.bipedBody, animate);
                part(compiled, lod, pivots, BodyPart.HEAD, biped.bipedHead, animate);
                part(compiled, lod, pivots, BodyPart.RIGHT_ARM, biped.bipedRightArm, animate);
                part(compiled, lod, pivots, BodyPart.LEFT_ARM, biped.bipedLeftArm, animate);
                part(compiled, lod, pivots, BodyPart.RIGHT_LEG, biped.bipedRightLeg, animate);
                part(compiled, lod, pivots, BodyPart.LEFT_LEG, biped.bipedLeftLeg, animate);
            } finally {
                end();
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static void part(CompiledModel compiled, int lod, float[][] pivots, BodyPart part, ModelRenderer renderer,
            boolean animate) {
        int index = part.ordinal();
        if (!compiled.hasPart(lod, index)) return;
        float[] pivot = pivots[index];
        GlStateManager.pushMatrix();
        if (animate) {
            // Follow vanilla's joint: its offset from the resting position and its rotation.
            GlStateManager.translate(pivot[0] + renderer.rotationPointX - part.restX, pivot[1] + renderer.rotationPointY - part.restY,
                    pivot[2] + renderer.rotationPointZ - part.restZ);
            if (renderer.rotateAngleZ != 0.0F) GlStateManager.rotate(renderer.rotateAngleZ * RADIANS_TO_DEGREES, 0.0F, 0.0F, 1.0F);
            if (renderer.rotateAngleY != 0.0F) GlStateManager.rotate(renderer.rotateAngleY * RADIANS_TO_DEGREES, 0.0F, 1.0F, 0.0F);
            if (renderer.rotateAngleX != 0.0F) GlStateManager.rotate(renderer.rotateAngleX * RADIANS_TO_DEGREES, 1.0F, 0.0F, 0.0F);
            GlStateManager.translate(-pivot[0], -pivot[1], -pivot[2]);
        }
        compiled.drawPart(lod, index, TINT, false);
        GlStateManager.popMatrix();
    }

    /** Armor is shaped for vanilla's body, so it is hidden while a custom model is drawn instead. */
    public static boolean hidesArmor(Object entity) {
        if (!(entity instanceof EntityPlayer)) return false;
        CustomModelRendererModule settings = module();
        return settings != null && settings.getHideArmor().isEnabled() && settings.appliesToPlayer((EntityPlayer) entity)
                && !HypixelModule.visualsSuppressed() && settings.playerModel() != null;
    }

    // ---- GL state ----

    /** Saves the state custom drawing changes and switches to smooth shading without culling. Not reentrant. */
    private static void begin() {
        COLOR.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, COLOR);
        for (int index = 0; index < 4; index++) TINT[index] = COLOR.get(index);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        alphaFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        alphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        shadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        // Layers drawn after the body (capes, cosmetics) may rely on the skin still being bound.
        boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        // Imported meshes rarely have consistent winding, and vanilla's mirrored entity
        // transform flips it anyway; drawing both sides is the robust choice.
        GlStateManager.disableCull();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    private static void end() {
        if (cull) GlStateManager.enableCull();
        else GlStateManager.disableCull();
        if (blend) GlStateManager.enableBlend();
        else GlStateManager.disableBlend();
        if (alpha) GlStateManager.enableAlpha();
        else GlStateManager.disableAlpha();
        GlStateManager.alphaFunc(alphaFunction, alphaReference);
        GlStateManager.shadeModel(shadeModel);
        GlStateManager.bindTexture(boundTexture);
        GlStateManager.color(TINT[0], TINT[1], TINT[2], TINT[3]);
    }
}
