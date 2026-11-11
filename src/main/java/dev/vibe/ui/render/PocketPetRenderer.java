package dev.vibe.ui.render;

import dev.vibe.module.impl.meme.PocketPetsModule;
import dev.vibe.ui.effect.EffectState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.*;

/** Small shaded meshes, no entity AI, new render targets or texture allocations. */
public final class PocketPetRenderer {
    public void render(PocketPetsModule m,float partial){Minecraft mc=Minecraft.getMinecraft();Vec3 p=m.position(partial);double age=(mc.thePlayer.ticksExisted+partial),s=m.size.getDouble();
        try(EffectState state=new EffectState()){GL11.glPushMatrix();try{GL20.glUseProgram(0);GL11.glTranslated(p.xCoord-mc.getRenderManager().viewerPosX,p.yCoord-mc.getRenderManager().viewerPosY,p.zCoord-mc.getRenderManager().viewerPosZ);GL11.glScaled(s,s,s);GL11.glRotatef(-m.follower.yaw,0,1,0);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            if(m.pet.is("Ghost")){GL11.glTranslated(0,.12+Math.sin(age*.075)*.035,0);ghost(m.ghostColor.getArgb(),age);oval(-.075,.29,.173,.033,.052,.015,0xFF17233A);oval(.075,.29,.173,.033,.052,.015,0xFF17233A);oval(-.084,.31,.185,.009,.014,.005,0xFFF9FDFF);oval(.066,.31,.185,.009,.014,.005,0xFFF9FDFF);oval(0,.20,.184,.018,.023,.009,0xFF31445E);
            }else{double w=Math.sin(age*.12)*.04;GL11.glRotated(m.follower.walkAmount*8*Math.sin(age*.8),0,0,1);oval(0,.15,0,.22,.15,.28,m.duckColor.getArgb());oval(0,.33,.17,.145,.15,.14,m.duckColor.getArgb());oval(0,.285,.315,.12,.035,.085,0xFFE78E32);oval(-.09,.36,.272,.02,.025,.013,0xFF242536);oval(.09,.36,.272,.02,.025,.013,0xFF242536);oval(-.197,.18,-.025,.04,.075,.16,0xFFE9B43C);oval(.197,.18,-.025,.04,.075,.16,0xFFE9B43C);oval(0,.21,-.235,.10,.09,.13,0xFFF4C247);}
        }finally{GL11.glPopMatrix();}}
    }
    private static void ghost(int c,double age){for(int band=0;band<10;band++){GL11.glBegin(GL11.GL_QUAD_STRIP);for(int i=0;i<=24;i++){double a=i*Math.PI*2/24;for(int k=0;k<2;k++){double t=(band+k)/10.,r=.19*Math.sqrt(Math.max(0,1-Math.pow(Math.max(0,(t-.45)/.55),2))),y=.07+t*.4;if(band+k==0)y+=Math.sin(a*5+age*.06)*.022;shade(c,.76+.24*Math.max(0,Math.sin(a)*.6+Math.cos(a)*.4));GL11.glVertex3d(Math.sin(a)*r,y,Math.cos(a)*r);}}GL11.glEnd();}}
    private static void oval(double x,double y,double z,double rx,double ry,double rz,int c){for(int j=0;j<8;j++){GL11.glBegin(GL11.GL_QUAD_STRIP);for(int i=0;i<=16;i++){double a=i*Math.PI*2/16;for(int k=0;k<2;k++){double b=-Math.PI/2+(j+k)*Math.PI/8;shade(c,.68+.32*Math.max(0,Math.sin(b)*.5+Math.cos(a)*Math.cos(b)*.5));GL11.glVertex3d(x+Math.sin(a)*Math.cos(b)*rx,y+Math.sin(b)*ry,z+Math.cos(a)*Math.cos(b)*rz);}}GL11.glEnd();}}
    private static void shade(int c,double light){GL11.glColor4d((c>>16&255)/255.*light,(c>>8&255)/255.*light,(c&255)/255.*light,(c>>>24)/255.);}
}
