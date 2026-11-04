package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.cosmetic.CosmeticaAccessory;
import dev.vibe.cosmetic.CosmeticaCatalogService;
import dev.vibe.cosmetic.CosmeticaModel;
import dev.vibe.cosmetic.CosmeticPreset;
import dev.vibe.module.impl.visual.CosmeticsModule;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Renders the original JSON model and texture supplied by a Cosmetica catalog accessory. */
public final class CosmeticsRenderer {
    private static final ThreadLocal<CosmeticPreset> PREVIEW_PRESET = new ThreadLocal<CosmeticPreset>();
    private static final ThreadLocal<Boolean> RENDERING = new ThreadLocal<Boolean>();
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Map<UUID, ResourceLocation> customSkins = new HashMap<UUID, ResourceLocation>();
    /** Both vanilla player renderers (default and slim) receive one layer. */
    private final Set<RenderPlayer> layeredRenderers = Collections.newSetFromMap(new IdentityHashMap<RenderPlayer, Boolean>());

    /** Core owns applying/restoring skin overrides and gives privacy masking priority. */
    public ResourceLocation skin(EntityPlayer player) {
        CosmeticsModule module = Vibe.getInstance().getModuleManager().getModule(CosmeticsModule.class);
        CosmeticPreset preset = module != null && module.isEnabled()
                ? dev.vibe.extension.cosmetica.CosmeticaExtension.presets().resolve(player, Vibe.getInstance().getFriendManager()) : null;
        return preset == null ? null : skinFor(player.getUniqueID(), preset.getSkinName());
    }

    /**
     * Registers the accessory layer on the default and slim RenderPlayer
     * instances. Rendering after RenderPlayerEvent.Post is too late: vanilla
     * has already popped the entity transform, so a part can no longer follow
     * its player. Cosmetica itself renders at the equivalent layer stage.
     */
    public void installLayers() {
        if (minecraft.getRenderManager() == null || minecraft.getRenderManager().getSkinMap() == null) return;
        for (RenderPlayer renderer : minecraft.getRenderManager().getSkinMap().values()) {
            if (renderer == null || layeredRenderers.contains(renderer)) continue;
            if (!(renderer.getMainModel() instanceof ModelPlayer)) continue;
            renderer.addLayer(new AccessoriesLayer(this, (ModelPlayer) renderer.getMainModel()));
            layeredRenderers.add(renderer);
        }
    }

    private CosmeticPreset visiblePreset(EntityPlayer player) {
        if (player == null || player.isInvisible() || dev.vibe.module.impl.meme.HypixelModule.visualsSuppressed()) return null;
        CosmeticPreset previewPreset = PREVIEW_PRESET.get();
        if (previewPreset != null) return previewPreset;
        CosmeticsModule module = Vibe.getInstance().getModuleManager().getModule(CosmeticsModule.class);
        if (module == null || !module.isEnabled()) return null;
        CosmeticPreset preset = dev.vibe.extension.cosmetica.CosmeticaExtension.presets().resolve(player, Vibe.getInstance().getFriendManager());
        return preset != null && player == minecraft.thePlayer && preset.isOnlyThirdPerson()
                && minecraft.gameSettings.thirdPersonView == 0 ? null : preset;
    }

    public boolean replacesVanillaCape(EntityPlayer player) {
        if (!player.isWearing(EnumPlayerModelParts.CAPE)) return false;
        CosmeticPreset preset = visiblePreset(player);
        return preset != null && preset.getCape() != null;
    }

    /** Called while the vanilla player-model matrix and limb pose are live. */
    private void renderLayer(AbstractClientPlayer player, ModelPlayer playerModel, float partialTicks, float scale) {
        if (Boolean.TRUE.equals(RENDERING.get())) return;
        CosmeticPreset preset = visiblePreset(player);
        if (preset == null) return;
        List<CosmeticaAccessory> accessories = preset.getAccessories();
        if (accessories.isEmpty()) return;
        CosmeticaAccessory cape = preset.getCape();
        boolean capeVisible = player.isWearing(EnumPlayerModelParts.CAPE) && !CustomModelRenderer.hidesPlayerCape(player);
        boolean hasCloak = capeVisible && (cape != null || minecraft.getNetHandler() != null && player.getLocationCape() != null);
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        RENDERING.set(Boolean.TRUE);
        // Keep direct GL changes scoped so Minecraft's cached state still
        // matches the driver on return. Texture binds go through its manager.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            CosmeticaCatalogService catalog = dev.vibe.extension.cosmetica.CosmeticaExtension.catalog();
            if (cape != null && capeVisible) renderCape(player, playerModel, catalog, cape, partialTicks, scale);
            GL11.glDisable(GL11.GL_CULL_FACE);
            for (CosmeticaAccessory accessory : accessories) {
                if (accessory.isCape() || !accessory.isEnabled() || hiddenByEquipment(player, accessory, hasCloak)) continue;
                renderAccessory(player, playerModel, catalog, accessory, scale);
            }
        } finally {
            GL11.glMatrixMode(matrixMode);
            GL11.glPopAttrib();
            RENDERING.remove();
        }
    }

    private void renderAccessory(AbstractClientPlayer entity, ModelPlayer player, CosmeticaCatalogService catalog,
                                 CosmeticaAccessory accessory, float scale) {
        ModelRenderer part = part(player, accessory); if (part == null || !part.showModel || part.isHidden) return;
        CosmeticaModel model = catalog.getModel(accessory); if (model == null) return;
        minecraft.getTextureManager().bindTexture(catalog.getTexture(accessory, false));
        GL11.glPushMatrix();
        try {
            // 1.8.9 applies this inside ModelBiped.render, outside the
            // individual part pivots. Modern Minecraft bakes it into the pose.
            if (entity.isSneaking()) GL11.glTranslatef(0.0F, 0.2F, 0.0F);
            GL11.glTranslatef(part.offsetX, part.offsetY, part.offsetZ);
            part.postRender(scale);
            GL11.glScalef(1.0F, -1.0F, -1.0F);
            if (accessory.isMirrored()) GL11.glScalef(-1.0F, 1.0F, 1.0F);
            GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);
            applyAttachmentOffset(player, accessory);
            model.render(animationFrame(accessory), accessory.getFrames());
        } finally {
            GL11.glPopMatrix();
        }
    }

    private void renderCape(AbstractClientPlayer player, ModelPlayer model, CosmeticaCatalogService catalog,
                            CosmeticaAccessory cape, float partialTicks, float scale) {
        minecraft.getTextureManager().bindTexture(catalog.getTexture(cape, false));
        int frames = cape.getFrames();
        if (frames > 1) {
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPushMatrix();
            GL11.glTranslatef(0, animationFrame(cape) / (float) frames, 0);
            GL11.glScalef(1, 1.0F / frames, 1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
        GL11.glPushMatrix();
        try {
            // Match 1.8.9 LayerCape motion and let ModelPlayer supply the
            // cape geometry and its own sneaking pivot.
            double dx = lerp(player.prevChasingPosX - player.prevPosX, player.chasingPosX - player.posX, partialTicks);
            double dy = lerp(player.prevChasingPosY - player.prevPosY, player.chasingPosY - player.posY, partialTicks);
            double dz = lerp(player.prevChasingPosZ - player.prevPosZ, player.chasingPosZ - player.posZ, partialTicks);
            float yaw = (float) Math.toRadians(lerp(player.prevRenderYawOffset, player.renderYawOffset, partialTicks));
            float sin = MathHelper.sin(yaw), cos = -MathHelper.cos(yaw);
            float vertical = MathHelper.clamp_float((float) dy * 10, -6, 32);
            float forward = Math.max(0, (float) (dx * sin + dz * cos) * 100);
            float side = (float) (dx * cos - dz * sin) * 50;
            float walk = (float) lerp(player.prevDistanceWalkedModified, player.distanceWalkedModified, partialTicks);
            vertical += MathHelper.sin(walk * 6) * 32 * lerp(player.prevCameraYaw, player.cameraYaw, partialTicks);
            if (player.isSneaking()) vertical += 25;
            GL11.glTranslatef(0, 0, 0.125F);
            GL11.glRotatef(6 + forward * 0.5F + vertical, 1, 0, 0);
            GL11.glRotatef(side, 0, 0, 1);
            GL11.glRotatef(-side, 0, 1, 0);
            GL11.glRotatef(180, 0, 1, 0);
            model.renderCape(scale);
        } finally {
            GL11.glPopMatrix();
            if (frames > 1) {
                GL11.glMatrixMode(GL11.GL_TEXTURE);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
            }
        }
    }

    private static double lerp(double start, double end, float delta) {
        return start + (end - start) * delta;
    }

    private static int animationFrame(CosmeticaAccessory accessory) {
        return (int) Math.floorMod(System.nanoTime() / (50_000_000L * accessory.getTicksPerFrame()), (long) accessory.getFrames());
    }

    private boolean hiddenByEquipment(EntityPlayer player, CosmeticaAccessory accessory, boolean hasCloak) {
        return hidden(accessory, CosmeticaAccessory.HIDE_WITH_HELMET, player.getCurrentArmor(3))
                || hidden(accessory, CosmeticaAccessory.HIDE_WITH_CHESTPLATE, player.getCurrentArmor(2))
                || hidden(accessory, CosmeticaAccessory.HIDE_WITH_LEGGINGS, player.getCurrentArmor(1))
                || hidden(accessory, CosmeticaAccessory.HIDE_WITH_BOOTS, player.getCurrentArmor(0))
                || hasCloak && accessory.hasVisibilityFlag(CosmeticaAccessory.HIDE_WITH_CLOAK);
    }

    private static boolean hidden(CosmeticaAccessory accessory, int flag, ItemStack stack) {
        return accessory.hasVisibilityFlag(flag) && stack != null;
    }

    private ModelRenderer part(ModelPlayer player, CosmeticaAccessory accessory) {
        String point = accessory.getAttachment(); boolean mirror = accessory.isMirrored();
        if ("head".equals(point)) return player.bipedHead;
        if ("body".equals(point) || "cape".equals(point) || "cloak".equals(point)) return player.bipedBody;
        if ("left_arm".equals(point) || "leftarm".equals(point)) return mirror ? player.bipedRightArm : player.bipedLeftArm;
        if ("right_arm".equals(point) || "rightarm".equals(point)) return mirror ? player.bipedLeftArm : player.bipedRightArm;
        if ("left_leg".equals(point) || "leftleg".equals(point)) return mirror ? player.bipedRightLeg : player.bipedLeftLeg;
        if ("right_leg".equals(point) || "rightleg".equals(point)) return mirror ? player.bipedLeftLeg : player.bipedRightLeg;
        return null;
    }

    private static void applyAttachmentOffset(ModelPlayer player, CosmeticaAccessory accessory) {
        String point = accessory.getAttachment();
        float x = 0, y = -8;
        float armInset = player.bipedLeftArm.rotationPointY > 2.25F ? 0.5F : 1;
        if ("head".equals(point)) y = 4;
        else if ("right_arm".equals(point) || "rightarm".equals(point)) { x = armInset; y = -6; }
        else if ("left_arm".equals(point) || "leftarm".equals(point)) { x = -armInset; y = -6; }
        GL11.glTranslatef((accessory.getOffsetX() + x) / 16, (accessory.getOffsetY() + y) / 16, accessory.getOffsetZ() / 16);
    }

    private static final class AccessoriesLayer implements LayerRenderer<AbstractClientPlayer> {
        private final CosmeticsRenderer parent;
        private final ModelPlayer playerModel;

        private AccessoriesLayer(CosmeticsRenderer parent, ModelPlayer playerModel) {
            this.parent = parent;
            this.playerModel = playerModel;
        }

        @Override
        public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                                  float partialTicks, float ageInTicks, float netHeadYaw,
                                  float headPitch, float scale) {
            parent.renderLayer(player, playerModel, partialTicks, scale);
        }

        @Override
        public boolean shouldCombineTextures() {
            return false;
        }
    }

    /** Allows the editor's EntityOtherPlayerMP render pass to use the same real accessory renderer. */
    public static void withPreviewPreset(CosmeticPreset preset, Runnable renderer) {
        if (preset == null || renderer == null) return;
        PREVIEW_PRESET.set(preset);
        try { renderer.run(); } finally { PREVIEW_PRESET.remove(); }
    }

    private ResourceLocation skinFor(UUID uuid, String name) {
        String safe = name == null ? "xheist_" : name.toLowerCase(java.util.Locale.ROOT);
        if ("xheist_".equals(safe)) {
            ResourceLocation bundled = new ResourceLocation("vibe", "cosmetica/skins/xheist_.png");
            if (hasResource(bundled)) return bundled;
        }
        String path = "cosmetic_skin/" + uuid.toString().replace("-", "") + "/" + safe;
        ResourceLocation existing = customSkins.get(uuid); if (existing != null && path.equals(existing.getResourcePath())) return existing;
        ResourceLocation result = new ResourceLocation("vibe", path);
        try { minecraft.getTextureManager().loadTexture(result, new ThreadDownloadImageData(null, "https://minotar.net/skin/" + safe, DefaultPlayerSkin.getDefaultSkinLegacy(), null)); } catch (Throwable ignored) { }
        customSkins.put(uuid, result); return result;
    }
    private boolean hasResource(ResourceLocation location) { try { minecraft.getResourceManager().getResource(location); return true; } catch (Exception ignored) { return false; } }
}
