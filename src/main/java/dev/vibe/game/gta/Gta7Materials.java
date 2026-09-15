package dev.vibe.game.gta;

import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Small repeatable material textures with explicit mip levels, generated once per GL context. */
final class Gta7Materials {
    static final int PLAIN=0, PLASTER=1, BRICK=2, ASPHALT=3, PAVING=4, WOOD=5, GRASS=6, GLASS=7;
    static final int SAND=8, SNOW=9, METAL=10, STONE=11;
    static final int COUNT=12;
    private final int[] textures=new int[COUNT];

    void create() {
        if(textures[0]!=0)return;
        for(int material=0;material<COUNT;material++) {
            int size=128;
            byte[] pixels=new byte[size*size*4];
            for(int y=0;y<size;y++)for(int x=0;x<size;x++) {
                int noise=hash(x,y,material)%13-6;
                double value=250;
                if(material==PLASTER)value=242+noise*.65;
                if(material==BRICK) {
                    int row=y/16, col=(x+(row%2)*16)/32;
                    boolean mortar=y%16<2||(x+(row%2)*16)%32<2;
                    value=mortar?178:237+(hash(col,row,11)%15-7)+noise*.55;
                }
                if(material==ASPHALT)value=232+noise*1.9+(hash(x/8,y/8,17)%7-3);
                if(material==PAVING) {
                    boolean seam=x%32<1||y%32<1;
                    value=seam?193:239+(hash(x/32,y/32,19)%9-4)+noise*.4;
                }
                if(material==WOOD)value=237+Math.sin(y*.47+Math.sin(x*.055)*1.2)*8+noise*.3-(y%32<2?27:0);
                if(material==GRASS)value=232+noise*2.1+(hash(x/3,y/5,31)%15-7);
                if(material==SAND)value=239+noise*.5+Math.sin(y*.21+Math.sin(x*.046)*3)*5;
                if(material==SNOW)value=247+noise*.35+(hash(x/7,y/7,47)%5-2);
                if(material==METAL)value=240+noise*.3-(x%64<1||y%64<1?30:0);
                if(material==STONE)value=234+noise*.8-(y%32<2||(x+(y/32%2)*32)%64<2?20:0);
                if(material==GLASS) {
                    value=182+y*.48;
                    if((x+y/2)%100<15)value+=23;
                }
                int channel=Math.max(0,Math.min(255,(int)value)),index=(y*size+x)*4;
                pixels[index]=pixels[index+1]=pixels[index+2]=(byte)channel;pixels[index+3]=(byte)255;
            }
            textures[material]=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[material]);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL11.GL_REPEAT);
            for(int level=0;;level++) {
                ByteBuffer buffer=BufferUtils.createByteBuffer(pixels.length);buffer.put(pixels).flip();
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,level,GL11.GL_RGBA8,size,size,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);
                if(size==1)break;
                byte[] reduced=new byte[size*size];int next=size/2;
                for(int y=0;y<next;y++)for(int x=0;x<next;x++)for(int c=0;c<4;c++) {
                    int sum=0;for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++)sum+=pixels[((y*2+dy)*size+x*2+dx)*4+c]&255;
                    reduced[(y*next+x)*4+c]=(byte)(sum/4);
                }
                pixels=reduced;size=next;
            }
        }
    }

    static int material(Gta7World.Box box) {
        int rgb=box.color;
        if(rgb==0xD1AF72||rgb==0xD1B689||rgb==0xA49C8A)return SAND;
        if(rgb==0xDAE9ED||rgb==0xB9DCE5||rgb==0xE4E9E4)return SNOW;
        if(rgb==0x6C7850||rgb==0x3D6458||rgb==0x52664D||rgb==0x89938F||rgb==0xDFA5C7)return GRASS;
        if(rgb==0x9FAEB5||rgb==0xD7CAB1)return STONE;
        if(rgb==0x88704B||rgb==0xA77D51||rgb==0xBA8055)return WOOD;
        if(rgb==0x929A98||rgb==0x8572A4||rgb==0xC6B18D)return PLASTER;
        if(rgb==0x839598||rgb==0x88989A||rgb==0x556975||rgb==0x637D88)return METAL;
        if(rgb==0x343C44)return ASPHALT;
        if(rgb==0xB6B4A6||rgb==0xBCC0B6||rgb==0xC7BB9D)return PAVING;
        if(rgb==0x648455||rgb==0x526454||rgb==0x7B8966)return GRASS;
        if(rgb==0x976C5D)return BRICK;
        if(rgb==0x97836D||rgb==0x987B53||rgb==0xB19B75||rgb==0x846A51||rgb==0x725744)return WOOD;
        if(rgb==0x60818D||rgb==0x63858D||rgb==0x759B9D)return GLASS;
        if(rgb==0xB5A08A||rgb==0xC5C1B2||rgb==0x839494||rgb==0xA6ABB6||rgb==0xC4AE84||rgb==0xB7AE99)return PLASTER;
        return PLAIN;
    }
    private static int hash(int x,int y,int salt) { int n=x*73428767^y*912931^salt*438289;n=(n^(n>>>13))*1274126177;return (n^(n>>>16))&0x7fffffff; }
    void bind(int material) { GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[material]); }
    void close() { for(int i=0;i<textures.length;i++)if(textures[i]!=0){GL11.glDeleteTextures(textures[i]);textures[i]=0;} }
}
