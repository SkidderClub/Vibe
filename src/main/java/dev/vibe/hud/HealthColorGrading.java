package dev.vibe.hud;
import dev.vibe.setting.*;
import java.util.*;
import java.util.function.Function;
public final class HealthColorGrading {
    public final List<Setting<?>> all=new ArrayList<>();public final BooleanSetting enabled;public final NumberSetting exposure,contrast,temperature;public final ModeSetting vision,preview;
    public HealthColorGrading(Function<Setting<?>,Setting<?>> add){enabled=add(add,new BooleanSetting("Health Color Grading",false));exposure=add(add,new NumberSetting("Health Exposure",0,-2,2,.05,enabled::isEnabled));contrast=add(add,new NumberSetting("Health Contrast",1,.25,2,.05,enabled::isEnabled));temperature=add(add,new NumberSetting("Health Temperature",0,-1,1,.05,enabled::isEnabled));vision=add(add,new ModeSetting("Health Color Vision","Normal",enabled::isEnabled,"Normal","Deuteranopia","Protanopia","Tritanopia","Monochrome"));preview=add(add,new ModeSetting("Health Editor Preview","Graded",enabled::isEnabled,"Graded","Original"));}
    private <T extends Setting<?>> T add(Function<Setting<?>,Setting<?>> register,T setting){register.apply(setting);all.add(setting);return setting;}
    public int visionIndex(){return vision.getModes().indexOf(vision.getValue());}
}
