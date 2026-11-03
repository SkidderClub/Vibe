package dev.vibe.ui.menu;


import dev.vibe.ui.RenderUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.*;
import org.lwjgl.opengl.*;

/** One browser for complete shader/colour themes, with bounded, static GPU previews. */
public class MenuThemesGui extends GuiScreen {
    private final GuiScreen parent;private final MainMenuShaderManager shaders;private final Consumer<String> chooseShader;
    private int left,top,panelWidth,panelHeight,listTop,columns,visibleRows,scrollRow;private boolean custom,choosingFile;
    private MenuTextField search;private String query="",status="";private final List<MenuTheme> entries=new ArrayList<>();
    private final LinkedHashMap<String,Framebuffer> thumbnails=new LinkedHashMap<>(12,.75F,true);
    private final Set<String> unavailable=new HashSet<>();private long nextPreview=System.nanoTime()+300000000L;
    private long nextRefresh;private List<String> knownShaders=Collections.emptyList();
    private int rowHeight;
    public MenuThemesGui(GuiScreen parent,MainMenuShaderManager shaders){this(parent,shaders,null);}
    MenuThemesGui(GuiScreen parent,MainMenuShaderManager shaders,Consumer<String> chooseShader){this.parent=parent;this.shaders=shaders;this.chooseShader=chooseShader;}
    @Override public void initGui(){
        Keyboard.enableRepeatEvents(true);MenuThemes.active(shaders);boolean focus=search!=null&&search.isFocused();if(search!=null)query=search.getText();
        panelWidth=Math.min(600,width-24);panelHeight=Math.min(450,height-20);left=(width-panelWidth)/2;top=(height-panelHeight)/2;listTop=top+78;columns=panelWidth<380?1:2;int available=Math.max(70,panelHeight-136);visibleRows=Math.max(1,available/104);rowHeight=available/visibleRows;
        int searchWidth=chooseShader!=null?panelWidth-28:Math.max(70,panelWidth-202);
        search=new MenuTextField(1,fontRendererObj,left+22,top+44,searchWidth-16,23);search.setEnableBackgroundDrawing(false);search.setMaxStringLength(64);search.setText(query);search.setFocused(focus);
        rebuild();
    }
    private void rebuild(){
        entries.clear();String q=query.trim().toLowerCase(Locale.ROOT);
        if(custom&&chooseShader==null){for(MenuTheme theme:MenuThemes.custom())if((theme.name+" "+theme.shader).toLowerCase(Locale.ROOT).contains(q))entries.add(theme.copy());}
        else{knownShaders=shaders.getPresets();for(String shader:knownShaders){MenuTheme theme=MenuThemes.automatic(shader);if((theme.name+" "+shader).toLowerCase(Locale.ROOT).contains(q))entries.add(theme);}}
        scrollRow=Math.max(0,Math.min(scrollRow,Math.max(0,(entries.size()+columns-1)/columns-visibleRows)));buttonList.clear();
        buttonList.add(new AccountScreenStyle.Button(0,left+panelWidth-66,top+12,52,24,"Back","",false,false));
        if(chooseShader==null){int x=left+panelWidth-180;buttonList.add(new AccountScreenStyle.Button(1,x,top+44,78,23,"Presets","",!custom,false));buttonList.add(new AccountScreenStyle.Button(2,x+84,top+44,82,23,"Custom","",custom,false));}
        int cardWidth=(panelWidth-34-(columns-1)*8)/columns;
        for(int slot=0;slot<visibleRows*columns&&scrollRow*columns+slot<entries.size();slot++)buttonList.add(new ThemeCard(3000+slot,left+14+(slot%columns)*(cardWidth+8),listTop+(slot/columns)*rowHeight,cardWidth,rowHeight-8,entries.get(scrollRow*columns+slot)));
        int footer=top+panelHeight-35,bw=(panelWidth-44)/3;
        buttonList.add(new AccountScreenStyle.Button(3,left+14,footer,bw,24,"New theme","",false,false));buttonList.get(buttonList.size()-1).visible=chooseShader==null;
        buttonList.add(new AccountScreenStyle.Button(4,left+22+bw,footer,bw,24,panelWidth<380?"Import":"Import shader","",false,false));
        buttonList.add(new AccountScreenStyle.Button(5,left+30+bw*2,footer,bw,24,"Folder","",false,false));
        for(GuiButton b:buttonList)b.enabled=!choosingFile;
    }
    @Override public void drawScreen(int mx,int my,float partial){
        shaders.draw(width,height);updatePreview();MenuGlass.begin(this,shaders,width,height);
        try{
            AccountScreenStyle.window(left,top,panelWidth,panelHeight);AccountScreenStyle.title(chooseShader==null?"Themes":"Choose a shader",left+14,top+14);
            dev.vibe.ui.MenuRoundedRenderer.rect(left+12,top+panelHeight-57,panelWidth-24,17,4,0xB010141C);
            String description=!shaders.getLastError().isEmpty()?"This shader is unavailable. Choose another theme.":status.isEmpty()?chooseShader!=null?"Choose the background for your theme.":"Shader previews with matching menu colours. Right-click a custom theme to edit.":status;
            AccountScreenStyle.SMALL_FONT.middle(AccountScreenStyle.SMALL_FONT.fit(description,panelWidth-36),left+18,top+panelHeight-57,17,shaders.getLastError().isEmpty()?AccountScreenStyle.TEXT:AccountScreenStyle.ERROR);
            AccountScreenStyle.panel(search.xPosition-8,search.yPosition,search.getWidth()+16,23,AccountScreenStyle.SURFACE,search.isFocused()?AccountScreenStyle.ACCENT:AccountScreenStyle.BORDER);search.drawTextBox();if(search.getText().isEmpty()&&!search.isFocused())AccountScreenStyle.SMALL_FONT.middle("Search themes",search.xPosition,search.yPosition,23,AccountScreenStyle.MUTED);
            if(entries.isEmpty())AccountScreenStyle.BODY_FONT.centered(custom?"Create your first custom theme.":"No matching themes.",left+14,listTop,panelWidth-28,Math.max(30,visibleRows*rowHeight),AccountScreenStyle.MUTED);
            int rows=(entries.size()+columns-1)/columns;if(rows>visibleRows){int track=visibleRows*rowHeight-8,thumb=Math.max(12,track*visibleRows/rows);MenuScrollbar.draw(left+panelWidth-8,listTop,3,track,listTop+(track-thumb)*scrollRow/(rows-visibleRows),thumb);}
            super.drawScreen(mx,my,partial);
        }finally{MenuGlass.end();}
    }
    @Override protected void actionPerformed(GuiButton button){
        if(button.id==0){mc.displayGuiScreen(parent);return;}
        if(button instanceof ThemeCard){MenuTheme theme=((ThemeCard)button).theme;if(chooseShader!=null){chooseShader.accept(theme.shader);mc.displayGuiScreen(parent);}else try{status=MenuThemes.activate(theme,shaders)?"Selected "+theme.name:"Could not save the theme.";}catch(IllegalArgumentException invalid){status=invalid.getMessage();}return;}
        if(button.id==1||button.id==2){custom=button.id==2;query="";search.setText("");scrollRow=0;rebuild();}
        else if(button.id==3){MenuTheme t=MenuThemes.active(shaders);t.id=UUID.randomUUID().toString();t.name="My theme";mc.displayGuiScreen(new MenuThemeEditorGui(this,shaders,t,false));}
        else if(button.id==4)importShader();else if(button.id==5){if(!shaders.openFolder())status="Folder: "+shaders.getShaderDirectory();}
    }
    private void importShader(){
        if(choosingFile)return;choosingFile=true;rebuild();
        javax.swing.SwingUtilities.invokeLater(()->{java.nio.file.Path selected=null;try{javax.swing.JFileChooser chooser=new javax.swing.JFileChooser();chooser.setDialogTitle("Import fragment shader");chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Fragment shaders (*.frag, *.glsl, *.fsh)","frag","glsl","fsh"));if(chooser.showOpenDialog(null)==javax.swing.JFileChooser.APPROVE_OPTION)selected=chooser.getSelectedFile().toPath();}catch(RuntimeException failure){}
            final java.nio.file.Path source=selected;mc.addScheduledTask(()->{choosingFile=false;if(source!=null)try{String name=shaders.importShader(source);custom=false;query=name.substring(0,name.length()-5);if(search!=null)search.setText(query);status="Imported "+name+". Select it to use it.";}catch(IOException failure){status=failure.getMessage();}if(mc.currentScreen==this)rebuild();});});
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        search.mouseClicked(x,y,button);if(button==1&&chooseShader==null)for(GuiButton b:buttonList)if(b instanceof ThemeCard&&x>=b.xPosition&&x<b.xPosition+b.width&&y>=b.yPosition&&y<b.yPosition+b.height){MenuTheme t=((ThemeCard)b).theme;if(!t.automatic()){mc.displayGuiScreen(new MenuThemeEditorGui(this,shaders,t.copy(),true));return;}}
        super.mouseClicked(x,y,button);
    }
    @Override protected void keyTyped(char c,int key)throws IOException{if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}if(search.textboxKeyTyped(c,key)){query=search.getText();scrollRow=0;rebuild();return;}super.keyTyped(c,key);}
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel!=0){scrollRow+=wheel>0?-1:1;rebuild();}}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void updateScreen(){search.updateCursorCounter();long now=System.nanoTime();if(now>=nextRefresh){nextRefresh=now+2000000000L;if(!shaders.getPresets().equals(knownShaders))rebuild();}}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);for(Framebuffer f:thumbnails.values())f.deleteFramebuffer();thumbnails.clear();}
    private void updatePreview(){
        if(System.nanoTime()<nextPreview||!OpenGlHelper.isFramebufferEnabled())return;
        for(GuiButton b:buttonList)if(b instanceof ThemeCard){String name=((ThemeCard)b).theme.shader;if(thumbnails.containsKey(name)||unavailable.contains(name))continue;nextPreview=System.nanoTime()+180000000L;
            try{Framebuffer f=shaders.thumbnail(name);if(f==null){unavailable.add(name);return;}thumbnails.put(name,f);
                try(dev.vibe.ui.effect.EffectState state=new dev.vibe.ui.effect.EffectState()){f.bindFramebuffer(false);ByteBuffer pixels=BufferUtils.createByteBuffer(128*72*4);GL11.glReadPixels(0,0,128,72,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);int color=ShaderThemeColors.sample(pixels);MenuThemes.sampled(name,color);for(MenuTheme theme:entries)if(theme.automatic()&&theme.shader.equals(name))theme.first=theme.second=color;}
                while(thumbnails.size()>8){String oldest=thumbnails.keySet().iterator().next();thumbnails.remove(oldest).deleteFramebuffer();}
            }catch(RuntimeException error){unavailable.add(name);}return;
        }
    }
    private final class ThemeCard extends GuiButton{
        final MenuTheme theme;
        ThemeCard(int id,int x,int y,int w,int h,MenuTheme theme){super(id,x,y,w,h,theme.name);this.theme=theme;}
        @Override public void drawButton(net.minecraft.client.Minecraft mc,int mx,int my){
            hovered=mx>=xPosition&&my>=yPosition&&mx<xPosition+width&&my<yPosition+height;boolean chosen=MenuThemes.selectedId().equals(theme.id);
            AccountScreenStyle.buttonPanel(xPosition,yPosition,width,height,AccountScreenStyle.SURFACE,chosen?AccountScreenStyle.ACCENT:AccountScreenStyle.BORDER,hovered?.5F:0,mx,my);
            AccountScreenStyle.BOLD_FONT.middle(AccountScreenStyle.BOLD_FONT.fit(theme.name,width-40),xPosition+10,yPosition+2,24,AccountScreenStyle.TEXT);
            MenuPalette p=theme.palette().sample(System.currentTimeMillis());dev.vibe.ui.MenuRoundedRenderer.rect(xPosition+width-23,yPosition+9,11,11,5,p.accent);
            Framebuffer f=thumbnails.get(theme.shader);int x=xPosition+7,y=yPosition+29,w=width-14,h=height-36;
            if(f==null){AccountScreenStyle.SMALL_FONT.centered(unavailable.contains(theme.shader)?"Preview unavailable":"Preparing preview",x,y,w,h,AccountScreenStyle.MUTED);return;}
            GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.color(1,1,1,1);f.bindFramebufferTexture();float span=Math.min(1,(128/72F)/(w/(float)h)),v0=(1-span)/2,v1=1-v0;
            GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(0,v1);GL11.glVertex2f(x,y);GL11.glTexCoord2f(0,v0);GL11.glVertex2f(x,y+h);GL11.glTexCoord2f(1,v0);GL11.glVertex2f(x+w,y+h);GL11.glTexCoord2f(1,v1);GL11.glVertex2f(x+w,y);GL11.glEnd();
        }
    }
}
