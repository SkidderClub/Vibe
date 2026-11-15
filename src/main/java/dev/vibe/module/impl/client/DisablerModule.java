package dev.vibe.module.impl.client;

import dev.vibe.features.FeatureModule;
import dev.vibe.module.Category;
import dev.vibe.setting.ModeSetting;
import io.netty.channel.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;

/** Exire Watchdog: release confirmations FIFO immediately before the next C03. */
public final class DisablerModule extends FeatureModule {
    public final ModeSetting mode=addSetting(new ModeSetting("Mode","Rewound","Rewound"));
    private final ConcurrentLinkedDeque<Pending> transactions=new ConcurrentLinkedDeque<>();
    private volatile EntityPlayerSP player;
    private Object world;
    public DisablerModule(){super("Disabler","Defer Rewound transaction confirmations until the next movement update",Category.CLIENT);}
    @Override protected void onEnable(){tick();}
    @Override protected void onDisable(){release();}
    @Override public void tick(){
        if(player!=mc.thePlayer||world!=mc.theWorld){discard();player=mc.thePlayer;world=mc.theWorld;}
        if(player==null||player.ticksExisted==0)discard();
    }
    public boolean outbound(Packet<?> packet,ChannelHandlerContext ctx,ChannelPromise promise){
        if(!isEnabled())return false;
        EntityPlayerSP local=player;
        if(local==null||local!=mc.thePlayer||world!=mc.theWorld||local.ticksExisted==0){discard();return false;}
        if(packet instanceof C0FPacketConfirmTransaction){transactions.add(new Pending(packet,ctx));promise.trySuccess();if(!isEnabled())release();return true;}
        if(packet instanceof C03PacketPlayer)release();
        return false;
    }
    private void discard(){transactions.clear();}
    private void release(){for(Pending p;(p=transactions.poll())!=null;){final Pending pending=p;
        Runnable send=()->{if(pending.ctx.channel().isActive())pending.ctx.writeAndFlush(pending.packet);};
        if(p.ctx.executor().inEventLoop())send.run();else p.ctx.executor().execute(send);
    }}
    public int pendingTransactions(){return transactions.size();}
    private static final class Pending {
        final Packet<?> packet;final ChannelHandlerContext ctx;
        Pending(Packet<?> packet,ChannelHandlerContext ctx){this.packet=packet;this.ctx=ctx;}
    }
}
