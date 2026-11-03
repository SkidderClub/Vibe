package dev.vibe.ui.menu;

import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Exercises the actual custom-theme editor and colour picker against native GL. */
final class ThemeControlsRenderCheck {
    static void verify(Minecraft mc,MainMenuShaderManager shaders,Path output)throws Exception{
        Field soundField=null;Object previousSound=null;for(Field f:Minecraft.class.getDeclaredFields())if(f.getType()==net.minecraft.client.audio.SoundHandler.class){f.setAccessible(true);soundField=f;previousSound=f.get(mc);f.set(mc,PathTestFixture.allocate(SilentSound.class));break;}
        MenuTheme previous=MenuThemes.active(shaders);Path source=Files.createTempFile(output,"theme-test-",".glsl");
        Files.write(source,"#version 120\nvoid main(){gl_FragColor=vec4(0.1,0.6,0.9,1.0);}\n".getBytes(StandardCharsets.UTF_8));String imported=shaders.importShader(source);
        MenuThemesGui browser=new MenuThemesGui(new GuiMainMenu(),shaders);setup(mc,browser,960,540);
        try{
            for(int i=0;i<5;i++){PathTestFixture.set(MenuThemesGui.class,browser,"nextPreview",0L);draw(mc,browser,960,540);}
            MenuTheme theme=MenuThemes.automatic(imported);MenuThemes.activate(theme,shaders);
            MenuThemesGui filtered=new MenuThemesGui(browser,shaders);setup(mc,filtered,960,540);((MenuTextField)get(filtered,"search")).setText(imported.substring(0,imported.length()-5));GuiInput.keyTyped(filtered,' ',org.lwjgl.input.Keyboard.KEY_END);
            PathTestFixture.set(MenuThemesGui.class,filtered,"query",imported.substring(0,imported.length()-5));Method rebuild=MenuThemesGui.class.getDeclaredMethod("rebuild");rebuild.setAccessible(true);rebuild.invoke(filtered);PathTestFixture.set(MenuThemesGui.class,filtered,"nextPreview",0L);draw(mc,filtered,960,540);
            int accent=MenuThemes.current().accent;check((accent&255)>(accent>>16&255),"Imported blue shader did not select blue menu colours");save(output.resolve("theme-imported-preview.png"),960,540);filtered.onGuiClosed();
            MenuTheme draft=theme.copy();draft.id=UUID.randomUUID().toString();draft.name="Arctic fade";
            MenuThemeEditorGui editor=new MenuThemeEditorGui(browser,shaders,draft,false);
            for(int[] size:new int[][]{{960,540},{480,300},{320,240}}){setup(mc,editor,size[0],size[1]);draw(mc,editor,size[0],size[1]);click(editor,12);draw(mc,editor,size[0],size[1]);save(output.resolve("theme-editor-"+size[0]+".png"),size[0],size[1]);
                ThemeColourGui color=new ThemeColourGui(editor,shaders,"Accent colour",draft.first,value->draft.first=value);setup(mc,color,size[0],size[1]);draw(mc,color,size[0],size[1]);save(output.resolve("theme-colour-"+size[0]+".png"),size[0],size[1]);((MenuTextField)get(color,"hex")).setText("#55CFAA");click(color,1);check(draft.first==0xFF55CFAA,"The hex colour control did not update the draft");
                setup(mc,browser,size[0],size[1]);draw(mc,browser,size[0],size[1]);for(GuiButton b:buttons(browser))if(b.id>=3000)check(b.yPosition+b.height<=(Integer)get(browser,"top")+(Integer)get(browser,"panelHeight")-57,"A theme card overlaps its footer");save(output.resolve("theme-browser-"+size[0]+".png"),size[0],size[1]);
            }
            setup(mc,editor,960,540);draw(mc,editor,960,540);click(editor,4);check(MenuThemes.custom().stream().anyMatch(t->t.id.equals(draft.id)&&t.colorMode.equals("Fade")),"The editor did not save the complete custom theme");
            MenuThemeStore persisted=new MenuThemeStore(mc.mcDataDir.toPath());check(persisted.custom.stream().anyMatch(t->t.id.equals(draft.id)&&t.first==0xFF55CFAA),"Custom theme colours were not persisted");
            MenuThemes.deleteCustom(draft.id,shaders);System.out.println("Unified themes: imported shader/palette, real Fade and hex controls, custom persistence, three GUI sizes, bounded previews and footer clipping passed.");
        }finally{browser.onGuiClosed();MenuThemes.activate(previous,shaders);Files.deleteIfExists(source);mc.currentScreen=null;if(soundField!=null)soundField.set(mc,previousSound);}
    }
    private static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    @SuppressWarnings("unchecked")private static List<GuiButton> buttons(GuiScreen gui)throws Exception{Field f=GuiScreen.class.getDeclaredField("buttonList");f.setAccessible(true);return (List<GuiButton>)f.get(gui);}
    private static void setup(Minecraft mc,GuiScreen gui,int w,int h){mc.displayWidth=w;mc.displayHeight=h;mc.gameSettings.guiScale=1;mc.currentScreen=gui;gui.setWorldAndResolution(mc,w,h);}
    private static void draw(Minecraft mc,GuiScreen gui,int w,int h)throws Exception{ClientChangesRenderCheck.frame(w,h);gui.drawScreen(-1,-1,0);check(GL11.glGetError()==0,"Theme control GL error");}
    private static void click(GuiScreen gui,int id)throws Exception{for(GuiButton b:buttons(gui))if(b.id==id){GuiInput.mouseClicked(gui,b.xPosition+b.width/2,b.yPosition+b.height/2,0);return;}throw new AssertionError("Missing theme button "+id);}
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
    private static final class SilentSound extends net.minecraft.client.audio.SoundHandler{private SilentSound(){super(null,null);}@Override public void playSound(net.minecraft.client.audio.ISound sound){}}
    private static void save(Path path,int w,int h)throws Exception{ByteBuffer p=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=(y*w+x)*4;image.setRGB(x,h-1-y,0xFF000000|(p.get(i)&255)<<16|(p.get(i+1)&255)<<8|p.get(i+2)&255);}ImageIO.write(image,"png",path.toFile());}
}
