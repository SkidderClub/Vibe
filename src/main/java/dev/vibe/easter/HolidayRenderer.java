package dev.vibe.easter;

import dev.vibe.ui.effect.EffectState;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Collections;
import javax.imageio.ImageIO;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** The console and crash screen are pictures in the game framebuffer, never OS windows. */
public final class HolidayRenderer implements AutoCloseable {
    private int pumpkin,console,crash,progress;
    private boolean pumpkinUnavailable;
    private static final int BLUE=0x0078D7;
    public static BufferedImage vanillaPumpkin() throws IOException {
        // Bypass Minecraft's resource-pack manager altogether, then retain our
        // own GL image. Pack changes/reloads cannot replace this texture.
        try(InputStream stream=new DefaultResourcePack(Collections.<String,File>emptyMap()).getInputStream(
                new ResourceLocation("textures/misc/pumpkinblur.png"))){
            BufferedImage image=ImageIO.read(stream);if(image==null)throw new IOException("Missing vanilla pumpkin image.");return image;
        }
    }
    public void pumpkin(int width,int height){
        if(pumpkinUnavailable)return;
        try(EffectState ignored=new EffectState()){
            prepare();if(pumpkin==0)try{pumpkin=upload(vanillaPumpkin());}catch(IOException error){pumpkinUnavailable=true;return;}
            image(pumpkin,0,0,width,height,0,0,1,1,1);
        }
    }
    public void console(int width,int height,double age){
        try(EffectState ignored=new EffectState()){
            prepare();if(console==0)console=upload(consoleImage());
            float w=Math.min(width*.78F,height*1.55F),h=w*480/900F,x=(width-w)/2,y=(height-h)*.42F;
            float opacity=(float)(age>.86?(1-age)/.14:1),title=h*30/480F;
            rectangle(x+4,y+5,w,h,0x000000,.45F*opacity);rectangle(x,y,w,h,0x000000,opacity);
            image(console,x,y,w,title,0,0,1,30/480F,opacity);
            float reveal=(float)Math.min(1,age/.78),body=h-title;
            image(console,x,y+title,w,body*reveal,0,30/480F,1,30/480F+(1-30/480F)*reveal,opacity);
        }
    }
    public void crash(int width,int height,double age){
        try(EffectState ignored=new EffectState()){
            prepare();if(crash==0){crash=upload(crashImage());progress=upload(progressImage());}
            image(crash,0,0,width,height,0,0,1,1,1);
            int row=Math.min(5,(int)(age*6));
            image(progress,width*132/1280F,height*366/720F,width*512/1280F,height*60/720F,0,row/6F,1,(row+1)/6F,1);
        }
    }
    private static BufferedImage consoleImage(){
        BufferedImage image=new BufferedImage(900,480,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();quality(g);
        g.setColor(Color.BLACK);g.fillRect(0,0,900,480);g.setColor(new Color(0xEEEEEE));g.fillRect(0,0,900,30);
        g.setFont(new Font("Segoe UI",Font.PLAIN,14));g.setColor(new Color(0x222222));g.drawString("C:\\Windows\\system32\\cmd.exe",32,20);
        g.setColor(new Color(0x111111));g.fillRect(8,8,16,13);g.setColor(Color.WHITE);g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,9));g.drawString(">_",9,18);
        g.setColor(new Color(0x444444));g.setFont(new Font("Segoe UI",Font.PLAIN,17));g.drawString("\u2212",791,21);g.drawRect(831,10,10,9);g.drawString("\u00d7",875,21);
        g.setColor(new Color(0xD5D5D5));g.setFont(new Font("Consolas",Font.PLAIN,16));
        // These strings are drawn with Graphics2D. They are never executed,
        // passed to a shell, written to a script, or sent anywhere.
        String[] lines={"Microsoft Windows [Version 10.0.19045.4046]","(c) Microsoft Corporation. All rights reserved.","",
                "C:\\Users\\Player> whoami /priv","[+] SeDebugPrivilege ........ enabled",
                "C:\\Users\\Player> net stop WinDefend","The service was stopped successfully.",
                "C:\\Users\\Player> vssadmin delete shadows /all /quiet","[+] Shadow copies removed.",
                "C:\\Users\\Player> cipher /w:C:\\","[+] Overwriting free space...",
                "C:\\Users\\Player> taskkill /f /im explorer.exe","SUCCESS: The process explorer.exe has been terminated.","C:\\Users\\Player> exit"};
        int y=56;for(String line:lines){g.drawString(line,12,y);y+=27;}g.dispose();return image;
    }
    private static BufferedImage crashImage(){
        BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();quality(g);
        g.setColor(new Color(BLUE));g.fillRect(0,0,1280,720);g.setColor(Color.WHITE);g.setFont(new Font("Segoe UI",Font.PLAIN,132));g.drawString(":(",123,185);
        g.setFont(new Font("Segoe UI",Font.PLAIN,27));g.drawString("Your device ran into a problem and needs to restart.",132,269);
        g.drawString("We're just collecting some error info, and then we'll",132,310);g.drawString("restart for you.",132,351);
        g.setFont(new Font("Segoe UI",Font.PLAIN,18));g.drawString("If you call a support person, give them this info:",132,487);
        g.drawString("Stop code: CRITICAL_PROCESS_DIED",132,522);g.dispose();return image;
    }
    private static BufferedImage progressImage(){
        BufferedImage image=new BufferedImage(512,360,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();quality(g);
        g.setColor(new Color(BLUE));g.fillRect(0,0,512,360);g.setColor(Color.WHITE);g.setFont(new Font("Segoe UI",Font.PLAIN,27));
        int row=0;for(String percent:new String[]{"0","18","36","57","83","100"})g.drawString(percent+"% complete",0,row++*60+35);
        g.dispose();return image;
    }
    private static void quality(Graphics2D g){g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);}
    private static int upload(BufferedImage image){
        ByteBuffer bytes=BufferUtils.createByteBuffer(image.getWidth()*image.getHeight()*4);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int color=image.getRGB(x,y);bytes.put((byte)(color>>16)).put((byte)(color>>8)).put((byte)color).put((byte)(color>>>24));}bytes.flip();
        int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,image.getWidth(),image.getHeight(),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);return texture;
    }
    private static void prepare(){
        GL20.glUseProgram(0);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        for(int unit=1;unit<=2;unit++){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glDisable(GL11.GL_TEXTURE_2D);}
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
    }
    private static void rectangle(float x,float y,float w,float h,int color,float opacity){GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor4f((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,opacity);GL11.glBegin(GL11.GL_QUADS);GL11.glVertex2f(x,y);GL11.glVertex2f(x,y+h);GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x+w,y);GL11.glEnd();}
    private static void image(int texture,float x,float y,float w,float h,float u0,float v0,float u1,float v1,float opacity){
        GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glColor4f(1,1,1,opacity);
        GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(u0,v0);GL11.glVertex2f(x,y);GL11.glTexCoord2f(u0,v1);GL11.glVertex2f(x,y+h);GL11.glTexCoord2f(u1,v1);GL11.glVertex2f(x+w,y+h);GL11.glTexCoord2f(u1,v0);GL11.glVertex2f(x+w,y);GL11.glEnd();
    }
    @Override public void close(){for(int texture:new int[]{pumpkin,console,crash,progress})if(texture!=0)GL11.glDeleteTextures(texture);pumpkin=console=crash=progress=0;pumpkinUnavailable=false;}
}
