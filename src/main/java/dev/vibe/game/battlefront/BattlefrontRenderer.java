package dev.vibe.game.battlefront;

import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;
import static dev.vibe.game.battlefront.BattlefrontContent.*;
import static dev.vibe.game.battlefront.BattlefrontMeshes.*;

/** Cached terrain/scenery, directional light, terrain-conforming shadows and emissive blaster effects. */
public final class BattlefrontRenderer {
    private final Minecraft mc;private final BattlefrontRenderTarget target=new BattlefrontRenderTarget();
    private final BattlefrontMaterials materials=new BattlefrontMaterials();
    private BattlefrontWorld cached;private int scene;private final FloatBuffer buffer=BufferUtils.createFloatBuffer(4);
    private static final int CHUNK=48,ACROSS=BattlefrontWorld.TERRAIN_EDGE*2/CHUNK,CHUNKS=ACROSS*ACROSS;
    public BattlefrontRenderer(Minecraft mc){this.mc=mc;}
    private FloatBuffer values(float a,float b,float c,float d){buffer.clear();buffer.put(a).put(b).put(c).put(d).flip();return buffer;}
    public void render(BattlefrontGame g,boolean aim,boolean overview){
        int matrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int fbo=OpenGlHelper.framebufferSupported?GL11.glGetInteger(0x8CA6):0,rb=OpenGlHelper.framebufferSupported?GL11.glGetInteger(0x8CA7):0;
        int program=OpenGlHelper.shadersSupported?GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM):0;
        if(OpenGlHelper.shadersSupported)OpenGlHelper.glUseProgram(0);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT|GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPushMatrix();GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
        try{
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);GL11.glDisable(GL11.GL_TEXTURE_2D);OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,0);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);materials.create();
            boolean supersample=target.prepare(mc.displayWidth,mc.displayHeight);if(supersample)target.bind();else{if(OpenGlHelper.framebufferSupported)OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,fbo);GL11.glViewport(0,0,mc.displayWidth,mc.displayHeight);}
            if(GLContext.getCapabilities().OpenGL14)GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
            for(int i=0;i<6;i++)GL11.glDisable(GL11.GL_CLIP_PLANE0+i);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);GL11.glColorMask(true,true,true,true);GL11.glDepthRange(0,1);
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthMask(true);GL11.glClearDepth(1);
            boolean forest=g.scenario==Scenario.ENDOR;
            float r=forest?.16f:.58f,gr=forest?.25f:.36f,b=forest?.27f:.23f;
            GL11.glClearColor(r,gr,b,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_FOG);GL11.glFogi(GL11.GL_FOG_MODE,GL11.GL_LINEAR);GL11.glFogf(GL11.GL_FOG_START,forest?48:85);GL11.glFogf(GL11.GL_FOG_END,forest?190:270);GL11.glFog(GL11.GL_FOG_COLOR,values(r,gr,b,1));
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GLU.gluPerspective(overview?58:(float)g.fieldOfView(aim),(float)mc.displayWidth/Math.max(1,mc.displayHeight),.08f,420);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
            if(overview)GLU.gluLookAt(58,42,110,0,8,-12,0,1,0);
            else{BattlefrontGame.Camera c=g.camera(aim);GLU.gluLookAt((float)c.x,(float)c.y,(float)c.z,(float)(c.x+c.dx),(float)(c.y+c.dy),(float)(c.z+c.dz),0,1,0);}
            sky(g,forest,overview);
            GL11.glDisable(GL11.GL_LIGHTING);sphere(forest?-80:-90,forest?120:100,-190,forest?24:12,forest?24:12,forest?24:12,forest?0xB3C7BA:0xFFE6B0);
            if(forest){sphere(-77,119,-177,7,7,3,0x617B7C);box(-105,116,-175,-53,117,-174,0x7C9290);}
            lighting();interiorLighting(g);if(cached!=g.world)compile(g.world);
            for(int cx=0;cx<ACROSS;cx++)for(int cz=0;cz<ACROSS;cz++){
                double px=-BattlefrontWorld.TERRAIN_EDGE+cx*CHUNK+CHUNK/2.0,pz=-BattlefrontWorld.TERRAIN_EDGE+cz*CHUNK+CHUNK/2.0;
                if(Math.hypot(px-(overview?58:g.x),pz-(overview?110:g.z))>(forest?230:300))continue;
                if(scene!=0)GL11.glCallList(scene+cx*ACROSS+cz);else drawChunk(g.world,cx,cz);
            }
            for(BattlefrontGame.Soldier s:g.soldiers){
                if(Math.hypot(s.x-g.x,s.z-g.z)>135&&!overview)continue;
                if(!s.alive()&&s.respawn<4)continue;
                if(s.alive())shadow(g.world,s.x,s.z,.6,.42,.3);
                GL11.glPushMatrix();GL11.glTranslated(s.x,s.y,s.z);GL11.glRotated(-s.yaw,0,1,0);
                if(!s.alive()){GL11.glTranslated(0,.25,0);GL11.glRotated(90,1,0,0);}
                if(s.side==g.side)customizedSoldier(g.scenario.faction(s.side),s.role,g.progress,false,s.walk,g.time,false);
                else soldier(g.scenario.faction(s.side),s.role,s.walk,g.time);GL11.glPopMatrix();
            }
            if(!overview&&g.alive()){
                BattlefrontGame.Camera c=g.camera(aim);double distance=Math.sqrt(Math.pow(c.x-g.x,2)+Math.pow(c.y-g.eyeY(),2)+Math.pow(c.z-g.z,2));
                if(distance>.6){shadow(g.world,g.x,g.z,.7,.48,.4);GL11.glPushMatrix();GL11.glTranslated(g.x,g.y,g.z);GL11.glRotated(-g.yaw,0,1,0);
                    customizedSoldier(g.faction,Role.ASSAULT,g.progress,true,g.walk,g.time,g.jetting,g.reloadProgress());GL11.glPopMatrix();}
            }
            GL11.glDisable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            for(int i=0;i<g.posts.length;i++)post(g,g.posts[i],i);
            GL11.glDepthMask(false);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
            for(BattlefrontGame.Bolt bolt:g.bolts){
                int c=bolt.side==0?0x79D6FF:0xFF5D4D;GL11.glLineWidth(5);rgba(c,.16);line(bolt);GL11.glLineWidth(2);rgba(c,.95);line(bolt);
            }
            GL11.glPointSize(4);GL11.glBegin(GL11.GL_POINTS);for(BattlefrontGame.Particle p:g.particles){rgba(p.color,Math.min(1,p.life/p.max));v(p.x,p.y,p.z);}GL11.glEnd();
            // Floating motes make depth and atmosphere legible in both biomes.
            GL11.glPointSize(forest?2:1.5f);GL11.glBegin(GL11.GL_POINTS);
            for(int i=0;i<85;i++){double px=g.x+Math.sin(i*78.21)*65,pz=g.z+Math.cos(i*33.41)*65;rgba(forest?0xB1E8B2:0xF4C697,.25+.2*Math.sin(g.time+i));v(px,worldY(g,px,pz)+2+(i%9)*1.7+Math.sin(g.time*.3+i),pz);}GL11.glEnd();
            GL11.glDepthMask(true);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_FOG);
            if(supersample)target.present(fbo,mc.displayWidth,mc.displayHeight);
        }finally{
            if(OpenGlHelper.framebufferSupported){OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,fbo);OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER,rb);}
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPopMatrix();GL11.glMatrixMode(matrix);
            GL11.glPopClientAttrib();GL11.glPopAttrib();GlStateManager.bindTexture(0);GlStateManager.bindTexture(texture);
            GlStateManager.setActiveTexture(active);OpenGlHelper.setActiveTexture(active);if(OpenGlHelper.shadersSupported)OpenGlHelper.glUseProgram(program);GlStateManager.resetColor();
        }
    }
    private static double worldY(BattlefrontGame g,double x,double z){return g.world.height(x,z);}
    private void sky(BattlefrontGame g,boolean forest,boolean overview){
        GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_FOG);GL11.glDepthMask(false);GL11.glShadeModel(GL11.GL_SMOOTH);
        double cx=overview?58:g.x,cz=overview?110:g.z;
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for(int i=0;i<=32;i++){double a=i*Math.PI/16,px=cx+Math.sin(a)*300,pz=cz+Math.cos(a)*300;color(forest?0x628C90:0xC39E79);v(px,-15,pz);color(forest?0x102E42:0x483F55);v(px,235,pz);}GL11.glEnd();
        color(forest?0x102E42:0x483F55);GL11.glBegin(GL11.GL_TRIANGLE_FAN);v(cx,235,cz);for(int i=0;i<=32;i++){double a=i*Math.PI/16;v(cx+Math.sin(a)*300,235,cz+Math.cos(a)*300);}GL11.glEnd();
        GL11.glDepthMask(true);GL11.glEnable(GL11.GL_FOG);
    }
    private void lighting(){
        GL11.glEnable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_LIGHT0);GL11.glEnable(GL11.GL_COLOR_MATERIAL);GL11.glEnable(GL11.GL_NORMALIZE);
        for(int i=1;i<8;i++)GL11.glDisable(GL11.GL_LIGHT0+i);
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK,GL11.GL_AMBIENT_AND_DIFFUSE);GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT,values(.36f,.40f,.43f,1));
        GL11.glLight(GL11.GL_LIGHT0,GL11.GL_POSITION,values(-.5f,1,.35f,0));GL11.glLight(GL11.GL_LIGHT0,GL11.GL_DIFFUSE,values(.88f,.80f,.66f,1));
        GL11.glShadeModel(GL11.GL_SMOOTH);
    }
    private void interiorLighting(BattlefrontGame g){
        BattlefrontArchitecture.Building b=g.world.buildingAt(g.x,g.y,g.z);if(b==null)return;
        boolean wood=b.type.equals("village");
        for(int i=0;i<2;i++){int light=GL11.GL_LIGHT1+i;GL11.glEnable(light);
            GL11.glLight(light,GL11.GL_POSITION,values((float)(b.x+(i==0?-1:1)*b.w*.45),(float)(b.base+b.floor+3.2),(float)b.z,1));
            GL11.glLight(light,GL11.GL_DIFFUSE,values(wood?.95f:.5f,wood?.65f:.85f,wood?.3f:1,1));
            GL11.glLightf(light,GL11.GL_CONSTANT_ATTENUATION,1);GL11.glLightf(light,GL11.GL_LINEAR_ATTENUATION,.045f);GL11.glLightf(light,GL11.GL_QUADRATIC_ATTENUATION,.006f);
        }
    }
    private void compile(BattlefrontWorld w){
        if(scene!=0)GL11.glDeleteLists(scene,CHUNKS);scene=GL11.glGenLists(CHUNKS);cached=w;
        if(scene!=0)for(int x=0;x<ACROSS;x++)for(int z=0;z<ACROSS;z++){GL11.glNewList(scene+x*ACROSS+z,GL11.GL_COMPILE);drawChunk(w,x,z);GL11.glEndList();}
    }
    private boolean inChunk(double x,double z,int cx,int cz){return (int)Math.floor((x+BattlefrontWorld.TERRAIN_EDGE)/CHUNK)==cx&&(int)Math.floor((z+BattlefrontWorld.TERRAIN_EDGE)/CHUNK)==cz;}
    private void drawChunk(BattlefrontWorld w,int cx,int cz){
        int startX=-BattlefrontWorld.TERRAIN_EDGE+cx*CHUNK,startZ=-BattlefrontWorld.TERRAIN_EDGE+cz*CHUNK;
        GL11.glEnable(GL11.GL_TEXTURE_2D);materials.bind(w.scenario==Scenario.ENDOR?BattlefrontMaterials.SOIL:BattlefrontMaterials.SAND);
        GL11.glBegin(GL11.GL_TRIANGLES);
        for(int x=startX;x<startX+CHUNK;x+=2)for(int z=startZ;z<startZ+CHUNK;z+=2){terrainVertex(w,x,z);terrainVertex(w,x,z+2);terrainVertex(w,x+2,z);terrainVertex(w,x+2,z+2);terrainVertex(w,x+2,z);terrainVertex(w,x,z+2);}
        GL11.glEnd();
        for(BattlefrontWorld.Prop p:w.props){
            if(!inChunk(p.x,p.z,cx,cz))continue;
            materials.bind(p.type.equals("tree")||p.type.equals("village")?BattlefrontMaterials.BARK:p.type.equals("fern")?BattlefrontMaterials.LEAF:p.type.equals("rock")||p.type.equals("spire")?BattlefrontMaterials.STONE:BattlefrontMaterials.METAL);
            prop(p,w.scenario==Scenario.ENDOR,materials);
        }
        for(BattlefrontArchitecture.Building b:w.buildings)if(inChunk(b.x,b.z,cx,cz))for(BattlefrontArchitecture.Part p:b.parts){
            if(p.emissive){GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_TEXTURE_2D);}else{GL11.glEnable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_TEXTURE_2D);materials.bind(p.material);}
            box(p.x0,p.y0,p.z0,p.x1,p.y1,p.z1,p.color);
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        for(BattlefrontWorld.Prop p:w.props)if(p.radius>0&&inChunk(p.x,p.z,cx,cz)){double size=p.type.equals("tree")?p.size*.21:p.radius*1.3;shadow(w,p.x+size*.35,p.z-size*.2,size,size*.7,p.type.equals("tree")?.24:.30);}
    }
    private void terrainVertex(BattlefrontWorld w,double x,double z){
        double h=w.height(x,z),dx=w.height(x-.5,z)-w.height(x+.5,z),dz=w.height(x,z-.5)-w.height(x,z+.5),l=Math.sqrt(dx*dx+dz*dz+1);
        double noise=.93+.055*Math.sin(x*.91+z*1.43)+.035*Math.cos(x*.33-z*.67),trail=w.onTrail(x,z,3)?1:0;
        int c=w.scenario==Scenario.ENDOR?(trail==1?0x85785D:h>9?0x63715A:0x596E50):(h<4?0x9F6F53:trail==1?0xC89B70:0xB88961);
        GL11.glColor3d((c>>16&255)/255.0*noise,(c>>8&255)/255.0*noise,(c&255)/255.0*noise);GL11.glNormal3d(dx/l,1/l,dz/l);GL11.glTexCoord2d(x/5,z/5);v(x,h,z);
    }
    private void shadow(BattlefrontWorld w,double x,double z,double rx,double rz,double alpha){
        GL11.glDisable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glDepthMask(false);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glColor4d(.03,.045,.05,alpha);v(x,w.height(x,z)+.04,z);
        for(int i=0;i<=24;i++){double a=i*Math.PI/12,px=x+Math.sin(a)*rx,pz=z+Math.cos(a)*rz;GL11.glColor4d(.03,.045,.05,0);v(px,w.height(px,pz)+.055,pz);}GL11.glEnd();
        GL11.glDepthMask(true);GL11.glDisable(GL11.GL_BLEND);GL11.glEnable(GL11.GL_LIGHTING);
    }
    private void post(BattlefrontGame g,BattlefrontGame.Post p,int index){
        int c=p.owner<0?0xEBCB84:g.scenario.faction(p.owner).color;double y=g.world.height(p.x,p.z);
        box(p.x-.5,y,p.z-.5,p.x+.5,y+.4,p.z+.5,0x788B8F);box(p.x-.06,y,p.z-.06,p.x+.06,y+5,p.z+.06,0xB2CFD0);
        box(p.x,y+3.3,p.z-.035,p.x+1.9,y+4.7,p.z+.035,c);
        GL11.glDepthMask(false);GL11.glBegin(GL11.GL_QUAD_STRIP);
        for(int i=0;i<=48;i++){double a=i*Math.PI/24,px=p.x+Math.sin(a)*8,pz=p.z+Math.cos(a)*8,h=g.world.height(px,pz);rgba(c,.32);v(px,h+.12,pz);rgba(c,0);v(px,h+1.5,pz);}GL11.glEnd();GL11.glDepthMask(true);
    }
    private static void rgba(int c,double a){GL11.glColor4d((c>>16&255)/255.0,(c>>8&255)/255.0,(c&255)/255.0,a);}
    private static void line(BattlefrontGame.Bolt b){GL11.glBegin(GL11.GL_LINES);v(b.x,b.y,b.z);v(b.tx,b.ty,b.tz);GL11.glEnd();}
    /** Preview embedded in GUI coordinates; preserves Minecraft's matrices and state. */
    public void preview(Faction faction,Role role,int x,int y,int size,double spin){
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushMatrix();
        try{GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glTranslated(x,y,150);GL11.glScaled(size,-size,size);GL11.glRotated(12,1,0,0);GL11.glRotated(180+spin,0,1,0);lighting();soldier(faction,role,0,0);
        }finally{GL11.glPopMatrix();GL11.glPopAttrib();GlStateManager.resetColor();}
    }
    public void equipmentPreview(Faction faction,Armor armor,Weapon weapon,int x,int y,int size,double spin,boolean weaponOnly,int weaponLevel,int armorLevel){
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushMatrix();
        try{GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glTranslated(x,y,150);GL11.glScaled(size,-size,size);GL11.glRotated(12,1,0,0);GL11.glRotated(weaponOnly?75+spin:180+spin,0,1,0);lighting();
            if(weaponOnly)weaponModel(weapon,weaponLevel);else equippedSoldier(faction,armor,weapon,0,0,weaponLevel,armorLevel,false);
        }finally{GL11.glPopMatrix();GL11.glPopAttrib();GlStateManager.resetColor();}
    }
    public void customizedPreview(Faction faction,Role role,BattlefrontProgress progress,boolean player,int x,int y,int size,double spin){
        customizedPreview(faction,role,progress,player,x,y,size,spin,-1);
    }
    public void customizedPreview(Faction faction,Role role,BattlefrontProgress progress,boolean player,int x,int y,int size,double spin,double reloadProgress){
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushMatrix();
        try{GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glTranslated(x,y,150);GL11.glScaled(size,-size,size);GL11.glRotated(12,1,0,0);GL11.glRotated(180+spin,0,1,0);lighting();customizedSoldier(faction,role,progress,player,0,0,false,reloadProgress);
        }finally{GL11.glPopMatrix();GL11.glPopAttrib();GlStateManager.resetColor();}
    }
    public void close(){if(scene!=0)GL11.glDeleteLists(scene,CHUNKS);scene=0;cached=null;target.close();materials.close();}
}
