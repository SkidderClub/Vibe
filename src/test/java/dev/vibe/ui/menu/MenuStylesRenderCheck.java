package dev.vibe.ui.menu;

import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.ClientChangesRenderCheck;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class MenuStylesRenderCheck {
    public static void verify(Minecraft mc,Path output)throws Exception{
        MainMenuShaderManager shaders=new MainMenuShaderManager();shaders.getPresets();MenuTheme previous=MenuThemes.active(shaders);
        try{
            for(String style:new String[]{"Minimalistic","Skeet"}){
                MenuThemes.activate(MenuTheme.builtin(style),shaders);MenuThemesGui gui=new MenuThemesGui(null,shaders);mc.displayWidth=960;mc.displayHeight=540;gui.setWorldAndResolution(mc,960,540);ClientChangesRenderCheck.frame(960,540);gui.drawScreen(-1,-1,0);check(GL11.glGetError()==0,"Flat menu GL state");save(output.resolve("menu-style-"+style.toLowerCase()+".png"),960,540);gui.onGuiClosed();
            }
            MenuThemes.activate(previous,shaders);MenuTheme draft=new MenuTheme();draft.name="Live violet";draft.first=0xFFAF72FF;draft.second=0xFFFF7CAB;draft.colorMode="Fade";
            MenuThemeEditorGui editor=new MenuThemeEditorGui(null,shaders,draft,false);editor.setWorldAndResolution(mc,960,540);ClientChangesRenderCheck.frame(960,540);editor.drawScreen(-1,-1,0);
            check(!MenuThemes.selectedId().equals(draft.id),"Preview persisted an unsaved theme");check(MenuThemes.current().accent!=previous.first,"Draft did not change live colours");save(output.resolve("menu-live-theme-preview.png"),960,540);
            int before=draft.first;ThemeColourGui picker=new ThemeColourGui(editor,(MainMenuShaderManager)get(editor,"previewShaders"),"Accent",before,value->draft.first=value,true);picker.setWorldAndResolution(mc,960,540);
            java.lang.reflect.Method pick=ThemeColourGui.class.getDeclaredMethod("pick",int.class,int.class);pick.setAccessible(true);PathTestFixture.set(ThemeColourGui.class,picker,"drag",0);pick.invoke(picker,380,200);check(draft.first!=before,"Colour picker did not preview live");
            picker.keyTyped('\0',org.lwjgl.input.Keyboard.KEY_ESCAPE);check(draft.first==before,"Cancelling the colour picker did not restore the draft");
            MenuThemes.preview(null);((MainMenuShaderManager)get(editor,"previewShaders")).close();
            System.out.println("Minimalistic/Skeet menu rendering and isolated live custom-theme preview passed.");
        }finally{MenuThemes.preview(null);MenuThemes.activate(previous,shaders);shaders.close();}
    }
    private static Object get(Object o,String name)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
    private static void save(Path path,int w,int h)throws Exception{ByteBuffer p=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=((h-1-y)*w+x)*4;img.setRGB(x,y,(p.get(i)&255)<<16|(p.get(i+1)&255)<<8|p.get(i+2)&255);}ImageIO.write(img,"png",path.toFile());}
}
