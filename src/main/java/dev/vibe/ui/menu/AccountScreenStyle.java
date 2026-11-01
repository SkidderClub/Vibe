package dev.vibe.ui.menu;

import dev.vibe.ui.MenuRoundedRenderer;
import dev.vibe.ui.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

/** Shared presentation for the main menu, account screens and the HUD editor. */
public final class AccountScreenStyle {
    public static final MenuFont BODY_FONT = MenuFont.BODY;
    public static final MenuFont SMALL_FONT = MenuFont.SMALL;
    public static final MenuFont BOLD_FONT = MenuFont.BOLD;
    public static final MenuFont TITLE_FONT = MenuFont.TITLE;
    public static int BACKGROUND, SURFACE, BORDER, MUTED, ACCENT, TINT, HOVER;
    public static final int TEXT = 0xFFF2F3F5;
    public static final int SUCCESS = 0xFF7DDDC3;
    public static final int ERROR = 0xFFFF969F;

    static { applyTheme(MenuThemes.current()); }

    static void applyTheme(MenuThemes.Preset preset) {
        BACKGROUND = preset.background;
        SURFACE = preset.surface;
        ACCENT = preset.accent;
        MUTED = preset.muted;
        BORDER = RenderUtils.blend(SURFACE, ACCENT, 0.18F);
        TINT = RenderUtils.blend(SURFACE, ACCENT, 0.12F);
        HOVER = RenderUtils.blend(SURFACE, ACCENT, 0.18F);
    }

    private AccountScreenStyle() { }

    public static void panel(int x, int y, int width, int height, int fill, int border) {
        if (MenuGlass.pane(x, y, width, height, 10, fill, border, false)) return;
        MenuRoundedRenderer.rect(x, y, width, height, 10, border);
        MenuRoundedRenderer.rect(x + 1, y + 1, width - 2, height - 2, 9, fill);
    }

    public static void window(int x, int y, int width, int height) {
        if (MenuGlass.pane(x, y, width, height, 14, BACKGROUND, BORDER, true)) return;
        MenuRoundedRenderer.rect(x - 3, y + 4, width + 6, height + 1, 16, 0x55000000);
        MenuRoundedRenderer.rect(x, y, width, height, 14, BORDER);
        MenuRoundedRenderer.rect(x + 1, y + 1, width - 2, height - 2, 13, BACKGROUND);
    }

    public static void buttonPanel(int x,int y,int width,int height,int fill,int border,float hover,int mouseX,int mouseY){
        float pointerX=Math.max(0,Math.min(1,(mouseX-x)/(float)Math.max(1,width)));
        float pointerY=Math.max(0,Math.min(1,(mouseY-y)/(float)Math.max(1,height)));
        if(!MenuGlass.button(x,y,width,height,fill,border,hover,pointerX,pointerY))panel(x,y,width,height,fill,border);
    }

    public static void text(String value, int x, int y, int color) {
        value = dev.vibe.language.LanguageManager.translate(value);
        rawText(value, x, y, color);
    }

    public static void rawText(String value, int x, int y, int color) {
        if (MenuGlass.active()) { (color == MUTED ? SMALL_FONT : BODY_FONT).draw(value, x, y, color); return; }
        Minecraft.getMinecraft().fontRendererObj.drawString(value, x, y, color);
    }
    public static int textWidth(String value) { return MenuGlass.active() ? (int)Math.ceil(BODY_FONT.width(value)) : Minecraft.getMinecraft().fontRendererObj.getStringWidth(value); }

    public static String fit(String value, int width) {
        value = dev.vibe.language.LanguageManager.translate(value);
        return fitRaw(value, width);
    }

    public static String fitRaw(String value, int width) {
        if (MenuGlass.active()) return BODY_FONT.fit(value, Math.max(0, width));
        return Minecraft.getMinecraft().fontRendererObj.trimStringToWidth(value, Math.max(0, width));
    }

    public static void title(String value, int x, int y) {
        if (MenuGlass.active()) { TITLE_FONT.draw(dev.vibe.language.LanguageManager.translate(value), x, y, TEXT); return; }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(1.5F, 1.5F, 1);
        text(value, 0, 0, TEXT);
        GlStateManager.popMatrix();
    }

    public static class Button extends GuiButton {
        private final String subtitle;
        private final boolean primary;
        private final boolean danger;
        private float hover;
        private float pointerX=.5F,pointerY=.5F;
        private long lastFrame = System.nanoTime();

        public Button(int id, int x, int y, int width, int height, String label, String subtitle, boolean primary, boolean danger) {
            super(id, x, y, width, height, label);
            this.subtitle = subtitle;
            this.primary = primary;
            this.danger = danger;
        }

        @Override public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!visible) return;
            hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
            long now = System.nanoTime(); float dt = Math.min(.05F, (now-lastFrame)/1e9F); lastFrame = now;
            hover += ((hovered && enabled ? 1 : 0)-hover) * (1-(float)Math.exp(-20*dt));
            if(hovered&&enabled){
                float follow=1-(float)Math.exp(-14*dt);
                pointerX+=((mouseX-xPosition)/(float)Math.max(1,width)-pointerX)*follow;
                pointerY+=((mouseY-yPosition)/(float)Math.max(1,height)-pointerY)*follow;
            }
            int fill = !enabled ? BACKGROUND : RenderUtils.blend(primary ? TINT : SURFACE, HOVER, hover);
            int border = !enabled ? SURFACE : RenderUtils.blend(BORDER, ACCENT, primary ? .65F : hover * .7F);
            if(!MenuGlass.button(xPosition,yPosition,width,height,fill,border,hover,pointerX,pointerY))panel(xPosition, yPosition, width, height, fill, border);
            int color = !enabled ? RenderUtils.blend(SURFACE, MUTED, 0.6F) : danger ? ERROR : TEXT;
            String label = fit(displayString, width - 14);
            if (MenuGlass.active()) {
                if (subtitle.isEmpty()) BODY_FONT.centered(label, xPosition, yPosition, width, height, color);
                else {
                    String detail = SMALL_FONT.fit(dev.vibe.language.LanguageManager.translate(subtitle), width - 24);
                    float first = BODY_FONT.inkHeight(label), second = SMALL_FONT.inkHeight(detail), gap = 6;
                    float y = yPosition + (height - first - gap - second) * .5F;
                    BODY_FONT.draw(label, xPosition + 12, y, color);
                    SMALL_FONT.draw(detail, xPosition + 12, y + first + gap, !enabled ? RenderUtils.blend(SURFACE, MUTED, .6F) : MUTED);
                }
                mouseDragged(mc, mouseX, mouseY);return;
            }
            if (subtitle.isEmpty()) {
                text(label, xPosition + (width - textWidth(label)) / 2, yPosition + (height - 8) / 2, color);
            } else {
                text(label, xPosition + 12, yPosition + 9, color);
                text(fit(subtitle, width - 24), xPosition + 12, yPosition + 23,
                        !enabled ? RenderUtils.blend(SURFACE, MUTED, 0.6F) : MUTED);
            }
            mouseDragged(mc, mouseX, mouseY);
        }
    }
}
