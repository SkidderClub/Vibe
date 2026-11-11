package dev.vibe.module.impl.visual;
import dev.vibe.features.*;
import dev.vibe.module.Category;
import dev.vibe.setting.*;
import net.minecraft.util.MovementInput;
public final class MovementPredictorModule extends FeatureModule {
    public final NumberSetting ticks=addSetting(new NumberSetting("Ticks",30,5,100,1));
    public final WorldStyle style=new WorldStyle(this::addSetting);private MovementForecast.Path path;
    public MovementPredictorModule(){super("Movement Predictor","Predict your held-input movement using Vibe's 1.8.9 physics",Category.VISUAL);}
    public void capture(MovementInput input){if(controlling())path=MovementForecast.simulate(input,ticks.getInt(),dev.vibe.module.impl.movement.MoveFixModule.movementYawHook(mc.thePlayer,mc.thePlayer.rotationYaw));else path=null;}
    @Override public void render(float partial){if(path!=null)WorldDraw.lines(path.points,style.color(0,1,0),style.width.getFloat(),style.glow.isEnabled());}
    @Override protected void onDisable(){path=null;}
}
