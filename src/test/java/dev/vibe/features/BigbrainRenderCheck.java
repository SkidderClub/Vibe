package dev.vibe.features;

import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.meme.*;
import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.ClientChangesRenderCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.client.renderer.GlStateManager;
import java.nio.*;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class BigbrainRenderCheck {
    public static void verify(Minecraft mc,ModuleManager modules,Path output)throws Exception{
        BigbrainModule big=new BigbrainModule();BobbleheadModule bobble=new BobbleheadModule();modules.registerDynamic(big);modules.registerDynamic(bobble);
        EntityPlayer player=PathTestFixture.allocate(net.minecraft.client.entity.EntityPlayerSP.class);EntityPig pig=PathTestFixture.allocate(EntityPig.class);
        ClientChangesRenderCheck.frame(1280,720);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GlStateManager.disableTexture2D();
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(-4,4,-.2,3.4,-10,10);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
        try{
            for(int i=0;i<3;i++){
                big.setEnabled(i>0);bobble.setEnabled(i==2);PathTestFixture.set(BobbleheadModule.class,bobble,"roll",.32F);PathTestFixture.set(BobbleheadModule.class,bobble,"pitch",.16F);
                ModelPlayer model=new ModelPlayer(0,i==2);model.setRotationAngles(.25F,.3F,50,0,0,.0625F,player);if(i==2)EmotePoses.apply(model);
                GlStateManager.pushMatrix();GlStateManager.translate(-2.5+i*2.5,2.15,0);GlStateManager.scale(1,-1,1);GL11.glRotatef(-15,0,1,0);
                float roll=model.bipedHead.rotateAngleZ;
                for(ModelRenderer part:new ModelRenderer[]{model.bipedHead,model.bipedHeadwear,model.bipedBody,model.bipedRightArm,model.bipedLeftArm,model.bipedRightLeg,model.bipedLeftLeg,model.bipedBodyWear,model.bipedRightArmwear,model.bipedLeftArmwear,model.bipedRightLegwear,model.bipedLeftLegwear}){
                    GlStateManager.color(part==model.bipedHead||part==model.bipedHeadwear?.78F:.3F,.68F,.9F,1);float[] before=matrix();BigbrainModule.renderPart(part,.0625F,model,player);same(before,matrix());
                }
                if(model.bipedHead.rotateAngleZ!=roll)throw new AssertionError("Bigbrain changed Bobblehead's pose");
                GlStateManager.popMatrix();EmotePoses.before(model);
            }
            big.setEnabled(true);GlStateManager.pushMatrix();GL11.glLoadIdentity();ModelBiped model=new ModelBiped();Probe head=new Probe(model);head.rotationPointY=1;head.rotationPointZ=2;model.bipedHead=head;
            BigbrainModule.renderPart(head,1,model,player);near(head.seen[0],BigbrainModule.HEAD_SCALE);near(head.seen[13]+head.rotationPointY*BigbrainModule.HEAD_SCALE,24*(1-BigbrainModule.BODY_SCALE)+BigbrainModule.BODY_SCALE);near(head.seen[14]+head.rotationPointZ*BigbrainModule.HEAD_SCALE,2*BigbrainModule.BODY_SCALE);
            BigbrainModule.renderPart(head,1,model,pig);near(head.seen[0],1);near(head.seen[13],0);
            big.setEnabled(false);BigbrainModule.renderPart(head,1,model,player);near(head.seen[0],1);near(head.seen[13],0);
            big.setEnabled(true);Probe body=new Probe(model);BigbrainModule.renderPart(body,1,model,player);near(body.seen[0],BigbrainModule.BODY_SCALE);near(body.seen[13]+24*body.seen[5],24);
            BigbrainModule.beginCape(player);float[] cape=matrix();near(cape[5],BigbrainModule.BODY_SCALE);near(cape[13]+1.5F*cape[5],1.5F);GlStateManager.popMatrix();
            save(output.resolve("bigbrain-normal-large-bobblehead.png"));if(GL11.glGetError()!=0)throw new AssertionError("Bigbrain GL state error");
        }finally{big.setEnabled(false);bobble.setEnabled(false);}
        System.out.println("Bigbrain native skin/slim layers, grounded body, head pivots, Bobblehead, non-player exclusion and immediate disable passed.");
    }
    private static final class Probe extends ModelRenderer {float[] seen;Probe(ModelBase model){super(model);} @Override public void render(float scale){seen=matrix();}}
    private static float[] matrix(){FloatBuffer buffer=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,buffer);float[] result=new float[16];buffer.get(result);return result;}
    private static void same(float[] a,float[] b){for(int i=0;i<16;i++)near(a[i],b[i]);}
    private static void near(float a,float b){if(Math.abs(a-b)>.0001F)throw new AssertionError(a+" != "+b);}
    private static void save(Path path)throws Exception{ByteBuffer p=BufferUtils.createByteBuffer(1280*720*4);GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB);for(int y=0;y<720;y++)for(int x=0;x<1280;x++){int i=((719-y)*1280+x)*4;image.setRGB(x,y,(p.get(i)&255)<<16|(p.get(i+1)&255)<<8|p.get(i+2)&255);}ImageIO.write(image,"png",path.toFile());}
}
