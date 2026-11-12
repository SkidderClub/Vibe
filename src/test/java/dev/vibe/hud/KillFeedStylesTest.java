package dev.vibe.hud;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;
public class KillFeedStylesTest {
    @Test public void redBorderOnlyAppliesToAnInvolvedPlayer(){UUID self=UUID.randomUUID(),other=UUID.randomUUID();
        SessionJournal.Entry kill=new SessionJournal.Entry("Kill","Alex","Enemy",self,other,null,true,"","Alex");assertTrue(KillFeedRenderer.involved(kill,self,"Alex"));
        SessionJournal.Entry death=new SessionJournal.Entry("Kill","Enemy","Alex",other,self,null,true,"","Alex");assertTrue(KillFeedRenderer.involved(death,self,"Alex"));
        SessionJournal.Entry unrelated=new SessionJournal.Entry("Kill","Friend","Enemy",other,UUID.randomUUID(),null,true,"","Alex");assertFalse(KillFeedRenderer.involved(unrelated,self,"Alex"));
        SessionJournal.Entry sameName=new SessionJournal.Entry("Kill","Alex","Enemy",other,UUID.randomUUID(),null,true,"","Alex");assertFalse(KillFeedRenderer.involved(sameName,self,"Alex"));
        SessionJournal.Entry namesOnly=new SessionJournal.Entry("Kill","Enemy","alex",null,null,null,true,"","Alex");assertTrue(KillFeedRenderer.involved(namesOnly,self,"Alex"));
    }
}
