package dev.vibe.easter;

import dev.vibe.path.PathTestFixture;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Actual GPU pixels; no visible window, sounds, OS dialog or player profile. */
public final class HolidayRenderCheck {
    private static final int W=960,H=540;
    private static Path output;
    public static void main(String[] args)throws Exception {
        output=Paths.get(args[0]);Files.createDirectories(output);
        Pbuffer buffer=new Pbuffer(W,H,new PixelFormat(8,24,8),null,null);buffer.makeCurrent();
        try(PathTestFixture fixture=new PathTestFixture();HolidayRenderer renderer=new HolidayRenderer()) {
            OpenGlHelper.initializeTextures();
            check(new HolidayCalendar().chineseNewYear(java.time.LocalDate.of(2026,2,17)),"Bundled Java 8 lunar engine failed");
            BufferedImage replacement=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<256;y++)for(int x=0;x<256;x++)replacement.setRGB(x,y,0xFFFF00FF);
            ByteArrayOutputStream encoded=new ByteArrayOutputStream();ImageIO.write(replacement,"png",encoded);final byte[] packBytes=encoded.toByteArray();
            final ResourceLocation overlay=new ResourceLocation("textures/misc/pumpkinblur.png");
            SimpleReloadableResourceManager packs=new SimpleReloadableResourceManager(new IMetadataSerializer());
            packs.reloadResourcePack(new DefaultResourcePack(Collections.<String,File>emptyMap()) {
                @Override public boolean resourceExists(ResourceLocation location){return location.equals(overlay);}
                @Override public InputStream getInputStream(ResourceLocation location)throws IOException{return location.equals(overlay)?new ByteArrayInputStream(packBytes):super.getInputStream(location);}
            });
            PathTestFixture.set(Minecraft.class,fixture.mc,"mcResourceManager",packs);
            BufferedImage packImage=ImageIO.read(packs.getResource(overlay).getInputStream());
            check(packImage.getRGB(0,0)==0xFFFF00FF,"Replacement pack fixture was not applied");
            BufferedImage vanilla=HolidayRenderer.vanillaPumpkin();check(vanilla.getRGB(0,0)!=0xFFFF00FF,"Pumpkin came from the active texture pack");

            frame();State before=new State();renderer.pumpkin(W,H);before.same();BufferedImage pumpkin=capture("pumpkin-960.png");
            check((pumpkin.getRGB(8,8)&0xFFFFFF)!=0x83B4EA,"Vanilla mask did not cover the corner");
            check((pumpkin.getRGB(W/3,H/3)&0xFFFFFF)==0x83B4EA,"Vanilla mask did not retain its transparent eye opening");
            int pumpkinId=id(renderer,"pumpkin");renderer.pumpkin(W,H);check(pumpkinId==id(renderer,"pumpkin"),"Pumpkin texture reuploaded per frame");

            frame();before=new State();renderer.console(W,H,.22);before.same();capture("console-start-960.png");
            frame();before=new State();renderer.console(W,H,.78);before.same();BufferedImage console=capture("console-960.png");
            check((console.getRGB(W/2,200)&0xFFFFFF)==0,"Console body was not drawn black");
            int consoleId=id(renderer,"console");renderer.console(W,H,.9);check(consoleId==id(renderer,"console"),"Console texture was not cached");

            frame();before=new State();renderer.crash(W,H,0);before.same();capture("crash-start-960.png");
            frame();before=new State();renderer.crash(W,H,.72);before.same();BufferedImage crash=capture("crash-960.png");
            check((crash.getRGB(W-1,H-1)&0xFFFFFF)==0x0078D7,"Crash image did not cover the framebuffer");
            int crashId=id(renderer,"crash"),progressId=id(renderer,"progress");renderer.crash(W,H,.99);
            check(crashId==id(renderer,"crash")&&progressId==id(renderer,"progress"),"Crash textures were not cached");
            renderer.close();check(!GL11.glIsTexture(pumpkinId)&&!GL11.glIsTexture(consoleId)&&!GL11.glIsTexture(crashId)&&!GL11.glIsTexture(progressId),"Owned textures leaked");
            checkPlayerViews(fixture);
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"Holiday renderer left a GL error");
            System.out.println("Holiday GPU checks passed: genuine vanilla pumpkin despite a replacement pack, first-person mask with every vanilla helmet and no helmet, no mask in either third-person view including the fallback, transparent eye opening, console reveal, fullscreen crash/progress, texture reuse/disposal and exact GL state restoration.");
        }finally{buffer.destroy();}
    }
    private static void checkPlayerViews(PathTestFixture fixture)throws Exception{
        Minecraft mc=fixture.mc;mc.displayWidth=W;mc.displayHeight=H;mc.gameSettings.guiScale=1;
        PathTestFixture.set(Minecraft.class,mc,"mcLanguageManager",new LanguageManager(new IMetadataSerializer(),"en_US"));
        fixture.player.inventory=new InventoryPlayer(fixture.player);
        EasterEggs eggs=EasterEggs.INSTANCE;eggs.command("halloween");
        try{
            ItemStack[] helmets={null,new ItemStack(Items.leather_helmet),new ItemStack(Items.chainmail_helmet),new ItemStack(Items.iron_helmet),new ItemStack(Items.golden_helmet),new ItemStack(Items.diamond_helmet),new ItemStack(Blocks.pumpkin)};
            for(int helmet=0;helmet<helmets.length;helmet++){
                fixture.player.inventory.armorInventory[3]=helmets[helmet];
                for(int view=0;view<3;view++){
                    mc.gameSettings.thirdPersonView=view;eggs.beginFrame();frame();State state=new State();
                    eggs.renderPumpkin(new ScaledResolution(mc));
                    // A hidden HUD uses the end-of-frame path. It must also stay clear in F5.
                    if(view!=0)eggs.renderEnd();
                    state.same();ByteBuffer pixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels(8,H-9,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
                    int color=(pixel.get(0)&255)<<16|(pixel.get(1)&255)<<8|pixel.get(2)&255;
                    check(view==0?color!=0x83B4EA:color==0x83B4EA,"Wrong pumpkin visibility for view "+view+" and helmet "+helmet);
                }
            }
        }finally{eggs.command("off");eggs.clear();}
    }
    private static int id(HolidayRenderer renderer,String name)throws Exception{Field field=HolidayRenderer.class.getDeclaredField(name);field.setAccessible(true);return field.getInt(renderer);}
    private static void frame(){
        GL11.glViewport(0,0,W,H);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glClearColor(0x83/255F,0xB4/255F,0xEA/255F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,W,H,0,-1,1);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
        GL11.glColor3f(.3F,.5F,.22F);GL11.glBegin(GL11.GL_QUADS);GL11.glVertex2i(0,365);GL11.glVertex2i(0,H);GL11.glVertex2i(W,H);GL11.glVertex2i(W,365);GL11.glEnd();
        // Simulate lightmap and caller state that used to darken raw overlays.
        for(int unit=1;unit<=2;unit++){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glEnable(GL11.GL_TEXTURE_2D);}
        GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.1F);GL11.glColor4f(.2F,.4F,.6F,.8F);
    }
    private static BufferedImage capture(String name)throws IOException {
        ByteBuffer pixels=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int index=((H-1-y)*W+x)*4;image.setRGB(x,y,(pixels.get(index)&255)<<16|(pixels.get(index+1)&255)<<8|pixels.get(index+2)&255);}
        ImageIO.write(image,"png",output.resolve(name).toFile());return image;
    }
    private static final class State {
        final int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),blendSrc=GL11.glGetInteger(GL11.GL_BLEND_SRC),blendDst=GL11.glGetInteger(GL11.GL_BLEND_DST);
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int[] textures=new int[3];final boolean[] enabled=new boolean[3];final float[] color=new float[4];
        State(){for(int i=0;i<3;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);enabled[i]=GL11.glIsEnabled(GL11.GL_TEXTURE_2D);}GL13.glActiveTexture(active);FloatBuffer data=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_CURRENT_COLOR,data);data.get(color);}
        void same(){State after=new State();check(active==after.active&&program==after.program&&depth==after.depth&&alpha==after.alpha&&blend==after.blend&&mask==after.mask&&blendSrc==after.blendSrc&&blendDst==after.blendDst&&Arrays.equals(textures,after.textures)&&Arrays.equals(enabled,after.enabled)&&Arrays.equals(color,after.color),"Caller GL state was changed");}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
