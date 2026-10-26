package dev.vibe.ui.clickgui;

import dev.vibe.module.impl.client.ClickGuiModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import dev.vibe.ui.GuiInput;
import dev.vibe.ui.ScrollbarDrag;
import java.lang.reflect.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SliderRoutingTest {
    @Test public void skeetAndFuturisticWheelsNeverChangeNumericSettings() throws Exception {
        try(PathTestFixture f=new PathTestFixture()) {
            ClickGuiModule theme=new ClickGuiModule();f.modules.registerDynamic(theme);
            for(String name:new String[]{"Skeet","Futuristic"}) {
                theme.getTheme().setValue(name);VibeClickGui gui=gui();
                NumberSetting number=new NumberSetting("Value",5,0,10,.5);
                addTarget(gui,"NUMBER",number);gui.wheel(220,90,-120);assertEquals(5,number.getDouble(),0);
                if(name.equals("Skeet"))assertEquals(122,field("skeetScroll").getInt(gui));
                ((List<?>)field("targets").get(gui)).clear();RangeSetting range=new RangeSetting("Range",2,6,0,10,1);
                addTarget(gui,"RANGE",range);gui.wheel(190,90,120);assertEquals(2,range.getMin(),0);
                gui.wheel(270,90,120);assertEquals(6,range.getMax(),0);
            }
        }
    }
    @Test public void skeetScrollbarHasARealThumbDragHandler() throws Exception {
        try(PathTestFixture f=new PathTestFixture()) {
            VibeClickGui gui=gui();GuiInput.mouseClicked(gui,441,110,0);
            ScrollbarDrag dragging=(ScrollbarDrag)field("skeetScrollbar").get(gui);
            assertNotNull(dragging);assertEquals(300,dragging.at(1000));
            GuiInput.mouseReleased(gui,441,110,0);assertNull(field("skeetScrollbar").get(gui));
        }
    }
    private static VibeClickGui gui() throws Exception {
        VibeClickGui gui=new VibeClickGui();gui.width=960;gui.height=540;
        field("skeetX").setInt(gui,100);field("skeetY").setInt(gui,50);field("skeetWidth").setInt(gui,350);field("skeetHeight").setInt(gui,300);
        field("skeetScroll").setInt(gui,100);field("skeetMaxScroll").setInt(gui,300);return gui;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void addTarget(VibeClickGui gui,String kind,Setting<?> setting) throws Exception {
        Class<?> type=Class.forName(VibeClickGui.class.getName()+"$Target"),enumeration=Class.forName(VibeClickGui.class.getName()+"$TargetType");
        Constructor<?> ctor=type.getDeclaredConstructor(enumeration,int.class,int.class,int.class,int.class,dev.vibe.module.Module.class,Setting.class,int.class);ctor.setAccessible(true);
        ((List)field("targets").get(gui)).add(ctor.newInstance(Enum.valueOf((Class)enumeration,kind),180,80,280,100,null,setting,-1));
    }
    private static Field field(String name) throws Exception {Field field=VibeClickGui.class.getDeclaredField(name);field.setAccessible(true);return field;}
}
