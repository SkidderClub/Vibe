package dev.vibe.ui.menu;

import java.util.UUID;

/** A complete background and colour choice. Only custom entries are editable. */
public final class MenuTheme {
    public String id=UUID.randomUUID().toString(),name="My theme",shader="prestige.frag",colorMode="Solid";
    public int first=MenuPalette.OCEAN.accent,second=MenuPalette.LAVENDER.accent;
    public double speed=1;public boolean background=true;
    public MenuTheme copy(){MenuTheme t=new MenuTheme();t.id=id;t.name=name;t.shader=shader;t.colorMode=colorMode;t.first=first;t.second=second;t.speed=speed;t.background=background;return t;}
    public boolean automatic(){return id.startsWith("shader:");}
    public MenuPalette palette(){return MenuPalette.custom(name,first,second,colorMode,speed);}
    public void validate(){
        if(id==null||id.isEmpty()||id.length()>200||name==null||name.trim().isEmpty()||name.length()>40)throw new IllegalArgumentException("Enter a theme name (1–40 characters).");
        if(shader==null||shader.length()>160||!shader.toLowerCase(java.util.Locale.ROOT).endsWith(".frag")||!new java.io.File(shader).getName().equals(shader)||shader.contains("\\")||shader.contains("/"))throw new IllegalArgumentException("Choose a shader with a name of at most 160 characters.");
        if(!"Solid".equals(colorMode)&&!"Rainbow".equals(colorMode)&&!"Fade".equals(colorMode))throw new IllegalArgumentException("Choose Solid, Rainbow or Fade.");
        if(!Double.isFinite(speed)||speed<.1||speed>3)throw new IllegalArgumentException("Colour speed must be 0.1–3.");first|=0xFF000000;second|=0xFF000000;name=name.trim();
    }
    public static MenuTheme automatic(String shader,int color){MenuTheme t=new MenuTheme();t.id="shader:"+shader;t.shader=shader;String stem=shader.substring(0,shader.length()-5).replace('_',' ');t.name=stem.isEmpty()?"Shader":Character.toUpperCase(stem.charAt(0))+stem.substring(1);if(t.name.length()>40)t.name=t.name.substring(0,40);t.first=color;t.second=color;return t;}
}
