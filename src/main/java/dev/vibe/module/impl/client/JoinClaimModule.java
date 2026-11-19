package dev.vibe.module.impl.client;

import dev.vibe.features.FeatureModule;
import dev.vibe.feedback.ModuleFeedback;
import dev.vibe.module.Category;
import java.security.SecureRandom;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.util.Session;

/** Port of Adjust's JoinClaimModule. Only the official Minecraft session service is contacted. */
public final class JoinClaimModule extends FeatureModule {
    private static final String SERVER_ID=serverId();
    private static final ExecutorService CLAIMS=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Vibe join claim");t.setDaemon(true);return t;});
    private final AtomicBoolean running=new AtomicBoolean();
    private long nextClaim,nextReminder;
    public JoinClaimModule(){super("Join Claim","Periodically refreshes your session's join claim while in multiplayer",Category.CLIENT);}
    @Override protected void onEnable(){long now=System.currentTimeMillis();nextClaim=now+250;nextReminder=now+300000;}
    @Override public void tick(){
        if(mc.thePlayer==null||mc.theWorld==null||mc.isSingleplayer())return;
        long now=System.currentTimeMillis();
        if(now>=nextReminder){nextReminder=now+300000;ModuleFeedback.message("joinclaim-reminder","Join Claim","Still enabled. Join Claim is intended for temporary use.");}
        if(now<nextClaim)return;nextClaim=now+250;
        final Session session=mc.getSession();
        if(session==null||session.getToken()==null||session.getToken().isEmpty()||"0".equals(session.getToken())||!running.compareAndSet(false,true))return;
        CLAIMS.execute(()->{try{if(isEnabled())mc.getSessionService().joinServer(session.getProfile(),session.getToken(),SERVER_ID);}catch(Exception ignored){/* Offline/expired sessions must not interrupt gameplay. */}finally{running.set(false);}});
    }
    private static String serverId(){byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);StringBuilder s=new StringBuilder(32);for(byte b:bytes)s.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));return s.toString();}
}
