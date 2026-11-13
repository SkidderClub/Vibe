package dev.vibe.features;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.visual.AspectRatioModule;
import dev.vibe.ui.ClientChangesRenderCheck;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import java.nio.FloatBuffer;
public final class AspectRatioRenderCheck {
    public static void verify(ModuleManager modules)throws Exception{
        ClientChangesRenderCheck.frame(1280,720);AspectRatioModule module=new AspectRatioModule();modules.registerDynamic(module);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();
        try{GL11.glLoadIdentity();GLU.gluPerspective(70,16F/9,.05F,128);float[] nativeMatrix=matrix();module.setEnabled(true);
            GL11.glLoadIdentity();AspectRatioModule.perspective(70,16F/9,.05F,128,false);float[] world=matrix();near(world[0]/nativeMatrix[0],4F/3);for(int i:new int[]{5,10,11,14})near(world[i],nativeMatrix[i]);
            GL11.glLoadIdentity();AspectRatioModule.perspective(70,16F/9,.05F,128,true);same(nativeMatrix,matrix());module.hand.setEnabled(true);GL11.glLoadIdentity();AspectRatioModule.perspective(70,16F/9,.05F,128,true);same(world,matrix());
            module.setEnabled(false);GL11.glLoadIdentity();AspectRatioModule.perspective(70,16F/9,.05F,128,false);same(nativeMatrix,matrix());
        }finally{module.setEnabled(false);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_MODELVIEW);}if(GL11.glGetError()!=0)throw new AssertionError("Aspect Ratio projection GL error");System.out.println("Aspect Ratio native world/hand projection, FOV/depth preservation and disable restoration passed.");
    }
    private static float[] matrix(){FloatBuffer b=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,b);float[] result=new float[16];b.get(result);return result;}
    private static void near(float a,float b){if(Math.abs(a-b)>.0001F)throw new AssertionError(a+" != "+b);}
    private static void same(float[] a,float[] b){for(int i=0;i<16;i++)near(a[i],b[i]);}
}
