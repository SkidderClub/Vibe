package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.inventory.InventoryLayout;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.StringSetting;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.ui.InventoryEditorGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens the persisted drag-and-drop layout editor used by Inventory Manager. */
public final class InventoryEditorModule extends Module {

    private final StringSetting layoutData = addSetting(new StringSetting("Layout Data", "", 4096, () -> false));
    private final BooleanSetting drawPosition = addSetting(new BooleanSetting("Draw Position", true));
    private final ColorSetting positionColor = addSetting(new ColorSetting("Position Color", 0xFF2DE2C2,
            () -> drawPosition.isEnabled()));

    public InventoryEditorModule() {
        super("Inventory Editor", "Arrange preferred inventory slots and the drop bin", Category.CLIENT, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null) {
            setEnabled(false);
            return;
        }
        minecraft.displayGuiScreen(new InventoryEditorGui(this));
    }

    public InventoryLayout getLayout() {
        return InventoryLayout.decode(layoutData.getValue());
    }

    public void setLayout(InventoryLayout layout) {
        layoutData.setValue(layout == null ? "" : layout.encode());
        if (Vibe.getInstance() != null && Vibe.getInstance().getConfig() != null) {
            Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
        }
    }

    public BooleanSetting getDrawPosition() { return drawPosition; }
    public ColorSetting getPositionColor() { return positionColor; }
}
