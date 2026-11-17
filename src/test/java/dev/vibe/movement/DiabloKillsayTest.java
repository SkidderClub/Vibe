package dev.vibe.movement;

import com.mojang.authlib.GameProfile;
import dev.vibe.hud.SessionJournal;
import dev.vibe.module.impl.client.KillsayModule;
import dev.vibe.path.PathTestFixture;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C01PacketChatMessage;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiabloKillsayTest {
    private final UUID own=UUID.randomUUID(),other=UUID.randomUUID();
    private SessionJournal.Entry entry(UUID actor,UUID victim,boolean player,boolean observed,boolean confirmed){return new SessionJournal.Entry("Kill","Tester","Victim",actor,victim,null,confirmed,"","",player,observed);}
    @Test public void acceptsOnlyOwnPlayerKillsWithDeathEvidence(){
        assertTrue(KillsayModule.eligible(entry(own,other,true,true,false),own,"Tester"));
        assertTrue(KillsayModule.eligible(entry(null,other,true,false,true),own,"Tester"));
        assertFalse(KillsayModule.eligible(entry(own,other,true,false,false),own,"Tester"));
        assertFalse(KillsayModule.eligible(entry(own,other,false,true,false),own,"Tester"));
        assertFalse(KillsayModule.eligible(entry(other,own,true,true,true),own,"Tester"));
        assertFalse(KillsayModule.eligible(entry(own,own,true,true,true),own,"Tester"));
    }
    @Test public void preservesAllSevenDiabloMessagesAndReplacesVibeNames(){
        String[] source={"There is a reason why you are losing","Investing in Diablo is ur best choice","Even my dog is better at the game then you","I can't imagine being that bad","Did u forget to use your mouse?","get good get diablo @ intent","vince is quite literary the most hot man on earth"};
        assertEquals(source.length,KillsayModule.messageCount("Diablo"));
        for(int i=0;i<source.length;i++)assertEquals(source[i]+" Victim",KillsayModule.message("Diablo",i,"\u00a7cVictim\n"));
        for(int i=0;i<KillsayModule.messageCount("Vibe");i++){String s=KillsayModule.message("Vibe",i,"Victim");assertTrue(s.contains("Victim"));assertFalse(s.contains("%name%"));}
    }
    @Test public void enablingSkipsHistoryAndConfirmationCannotRepeatAMessage() throws Exception {
        Field field=SessionJournal.class.getDeclaredField("history");field.setAccessible(true);
        @SuppressWarnings("unchecked") Deque<SessionJournal.Entry> history=(Deque<SessionJournal.Entry>)field.get(SessionJournal.INSTANCE);
        List<SessionJournal.Entry> previous=new ArrayList<>(history);history.clear();
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            PathTestFixture.set(EntityPlayer.class,f.player,"gameProfile",new GameProfile(own,"Tester"));
            PathTestFixture.set(net.minecraft.entity.Entity.class,f.player,"entityUniqueID",own);
            assertEquals(own,f.player.getUniqueID());
            history.addFirst(entry(own,other,true,true,false));KillsayModule say=new KillsayModule();say.mode.setValue("Diablo");say.setEnabled(true);say.tick();assertTrue(f.queue.packets.isEmpty());
            history.addFirst(entry(own,other,true,true,false));say.tick();assertEquals(1,f.queue.packets.size());assertTrue(f.queue.packets.get(0) instanceof C01PacketChatMessage);
            history.addFirst(entry(own,other,true,true,true));say.tick();assertEquals(1,f.queue.packets.size());
            say.tick();assertEquals(1,f.queue.packets.size());say.setEnabled(false);
        }finally{history.clear();history.addAll(previous);}
    }
}
