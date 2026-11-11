package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.statistics.StatisticsService;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;

/** Bounded session history. A tracked target dying is explicitly weaker evidence than a server death message. */
public final class SessionJournal implements StatisticsService.ActivityListener {
    public static final SessionJournal INSTANCE=new SessionJournal();
    public static final class Entry {
        public final String kind,actor,victim,account,label;public final UUID actorId,victimId;public final ItemStack weapon;public final boolean confirmed;public final long time,wallTime;
        public Entry(String kind,String actor,String victim,UUID actorId,UUID victimId,ItemStack weapon,boolean confirmed,String label,String account){this.kind=kind;this.actor=actor;this.victim=victim;this.actorId=actorId;this.victimId=victimId;this.weapon=weapon==null?null:weapon.copy();this.confirmed=confirmed;this.label=label;this.account=account;time=System.nanoTime()/1000000;wallTime=System.currentTimeMillis();}
    }
    private final Deque<Entry> history=new ArrayDeque<>();private StatisticsService statistics;private Object world;private boolean dead;private String profile;
    private SessionJournal(){}
    public List<Entry> entries(){return new ArrayList<>(history);}
    public void tick(){Minecraft mc=Minecraft.getMinecraft();Vibe v=Vibe.getInstance();StatisticsService next=v==null?null:v.getStatistics();if(next!=statistics){if(statistics!=null)statistics.removeActivityListener(this);statistics=next;if(next!=null)next.addActivityListener(this);}
        if(world!=mc.theWorld){world=mc.theWorld;dead=false;}if(mc.thePlayer!=null){boolean d=mc.thePlayer.isDead||mc.thePlayer.getHealth()<=0;if(d&&!dead)add(new Entry("Death","",mc.thePlayer.getName(),null,mc.thePlayer.getUniqueID(),null,true,"You died",account()));dead=d;}
        String p=v==null||v.getConfig()==null?null:v.getConfig().getActiveName();if(p!=null&&!p.equals(profile)){if(profile!=null)add(new Entry("Profile","","",null,null,null,true,"Profile: "+p,account()));profile=p;}
    }
    private static String account(){Minecraft mc=Minecraft.getMinecraft();return mc.thePlayer==null?mc.getSession().getUsername():mc.thePlayer.getName();}
    public void bookmark(){add(new Entry("Bookmark","","",null,null,null,true,"Bookmark",account()));}
    private void add(Entry entry){history.addFirst(entry);while(history.size()>64)history.removeLast();}
    @Override public void onActivity(StatisticsService.Activity action,BlockPos pos,EntityLivingBase target){Minecraft mc=Minecraft.getMinecraft();if(action!=StatisticsService.Activity.KILL||target==null||mc.thePlayer==null)return;addKill(mc.thePlayer.getName(),target.getName(),mc.thePlayer.getUniqueID(),target.getUniqueID(),mc.thePlayer.getHeldItem(),false);}
    private void addKill(String actor,String victim,UUID actorId,UUID victimId,ItemStack weapon,boolean confirmed){long now=System.nanoTime()/1000000;for(Iterator<Entry> i=history.iterator();i.hasNext();){Entry e=i.next();if(now-e.time>3500)break;if(e.kind.equals("Kill")&&e.victim.equals(victim)){if(e.confirmed||!confirmed)return;i.remove();break;}}
        add(new Entry("Kill",actor,victim,actorId,victimId,weapon,confirmed,actor+(actor.isEmpty()?"":" → ")+victim,account()));
    }
    public void chat(IChatComponent component){if(component instanceof ChatComponentTranslation){ChatComponentTranslation c=(ChatComponentTranslation)component;String key=c.getKey();Object[] a=c.getFormatArgs();if(key!=null&&key.startsWith("death.")&&a.length>=1){String victim=name(a[0]),killer=a.length>=2?name(a[1]):"";Minecraft mc=Minecraft.getMinecraft();EntityPlayer p=mc.theWorld==null?null:mc.theWorld.getPlayerEntityByName(killer),q=mc.theWorld==null?null:mc.theWorld.getPlayerEntityByName(victim);addKill(killer,victim,p==null?null:p.getUniqueID(),q==null?null:q.getUniqueID(),p==null?null:p.getHeldItem(),true);}}
        for(IChatComponent sibling:component.getSiblings())chat(sibling);
    }
    private static String name(Object value){return value instanceof IChatComponent?((IChatComponent)value).getUnformattedText():String.valueOf(value);}
}
