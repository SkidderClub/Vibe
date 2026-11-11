package dev.vibe.module.impl.visual;
import dev.vibe.features.*;
import dev.vibe.module.Category;
import dev.vibe.setting.*;
import net.minecraft.client.renderer.GlStateManager;
public final class EnchantmentGlintModule extends FeatureModule {
    public final WorldStyle style=new WorldStyle(this::addSetting,()->true,false);
    public final NumberSetting opacity=addSetting(new NumberSetting("Opacity",.8,0,1,.05)),speed=addSetting(new NumberSetting("Animation Speed",1,.05,5,.05));
    public EnchantmentGlintModule(){super("Enchantment Glint","Custom enchantment sheen across items, armour and GUI/HUD renders",Category.VISUAL);}
    public static int tint(int original){EnchantmentGlintModule m=module(EnchantmentGlintModule.class);if(m==null||!m.isEnabled())return original;int c=m.style.color(0,1,0);float a=m.opacity.getFloat()*(c>>>24)/255F;return 0xFF000000|((int)((c>>16&255)*a)<<16)|((int)((c>>8&255)*a)<<8)|(int)((c&255)*a);}
    public static void color(float r,float g,float b,float a){EnchantmentGlintModule m=module(EnchantmentGlintModule.class);if(m==null||!m.isEnabled()||r==1&&g==1&&b==1){GlStateManager.color(r,g,b,a);return;}int c=tint(0);GlStateManager.color((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,a);}
    public static long time(long original){EnchantmentGlintModule m=module(EnchantmentGlintModule.class);return m==null||!m.isEnabled()?original:(long)(original*m.speed.getDouble());}
    public static void translate(float x,float y,float z){EnchantmentGlintModule m=module(EnchantmentGlintModule.class);float speed=m==null||!m.isEnabled()?1:m.speed.getFloat();GlStateManager.translate(x*speed,y*speed,z*speed);}
}
