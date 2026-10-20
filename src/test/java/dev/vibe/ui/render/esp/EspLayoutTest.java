package dev.vibe.ui.render.esp;

import dev.vibe.module.impl.visual.Esp2DSettings;
import dev.vibe.module.impl.visual.EspModule;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EspLayoutTest {
    @Test public void largeTagsBarsAndOutlinesNeverIntersect() {
        Random random=new Random(241);
        String[] sides={"Left","Top","Bottom","Right","Left Up","Left Down","Right Up","Right Down"};
        for(int sample=0;sample<500;sample++) {
            EspLayout.Rect box=new EspLayout.Rect(300,200,10+random.nextFloat()*150,10+random.nextFloat()*250);
            List<EspLayout.Request> requests=new ArrayList<EspLayout.Request>();
            for(int i=0;i<9;i++)requests.add(new EspLayout.Request(""+i,sides[random.nextInt(sides.length)],5+random.nextFloat()*180,2+random.nextFloat()*140,i,random.nextFloat()*400-200,2));
            Map<String,EspLayout.Rect> result=EspLayout.arrange(box,requests,3);
            List<EspLayout.Rect> all=new ArrayList<EspLayout.Rect>();all.add(box);
            for(EspLayout.Rect rect:result.values()) {
                for(EspLayout.Rect other:all)assertFalse("Overlapping elements at sample "+sample,rect.expand(2).overlaps(other,2.99F));
                all.add(rect.expand(2));
            }
        }
    }
    @Test public void orderControlsStacksAndSideTagsGrowInwardVertically() {
        EspLayout.Rect box=new EspLayout.Rect(100,100,60,180);
        List<EspLayout.Request> requests=Arrays.asList(new EspLayout.Request("name","Left Up",40,10,2,0,0),new EspLayout.Request("distance","Left Up",40,10,1,0,0));
        Map<String,EspLayout.Rect> placed=EspLayout.arrange(box,requests,3);
        assertEquals(placed.get("name").x,placed.get("distance").x,.001);
        assertEquals(113,placed.get("name").y,.001);
        assertEquals(100,placed.get("distance").y,.001);
    }
    @Test public void barsSpanTheDrawnBoxIncludingOutlines() {
        Esp2DSettings s=new EspModule().get2D();
        for(Esp2DSettings.Element e:s.elements)e.enabled.setValue(false);
        s.distanceScaling.setValue(0D);s.box.enabled.setValue(true);s.box.width.setValue(2D);s.box.outline.setValue(true);s.box.outlineWidth.setValue(1D);
        s.healthBar.enabled.setValue(true);s.healthBar.outline.setValue(true);s.healthBar.outlineWidth.setValue(1.5D);
        Esp2DRenderer.Actor actor=new Esp2DRenderer.Actor("Player",10,20,0,12,"",null,null,1);
        EspLayout.Rect box=new EspLayout.Rect(300,180,100,180);
        // Half the 2px stroke plus the 1px outline is drawn outside the projected rectangle.
        EspLayout.Rect drawn=box.expand(2);
        for(String side:new String[]{"Left","Right","Top","Bottom"}) {
            s.healthBar.position.setValue(side);
            EspLayout.Rect bar=new Esp2DRenderer().measure(s,actor,box).elements.get("Health Bar").expand(1.5F);
            boolean vertical=side.equals("Left")||side.equals("Right");
            assertEquals(side,vertical?drawn.y:drawn.x,vertical?bar.y:bar.x,.0001);
            assertEquals(side,vertical?drawn.bottom():drawn.right(),vertical?bar.bottom():bar.right(),.0001);
        }
        s.box.enabled.setValue(false);s.healthBar.outline.setValue(false);s.healthBar.position.setValue("Left");
        EspLayout.Rect plain=new Esp2DRenderer().measure(s,actor,box).elements.get("Health Bar");
        assertEquals(box.y,plain.y,.0001);assertEquals(box.bottom(),plain.bottom(),.0001);
    }
    @Test public void allAnchorsAndSmoothScaling() {
        EspLayout.Rect box=new EspLayout.Rect(100,100,60,180);
        assertEquals("Top",EspLayout.snap(box,130,90,true));
        assertEquals("Bottom",EspLayout.snap(box,130,300,true));
        assertEquals("Left Up",EspLayout.snap(box,90,120,false));
        assertEquals("Right Down",EspLayout.snap(box,170,260,false));
        for(float height=10;height<400;height+=.25F){float a=EspLayout.scale(height,.6F),b=EspLayout.scale(height+.01F,.6F);assertTrue(b>=a);assertTrue(b-a<.001);}
        assertEquals(1,EspLayout.scale(10,0),.0001);
    }
}
