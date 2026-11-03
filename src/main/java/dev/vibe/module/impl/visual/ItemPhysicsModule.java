package dev.vibe.module.impl.visual;
import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.setting.*;
import java.util.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/** Cosmetic dropped-item tumbling and settling. Never changes an entity's actual position or velocity. */
public final class ItemPhysicsModule extends dev.vibe.module.Module {
    public final NumberSetting rotationSpeed=addSetting(new NumberSetting("Rotation Speed",1,0,3,.05));
    public final NumberSetting settleSpeed=addSetting(new NumberSetting("Settling Speed",12,2,30,1));
    public final BooleanSetting airSpin=addSetting(new BooleanSetting("Spin In Air",true));
    private final Map<EntityItem,Pose> poses=new WeakHashMap<>();
    public ItemPhysicsModule(){super("Item Physics","Dropped items tumble in the air and settle flat on the ground",Category.VISUAL,Keyboard.KEY_NONE);}
    @Override protected void onDisable(){poses.clear();}
    public static int transform(Object entity,double x,double y,double z,float partial,Object model){
        Vibe v=Vibe.getInstance();if(v==null||v.getModuleManager()==null||!(entity instanceof EntityItem)||!(model instanceof IBakedModel))return Integer.MIN_VALUE;
        ItemPhysicsModule m=v.getModuleManager().getModule(ItemPhysicsModule.class);if(m==null||!m.isEnabled())return Integer.MIN_VALUE;
        EntityItem item=(EntityItem)entity;ItemStack stack=item.getEntityItem();if(stack==null||stack.getItem()==null)return 0;
        IBakedModel baked=(IBakedModel)model;int count=stack.stackSize>48?5:stack.stackSize>32?4:stack.stackSize>16?3:stack.stackSize>1?2:1;
        float groundScale=baked.getItemCameraTransforms().getTransform(ItemCameraTransforms.TransformType.GROUND).scale.y;
        double time=item.getAge()+partial,phase=item.hoverStart*57.2957795,spin=m.airSpin.isEnabled()?time*m.rotationSpeed.getDouble()*12:0;
        Pose pose=m.poses.get(item);long now=System.nanoTime();if(pose==null){pose=new Pose();pose.pitch=item.onGround?90:(float)(spin+phase);pose.at=now;m.poses.put(item,pose);}
        float amount=1-(float)Math.exp(-Math.min(.1,(now-pose.at)/1e9)*m.settleSpeed.getDouble());pose.at=now;
        if(item.onGround){float target=90+360*Math.round((pose.pitch-90)/360);pose.pitch+=(target-pose.pitch)*amount;}else pose.pitch=(float)(spin+phase);
        GlStateManager.translate((float)x,(float)y+.25F*groundScale,(float)z);
        GlStateManager.rotate((float)phase,0,1,0);GlStateManager.rotate(pose.pitch,1,0,0);
        if(!baked.isGui3d())GlStateManager.translate(0,0,-.046875F*(count-1)*.5F);
        GlStateManager.color(1,1,1,1);return count;
    }
    private static final class Pose{float pitch;long at;}
}
