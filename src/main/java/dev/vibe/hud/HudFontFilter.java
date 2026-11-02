package dev.vibe.hud;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Fractional HUD scaling needs filtered glyph edges; restore shared font textures after each widget. */
public final class HudFontFilter implements AutoCloseable {
    private static HudFontFilter active;
    private final HudFontFilter previous;
    private final Map<Integer,int[]> filters=new LinkedHashMap<>();
    private HudFontFilter(){previous=active;active=this;}
    public static HudFontFilter begin(float physicalScale){return Math.abs(physicalScale-Math.round(physicalScale))>.01F?new HudFontFilter():null;}
    public static void bind(Object manager,Object location){
        TextureManager textures=(TextureManager)manager;ResourceLocation resource=(ResourceLocation)location;
        textures.bindTexture(resource);
        if(active==null)return;
        int id=textures.getTexture(resource).getGlTextureId();
        if(active.filters.containsKey(id))return;
        active.filters.put(id,new int[]{GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER)});
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
    }
    @Override public void close(){
        int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        for(Map.Entry<Integer,int[]> e:filters.entrySet()){GlStateManager.bindTexture(e.getKey());GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,e.getValue()[0]);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,e.getValue()[1]);}
        GlStateManager.bindTexture(bound);active=previous;
    }
}
