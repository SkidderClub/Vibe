package dev.vibe.extension.cosmetica;

import dev.vibe.Vibe;
import dev.vibe.cosmetic.CosmeticPreset;
import dev.vibe.cosmetic.CosmeticPresetManager;
import dev.vibe.cosmetic.CosmeticaCatalogService;
import dev.vibe.extension.ExtensionContext;
import dev.vibe.extension.FriendProfileProvider;
import dev.vibe.extension.PlayerSkinProvider;
import dev.vibe.extension.VibeExtension;
import dev.vibe.friend.FriendManager;
import dev.vibe.module.impl.client.CosmeticsEditorModule;
import dev.vibe.module.impl.visual.CosmeticsModule;
import dev.vibe.ui.render.CosmeticsRenderer;
import java.io.File;

/** Original Cosmetica catalog, preset editor and player accessories; CustomCosmetics stays in core. */
public final class CosmeticaExtension implements VibeExtension {
    @Override public void initialize(ExtensionContext context) {
        // Keep the original paths so existing presets and downloaded assets need no migration.
        CosmeticPresetManager presets = context.registerService(CosmeticPresetManager.class,
                new CosmeticPresetManager(new File(context.getGameDirectory(), "config")));
        CosmeticaCatalogService catalog = context.registerService(CosmeticaCatalogService.class,
                new CosmeticaCatalogService(context.getGameDirectory()));
        context.onShutdown(presets::save);
        context.onShutdown(catalog::close);
        context.registerModule(new CosmeticsModule());
        context.registerModule(new CosmeticsEditorModule());
        CosmeticsRenderer renderer = new CosmeticsRenderer();
        context.onTickStart(renderer::installLayers);
        context.registerService(PlayerSkinProvider.class, renderer::skin);
        context.registerService(FriendProfileProvider.class, new FriendProfileProvider() {
            public String name(FriendManager.Friend friend) {
                CosmeticPreset preset = friend == null ? null : presets.findById(friend.getCosmeticPreset());
                return preset == null ? "None" : preset.getName();
            }
            public void cycle(FriendManager.Friend friend, boolean backwards) {
                if (friend != null) Vibe.getInstance().getFriendManager().setCosmeticPreset(friend.getName(),
                        presets.cycleId(friend.getCosmeticPreset(), backwards));
            }
        });
    }
    public static CosmeticPresetManager presets() { return Vibe.getInstance().getExtensions().getService(CosmeticPresetManager.class); }
    public static CosmeticaCatalogService catalog() { return Vibe.getInstance().getExtensions().getService(CosmeticaCatalogService.class); }
}
