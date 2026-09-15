package dev.vibe.game.gta;

import dev.vibe.module.impl.EspModule;
import dev.vibe.ui.EspRenderer;
import java.nio.FloatBuffer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

/** Native LWJGL 2 / Minecraft OpenGL city renderer, with cached district meshes. */
public final class Gta7Renderer {
    private final Minecraft mc;
    private final EspRenderer policeEsp = new EspRenderer();
    private final Gta7Materials materials = new Gta7Materials();
    private final Gta7RenderTarget target = new Gta7RenderTarget();
    private final dev.vibe.ui.effect.FogRenderer fog = new dev.vibe.ui.effect.FogRenderer();
    private int lists;
    private int listCount;
    private int shadowList;
    private final Gta7EmojiRenderer emoji=new Gta7EmojiRenderer();
    private int actorLists,carLists,wheelList;
    private final boolean[] actorBuilt=new boolean[Gta7Game.Kind.values().length*8],carBuilt=new boolean[CAR_COUNT];
    private static final int CAR_COUNT=6;
    private static final int[] SKIN={0xD4AE8B,0xAE8263,0xC99B76,0x8B6854};
    private double viewSin,viewCos,pitchSin,pitchCos,viewTanX,viewTanY;
    private Gta7World cachedWorld;
    private boolean[] compiled;
    private final FloatBuffer lightBuffer=BufferUtils.createFloatBuffer(4);
    private final java.util.List<Gta7World.Window> visibleWindows=new java.util.ArrayList<Gta7World.Window>();
    private double aimBlend, equipDip;
    private int lastWeapon = -1;
    private long previousFrame;
    private final FloatBuffer fogColor = BufferUtils.createFloatBuffer(4);
    private static final int[] CAR_COLORS = {0xD2B879, 0x508286, 0xA45349, 0xD0D2C7, 0x465568, 0x739A79};
    private static final int[] SHIRTS = {0xAA6D55,0x568589,0xC0A36B,0x747B92,0xA4B0A0,0x94687F,0x738354,0xC19B85};

    public Gta7Renderer(Minecraft mc) { this.mc = mc; fogColor.put(new float[]{.73f,.78f,.77f,1}).flip(); }

    public void render(Gta7Game game, EspModule esp, boolean aim) {
        long now=System.nanoTime();
        double dt=previousFrame==0?0:Math.min(.05,(now-previousFrame)/1e9);previousFrame=now;
        aimBlend+=(aim&&game.weapon==1?1-aimBlend:-aimBlend)*(1-Math.exp(-dt*18));
        if(lastWeapon!=game.weapon){equipDip=lastWeapon<0?0:.16;lastWeapon=game.weapon;}
        equipDip*=Math.exp(-dt*16);
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int activeTexture = GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int defaultTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int destination=OpenGlHelper.framebufferSupported?GL11.glGetInteger(0x8CA6):0;
        int renderbuffer=OpenGlHelper.framebufferSupported?GL11.glGetInteger(0x8CA7):0;
        int program = OpenGlHelper.shadersSupported ? GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM) : 0;
        if (OpenGlHelper.shadersSupported) OpenGlHelper.glUseProgram(0);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT | GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPushMatrix();GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        try {
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,0);
            if(org.lwjgl.opengl.GLContext.getCapabilities().OpenGL14)org.lwjgl.opengl.GL14.glBlendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);
            materials.create();
            boolean supersample=target.prepare(mc.displayWidth,mc.displayHeight);
            if(supersample)target.bind();
            else {
                if(OpenGlHelper.framebufferSupported)OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,destination);
                GL11.glViewport(0,0,mc.displayWidth,mc.displayHeight);
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_LINE);GL11.glDisable(GL11.GL_POLYGON_STIPPLE);
            GL11.glDisable(GL11.GL_LINE_STIPPLE);GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
            GL11.glDisable(GL11.GL_TEXTURE_GEN_S);GL11.glDisable(GL11.GL_TEXTURE_GEN_T);
            GL11.glDisable(GL11.GL_TEXTURE_GEN_R);GL11.glDisable(GL11.GL_TEXTURE_GEN_Q);
            for(int i=0;i<6;i++)GL11.glDisable(GL11.GL_CLIP_PLANE0+i);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);
            GL11.glShadeModel(GL11.GL_SMOOTH);GL11.glDepthRange(0,1);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
            GL11.glColorMask(true,true,true,true);
            GL11.glDepthMask(true); GL11.glClearDepth(1);
            float light=(float)game.daylight(),dusk=(float)twilight(light);
            float red=.06f+.67f*light+.3f*dusk,green=.09f+.69f*light+.08f*dusk,blue=.16f+.61f*light-.04f*dusk;
            fogColor.clear();fogColor.put(new float[]{red,green,blue,1}).flip();
            GL11.glClearColor(red,green,blue,1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_FOG); GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
            GL11.glFogf(GL11.GL_FOG_START, 65); GL11.glFogf(GL11.GL_FOG_END, 185);
            fogColor.rewind(); GL11.glFog(GL11.GL_FOG_COLOR, fogColor);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
            float fov=Math.max(60,Math.min(105,mc.gameSettings.fovSetting));
            GLU.gluPerspective((float)(fov+(52-fov)*aimBlend),
                    (float)mc.displayWidth / Math.max(1,mc.displayHeight), .12f, 900);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            GL11.glRotated(game.pitch,1,0,0);
            GL11.glRotated(game.yaw,0,1,0);
            GL11.glTranslated(-game.x, -game.eyeY(), -game.z);
            sky(game);
            if (!fog.hasFailed() && dev.vibe.ui.effect.FogRenderer.replacesVanillaFog()) GL11.glDisable(GL11.GL_FOG);
            if(cachedWorld!=game.world)compile(game.world);
            lighting(game);
            double viewDistance=190;
            double heading=Math.toRadians(game.yaw),tilt=Math.toRadians(game.pitch);viewSin=Math.sin(heading);viewCos=Math.cos(heading);pitchSin=Math.sin(tilt);pitchCos=Math.cos(tilt);
            viewTanY=Math.tan(Math.toRadians(fov+(52-fov)*aimBlend)/2);viewTanX=viewTanY*mc.displayWidth/Math.max(1,mc.displayHeight);
            for (int i = 0; i < game.world.chunks.size(); i++) {
                Gta7World.Chunk chunk = game.world.chunks.get(i);
                if(chunk.distanceSquared(game.x,game.eyeY(),game.z)>viewDistance*viewDistance||!inView(chunk,game,fov+(52-fov)*aimBlend))continue;
                if(lists!=0) {
                    if(!compiled[i]){GL11.glNewList(lists+i,GL11.GL_COMPILE);drawChunk(game.world,chunk,i);GL11.glEndList();compiled[i]=true;}
                    GL11.glCallList(lists+i);
                } else drawChunk(game.world,chunk,i);
            }
            emoji.draw(game);
            GL11.glDisable(GL11.GL_LIGHTING);buildingShadows(game);GL11.glEnable(GL11.GL_LIGHTING);
            for (Gta7World.Prop prop : game.world.animatedProps) if (Math.abs(prop.x-game.x)<95&&Math.abs(prop.z-game.z)<95) dynamicProp(prop,game.time);
            for (Gta7Game.Car car : game.cars) if(actorVisible(car.x,1,car.z,3.2,game,130))car(car,game.time);
            for (Gta7Game.Npc npc : game.npcs) if(actorVisible(npc.x,npc.y+1,npc.z,2.5,game,110))person(npc,npc.y);
            drops(game);
            GL11.glDisable(GL11.GL_LIGHTING);
            tracers(game.tracers);
            particles(game);
            windows(game);
            signs(game);
            lightGlows(game);
            policeEsp.beginLocalFrame();
            if (esp != null && esp.isEnabled()) for (Gta7Game.Npc npc : game.npcs) if (npc.cop && npc.health > 0) {
                Gta7Bounds b = npc.bounds();
                double ground=npc.y;
                policeEsp.captureLocalActor(new AxisAlignedBB(b.x0-.06,ground-.02,b.z0-.06,b.x1+.06,ground+1.92,b.z1+.06),
                        "Police",(float)npc.health,(float)npc.maxHealth,(float)Math.hypot(npc.x-game.x,npc.z-game.z),"Service pistol",esp);
            }
            fog.render(dev.vibe.ui.effect.FogRenderer.module(), .12f, 900, (float)game.yaw, (float)game.pitch);
            if (!game.dead) weapon(game,aim);
            if(supersample)target.present(destination,mc.displayWidth,mc.displayHeight);
        } finally {
            if(OpenGlHelper.framebufferSupported) {
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,destination);
                OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER,renderbuffer);
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
            GL11.glPopClientAttrib(); GL11.glPopAttrib();
            // Attribute restoration bypasses Minecraft's texture/tint caches. Reconcile them before returning.
            GlStateManager.bindTexture(0); GlStateManager.bindTexture(defaultTexture);
            GlStateManager.setActiveTexture(activeTexture);
            OpenGlHelper.setActiveTexture(activeTexture);
            if (OpenGlHelper.shadersSupported) OpenGlHelper.glUseProgram(program);
            GlStateManager.resetColor();
        }
    }

    public void overlay() { policeEsp.renderOverlay(); }

    private void compile(Gta7World world) {
        if(lists!=0)GL11.glDeleteLists(lists,listCount);
        if(shadowList!=0){GL11.glDeleteLists(shadowList,1);shadowList=0;}
        cachedWorld=world;listCount=world.chunks.size();lists=GL11.glGenLists(listCount);
        compiled=new boolean[listCount];
    }

    private void drawChunk(Gta7World world, Gta7World.Chunk chunk, int index) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        for(int material=0;material<Gta7Materials.COUNT;material++) {
            materials.bind(material);GL11.glBegin(GL11.GL_QUADS);
            for(Gta7World.Box box:chunk.boxes)if(Gta7Materials.material(box)==material)texturedBox(box.bounds,box.color,material);
            GL11.glEnd();
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        for (Gta7World.Prop prop : chunk.props) {
            Gta7Scenery.draw(prop);
            if("candy".equals(prop.type)) {
                ellipsoid(prop.x,prop.size,prop.z,1.3,1.3,.5,new int[]{0xEC7FAA,0x82DDC5,0xD9BD6B}[prop.variant]);
            } else if("sphere".equals(prop.type))ellipsoid(prop.x,prop.size,prop.z,2,2,2,0xB8985F);
            else if("tie".equals(prop.type)) {
                ellipsoid(prop.x,4,prop.z,2.1,2.1,2.1,0xA5AFB0);
                box(prop.x-6,3.4,prop.z-.5,prop.x+6,4.4,prop.z+.5,0x828F94);
                ellipsoid(prop.x,4,prop.z-1.85,1.05,1.05,.15,0x243C4A);
            } else if ("tree".equals(prop.type)) {
                shadow(prop.x+.65,prop.z-.55,2,1.4,.135);
                int green = prop.variant==0?0x527C56:prop.variant==1?0x648A5A:0x6C8C64;
                if(Gta7Regions.at(prop.x,prop.z)==Gta7Regions.Region.FOREST)green=prop.variant==0?0x5A888D:prop.variant==1?0x6F648E:0x557F73;
                ellipsoid(prop.x,prop.size-.3,prop.z,1.55,1.65,1.5,green);
                ellipsoid(prop.x-.95,prop.size-.8,prop.z+.3,1,1.05,1,green);
                ellipsoid(prop.x+.85,prop.size-.4,prop.z-.25,1,1.05,1,green);
                ellipsoid(prop.x+.2,prop.size+.75,prop.z+.15,1,1.05,1,green);
            } else if ("plant".equals(prop.type)) {
                ellipsoid(prop.x,.95,prop.z,.32,.55,.3,0x527F56);
            } else if ("tank".equals(prop.type)) {
                for (int dx : new int[]{-1,1}) for (int dz : new int[]{-1,1})
                    box(prop.x+dx*.6-.05,prop.size,prop.z+dz*.6-.05,prop.x+dx*.6+.05,prop.size+1,prop.z+dz*.6+.05,0x485854);
                GL11.glPushMatrix();GL11.glTranslated(prop.x,prop.size+1,prop.z);
                cylinder(.95,1.8,0x937B59);
                for (double ring : new double[]{.1,.9,1.7}) { GL11.glPushMatrix();GL11.glTranslated(0,ring,0);cylinder(.975,.08,0x546661);GL11.glPopMatrix(); }
                GL11.glPopMatrix();
            }
        }
    }

    private void sky(Gta7Game game) {
        GL11.glDisable(GL11.GL_FOG);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);
        GL11.glPushMatrix();GL11.glTranslated(game.x,game.eyeY(),game.z);
        GL11.glBegin(GL11.GL_QUADS);
        for(int latitude=-4;latitude<8;latitude++)for(int longitude=0;longitude<32;longitude++) {
            double p=latitude*Math.PI/16,q=(latitude+1)*Math.PI/16,a=longitude*Math.PI/16,b=(longitude+1)*Math.PI/16;
            skyVertex(p,a,game.daylight());skyVertex(p,b,game.daylight());skyVertex(q,b,game.daylight());skyVertex(q,a,game.daylight());
        }
        GL11.glEnd();
        // Luminous disc and a soft halo instead of a shaded solid sphere.
        double orbit=game.dayTime/600*Math.PI*2;
        GL11.glPushMatrix();GL11.glTranslated(-Math.cos(orbit)*102,Math.cos(orbit)*132,114);GL11.glRotated(-game.yaw,0,1,0);GL11.glRotated(-game.pitch,1,0,0);
        GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        radialDisc(18,.17f,0xFFE3AF);radialDisc(8,.35f,0xFFF0CC);radialDisc(3.8,1,0xFFF5D6);
        GL11.glDisable(GL11.GL_BLEND);GL11.glPopMatrix();
        if(game.daylight()<.6) {
            GL11.glPushMatrix();GL11.glTranslated(Math.cos(orbit)*102,-Math.cos(orbit)*132,-114);
            GL11.glRotated(-game.yaw,0,1,0);GL11.glRotated(-game.pitch,1,0,0);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            radialDisc(4,1,0xDBE8F4);GL11.glDisable(GL11.GL_BLEND);GL11.glPopMatrix();
            GL11.glPointSize(2);GL11.glBegin(GL11.GL_POINTS);
            for(int i=0;i<120;i++){double a=i*2.39996,p=.15+(i%31)/31.0*1.3;GL11.glColor3d(.7,.8,.95);sphereVertex(0,0,0,240,240,240,p,a);}
            GL11.glEnd();
        }
        for(int i=0;i<10;i++) {
            double angle=i*2.39996,drift=game.time*.0005, cx=Math.cos(angle+drift)*185,cz=Math.sin(angle+drift)*185;
            for(int puff=0;puff<4;puff++)cloud(cx+puff*7,49+(i%3)*11+Math.sin(puff)*2,cz,11-puff,3.3,6.5,game.daylight());
        }
        GL11.glPopMatrix();GL11.glDepthMask(true);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_FOG);
    }

    private static double twilight(double daylight){return Math.pow(Math.max(0,1-Math.abs(daylight-.4)*2.5),3);}
    private boolean inView(Gta7World.Chunk c,Gta7Game game,double unusedFov){
        return sphereInView((c.x0+c.x1)/2-game.x,(c.y0+c.y1)/2-game.eyeY(),(c.z0+c.z1)/2-game.z,c.renderRadius);
    }
    private boolean actorVisible(double x,double y,double z,double radius,Gta7Game game,double limit){
        double dx=x-game.x,dy=y-game.eyeY(),dz=z-game.z;
        return dx*dx+dy*dy+dz*dz<limit*limit&&sphereInView(dx,dy,dz,radius);
    }
    private boolean sphereInView(double dx,double dy,double dz,double radius){
        double depth=dx*viewSin*pitchCos-dy*pitchSin-dz*viewCos*pitchCos,right=dx*viewCos+dz*viewSin,up=dx*viewSin*pitchSin+dy*pitchCos-dz*viewCos*pitchSin;
        return depth+radius>0&&Math.abs(right)-depth*viewTanX<=radius*Math.sqrt(1+viewTanX*viewTanX)
                &&Math.abs(up)-depth*viewTanY<=radius*Math.sqrt(1+viewTanY*viewTanY);
    }
    private static void skyVertex(double latitude,double longitude,double light) {
        double mix=Math.max(0,Math.min(1,Math.sin(latitude)*1.7));
        double dusk=twilight(light)*(1-mix);
        GL11.glColor3d((.73-.33*mix)*light+.06*(1-light)+.3*dusk,(.78-.15*mix)*light+.09*(1-light)+.08*dusk,(.77+.01*mix)*light+.16*(1-light)-.04*dusk);
        sphereVertex(0,0,0,800,800,800,latitude,longitude);
    }
    private static void cloud(double x,double y,double z,double rx,double ry,double rz,double day) {
        GL11.glBegin(GL11.GL_QUADS);
        for(int j=0;j<8;j++)for(int i=0;i<16;i++)for(int corner=0;corner<4;corner++) {
            double p=-Math.PI/2+(j+(corner>1?1:0))*Math.PI/8,a=(i+(corner==1||corner==2?1:0))*Math.PI/8;
            double light=.92+.07*Math.sin(p);
            double dusk=twilight(day);
            GL11.glColor3d(light*(.1+.9*day)+.24*dusk,light*(.14+.846*day)+.1*dusk,light*(.22+.723*day)+.06*dusk);sphereVertex(x,y,z,rx,ry,rz,p,a);
        }
        GL11.glEnd();
    }

    private void buildingShadows(Gta7Game game) {
        if(shadowList!=0){GL11.glCallList(shadowList);return;}
        shadowList=GL11.glGenLists(1);
        if(shadowList!=0)GL11.glNewList(shadowList,GL11.GL_COMPILE_AND_EXECUTE);
        GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glPolygonOffset(-1,-1);
        for(Gta7World.Building b:game.world.buildings) {
            double dx=b.height*.55,dz=-b.height*.6;
            java.util.List<double[]> polygon=java.util.Arrays.asList(new double[]{b.x,b.z},new double[]{b.x+dx,b.z+dz},
                    new double[]{b.x+b.width+dx,b.z+dz},new double[]{b.x+b.width+dx,b.z+b.depth+dz},
                    new double[]{b.x+b.width,b.z+b.depth},new double[]{b.x,b.z+b.depth});
            shadowPolygon(polygon,.002);
            for(int bx=0;bx<Gta7World.BLOCKS;bx++)for(int bz=0;bz<Gta7World.BLOCKS;bz++) {
                java.util.List<double[]> clipped=polygon;
                clipped=clipGround(clipped,0,bx*36+4.5,true);clipped=clipGround(clipped,0,bx*36+31.5,false);
                clipped=clipGround(clipped,1,bz*36+4.5,true);clipped=clipGround(clipped,1,bz*36+31.5,false);
                shadowPolygon(clipped,.082);
            }
        }
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glDepthMask(true);GL11.glDisable(GL11.GL_BLEND);
        if(shadowList!=0)GL11.glEndList();
    }
    private static java.util.List<double[]> clipGround(java.util.List<double[]> polygon,int axis,double edge,boolean above) {
        java.util.List<double[]> result=new java.util.ArrayList<double[]>();
        if(polygon.isEmpty())return result;
        double[] previous=polygon.get(polygon.size()-1);
        for(double[] point:polygon) {
            boolean in=above?point[axis]>=edge:point[axis]<=edge,wasIn=above?previous[axis]>=edge:previous[axis]<=edge;
            if(in!=wasIn){double t=(edge-previous[axis])/(point[axis]-previous[axis]);result.add(new double[]{previous[0]+(point[0]-previous[0])*t,previous[1]+(point[1]-previous[1])*t});}
            if(in)result.add(point);previous=point;
        }
        return result;
    }
    private static void shadowPolygon(java.util.List<double[]> polygon,double y) {
        if(polygon.size()<3)return;GL11.glColor4f(.12f,.19f,.29f,.20f);GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        for(double[] point:polygon)GL11.glVertex3d(point[0],y,point[1]);GL11.glEnd();
    }

    private void dynamicProp(Gta7World.Prop prop,double time) {
        if("windmill".equals(prop.type)){
            GL11.glPushMatrix();GL11.glTranslated(prop.x,prop.size,prop.z-.55);GL11.glRotated(time*22,0,0,1);
            for(int i=0;i<8;i++){GL11.glRotated(45,0,0,1);box(-.09,.3,-.03,.09,3.1,.03,0x74858A);box(-.14,1.75,-.035,.5,3.1,.035,0xBAC4BD);}
            GL11.glPopMatrix();
        } else if("wisp".equals(prop.type)) {
            double bob=Math.sin(time*1.5+prop.variant)*.3;
            ellipsoid(prop.x+Math.cos(time+prop.variant)*.15,prop.size+bob,prop.z,.13,.18,.13,0xBBF7E3);
        } else if ("signal".equals(prop.type)) {
            box(prop.x-.16,2.7,prop.z-.17,prop.x+.16,3.55,prop.z+.17,0x273B40);
            boolean red=((int)(time/7)+(int)prop.size)%2==0;
            box(prop.x-.1,3.29,prop.z-.185,prop.x+.1,3.47,prop.z-.175,red?0xFA785D:0x653F38);
            box(prop.x-.1,2.79,prop.z-.185,prop.x+.1,2.97,prop.z-.175,red?0x3E5748:0x9CDD8B);
        } else if ("fountain".equals(prop.type)) {
            for(int i=0;i<16;i++) {
                double angle=i*Math.PI/8, phase=(time*.7+i*.12)%1;
                double r=phase*1.8, yy=1.7+Math.sin(phase*Math.PI)*1.2-phase;
                ellipsoid(prop.x+Math.cos(angle)*r,yy,prop.z+Math.sin(angle)*r,.055,.12,.055,0xB7DFDC);
            }
        }
    }

    private void signs(Gta7Game game) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_POLYGON_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glPolygonOffset(-1,-1);GL11.glDepthMask(false);
        GlStateManager.bindTexture(0);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        for(Gta7World.Landmark sign:game.world.landmarks) {
            if(Math.hypot(sign.x-game.x,sign.z-game.z)>60||Math.abs(sign.y-game.eyeY())>20)continue;
            GL11.glPushMatrix();GL11.glTranslated(sign.x,sign.y,sign.z);
            // Positive local X must point to camera-right on the street-facing (-Z) side.
            GL11.glRotated(game.z<sign.z?180:0,0,1,0);GL11.glScaled(sign.scale,-sign.scale,sign.scale);
            String label=dev.vibe.language.LanguageManager.translate(sign.name);GlStateManager.resetColor();mc.fontRendererObj.drawString(label,-mc.fontRendererObj.getStringWidth(label)/2,0,0xE4DFC6);
            GL11.glPopMatrix();
        }
        GL11.glPopAttrib();GlStateManager.resetColor();
    }
    private FloatBuffer vector(float x,float y,float z,float w){lightBuffer.clear();lightBuffer.put(x).put(y).put(z).put(w).flip();return lightBuffer;}
    private void lighting(Gta7Game game) {
        float day=(float)game.daylight(),dusk=(float)Math.pow(Math.max(0,1-Math.abs(day-.45)*2.5),3);
        GL11.glEnable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_NORMALIZE);GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK,GL11.GL_AMBIENT_AND_DIFFUSE);
        GL11.glMaterial(GL11.GL_FRONT_AND_BACK,GL11.GL_SPECULAR,vector(0,0,0,1));
        GL11.glMaterial(GL11.GL_FRONT_AND_BACK,GL11.GL_EMISSION,vector(0,0,0,1));
        GL11.glMaterialf(GL11.GL_FRONT_AND_BACK,GL11.GL_SHININESS,0);
        GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT,vector(0,0,0,1));
        GL11.glLightModeli(GL11.GL_LIGHT_MODEL_TWO_SIDE,GL11.GL_FALSE);
        GL11.glLightModeli(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER,GL11.GL_FALSE);
        if(org.lwjgl.opengl.GLContext.getCapabilities().OpenGL12)GL11.glLightModeli(org.lwjgl.opengl.GL12.GL_LIGHT_MODEL_COLOR_CONTROL,org.lwjgl.opengl.GL12.GL_SINGLE_COLOR);
        for(int i=0;i<8;i++){
            int id=GL11.GL_LIGHT0+i;GL11.glDisable(id);
            GL11.glLight(id,GL11.GL_SPECULAR,vector(0,0,0,1));GL11.glLight(id,GL11.GL_AMBIENT,vector(0,0,0,1));
            GL11.glLightf(id,GL11.GL_SPOT_CUTOFF,180);GL11.glLightf(id,GL11.GL_SPOT_EXPONENT,0);
            GL11.glLightf(id,GL11.GL_CONSTANT_ATTENUATION,1);GL11.glLightf(id,GL11.GL_LINEAR_ATTENUATION,0);GL11.glLightf(id,GL11.GL_QUADRATIC_ATTENUATION,0);
        }
        GL11.glEnable(GL11.GL_LIGHT0);
        double orbit=game.dayTime/600*Math.PI*2;
        GL11.glLight(GL11.GL_LIGHT0,GL11.GL_POSITION,vector((float)-Math.sin(orbit+.5),.7f+.4f*day,.5f,0));
        GL11.glLight(GL11.GL_LIGHT0,GL11.GL_AMBIENT,vector(.22f+.41f*day,.27f+.37f*day,.37f+.26f*day,1));
        GL11.glLight(GL11.GL_LIGHT0,GL11.GL_DIFFUSE,vector(.13f+.34f*day+.2f*dusk,.16f+.27f*day,.24f+.12f*day,1));
        // Three local lights illuminate geometry as well as their visible source and ground pool.
        Gta7World.Light[] nearest=new Gta7World.Light[3];double[] distance={180,180,180};
        for(Gta7World.Light lamp:game.world.lights){
            double d=Math.pow(lamp.x-game.x,2)+Math.pow(lamp.y-game.eyeY(),2)+Math.pow(lamp.z-game.z,2);
            for(int i=0;i<3;i++)if(d<distance[i]){for(int j=2;j>i;j--){distance[j]=distance[j-1];nearest[j]=nearest[j-1];}distance[i]=d;nearest[i]=lamp;break;}
        }
        for(int i=0;i<3;i++)if(nearest[i]!=null){
            Gta7World.Light lamp=nearest[i];int id=GL11.GL_LIGHT1+i,rgb=lamp.color;float intensity=.3f+.7f*(1-day);
            GL11.glEnable(id);GL11.glLight(id,GL11.GL_POSITION,vector((float)lamp.x,(float)lamp.y,(float)lamp.z,1));
            GL11.glLight(id,GL11.GL_DIFFUSE,vector((rgb>>16&255)/255f*intensity,(rgb>>8&255)/255f*intensity,(rgb&255)/255f*intensity,1));
            GL11.glLightf(id,GL11.GL_QUADRATIC_ATTENUATION,(float)(1.5/(lamp.radius*lamp.radius)));
        }
    }
    private void lightGlows(Gta7Game game){
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_FOG);
        GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);GL11.glDepthMask(false);
        float night=(float)(1-game.daylight());
        for(Gta7World.Light lamp:game.world.lights){
            double dx=lamp.x-game.x,dy=lamp.y-game.eyeY(),dz=lamp.z-game.z;
            if(dx*dx+dy*dy+dz*dz>65*65)continue;
            GL11.glPushMatrix();GL11.glTranslated(lamp.x,lamp.y,lamp.z);
            GL11.glRotated(-game.yaw,0,1,0);GL11.glRotated(-game.pitch,1,0,0);
            radialDisc(.13,.55f,lamp.color);radialDisc(.65,.035f+.12f*night,lamp.color);GL11.glPopMatrix();
            if(night>.05){
                double floor=game.world.support(lamp.x,lamp.z,lamp.y-.3,.02);
                GL11.glPushMatrix();GL11.glTranslated(lamp.x,floor+.015,lamp.z);GL11.glRotated(-90,1,0,0);
                radialDisc(lamp.radius,.12f*night,lamp.color);GL11.glPopMatrix();
            }
        }
        for(Gta7World.Prop p:game.world.glowingProps){
            if(Math.abs(p.x-game.x)>60||Math.abs(p.z-game.z)>60)continue;
            if(p.type.equals("firefly")||p.type.equals("wisp")){
                GL11.glPushMatrix();GL11.glTranslated(p.x+Math.sin(game.time*.6+p.variant)*.6,p.size+Math.sin(game.time*1.4+p.variant)*.25,p.z+Math.cos(game.time*.8+p.variant)*.6);
                GL11.glRotated(-game.yaw,0,1,0);GL11.glRotated(-game.pitch,1,0,0);
                radialDisc(.065,.85f,0xC2FFE4);radialDisc(.5,.15f,0x7ADFC7);GL11.glPopMatrix();
            }else if(p.type.equals("campfire")){
                for(int i=0;i<5;i++){
                    double phase=(game.time*1.7+i*.2)%1;
                    GL11.glPushMatrix();GL11.glTranslated(p.x+Math.sin(i*2.4)*phase*.15,.3+phase*.85,p.z+Math.cos(i*2.4)*phase*.15);
                    GL11.glRotated(-game.yaw,0,1,0);GL11.glRotated(-game.pitch,1,0,0);GL11.glScaled(1,1.7,1);
                    radialDisc(.36*(1-phase),.65f,i%2==0?0xFFB34F:0xFF763D);GL11.glPopMatrix();
                }
            }
        }
        GL11.glPopAttrib();
    }
    private void particles(Gta7Game game){
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glDepthMask(false);
        for(Gta7Game.Particle p:game.particles){
            GL11.glPushMatrix();GL11.glTranslated(p.x,p.y,p.z);GL11.glRotated(p.life*190+p.seed,1,1,0);
            GL11.glColor4d((p.color>>16&255)/255.0,(p.color>>8&255)/255.0,(p.color&255)/255.0,Math.min(.85,p.life*2));
            GL11.glBegin(GL11.GL_TRIANGLES);tri(-p.size,0,0,p.size,0,0,0,p.size*2,0);GL11.glEnd();GL11.glPopMatrix();
        }
        GL11.glPopAttrib();
    }
    private void windows(Gta7Game game) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDepthMask(false);
        java.util.List<Gta7World.Window> visible=visibleWindows;visible.clear();
        for(Gta7World.Window window:game.world.windows)if(!window.broken){
            Gta7Bounds b=window.bounds;double limit=(b.x1-b.x0>35||b.z1-b.z0>35)?700:110;
            if(glassDistance(window,game)<limit*limit)visible.add(window);
        }
        visible.sort((a,b)->Double.compare(glassDistance(b,game),glassDistance(a,game)));
        GL11.glBegin(GL11.GL_QUADS);
        for(Gta7World.Window window:visible) {
            Gta7Bounds b=window.bounds;GL11.glColor4f(.53f,.80f,.87f,glassDistance(window,game)>120*120?.48f:.22f);
            if(b.x1-b.x0<b.z1-b.z0)quad(b.x0,b.y0,b.z0,b.x0,b.y0,b.z1,b.x0,b.y1,b.z1,b.x0,b.y1,b.z0);
            else quad(b.x0,b.y0,b.z0,b.x1,b.y0,b.z0,b.x1,b.y1,b.z0,b.x0,b.y1,b.z0);
            GL11.glColor4f(.83f,.95f,.94f,.12f);
            double bottom=b.y0+(b.y1-b.y0)*.35,top=b.y0+(b.y1-b.y0)*.55;
            if(b.x1-b.x0<b.z1-b.z0)quad(b.x0,bottom,b.z0,b.x0,bottom+.2,b.z1,b.x0,top+.2,b.z1,b.x0,top,b.z0);
            else quad(b.x0,bottom,b.z0,b.x1,bottom+.2,b.z0,b.x1,top+.2,b.z0,b.x0,top,b.z0);
        }
        GL11.glEnd();GL11.glPopAttrib();
    }
    private static double glassDistance(Gta7World.Window w,Gta7Game game){Gta7Bounds b=w.bounds;return Math.pow(b.x0-game.x,2)+Math.pow(b.y0-game.y,2)+Math.pow(b.z0-game.z,2);}

    private void person(Gta7Game.Npc npc,double ground) {
        shadow(npc.x+.1,npc.z-.08,.42,.3,ground+.003);
        GL11.glPushMatrix(); GL11.glTranslated(npc.x,ground,npc.z); GL11.glRotated(-npc.yaw,0,1,0);
        if(npc.health<=0){GL11.glTranslated(0,.05,0);GL11.glRotated(Math.min(90,npc.death*240),1,0,0);}
        if(npc.hurt>0&&npc.health>0)GL11.glRotated(Math.sin(npc.hurt*40)*5,0,0,1);
        if(npc.kind==Gta7Game.Kind.PENGUIN||npc.kind==Gta7Game.Kind.POLAR_BEAR){animal(npc);GL11.glPopMatrix();return;}
        double scale=npc.kind.height/1.8;GL11.glScaled(scale,scale,scale);
        int shirt=npc.kind.color==0?SHIRTS[npc.variant]:npc.kind.color;
        int skin=npc.kind==Gta7Game.Kind.GOBLIN?(npc.variant%2==0?0x7C9E4A:0x638D47):SKIN[npc.variant%4];
        double swing=npc.health<=0?0:Math.sin(npc.walk)*24;
        limb(-.14,.8,0,swing,.19,.8,.22,npc.cop?0x263B51:0x465359,true);
        limb(.14,.8,0,-swing,.19,.8,.22,npc.cop?0x263B51:0x465359,true);
        if(!(npc.cop||npc.kind.aggressive&&!npc.kind.melee||npc.kind==Gta7Game.Kind.COUNTER_TERRORIST)){
            arm(-.32,-swing,shirt,skin);arm(.32,swing,shirt,skin);
        }
        if(actorLists==0)actorLists=GL11.glGenLists(actorBuilt.length);
        int index=npc.kind.ordinal()*8+npc.variant;
        if(actorLists!=0){
            if(!actorBuilt[index]){GL11.glNewList(actorLists+index,GL11.GL_COMPILE);personBody(npc,shirt,skin);GL11.glEndList();actorBuilt[index]=true;}
            GL11.glCallList(actorLists+index);
        }else personBody(npc,shirt,skin);
        GL11.glPopMatrix();
    }
    private void personBody(Gta7Game.Npc npc,int shirt,int skin){
        box(-.24,.79,-.15,.24,1.42,.15,shirt);
        box(-.23,.79,-.17,.23,.88,.17,0x2B363A);
        box(-.07,.82,-.186,.07,.88,-.17,0xB7AB85);
        box(-.11,1.41,-.1,.11,1.5,.1,skin);
        if(npc.kind==Gta7Game.Kind.GOBLIN){
            ellipsoid(0,1.64,-.04,.29,.25,.24,skin);
            box(-.075,1.51,-.39,.075,1.68,-.22,skin);
            GL11.glBegin(GL11.GL_TRIANGLES);color(skin,1);GL11.glNormal3d(0,0,-1);
            for(int side:new int[]{-1,1}){tri(side*.2,1.56,.02,side*.65,1.88,.01,side*.24,1.79,-.09);}
            color(0xD1BC6E,1);tri(-.19,1.68,-.267,-.06,1.66,-.277,-.18,1.60,-.272);tri(.19,1.68,-.267,.18,1.60,-.272,.06,1.66,-.277);
            color(0xE6DCC0,1);tri(-.15,1.47,-.245,-.1,1.59,-.26,-.07,1.47,-.256);tri(.15,1.47,-.245,.07,1.47,-.256,.1,1.59,-.26);GL11.glEnd();
        }else{
            box(-.18,1.47,-.17,.18,1.8,.16,skin);
            box(-.19,1.75,-.175,.19,1.86,.17,npc.cop?0x273E58:0x514B42);
        }
        if(npc.cop||npc.kind.aggressive&&!npc.kind.melee||npc.kind==Gta7Game.Kind.COUNTER_TERRORIST){
            if(npc.cop) {
            box(-.21,1.76,-.25,.21,1.8,.16,0x273E58);
            box(-.07,1.79,-.18,.07,1.84,-.176,0xD4BD72);
            box(-.14,1.25,-.17,-.05,1.35,-.155,0xD8BF74);
            box(.12,1.1,-.18,.22,1.31,-.155,0x182F40);
            }
            limb(-.32,1.36,-.02,65,.16,.5,.18,shirt,false);
            limb(.32,1.36,-.02,75,.16,.5,.18,shirt,false);
            box(.15,1.11,-.51,.3,1.26,-.37,skin);
            if(npc.kind==Gta7Game.Kind.CANDY_KID||npc.kind==Gta7Game.Kind.TERRORIST||npc.kind==Gta7Game.Kind.OUTLAW) {
                box(.18,1.2,-.88,.3,1.34,-.36,0x34434A);
                box(.17,1.2,-1.05,.31,1.32,-.81,0x9D6E47);
                box(.21,1.24,-1.32,.27,1.3,-1,0x263A43);
                box(.19,1.21,-.35,.29,1.33,-.12,0x9D6E47);
                for(int i=0;i<4;i++)box(.2,1.15-i*.06,-.63+i*.025,.28,1.24-i*.06,-.5+i*.025,0x34434A);
            } else if(npc.kind==Gta7Game.Kind.STORMTROOPER||npc.kind==Gta7Game.Kind.IMPERIAL_DROID) {
                box(.19,1.22,-1.12,.29,1.35,-.38,0x26333B);box(.21,1.35,-.74,.27,1.42,-.52,0x3E4E55);
            } else box(.19,1.22,-.76,.29,1.35,-.38,0x29373C);
        }
        if(npc.kind==Gta7Game.Kind.COWBOY||npc.kind==Gta7Game.Kind.SHERIFF||npc.kind==Gta7Game.Kind.OUTLAW) {
            box(-.32,1.78,-.3,.32,1.85,.3,0x6F543C);box(-.17,1.85,-.17,.17,2.03,.17,0x806348);
        }
        if(npc.kind==Gta7Game.Kind.INUIT||npc.kind==Gta7Game.Kind.ARCTIC_COP) {
            box(-.23,1.45,-.19,-.15,1.83,.19,0xE3D7BE);box(.15,1.45,-.19,.23,1.83,.19,0xE3D7BE);
            box(-.23,1.81,-.19,.23,1.92,.19,0xE3D7BE);box(-.24,.88,-.17,.24,.97,.17,0xE3D7BE);
        }
        if(npc.kind==Gta7Game.Kind.WIZARD||npc.kind==Gta7Game.Kind.WIZARD_GUARD) {
            box(-.32,1.8,-.3,.32,1.87,.3,shirt);
            for(int i=0;i<5;i++){double r=.2-i*.035;box(-r,1.87+i*.12,-r,r,1.99+i*.12,r,shirt);}
            box(.36,.15,-.2,.42,1.8,-.14,0x876B4F);ellipsoid(.39,1.9,-.17,.12,.18,.12,0xA7E3D4);
        }
        if(npc.kind==Gta7Game.Kind.STORMTROOPER||npc.kind==Gta7Game.Kind.COUNTER_TERRORIST||npc.kind==Gta7Game.Kind.VADER||npc.kind==Gta7Game.Kind.IMPERIAL_DROID) {
            box(-.2,1.45,-.2,.2,1.87,.19,shirt);box(-.17,1.65,-.21,.17,1.72,-.2,0x172C36);
            box(-.17,1.5,-.22,.17,1.56,-.21,0x465158);
        }
        if(npc.kind.melee) {
            box(.26,.74,-.2,.33,1,-.12,0x4A5253);
            box(.27,.98,-.2,.32,1.45,-.12,npc.kind==Gta7Game.Kind.VADER?0xEE4352:0xCCDCD9);
        }
        if(npc.kind==Gta7Game.Kind.VISITOR){box(-.035,1.03,-.17,.035,1.41,-.156,0xD4BA91);}
        if(npc.kind==Gta7Game.Kind.GOBLIN){box(-.15,1.61,-.281,-.1,1.67,-.266,0x202C1E);box(.1,1.61,-.281,.15,1.67,-.266,0x202C1E);return;}
        box(-.12,1.64,-.183,-.055,1.68,-.171,0x333A39);
        box(.055,1.64,-.183,.12,1.68,-.171,0x333A39);
        box(-.035,1.54,-.194,.07,1.57,-.182,0x886451);
    }
    private void animal(Gta7Game.Npc npc) {
        if(npc.kind==Gta7Game.Kind.PENGUIN) {
            ellipsoid(0,.4,0,.25,.38,.23,0x263A44);ellipsoid(0,.4,-.18,.17,.27,.09,0xEBEBDD);
            ellipsoid(0,.74,0,.18,.17,.17,0x263A44);box(-.07,.69,-.31,.07,.75,-.13,0xDAAC67);
            for(int side:new int[]{-1,1}){box(side*.13-.09,.01,-.21,side*.13+.09,.08,.06,0xDAAC67);ellipsoid(side*.25,.42,0,.075,.24,.1,0x263A44);}
        } else {
            ellipsoid(0,.78,0,.6,.48,.82,0xE0E5DA);ellipsoid(0,1.06,-.73,.4,.36,.38,0xEEF0E4);
            box(-.2,.88,-1.18,.2,1.09,-.86,0xD8DDD4);box(-.1,1,-1.2,.1,1.09,-1.17,0x35434B);
            for(int side:new int[]{-1,1})for(int end:new int[]{-1,1})limb(side*.4,.65,end*.48,Math.sin(npc.walk+side+end)*15,.23,.62,.25,0xE3E8DD,false);
        }
    }
    private void arm(double x,double swing,int shirt,int skin) {
        GL11.glPushMatrix();GL11.glTranslated(x,1.36,0);GL11.glRotated(swing,1,0,0);
        box(-.085,-.45,-.1,.085,0,.1,shirt);box(-.08,-.61,-.09,.08,-.45,.09,skin);GL11.glPopMatrix();
    }
    private void limb(double x,double y,double z,double angle,double w,double h,double d,int color,boolean shoe) {
        GL11.glPushMatrix();GL11.glTranslated(x,y,z);GL11.glRotated(angle,1,0,0);
        box(-w/2,-h,-d/2,w/2,0,d/2,color);
        if(shoe)box(-w/2-.01,-h,-d/2-.08,w/2+.01,-h+.12,d/2,0x293739);
        GL11.glPopMatrix();
    }

    private void car(Gta7Game.Car car,double time) {
        boolean horizontal=Math.round(car.yaw)%180!=0;
        shadow(car.x,car.z,horizontal?2.15:1.04,horizontal?1.04:2.15,.004);
        GL11.glPushMatrix(); GL11.glTranslated(car.x,0,car.z);GL11.glRotated(-car.yaw,0,1,0);
        if(carLists==0)carLists=GL11.glGenLists(CAR_COUNT);
        if(carLists!=0){
            if(!carBuilt[car.color]){GL11.glNewList(carLists+car.color,GL11.GL_COMPILE);carBody(car.color);GL11.glEndList();carBuilt[car.color]=true;}
            GL11.glCallList(carLists+car.color);
        }else carBody(car.color);
        for(int side=-1;side<=1;side+=2){wheel(side*.88,.4,-1.22,time*car.speed*1.6);wheel(side*.88,.4,1.23,time*car.speed*1.6);}
        box(-.79,.67,1.978,-.47,.85,1.99,car.speed<1?0xF28364:0x9E554B);
        box(.47,.67,1.978,.79,.85,1.99,car.speed<1?0xF28364:0x9E554B);
        GL11.glPopMatrix();
    }
    private void carBody(int paintIndex){
        int paint=CAR_COLORS[paintIndex];
        box(-.87,.39,-1.97,.87,.93,1.97,paint);
        box(-.84,.88,-1.94,.84,1,-.8,paint);
        box(-.72,.94,-.72,.72,1.52,.9,paint);
        box(-.66,1.01,-.745,.66,1.43,-.73,0x7399A1);
        box(-.66,1.02,.91,.66,1.41,.93,0x597E87);
        for(int side:new int[]{-1,1}) {
            double xx=side*.738;
            box(xx-.008,1.02,-.58,xx+.008,1.43,.08,0x688F96);
            box(xx-.008,1.02,.19,xx+.008,1.43,.79,0x73999D);
            box(side*.88-.05,.81,-.58,side*.88+.05,.94,-.32,paint);
            box(side*.881-.012,.83,.38,side*.881+.012,.87,.57,0xCAD2C5);
        }
        box(-.7,.67,-1.99,-.34,.83,-1.975,0xFFF0C6);box(.34,.67,-1.99,.7,.83,-1.975,0xFFF0C6);
        box(-.3,.54,-2.005,.3,.73,-1.99,0x273A40);
        for(int i=0;i<4;i++)box(-.27,.55+i*.042,-2.014,.27,.565+i*.042,-2.006,0x839696);
        box(-.26,.48,1.995,.26,.62,2.006,0xDAD9BB);
        box(-.92,.43,-2.04,.92,.53,-1.99,0x7B8B8A);box(-.92,.43,1.99,.92,.53,2.04,0x7B8B8A);
    }
    private void wheel(double x,double y,double z,double roll) {
        GL11.glPushMatrix();GL11.glTranslated(x,y,z);GL11.glRotated(x<0?90:-90,0,0,1);GL11.glRotated(roll*60,0,1,0);
        if(wheelList==0){wheelList=GL11.glGenLists(1);if(wheelList!=0){GL11.glNewList(wheelList,GL11.GL_COMPILE);wheelGeometry();GL11.glEndList();}}
        if(wheelList!=0)GL11.glCallList(wheelList);else wheelGeometry();
        GL11.glPopMatrix();
    }
    private void wheelGeometry(){cylinder(.32,.2,0x243237);GL11.glTranslated(0,.205,0);cylinder(.17,.014,0x8D9C99);}

    private void drops(Gta7Game game) {
        for(Gta7Game.Drop drop:game.drops){
            if(!actorVisible(drop.x,drop.y+.4,drop.z,.5,game,90))continue;
            shadow(drop.x,drop.z,.23,.23,game.world.groundHeight(drop.x,drop.z)+.003);
            GL11.glPushMatrix();GL11.glTranslated(drop.x,drop.y+.38+Math.sin(game.time*3+drop.x)*.12,drop.z);GL11.glRotated(game.time*95,0,1,0);
            GL11.glRotated(45,0,0,1);box(-.12,-.12,-.12,.12,.12,.12,0x96E9A0);GL11.glPopMatrix();
        }
    }
    private void tracers(List<Gta7Game.Tracer> tracers) {
        boolean fogEnabled=GL11.glIsEnabled(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_FOG);GL11.glLineWidth(1.6f);
        GL11.glBegin(GL11.GL_LINES);
        for(Gta7Game.Tracer t:tracers){color(t.police?0xF3A57E:0xFFEDAF,1);GL11.glVertex3d(t.x,t.y,t.z);GL11.glVertex3d(t.xx,t.yy,t.zz);}
        GL11.glEnd();if(fogEnabled)GL11.glEnable(GL11.GL_FOG);
    }

    private void weapon(Gta7Game game,boolean aim) {
        GL11.glDisable(GL11.GL_FOG); GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(true);GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();
        GLU.gluPerspective(65,(float)mc.displayWidth/Math.max(1,mc.displayHeight),.045f,8);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
        double bob=game.speed>0?Math.sin(game.walkCycle)*.012:Math.sin(game.time*1.5)*.003;
        double hipX=.26,hipY=-.255;
        GL11.glTranslated(hipX*(1-aimBlend)+bob*(1-aimBlend),hipY+(.127)*aimBlend+Math.abs(bob)*(1-aimBlend)-equipDip,-.64);
        GL11.glScaled(.72,.72,.72);GL11.glRotated(1.253*aimBlend,1,0,0);
        if(game.reload>0){GL11.glTranslated(0,-.12,0);GL11.glRotated(24*Math.sin(game.reload/2.1*Math.PI),0,0,1);}
        Gta7Progress.Skin skin=game.progress.skin(game.weapon);
        if(game.weapon==0){
            GL11.glRotated(22,0,1,0);GL11.glRotated(18,1,0,0);
            GL11.glTranslated(-game.recoil*.17,-game.recoil*.07,-game.recoil*.28);GL11.glRotated(-game.recoil*65,0,0,1);
            box(-.07,-.28,.09,.08,-.035,.35,0x344E55);
            box(-.065,-.08,-.07,.075,.03,.12,0xCDA682);
            box(-.033,-.025,-.21,.033,.025,.08,0x343D3F);
            for(int i=0;i<6;i++)box(-.036,-.028,-.18+i*.039,.036,.028,-.163+i*.039,0x59615C);
            box(-.095,-.03,-.24,.095,.032,-.205,0x8EAAA9);
            // Solid tapered blade with bevels and a real point in perspective.
            GL11.glBegin(GL11.GL_TRIANGLES);
            color(skin.secondary,1);tri(-.056,0,-.235, .056,0,-.235, -.008,0,-.78);
            color(skin==Gta7Progress.Skin.STOCK?0x7D989D:skin.primary,1);tri(-.056,0,-.235, 0,.025,-.3, -.008,0,-.78);
            color(0xAFCCC9,1);tri(.056,0,-.235, -.008,0,-.78, 0,.025,-.3);
            GL11.glEnd();
        }else{
            GL11.glTranslated(0,-game.recoil*.025,game.recoil*.08);
            GL11.glRotated(game.recoil*3,1,0,0);
            // Right trigger hand, left support hand, receiver, wood furniture and curved magazine.
            box(-.015,-.32,.07,.12,-.12,.39,0x355158);
            box(-.06,-.16,-.035,.075,-.035,.12,0xCAA380);
            box(-.2,-.28,-.39,-.08,-.16,.09,0x355158);
            box(-.17,-.12,-.5,-.035,-.02,-.32,0xCAA380);
            box(-.06,-.055,-.53,.06,.085,.05,0x35444A);
            box(-.052,.085,-.49,.052,.11,-.02,0x657175);
            box(-.063,-.015,.05,.063,.1,.29,skin.primary);
            box(-.04,-.17,-.025,.045,-.04,.06,skin.primary);
            box(-.067,-.06,-.76,.067,.05,-.5,skin.primary);
            for(int i=0;i<4;i++)box(-.07,-.061,-.71+i*.049,.07,-.052,-.697+i*.049,0x644F3D);
            box(-.025,.019,-1.1,.025,.066,-.75,0x32464C);
            box(-.018,.075,-.91,.018,.101,-.66,0x526469);
            box(-.032,.035,-1.13,.032,.078,-1.085,0x637578);
            box(-.012,.073,-1.07,.012,.155,-1.04,0x30494F);
            box(-.04,.11,-.1,.04,.155,-.064,0x293F45);
            box(-.05,.15,-.098,-.024,.176,-.062,0x6E8485);
            box(.024,.15,-.098,.05,.176,-.062,0x6E8485);
            box(.06,.025,-.24,.095,.055,-.14,0x708384);
            for(int i=0;i<5;i++){
                double zz=-.31+i*.024, yy=-.055-i*.048;
                box(-.046,yy-.055,zz-.09,.046,yy,zz+.055,0x394D51);
                box(-.048,yy-.035,zz-.067,-.043,yy-.022,zz+.035,0x73817D);
            }
            if(game.flash>0){
                GL11.glPushMatrix();GL11.glTranslated(0,.055,-1.15);
                GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
                GL11.glDepthMask(false);radialDisc(.11,.65f,0xFFC576);radialDisc(.035,.95f,0xFFF4D1);
                GL11.glDepthMask(true);GL11.glDisable(GL11.GL_BLEND);GL11.glPopMatrix();
            }
        }
    }

    private static void tri(double a,double b,double c,double d,double e,double f,double g,double h,double i){GL11.glVertex3d(a,b,c);GL11.glVertex3d(d,e,f);GL11.glVertex3d(g,h,i);}
    private static void shadow(double x,double z,double rx,double rz,double y){
        GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glDepthMask(false);
        GL11.glColor4f(.08f,.15f,.2f,.28f);GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glVertex3d(x,y,z);
        GL11.glColor4f(.08f,.15f,.2f,0);
        for(int i=0;i<=24;i++){double a=i*Math.PI/12;GL11.glVertex3d(x+Math.cos(a)*rx,y,z+Math.sin(a)*rz);}GL11.glEnd();
        GL11.glDepthMask(true);GL11.glDisable(GL11.GL_BLEND);
    }
    private static void radialDisc(double radius,float alpha,int rgb) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,alpha);GL11.glVertex3d(0,0,0);
        GL11.glColor4f((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f,0);
        for(int i=0;i<=48;i++){double a=i*Math.PI/24;GL11.glVertex3d(Math.cos(a)*radius,Math.sin(a)*radius,0);}
        GL11.glEnd();
    }
    static void cylinder(double r,double h,int rgb){
        GL11.glBegin(GL11.GL_QUADS);
        for(int i=0;i<12;i++){double a=i*Math.PI/6,b=(i+1)*Math.PI/6;color(rgb,.85+.1*Math.cos(a));GL11.glNormal3d(Math.cos((a+b)/2),0,Math.sin((a+b)/2));
            GL11.glVertex3d(Math.cos(a)*r,0,Math.sin(a)*r);GL11.glVertex3d(Math.cos(b)*r,0,Math.sin(b)*r);
            GL11.glVertex3d(Math.cos(b)*r,h,Math.sin(b)*r);GL11.glVertex3d(Math.cos(a)*r,h,Math.sin(a)*r);}
        GL11.glEnd();GL11.glBegin(GL11.GL_TRIANGLE_FAN);color(rgb,1);GL11.glNormal3d(0,1,0);GL11.glVertex3d(0,h,0);
        for(int i=0;i<=12;i++){double a=i*Math.PI/6;GL11.glVertex3d(Math.cos(a)*r,h,Math.sin(a)*r);}GL11.glEnd();
    }
    static void ellipsoid(double x,double y,double z,double rx,double ry,double rz,int rgb){
        GL11.glBegin(GL11.GL_QUADS);
        for(int j=0;j<6;j++)for(int i=0;i<12;i++){
            double p=-Math.PI/2+j*Math.PI/6,q=p+Math.PI/6,a=i*Math.PI/6,b=a+Math.PI/6;
            color(rgb,.68+.19*Math.sin((p+q)/2)+.12*Math.cos((a+b)/2));
            sphereVertex(x,y,z,rx,ry,rz,p,a);sphereVertex(x,y,z,rx,ry,rz,p,b);sphereVertex(x,y,z,rx,ry,rz,q,b);sphereVertex(x,y,z,rx,ry,rz,q,a);
        }GL11.glEnd();
    }
    private static void sphereVertex(double x,double y,double z,double rx,double ry,double rz,double p,double a){GL11.glNormal3d(Math.cos(p)*Math.cos(a)/rx,Math.sin(p)/ry,Math.cos(p)*Math.sin(a)/rz);GL11.glVertex3d(x+Math.cos(p)*Math.cos(a)*rx,y+Math.sin(p)*ry,z+Math.cos(p)*Math.sin(a)*rz);}
    static void box(double x,double y,double z,double xx,double yy,double zz,int rgb){GL11.glBegin(GL11.GL_QUADS);boxVertices(new Gta7Bounds(x,y,z,xx,yy,zz),rgb);GL11.glEnd();}
    static void color(int rgb,double shade){GL11.glColor3d(((rgb>>16)&255)/255.0*shade*(.86+.24*shade),((rgb>>8)&255)/255.0*shade*(.89+.16*shade),(rgb&255)/255.0*shade*(.97+.04*shade));}
    private static void texturedBox(Gta7Bounds b,int rgb,int material) {
        GL11.glNormal3d(0,0,-1);color(rgb,.84);tquad(b,material,0,b.x0,b.y0,b.z0,b.x1,b.y0,b.z0,b.x1,b.y1,b.z0,b.x0,b.y1,b.z0);
        GL11.glNormal3d(0,0,1);color(rgb,.99);tquad(b,material,0,b.x1,b.y0,b.z1,b.x0,b.y0,b.z1,b.x0,b.y1,b.z1,b.x1,b.y1,b.z1);
        GL11.glNormal3d(-1,0,0);color(rgb,1.02);tquad(b,material,1,b.x0,b.y0,b.z1,b.x0,b.y0,b.z0,b.x0,b.y1,b.z0,b.x0,b.y1,b.z1);
        GL11.glNormal3d(1,0,0);color(rgb,.79);tquad(b,material,1,b.x1,b.y0,b.z0,b.x1,b.y0,b.z1,b.x1,b.y1,b.z1,b.x1,b.y1,b.z0);
        GL11.glNormal3d(0,1,0);color(rgb,1.07);tquad(b,material,2,b.x0,b.y1,b.z0,b.x1,b.y1,b.z0,b.x1,b.y1,b.z1,b.x0,b.y1,b.z1);
        GL11.glNormal3d(0,-1,0);color(rgb,.66);tquad(b,material,2,b.x0,b.y0,b.z1,b.x1,b.y0,b.z1,b.x1,b.y0,b.z0,b.x0,b.y0,b.z0);
    }
    private static void tquad(Gta7Bounds box,int material,int face,double a,double b,double c,double d,double e,double f,double g,double h,double i,double j,double k,double l) {
        tvertex(box,material,face,a,b,c);tvertex(box,material,face,d,e,f);tvertex(box,material,face,g,h,i);tvertex(box,material,face,j,k,l);
    }
    private static void tvertex(Gta7Bounds b,int material,int face,double x,double y,double z) {
        double u=face==1?z:x,v=face==2?z:y;
        if(material==Gta7Materials.GLASS){u=face==1?(z-b.z0)/Math.max(.001,b.z1-b.z0):(x-b.x0)/Math.max(.001,b.x1-b.x0);v=face==2?(z-b.z0)/Math.max(.001,b.z1-b.z0):(y-b.y0)/Math.max(.001,b.y1-b.y0);}
        else {double scale=material==Gta7Materials.WOOD?1:2;u/=scale;v/=scale;}
        GL11.glTexCoord2d(u,v);GL11.glVertex3d(x,y,z);
    }
    private static void boxVertices(Gta7Bounds b,int rgb){
        GL11.glNormal3d(0,0,-1);color(rgb,.84);quad(b.x0,b.y0,b.z0,b.x1,b.y0,b.z0,b.x1,b.y1,b.z0,b.x0,b.y1,b.z0);
        GL11.glNormal3d(0,0,1);color(rgb,.66);quad(b.x1,b.y0,b.z1,b.x0,b.y0,b.z1,b.x0,b.y1,b.z1,b.x1,b.y1,b.z1);
        GL11.glNormal3d(-1,0,0);color(rgb,.74);quad(b.x0,b.y0,b.z1,b.x0,b.y0,b.z0,b.x0,b.y1,b.z0,b.x0,b.y1,b.z1);
        GL11.glNormal3d(1,0,0);color(rgb,.92);quad(b.x1,b.y0,b.z0,b.x1,b.y0,b.z1,b.x1,b.y1,b.z1,b.x1,b.y1,b.z0);
        GL11.glNormal3d(0,1,0);color(rgb,1);quad(b.x0,b.y1,b.z0,b.x1,b.y1,b.z0,b.x1,b.y1,b.z1,b.x0,b.y1,b.z1);
        GL11.glNormal3d(0,-1,0);color(rgb,.57);quad(b.x0,b.y0,b.z1,b.x1,b.y0,b.z1,b.x1,b.y0,b.z0,b.x0,b.y0,b.z0);
    }
    private static void quad(double a,double b,double c,double d,double e,double f,double g,double h,double i,double j,double k,double l){GL11.glVertex3d(a,b,c);GL11.glVertex3d(d,e,f);GL11.glVertex3d(g,h,i);GL11.glVertex3d(j,k,l);}
    public void close(){
        fog.close();
        if(lists!=0){GL11.glDeleteLists(lists,listCount);lists=0;}cachedWorld=null;compiled=null;
        if(shadowList!=0){GL11.glDeleteLists(shadowList,1);shadowList=0;}
        if(actorLists!=0)GL11.glDeleteLists(actorLists,actorBuilt.length);if(carLists!=0)GL11.glDeleteLists(carLists,CAR_COUNT);if(wheelList!=0)GL11.glDeleteLists(wheelList,1);
        actorLists=carLists=wheelList=0;java.util.Arrays.fill(actorBuilt,false);java.util.Arrays.fill(carBuilt,false);
        emoji.close();materials.close();target.close();
    }
}
