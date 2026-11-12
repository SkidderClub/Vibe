package dev.vibe.module.impl.meme;

import dev.vibe.features.*;
import dev.vibe.module.Category;
import dev.vibe.setting.BooleanSetting;
import net.minecraft.util.MovementInput;

/** Entirely local poses. The player's real stance, camera and collision box are unchanged. */
public final class EmoteWheelModule extends FeatureModule {
    public final BooleanSetting idleSit=addSetting(new BooleanSetting("Sit While AFK",true));
    public final BooleanSetting open=addSetting(new BooleanSetting("Open Wheel",false).onEnable(()->{openWheel();}));
    public static final String[] EMOTES={"Dab","Sit","Wave","Cheer","Dance","Bow","Facepalm","Stop"};
    private String selected="Stop";private double began;private float sit,dab;private Object world;
    private final java.util.Map<String,Float> weights=new java.util.HashMap<>();
    public EmoteWheelModule(){super("Emote Wheel","Local third-person emotes and an optional idle sit",Category.MEME);}
    public void openWheel(){open.setEnabled(false);if(mc.thePlayer!=null){if(!isEnabled())setEnabled(true);mc.displayGuiScreen(new dev.vibe.ui.screen.EmoteWheelGui(this));}}
    public void select(String value){if(!java.util.Arrays.asList(EMOTES).contains(value))return;selected=value;began=System.nanoTime()/1E9;}
    public String selected(){return selected;}
    @Override protected void onEnable(){world=mc.theWorld;selected="Stop";sit=dab=0;weights.clear();UserActivity.mark();}
    public float sit(){return sit;}public float dab(){return dab;}
    public float weight(String name){return weights.getOrDefault(name,0F);}
    public double elapsed(){return System.nanoTime()/1E9-began;}
    @Override public void tick(){
        if(!playing()||world!=mc.theWorld){selected="Stop";sit=dab=0;weights.clear();world=mc.theWorld;UserActivity.mark();if(!playing())return;}
        boolean moving=dev.vibe.input.VanillaClicks.physicallyDown(mc.gameSettings.keyBindForward)||dev.vibe.input.VanillaClicks.physicallyDown(mc.gameSettings.keyBindBack)||dev.vibe.input.VanillaClicks.physicallyDown(mc.gameSettings.keyBindLeft)||dev.vibe.input.VanillaClicks.physicallyDown(mc.gameSettings.keyBindRight)||dev.vibe.input.VanillaClicks.physicallyDown(mc.gameSettings.keyBindJump);
        if(moving){UserActivity.mark();if("Sit".equals(selected))selected="Stop";}
        boolean travelling=Math.hypot(mc.thePlayer.posX-mc.thePlayer.lastTickPosX,mc.thePlayer.posZ-mc.thePlayer.lastTickPosZ)>.015;
        if(!"Sit".equals(selected)&&!"Stop".equals(selected)&&elapsed()>=("Dance".equals(selected)?5:"Wave".equals(selected)||"Cheer".equals(selected)?3:2))selected="Stop";
        // AFK is an entry condition, not an exit condition. Looking around and
        // server knockback must not cancel an already selected sitting pose.
        if("Stop".equals(selected)&&!moving&&!travelling&&mc.thePlayer.onGround&&idleSit.isEnabled()&&UserActivity.idleSeconds()>=120)select("Sit");
        boolean sitting="Sit".equals(selected)&&!mc.thePlayer.isRiding();
        sit+=( (sitting?1:0)-sit)*.3F;dab+=( ("Dab".equals(selected)?1:0)-dab)*.4F;
        for(String name:EMOTES)weights.put(name,weight(name)+((selected.equals(name)?1:0)-weight(name))*.3F);
    }
    @Override protected void onDisable(){selected="Stop";sit=dab=0;weights.clear();}
}
