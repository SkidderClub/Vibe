package dev.vibe.ui.effect;

import dev.vibe.ui.GuiClip;
import dev.vibe.ui.menu.MenuPalette;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Native pixel checks for animated, fading and transformed HUD lava. */
public final class LavaLampRenderCheck {
    public static void verify(Minecraft mc, Path output) throws Exception {
        mc.displayWidth=1280;mc.displayHeight=720;mc.gameSettings.guiScale=1;
        try (LiquidGlassRenderer glass=new LiquidGlassRenderer();LavaLampRenderer lava=new LavaLampRenderer(glass)) {
            frame();int[] backdrop=pixels();
            draw(lava,1,1);int[] first=pixels();
            check(difference(backdrop,first)>1000,"Lavalamp did not draw a visible surface");
            save(output.resolve("lavalamp-hud-surface.png"),first);
            frame();draw(lava,4,1);int[] second=pixels();
            check(difference(first,second)>100,"Lava bubbles and reservoirs did not animate");
            frame();draw(lava,1,0);check(Arrays.equals(backdrop,pixels()),"A faded lava pane remained visible");
            frame();GL11.glPushMatrix();GL11.glTranslatef(37,28,0);GL11.glScalef(1.3F,1.3F,1);
            try(GuiClip clip=new GuiClip(160,115,160,90)) {
                draw(lava,3,.6F);
                check(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),"Lava discarded the enclosing clip");
            } finally {GL11.glPopMatrix();}
            int[] transformed=pixels();
            check(difference(backdrop,transformed)>100,"Transformed lava was empty");
            for(int y=0;y<720;y++)for(int x=0;x<1280;x++) {
                if(x>=160&&x<320&&y>=115&&y<205)continue;
                check(backdrop[y*1280+x]==transformed[y*1280+x],"Lava escaped its transformed or enclosing bounds");
            }
            check(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)&&GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==0,"Lava leaked GL state");
            save(output.resolve("lavalamp-hud-scaled-clipped.png"),transformed);
            for(int i=0;i<4;i++){frame();for(int j=0;j<8;j++)lava.draw(20+(j%4)*310,40+(j/4)*150,260,90,9,2,4,.72F,0xFFFFFFFF);}
            GL11.glFinish();long start=System.nanoTime();
            for(int i=0;i<20;i++){frame();for(int j=0;j<8;j++)lava.draw(20+(j%4)*310,40+(j/4)*150,260,90,9,2,4,.72F,0xFFFFFFFF);}
            GL11.glFinish();
            System.out.printf(java.util.Locale.ROOT,"Eight Lavalamp HUD panes: %.2f ms/frame on %s%n",(System.nanoTime()-start)/20e6,GL11.glGetString(GL11.GL_RENDERER));
            check(GL11.glGetError()==0,"Lava renderer left an OpenGL error");
        }
    }
    private static void draw(LavaLampRenderer lava,double time,float opacity){
        check(lava.draw(70,50,220,140,10,2,4,opacity,0xFFFFFFFF,time,MenuPalette.OCEAN),"Lava shader failed");
    }
    private static void frame() throws Exception {
        dev.vibe.ui.ClientChangesRenderCheck.frame(1280,720);
        for(int y=0;y<720;y+=16)for(int x=0;x<1280;x+=16)
            net.minecraft.client.gui.Gui.drawRect(x,y,x+16,y+16,((x/16+y/16)&1)==0?0xFF384653:0xFF252E38);
    }
    private static int[] pixels(){
        ByteBuffer data=BufferUtils.createByteBuffer(1280*720*4);GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
        int[] result=new int[1280*720];
        for(int y=0;y<720;y++)for(int x=0;x<1280;x++){int i=((719-y)*1280+x)*4;result[y*1280+x]=(data.get(i)&255)<<16|(data.get(i+1)&255)<<8|(data.get(i+2)&255);}
        return result;
    }
    private static int difference(int[] a,int[] b){int count=0;for(int i=0;i<a.length;i++)if(a[i]!=b[i])count++;return count;}
    private static void save(Path file,int[] pixels)throws Exception{BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,1280,720,pixels,0,1280);ImageIO.write(image,"png",file.toFile());}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
