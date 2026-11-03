package dev.vibe.ui.menu;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;

/** One persisted selection owns the shader, menu colours and loading-screen colours. */
final class MenuThemes {
    private static MenuPalette selected=MenuPalette.read(Minecraft.getMinecraft().mcDataDir.toPath()),base=selected;
    private static Path directory;private static MenuThemeStore store;private static MenuTheme active;private static Properties preferences;
    private static long frameAt;
    private static boolean applying;
    private MenuThemes(){}
    private static void ensureStore(){
        Path dir=Minecraft.getMinecraft().mcDataDir.toPath().toAbsolutePath().normalize();if(store!=null&&dir.equals(directory))return;
        directory=dir;store=new MenuThemeStore(dir);preferences=new Properties();try(InputStream in=Files.newInputStream(file())){preferences.load(in);}catch(IOException ignored){}
        base=selected=MenuPalette.read(dir);active=null;
        if(preferences.containsKey("shader"))try{MenuTheme t=new MenuTheme();t.id=preferences.getProperty("themeId","shader:"+preferences.getProperty("shader"));t.name=preferences.getProperty("name",base.label);t.shader=preferences.getProperty("shader");t.background=Boolean.parseBoolean(preferences.getProperty("background","true"));t.colorMode=preferences.getProperty("colorMode","Solid");t.first=MenuPalette.parse(preferences.getProperty("color1",String.format("%08X",base.accent)));t.second=MenuPalette.parse(preferences.getProperty("color2",String.format("%08X",t.first)));t.speed=Double.parseDouble(preferences.getProperty("colorSpeed","1"));t.validate();active=t;}catch(RuntimeException invalid){}
    }
    private static Path file(){return directory.resolve("vibe/menu.properties");}
    static MenuPalette current(){ensureStore();return selected;}
    static String selectedId(){ensureStore();return active==null?"":active.id;}
    static boolean needsSample(String shader){ensureStore();return active!=null&&active.automatic()&&active.shader.equals(shader)&&!store.shaderColors.containsKey(shader);}
    static void frame(){ensureStore();long now=System.currentTimeMillis();if(base.animated()&&now-frameAt>=16){selected=base.sample(now);frameAt=now;}AccountScreenStyle.applyTheme(selected);}
    static void bind(MainMenuShaderManager shaders){
        ensureStore();if(active==null){active=MenuTheme.automatic(shaders.getSelected(),base.accent);active.background=shaders.isEnabled();}
        else if(shaders.getPresets().contains(active.shader)){applying=true;try{if(!active.shader.equals(shaders.getSelected()))shaders.select(active.shader);if(active.background!=shaders.isEnabled())shaders.setEnabled(active.background);}finally{applying=false;}}
    }
    static MenuTheme active(MainMenuShaderManager shaders){ensureStore();shaders.getPresets();if(active==null)bind(shaders);return active.copy();}
    static List<MenuTheme> custom(){ensureStore();return Collections.unmodifiableList(store.custom);}
    static MenuTheme automatic(String shader){ensureStore();return MenuTheme.automatic(shader,store.shaderColors.getOrDefault(shader,ShaderThemeColors.fallback(shader)));}
    static boolean activate(MenuTheme theme,MainMenuShaderManager shaders){
        ensureStore();theme.validate();if(!shaders.getPresets().contains(theme.shader))return false;
        active=theme.copy();base=active.palette();selected=base.sample(System.currentTimeMillis());AccountScreenStyle.applyTheme(selected);
        applying=true;try{if(!active.shader.equals(shaders.getSelected()))shaders.select(active.shader);if(active.background!=shaders.isEnabled())shaders.setEnabled(active.background);}finally{applying=false;}
        return persist(false);
    }
    static boolean saveCustom(MenuTheme theme,MainMenuShaderManager shaders){
        ensureStore();theme.validate();if(theme.automatic())throw new IllegalArgumentException("Create a custom copy first.");
        List<MenuTheme> previous=new ArrayList<>(store.custom);store.custom.removeIf(t->t.id.equals(theme.id));if(store.custom.size()>=64){store.custom.clear();store.custom.addAll(previous);throw new IllegalArgumentException("Keep up to 64 custom themes.");}store.custom.add(theme.copy());
        try{store.save();}catch(IOException e){store.custom.clear();store.custom.addAll(previous);return false;}return activate(theme,shaders);
    }
    static boolean deleteCustom(String id,MainMenuShaderManager shaders){ensureStore();List<MenuTheme> previous=new ArrayList<>(store.custom);store.custom.removeIf(t->t.id.equals(id));try{store.save();}catch(IOException e){store.custom.clear();store.custom.addAll(previous);return false;}if(active!=null&&active.id.equals(id))return activate(automatic(shaders.getSelected()),shaders);return true;}
    static void sampled(String shader,int color){
        ensureStore();Integer previous=store.shaderColors.put(shader,color);if(previous!=null&&previous==color)return;
        try{store.save();}catch(IOException ignored){}
        if(active!=null&&active.automatic()&&active.shader.equals(shader)){active.first=active.second=color;base=selected=active.palette();AccountScreenStyle.applyTheme(selected);persist(false);}
    }
    static void shaderSelected(MainMenuShaderManager shaders,String name){if(applying)return;ensureStore();active=automatic(name);active.background=shaders.isEnabled();base=selected=active.palette();AccountScreenStyle.applyTheme(selected);persist(false);}
    static void backgroundChanged(boolean enabled){if(applying)return;ensureStore();if(active!=null){active.background=enabled;persist(false);}}
    /** Retains the legacy palette API for loading-screen previews and migration. */
    static boolean select(MenuPalette palette){ensureStore();base=selected=palette;AccountScreenStyle.applyTheme(palette);if(active!=null){active.first=active.second=palette.accent;active.colorMode="Solid";}return persist(true);}
    private static boolean persist(boolean legacy){
        Properties p=new Properties();p.setProperty("theme",legacy?base.name():"OCEAN");
        if(active!=null){p.setProperty("themeId",active.id);p.setProperty("shader",active.shader);p.setProperty("background",String.valueOf(active.background));p.setProperty("name",active.name);
            if(!legacy){p.setProperty("color1",String.format("%08X",active.first));p.setProperty("color2",String.format("%08X",active.second));p.setProperty("colorMode",active.colorMode);p.setProperty("colorSpeed",String.valueOf(active.speed));}}
        try{StringWriter out=new StringWriter();p.store(out,"Vibe theme: background and menu colours");MenuThemeStore.write(file(),out.toString());preferences=p;return true;}catch(IOException e){return false;}
    }
}
