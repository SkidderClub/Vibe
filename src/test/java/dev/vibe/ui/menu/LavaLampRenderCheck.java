package dev.vibe.ui.menu;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

/** Pixel checks for a bottom reservoir, discrete ascending drops, sharp wax and a separately blurred scene. */
final class LavaLampRenderCheck {
    private static final int W=960,H=540,X=120,Y=70,PW=720,PH=400;
    private static Minecraft mc;
    private static Path output;
    static void verify(Minecraft minecraft,Path directory) throws Exception {
        mc=minecraft;output=directory;mc.gameSettings.guiScale=1;MenuThemes.select(MenuThemes.Preset.LAVENDER);
        byte[] first=render(3,0xFF000000,false),later=render(3.6F,0xFF000000,false);
        save(first,"lava-lamp-3s.png");
        int filled=0,total=0;
        for(int y=5;y<28;y++)for(int x=20;x<PW-20;x++){total++;if(bright(first,X+x,Y+y)>330)filled++;}
        require(filled>total*.88,"There is no continuous volume of wax at the bottom of the panel");
        filled=0;total=0;
        for(int y=PH-20;y<PH-5;y++)for(int x=20;x<PW-20;x++){total++;if(bright(first,X+x,Y+y)>170)filled++;}
        require(filled>total*.88,"There is no continuous upper reservoir to absorb arriving wax");
        List<Blob> start=blobs(first),end=blobs(later);require(!start.isEmpty()&&!end.isEmpty(),"No detached floating spheres were rendered");
        boolean ascending=false,sharp=false,organic=false,luminous=false,glowing=false;
        for(Blob a:start){
            if(a.width()<PH*.19&&a.height()<PH*.19){
                if(Math.abs(a.height()-a.width())>4)organic=true;
                if(bright(first,(int)a.cx(),(int)a.cy())>560)luminous=true;
                if(a.minX>45&&bright(first,a.minX-5,(int)a.cy())-bright(first,a.minX-30,(int)a.cy())>35)glowing=true;
                for(Blob b:end)if(Math.abs(a.cx()-b.cx())<14&&b.cy()-a.cy()>5&&b.cy()-a.cy()<48)ascending=true;
                int row=(int)a.cy();for(int x=a.minX-3;x<a.maxX+3;x++)if(x>=1&&x+1<W&&Math.abs(bright(first,x,row)-bright(first,x+1,row))>80)sharp=true;
            }
        }
        require(ascending,"The small wax spheres do not travel upwards");require(sharp,"Wax silhouettes are blurred instead of rendered sharply over the glass");
        require(organic,"Floating wax is still uniformly circular");
        require(luminous&&glowing,"Wax needs both a luminous core and visible light outside its silhouette");
        absorption();
        emissionSeams();
        for(int second:new int[]{0,3,6,9,12})save(render(second,0xFF101018,false),"lava-lamp-"+second+"s.png");

        byte[] red=render(4,0xFFB03018,false),blue=render(4,0xFF1840B0,false);int[] response=new int[(PW-40)*(PH-40)];int index=0;
        for(int y=20;y<PH-20;y++)for(int x=20;x<PW-20;x++){int at=((Y+y)*W+X+x)*4;response[index++]=Math.abs((red[at]&255)-(blue[at]&255));}
        Arrays.sort(response);require(response[response.length/2]>65,"The frosted panel is opaque and does not transmit the background");

        GlassMenusRenderCheck.frame(W,H);checker();byte[] original=pixels();draw(5);byte[] frosted=pixels();
        double before=contrast(original),after=contrast(frosted);require(after<before*.20,"The transmitted background is not blurred");
        save(frosted,"lava-lamp-frosted-background.png");require(GL11.glGetError()==GL11.GL_NO_ERROR,"Lava lamp produced a GL error");
        profile();
        System.out.println("Lava lamp pixels passed: upper and lower wax reservoirs, organic ascending blobs, sharp silhouettes, transparent frosted background and theme-colored glow.");
    }
    private static void absorption() throws Exception {
        Field clock=MenuGlass.class.getDeclaredField("frameTime"),epoch=MenuGlass.class.getDeclaredField("EPOCH"),source=MenuGlass.class.getDeclaredField("drops"),shape=MenuGlass.class.getDeclaredField("forms");
        clock.setAccessible(true);epoch.setAccessible(true);source.setAccessible(true);shape.setAccessible(true);
        java.lang.reflect.Method emit=MenuGlass.class.getDeclaredMethod("prepareDrops");emit.setAccessible(true);
        java.nio.FloatBuffer drops=(java.nio.FloatBuffer)source.get(null),forms=(java.nio.FloatBuffer)shape.get(null);
        float[] previousRadius=new float[8],previousBottom=new float[8];int absorbed=0;
        for(int frame=0;frame<1600;frame++){
            clock.setLong(null,epoch.getLong(null)+frame*20000000L);emit.invoke(null);
            for(int i=0;i<8;i++){
                float radius=drops.get(i*4+2);
                if(previousRadius[i]>0&&radius==0){require(previousBottom[i]>.94F,"A wax blob disappeared before being fully absorbed into the upper pool");absorbed++;}
                previousRadius[i]=radius;previousBottom[i]=drops.get(i*4+1)-radius*(Math.max(drops.get(i*4+3),forms.get(i*4))+.25F);
            }
        }
        require(absorbed>=8,"Did not observe a full wax absorption cycle for all emitters");
    }
    private static void emissionSeams() throws Exception {
        byte[] image=render(3,0xFF101018,false);Field field=MenuGlass.class.getDeclaredField("drops");field.setAccessible(true);java.nio.FloatBuffer sources=(java.nio.FloatBuffer)field.get(null);
        Field formField=MenuGlass.class.getDeclaredField("forms");formField.setAccessible(true);java.nio.FloatBuffer forms=(java.nio.FloatBuffer)formField.get(null);int checked=0;
        for(int i=0;i<8;i++){
            float r=sources.get(i*4+2)*PH;if(r<=0)continue;
            float cx=X+sources.get(i*4)*PW,cy=Y+sources.get(i*4+1)*PH,stretch=sources.get(i*4+3);
            float longest=Math.max(stretch,forms.get(i*4)),shortest=Math.min(stretch,forms.get(i*4));
            float ex=r*(longest*1.8F+1)+PH*.052F*2.85F*longest/shortest,ey=ex;
            for(int x:new int[]{(int)Math.floor(cx-ex),(int)Math.ceil(cx+ex)})for(int y=Y+52;y<Y+100;y++){
                if(x<X+25||x>=X+PW-25||y<cy-ey||y>cy+ey)continue;boolean near=false;
                for(int j=0;j<8;j++){float jr=sources.get(j*4+2)*PH;if(jr<=0)continue;double dx=x-(X+sources.get(j*4)*PW),dy=y-(Y+sources.get(j*4+1)*PH);if(dx*dx+dy*dy<jr*jr*4.5)near=true;}
                if(near)continue;checked++;require(Math.abs(bright(image,x-1,y)-bright(image,x,y))<10,"A rectangular overlap is visible in the reservoir glow");
            }
        }
        require(checked>0,"No emitter overlap boundaries were checked");
    }
    private static void profile() throws Exception {
        if(!org.lwjgl.opengl.GLContext.getCapabilities().GL_ARB_timer_query&&!org.lwjgl.opengl.GLContext.getCapabilities().OpenGL33)return;
        long background=0,blur=0,lava=0,layers=0;long[] gpu=new long[4];int[] queries={GL15.glGenQueries(),GL15.glGenQueries(),GL15.glGenQueries(),GL15.glGenQueries()};MainMenuShaderManager shaders=new MainMenuShaderManager();
        for(int i=0;i<6;i++){
            GlassMenusRenderCheck.frame(W,H);GL11.glFinish();long start=System.nanoTime();GL15.glBeginQuery(0x88BF,queries[0]);shaders.draw(W,H);GL15.glEndQuery(0x88BF);GL11.glFinish();long first=System.nanoTime();
            GL15.glBeginQuery(0x88BF,queries[1]);MenuGlass.begin(new GuiChat(),shaders,W,H);GL15.glEndQuery(0x88BF);GL11.glFinish();long second=System.nanoTime();GL15.glBeginQuery(0x88BF,queries[2]);AccountScreenStyle.window(X,Y,PW,PH);GL15.glEndQuery(0x88BF);GL11.glFinish();long third=System.nanoTime();
            GL15.glBeginQuery(0x88BF,queries[3]);MenuGlass.end();GL15.glEndQuery(0x88BF);GL11.glFinish();long fourth=System.nanoTime();if(i>0){background+=first-start;blur+=second-first;lava+=third-second;layers+=fourth-third;for(int j=0;j<4;j++)gpu[j]+=GL15.glGetQueryObjecti(queries[j],GL15.GL_QUERY_RESULT);}
        }
        System.out.printf(java.util.Locale.ROOT,"Menu GPU stages (%s): backdrop %.2f ms, blur %.2f ms, lava %.2f ms, layers %.2f ms.%n",GL11.glGetString(GL11.GL_RENDERER),background/5e6,blur/5e6,lava/5e6,layers/5e6);
        System.out.printf(java.util.Locale.ROOT,"GPU timer: backdrop %.2f ms, blur %.2f ms, lava %.2f ms, layers %.2f ms.%n",gpu[0]/5e6,gpu[1]/5e6,gpu[2]/5e6,gpu[3]/5e6);for(int query:queries)GL15.glDeleteQueries(query);
    }
    private static byte[] render(float time,int background,boolean checker) throws Exception {
        GlassMenusRenderCheck.frame(W,H);Gui.drawRect(0,0,W,H,background);if(checker)checker();draw(time);return pixels();
    }
    private static void draw(float seconds) throws Exception {
        MenuGlass.begin(new GuiChat(),null,W,H);
        Field epoch=MenuGlass.class.getDeclaredField("EPOCH"),clock=MenuGlass.class.getDeclaredField("frameTime");epoch.setAccessible(true);clock.setAccessible(true);
        clock.setLong(null,epoch.getLong(null)+(long)(seconds*1e9));AccountScreenStyle.window(X,Y,PW,PH);MenuGlass.end();
    }
    private static void checker(){for(int y=0;y<H;y+=4)for(int x=0;x<W;x+=4)Gui.drawRect(x,y,x+4,y+4,((x/4+y/4)&1)==0?0xFF202020:0xFFE0E0E0);}
    private static double contrast(byte[] image){long sum=0;int count=0;for(int y=Y+90;y<Y+PH-30;y++)for(int x=X+25;x<X+PW-26;x++){sum+=Math.abs((image[(y*W+x)*4]&255)-(image[(y*W+x+1)*4]&255));count++;}return sum/(double)count;}
    private static List<Blob> blobs(byte[] image){
        boolean[] visited=new boolean[PW*PH];int[] queue=new int[PW*PH];List<Blob> blobs=new ArrayList<Blob>();
        for(int y=58;y<PH-5;y++)for(int x=8;x<PW-8;x++){
            int initial=y*PW+x;if(visited[initial]||bright(image,X+x,Y+y)<=330)continue;
            Blob blob=new Blob();int first=0,last=0;queue[last++]=initial;visited[initial]=true;
            while(first<last){int value=queue[first++],px=value%PW,py=value/PW;blob.add(X+px,Y+py);
                for(int offset:new int[]{-1,1,-PW,PW}){int next=value+offset;if(next<0||next>=visited.length)continue;int nx=next%PW,ny=next/PW;
                    if(nx<8||nx>=PW-8||ny<58||ny>=PH-5||visited[next]||bright(image,X+nx,Y+ny)<=330)continue;
                    visited[next]=true;queue[last++]=next;
                }
            }
            if(blob.area>80&&blob.minY>Y+60&&blob.maxY<Y+PH-7)blobs.add(blob);
        }
        return blobs;
    }
    private static final class Blob {
        int minX=W,maxX=0,minY=H,maxY=0,area;long x,y;
        void add(int px,int py){minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);x+=px;y+=py;area++;}
        float cx(){return x/(float)area;}float cy(){return y/(float)area;}int width(){return maxX-minX+1;}int height(){return maxY-minY+1;}
    }
    private static int bright(byte[] pixels,int x,int y){int i=(y*W+x)*4;return(pixels[i]&255)+(pixels[i+1]&255)+(pixels[i+2]&255);}
    private static byte[] pixels(){ByteBuffer pixels=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);byte[] image=new byte[pixels.capacity()];pixels.get(image);return image;}
    private static void save(byte[] pixels,String name) throws Exception {BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=((H-1-y)*W+x)*4;image.setRGB(x,y,((pixels[i]&255)<<16)|((pixels[i+1]&255)<<8)|(pixels[i+2]&255));}ImageIO.write(image,"png",output.resolve(name).toFile());}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
