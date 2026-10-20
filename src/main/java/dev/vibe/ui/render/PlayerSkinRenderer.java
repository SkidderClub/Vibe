package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.extension.PlayerSkinProvider;
import dev.vibe.module.impl.client.NameProtectModule;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Core privacy masking and optional extension skin overrides, with normal skin restoration. */
public final class PlayerSkinRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<NetworkPlayerInfo, ResourceLocation> originals = new IdentityHashMap<NetworkPlayerInfo, ResourceLocation>();
    private final ResourceLocation protectedSkin = new ResourceLocation("vibe", "protected_skin/gommehd");
    private boolean privacyRequested;
    private Field skinField;
    private Object world;
    private PlayerSkinProvider failedProvider;

    public void prepareSkin(RenderPlayerEvent.Pre event) {
        if (event.entityPlayer == null || mc.getNetHandler() == null || Vibe.getInstance() == null) return;
        if (world != mc.theWorld) { originals.clear(); world = mc.theWorld; }
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(event.entityPlayer.getUniqueID());
        if (info == null) return;
        NameProtectModule protect = Vibe.getInstance().getModuleManager().getModule(NameProtectModule.class);
        ResourceLocation replacement = null;
        if (protect != null && protect.hidesOthers() && event.entityPlayer != mc.thePlayer) {
            if (!privacyRequested) {
                mc.getTextureManager().loadTexture(protectedSkin, new ThreadDownloadImageData(null,
                        "https://minotar.net/skin/gommehd", DefaultPlayerSkin.getDefaultSkinLegacy(), null));
                privacyRequested = true;
            }
            replacement = protectedSkin;
        } else {
            PlayerSkinProvider provider = Vibe.getInstance().getExtensions() == null ? null
                    : Vibe.getInstance().getExtensions().getService(PlayerSkinProvider.class);
            if (provider != null && provider != failedProvider) {
                try { replacement = provider.skin(event.entityPlayer); }
                catch (RuntimeException | LinkageError | AssertionError error) {
                    failedProvider = provider;
                    org.apache.logging.log4j.LogManager.getLogger("Vibe extensions").error("Player skin provider failed", error);
                }
            }
        }
        apply(info, replacement);
    }
    void apply(NetworkPlayerInfo info, ResourceLocation replacement) {
        try {
            if (skinField == null) skinField = ReflectionHelper.findField(NetworkPlayerInfo.class, "locationSkin", "field_178865_e");
            if (replacement != null) {
                if (!originals.containsKey(info)) originals.put(info, info.getLocationSkin());
                skinField.set(info, replacement);
            } else if (originals.containsKey(info)) skinField.set(info, originals.remove(info));
        } catch (RuntimeException | IllegalAccessException error) {
            org.apache.logging.log4j.LogManager.getLogger("Vibe").debug("Could not apply player skin", error);
        }
    }
}
