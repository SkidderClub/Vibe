package dev.vibe.extension;

import dev.vibe.Vibe;
import dev.vibe.model.CompiledModel;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.statistics.StatisticsService;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.resources.data.TextureMetadataSection;
import net.minecraft.client.resources.data.TextureMetadataSectionSerializer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

/** Actual JAR discovery, Forge event subscriptions, class/resource loading and model loading. No game window. */
public final class ExtensionIntegrationCheck {
    public static void main(String[] args) throws Exception {
        String mode = args[1]; boolean legoPresent = mode.equals("lego") || mode.equals("both") || mode.equals("all");
        boolean examplePresent = mode.equals("example") || mode.equals("both") || mode.equals("all") || mode.equals("public");
        boolean cosmeticaPresent = mode.equals("cosmetica") || mode.equals("all") || mode.equals("public");
        Path profile = Files.createTempDirectory(Paths.get(args[0]), mode + "-");
        Path mods = Files.createDirectories(profile.resolve("mods"));
        if (examplePresent) Files.copy(Paths.get(args[2]), mods.resolve("example.jar"));
        if (legoPresent) Files.copy(Paths.get(args[3]), mods.resolve("lego.jar"));
        if (cosmeticaPresent) Files.copy(Paths.get(args[4]), mods.resolve("cosmetica.jar"));
        Pbuffer buffer = new Pbuffer(64, 64, new PixelFormat(8, 24, 8), null, null); buffer.makeCurrent();
        ExtensionManager extensions = null;
        try {
            Class<?> log = Class.forName("net.minecraftforge.fml.relauncher.FMLRelaunchLog");
            Field logInstance = log.getDeclaredField("log");
            set(log, logInstance.get(null), "myLog", org.apache.logging.log4j.LogManager.getLogger("FML"));
            set(log, null, "configured", true);
            set(Class.forName("net.minecraftforge.fml.relauncher.FMLLaunchHandler"), null, "side", Side.CLIENT);
            // Model Forge's active Vibe container without starting the full Minecraft launcher.
            Loader loader = allocate(Loader.class); LoadController controller = allocate(LoadController.class);
            ModMetadata mod = new ModMetadata(); mod.modId = Vibe.MOD_ID; mod.name = "Vibe integration check";
            set(LoadController.class, controller, "activeContainer", new DummyModContainer(mod));
            set(Loader.class, loader, "modController", controller); set(Loader.class, null, "instance", loader);
            Object side = java.lang.reflect.Proxy.newProxyInstance(IFMLSidedHandler.class.getClassLoader(),
                    new Class<?>[] {IFMLSidedHandler.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("getSide")) return Side.CLIENT;
                        if (method.getReturnType() == boolean.class) return false;
                        return null;
                    });
            set(FMLCommonHandler.class, FMLCommonHandler.instance(), "sidedDelegate", side);
            Minecraft mc = allocate(TestMinecraft.class); set(Minecraft.class, null, "theMinecraft", mc);
            set(Minecraft.class, mc, "mcDataDir", profile.toFile()); mc.gameSettings = new GameSettings();
            mc.thePlayer = allocate(EntityPlayerSP.class);
            net.minecraft.entity.DataWatcher watcher = new net.minecraft.entity.DataWatcher(mc.thePlayer);
            watcher.addObject(0, Byte.valueOf((byte) 0));
            set(net.minecraft.entity.Entity.class, mc.thePlayer, "dataWatcher", watcher);
            IMetadataSerializer metadata = new IMetadataSerializer();
            metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(), TextureMetadataSection.class);
            List<IResourcePack> packs = new ArrayList<IResourcePack>();
            packs.add(new DefaultResourcePack(Collections.<String, File>emptyMap())); packs.add(new FolderResourcePack(new File("src/main/resources")));
            set(Minecraft.class, mc, "defaultResourcePacks", packs);
            SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(metadata); resources.reloadResources(packs);
            set(Minecraft.class, mc, "mcResourceManager", resources); mc.renderEngine = new TextureManager(resources);
            OpenGlHelper.initializeTextures();
            Vibe vibe = new Vibe(); set(Vibe.class, null, "instance", vibe);
            dev.vibe.friend.FriendManager friends = new dev.vibe.friend.FriendManager(profile.resolve("config").toFile());
            set(Vibe.class, vibe, "friendManager", friends);
            ModuleManager modules = new ModuleManager(); set(Vibe.class, vibe, "moduleManager", modules);
            require(modules.getModule("lego") == null && modules.getModule("test") == null, "Core registered an extension module");
            require(ExtensionIntegrationCheck.class.getResource("/assets/vibe/lego/catalog.json") == null, "Core contains LEGO assets");
            require(modules.getModule("cosmetics") == null && modules.getModule("cosmeticseditor") == null, "Core registered Cosmetica modules");
            require(!modules.getModule(dev.vibe.module.impl.client.HudModule.class).getArrayListModules().hasOption("Cosmetics"), "Core exposes an absent Cosmetica selector");
            require(modules.getModule(dev.vibe.module.impl.visual.CustomCosmeticsModule.class) != null, "CustomCosmetics missing from core");
            require(ExtensionIntegrationCheck.class.getResource("/assets/vibe/cosmetica/catalog.json") == null, "Core contains Cosmetica assets");
            int coreCount = modules.getModules().size();
            StatisticsService statistics = new StatisticsService(mc, profile.resolve("statistics"));
            set(Vibe.class, vibe, "statistics", statistics);
            EventBus forge = new EventBus(), fml = new EventBus();
            extensions = new ExtensionManager(profile.toFile(), modules, statistics, forge, fml);
            set(Vibe.class, vibe, "extensions", extensions); extensions.load();
            int expected = (legoPresent ? 1 : 0) + (examplePresent ? 1 : 0) + (cosmeticaPresent ? 1 : 0);
            require(extensions.getLoaded().size() == expected, "Unexpected load status: " + extensions.getStatus());
            require(modules.getModules().size() == coreCount + (legoPresent ? 3 : 0) + (examplePresent ? 1 : 0) + (cosmeticaPresent ? 2 : 0), "Wrong module count");
            if (cosmeticaPresent) {
                Module cosmetics = modules.getModule("cosmetics"); require(cosmetics != null, "Missing Cosmetics module");
                require(source(cosmetics).endsWith("cosmetica.jar"), "Cosmetics not loaded from its JAR");
                require(modules.getModule("cosmeticseditor") != null, "Missing Cosmetics Editor");
                require(modules.getModule(dev.vibe.module.impl.client.HudModule.class).getArrayListModules().hasOption("Cosmetics"), "Cosmetica missing from the HUD selector");
                Object catalog = extensions.getService(Class.forName("dev.vibe.cosmetic.CosmeticaCatalogService"));
                require(catalog != null, "Missing catalog service");
                List<?> entries = (List<?>) catalog.getClass().getMethod("getResults").invoke(catalog);
                require(entries.size() == 629, "Bundled Cosmetica catalog incomplete");
                require(!(Boolean) catalog.getClass().getMethod("isLoading").invoke(catalog), "Offline catalog started a download");
                ResourceLocation icon = new ResourceLocation("vibe", "cosmetica/thumbnails/EisxE.png");
                texture(resources, icon); resources.reloadResources(new ArrayList<IResourcePack>(packs)); texture(resources, icon);
                require(extensions.getService(FriendProfileProvider.class) != null, "Friend profile integration missing");
                friends.add("Alice", "Ally"); friends.add("Bob", "Buddy");
                extensions.getService(FriendProfileProvider.class).cycle(friends.findPlayer("Alice"), false);
                require(!friends.findPlayer("Alice").getCosmeticPreset().isEmpty(), "Friend preset was not assigned");
                require(friends.findPlayer("Bob").getCosmeticPreset().isEmpty(), "Friend presets were not independent");
                require(extensions.getService(FriendProfileProvider.class).name(friends.findPlayer("Alice")).equals("Default"), "Friend preset name unavailable");
                require(extensions.getService(PlayerSkinProvider.class) != null, "Skin integration missing");
                require(extensions.getService(PlayerSkinProvider.class).skin(mc.thePlayer) == null, "Disabled Cosmetics changes skins");
                cosmetics.setEnabled(true);
                require(extensions.getService(PlayerSkinProvider.class).skin(mc.thePlayer).equals(new ResourceLocation("vibe", "cosmetica/skins/xheist_.png")), "Bundled selected skin not used");
                // Install on actual default/slim player renderers and draw an authored accessory.
                net.minecraft.client.renderer.entity.RenderManager renderManager = allocate(net.minecraft.client.renderer.entity.RenderManager.class);
                java.util.Map<String, net.minecraft.client.renderer.entity.RenderPlayer> skins = new java.util.HashMap<String, net.minecraft.client.renderer.entity.RenderPlayer>();
                skins.put("default", new net.minecraft.client.renderer.entity.RenderPlayer(renderManager, false));
                skins.put("slim", new net.minecraft.client.renderer.entity.RenderPlayer(renderManager, true));
                set(net.minecraft.client.renderer.entity.RenderManager.class, renderManager, "skinMap", skins);
                set(Minecraft.class, mc, "renderManager", renderManager);
                Field layers = net.minecraft.client.renderer.entity.RendererLivingEntity.class.getDeclaredField("layerRenderers"); layers.setAccessible(true);
                int before = ((List<?>) layers.get(skins.get("default"))).size();
                extensions.tickStart(); extensions.tickStart();
                require(((List<?>) layers.get(skins.get("default"))).size() == before + 1, "Accessory layer was missing or duplicated");
                require(((List<?>) layers.get(skins.get("slim"))).size() == before + 1, "Slim accessory layer missing");
                Object presets = extensions.getService(Class.forName("dev.vibe.cosmetic.CosmeticPresetManager"));
                Object selected = presets.getClass().getMethod("getSelected").invoke(presets);
                selected.getClass().getMethod("equip", entries.get(0).getClass()).invoke(selected, entries.get(0));
                Object model = catalog.getClass().getMethod("getModel", entries.get(0).getClass()).invoke(catalog, entries.get(0));
                require(model != null, "Bundled accessory model unavailable");
                mc.thePlayer.inventory = new net.minecraft.entity.player.InventoryPlayer(mc.thePlayer);
                List<?> attached = (List<?>) layers.get(skins.get("default"));
                @SuppressWarnings("unchecked") net.minecraft.client.renderer.entity.layers.LayerRenderer<net.minecraft.client.entity.AbstractClientPlayer> accessoryLayer =
                        (net.minecraft.client.renderer.entity.layers.LayerRenderer<net.minecraft.client.entity.AbstractClientPlayer>) attached.get(attached.size() - 1);
                accessoryLayer.doRenderLayer(mc.thePlayer, 0, 0, 0, 0, 0, 0, .0625F);
                cosmetics.setEnabled(false);
                require(Files.isRegularFile(profile.resolve("config/vibe/cosmetics.json")), "Preset save path changed");
            } else {
                require(extensions.getService(FriendProfileProvider.class) == null, "Friend profile service leaked into core");
                require(extensions.getService(PlayerSkinProvider.class) == null, "Skin service leaked into core");
            }
            if (examplePresent) {
                Module test = modules.getModule("test"); require(test != null, "Missing Test module");
                require(source(test).endsWith("example.jar"), "Test was not loaded from its JAR: " + source(test));
                MoveFixModule moveFix = modules.getModule(MoveFixModule.class); moveFix.beginRotationTick();
                ((dev.vibe.setting.NumberSetting) test.getSettings().get(0)).setValue(72D);
                test.setEnabled(true); extensions.tickStart(); require(moveFix.getRotationYaw() == 72F, "Example tick hook did not produce rotation");
                require(mc.thePlayer.rotationYaw == 0F, "Test changed the camera"); test.setEnabled(false);
            }
            if (legoPresent) {
                Module lego = modules.getModule("lego"); require(lego != null, "Missing Lego module");
                require(source(lego).endsWith("lego.jar"), "LEGO was not loaded from its JAR: " + source(lego));
                Field listeners = StatisticsService.class.getDeclaredField("activityListeners"); listeners.setAccessible(true);
                require(((List<?>) listeners.get(statistics)).size() == 1, "LEGO statistics listener not registered");
                ResourceLocation stud = new ResourceLocation("vibe", "lego/textures/things_pc4.png");
                texture(resources, stud); resources.reloadResources(new ArrayList<IResourcePack>(packs)); texture(resources, stud);
                Class<?> roster = Class.forName("dev.vibe.lego.LegoRoster");
                require(((List<?>) roster.getField("CHARACTERS").get(null)).size() == 989, "Incomplete LEGO catalog");
                ((dev.vibe.setting.BooleanSetting) lego.getSettings().get(1)).setEnabled(false);
                lego.setEnabled(true);
                PlayerModelProvider provider = extensions.playerModel(mc.thePlayer);
                require(provider != null, "LEGO model provider not registered");
                long deadline = System.currentTimeMillis() + 15000;
                CompiledModel model;
                do {
                    fml.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
                    model = provider.model(); if (model == null) Thread.sleep(20);
                } while (model == null && System.currentTimeMillis() < deadline);
                require(model != null && model.getPivots() != null, "Model failed to load through the extension");
                require(dev.vibe.ui.render.CustomModelRenderer.renderBody(new net.minecraft.client.model.ModelBiped(0),
                        mc.thePlayer, 0, 0, 0, 0, 0, .0625F), "Extension model did not draw through the core renderer");
                require(dev.vibe.ui.render.CustomModelRenderer.hidesArmor(mc.thePlayer), "Extension armor override did not apply");
                require(Files.isRegularFile(profile.resolve("vibe/lego/models/players/LEGO r11 - obiwan.zip")), "Selected model was not extracted");
                lego.setEnabled(false); require(extensions.playerModel(mc.thePlayer) == null, "Disabled LEGO still overrides the character");
            }
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error in extension loading");
            extensions.close(); extensions = null;
            require(modules.getModules().size() == coreCount, "Extension modules leaked after cleanup");
            require(Vibe.getInstance().getExtensions().getService(PlayerSkinProvider.class) == null, "Extension service leaked after cleanup");
            System.out.println("Extension installation OK: " + mode);
        } finally { if (extensions != null) extensions.close(); buffer.destroy(); }
    }
    private static void texture(SimpleReloadableResourceManager resources, ResourceLocation location) throws Exception {
        try (InputStream in = resources.getResource(location).getInputStream()) { require(ImageIO.read(in) != null, "Invalid extension texture"); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static String source(Object object) throws Exception {
        java.net.URL location = object.getClass().getProtectionDomain().getCodeSource().getLocation();
        if (location.getProtocol().equals("jar")) location = ((java.net.JarURLConnection) location.openConnection()).getJarFileURL();
        return location.toString();
    }
    @SuppressWarnings("unchecked") private static <T> T allocate(Class<T> type) throws Exception {
        Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); f.setAccessible(true);
        Object unsafe = f.get(null); return (T) unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type);
    }
    private static void set(Class<?> type, Object instance, String name, Object value) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(instance, value);
    }
    /** Reload the real pack stack without creating a resource-pack repository or game window. */
    public static final class TestMinecraft extends Minecraft {
        private TestMinecraft() { super((net.minecraft.client.main.GameConfiguration) null); }
        @Override @SuppressWarnings("unchecked") public void refreshResources() {
            try {
                Field f = Minecraft.class.getDeclaredField("defaultResourcePacks"); f.setAccessible(true);
                ((SimpleReloadableResourceManager) getResourceManager()).reloadResources(new ArrayList<IResourcePack>((List<IResourcePack>) f.get(this)));
            } catch (Exception error) { throw new RuntimeException(error); }
        }
    }
}
