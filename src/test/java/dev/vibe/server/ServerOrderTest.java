package dev.vibe.server;

import dev.vibe.ui.server.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ServerOrderTest {
    @Test public void draggingInsertsWithoutLosingOrDuplicatingServers() {
        List<String> original = Arrays.asList("a", "b", "c", "d");
        assertEquals(Arrays.asList("b", "c", "a", "d"), ServerOrder.before(original, "a", "d"));
        assertEquals(Arrays.asList("d", "a", "b", "c"), ServerOrder.before(original, "d", "a"));
        assertEquals(Arrays.asList("a", "c", "d", "b"), ServerOrder.before(original, "b", null));
        assertEquals(Arrays.asList("a", "b", "c", "d"), original);
    }
    @Test public void filteredMovesPreserveHiddenRelativeOrder() {
        List<String> original = Arrays.asList("visibleA", "hiddenA", "visibleB", "hiddenB", "visibleC");
        assertEquals(Arrays.asList("hiddenA", "visibleB", "hiddenB", "visibleA", "visibleC"), ServerOrder.before(original, "visibleA", "visibleC"));
    }
    @Test public void responsiveLayoutFitsAllSupportedSizes() {
        for (int[] size : new int[][] {{320,240},{427,240},{480,270},{640,360},{854,480},{960,540},{1280,720},{1920,1080}}) {
            ServerBrowserLayout l = new ServerBrowserLayout(size[0], size[1]);
            assertTrue(l.x >= 0 && l.y >= 0 && l.w > 250 && l.h > 180);
            assertTrue(l.listW > 250 && l.listH >= 50);
            assertEquals(l.w-32,l.listW);
            assertTrue(l.listY + l.listH < l.footer);
            assertTrue(l.footer + (l.compact ? 58 : 28) <= l.y + l.h);
            assertEquals(0, l.maxScroll(0)); assertTrue(l.maxScroll(50) > 0);
        }
    }
    @Test public void aLargeListVisitsOnlyTheViewport(){
        ServerBrowserLayout l=new ServerBrowserLayout(960,540);
        for(float scroll:new float[]{0,100,7100,50000}){
            int first=l.firstVisible(scroll),end=l.lastVisible(1000,scroll);
            assertTrue(end-first<12);assertTrue(first>=0&&end<=1000);
        }
    }
    @Test public void footerUsesVanillaOrderAndCompactProportions(){
        for(int[] size:new int[][]{{320,240},{480,270},{960,540},{1280,720}}){
            ServerBrowserLayout l=new ServerBrowserLayout(size[0],size[1]);ServerBrowserLayout.FooterButton[] buttons=l.footerButtons();
            int[] expected={4,2,1,5,6,3,0};String[] labels={"Join Server","Direct Connect","Add Server","Edit","Delete","Refresh","Cancel"};
            for(int i=0;i<buttons.length;i++){
                ServerBrowserLayout.FooterButton b=buttons[i];assertEquals(expected[i],b.id);assertEquals(labels[i],b.label);
                assertEquals(l.footer+(i<3?0:24),b.y);assertTrue(b.x>=l.x&&b.x+b.w<=l.x+l.w);
                if(i>0&&i!=3)assertTrue(buttons[i-1].x+buttons[i-1].w<b.x);
            }
            assertEquals(28,l.icon);assertTrue(l.row<=52);
        }
    }
}
