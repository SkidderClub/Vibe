package dev.vibe.hud;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.Category;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.ClientChangesRenderCheck;
import java.nio.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
public final class HudReviewRenderCheck {
    public static void verify(Minecraft mc,ModuleManager modules,HudManager manager,HudModule hud,Path output)throws Exception{
        EntityPlayerSP previous=mc.thePlayer;net.minecraft.client.multiplayer.WorldClient world=mc.theWorld;
        EntityPlayerSP sample=PathTestFixture.allocate(EntityPlayerSP.class);mc.thePlayer=sample;mc.theWorld=null;
        Module enabled=new Module("Flight","",Category.MOVEMENT,33){},disabled=new Module("Sneak","",Category.MOVEMENT,34){};
        modules.registerDynamic(enabled);modules.registerDynamic(disabled);enabled.setEnabled(true);hud.dashboards.get("keybindlist").onlyEnabled.setEnabled(false);
        java.lang.reflect.Field field=DashboardWidgets.class.getDeclaredField("map");field.setAccessible(true);RadarMapCache map=(RadarMapCache)field.get(manager.dashboards);
        DynamicTexture texture=new DynamicTexture(128,128);int[] data=texture.getTextureData();for(int y=0;y<128;y++)for(int x=0;x<128;x++)data[x+y*128]=x>78&&y>78?0:((x/8+y/8)%2==0?0xFF527850:0xFF648E58);texture.updateDynamicTexture();
        PathTestFixture.set(RadarMapCache.class,map,"texture",texture);map.ready=true;map.x=map.z=0;map.stride=1;
        DashboardSettings radar=hud.dashboards.get("radar");radar.mode.setValue("Map");radar.rotate.setEnabled(false);radar.width.setValue(184D);radar.height.setValue(184D);
        try{for(String theme:new String[]{"Vibe","Skeet","LiquidGlass","Lavalamp"}){
            ClientChangesRenderCheck.frame(1280,720);manager.beginEditorPreview(true);
            try{
                HudManager.HudElement binds=manager.getElement("keybindlist"),notifications=manager.getElement(HudManager.NOTIFICATIONS),mini=manager.getElement("radar");
                binds.setTheme(theme);manager.drawPreview(binds,mc.fontRendererObj);binds.moveTo(40,85,new ScaledResolution(mc));manager.drawPreview(binds,mc.fontRendererObj);
                notifications.setTheme(theme);manager.drawPreview(notifications,mc.fontRendererObj);notifications.moveTo(330,85,new ScaledResolution(mc));manager.drawPreview(notifications,mc.fontRendererObj);
                for(int i=0;i<3;i++){radar.mapCorners.setValue(i*50D);mini.setTheme(theme);mini.moveTo(590+i*220,85,new ScaledResolution(mc));manager.drawPreview(mini,mc.fontRendererObj);mc.fontRendererObj.drawString("Radius "+i*50,590+i*220,66,0xFFFFFFFF);}
            }finally{manager.endEditorPreview();}
            if(GL11.glGetError()!=0)throw new AssertionError("HUD review OpenGL state: "+theme);
            ByteBuffer pixels=BufferUtils.createByteBuffer(1280*720*4);GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<720;y++)for(int x=0;x<1280;x++){int p=(x+(719-y)*1280)*4;image.setRGB(x,y,0xFF000000|(pixels.get(p)&255)<<16|(pixels.get(p+1)&255)<<8|pixels.get(p+2)&255);}
            ImageIO.write(image,"png",output.resolve("hud-review-"+theme.toLowerCase(java.util.Locale.ROOT)+".png").toFile());
        }}finally{mc.thePlayer=previous;mc.theWorld=world;enabled.setEnabled(false);radar.mode.setValue("Radar");}
    }
}
