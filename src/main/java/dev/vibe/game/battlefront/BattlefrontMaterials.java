package dev.vibe.game.battlefront;

import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Original seamless material maps with complete mip chains for stable distant detail. */
final class BattlefrontMaterials {
    static final int SAND=0,SOIL=1,BARK=2,STONE=3,METAL=4,LEAF=5;
    private final int[] textures=new int[6];
    void create(){
        if(textures[0]!=0)return;
        for(int m=0;m<textures.length;m++){
            int size=128;byte[] pixels=new byte[size*size*4];
            for(int y=0;y<size;y++)for(int x=0;x<size;x++){
                double n=(hash(x,y,m)%101)/100.0,value=220;
                if(m==SAND)value=216+18*Math.sin(y*Math.PI/16+Math.sin(x*Math.PI/32)*1.3)+n*24;
                if(m==SOIL)value=176+n*54+(hash(x/4,y/3,8)%29); // leaf litter, pebbles and short blades
                if(m==BARK)value=182+22*Math.sin(x*Math.PI/8+Math.sin(y*Math.PI/32)*.6)+n*24;
                if(m==STONE)value=219+5*Math.sin(x*Math.PI/32)*Math.cos(y*Math.PI/16)+n*20;
                if(m==METAL)value=239+n*10-(x%64<2||y%64<2?45:0)-(x%64>7&&x%64<12&&y%64>7&&y%64<12?55:0);
                if(m==LEAF)value=186+n*38+18*Math.cos(x*Math.PI/8)*Math.sin(y*Math.PI/16);
                int v=(int)Math.max(50,Math.min(255,value)),i=(y*size+x)*4;
                pixels[i]=pixels[i+1]=pixels[i+2]=(byte)v;pixels[i+3]=(byte)255;
            }
            textures[m]=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[m]);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL11.GL_REPEAT);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL11.GL_REPEAT);
            for(int level=0;;level++){
                ByteBuffer data=BufferUtils.createByteBuffer(pixels.length);data.put(pixels).flip();GL11.glTexImage2D(GL11.GL_TEXTURE_2D,level,GL11.GL_RGBA8,size,size,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
                if(size==1)break;int next=size/2;byte[] down=new byte[next*next*4];
                for(int y=0;y<next;y++)for(int x=0;x<next;x++)for(int c=0;c<4;c++){int sum=0;for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++)sum+=pixels[((y*2+dy)*size+x*2+dx)*4+c]&255;down[(y*next+x)*4+c]=(byte)(sum/4);}
                pixels=down;size=next;
            }
        }
    }
    void bind(int material){GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[material]);}
    void close(){for(int i=0;i<textures.length;i++)if(textures[i]!=0){GL11.glDeleteTextures(textures[i]);textures[i]=0;}}
    private static int hash(int x,int y,int salt){int h=x*73428767^y*912931^salt*438289;h=(h^(h>>>13))*1274126177;return (h^(h>>>16))&0x7fffffff;}
}
