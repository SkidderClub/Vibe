package dev.vibe.module.impl.client;

import dev.vibe.features.FeatureModule;
import dev.vibe.hud.SessionJournal;
import dev.vibe.module.Category;
import dev.vibe.setting.ModeSetting;
import java.util.*;
import net.minecraft.util.EnumChatFormatting;

/** Shares the kill feed's observed-death/server-death evidence, never tab-list removals. */
public final class KillsayModule extends FeatureModule {
    public final ModeSetting mode=addSetting(new ModeSetting("Mode","Vibe","Vibe","Diablo"));
    private static final String[] VIBE={
        "Yo %name%, bro, you should start pasting instead of coding it yourself.",
        "%name%, your next update needs less guessing and more skidding.",
        "%name%, try vibe coding your way out of that one.",
        "%name%, that fight needed another copy and paste.",
        "%name%, your paste is missing a few features.",
        "%name%, did your code come with the comments included?",
        "%name%, time to skid a better strategy.",
        "%name%, the compiler cannot fix that aim."
    };
    // All seven messages in the supplied KillSay list; none contains a racial slur.
    private static final String[] DIABLO={"There is a reason why you are losing","Investing in Diablo is ur best choice",
        "Even my dog is better at the game then you","I can't imagine being that bad","Did u forget to use your mouse?",
        "get good get diablo @ intent","vince is quite literary the most hot man on earth"};
    private final Set<SessionJournal.Entry> seen=Collections.newSetFromMap(new IdentityHashMap<SessionJournal.Entry,Boolean>());
    private final Map<String,Long> sent=new HashMap<>();private Object world,player;private final Random random=new Random();
    public KillsayModule(){super("Killsay","Send a message after one of your observed kills",Category.CLIENT);}
    @Override protected void onEnable(){baseline();}
    @Override protected void onDisable(){seen.clear();sent.clear();world=player=null;}
    private void baseline(){seen.clear();seen.addAll(SessionJournal.INSTANCE.entries());sent.clear();world=mc.theWorld;player=mc.thePlayer;}
    @Override public void tick(){
        if(!isEnabled())return;
        if(world!=mc.theWorld||player!=mc.thePlayer){baseline();return;}
        if(!playing())return;
        List<SessionJournal.Entry> entries=SessionJournal.INSTANCE.entries();List<SessionJournal.Entry> chronological=new ArrayList<>(entries);Collections.reverse(chronological);
        for(SessionJournal.Entry e:chronological){if(!seen.add(e)||!eligible(e,mc.thePlayer.getUniqueID(),mc.thePlayer.getName()))continue;
            String key=EnumChatFormatting.getTextWithoutFormattingCodes(e.victim).toLowerCase(Locale.ROOT);Long previous=sent.get(key);
            if(previous!=null&&e.time-previous<3500)continue;
            sent.put(key,e.time);mc.thePlayer.sendChatMessage(message(mode.getValue(),random.nextInt(messageCount(mode.getValue())),e.victim));
        }
        seen.retainAll(entries);long now=System.nanoTime()/1000000;sent.entrySet().removeIf(e->now-e.getValue()>3500);
    }
    public static boolean eligible(SessionJournal.Entry e,UUID own,String name){return "Kill".equals(e.kind)&&e.playerVictim&&(e.confirmed||e.observedDeath)&&!e.victim.isEmpty()
        &&(e.actorId!=null?e.actorId.equals(own):e.actor.equalsIgnoreCase(name))
        &&(e.victimId!=null?!e.victimId.equals(own):!e.victim.equalsIgnoreCase(name));}
    public static int messageCount(String mode){return "Diablo".equals(mode)?DIABLO.length:VIBE.length;}
    public static String message(String mode,int index,String victim){
        String name=EnumChatFormatting.getTextWithoutFormattingCodes(victim==null?"":victim).replaceAll("[\\p{Cntrl}]","");
        String[] list="Diablo".equals(mode)?DIABLO:VIBE;String text=list[Math.floorMod(index,list.length)];
        return "Diablo".equals(mode)?text+" "+name:text.replace("%name%",name);
    }
}
