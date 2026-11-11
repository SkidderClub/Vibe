package dev.vibe.module.impl.meme;

import dev.vibe.features.FeatureModule;
import dev.vibe.module.Category;
import dev.vibe.setting.*;
import dev.vibe.companion.CompanionFollower;
import net.minecraft.util.*;

public final class PocketPetsModule extends FeatureModule {
    public final ModeSetting pet=addSetting(new ModeSetting("Pet","Ghost","Ghost","Rubber Duck"));
    public final NumberSetting distance=addSetting(new NumberSetting("Follow Distance",1.4,.7,3,.1));
    public final NumberSetting radius=addSetting(new NumberSetting("Stay Within",6,3,12,.5));
    public final NumberSetting speed=addSetting(new NumberSetting("Follow Speed",1,.5,1.5,.05));
    public final NumberSetting size=addSetting(new NumberSetting("Size",.65,.35,1.2,.05));
    public final BooleanSetting perch=addSetting(new BooleanSetting("Perch While Idle",false));
    public final ColorSetting ghostColor=addSetting(new ColorSetting("Ghost Color",0xFFDFE9F7,()->pet.is("Ghost")));
    public final ColorSetting duckColor=addSetting(new ColorSetting("Duck Color",0xFFFFD254,()->pet.is("Rubber Duck")));
    public final CompanionFollower follower=new CompanionFollower();
    private final dev.vibe.ui.render.PocketPetRenderer renderer=new dev.vibe.ui.render.PocketPetRenderer();
    private boolean perched;private double px,py,pz;private Object world;
    public PocketPetsModule(){super("Pocket Pets","Small collision-aware companions with their own idle behaviour",Category.MEME);}
    @Override public void tick(){if(!playing()){onDisable();return;}if(world!=mc.theWorld){follower.clear();world=mc.theWorld;}follower.tick(mc.theWorld,mc.thePlayer,distance.getDouble(),radius.getDouble(),speed.getDouble(),.24*size.getDouble());
        perched=false;if(perch.isEnabled()&&dev.vibe.features.UserActivity.idleSeconds()>5&&mc.thePlayer.onGround&&!mc.thePlayer.isSneaking()){
            double a=Math.toRadians(mc.thePlayer.renderYawOffset),s=size.getDouble();px=mc.thePlayer.posX+Math.cos(a)*.38;py=mc.thePlayer.posY+1.38;pz=mc.thePlayer.posZ+Math.sin(a)*.38;
            AxisAlignedBB b=new AxisAlignedBB(px-.18*s,py,pz-.18*s,px+.18*s,py+.5*s,pz+.18*s);if(mc.theWorld.isAreaLoaded(new BlockPos(b.minX,b.minY,b.minZ),new BlockPos(b.maxX,b.maxY,b.maxZ))&&mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,b).isEmpty())perched=true;
        }
    }
    public boolean perched(){return perched;}public Vec3 position(float partial){if(perched)return new Vec3(px,py,pz);return new Vec3(follower.previousX+(follower.x-follower.previousX)*partial,follower.previousY+(follower.y-follower.previousY)*partial,follower.previousZ+(follower.z-follower.previousZ)*partial);}
    @Override public void render(float partial){if(playing()&&follower.active)renderer.render(this,partial);}
    @Override protected void onDisable(){follower.clear();world=null;perched=false;}
}
