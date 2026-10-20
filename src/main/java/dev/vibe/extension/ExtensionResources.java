package dev.vibe.extension;

import java.io.File;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Keep extension textures in the normal resource stack, including after F3+T. */
final class ExtensionResources {
    @SuppressWarnings("unchecked")
    static Runnable mount(File jar) {
        Minecraft mc = Minecraft.getMinecraft();
        List<IResourcePack> packs = ReflectionHelper.getPrivateValue(Minecraft.class, mc,
                "defaultResourcePacks", "field_110449_ao");
        IResourcePack pack = new FileResourcePack(jar);
        packs.add(pack);
        try {
            ((SimpleReloadableResourceManager) mc.getResourceManager()).reloadResourcePack(pack);
        } catch (RuntimeException | LinkageError | AssertionError error) { packs.remove(pack); throw error; }
        return () -> { packs.remove(pack); mc.refreshResources(); };
    }
}
