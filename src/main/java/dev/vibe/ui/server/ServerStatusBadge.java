package dev.vibe.ui.server;

import dev.vibe.server.ServerJoinHistory;
import dev.vibe.server.ServerJoinTracker;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Supersampled, cached status chips. A connection failure is distinguished from an actual ban. */
final class ServerStatusBadge {
    enum Kind {
        JOINED("Joined",0x7DDDC3),BANNED("Banned",0xF28594),FAILED("Failed",0xE4BB7E),UNKNOWN("Untried",0xA0A7B6),CONNECTING("Joining",0xB4A0FF);
        final String label;final int color;Kind(String label,int color){this.label=label;this.color=color;}
    }
    private static final EnumMap<Kind,ResourceLocation> TEXTURES=new EnumMap<>(Kind.class);
    static Kind kind(ServerJoinHistory.Result result){
        switch(result.status){case JOINED:return Kind.JOINED;case FAILED:return ServerJoinTracker.isBanReason(result.reason)?Kind.BANNED:Kind.FAILED;case CONNECTING:return Kind.CONNECTING;default:return Kind.UNKNOWN;}
    }
    static void draw(int x,int y,Kind kind){
        Minecraft mc=Minecraft.getMinecraft();ResourceLocation texture=TEXTURES.get(kind);
        if(texture==null){
            BufferedImage image=new BufferedImage(152,40,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(kind.color|0x18000000,true));g.fillRoundRect(0,0,152,40,20,20);
            g.setStroke(new BasicStroke(1));g.setColor(new Color(kind.color|0x34000000,true));g.drawRoundRect(1,1,150,38,19,19);
            g.setColor(new Color(kind.color));g.setStroke(new BasicStroke(2.2F,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            if(kind==Kind.JOINED||kind==Kind.BANNED){
                Path2D shield=new Path2D.Float();shield.moveTo(15,12);shield.lineTo(24,9);shield.lineTo(33,12);shield.lineTo(32,23);shield.quadTo(30,28,24,31);shield.quadTo(18,28,16,23);shield.closePath();g.draw(shield);
                if(kind==Kind.JOINED){Path2D check=new Path2D.Float();check.moveTo(20,20);check.lineTo(23,23);check.lineTo(28,17);g.draw(check);}
                else{g.draw(new Line2D.Float(20,24,28,16));}
            } else {g.draw(new Ellipse2D.Float(16,12,16,16));g.draw(new Line2D.Float(24,16,24,21));if(kind==Kind.FAILED)g.fill(new Ellipse2D.Float(23,24,2,2));}
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));g.drawString(kind.label,44,27);g.dispose();
            DynamicTexture dynamic=new DynamicTexture(image);dynamic.setBlurMipmap(true,false);texture=mc.getTextureManager().getDynamicTextureLocation("vibe_server_status_"+kind.name(),dynamic);TEXTURES.put(kind,texture);
        }
        GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        boolean alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);GlStateManager.disableAlpha();GlStateManager.color(1,1,1,1);mc.getTextureManager().bindTexture(texture);
        Gui.drawScaledCustomSizeModalRect(x,y,0,0,152,40,76,20,152,40);if(alpha)GlStateManager.enableAlpha();
    }
}
