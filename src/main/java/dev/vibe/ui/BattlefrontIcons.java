package dev.vibe.ui;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.w3c.dom.*;

/** Renders the bundled SVG symbol sheet; supports only the primitives used by our artwork. */
public final class BattlefrontIcons {
    private static final int RESOLUTION=128;
    private static Document symbols;
    private final Map<String,DynamicTexture> textures=new HashMap<String,DynamicTexture>();
    private static synchronized Document symbols()throws Exception{
        if(symbols==null){
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
            try(InputStream in=BattlefrontIcons.class.getResourceAsStream("/assets/vibe/battlefront/command-icons.svg")){
                if(in==null)throw new IllegalStateException("Missing command deck SVG");symbols=factory.newDocumentBuilder().parse(in);
            }
        }return symbols;
    }
    public static BufferedImage rasterize(String name){
        try{
            Element symbol=null;NodeList list=symbols().getElementsByTagName("symbol");for(int i=0;i<list.getLength();i++){Element e=(Element)list.item(i);if(name.equals(e.getAttribute("id"))){symbol=e;break;}}
            if(symbol==null)throw new IllegalArgumentException("Unknown Battlefront icon: "+name);
            BufferedImage image=new BufferedImage(RESOLUTION,RESOLUTION,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
            try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);g.scale(RESOLUTION/24.0,RESOLUTION/24.0);g.setColor(Color.WHITE);g.setStroke(new BasicStroke(1.65f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                NodeList shapes=symbol.getChildNodes();for(int i=0;i<shapes.getLength();i++)if(shapes.item(i) instanceof Element){Element e=(Element)shapes.item(i);Shape shape=shape(e);if(shape!=null)g.draw(shape);}
            }finally{g.dispose();}return image;
        }catch(RuntimeException ex){throw ex;}catch(Exception ex){throw new IllegalStateException("Cannot load command deck SVG",ex);}
    }
    private static double n(Element e,String name){return e.hasAttribute(name)?Double.parseDouble(e.getAttribute(name)):0;}
    private static Shape shape(Element e){
        String tag=e.getTagName();
        if(tag.equals("line"))return new Line2D.Double(n(e,"x1"),n(e,"y1"),n(e,"x2"),n(e,"y2"));
        if(tag.equals("circle")){double r=n(e,"r");return new Ellipse2D.Double(n(e,"cx")-r,n(e,"cy")-r,2*r,2*r);}
        if(tag.equals("ellipse"))return new Ellipse2D.Double(n(e,"cx")-n(e,"rx"),n(e,"cy")-n(e,"ry"),2*n(e,"rx"),2*n(e,"ry"));
        if(tag.equals("rect"))return new RoundRectangle2D.Double(n(e,"x"),n(e,"y"),n(e,"width"),n(e,"height"),2*n(e,"rx"),2*n(e,"rx"));
        if(tag.equals("polyline")||tag.equals("polygon")){String[] points=e.getAttribute("points").trim().split("[ ,]+");Path2D path=new Path2D.Double();for(int i=0;i<points.length;i+=2){double x=Double.parseDouble(points[i]),y=Double.parseDouble(points[i+1]);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}if(tag.equals("polygon"))path.closePath();return path;}
        throw new IllegalArgumentException("Unsupported authored SVG primitive: "+tag);
    }
    public void draw(String name,int x,int y,int size,int color){
        int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);DynamicTexture texture=textures.get(name);
        if(texture==null){texture=new DynamicTexture(rasterize(name));textures.put(name,texture);}
        GlStateManager.enableTexture2D();GlStateManager.bindTexture(texture.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GlStateManager.color((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,(color>>>24)/255f);
        Gui.drawScaledCustomSizeModalRect(x,y,0,0,RESOLUTION,RESOLUTION,size,size,RESOLUTION,RESOLUTION);
        GlStateManager.color(1,1,1,1);GlStateManager.bindTexture(previous);
    }
    public void close(){for(DynamicTexture texture:textures.values())texture.deleteGlTexture();textures.clear();}
}
