package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.cosmetic.CosmeticaAccessory;
import dev.vibe.cosmetic.CosmeticaCatalogService;
import dev.vibe.cosmetic.CosmeticaModel;
import dev.vibe.cosmetic.CosmeticPreset;
import dev.vibe.module.impl.CosmeticsModule;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;
import org.lwjgl.opengl.GL11;

/** Renders the original JSON model and texture supplied by a Cosmetica catalog accessory. */
public final class CosmeticsRenderer {
    private static final ThreadLocal<CosmeticPreset> PREVIEW_PRESET = new ThreadLocal<CosmeticPreset>();
    private static final ThreadLocal<Boolean> RENDERING = new ThreadLocal<Boolean>();
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Map<UUID, ResourceLocation> customSkins = new HashMap<UUID, ResourceLocation>();
    private final Map<UUID, ResourceLocation> originalSkins = new HashMap<UUID, ResourceLocation>();
    /** Both vanilla player renderers (default and slim) receive one layer. */
    private final Set<RenderPlayer> layeredRenderers = Collections.newSetFromMap(new IdentityHashMap<RenderPlayer, Boolean>());
    private Field skinField;
    private boolean searchedSkinField;

    /** Runs before vanilla binds a player texture, allowing only assigned presets to override a skin. */
    public void prepareSkin(RenderPlayerEvent.Pre event) {
        EntityPlayer player = event.entityPlayer;
        if (player == null || minecraft.getNetHandler() == null) return;
        NetworkPlayerInfo info = minecraft.getNetHandler().getPlayerInfo(player.getUniqueID()); if (info == null) return;
        dev.vibe.module.impl.NameProtectModule nameProtect = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.NameProtectModule.class);
        if (nameProtect != null && nameProtect.hidesOthers() && player != minecraft.thePlayer) {
            try { Field field = findSkinField(); if (field != null) field.set(info, skinFor(player.getUniqueID(), "GommeHD")); } catch (Throwable ignored) { }
            return;
        }
        CosmeticsModule module = Vibe.getInstance().getModuleManager().getModule(CosmeticsModule.class);
        CosmeticPreset preset = module != null && module.isEnabled() ? Vibe.getInstance().getCosmeticPresetManager().resolve(player, Vibe.getInstance().getFriendManager()) : null;
        if (preset == null) { restoreSkin(player.getUniqueID(), info); return; }
        ResourceLocation skin = skinFor(player.getUniqueID(), preset.getSkinName());
        try { Field field = findSkinField(); if (field == null) return; if (!originalSkins.containsKey(player.getUniqueID())) originalSkins.put(player.getUniqueID(), info.getLocationSkin()); field.set(info, skin); } catch (Throwable ignored) { }
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

    /** Called while the vanilla player-model matrix and limb pose are live. */
    private void renderLayer(AbstractClientPlayer player, ModelPlayer playerModel) {
        if (dev.vibe.module.impl.HypixelModule.visualsSuppressed()) return;
        if (Boolean.TRUE.equals(RENDERING.get())) return;
        if (player == null || playerModel == null) return;
        CosmeticsModule module = Vibe.getInstance().getModuleManager().getModule(CosmeticsModule.class);
        CosmeticPreset previewPreset = PREVIEW_PRESET.get();
        if (previewPreset == null && (module == null || !module.isEnabled())) return;
        CosmeticPreset preset = previewPreset == null ? Vibe.getInstance().getCosmeticPresetManager().resolve(player, Vibe.getInstance().getFriendManager()) : previewPreset;
        if (preset == null || preset.getAccessories().isEmpty()) return;
        if (player == minecraft.thePlayer && preset.isOnlyThirdPerson() && minecraft.gameSettings.thirdPersonView == 0) return;
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        RENDERING.set(Boolean.TRUE);
        try {
            GL11.glDisable(GL11.GL_CULL_FACE); GlStateManager.enableBlend(); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            // Cosmetica's model layer uses the texture's own full colour.
            // The immediate-mode 1.8 renderer has no baked per-vertex normal
            // or packed light, so leaving vanilla lighting enabled makes the
            // same source texture appear gray depending on the last model
            // normal. Render the authored PNG at its original colour.
            GlStateManager.enableTexture2D(); GlStateManager.enableDepth(); GlStateManager.disableLighting();
            GlStateManager.depthMask(true); GlStateManager.color(1, 1, 1, 1); GL11.glColor4f(1, 1, 1, 1);
            CosmeticaCatalogService catalog = Vibe.getInstance().getCosmeticaCatalogService();
            for (CosmeticaAccessory accessory : preset.getAccessories()) renderAccessory(player, playerModel, catalog, accessory);
        } finally {
            restore(GL11.GL_CULL_FACE, cull);
            restore(GL11.GL_BLEND, blend);
            restore(GL11.GL_TEXTURE_2D, texture);
            restore(GL11.GL_DEPTH_TEST, depth);
            restore(GL11.GL_LIGHTING, lighting);
            GlStateManager.depthMask(depthMask);
            GlStateManager.color(1, 1, 1, 1);
            RENDERING.remove();
        }
    }

    private void renderAccessory(EntityPlayer entity, ModelPlayer player, CosmeticaCatalogService catalog, CosmeticaAccessory accessory) {
        if (!accessory.isEnabled() || hiddenByEquipment(entity, accessory)) return;
        ModelRenderer part = part(player, accessory); if (part == null || !part.showModel) return;
        CosmeticaModel model = catalog.getModel(accessory); if (model == null) return;
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            // Direct 1.8.9 equivalent of Cosmetica Core's renderOnPart:
            // ModelPart#translateAndRotate -> scale(1,-1,-1) -> mirrored
            // scale -> rotate Y 180 -> authored, attachment-relative offset.
            part.postRender(0.0625F);
            GL11.glScalef(1.0F, -1.0F, -1.0F);
            if (accessory.isMirrored()) GL11.glScalef(-1.0F, 1.0F, 1.0F);
            GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);
            float[] transform = attachmentTransform(accessory);
            GL11.glTranslatef(transform[0] + slimArmOffset(player, accessory), transform[1], transform[2]);
            minecraft.getTextureManager().bindTexture(catalog.getTexture(accessory, false));
            int frame = (int) ((System.currentTimeMillis() / 50L) / Math.max(1, accessory.getTicksPerFrame()));
            model.render(frame, accessory.getFrames());
        } catch (Throwable ignored) {
            // A malformed community asset must not break player rendering; other catalog entries still render.
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(previousMatrixMode);
        }
    }

    private boolean hiddenByEquipment(EntityPlayer player, CosmeticaAccessory accessory) {
        return hidden(accessory, CosmeticaAccessory.HIDE_WITH_HELMET, player.getCurrentArmor(3))
                || hidden(accessory, CosmeticaAccessory.HIDE_WITH_CHESTPLATE, player.getCurrentArmor(2))
                || hidden(accessory, CosmeticaAccessory.HIDE_WITH_LEGGINGS, player.getCurrentArmor(1))
                || hidden(accessory, CosmeticaAccessory.HIDE_WITH_BOOTS, player.getCurrentArmor(0));
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

    /** Cosmetica Core's attachment Y origins, retained verbatim. */
    private float[] attachmentTransform(CosmeticaAccessory accessory) {
        String point = accessory.getAttachment(); float xBase = 0.0F, yBase = -8.0F;
        // The prior 1.8 adapter used the model pivots as y origins, placing
        // every accessory below its Cosmetica position.  Cosmetica's own
        // attachmentTransform uses head=8, arms=0 and body/legs=-2 pixels.
        if ("head".equals(point)) yBase = 8.0F;
        else if ("right_arm".equals(point) || "rightarm".equals(point)) { xBase = 1.0F; yBase = 0.0F; }
        else if ("left_arm".equals(point) || "leftarm".equals(point)) { xBase = -1.0F; yBase = 0.0F; }
        else yBase = -2.0F;
        return new float[]{(accessory.getOffsetX() + xBase) / 16.0F,
                (accessory.getOffsetY() + yBase) / 16.0F, accessory.getOffsetZ() / 16.0F};
    }

    /**
     * Cosmetica applies a half-pixel X correction for the 3px-wide Alex arms.
     * ModelPlayer exposes the slim model through its 2.5px arm pivot, whereas
     * the standard (Steve) pivot is 2px. This is read from the posed model so
     * no fragile reflection or skin-name guess is required.
     */
    private static float slimArmOffset(ModelPlayer player, CosmeticaAccessory accessory) {
        boolean slim = player.bipedLeftArm.rotationPointY > 2.25F;
        if (!slim) return 0.0F;
        String point = accessory.getAttachment();
        if ("left_arm".equals(point) || "leftarm".equals(point)) return 0.5F / 16.0F;
        if ("right_arm".equals(point) || "rightarm".equals(point)) return -0.5F / 16.0F;
        return 0.0F;
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
            parent.renderLayer(player, playerModel);
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
    private Field findSkinField() { if (searchedSkinField) return skinField; searchedSkinField=true; try { skinField=NetworkPlayerInfo.class.getDeclaredField("locationSkin"); } catch(Throwable ignored) { try { skinField=NetworkPlayerInfo.class.getDeclaredField("field_178865_e"); } catch(Throwable ignoredAgain) { } } if(skinField!=null)skinField.setAccessible(true); return skinField; }
    private void restoreSkin(UUID id,NetworkPlayerInfo info) { if(!originalSkins.containsKey(id))return; try { Field field=findSkinField();if(field!=null)field.set(info,originalSkins.remove(id)); }catch(Throwable ignored){} }
    private boolean hasResource(ResourceLocation location) { try { minecraft.getResourceManager().getResource(location); return true; } catch (Exception ignored) { return false; } }
    private static void restore(int capability, boolean enabled) { if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability); }
}
