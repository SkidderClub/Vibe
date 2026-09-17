package dev.vibe.combat;

import dev.vibe.module.Module;
import dev.vibe.module.impl.HitmarkerModule;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.monster.EntityZombie;
import org.junit.Test;
import static org.junit.Assert.*;

public class HitmarkerDelayTest {
    @Test public void delayLimitsOnlyTorusesAndKeepsTheirIndependentLifetime() throws Exception {
        Minecraft previous = Minecraft.getMinecraft();
        try {
            set(Minecraft.class,null,"theMinecraft",allocate(Minecraft.class));
            HitmarkerModule module = new HitmarkerModule();
            set(Module.class,module,"enabled",true);
            module.getModes().setValue(new LinkedHashSet<String>(Arrays.asList("Torus","3D (World)","2D (Crosshair)")));
            module.torusDelay.setValue(1000D);
            module.torusLifetime.setValue(5000D);
            EntityZombie target = allocate(EntityZombie.class);
            module.mark(target);
            HitmarkerModule.Marker first = module.getTorusMarkers().get(0);
            // Pin the last-spawn time ahead of the clock to avoid timing-dependent sleeps.
            set(HitmarkerModule.class,module,"lastTorus",System.currentTimeMillis()+1000);
            for(int i=0;i<30;i++)module.mark(target);
            assertEquals(1,module.getTorusMarkers().size());
            assertSame(first,module.getTorusMarkers().get(0));
            assertEquals("3D confirmations are still emitted",18,module.getMarkers().size());
            assertTrue("Crosshair confirmation is still emitted",module.progress()>0);
            set(HitmarkerModule.class,module,"lastTorus",System.currentTimeMillis()-1001);
            module.mark(target);
            assertEquals(2,module.getTorusMarkers().size());
            module.torusDelay.setValue(0D);
            module.mark(target); module.mark(target);
            assertEquals("Zero delay allows every hit",4,module.getTorusMarkers().size());
            module.clear();
            assertTrue(module.getMarkers().isEmpty()); assertTrue(module.getTorusMarkers().isEmpty());
            assertEquals(0,module.progress(),0);
        } finally { set(Minecraft.class,null,"theMinecraft",previous); }
    }
    private static void set(Class<?> type,Object object,String name,Object value)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private static <T>T allocate(Class<T> type)throws Exception{Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);return type.cast(u.getMethod("allocateInstance",Class.class).invoke(f.get(null),type));}
}
