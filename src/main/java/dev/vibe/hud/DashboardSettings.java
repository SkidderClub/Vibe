package dev.vibe.hud;

import dev.vibe.setting.*;
import java.util.function.Function;

/** Independent controls, registered in the normal profile and HUD inspector. */
public final class DashboardSettings extends OverlaySettings {
    public final NumberSetting width,height,range,zoom,markerSize,lifetime,rows,flameStrength;
    public final BooleanSetting rotate,sweep,players,items,avatars,weapons,jitter,stalls;
    public final ModeSetting mode,markers;
    public final ColorSetting friends,targets,hostile,flame;
    public final BooleanSetting bookmark,history;
    public DashboardSettings(Function<Setting<?>,Setting<?>> r,String id){super(r,id);
        boolean radar=id.equals("Radar"),network=id.equals("Network Graph"),candle=id.equals("Candle");
        width=add(new NumberSetting(id+" Width",radar?144:210,radar?96:120,radar?260:400,1,()->!candle));
        height=add(new NumberSetting(id+" Height",network?74:144,48,260,1,()->radar||network));
        mode=add(new ModeSetting(id+" View","Radar",()->radar,"Radar","Map"));
        range=add(new NumberSetting(id+" Range",48,8,128,1,()->radar));zoom=add(new NumberSetting(id+" Zoom",1,.5,4,.1,()->radar));
        rotate=add(new BooleanSetting(id+" Rotate",true,()->radar));sweep=add(new BooleanSetting(id+" Sweep",true,()->radar&&mode.is("Radar")));
        players=add(new BooleanSetting(id+" Players",true,()->radar));items=add(new BooleanSetting(id+" Dropped Items",false,()->radar));
        markers=add(new ModeSetting(id+" Markers","Arrow",()->radar,"Dot","Arrow","Square","Head"));markerSize=add(new NumberSetting(id+" Marker Size",4,2,10,.5,()->radar));
        friends=add(new ColorSetting(id+" Friend Color",0xFF79E6A8,()->radar));targets=add(new ColorSetting(id+" Target Color",0xFFFF6980,()->radar));hostile=add(new ColorSetting(id+" Player Color",0xFFE7EAF4,()->radar));
        avatars=add(new BooleanSetting(id+" Avatars",true,()->id.equals("Kill Feed")));weapons=add(new BooleanSetting(id+" Weapon Icons",true,()->id.equals("Kill Feed")));
        lifetime=add(new NumberSetting(id+" Lifetime",8,2,30,.5,()->id.equals("Kill Feed")||id.equals("Session Timeline")));rows=add(new NumberSetting(id+" Rows",4,1,8,1,()->id.equals("Kill Feed")||id.equals("Session Timeline")));
        jitter=add(new BooleanSetting(id+" Jitter",true,()->network));stalls=add(new BooleanSetting(id+" Stall Markers",true,()->network));
        flameStrength=add(new NumberSetting(id+" Flame Motion",1,0,3,.1,()->candle));flame=add(new ColorSetting(id+" Flame Color",0xFFFFB853,()->candle));
        bookmark=add(new BooleanSetting(id+" Add Bookmark",false,()->id.equals("Session Timeline")).onEnable(()->{mark();}));
        history=add(new BooleanSetting(id+" Open History",false,()->id.equals("Session Timeline")).onEnable(()->{open();}));
    }
    private boolean loading(){dev.vibe.Vibe v=dev.vibe.Vibe.getInstance();return v!=null&&v.getConfig()!=null&&v.getConfig().isLoading();}
    private void mark(){bookmark.setEnabled(false);if(!loading())SessionJournal.INSTANCE.bookmark();}
    private void open(){history.setEnabled(false);if(!loading())net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new dev.vibe.ui.screen.SessionTimelineGui(net.minecraft.client.Minecraft.getMinecraft().currentScreen));}
}
