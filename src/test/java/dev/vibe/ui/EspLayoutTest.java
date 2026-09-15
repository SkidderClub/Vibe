package dev.vibe.ui;

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
