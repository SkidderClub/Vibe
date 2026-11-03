package dev.vibe.hud;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.DocumentBuilderFactory;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.GlStateManager;
import org.w3c.dom.*;
import org.lwjgl.opengl.GL11;
/** Rasterises the three bundled LiquidBounce SVG paths once, preserving their original colours. */
final class TargetHudIcons{
    private static final Map<String,DynamicTexture> CACHE=new HashMap<>();
    static void draw(String name,float x,float y,float w,float h){
        DynamicTexture texture=CACHE.get(name);if(texture==null){texture=new DynamicTexture(load(name));texture.setBlurMipmap(true,false);CACHE.put(name,texture);}
        GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,771);GlStateManager.disableAlpha();GlStateManager.bindTexture(texture.getGlTextureId());GlStateManager.color(1,1,1,1);
        GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(0,0);GL11.glVertex2f(x,y);GL11.glTexCoord2f(0,1);GL11.glVertex2f(x,y+h);GL11.glTexCoord2f(1,1);GL11.glVertex2f(x+w,y+h);GL11.glTexCoord2f(1,0);GL11.glVertex2f(x+w,y);GL11.glEnd();GlStateManager.enableAlpha();
    }
    private static BufferedImage load(String name){
        try(InputStream in=TargetHudIcons.class.getResourceAsStream("/assets/vibe/targethud/liquidbounce/"+name+".svg")){
            DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.setXIncludeAware(false);f.setExpandEntityReferences(false);
            Element root=f.newDocumentBuilder().parse(in).getDocumentElement();String[] box=root.getAttribute("viewBox").split(" +");BufferedImage image=new BufferedImage(72,72,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.scale(72/Double.parseDouble(box[2]),72/Double.parseDouble(box[3]));NodeList paths=root.getElementsByTagName("path");
            for(int k=0;k<paths.getLength();k++){Element e=(Element)paths.item(k);Path2D path=new Path2D.Double();java.util.List<String> tokens=new ArrayList<>();Matcher match=Pattern.compile("[MLCHVZ]|[-+]?(?:[0-9]*\\.)?[0-9]+(?:[eE][-+]?[0-9]+)?").matcher(e.getAttribute("d"));while(match.find())tokens.add(match.group());
                String command="M";for(int i=0;i<tokens.size();){if(Character.isLetter(tokens.get(i).charAt(0)))command=tokens.get(i++);if(command.equals("Z")){path.closePath();continue;}
                    int count=command.equals("C")?6:command.equals("H")||command.equals("V")?1:2;double[] v=new double[count];for(int j=0;j<count;j++)v[j]=Double.parseDouble(tokens.get(i++));
                    if(command.equals("M")){path.moveTo(v[0],v[1]);command="L";}else if(command.equals("L"))path.lineTo(v[0],v[1]);else if(command.equals("H"))path.lineTo(v[0],path.getCurrentPoint().getY());else if(command.equals("V"))path.lineTo(path.getCurrentPoint().getX(),v[0]);else path.curveTo(v[0],v[1],v[2],v[3],v[4],v[5]);}
                g.setColor(Color.decode(e.getAttribute("fill")));g.fill(path);
            }g.dispose();return image;
        }catch(Exception error){throw new IllegalStateException("Cannot load TargetHUD icon "+name,error);}
    }
}
