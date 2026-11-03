package dev.vibe.ui.menu;

import dev.vibe.Vibe;
import dev.vibe.account.*;
import dev.vibe.event.MainMenuEvents;
import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.GuiRenderState;
import dev.vibe.ui.account.*;
import dev.vibe.ui.server.ServerBrowserGui;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Session;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Native driver screenshots and assertions for the three real menu presentations. */
public final class GlassMenusRenderCheck {
    private static Minecraft mc;
    private static Path output;
    public static void verify(Minecraft minecraft,Path directory) throws Exception {
        mc=minecraft;output=directory;Files.createDirectories(output);
        Path profile=Files.createTempDirectory(output,"glass-profile-");
        set(Minecraft.class,mc,"mcDataDir",profile.toFile());
        set(Minecraft.class,mc,"session",new Session("VibePreview","22222222222232228222222222222222","","legacy"));
        mc.theWorld=null;mc.gameSettings.guiScale=1;
        // Forge's locale reload hook requires LaunchClassLoader. Read the same
        // native English resource directly in this standalone GL fixture.
        net.minecraft.client.resources.Locale nativeLocale=new net.minecraft.client.resources.Locale();
        Map<String,String> translations=new HashMap<String,String>();
        try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(mc.getResourceManager().getResource(new net.minecraft.util.ResourceLocation("lang/en_US.lang")).getInputStream(),java.nio.charset.StandardCharsets.UTF_8))){
            String line;while((line=reader.readLine())!=null){int split=line.indexOf('=');if(!line.startsWith("#")&&split>0)translations.put(line.substring(0,split),line.substring(split+1));}
        }
        set(net.minecraft.client.resources.Locale.class,nativeLocale,"properties",translations);set(net.minecraft.client.resources.I18n.class,null,"i18nLocale",nativeLocale);
        AccountManager accounts=new AccountManager(mc,profile.resolve("accounts"));
        List<Account> entries=Arrays.asList(Account.offline("CloudRunner"),Account.offline("River"),Account.offline("VibePreview"));
        set(AccountManager.class,accounts,"accounts",entries);set(Vibe.class,Vibe.getInstance(),"accountManager",accounts);
        set(Minecraft.class,mc,"session",new Session("VibePreview",entries.get(2).getUuid().toString().replace("-",""),"","legacy"));
        ((AutoLoginPreference)get(AccountManager.class,accounts,"autoLogin")).set(entries.get(2));
        MainMenuEvents events=new MainMenuEvents();MainMenuShaderManager shaders=(MainMenuShaderManager)get(MainMenuEvents.class,events,"shaders");
        shaders.getPresets();require(shaders.getSelected().equals("prestige.frag"),"Fresh profiles must default to Prestige");
        require(MenuThemes.current()==MenuPalette.OCEAN,"Fresh profiles must default to Ocean");
        shaders.select("liquid-lava.frag");MainMenuShaderManager restored=new MainMenuShaderManager();restored.getPresets();require(restored.getSelected().equals("liquid-lava.frag"),"An explicit shader selection was overwritten by the new default");
        shaders.select("prestige.frag");shaders.setEnabled(true);
        frame(960,540);seedHead(mc.getSession().getProfile().getId(),"VibePreview");for(Account account:entries)seedHead(account.getUuid(),account.getName());
        ServerList saved=new ServerList(mc);
        for(String[] item:new String[][]{{"Vibe survival","survival.example.net"},{"Practice","pvp.example.net"},{"Creative","build.example.net"}}){
            ServerData data=new ServerData(item[0],item[1],false);data.serverMOTD="\u00a77A place to play together";data.populationInfo="24 / 100";data.pingToServer=36;saved.addServerData(data);
        }
        saved.saveServerList();
        final List<net.minecraft.world.storage.SaveFormatComparator> worlds=Arrays.asList(
                new net.minecraft.world.storage.SaveFormatComparator("New World","New World",1700000000000L,0,net.minecraft.world.WorldSettings.GameType.SURVIVAL,false,false,false),
                new net.minecraft.world.storage.SaveFormatComparator("Creative","Building world",1700000000000L,0,net.minecraft.world.WorldSettings.GameType.CREATIVE,false,false,true));
        set(Minecraft.class,mc,"saveLoader",new net.minecraft.world.storage.SaveFormatOld(profile.resolve("saves").toFile()){
            @Override public List<net.minecraft.world.storage.SaveFormatComparator> getSaveList(){return new ArrayList<net.minecraft.world.storage.SaveFormatComparator>(worlds);}
        });
        for(int[] size:new int[][]{{960,540},{480,300},{320,240}}) {
            GuiMainMenu main=main(events,size[0],size[1]);
            Map<Integer,String> mainBounds=bounds(main);frame(size[0],size[1]);events.onMenuDraw(new GuiScreenEvent.DrawScreenEvent.Pre(main,-1,-1,0){@Override public boolean isCancelable(){return true;}});
            require(mainBounds.equals(bounds(main)),"Main menu moved controls while drawing");save("glass-main-"+size[0]+".png");
            if(size[0]==960){
                GuiButton hoverButton=button(main,2);set(AccountScreenStyle.Button.class,hoverButton,"hover",1F);
                frame(size[0],size[1]);events.onMenuDraw(new GuiScreenEvent.DrawScreenEvent.Pre(main,hoverButton.xPosition+70,hoverButton.yPosition+20,0){@Override public boolean isCancelable(){return true;}});save("glass-main-hover.png");
                set(AccountScreenStyle.Button.class,hoverButton,"hover",0F);
            }
            AccountManagerGui alts=new AccountManagerGui(main,shaders,accounts);setup(alts,size[0],size[1]);
            if(size[0]==320)set(AccountManagerGui.class,alts,"scroll",2);
            Map<Integer,String> altBounds=bounds(alts);frame(size[0],size[1]);alts.drawScreen(-1,-1,0);
            require(altBounds.equals(bounds(alts)),"Account manager moved controls while drawing");save("glass-accounts-"+size[0]+".png");
            ServerBrowserGui servers=new ServerBrowserGui(main,shaders,null,false);set(ServerBrowserGui.class,servers,"saved",saved);setup(servers,size[0],size[1]);
            Map<Integer,String> serverBounds=bounds(servers);frame(size[0],size[1]);servers.drawScreen(-1,-1,0);
            require(serverBounds.equals(bounds(servers)),"Server browser moved controls while drawing");save("glass-servers-"+size[0]+".png");
            WorldBrowserGui singleplayer=new WorldBrowserGui(main,shaders);setup(singleplayer,size[0],size[1]);
            GuiSelectWorld vanilla=new GuiSelectWorld(main);vanilla.setWorldAndResolution(mc,size[0],size[1]);
            require(bounds(singleplayer).equals(bounds(vanilla)),"Singleplayer changed Minecraft's world-management buttons");
            require(!button(singleplayer,1).enabled,"Play should require a selected world");
            singleplayer.keyTyped('\0',org.lwjgl.input.Keyboard.KEY_DOWN);require(button(singleplayer,1).enabled&&button(singleplayer,2).enabled,"Selecting a world did not enable native actions");
            frame(size[0],size[1]);singleplayer.drawScreen(-1,-1,0);save("glass-worlds-"+size[0]+".png");
            LanguagePickerGui languages=new LanguagePickerGui(main,shaders);setup(languages,size[0],size[1]);frame(size[0],size[1]);languages.drawScreen(-1,-1,0);save("glass-languages-"+size[0]+".png");languages.onGuiClosed();
            GuiButton escape=button(languages,0);require(escape.mousePressed(mc,escape.xPosition+2,escape.yPosition+2),"Language Esc button is not clickable");
            languages.actionPerformed(escape);require(get(mc.getClass(),mc,"destination")==main,"Language Esc button did not return to its parent");
            set(mc.getClass(),mc,"destination",null);languages.keyTyped('\0',org.lwjgl.input.Keyboard.KEY_ESCAPE);require(get(mc.getClass(),mc,"destination")==main,"Keyboard Escape no longer returns to parent");
            LicensesGui licenses=new LicensesGui(main,shaders);setup(licenses,size[0],size[1]);frame(size[0],size[1]);licenses.drawScreen(-1,-1,0);save("glass-licenses-"+size[0]+".png");
            if(size[0]==960){
                GuiButton single=button(main,1),multi=button(main,2);
                require(single.xPosition==316&&single.yPosition==184&&single.width==328&&single.height==42,"Main menu layout changed");
                require(multi.xPosition==316&&multi.yPosition==234&&multi.width==328&&multi.height==42,"Multiplayer entry moved");
                require(button(alts,6).xPosition==738&&button(alts,6).yPosition==75,"Alt manager Back button moved");
                frame(size[0],size[1]);servers.drawScreen(-1,-1,0);transitions(servers,main,shaders);
            }
            require(!MenuGlass.active(),"A menu leaked its glass drawing scope");
            require(!(Boolean)get(MenuGlass.class,null,"failed"),"Menu glass renderer failed");
            require(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),"Menu leaked row clipping");
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"Menu rendering produced an OpenGL error");servers.onGuiClosed();
        }
        statusChips(shaders);colors(shaders);clippingAndFallback(shaders);fontScales();scaledMenus(events,shaders,accounts);inputsAndThemes(shaders);newMenus(events,shaders);LavaLampRenderCheck.verify(mc,output);
        require(shaders.getLastError().isEmpty(),"Lava shader failed: "+shaders.getLastError());
        System.out.println("Glass menus passed: real screens and controls, GUI scales 1–4 with pixel-measured type centering, six in-panel palettes, local lava motion, clipping, fallback and reversible 220 ms window resizing.");
    }
    private static void statusChips(MainMenuShaderManager shaders) throws Exception {
        Class<?> badge=Class.forName("dev.vibe.ui.server.ServerStatusBadge"),kind=Class.forName("dev.vibe.ui.server.ServerStatusBadge$Kind");
        java.lang.reflect.Method draw=badge.getDeclaredMethod("draw",int.class,int.class,kind);draw.setAccessible(true);
        for(int scale:new int[]{1,2,4}){
            mc.gameSettings.guiScale=scale;frame(1920,1080);int w=1920/scale,h=1080/scale;
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,w,h,0,-1000,1000);GL11.glMatrixMode(GL11.GL_MODELVIEW);
            shaders.draw(w,h);MenuGlass.begin(new GuiChat(),shaders,w,h);AccountScreenStyle.window(w/2-180,h/2-110,360,220);
            AccountScreenStyle.TITLE_FONT.draw("Status",w/2-160,h/2-92,AccountScreenStyle.TEXT);
            int row=0;for(Object value:kind.getEnumConstants()){draw.invoke(null,w/2-160,h/2-57+row*26,value);row++;}
            MenuStatusChip.draw(w/2-35,h/2-57,90,18,"Active",AccountScreenStyle.SUCCESS);
            MenuStatusChip.draw(w/2-35,h/2-31,90,18,"Auto Login",AccountScreenStyle.ACCENT);
            MenuGlass.end();save("glass-status-scale-"+scale+".png");
        }
        mc.gameSettings.guiScale=1;
    }
    private static GuiMainMenu main(MainMenuEvents events,int w,int h) throws Exception {
        mc.displayWidth=w;mc.displayHeight=h;
        GuiMainMenu main=PathTestFixture.allocate(GuiMainMenu.class);main.width=w;main.height=h;
        set(GuiScreen.class,main,"mc",mc);set(GuiScreen.class,main,"fontRendererObj",mc.fontRendererObj);
        List<GuiButton> buttons=new ArrayList<GuiButton>();
        for(int id:new int[]{1,2,0,4,6})buttons.add(new GuiButton(id,0,0,id==1?"Singleplayer":id==2?"Multiplayer":id==0?"Options":id==4?"Quit":"Mods"));
        set(GuiScreen.class,main,"buttonList",buttons);events.onMenuInit(new GuiScreenEvent.InitGuiEvent.Post(main,buttons));mc.currentScreen=main;return main;
    }
    private static void setup(GuiScreen screen,int w,int h){mc.displayWidth=w;mc.displayHeight=h;screen.setWorldAndResolution(mc,w,h);mc.currentScreen=screen;}
    static void frame(int w,int h) throws Exception {
        mc.displayWidth=w;mc.displayHeight=h;
        Framebuffer target=mc.getFramebuffer();
        if(target==null||target.framebufferWidth!=w||target.framebufferHeight!=h){if(target!=null)target.deleteFramebuffer();target=new Framebuffer(w,h,true);set(Minecraft.class,mc,"framebufferMc",target);}
        target.bindFramebuffer(true);GL11.glDepthMask(true);GL11.glClearColor(0,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,w,h,0,-1000,1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GuiRenderState.prepare(false);
    }
    private static void transitions(GuiScreen from,GuiScreen to,MainMenuShaderManager shaders) throws Exception {
        MenuTransitions.Rect original=(MenuTransitions.Rect)get(MenuTransitions.class,null,"lastWindow");
        MenuTransitions.open(from,to);long started=System.nanoTime();set(MenuTransitions.class,null,"started",started);
        MenuTransitions.Rect target=new MenuTransitions.Rect(300,120,360,300);
        MenuTransitions.Rect first=transitionFrame(to,shaders,target,started);require(Math.abs(first.width-original.width)<.01,"Window jumped on entry");
        MenuTransitions.Rect mid=transitionFrame(to,shaders,target,started+MenuTransitions.RESIZE_DURATION/2);save("glass-transition-midpoint.png");
        require(Math.abs(mid.width-(original.width+target.width)*.5F)<.01&&Math.abs(mid.x-(original.x+target.x)*.5F)<.01,"Window is not resizing between real menu bounds");
        require(MenuTransitions.incoming(to,started+MenuTransitions.RESIZE_DURATION)==0,"Incoming controls appeared before the resize completed");
        MenuTransitions.open(to,from);long reversed=System.nanoTime();set(MenuTransitions.class,null,"started",reversed);
        MenuTransitions.Rect reverse=transitionFrame(from,shaders,original,reversed);require(Math.abs(reverse.width-mid.width)<.01,"Rapid reverse navigation jumped");
        MenuTransitions.Rect end=transitionFrame(from,shaders,original,reversed+MenuTransitions.DURATION);require(Math.abs(end.width-original.width)<.01,"Window did not reach its destination");
        MenuTransitions.open(from,new GuiChat());
    }
    private static MenuTransitions.Rect transitionFrame(GuiScreen owner,MainMenuShaderManager shaders,MenuTransitions.Rect target,long time) throws Exception {
        frame(960,540);shaders.draw(960,540);MenuGlass.begin(owner,shaders,960,540);set(MenuGlass.class,null,"frameTime",time);
        AccountScreenStyle.window((int)target.x,(int)target.y,(int)target.width,(int)target.height);
        AccountScreenStyle.TITLE_FONT.draw("Menu transition",target.x+18,target.y+16,AccountScreenStyle.TEXT);
        MenuTransitions.Rect result=(MenuTransitions.Rect)get(MenuGlass.class,null,"window");MenuGlass.end();return result;
    }
    private static void colors(MainMenuShaderManager shaders) throws Exception {
        Set<Long> hashes=new HashSet<Long>();
        for(MenuPalette preset:MenuPalette.values()){
            MenuThemes.select(preset);frame(960,540);set(MainMenuShaderManager.class,shaders,"lastShaderFrame",0L);shaders.draw(960,540);
            MenuGlass.begin(new GuiChat(),shaders,960,540);AccountScreenStyle.window(120,70,720,400);
            AccountScreenStyle.TITLE_FONT.draw(preset.label,144,90,AccountScreenStyle.TEXT);
            new AccountScreenStyle.Button(0,144,390,180,40,"Continue","",true,false).drawButton(mc,-1,-1);
            MenuGlass.end();
            byte[] pixels=pixels();long hash=1;for(int i=0;i<pixels.length;i+=64)hash=31*hash+(pixels[i]&255);hashes.add(hash);
            require(shaders.getBackgroundTexture()!=0,"No lava backdrop texture");save("glass-lava-"+preset.name().toLowerCase(Locale.ROOT)+".png");
        }
        require(hashes.size()==6,"Theme selection did not recolor the lava");MenuThemes.select(MenuPalette.LAVENDER);
        shaders.setEnabled(false);long time=System.nanoTime();byte[][] motion=new byte[2][];
        for(int i=0;i<2;i++){frame(960,540);Gui.drawRect(0,0,960,540,0xFF101010);MenuGlass.begin(new GuiChat(),null,960,540);set(MenuGlass.class,null,"frameTime",time+i*3000000000L);AccountScreenStyle.window(120,70,720,400);MenuGlass.end();motion[i]=pixels();}
        require(maxDifference(motion[0],motion[1])>15,"Lava does not animate inside the panel without a background shader");shaders.setEnabled(true);
    }
    private static void clippingAndFallback(MainMenuShaderManager shaders) throws Exception {
        shaders.setEnabled(false);frame(960,540);shaders.draw(960,540);GuiScreen screen=new GuiChat();
        MenuGlass.begin(screen,shaders,960,540);byte[] before=pixels();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(100,100,100,100);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glColor4f(.2F,.3F,.4F,.5F);
        require(MenuGlass.pane(0,0,960,540,14,AccountScreenStyle.SURFACE,AccountScreenStyle.ACCENT,false),"Fallback backdrop could not render glass");
        require(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)&&GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),"Glass leaked render state");
        byte[] after=pixels();for(int y=0;y<540;y++)for(int x=0;x<960;x++)if(x<100||x>=200||y<100||y>=200){int at=(y*960+x)*4;for(int c=0;c<3;c++)require(before[at+c]==after[at+c],"Glass escaped the server-list scissor");}
        GL11.glDisable(GL11.GL_SCISSOR_TEST);MenuGlass.end();save("glass-fallback.png");shaders.setEnabled(true);
        require(GL11.glGetError()==GL11.GL_NO_ERROR,"Fallback glass produced a GL error");
    }
    private static void fontScales() throws Exception {
        for(int scale=1;scale<=4;scale++)for(String label:new String[]{"Settings","Multiplayer","Agjpqy","✓ Ready"}){
            mc.gameSettings.guiScale=scale;frame(1920,1080);int w=1920/scale,h=1080/scale;
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,w,h,0,-1000,1000);GL11.glMatrixMode(GL11.GL_MODELVIEW);
            MenuGlass.begin(new GuiChat(),null,w,h);AccountScreenStyle.BODY_FONT.centered(label,120,100,200,32,0xFFFFFFFF);MenuGlass.end();
            byte[] image=pixels();int minX=1920,minY=1080,maxX=-1,maxY=-1;
            for(int y=0;y<1080;y++)for(int x=0;x<1920;x++){int at=(y*1920+x)*4;if((image[at]&255)>16){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}}
            require(maxX>=minX,"Menu font produced no pixels");
            require(Math.abs((minX+maxX+1)*.5-220*scale)<=1.1,"Menu text is not horizontally centered at GUI scale "+scale);
            require(Math.abs((minY+maxY+1)*.5-(1080-116*scale))<=1.1,"Menu text is not vertically centered at GUI scale "+scale);
            require((maxY-minY+1)/(float)scale>=8&& (maxY-minY+1)/(float)scale<=14,"Font does not fit GUI scale "+scale);
            if(label.equals("Settings"))save("glass-font-scale-"+scale+".png");
        }
        mc.gameSettings.guiScale=1;
    }
    private static void scaledMenus(MainMenuEvents events,MainMenuShaderManager shaders,AccountManager accounts) throws Exception {
        for(int scale:new int[]{2,3,4}){
            mc.gameSettings.guiScale=scale;int w=1920/scale,h=1080/scale;GuiMainMenu main=main(events,w,h);
            frame(1920,1080);events.onMenuDraw(new GuiScreenEvent.DrawScreenEvent.Pre(main,-1,-1,0){@Override public boolean isCancelable(){return true;}});save("glass-main-scale-"+scale+".png");
            AccountManagerGui alts=new AccountManagerGui(main,shaders,accounts);alts.setWorldAndResolution(mc,w,h);mc.currentScreen=alts;
            frame(1920,1080);alts.drawScreen(-1,-1,0);save("glass-accounts-scale-"+scale+".png");
        }
        mc.gameSettings.guiScale=1;
    }
    private static void inputsAndThemes(MainMenuShaderManager shaders) throws Exception {
        frame(960,540);shaders.draw(960,540);MenuGlass.begin(new GuiChat(),shaders,960,540);AccountScreenStyle.window(90,70,460,230);
        MenuTextField field=new MenuTextField(1,mc.fontRendererObj,120,160,240,20);field.setEnableBackgroundDrawing(false);field.setMaxStringLength(256);field.setText("Hello world");
        field.drawTextBox();field.mouseClicked(120+Math.round(AccountScreenStyle.BODY_FONT.width("Hello")),170,0);require(field.getCursorPosition()==5,"Text-field clicks do not follow measured menu text");
        field.setCursorPosition(6);field.setSelectionPos(11);field.drawTextBox();field.writeText("friend");require(field.getText().equals("Hello friend"),"Native text-field selection/replacement broke");
        field.setCursorPosition(6);field.setSelectionPos(12);field.drawTextBox();MenuGlass.end();save("glass-search-selection.png");
        MenuThemesGui themes=new MenuThemesGui(new GuiMainMenu(),shaders);setup(themes,960,540);frame(960,540);themes.drawScreen(-1,-1,0);save("glass-theme-picker.png");
        ThemeControlsRenderCheck.verify(mc,shaders,output);
        require(!(Boolean)get(MenuGlass.class,null,"failed"),"Theme previews or input layers failed");require(GL11.glGetError()==GL11.GL_NO_ERROR,"Input/theme drawing produced a GL error");
    }
    private static void seedHead(UUID id,String name) throws Exception {
        SkinHeads.draw(id,name,0,0,20,false);Map<?,?> heads=(Map<?,?>)get(SkinHeads.class,null,"HEADS");
        for(Object head:heads.values())set(head.getClass(),head,"retryAt",Long.MAX_VALUE);
    }
    private static void newMenus(MainMenuEvents events,MainMenuShaderManager shaders)throws Exception{
        mc.gameSettings.guiScale=1;String selected=shaders.getSelected();
        ShaderMenuGui picker=new ShaderMenuGui(new GuiMainMenu(),shaders);setup(picker,960,540);
        for(int i=0;i<6;i++){set(MenuThemesGui.class,picker,"nextPreview",0L);frame(960,540);picker.drawScreen(-1,-1,0);}
        require(selected.equals(shaders.getSelected()),"Thumbnail generation changed the selected shader");
        Map<?,?> thumbnails=(Map<?,?>)get(MenuThemesGui.class,picker,"thumbnails");require(!thumbnails.isEmpty()&&thumbnails.size()<=8,"Shader previews are missing or unbounded");
        save("glass-shaders.png");picker.onGuiClosed();require(thumbnails.isEmpty(),"Shader browser retained GPU thumbnails after closing");
        dev.vibe.ui.server.MinibloxConnectGui loading=new dev.vibe.ui.server.MinibloxConnectGui(new GuiMainMenu(),new ServerData("Miniblox","miniblox.io",false),null);
        dev.vibe.miniblox.MinibloxRuntime.Attempt attempt=new dev.vibe.miniblox.MinibloxRuntime.Attempt();attempt.message="Starting the translation layer";
        set(loading.getClass(),loading,"attempt",attempt);setup(loading,960,540);frame(960,540);loading.drawScreen(-1,-1,0);save("glass-miniblox.png");loading.onGuiClosed();
        for(int[] size:new int[][]{{960,540},{480,300},{320,240}}){
            GuiIngameMenu pause=new GuiIngameMenu();setup(pause,size[0],size[1]);
            new dev.vibe.event.ReconnectEvents().init(new GuiScreenEvent.InitGuiEvent.Post(pause,buttons(pause)));
            events.onMenuInit(new GuiScreenEvent.InitGuiEvent.Post(pause,buttons(pause)));Map<Integer,String> original=bounds(pause);
            java.lang.reflect.Method fitted=MainMenuEvents.class.getDeclaredMethod("pausePanelBounds",GuiScreen.class,List.class);fitted.setAccessible(true);
            int[] card=(int[])fitted.invoke(null,pause,buttons(pause));GuiButton last=button(pause,1),first=button(pause,4);
            require(card[1]+card[3]==last.yPosition+last.height+12,"Pause card retains empty space below its actions");
            require(card[1]<=first.yPosition-24&&card[3]<180,"Pause card clips the title or remains oversized");
            frame(size[0],size[1]);events.onMenuDraw(new GuiScreenEvent.DrawScreenEvent.Pre(pause,-1,-1,0){@Override public boolean isCancelable(){return true;}});
            Thread.sleep(240);frame(size[0],size[1]);events.onMenuDraw(new GuiScreenEvent.DrawScreenEvent.Pre(pause,-1,-1,0){@Override public boolean isCancelable(){return true;}});
            require(original.equals(bounds(pause)),"Fitting the pause card moved its buttons");save("glass-pause-"+size[0]+".png");
        }
        require(!MenuGlass.active()&&GL11.glGetError()==0,"Additional menus leaked graphics state");
    }
    private static Map<Integer,String> bounds(GuiScreen screen) throws Exception {
        Map<Integer,String> result=new TreeMap<Integer,String>();for(GuiButton b:buttons(screen))result.put(b.id,b.xPosition+","+b.yPosition+","+b.width+","+b.height);return result;
    }
    @SuppressWarnings("unchecked") private static List<GuiButton> buttons(GuiScreen screen) throws Exception{return (List<GuiButton>)get(GuiScreen.class,screen,"buttonList");}
    private static GuiButton button(GuiScreen screen,int id) throws Exception {for(GuiButton b:buttons(screen))if(b.id==id)return b;throw new AssertionError("Missing button "+id);}
    private static byte[] pixels(){ByteBuffer buffer=BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);GL11.glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);byte[] data=new byte[buffer.capacity()];buffer.get(data);return data;}
    private static int maxDifference(byte[] a,byte[] b){int max=0;for(int i=0;i<a.length;i++)if(i%4!=3)max=Math.max(max,Math.abs((a[i]&255)-(b[i]&255)));return max;}
    private static void save(String name) throws Exception {byte[] pixels=pixels();int w=mc.displayWidth,h=mc.displayHeight;BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=((h-1-y)*w+x)*4;image.setRGB(x,y,((pixels[i]&255)<<16)|((pixels[i+1]&255)<<8)|(pixels[i+2]&255));}ImageIO.write(image,"png",output.resolve(name).toFile());}
    private static Object get(Class<?> type,Object target,String name) throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Class<?> type,Object target,String name,Object value) throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
