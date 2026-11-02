package dev.vibe.ui.render.esp;

import dev.vibe.Vibe;
import dev.vibe.module.impl.visual.TntESPModule;
import dev.vibe.ui.RenderUtils;
import dev.vibe.ui.effect.EffectState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.*;

/** A spherical grid with stronger principal rings, faint dashed occlusion and a fuse label. */
public final class TntEspRenderer {
    private final Minecraft mc=Minecraft.getMinecraft();
    private final java.util.Map<Integer,Integer> grids=new java.util.HashMap<>();
    public void render(RenderWorldLastEvent event){
        TntESPModule module=Vibe.getInstance().getModuleManager().getModule(TntESPModule.class);if(module==null||!module.isEnabled()||mc.theWorld==null||mc.thePlayer==null||module.forecasts().isEmpty())return;
        int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);boolean alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND);
        try(EffectState state=new EffectState()){
            GL20.glUseProgram(0);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glDepthMask(false);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_LINE_SMOOTH);
            for(TntESPModule.Forecast f:module.forecasts()){
                if(f.entity.isDead)continue;Vec3 p=module.predict.isEnabled()&&f.prediction.complete?f.prediction.position:new Vec3(f.entity.lastTickPosX+(f.entity.posX-f.entity.lastTickPosX)*event.partialTicks,f.entity.lastTickPosY+(f.entity.posY-f.entity.lastTickPosY)*event.partialTicks+f.entity.height/16D,f.entity.lastTickPosZ+(f.entity.posZ-f.entity.lastTickPosZ)*event.partialTicks);
                double x=p.xCoord-mc.getRenderManager().viewerPosX,y=p.yCoord-mc.getRenderManager().viewerPosY,z=p.zCoord-mc.getRenderManager().viewerPosZ;
                float seconds=Math.max(0,f.entity.fuse+1-event.partialTicks)/20F;int color=module.fuseColors.isEnabled()?RenderUtils.blend(module.urgent.getArgb(),module.color.getArgb(),Math.min(1,seconds/4)):module.color.getArgb();
                float fade=(float)Math.min(1,Math.max(0,(module.range.getDouble()+module.radius()-Math.sqrt(x*x+y*y+z*z))/12));
                if(module.hidden.isEnabled()){GL11.glDepthFunc(GL11.GL_GREATER);GL11.glEnable(GL11.GL_LINE_STIPPLE);GL11.glLineStipple(1,(short)0x0F0F);wireframe(module,x,y,z,color,module.hiddenOpacity.getFloat()*fade,1);GL11.glDisable(GL11.GL_LINE_STIPPLE);}
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                if(module.glow.isEnabled())circles(module,x,y,z,color,module.glowStrength.getFloat()*fade,module.lineWidth.getFloat()+2.5F);
                wireframe(module,x,y,z,color,module.opacity.getFloat()*fade,module.lineWidth.getFloat());
                if(module.path.isEnabled()&&f.prediction.complete){rgba(color,.32F*fade);GL11.glLineWidth(1);GL11.glBegin(GL11.GL_LINE_STRIP);for(Vec3 v:f.prediction.path)GL11.glVertex3d(v.xCoord-mc.getRenderManager().viewerPosX,v.yCoord-mc.getRenderManager().viewerPosY,v.zCoord-mc.getRenderManager().viewerPosZ);GL11.glEnd();}
                if(module.timer.isEnabled())label(x,y+.65,z,(Math.round(seconds*10)/10F)+"s"+(module.predict.isEnabled()&&!f.prediction.complete?" · position unknown":""),color,fade);
            }
        }finally{
            // Native fonts change Minecraft's Java cache inside the raw-GL scope.
            GlStateManager.bindTexture(0);GlStateManager.bindTexture(texture);GlStateManager.resetColor();
            GlStateManager.enableAlpha();GlStateManager.disableAlpha();if(alpha)GlStateManager.enableAlpha();
            GlStateManager.enableBlend();GlStateManager.disableBlend();if(blend)GlStateManager.enableBlend();
        }
    }
    private void wireframe(TntESPModule m,double x,double y,double z,int color,float alpha,float width){
        circles(m,x,y,z,color,alpha,width);
        if(m.gridOpacity.getFloat()<=0)return;
        int segments=segments(m),density=m.gridDensity.getInt(),key=segments*32+density;
        Integer list=grids.get(key);
        if(list==null){
            int allocated=GL11.glGenLists(1);
            if(allocated!=0){GL11.glNewList(allocated,GL11.GL_COMPILE);gridGeometry(segments,density);GL11.glEndList();grids.put(key,allocated);list=allocated;}
        }
        rgba(color,alpha*m.gridOpacity.getFloat());GL11.glLineWidth(Math.max(.5F,width*.8F));
        GL11.glPushMatrix();
        try{
            GL11.glTranslated(x,y,z);double r=m.radius();GL11.glScaled(r,r,r);
            if(list==null)gridGeometry(segments,density);else GL11.glCallList(list);
        }finally{GL11.glPopMatrix();}
    }
    private static int segments(TntESPModule m){return m.quality.is("Low")?32:m.quality.is("High")?96:64;}
    /** Unit-sphere geometry cached per quality and density; only the three main rings receive wide glow. */
    private static void gridGeometry(int segments,int density){
        for(int band=1;band<density;band++){
            if(band*2==density)continue; // The equator and two principal meridians are already drawn.
            double latitude=-Math.PI/2+Math.PI*band/density,y=Math.sin(latitude),radius=Math.cos(latitude),longitude=Math.PI*band/density;
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for(int i=0;i<segments;i++){double a=i*Math.PI*2/segments;GL11.glVertex3d(Math.cos(a)*radius,y,Math.sin(a)*radius);}
            GL11.glEnd();GL11.glBegin(GL11.GL_LINE_LOOP);
            for(int i=0;i<segments;i++){double a=i*Math.PI*2/segments,horizontal=Math.cos(a);GL11.glVertex3d(horizontal*Math.cos(longitude),Math.sin(a),horizontal*Math.sin(longitude));}
            GL11.glEnd();
        }
    }
    public void close(){for(int list:grids.values())GL11.glDeleteLists(list,1);grids.clear();}
    private static void circles(TntESPModule m,double x,double y,double z,int color,float alpha,float width){int n=segments(m);double r=m.radius();GL11.glLineWidth(width);
        for(int axis=0;axis<3;axis++){GL11.glBegin(GL11.GL_LINE_LOOP);for(int i=0;i<n;i++){double a=i*Math.PI*2/n,c=Math.cos(a),s=Math.sin(a);rgba(color,alpha*(float)(.52+.48*(s+1)/2));GL11.glVertex3d(x+(axis==1?0:c*r),y+(axis==0?0:(axis==1?c:s)*r),z+(axis==0?s*r:axis==1?s*r:0));}GL11.glEnd();}
    }
    private void label(double x,double y,double z,String text,int color,float opacity){GL11.glPushMatrix();try{GL11.glTranslated(x,y,z);GL11.glRotatef(-mc.getRenderManager().playerViewY,0,1,0);GL11.glRotatef(mc.getRenderManager().playerViewX,1,0,0);float size=.025F*(float)Math.max(1,Math.min(3,Math.sqrt(x*x+y*y+z*z)/12));GL11.glScalef(-size,-size,size);GL11.glEnable(GL11.GL_TEXTURE_2D);GlStateManager.resetColor();mc.fontRendererObj.drawString(text,-mc.fontRendererObj.getStringWidth(text)/2F,-10,Math.round(opacity*230)<<24|color&0xFFFFFF,true);GL11.glDisable(GL11.GL_TEXTURE_2D);}finally{GL11.glPopMatrix();}}
    private static void rgba(int c,float a){GL11.glColor4f((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,a*(c>>>24)/255F);}
}
