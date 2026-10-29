package dev.vibe.ui.render;

import com.mojang.authlib.GameProfile;
import dev.vibe.camera.PerspectiveTransition;
import dev.vibe.event.*;
import dev.vibe.module.impl.visual.*;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.world.ChestStealerModule;
import dev.vibe.path.PathTestFixture;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.model.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.util.*;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Matrix4f;

/** Actual model joints, native skin UVs, glow/material pixels and GUI controls in a hidden GL context. */
public final class CameraHologramRenderCheck {
    private static final int W=960,H=600;
    private static final ResourceLocation SKIN=new ResourceLocation("vibe","test-hologram-skin");
    private static Path output;private static Minecraft mc;private static HitmarkerModule module;
    public static void main(String[] args)throws Exception{
        output=Paths.get(args[0]);Files.createDirectories(output);Pbuffer buffer=new Pbuffer(W,H,new PixelFormat(8,24,8),null,null);buffer.makeCurrent();
        try(PathTestFixture f=new PathTestFixture();HologramRenderer renderer=new HologramRenderer()){
            mc=f.mc;resources();module=new HitmarkerModule();f.modules.registerDynamic(module);module.getModes().toggle("Hologram");module.setEnabled(true);module.hologramLifetime.setValue(5000D);module.hologramDelay.setValue(0D);module.hologramFade.setValue(false);
            RenderManager manager=PathTestFixture.allocate(RenderManager.class);PathTestFixture.set(Minecraft.class,mc,"renderManager",manager);
            RenderPlayer playerRenderer=new RenderPlayer(manager,false),slimRenderer=new RenderPlayer(manager,true);
            Map<String,RenderPlayer> skins=new HashMap<>();skins.put("default",playerRenderer);skins.put("slim",slimRenderer);PathTestFixture.set(RenderManager.class,manager,"skinMap",skins);renderer.installLayers();
            Avatar target=PathTestFixture.allocate(Avatar.class);target.worldObj=f.world;target.width=.6F;target.height=1.8F;target.posZ=-3;target.setEntityBoundingBox(new AxisAlignedBB(-.3,0,-3.3,.3,1.8,-2.7));
            preparePose(playerRenderer,target,false);FloatBuffer original=BufferUtils.createFloatBuffer(16);module.getHolograms().get(0).matrix(original);
            preparePose(playerRenderer,target,false,true);FloatBuffer moved=BufferUtils.createFloatBuffer(16);module.getHolograms().get(0).matrix(moved);for(int i=0;i<16;i++)check(Math.abs(original.get(i)-moved.get(i))<1e-5,"A moving camera changed the frozen world-space pose");
            module.hologramGlow.setValue(false);module.hologramTransparency.setValue(15D);
            preparePose(playerRenderer,target,false);scene();State before=new State();renderer.render(module);before.same();byte[] flat=pixels();check(visible(flat)>5000,"Native hologram was empty");save("hologram-flat.png",flat);
            module.hologramGlow.setValue(true);preparePose(playerRenderer,target,false);scene();before=new State();renderer.render(module);before.same();byte[] glow=pixels();check(visible(glow)>visible(flat),"Glow did not extend beyond the native mesh");save("hologram-glow.png",glow);
            module.hologramGlow.setValue(false);module.hologramSkin.setValue(true);module.hologramSkinOpacity.setValue(100D);module.hologramColor.setValue(0xFF00FF00);
            preparePose(playerRenderer,target,false);scene();renderer.render(module);byte[] skin=pixels();save("hologram-skin.png",skin);check(redPixels(skin)>100,"Original skin color/UVs were not drawn");
            module.hologramBrightness.setValue(true);preparePose(playerRenderer,target,false);scene();renderer.render(module);byte[] luminance=pixels();save("hologram-brightness.png",luminance);check(redPixels(luminance)<redPixels(skin)/4,"Brightness-only material retained skin hue");
            module.hologramBrightness.setValue(false);module.hologramTransparency.setValue(100D);preparePose(playerRenderer,target,false);scene();renderer.render(module);check(visible(pixels())==0,"Full transparency still drew a hologram");
            module.hologramTransparency.setValue(25D);module.hologramSkinOpacity.setValue(65D);module.hologramColor.setValue(0xFF65D8FF);module.hologramGlow.setValue(true);
            preparePose(slimRenderer,target,true);scene();renderer.render(module);check(visible(pixels())>1000,"Slim sneaking pose disappeared");save("hologram-slim-sneaking.png",pixels());
            Field failed=HologramRenderer.class.getDeclaredField("failed");failed.setAccessible(true);check(!failed.getBoolean(renderer),"Hologram shader failed to compile");
            preparePose(playerRenderer,target,false);scene();GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);int skinTexture=mc.getTextureManager().getTexture(SKIN).getGlTextureId();int caller0=GL11.glGenTextures(),caller2=GL11.glGenTextures();GlStateManager.bindTexture(caller0);GlStateManager.setActiveTexture(GL13.GL_TEXTURE2);GlStateManager.bindTexture(caller2);
            renderer.render(module);check(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==GL13.GL_TEXTURE2&&GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==caller2,"Lazy skin upload changed the caller texture unit");
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==caller0,"Lazy skin upload changed the caller skin binding");GlStateManager.setActiveTexture(GL13.GL_TEXTURE2);
            GlStateManager.bindTexture(skinTexture);check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==skinTexture,"Lazy upload left Minecraft's texture cache out of sync");GlStateManager.bindTexture(0);GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GlStateManager.bindTexture(0);GL11.glDeleteTextures(caller0);GL11.glDeleteTextures(caller2);
            camera(f);menus(f);module.setEnabled(false);check(module.getHolograms().isEmpty(),"Holograms survived module disable");
            check(GL11.glGetError()==0,"Camera/hologram rendering left a GL error");
            System.out.println("Camera/hologram GPU checks passed: actual RenderPlayer layers and frozen joints, normal/slim sneaking poses, skin UVs, luminance-only skin, transparency, shared glow, exact GL restoration, SmoothF5 matrix/body state and pause/chest button layouts.");
        }finally{buffer.destroy();}
    }
    private static void resources()throws Exception{
        mc.displayWidth=W;mc.displayHeight=H;mc.gameSettings.guiScale=1;OpenGlHelper.initializeTextures();
        PathTestFixture.set(Minecraft.class,mc,"mcDataDir",output.toFile());
        IMetadataSerializer metadata=new IMetadataSerializer();metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(),TextureMetadataSection.class);metadata.registerMetadataSectionType(new FontMetadataSectionSerializer(),FontMetadataSection.class);
        PathTestFixture.set(Minecraft.class,mc,"mcLanguageManager",new LanguageManager(metadata,"en_US"));SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(metadata);resources.reloadResourcePack(new DefaultResourcePack(Collections.<String,File>emptyMap()));
        PathTestFixture.set(Minecraft.class,mc,"mcResourceManager",resources);mc.renderEngine=new TextureManager(resources);mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.renderEngine,false);mc.fontRendererObj.onResourceManagerReload(resources);
        DynamicTexture texture=new DynamicTexture(64,64){@Override public int getGlTextureId(){int id=super.getGlTextureId();GlStateManager.bindTexture(id);return id;}};int[] data=texture.getTextureData();for(int y=0;y<64;y++)for(int x=0;x<64;x++)data[y*64+x]=0xFF000000|((x/4%2==0?220:30)<<16)|((y/4%2==0?40:170)<<8)|80;
        texture.updateDynamicTexture();mc.renderEngine.loadTexture(SKIN,texture);
    }
    private static void preparePose(RenderPlayer renderer,Avatar avatar,boolean sneak)throws Exception{preparePose(renderer,avatar,sneak,false);}
    @SuppressWarnings("unchecked") private static void preparePose(RenderPlayer renderer,Avatar avatar,boolean sneak,boolean movedCamera)throws Exception{
        module.clear();avatar.sneak=sneak;ModelPlayer model=renderer.getMainModel();model.isSneak=sneak;model.isRiding=false;model.swingProgress=.35F;model.heldItemRight=1;model.setRotationAngles(1.4F,.75F,80,25,-12,1/16F,avatar);
        scene();if(movedCamera){GL11.glRotatef(26,1,0,0);GL11.glRotatef(-41,0,1,0);GL11.glTranslatef(.7F,-.3F,1.2F);}HologramRenderer.beginWorld();GL11.glPushMatrix();GL11.glTranslatef(0,.1F,-3);GL11.glRotatef(155,0,1,0);GL11.glScalef(-1,-1,1);GL11.glTranslatef(0,-1.5078125F,0);
        Field layers=RendererLivingEntity.class.getDeclaredField("layerRenderers");layers.setAccessible(true);List<LayerRenderer> list=(List<LayerRenderer>)layers.get(renderer);list.get(list.size()-1).doRenderLayer(avatar,1.4F,.75F,1,80,25,-12,1/16F);
        GL11.glPopMatrix();HologramRenderer.endWorld();module.mark(avatar);check(module.getHolograms().size()==1,"RenderPlayer layer did not capture the attacked player");
        // The live model moves after impact; the retained pose must remain frozen.
        model.bipedRightArm.rotateAngleX=2.8F;
    }
    private static void camera(PathTestFixture f)throws Exception{
        SmoothF5Module smooth=new SmoothF5Module();f.modules.registerDynamic(smooth);smooth.setEnabled(true);mc.gameSettings.thirdPersonView=0;smooth.frameTick(1);
        Field field=SmoothF5Module.class.getDeclaredField("transition");field.setAccessible(true);PerspectiveTransition t=(PerspectiveTransition)field.get(smooth);long now=System.nanoTime();t.reset(0);t.update(1,now-500000000,1,"Sine","Top");mc.gameSettings.thirdPersonView=1;
        double[] body={f.player.posX,f.player.posY,f.player.posZ,f.player.rotationYaw,f.player.rotationPitch,f.player.motionX,f.player.motionY,f.player.motionZ};
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();check(SmoothF5Module.orientCameraHook(1),"SmoothF5 did not replace the intermediate camera matrix");FloatBuffer values=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,values);for(int i=0;i<16;i++)check(Float.isFinite(values.get(i)),"Camera matrix was not finite");
        double[] after={f.player.posX,f.player.posY,f.player.posZ,f.player.rotationYaw,f.player.rotationPitch,f.player.motionX,f.player.motionY,f.player.motionZ};check(Arrays.equals(body,after),"SmoothF5 changed player movement or rotations");smooth.setEnabled(false);check(!SmoothF5Module.orientCameraHook(1),"Disabled SmoothF5 kept overriding the camera");mc.gameSettings.thirdPersonView=0;
    }
    private static void menus(PathTestFixture f)throws Exception{
        f.mc.setServerData(new ServerData("Demo","example.invalid",false));GuiIngameMenu pause=new GuiIngameMenu();pause.width=W;pause.height=H;List<GuiButton> buttons=new ArrayList<>();new ReconnectEvents().init(new GuiScreenEvent.InitGuiEvent.Post(pause,buttons));
        gui();for(GuiButton b:buttons){check(b.enabled,"Pause reconnect button was disabled on a server");b.drawButton(mc,-1,-1);}save("pause-reconnect-buttons.png",pixels());
        KillAuraModule aura=new KillAuraModule();ChestStealerModule stealer=new ChestStealerModule();f.modules.registerDynamic(aura);f.modules.registerDynamic(stealer);PathTestFixture.set(dev.vibe.module.Module.class,aura,"enabled",true);PathTestFixture.set(dev.vibe.module.Module.class,stealer,"enabled",true);
        ChestControlsEvents controls=new ChestControlsEvents();GuiChest chest=PathTestFixture.allocate(GuiChest.class);chest.width=W;chest.height=H;buttons.clear();controls.init(new GuiScreenEvent.InitGuiEvent.Post(chest,buttons));gui();for(GuiButton b:buttons){check(b.visible&&b.yPosition<50,"Chest controls missing at top right");b.drawButton(mc,-1,-1);}save("chest-disable-buttons.png",pixels());
    }
    private static void scene(){
        GL20.glUseProgram(0);for(int i=0;i<3;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);GL11.glDisable(GL11.GL_TEXTURE_2D);}GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glViewport(0,0,W,H);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_FOG);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDepthMask(true);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glClearColor(0,0,0,1);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(-2.2,2.2,-.5,2.25,.1,100);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glColor4f(.4F,.5F,.6F,.7F);
    }
    private static void gui(){scene();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,W,H,0,-1,1);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();dev.vibe.ui.GuiRenderState.prepare(false);}
    private static byte[] pixels(){ByteBuffer data=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);byte[] result=new byte[data.capacity()];data.get(result);return result;}
    private static int visible(byte[] data){int count=0;for(int i=0;i<data.length;i+=4)if((data[i]&255)>3||(data[i+1]&255)>3||(data[i+2]&255)>3)count++;return count;}
    private static int redPixels(byte[] data){int count=0;for(int i=0;i<data.length;i+=4)if((data[i]&255)>30&&(data[i]&255)>(data[i+1]&255)*1.4)count++;return count;}
    private static void save(String name,byte[] data)throws Exception{BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=((H-1-y)*W+x)*4;image.setRGB(x,y,(data[i]&255)<<16|(data[i+1]&255)<<8|data[i+2]&255);}ImageIO.write(image,"png",output.resolve(name).toFile());}
    private static final class State {
        final int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);final float[] matrix=new float[16];
        State(){FloatBuffer data=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,data);data.get(matrix);}
        void same(){State after=new State();check(active==after.active&&program==after.program&&depth==after.depth&&mask==after.mask&&Arrays.equals(matrix,after.matrix),"Hologram changed caller graphics state");}
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static final class Avatar extends AbstractClientPlayer {
        boolean sneak;private Avatar(){super(null,new GameProfile(UUID.randomUUID(),"Avatar"));}
        @Override public ResourceLocation getLocationSkin(){return SKIN;}
        @Override public boolean isSneaking(){return sneak;}
    }
}
