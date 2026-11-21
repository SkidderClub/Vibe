package dev.vibe.module.impl.client;

import dev.vibe.Vibe;
import dev.vibe.inventory.InventoryLayout;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.StringSetting;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.ui.screen.InventoryEditorGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens the persisted drag-and-drop layout editor used by Inventory Manager. */
public final class InventoryEditorModule extends Module {

    private static final String MUSIC_RULE_VERSION = "music-rules=1;";
    private final StringSetting layoutData = addSetting(new StringSetting("Layout Data", MUSIC_RULE_VERSION + "40=DISK", 4096, () -> false));
    private final BooleanSetting drawPosition = addSetting(new BooleanSetting("Draw Position", true));
    private final ColorSetting positionColor = addSetting(new ColorSetting("Position Color", 0xFF2DE2C2,
            () -> drawPosition.isEnabled()));

    public InventoryEditorModule() {
        super("Inventory Editor", "Arrange preferred inventory slots and the drop bin", Category.CLIENT, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        if (isConfigLoading()) { setEnabled(false); return; }
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.displayGuiScreen(new InventoryEditorGui(this));
    }

    public InventoryLayout getLayout() {
        String encoded = layoutData.getValue();
        InventoryLayout layout = InventoryLayout.decode(encoded);
        // Add the new default to older layouts with room, while keeping an intentional removal on save.
        if (!encoded.startsWith(MUSIC_RULE_VERSION) && !layout.isAssigned(InventoryLayout.Type.DISK)) {
            for (int slot = 40; slot < 64; slot++) {
                if (layout.get(slot) != null) continue;
                layout.set(slot, InventoryLayout.Type.DISK);
                break;
            }
        }
        return layout;
    }

    public void setLayout(InventoryLayout layout) {
        layoutData.setValue(MUSIC_RULE_VERSION + (layout == null ? "40=DISK" : layout.encode()));
        if (Vibe.getInstance() != null && Vibe.getInstance().getConfig() != null) {
            Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
        }
    }

    public BooleanSetting getDrawPosition() { return drawPosition; }
    public ColorSetting getPositionColor() { return positionColor; }
}
