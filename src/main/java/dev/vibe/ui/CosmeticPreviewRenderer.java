package dev.vibe.ui;

import com.mojang.authlib.GameProfile;
import dev.vibe.cosmetic.CosmeticPreset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

/**
 * Shared player preview. It uses a bounded copy of GuiInventory's stable
 * render basis while leaving yaw under editor control; GuiInventory itself
 * overwrites the entity yaw from the mouse parameters and therefore cannot
 * support full, persistent preview rotation.
 */
public final class CosmeticPreviewRenderer {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Map<String, EntityOtherPlayerMP> players = new HashMap<String, EntityOtherPlayerMP>();

    private net.minecraft.world.World previewWorld;

    public void draw(final CosmeticPreset preset, int x, int bottom, int scale, float yaw) {
        if (preset == null) return;
        final EntityOtherPlayerMP player = playerFor(preset.getSkinName()); if (player == null) return;
        float oldOffset = player.renderYawOffset, oldYaw = player.rotationYaw, oldPitch = player.rotationPitch, oldHead = player.rotationYawHead;
        float oldPrevOffset = player.prevRenderYawOffset, oldPrevYaw = player.prevRotationYaw, oldPrevPitch = player.prevRotationPitch, oldPrevHead = player.prevRotationYawHead;
        try {
            player.renderYawOffset = yaw; player.rotationYaw = yaw; player.rotationPitch = 0.0F; player.rotationYawHead = yaw;
            player.prevRenderYawOffset = yaw; player.prevRotationYaw = yaw; player.prevRotationPitch = 0.0F; player.prevRotationYawHead = yaw;
            RenderManager manager = minecraft.getRenderManager();
            float oldView = manager.playerViewY;
            boolean oldShadow = manager.isRenderShadow();
            boolean oldEntityShadows = minecraft.gameSettings.entityShadows;
            org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ENABLE_BIT
                    | org.lwjgl.opengl.GL11.GL_LIGHTING_BIT | org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
                    | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_CURRENT_BIT);
            GlStateManager.pushMatrix();
            try {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableColorMaterial();
                GlStateManager.enableDepth();
                GlStateManager.depthMask(true);
                GlStateManager.translate(x, bottom, 50.0F);
                GlStateManager.scale(-scale, scale, scale);
                GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
                RenderHelper.enableStandardItemLighting();
                manager.setPlayerViewY(180.0F);
                minecraft.gameSettings.entityShadows = false;
                manager.setRenderShadow(false);
                CosmeticsRenderer.withPreviewPreset(preset, new Runnable() {
                    @Override public void run() { manager.renderEntityWithPosYaw(player, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F); }
                });
            } finally {
                manager.setPlayerViewY(oldView);
                manager.setRenderShadow(oldShadow);
                minecraft.gameSettings.entityShadows = oldEntityShadows;
                RenderHelper.disableStandardItemLighting();
                GlStateManager.popMatrix();
                org.lwjgl.opengl.GL11.glPopAttrib();
            }
        } finally {
            player.renderYawOffset = oldOffset; player.rotationYaw = oldYaw; player.rotationPitch = oldPitch; player.rotationYawHead = oldHead;
            player.prevRenderYawOffset = oldPrevOffset; player.prevRotationYaw = oldPrevYaw; player.prevRotationPitch = oldPrevPitch; player.prevRotationYawHead = oldPrevHead;
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private EntityOtherPlayerMP playerFor(final String name) {
        String key = name == null ? "xheist_" : name.toLowerCase(java.util.Locale.ROOT);
        EntityOtherPlayerMP cached = players.get(key); if (cached != null) return cached;
        final ResourceLocation bundled = new ResourceLocation("vibe", "cosmetica/skins/xheist_.png");
        final ResourceLocation skin;
        if ("xheist_".equals(key) && hasResource(bundled)) skin = bundled;
        else {
            skin = new ResourceLocation("vibe", "cosmetic_preview/" + key);
            try {
                // Use a valid vanilla fallback directly. A dynamically registered
                // fallback ResourceLocation is not available during texture setup.
                minecraft.getTextureManager().loadTexture(skin, new ThreadDownloadImageData(null, "https://minotar.net/skin/" + key, DefaultPlayerSkin.getDefaultSkinLegacy(), null));
            } catch (Throwable ignored) { }
        }
        EntityOtherPlayerMP result = new EntityOtherPlayerMP(previewWorld == null ? (previewWorld = new EspPreviewWorld()) : previewWorld, new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), name == null ? "xHeist_" : name)) {
            @Override public ResourceLocation getLocationSkin() { return skin; }
            @Override public boolean hasSkin() { return true; }
        };
        // Preview players are not spawned through NetHandler, so their skin
        // layer watcher otherwise remains at zero and hides every outer skin
        // layer in the cosmetic editor.
        result.getDataWatcher().updateObject(10, Byte.valueOf((byte) 0x7F));
        players.put(key, result); return result;
    }
    private boolean hasResource(ResourceLocation location) { try { minecraft.getResourceManager().getResource(location); return true; } catch (Exception ignored) { return false; } }
}
