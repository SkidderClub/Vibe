package dev.vibe.movement;

import java.lang.reflect.Field;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Diablo Criticals.Watchdog. The source changes Y without forcing a position packet. */
public final class DiabloCriticals {
    private boolean stage;
    private Object player, world;
    private Frame frame;
    private static Field y, ground;
    public synchronized void reset(){stage=false;frame=null;player=world=null;}
    public synchronized void prepare(Object player,Object world,boolean onGround,boolean target,double positionY){
        if(this.player!=player||this.world!=world){stage=false;this.player=player;this.world=world;}
        frame=new Frame(onGround,target,positionY);
    }
    public synchronized boolean outbound(C03PacketPlayer packet){
        Frame f=frame;if(f==null||!f.ground)return false;
        if(!f.target){stage=false;return false;}
        stage=!stage;
        try{
            if(y==null){y=ReflectionHelper.findField(C03PacketPlayer.class,"y","field_149477_b");y.setAccessible(true);}
            if(ground==null){ground=ReflectionHelper.findField(C03PacketPlayer.class,"onGround","field_149474_g");ground.setAccessible(true);}
            y.setDouble(packet,f.y+(stage?.01:.007));ground.setBoolean(packet,false);
        }catch(IllegalAccessException error){throw new IllegalStateException("Diablo critical packet fields",error);}
        return true;
    }
    private static final class Frame {final boolean ground,target;final double y;Frame(boolean g,boolean t,double y){ground=g;target=t;this.y=y;}}
}
