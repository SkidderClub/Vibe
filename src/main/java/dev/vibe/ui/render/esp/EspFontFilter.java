package dev.vibe.ui.render.esp;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Smooth the Minecraft atlas only during ESP; leave the rest of the client's font untouched. */
final class EspFontFilter implements AutoCloseable {
    private static Field atlas;
    private static boolean resolved;
    private final int texture,min,mag;
    private EspFontFilter(int texture){
        this.texture=texture;min=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER);mag=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
    }
    static EspFontFilter begin(Minecraft mc,String value){
        if(mc.fontRendererObj.getUnicodeFlag())return null;
        for(int i=0;i<value.length();i++)if(value.charAt(i)<32||value.charAt(i)>126)return null;
        try{
            if(!resolved){resolved=true;for(Field field:FontRenderer.class.getDeclaredFields())if(field.getType()==ResourceLocation.class&&!Modifier.isStatic(field.getModifiers())){field.setAccessible(true);atlas=field;break;}}
            if(atlas==null)return null;ResourceLocation resource=(ResourceLocation)atlas.get(mc.fontRendererObj);
            mc.getTextureManager().bindTexture(resource);return new EspFontFilter(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        }catch(ReflectiveOperationException|RuntimeException unavailable){return null;}
    }
    @Override public void close(){
        GlStateManager.bindTexture(texture);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,min);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,mag);
    }
}
