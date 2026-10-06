package dev.vibe.module.impl.visual;

import dev.vibe.Vibe;
import dev.vibe.model.CompiledModel;
import dev.vibe.model.CustomModelManager;
import dev.vibe.model.ModelKind;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.awt.Desktop;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;

/**
 * Replaces held swords and player models with glTF/GLB/OBJ models, such as Sketchfab
 * downloads. Models live in {@code .minecraft/vibe/models/<swords|players>/}; see
 * {@code docs/CUSTOM_MODELS.md}.
 */
public final class CustomModelRendererModule extends Module {

    public static final String SWORDS = "Swords";
    public static final String PLAYER_MODEL = "Player Model";
    private static final String DEFAULT_SWORD = "Karambit Knife Freehand";
    private static final String DEFAULT_PLAYER = "First order trooper Rigged and textured";

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final CustomModelManager manager = new CustomModelManager(minecraft.mcDataDir);

    private final MultiSelectSetting modes = addSetting(new MultiSelectSetting("Modes",
            Arrays.asList(SWORDS, PLAYER_MODEL), Arrays.asList(SWORDS, PLAYER_MODEL)));

    private final ModeSetting swordModel = addSetting(new ModeSetting("Sword Model", DEFAULT_SWORD,
            () -> modes.isSelected(SWORDS), choices(ModelKind.SWORDS, DEFAULT_SWORD)));
    private final NumberSetting swordScale = addSetting(new NumberSetting("Sword Scale", 1.0D, 0.2D, 3.0D, 0.05D,
            () -> modes.isSelected(SWORDS)));
    private final MultiSelectSetting swordViews = addSetting(new MultiSelectSetting("Sword Views",
            Arrays.asList("First Person", "Third Person", "Other Players"),
            Arrays.asList("First Person", "Third Person", "Other Players"), () -> modes.isSelected(SWORDS)));
    private final BooleanSetting enchantGlint = addSetting(new BooleanSetting("Enchant Glint", true,
            () -> modes.isSelected(SWORDS)));
    private final BooleanSetting reverseBlade = addSetting(new BooleanSetting("Reverse Blade", false,
            () -> modes.isSelected(SWORDS)));
    private final BooleanSetting flipBlade = addSetting(new BooleanSetting("Flip Blade", false,
            () -> modes.isSelected(SWORDS)));
    private final NumberSetting swordRotateX = addSetting(new NumberSetting("Sword Rotate X", 0.0D, -180.0D, 180.0D, 5.0D,
            () -> modes.isSelected(SWORDS)));
    private final NumberSetting swordRotateY = addSetting(new NumberSetting("Sword Rotate Y", 0.0D, -180.0D, 180.0D, 5.0D,
            () -> modes.isSelected(SWORDS)));
    private final NumberSetting swordRotateZ = addSetting(new NumberSetting("Sword Rotate Z", 0.0D, -180.0D, 180.0D, 5.0D,
            () -> modes.isSelected(SWORDS)));
    private final NumberSetting swordOffsetX = addSetting(new NumberSetting("Sword Offset X", 0.0D, -1.0D, 1.0D, 0.01D,
            () -> modes.isSelected(SWORDS)));
    private final NumberSetting swordOffsetY = addSetting(new NumberSetting("Sword Offset Y", 0.0D, -1.0D, 1.0D, 0.01D,
            () -> modes.isSelected(SWORDS)));
    private final NumberSetting swordOffsetZ = addSetting(new NumberSetting("Sword Offset Z", 0.0D, -1.0D, 1.0D, 0.01D,
            () -> modes.isSelected(SWORDS)));

    private final ModeSetting playerModel = addSetting(new ModeSetting("Character Model", DEFAULT_PLAYER,
            () -> modes.isSelected(PLAYER_MODEL), choices(ModelKind.PLAYERS, DEFAULT_PLAYER)));
    private final NumberSetting playerScale = addSetting(new NumberSetting("Player Scale", 1.0D, 0.25D, 3.0D, 0.05D,
            () -> modes.isSelected(PLAYER_MODEL)));
    private final MultiSelectSetting playerTargets = addSetting(new MultiSelectSetting("Player Targets",
            Arrays.asList("Self", "Friends", "Others"), Arrays.asList("Self"), () -> modes.isSelected(PLAYER_MODEL)));
    private final BooleanSetting animateLimbs = addSetting(new BooleanSetting("Animate Limbs", true,
            () -> modes.isSelected(PLAYER_MODEL)));
    private final BooleanSetting fixPose = addSetting(new BooleanSetting("Fix T-Pose", true,
            () -> modes.isSelected(PLAYER_MODEL)));
    private final BooleanSetting hideArmor = addSetting(new BooleanSetting("Hide Armor", true,
            () -> modes.isSelected(PLAYER_MODEL)));
    private final NumberSetting playerRotation = addSetting(new NumberSetting("Player Rotation", 0.0D, -180.0D, 180.0D, 15.0D,
            () -> modes.isSelected(PLAYER_MODEL)));

    private final ModeSetting detailLimit = addSetting(new ModeSetting("Detail Limit", "60k",
            "10k", "25k", "60k", "120k", "250k"));
    private final ModeSetting textureSize = addSetting(new ModeSetting("Texture Size", "1024", "256", "512", "1024", "2048"));
    private final NumberSetting lodDistance = addSetting(new NumberSetting("LOD Distance", 24.0D, 4.0D, 128.0D, 4.0D,
            () -> modes.isSelected(PLAYER_MODEL)));
    private final BooleanSetting openFolder = addSetting(new BooleanSetting("Open Folder", false));
    private final BooleanSetting reloadModels = addSetting(new BooleanSetting("Reload Models", false));

    private CustomModelManager.Request swordRequest;
    private CustomModelManager.Request playerRequest;
    private long lastChoiceRefresh;

    public CustomModelRendererModule() {
        super("CustomModelRenderer", "Replace held swords and player models with your own 3D models",
                Category.VISUAL, Keyboard.KEY_NONE);
    }

    private String[] choices(ModelKind kind, String fallback) {
        List<String> names = manager.choices(kind);
        if (names.isEmpty()) names = Arrays.asList(fallback);
        return names.toArray(new String[0]);
    }

    public void tick() {
        if (openFolder.isEnabled()) {
            openFolder.setEnabled(false);
            openFolder(null);
        }
        if (reloadModels.isEnabled()) {
            reloadModels.setEnabled(false);
            manager.reload();
            refreshChoices();
            say("Reloaded custom models.");
        }
        manager.tick(isEnabled());
        if (System.currentTimeMillis() - lastChoiceRefresh >= 3000L) refreshChoices();
        String message;
        int shown = 0;
        while (shown < 4 && (message = manager.pollMessage()) != null) {
            say(message);
            shown++;
        }
    }

    @Override
    protected void onDisable() {
        manager.releaseAll();
    }

    public void refreshChoices() {
        lastChoiceRefresh = System.currentTimeMillis();
        refresh(swordModel, ModelKind.SWORDS);
        refresh(playerModel, ModelKind.PLAYERS);
    }

    private void refresh(ModeSetting setting, ModelKind kind) {
        List<String> choices = manager.choices(kind);
        if (!choices.isEmpty() && !choices.equals(setting.getModes())) setting.replaceModes(choices);
    }

    public boolean openFolder(ModelKind kind) {
        File folder = kind == null ? manager.getRoot() : manager.folder(kind);
        folder.mkdirs();
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder);
                return true;
            }
        } catch (Exception ignored) {
            // Headless or sandboxed desktops cannot open folders.
        }
        say("Model folder: §f" + folder.getAbsolutePath());
        return false;
    }

    private void say(String text) {
        if (minecraft.thePlayer != null) minecraft.thePlayer.addChatMessage(new ChatComponentText("§8[§dModels§8] §7" + text));
    }

    // ---- queries used by CustomModelRenderer, called every frame ----

    public CompiledModel swordModel() {
        if (!isEnabled() || !modes.isSelected(SWORDS)) return null;
        int budget = budget(), texture = texture();
        String name = swordModel.getValue();
        if (swordRequest == null || !swordRequest.matches(name, budget, texture, 0, false)) {
            swordRequest = new CustomModelManager.Request(ModelKind.SWORDS, name, budget, texture, 0, false);
        }
        return manager.get(swordRequest);
    }

    public CompiledModel playerModel() {
        if (!isEnabled() || !modes.isSelected(PLAYER_MODEL)) return null;
        int budget = budget(), texture = texture(), yaw = playerRotation.getInt();
        boolean pose = fixPose.isEnabled();
        String name = playerModel.getValue();
        if (playerRequest == null || !playerRequest.matches(name, budget, texture, yaw, pose)) {
            playerRequest = new CustomModelManager.Request(ModelKind.PLAYERS, name, budget, texture, yaw, pose);
        }
        return manager.get(playerRequest);
    }

    public boolean appliesToPlayer(EntityPlayer player) {
        if (player == null || !isEnabled() || !modes.isSelected(PLAYER_MODEL)) return false;
        if (player == minecraft.thePlayer) return playerTargets.isSelected("Self");
        Vibe vibe = Vibe.getInstance();
        boolean friend = vibe != null && vibe.getFriendManager() != null && vibe.getFriendManager().isFriend(player);
        return friend ? playerTargets.isSelected("Friends") : playerTargets.isSelected("Others");
    }

    private int budget() {
        switch (detailLimit.getValue()) {
            case "10k": return 10000;
            case "25k": return 25000;
            case "120k": return 120000;
            case "250k": return 250000;
            default: return 60000;
        }
    }

    private int texture() {
        switch (textureSize.getValue()) {
            case "256": return 256;
            case "512": return 512;
            case "2048": return 2048;
            default: return 1024;
        }
    }

    public CustomModelManager getManager() { return manager; }
    public MultiSelectSetting getModes() { return modes; }
    public ModeSetting getSwordModel() { return swordModel; }
    public ModeSetting getPlayerModel() { return playerModel; }
    public NumberSetting getSwordScale() { return swordScale; }
    public MultiSelectSetting getSwordViews() { return swordViews; }
    public BooleanSetting getEnchantGlint() { return enchantGlint; }
    public BooleanSetting getReverseBlade() { return reverseBlade; }
    public BooleanSetting getFlipBlade() { return flipBlade; }
    public NumberSetting getSwordRotateX() { return swordRotateX; }
    public NumberSetting getSwordRotateY() { return swordRotateY; }
    public NumberSetting getSwordRotateZ() { return swordRotateZ; }
    public NumberSetting getSwordOffsetX() { return swordOffsetX; }
    public NumberSetting getSwordOffsetY() { return swordOffsetY; }
    public NumberSetting getSwordOffsetZ() { return swordOffsetZ; }
    public NumberSetting getPlayerScale() { return playerScale; }
    public BooleanSetting getAnimateLimbs() { return animateLimbs; }
    public BooleanSetting getHideArmor() { return hideArmor; }
    public NumberSetting getLodDistance() { return lodDistance; }
}
