package dev.vibe.ui;

import net.minecraft.client.gui.Gui;

/** Bottom-right window grip, kept in the border so it cannot cover a setting. */
final class GuiResizeGrip {
    private GuiResizeGrip() { }

    static boolean contains(int mouseX, int mouseY, int right, int bottom) {
        return mouseX >= right - 14 && mouseX < right && mouseY >= bottom - 14 && mouseY < bottom;
    }

    static void draw(int right, int bottom, int color) {
        for (int length = 3; length <= 9; length += 3) for (int step = 0; step < length; step++) {
            int x = right - 4 - length + step, y = bottom - 4 - step;
            Gui.drawRect(x, y, x + 1, y + 1, color);
        }
    }
}
