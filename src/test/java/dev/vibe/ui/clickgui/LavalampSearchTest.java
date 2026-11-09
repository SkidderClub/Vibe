package dev.vibe.ui.clickgui;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LavalampSearchTest {
    @Test public void globalSearchPrioritizesModuleNamesOverSettings(){
        Fixture setting=new Fixture("Aura",Category.COMBAT,"Glow strength"),name=new Fixture("Glow",Category.VISUAL,"Colour"),other=new Fixture("Phone",Category.MEME,"Ringtone");
        List<Module> modules=Arrays.asList(setting,other,name);
        assertEquals(Arrays.asList(name,setting),LavalampWorkspace.visibleModules(modules,Category.MOVEMENT,"gLoW"));
        assertEquals(Collections.singletonList(setting),LavalampWorkspace.visibleModules(modules,Category.COMBAT,"  "));
        assertEquals(Collections.singletonList(other),LavalampWorkspace.visibleModules(modules,Category.COMBAT,"ringtone"));
        assertTrue(LavalampWorkspace.visibleModules(modules,Category.COMBAT,"nothing matches").isEmpty());
    }
    private static final class Fixture extends Module{Fixture(String name,Category category,String setting){super(name,"Search fixture",category,0);addSetting(new BooleanSetting(setting,true));}}
}
