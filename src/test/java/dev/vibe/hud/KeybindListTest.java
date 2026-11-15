package dev.vibe.hud;
import dev.vibe.path.PathTestFixture;
import dev.vibe.module.Module;
import dev.vibe.module.Category;
import dev.vibe.module.impl.client.HudModule;
import org.junit.Test;
import static org.junit.Assert.*;
public class KeybindListTest {
    @Test public void minimapRadiusMaskCullsCornersAndPreservesTheCenter(){
        assertTrue(DashboardWidgets.insideMap(2,2,0,0,100,100,0,0));
        assertFalse(DashboardWidgets.insideMap(2,2,0,0,100,100,50,0));assertTrue(DashboardWidgets.insideMap(50,50,0,0,100,100,50,4));
    }
    @Test public void widgetAndSettingsAreRegisteredAndFilteringUsesBoundConfiguredModules()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            HudModule hud=new HudModule();f.modules.registerDynamic(hud);assertTrue(hud.getHudElements().hasOption("keybindlist"));
            DashboardSettings settings=hud.dashboards.get("keybindlist");assertNotNull(settings);assertTrue(settings.onlyEnabled.isEnabled());
            Module a=new Module("Bound","",Category.MOVEMENT,35){},b=new Module("Disabled","",Category.CLIENT,36){},unbound=new Module("Unbound","",Category.CLIENT,0){};
            f.modules.registerDynamic(a);f.modules.registerDynamic(b);f.modules.registerDynamic(unbound);a.setEnabled(true);unbound.setEnabled(true);
            assertEquals(1,DashboardWidgets.binds(settings).size());a.suspend(true);assertEquals(1,DashboardWidgets.binds(settings).size());
            settings.onlyEnabled.setEnabled(false);assertEquals(2,DashboardWidgets.binds(settings).size());
            assertEquals(15,hud.dashboards.get("radar").mapCorners.getInt());
        }
    }
}
