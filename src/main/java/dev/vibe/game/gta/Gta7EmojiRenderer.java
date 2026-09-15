package dev.vibe.game.gta;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Uses the supplied image unchanged. The front retains its colours and proportions under world lighting. */
final class Gta7EmojiRenderer {
    private int texture,mesh;
    void draw(Gta7Game game){
        double cx=Gta7Regions.Region.EMOJI.x(),cz=Gta7Regions.Region.EMOJI.z();
        if(Math.abs(game.x-cx)>220||Math.abs(game.z-cz)>220)return;
        if(texture==0)create();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_TEXTURE_BIT|GL11.GL_LIGHTING_BIT|GL11.GL_POLYGON_BIT);
        GL11.glPushMatrix();GL11.glTranslated(cx,0,cz);
        if(mesh!=0)GL11.glCallList(mesh);else geometry();
        GL11.glPopMatrix();GL11.glPopAttrib();
    }
    private void create(){
        try(InputStream stream=Gta7EmojiRenderer.class.getResourceAsStream(Gta7EmojiBuilding.TEXTURE)){
            if(stream==null)throw new IOException("Missing emoji facade resource");
            BufferedImage image=ImageIO.read(stream);int width=image.getWidth(),height=image.getHeight();
            ByteBuffer bytes=BufferUtils.createByteBuffer(width*height*4);
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){int rgb=image.getRGB(x,y);bytes.put((byte)(rgb>>16)).put((byte)(rgb>>8)).put((byte)rgb).put((byte)255);}bytes.flip();
            texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,width,height,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
            mesh=GL11.glGenLists(1);if(mesh!=0){GL11.glNewList(mesh,GL11.GL_COMPILE);geometry();GL11.glEndList();}
        }catch(IOException failure){throw new IllegalStateException("Could not load supplied emoji facade",failure);}
    }
    private void geometry(){
        double[][] outline=Gta7EmojiBuilding.OUTLINE;
        GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glBegin(GL11.GL_QUADS);
        for(int i=0;i<outline.length;i++){
            double[] a=outline[i],b=outline[(i+1)%outline.length];
            double ax=Gta7EmojiBuilding.worldX(0,a[0]),ay=Gta7EmojiBuilding.worldY(a[1]);
            double bx=Gta7EmojiBuilding.worldX(0,b[0]),by=Gta7EmojiBuilding.worldY(b[1]);
            GL11.glNormal3d(ay-by,bx-ax,0);Gta7Renderer.color(0xD7AB28,1);
            GL11.glVertex3d(ax,ay,-11);GL11.glVertex3d(bx,by,-11);GL11.glVertex3d(bx,by,10.8);GL11.glVertex3d(ax,ay,10.8);
            Gta7Renderer.color(0x201711,1);
            GL11.glVertex3d(ax,ay,10.8);GL11.glVertex3d(bx,by,10.8);GL11.glVertex3d(bx,by,11.03);GL11.glVertex3d(ax,ay,11.03);
        }GL11.glEnd();
        GL11.glNormal3d(0,0,-1);Gta7Renderer.color(0xDDC890,1);GL11.glBegin(GL11.GL_TRIANGLES);
        for(int index:Gta7EmojiBuilding.TRIANGLES){double[] p=outline[index];GL11.glVertex3d(Gta7EmojiBuilding.worldX(0,p[0]),Gta7EmojiBuilding.worldY(p[1]),10.69);}GL11.glEnd();
        GL11.glEnable(GL11.GL_CULL_FACE);GL11.glCullFace(GL11.GL_BACK);GL11.glFrontFace(GL11.GL_CW);
        GL11.glDisable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glColor4f(1,1,1,1);
        GL11.glBegin(GL11.GL_TRIANGLES);
        for(int index:Gta7EmojiBuilding.TRIANGLES){double[] p=outline[index];GL11.glTexCoord2d(p[0]/184,p[1]/184);GL11.glVertex3d(Gta7EmojiBuilding.worldX(0,p[0]),Gta7EmojiBuilding.worldY(p[1]),11.04);}
        GL11.glEnd();
    }
    void close(){if(texture!=0)GL11.glDeleteTextures(texture);if(mesh!=0)GL11.glDeleteLists(mesh,1);texture=mesh=0;}
}
