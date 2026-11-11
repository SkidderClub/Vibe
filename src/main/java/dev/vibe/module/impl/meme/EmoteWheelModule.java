package dev.vibe.module.impl.meme;

import dev.vibe.features.*;
import dev.vibe.module.Category;
import dev.vibe.setting.BooleanSetting;
import net.minecraft.util.MovementInput;

/** Entirely local poses. The player's real stance, camera and collision box are unchanged. */
public final class EmoteWheelModule extends FeatureModule {
    public final BooleanSetting idleSit=addSetting(new BooleanSetting("Sit While AFK",true));
    public final BooleanSetting open=addSetting(new BooleanSetting("Open Wheel",false).onEnable(()->{openWheel();}));
    private String selected="Stop";private double began;private float sit,dab;private Object world;private long selectedActivity;
    public EmoteWheelModule(){super("Emote Wheel","Local third-person emotes and an optional idle sit",Category.MEME);}
    public void openWheel(){open.setEnabled(false);if(mc.thePlayer!=null){if(!isEnabled())setEnabled(true);mc.displayGuiScreen(new dev.vibe.ui.screen.EmoteWheelGui(this));}}
    public void select(String value){selected=value;began=System.nanoTime()/1E9;selectedActivity=UserActivity.version();}
    public String selected(){return selected;}
    @Override protected void onEnable(){world=mc.theWorld;selected="Stop";sit=dab=0;UserActivity.mark();}
    public float sit(){return sit;}public float dab(){return dab;}
    @Override public void tick(){
        if(!playing()||world!=mc.theWorld){selected="Stop";sit=dab=0;world=mc.theWorld;UserActivity.mark();if(!playing())return;}
        boolean moving=dev.vibe.input.VanillaClicks.physicallyDown(mc.gameSettings.keyBindForward)||dev.vibe.input.VanillaClicks.physicallyDown(mc.gameSettings.keyBindBack)||dev.vibe.input.VanillaClicks.physicallyDown(mc.gameSettings.keyBindLeft)||dev.vibe.input.VanillaClicks.physicallyDown(mc.gameSettings.keyBindRight)||dev.vibe.input.VanillaClicks.physicallyDown(mc.gameSettings.keyBindJump);
        if(moving){UserActivity.mark();selected="Stop";sit=0;}
        boolean travelling=Math.hypot(mc.thePlayer.posX-mc.thePlayer.lastTickPosX,mc.thePlayer.posZ-mc.thePlayer.lastTickPosZ)>.015;
        if(travelling){selected="Stop";sit=0;}
        if(UserActivity.version()!=selectedActivity&&"Sit".equals(selected)){selected="Stop";sit=0;}
        if("Dab".equals(selected)&&System.nanoTime()/1E9-began>=2)selected="Stop";
        boolean sitting=!travelling&&mc.thePlayer.onGround&&!mc.thePlayer.isRiding()&&("Sit".equals(selected)||(idleSit.isEnabled()&&UserActivity.idleSeconds()>=120));
        sit+=( (sitting?1:0)-sit)*.3F;dab+=( ("Dab".equals(selected)?1:0)-dab)*.4F;
    }
    @Override protected void onDisable(){selected="Stop";sit=dab=0;}
}
