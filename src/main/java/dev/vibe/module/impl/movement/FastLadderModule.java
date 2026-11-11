package dev.vibe.module.impl.movement;
import dev.vibe.features.*;
import dev.vibe.module.Category;
import dev.vibe.setting.*;
import net.minecraft.util.MovementInput;
public final class FastLadderModule extends FeatureModule {
    public final ModeSetting mode=addSetting(new ModeSetting("Mode","Motion","Motion","Pulse"));
    public final NumberSetting up=addSetting(new NumberSetting("Up Speed",.35,.2,1,.05)),down=addSetting(new NumberSetting("Down Speed",.35,.15,1,.05)),interval=addSetting(new NumberSetting("Pulse Interval",2,1,5,1,()->mode.is("Pulse")));
    public FastLadderModule(){super("Fast Ladder","Motion: smooth ladder speed; Pulse: spaced collision-tested ladder steps",Category.MOVEMENT);}
    @Override public void input(MovementInput input){if(!controlling()||!mc.thePlayer.isOnLadder()||mode.is("Pulse")&&mc.thePlayer.ticksExisted%interval.getInt()!=0)return;
        boolean descending=input.sneak||input.moveForward<0;boolean ascending=!descending&&(input.moveForward>0||input.jump);if(!descending&&!ascending)return;
        double amount=(descending?-Math.max(0,down.getDouble()-(input.sneak?0:.15)):Math.max(0,up.getDouble()-.2))*(mode.is("Pulse")?interval.getInt():1);
        if(MovementForecast.loaded(mc.thePlayer.getEntityBoundingBox().addCoord(0,amount,0)))mc.thePlayer.moveEntity(0,amount,0);
    }
}
