package dev.vibe.module.impl.visual;
import dev.vibe.features.*;
import dev.vibe.module.Category;
import dev.vibe.setting.*;
import dev.vibe.chat.ClientChat;
import dev.vibe.feedback.ModuleFeedback;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.*;
public final class BeverageNotificationModule extends FeatureModule {
    public final BooleanSetting chat=addSetting(new BooleanSetting("Chat Message",true)),notification=addSetting(new BooleanSetting("Notification",true)),gapple=addSetting(new BooleanSetting("Gapple",true));
    private final Map<UUID,String> active=new HashMap<>();private Object world;
    public BeverageNotificationModule(){super("Beverage Notification","Notify when another player drinks a potion or eats a golden apple",Category.VISUAL);}
    @Override public void tick(){if(!playing()){active.clear();return;}if(world!=mc.theWorld){active.clear();world=mc.theWorld;}Set<UUID> present=new HashSet<>();
        for(EntityPlayer p:mc.theWorld.playerEntities){if(p==mc.thePlayer||p.isDead)continue;ItemStack stack=p.getHeldItem();if(!p.isUsingItem()||stack==null||!(stack.getItem() instanceof ItemPotion)&&!(gapple.isEnabled()&&stack.getItem()==Items.golden_apple)){active.remove(p.getUniqueID());continue;}
            UUID id=p.getUniqueID();present.add(id);String item=stack.getDisplayName();String key=stack.getItem().getUnlocalizedName()+":"+stack.getMetadata();if(key.equals(active.put(id,key)))continue;
            String message=p.getName()+(stack.getItem() instanceof ItemPotion?" is drinking ":" is eating ")+item+".";if(chat.isEnabled())ClientChat.send(message);if(notification.isEnabled())ModuleFeedback.message(getId(),getName(),message);
        }active.keySet().retainAll(present);
    }
    @Override protected void onDisable(){active.clear();world=null;}
}
