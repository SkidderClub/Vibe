package dev.vibe.chatter;

import dev.vibe.chatter.ChatterData.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ChatterTest {
    @org.junit.Rule public TemporaryFolder temporary=new TemporaryFolder();
    private ChatterStore store;private ChatterEngine engine;private Server server;
    @Before public void setup(){store=new ChatterStore(temporary.getRoot().toPath());engine=new ChatterEngine(store);server=new Server("Steve","example.org:25565");}
    @Test public void requestedExamplesAndLiteralQuestionMarkArePresent(){
        assertEquals(3,engine.data().rules.size());Rule register=engine.data().rules.get(0),login=engine.data().rules.get(1);
        assertEquals("/register %password% %password%",register.message);assertEquals(300,register.minimum);assertEquals(600,register.maximum);
        assertEquals("/login %password%",login.message);assertEquals(200,login.minimum);assertEquals(400,login.maximum);
        assertTrue(ChatterEngine.matches("%name%**cheat**?","<Alex> STEVE are you using cheats today?","Steve"));
        assertFalse(ChatterEngine.matches("%name%*cheat*?","<Alex> Steve uses cheats!","Steve"));
        assertFalse(ChatterEngine.matches("%name%*cheat*?","cheat? Steve","Steve"));
        assertTrue(ChatterEngine.matches("hello.world","prefix hello.world suffix","Steve"));
        assertFalse(ChatterEngine.matches("hello.world","helloXworld","Steve"));
    }
    @Test public void registrationAndLoginUseOneSavedPasswordAndRespectTheDelay()throws Exception{
        engine.receive("Please /reg\u00a7aister your account",server,0);assertEquals(1,engine.pending());engine.tick(server,299);assertTrue(server.sent.isEmpty());
        engine.tick(server,600);assertEquals(1,server.sent.size());String[] register=server.sent.get(0).split(" ");assertEquals(register[1],register[2]);assertEquals(16,register[1].length());
        engine.receive("Use /login now",server,650);engine.tick(server,849);assertEquals(1,server.sent.size());engine.tick(server,1050);
        assertEquals("/login "+register[1],server.sent.get(1));
        ChatterEngine reload=new ChatterEngine(store);assertEquals(register[1],reload.expand("%pass%","sTEVE","EXAMPLE.ORG"));assertEquals(1,reload.data().passwords.size());
        engine.receive("Please /register again",server,5000);engine.tick(server,6000);assertEquals(2,server.sent.size());
    }
    @Test public void leaveMatchesTheNameCheatAndQuestionInOrderAndWaits(){
        engine.receive("<Alex> Steve you cheat?",server,0);engine.tick(server,199);assertEquals(0,server.leaves);
        engine.tick(server,400);assertEquals(1,server.leaves);assertEquals(0,engine.pending());assertTrue(server.sent.isEmpty());
    }
    @Test public void disconnectSwitchingAccountsOrServersNeverSendsOldQueuedActions(){
        engine.receive("/login",server,0);engine.tick(new Server("Other","example.org"),1000);assertTrue(server.sent.isEmpty());assertEquals(0,engine.pending());
        engine.receive("/login",server,1500);engine.tick(null,2000);engine.tick(server,3000);assertTrue(server.sent.isEmpty());
        engine.receive("/login",server,4000);Server next=new Server("Steve","elsewhere.org");engine.tick(next,5000);assertTrue(next.sent.isEmpty());
        engine.receive("/login",server,6000);engine.cancel();engine.tick(server,7000);assertTrue(server.sent.isEmpty());
    }
    @Test public void passwordScopeFollowsUsernameAndServerAndCanUseAnExplicitGlobalEntry()throws Exception{
        String a=engine.expand("%password%","Steve","one.org"),same=engine.expand("%pass%","STEVE","one.org:25565");assertEquals(a,same);
        assertNotEquals(a,engine.expand("%password%","Steve","two.org"));assertNotEquals(a,engine.expand("%password%","Alex","one.org"));
        engine.data().passwords.add(new Password("Global","","Saved_global_123"));engine.save();assertEquals("Saved_global_123",engine.expand("%pass%","GLOBAL","anywhere.org"));
        engine.data().perServer=false;engine.save();assertEquals(a,engine.expand("%pass%","Steve","one.org"));
        String global=engine.expand("%pass%","NewName","one.org");assertEquals(global,engine.expand("%password%","NewName","two.org"));
    }
    @Test public void placeholdersKeepPasswordsStableAndRandomizeLettersAndDigits()throws Exception{
        String output=engine.expand("%name% %password% %pass% %s %n","Steve","test.org");String[] parts=output.split(" ");
        assertEquals("Steve",parts[0]);assertEquals(parts[1],parts[2]);assertTrue(parts[3].matches("[a-zA-Z]{6}"));assertTrue(parts[4].matches("[0-9]{3}"));
        engine.data().generation.lettersLength=3;engine.data().generation.digitsLength=8;
        assertTrue(engine.expand("%s%n","Steve","test.org").matches("[a-zA-Z]{3}[0-9]{8}"));
    }
    @Test public void passwordPolicyGuaranteesSelectedGroupsWithoutChangingExistingPasswords()throws Exception{
        String existing=engine.expand("%pass%","Steve","example.org");Generation generation=engine.data().generation;
        generation.symbols=true;generation.length=24;SecureRandom random=new SecureRandom();
        for(int i=0;i<100;i++){String value=generation.password(random);assertEquals(24,value.length());assertTrue(value.matches(".*[a-z].*"));assertTrue(value.matches(".*[A-Z].*"));assertTrue(value.matches(".*[0-9].*"));assertTrue(value.matches(".*[!@#$%&*_+=-].*"));assertFalse(value.matches(".*[O0Il].*"));}
        engine.save();assertEquals(existing,engine.expand("%pass%","Steve","example.org"));
    }
    @Test public void noSecretIsSentIfTheVaultCannotBeSaved()throws Exception{
        Path broken=temporary.newFile("not-a-directory").toPath();ChatterEngine unavailable=new ChatterEngine(new ChatterStore(broken));
        unavailable.receive("/register",server,0);unavailable.tick(server,600);assertTrue(server.sent.isEmpty());assertTrue(unavailable.data().passwords.isEmpty());assertTrue(unavailable.status().startsWith("Message not sent"));
    }
    @Test public void encryptedVaultIncludesRulesAndFailsClosedWhenKeyIsMissing()throws Exception{
        engine.data().rules.get(0).message="/register Very_private_123 Very_private_123";engine.expand("%pass%","Steve","example.org");engine.save();
        Path vault=temporary.getRoot().toPath().resolve("chatter.vault");byte[] ciphertext=Files.readAllBytes(vault);
        assertFalse(new String(ciphertext,StandardCharsets.ISO_8859_1).contains("Very_private_123"));assertEquals(16,Files.size(temporary.getRoot().toPath().resolve("chatter.key")));
        Files.delete(temporary.getRoot().toPath().resolve("chatter.key"));ChatterEngine broken=new ChatterEngine(store);assertFalse(broken.available());
        broken.receive("/register",server,0);broken.tick(server,1000);assertTrue(server.sent.isEmpty());assertArrayEquals(ciphertext,Files.readAllBytes(vault));
        try{broken.save();fail();}catch(IOException expected){assertFalse(expected.getMessage().contains("Very_private_123"));}
    }
    @Test public void corruptedVaultNeverGetsReplacedWithFreshPasswords()throws Exception{
        engine.expand("%pass%","Steve","example.org");Path vault=temporary.getRoot().toPath().resolve("chatter.vault");byte[] bytes=Files.readAllBytes(vault);bytes[bytes.length-1]^=1;Files.write(vault,bytes);
        ChatterEngine broken=new ChatterEngine(store);assertFalse(broken.available());broken.receive("/login",server,0);broken.tick(server,1000);assertTrue(server.sent.isEmpty());assertArrayEquals(bytes,Files.readAllBytes(vault));
    }
    @Test public void repeatedMessagesDoNotFloodAndExpandedMessagesAreNotTruncated()throws Exception{
        engine.data().rules.clear();Rule rule=new Rule("hello",Action.MESSAGE,"hello %s",0,0,false);engine.data().rules.add(rule);engine.save();
        for(int i=0;i<100;i++)engine.receive("hello",server,0);assertEquals(1,engine.pending());engine.tick(server,0);assertEquals(1,server.sent.size());
        engine.receive(server.sent.get(0),server,1);engine.tick(server,1);assertEquals(1,server.sent.size());
        rule.message=String.join("",Collections.nCopies(101,"a"));engine.save();engine.receive("hello",server,3000);engine.tick(server,3000);assertEquals(1,server.sent.size());
        assertTrue(engine.status().contains("100"));
    }
    @Test public void multipleMatchesQueueBoundedlyAndSendAtMostOnePerTick()throws Exception{
        engine.data().rules.clear();for(int i=0;i<64;i++)engine.data().rules.add(new Rule("hello",Action.MESSAGE,"response "+i,0,0,false));engine.save();
        engine.receive("hello",server,0);assertEquals(32,engine.pending());assertEquals(32,server.matches.size());engine.tick(server,0);assertEquals(1,server.sent.size());assertEquals(31,engine.pending());
    }
    @Test public void acceptedMatchesNotifyImmediatelyAndRespectPendingCooldownAndDisabledRules(){
        engine.data().rules.clear();Rule rule=new Rule("hello",Action.MESSAGE,"secret response",100,100,false);engine.data().rules.add(rule);
        engine.receive("unrelated",server,0);assertTrue(server.matches.isEmpty());
        engine.receive("hello",server,0);assertEquals(Collections.singletonList("CHAT:MESSAGE"),server.matches);assertTrue(server.sent.isEmpty());
        engine.receive("hello",server,50);assertEquals(1,server.matches.size());
        engine.tick(server,100);engine.receive("hello",server,1000);assertEquals(1,server.matches.size());
        engine.receive("hello",server,2000);assertEquals(2,server.matches.size());engine.tick(server,2100);
        rule.enabled=false;engine.receive("hello",server,4000);assertEquals(2,server.matches.size());
        rule.enabled=true;rule.oncePerConnection=true;engine.receive("hello",server,5000);assertEquals(2,server.matches.size());
    }
    @Test public void notificationsUseTheDetectedSourceAndCoverEveryAction(){
        engine.data().rules.clear();Rule rule=new Rule("match",Action.INTERACT,"",100,100,false);rule.source=Source.ANY;engine.data().rules.add(rule);
        engine.receive("match",Source.SUBTITLE,server,0);assertEquals(Collections.singletonList("SUBTITLE:INTERACT"),server.matches);
        engine.cancel();rule.source=Source.TITLE;rule.action=Action.LEAVE;
        engine.receive("match",server,1000);assertEquals(1,server.matches.size());
        engine.receive("match",Source.TITLE,server,1000);assertEquals(Arrays.asList("SUBTITLE:INTERACT","TITLE:LEAVE"),server.matches);assertEquals(0,server.leaves);
    }
    private static final class Server implements ChatterEngine.Connection {
        final Object id=new Object();final String name,host;final List<String> sent=new ArrayList<>();int leaves;
        final List<String> matches=new ArrayList<>();
        Server(String name,String host){this.name=name;this.host=host;}public Object identity(){return id;}public String name(){return name;}public String server(){return host;}
        int interacted=-1;boolean ready=true;
        public void matched(Source source,Action action){matches.add(source+":"+action);}
        public void message(String text){sent.add(text);}public void leave(){leaves++;}public boolean interact(int slot){if(!ready)return false;interacted=slot;return true;}
    }
    @Test public void hotkeyRulesWaitUseTheNormalQueueAndDoNotMatchChat()throws Exception{
        engine.data().rules.clear();Rule rule=new Rule("",Action.MESSAGE,"hello %name%",50,50,false);rule.source=Source.HOTKEY;rule.hotkey=34;engine.data().rules.add(rule);engine.save();
        engine.receive("hello",server,0);engine.key(35,server,0);assertEquals(0,engine.pending());engine.key(34,server,0);engine.key(34,server,1);assertEquals(1,engine.pending());assertEquals(Collections.singletonList("HOTKEY:MESSAGE"),server.matches);engine.tick(server,49);assertTrue(server.sent.isEmpty());engine.tick(server,50);assertEquals(Collections.singletonList("hello Steve"),server.sent);engine.key(34,server,100);assertEquals(0,engine.pending());engine.key(34,server,2000);assertEquals(1,engine.pending());assertEquals(34,store.load().rules.get(0).hotkey);
    }
    @Test public void titlesAndSubtitlesAreScopedAndInteractionsWaitForAvailableInput()throws Exception{
        engine.data().rules.clear();Rule rule=new Rule("Victory",Action.INTERACT,"",50,50,false);rule.source=Source.TITLE;rule.hotbarSlot=4;engine.data().rules.add(rule);engine.save();
        engine.receive("Victory",server,0);engine.receive("Victory",Source.SUBTITLE,server,0);assertEquals(0,engine.pending());
        engine.receive("Victory",Source.TITLE,server,0);assertEquals(1,engine.pending());engine.tick(server,49);assertEquals(-1,server.interacted);
        server.ready=false;engine.tick(server,50);assertEquals(1,engine.pending());server.ready=true;engine.tick(server,60);assertEquals(4,server.interacted);assertEquals(0,engine.pending());assertTrue(server.sent.isEmpty());
        ChatterData restored=store.load();assertEquals(Source.TITLE,restored.rules.get(0).source);assertEquals(4,restored.rules.get(0).hotbarSlot);assertEquals(4,rule.copy().hotbarSlot);
    }
    @Test public void anySourceMatchesSubtitleAndOldQueuedInteractionsCancelOnConnectionChanges()throws Exception{
        engine.data().rules.clear();Rule r=new Rule("Game over",Action.INTERACT,"",0,0,true);r.source=Source.ANY;engine.data().rules.add(r);engine.save();
        engine.receive("Game over",Source.SUBTITLE,server,0);assertEquals(1,engine.pending());Server changed=new Server("Other","other.example");engine.tick(changed,100);assertEquals(-1,changed.interacted);assertEquals(0,engine.pending());
        r.hotbarSlot=9;try{r.validate();fail("Invalid hotbar slot accepted");}catch(IllegalArgumentException expected){}
    }
}
