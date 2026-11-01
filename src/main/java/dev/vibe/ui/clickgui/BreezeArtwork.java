package dev.vibe.ui.clickgui;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/** Code-drawn backdrops matching the decorative shapes in Breeze's public previews. */
final class BreezeArtwork {
    private static final EnumMap<BreezePalette,ResourceLocation> textures=new EnumMap<BreezePalette,ResourceLocation>(BreezePalette.class);
    static void draw(BreezePalette palette,int x,int y,int w,int h){
        if(palette==BreezePalette.DEFAULT||palette==BreezePalette.SILENCE||palette==BreezePalette.QUANTUM)return;
        ResourceLocation texture=textures.get(palette);
        if(texture==null){DynamicTexture image=new DynamicTexture(paint(palette));image.setBlurMipmap(true,false);texture=Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("breeze_"+palette.name().toLowerCase(java.util.Locale.ROOT),image);textures.put(palette,texture);}
        GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,771);GlStateManager.disableAlpha();GlStateManager.color(1,1,1,1);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);Gui.drawScaledCustomSizeModalRect(x,y,0,0,1000,700,w,h,1000,700);GlStateManager.resetColor();
    }
    private static BufferedImage paint(BreezePalette p){
        BufferedImage image=new BufferedImage(1000,700,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setClip(new RoundRectangle2D.Float(0,0,1000,700,40,40));
        if(p==BreezePalette.HELLO_KITTY){
            g.setColor(new Color(0xA3417D));g.fillRect(0,0,1000,700);
            for(int i=0;i<7;i++){g.setColor(new Color(i%2==0?0x8C316C:0xAF4B85));Path2D wave=new Path2D.Float();wave.moveTo(-40,i*130-140);wave.curveTo(230,i*130+60,580,i*130-240,1050,i*130-65);wave.lineTo(1050,i*130+20);wave.curveTo(580,i*130-150,230,i*130+150,-40,i*130-50);wave.closePath();g.fill(wave);}
            g.setColor(new Color(0xC6A1B7));for(int[] point:new int[][]{{140,100},{410,60},{450,20},{160,160},{850,330},{950,390},{110,570}})star(g,point[0],point[1],24);
            for(int[] point:new int[][]{{50,280},{230,55},{850,55},{550,240},{920,540},{585,440},{640,390}})heart(g,point[0],point[1]);
            cat(g);
        }else{
            int c=p==BreezePalette.FLARIAL?0xFF5C2008:p==BreezePalette.LEAF?0xFF123442:0xFF211732;
            g.setPaint(new RadialGradientPaint(20,30,450,new float[]{0,1},new Color[]{new Color(c,true),new Color(c&0xFFFFFF,true)}));g.fillRect(0,0,1000,700);
            int second=p==BreezePalette.FLARIAL?0xFF5D000B:0xFF11344B;
            g.setPaint(new RadialGradientPaint(1000,700,430,new float[]{0,1},new Color[]{new Color(second,true),new Color(second&0xFFFFFF,true)}));g.fillRect(0,0,1000,700);
        }
        g.dispose();return image;
    }
    private static void star(Graphics2D g,int x,int y,int r){Path2D path=new Path2D.Float();path.moveTo(x,y-r);path.quadTo(x+3,y-3,x+r,y);path.quadTo(x+3,y+3,x,y+r);path.quadTo(x-3,y+3,x-r,y);path.quadTo(x-3,y-3,x,y-r);g.fill(path);}
    private static void heart(Graphics2D g,int x,int y){Path2D path=new Path2D.Float();path.moveTo(x+28,y+40);path.curveTo(x-25,y+5,x+2,y-17,x+25,y+4);path.curveTo(x+55,y-20,x+78,y+9,x+28,y+40);g.setColor(new Color(0xC378A5));g.fill(path);g.setColor(new Color(0x402536));g.setStroke(new BasicStroke(5,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(path);}
    private static void cat(Graphics2D g){
        g.setStroke(new BasicStroke(7,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));Color edge=new Color(0x352432),fur=new Color(0xCAB2BD);
        g.setColor(fur);Ellipse2D body=new Ellipse2D.Float(650,460,190,210);g.fill(body);g.setColor(edge);g.draw(body);
        for(int[] xy:new int[][]{{635,600},{775,600},{620,505},{805,510}}){Ellipse2D paw=new Ellipse2D.Float(xy[0],xy[1],85,65);g.setColor(fur);g.fill(paw);g.setColor(edge);g.draw(paw);}
        Path2D head=new Path2D.Float();head.moveTo(640,365);head.curveTo(638,325,632,285,650,270);head.quadTo(680,270,716,301);head.quadTo(766,290,823,304);head.quadTo(866,266,889,279);head.quadTo(910,321,892,365);head.curveTo(940,500,595,530,640,365);head.closePath();g.setColor(fur);g.fill(head);g.setColor(edge);g.draw(head);
        g.fill(new Ellipse2D.Float(696,389,13,21));g.fill(new Ellipse2D.Float(835,389,13,21));g.setColor(new Color(0xB79036));g.fill(new Ellipse2D.Float(763,412,21,15));g.setColor(edge);g.draw(new Ellipse2D.Float(763,412,21,15));
        for(int dy:new int[]{0,24,48}){g.drawLine(615,381+dy,658,388+dy);g.drawLine(889,388+dy,932,381+dy);}
        g.setColor(new Color(0xD950AD));for(int i=0;i<5;i++){double a=i*Math.PI*2/5;Ellipse2D petal=new Ellipse2D.Double(827+Math.cos(a)*26,316+Math.sin(a)*26,40,40);g.fill(petal);g.setColor(edge);g.draw(petal);g.setColor(new Color(0xD950AD));}g.setColor(new Color(0xC19B37));g.fill(new Ellipse2D.Float(842,330,28,28));g.setColor(edge);g.draw(new Ellipse2D.Float(842,330,28,28));
        g.setColor(new Color(0x8D5B78, true));g.fillRect(0,0,1000,700);
    }
}
