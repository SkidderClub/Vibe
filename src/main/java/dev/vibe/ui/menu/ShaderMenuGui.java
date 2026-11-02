package dev.vibe.ui.menu;

import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Small preset browser opened from the Forge-injected main-menu button. */
public final class ShaderMenuGui extends GuiScreen {
    private static final int PRESET_ID = 3000;
    private final GuiScreen parent;
    private final MainMenuShaderManager shaders;
    private static final int COLUMNS = 2;
    private static final int ROW_HEIGHT = 96;
    private int left, top, panelWidth, panelHeight, listTop, columnWidth;
    private int scrollRow;
    private int visibleRows;

    public ShaderMenuGui(GuiScreen parent, MainMenuShaderManager shaders) {
        this.parent = parent;
        this.shaders = shaders;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        List<String> presets = shaders.getPresets();
        panelWidth = Math.min(560, width - 24);
        panelHeight = Math.min(420, height - 20);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        listTop = top + 72;
        columnWidth = (panelWidth - 38) / COLUMNS;
        visibleRows = Math.max(1, (panelHeight - 125) / ROW_HEIGHT);
        clampScroll(presets);
        int start = scrollRow * COLUMNS;
        int visible = Math.min(presets.size() - start, visibleRows * COLUMNS);
        for (int slot = 0; slot < visible; slot++) {
            String preset = presets.get(start + slot);
            int column = slot % COLUMNS;
            int row = slot / COLUMNS;
            int buttonLeft = left + 14 + column * (columnWidth + 6);
            String label = preset;
            buttonList.add(new ShaderCard(PRESET_ID+slot,buttonLeft,listTop+row*ROW_HEIGHT,columnWidth,ROW_HEIGHT-6,preset));
        }
        int buttonWidth = (panelWidth - 40) / 3;
        int footer = top + panelHeight - 38;
        buttonList.add(new AccountScreenStyle.Button(1, left + 14, footer, buttonWidth, 25, shaders.isEnabled() ? "Disable shader" : "Enable shader", "", true, false));
        buttonList.add(new AccountScreenStyle.Button(2, left + 20 + buttonWidth, footer, buttonWidth, 25, "Open folder", "", false, false));
        buttonList.add(new AccountScreenStyle.Button(5, left + 26 + buttonWidth * 2, footer, buttonWidth, 25, "Back", "", false, false));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        List<String> presets = shaders.getPresets();
        if (button.id >= PRESET_ID) {
            int index = scrollRow * COLUMNS + button.id - PRESET_ID;
            if (index < presets.size()) {
                shaders.select(presets.get(index));
                initGui();
            }
            return;
        }
        if (button.id == 1) {
            shaders.setEnabled(!shaders.isEnabled());
            initGui();
        } else if (button.id == 2) {
            shaders.openFolder();
        } else if (button.id == 5) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        shaders.draw(width, height);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        Gui.drawRect(0, 0, width, height, 0x85000000);
        updatePreview();
        MenuGlass.begin(this,shaders,width,height);
        try{
        AccountScreenStyle.window(left, top, panelWidth, panelHeight);
        AccountScreenStyle.title(dev.vibe.language.LanguageManager.translate("Shaders"), left + 14, top + 14);
        AccountScreenStyle.text(dev.vibe.language.LanguageManager.translate("Active shader") + ": " + shaders.getSelected(), left + 14, top + 39, AccountScreenStyle.ACCENT);
        AccountScreenStyle.text(dev.vibe.language.LanguageManager.translate("Scroll to browse shaders"), left + 14, top + 55, AccountScreenStyle.MUTED);
        drawScrollBar();
        if (!shaders.getLastError().isEmpty()) {
            AccountScreenStyle.text(AccountScreenStyle.fit(dev.vibe.language.LanguageManager.translate("Shader unavailable on this OpenGL profile"), panelWidth - 28), left + 14, top + panelHeight - 51, AccountScreenStyle.ERROR);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        }finally{MenuGlass.end();}
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            List<String> presets = shaders.getPresets();
            scrollRow += wheel > 0 ? -1 : 1;
            clampScroll(presets);
            initGui();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private void clampScroll(List<String> presets) {
        int totalRows = (presets.size() + COLUMNS - 1) / COLUMNS;
        scrollRow = Math.max(0, Math.min(Math.max(0, totalRows - visibleRows), scrollRow));
    }

    private void drawScrollBar() {
        List<String> presets = shaders.getPresets();
        int totalRows = (presets.size() + COLUMNS - 1) / COLUMNS;
        if (totalRows <= visibleRows) return;
        int top = listTop;
        int bottom = top + visibleRows * ROW_HEIGHT - 1;
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(12, Math.round(trackHeight * (visibleRows / (float) totalRows)));
        int travel = trackHeight - thumbHeight;
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int thumbTop = top + Math.round(travel * (scrollRow / (float) maxScroll));
        MenuScrollbar.draw(left+panelWidth-10,top,3,trackHeight,thumbTop,thumbHeight);
    }

    private String compact(String value, int length) {
        return value.length() <= length ? value : value.substring(0, Math.max(1, length - 1)) + "…";
    }
    private final java.util.LinkedHashMap<String,net.minecraft.client.shader.Framebuffer> thumbnails=new java.util.LinkedHashMap<String,net.minecraft.client.shader.Framebuffer>(12,.75F,true);
    private final java.util.Set<String> unavailable=new java.util.HashSet<>();
    private long nextPreview=System.nanoTime()+300000000L;
    private void updatePreview(){
        if(System.nanoTime()<nextPreview||!net.minecraft.client.renderer.OpenGlHelper.isFramebufferEnabled())return;
        for(GuiButton b:buttonList)if(b instanceof ShaderCard){String name=((ShaderCard)b).preset;
            if(thumbnails.containsKey(name)||unavailable.contains(name))continue;
            nextPreview=System.nanoTime()+150000000L;
            net.minecraft.client.shader.Framebuffer snapshot;
            try{snapshot=shaders.thumbnail(name);}catch(RuntimeException error){unavailable.add(name);return;}
            if(snapshot==null)unavailable.add(name);else{
                thumbnails.put(name,snapshot);
                while(thumbnails.size()>8){String key=thumbnails.keySet().iterator().next();thumbnails.remove(key).deleteFramebuffer();}
            }
            return;
        }
    }
    @Override public void onGuiClosed(){for(net.minecraft.client.shader.Framebuffer f:thumbnails.values())f.deleteFramebuffer();thumbnails.clear();}
    private final class ShaderCard extends GuiButton {
        final String preset;
        ShaderCard(int id,int x,int y,int w,int h,String preset){super(id,x,y,w,h,preset);this.preset=preset;}
        @Override public void drawButton(net.minecraft.client.Minecraft mc,int mx,int my){
            hovered=mx>=xPosition&&my>=yPosition&&mx<xPosition+width&&my<yPosition+height;
            boolean selected=preset.equals(shaders.getSelected());
            AccountScreenStyle.buttonPanel(xPosition,yPosition,width,height,AccountScreenStyle.SURFACE,selected?AccountScreenStyle.ACCENT:AccountScreenStyle.BORDER,hovered?.6F:0,mx,my);
            AccountScreenStyle.text(AccountScreenStyle.fit(preset.replace(".frag",""),width-16),xPosition+8,yPosition+8,selected?AccountScreenStyle.ACCENT:AccountScreenStyle.TEXT);
            net.minecraft.client.shader.Framebuffer f=thumbnails.get(preset);
            if(f==null){AccountScreenStyle.text(unavailable.contains(preset)?"Preview unavailable":"Preparing preview",xPosition+8,yPosition+42,AccountScreenStyle.MUTED);return;}
            GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.color(1,1,1,1);f.bindFramebufferTexture();
            float x=xPosition+6,y=yPosition+25,w=width-12,h=height-31;
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
            org.lwjgl.opengl.GL11.glTexCoord2f(0,1);org.lwjgl.opengl.GL11.glVertex2f(x,y);
            org.lwjgl.opengl.GL11.glTexCoord2f(0,0);org.lwjgl.opengl.GL11.glVertex2f(x,y+h);
            org.lwjgl.opengl.GL11.glTexCoord2f(1,0);org.lwjgl.opengl.GL11.glVertex2f(x+w,y+h);
            org.lwjgl.opengl.GL11.glTexCoord2f(1,1);org.lwjgl.opengl.GL11.glVertex2f(x+w,y);
            org.lwjgl.opengl.GL11.glEnd();
        }
    }}
