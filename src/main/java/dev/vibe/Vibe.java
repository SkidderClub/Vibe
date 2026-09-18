package dev.vibe;

import dev.vibe.config.VibeConfig;
import dev.vibe.account.AccountManager;
import dev.vibe.command.CommandManager;
import dev.vibe.event.ClientEvents;
import dev.vibe.event.MainMenuEvents;
import dev.vibe.hud.HudManager;
import dev.vibe.identity.ClientIdentity;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.LanguageModule;
import dev.vibe.friend.FriendManager;
import dev.vibe.cosmetic.CosmeticPresetManager;
import dev.vibe.cosmetic.CosmeticaCatalogService;
import dev.vibe.target.TargetManager;
import dev.vibe.ui.VibeClickGui;
import dev.vibe.ui.AnimationItemRenderer;
import dev.vibe.script.ScriptRuntime;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

@Mod(modid = Vibe.MOD_ID, name = Vibe.FORGE_NAME, version = Vibe.VERSION, clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public final class Vibe {

    public static final String MOD_ID = "clientcore";
    public static final String NAME = "Vibe";
    public static final String FORGE_NAME = "Client Core";
    public static final String VERSION = "0.0.5";

    private static Vibe instance;

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private ModuleManager moduleManager;
    private VibeConfig config;
    private CommandManager commandManager;
    private HudManager hudManager;
    private ClientIdentity identity;
    private FriendManager friendManager;
    private CosmeticPresetManager cosmeticPresetManager;
    private CosmeticaCatalogService cosmeticaCatalogService;
    private TargetManager targetManager;
    private ScriptRuntime scriptRuntime;
    private AccountManager accountManager;
    private int loadedModuleCount;
    private int loadedSettingCount;

    public static Vibe getInstance() {
        return instance;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        instance = this;
        accountManager = new AccountManager(minecraft, new java.io.File(minecraft.mcDataDir, "vibe/accounts").toPath());
        config = new VibeConfig(event.getModConfigurationDirectory());
        identity = new ClientIdentity(minecraft.mcDataDir);
        friendManager = new FriendManager(event.getModConfigurationDirectory());
        cosmeticPresetManager = new CosmeticPresetManager(event.getModConfigurationDirectory());
        cosmeticaCatalogService = new CosmeticaCatalogService(minecraft.mcDataDir);
        targetManager = new TargetManager(event.getModConfigurationDirectory());
        hudManager = new HudManager(event.getModConfigurationDirectory());
        moduleManager = new ModuleManager();
        scriptRuntime = new ScriptRuntime(minecraft.mcDataDir);
        LanguageModule language = moduleManager.getModule(LanguageModule.class);
        if (language != null) language.getLanguage().setValue(identity.getLanguage());
        commandManager = new CommandManager();
        updateWindowTitle();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientEvents events = new ClientEvents();
        MinecraftForge.EVENT_BUS.register(events);
        MinecraftForge.EVENT_BUS.register(new MainMenuEvents());
        dev.vibe.event.ReconnectEvents reconnect = new dev.vibe.event.ReconnectEvents();
        MinecraftForge.EVENT_BUS.register(reconnect);
        FMLCommonHandler.instance().bus().register(reconnect);
        FMLCommonHandler.instance().bus().register(events);

        config.load(moduleManager);
        // Script modules are registered after the normal profile is loaded,
        // so a broken or removed .java file can never prevent Vibe itself
        // from starting. The runtime retains Raven's source-level API.
        scriptRuntime.reload();
        refreshLoadedCounts();
        LanguageModule language = moduleManager.getModule(LanguageModule.class);
        if (language != null) identity.setLanguage(language.getLanguage().getValue());
        AnimationItemRenderer.install(minecraft);
        updateWindowTitle();
    }

    public void openClickGui() {
        dev.vibe.module.impl.ClickGuiModule clickGui = moduleManager == null ? null
                : moduleManager.getModule(dev.vibe.module.impl.ClickGuiModule.class);
        if (clickGui != null) clickGui.setEnabled(true);
        minecraft.displayGuiScreen(new VibeClickGui());
    }

    public boolean isClickGuiPressed() {
        // ClickGUI binding belongs solely to the ClickGUI module.  Keeping a
        // second hard-coded Forge key binding here caused Right Shift to keep
        // opening the GUI after the module had been rebound.
        return false;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public VibeConfig getConfig() {
        return config;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public HudManager getHudManager() {
        return hudManager;
    }

    public ClientIdentity getIdentity() {
        return identity;
    }

    public FriendManager getFriendManager() { return friendManager; }
    public CosmeticPresetManager getCosmeticPresetManager() { return cosmeticPresetManager; }
    public CosmeticaCatalogService getCosmeticaCatalogService() { return cosmeticaCatalogService; }
    public TargetManager getTargetManager() { return targetManager; }
    public ScriptRuntime getScriptRuntime() { return scriptRuntime; }
    public AccountManager getAccountManager() { return accountManager; }

    /** Snapshot shown on the menu; recalculated after every client startup. */
    public void refreshLoadedCounts() {
        if (moduleManager == null) { loadedModuleCount = loadedSettingCount = 0; return; }
        loadedModuleCount = moduleManager.getModules().size();
        int settings = 0;
        for (dev.vibe.module.Module module : moduleManager.getModules()) settings += module.getSettings().size();
        loadedSettingCount = settings;
    }

    public int getLoadedModuleCount() { return loadedModuleCount; }
    public int getLoadedSettingCount() { return loadedSettingCount; }
    public String getLoadedCountsText() { return loadedModuleCount + " modules and " + loadedSettingCount + " settings loaded"; }

    public void updateWindowTitle() {
        if (!Display.isCreated()) {
            return;
        }
        String tag = identity != null && identity.isConfigured() ? identity.getGamertag() : "Gamertag";
        Display.setTitle(NAME + " - " + VERSION + " - " + tag);
    }

    public Minecraft getMinecraft() {
        return minecraft;
    }
}
