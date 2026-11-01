package dev.vibe.ui.clickgui;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.DocumentBuilderFactory;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.w3c.dom.*;

/** Bundled SVGs rasterized at the physical display scale, with cached white tint masks. */
final class BreezeIcons {
    private static final Map<String,DynamicTexture> textures=new HashMap<String,DynamicTexture>();
    private static Document document;
    private static Document symbols()throws Exception{
        if(document==null){DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
            try(InputStream input=BreezeIcons.class.getResourceAsStream("/assets/vibe/breeze/icons.svg")){if(input==null)throw new IllegalStateException("Breeze icons are missing");document=factory.newDocumentBuilder().parse(input);}}
        return document;
    }
    static BufferedImage rasterize(String name,int pixels){
        try{Element symbol=null;NodeList all=symbols().getElementsByTagName("symbol");for(int i=0;i<all.getLength();i++){Element candidate=(Element)all.item(i);if(name.equals(candidate.getAttribute("id"))){symbol=candidate;break;}}if(symbol==null)throw new IllegalArgumentException("Unknown Breeze icon: "+name);
            BufferedImage image=new BufferedImage(pixels,pixels,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
            try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);g.scale(pixels/24D,pixels/24D);g.setStroke(new BasicStroke(1.8F,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.setColor(Color.WHITE);
                NodeList children=symbol.getChildNodes();for(int i=0;i<children.getLength();i++)if(children.item(i) instanceof Element){Element e=(Element)children.item(i);Shape shape=shape(e);if("currentColor".equals(e.getAttribute("fill")))g.fill(shape);if(!"none".equals(e.getAttribute("stroke")))g.draw(shape);if(e.hasAttribute("mask"))mask(g,e.getAttribute("mask"));}}
            finally{g.dispose();}
            int[] data=image.getRGB(0,0,pixels,pixels,null,0,pixels);for(int i=0;i<data.length;i++)data[i]|=0xFFFFFF;image.setRGB(0,0,pixels,pixels,data,0,pixels);return image;
        }catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new IllegalStateException("Cannot rasterize Breeze icon",failure);}
    }
    private static void mask(Graphics2D g,String reference)throws Exception{
        String id=reference.substring(reference.indexOf('#')+1,reference.indexOf(')'));NodeList masks=symbols().getElementsByTagName("mask");
        for(int i=0;i<masks.getLength();i++){Element candidate=(Element)masks.item(i);if(!id.equals(candidate.getAttribute("id")))continue;NodeList shapes=candidate.getChildNodes();g.setComposite(AlphaComposite.Clear);for(int j=0;j<shapes.getLength();j++)if(shapes.item(j) instanceof Element){Element e=(Element)shapes.item(j);if("black".equals(e.getAttribute("fill")))g.fill(shape(e));}g.setComposite(AlphaComposite.SrcOver);return;}
        throw new IllegalArgumentException("Missing Breeze SVG mask: "+id);
    }
    private static double n(Element e,String name){return e.hasAttribute(name)?Double.parseDouble(e.getAttribute(name)):0;}
    private static Shape shape(Element e){
        String tag=e.getTagName();if(tag.equals("circle"))return new Ellipse2D.Double(n(e,"cx")-n(e,"r"),n(e,"cy")-n(e,"r"),n(e,"r")*2,n(e,"r")*2);
        if(tag.equals("ellipse"))return new Ellipse2D.Double(n(e,"cx")-n(e,"rx"),n(e,"cy")-n(e,"ry"),n(e,"rx")*2,n(e,"ry")*2);
        if(tag.equals("rect"))return new RoundRectangle2D.Double(n(e,"x"),n(e,"y"),n(e,"width"),n(e,"height"),n(e,"rx")*2,n(e,"rx")*2);
        Path2D path=new Path2D.Double();if(tag.equals("polygon")){String[] points=e.getAttribute("points").split("[ ,]+");for(int i=0;i<points.length;i+=2){double a=Double.parseDouble(points[i]),b=Double.parseDouble(points[i+1]);if(i==0)path.moveTo(a,b);else path.lineTo(a,b);}path.closePath();return path;}
        if(!tag.equals("path"))throw new IllegalArgumentException("Unsupported Breeze SVG primitive: "+tag);
        java.util.List<String> tokens=new ArrayList<String>();Matcher matcher=Pattern.compile("[MLCQZ]|[-+]?(?:[0-9]*\\.)?[0-9]+").matcher(e.getAttribute("d"));while(matcher.find())tokens.add(matcher.group());
        for(int i=0;i<tokens.size();){String command=tokens.get(i++);if(command.equals("Z")){path.closePath();continue;}int count=command.equals("C")?6:command.equals("Q")?4:2;double[] v=new double[count];for(int k=0;k<count;k++)v[k]=Double.parseDouble(tokens.get(i++));switch(command){case "M":path.moveTo(v[0],v[1]);break;case "L":path.lineTo(v[0],v[1]);break;case "Q":path.quadTo(v[0],v[1],v[2],v[3]);break;case "C":path.curveTo(v[0],v[1],v[2],v[3],v[4],v[5]);break;default:throw new IllegalArgumentException(command);}}
        return path;
    }
    static void draw(String name,int x,int y,int size,int color){
        int pixels=Math.max(32,Math.min(256,size*new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor()*2));String key=name+":"+pixels;DynamicTexture texture=textures.get(key);
        if(texture==null){texture=new DynamicTexture(rasterize(name,pixels));texture.setBlurMipmap(true,false);textures.put(key,texture);}
        boolean alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,771);GlStateManager.disableAlpha();GlStateManager.bindTexture(texture.getGlTextureId());GlStateManager.color((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,(color>>>24)/255F);
        Gui.drawScaledCustomSizeModalRect(x,y,0,0,pixels,pixels,size,size,pixels,pixels);GlStateManager.resetColor();if(alpha)GlStateManager.enableAlpha();
    }
}
