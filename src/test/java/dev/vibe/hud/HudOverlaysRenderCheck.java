package dev.vibe.hud;

import dev.vibe.module.impl.client.HudModule;
import dev.vibe.ui.GuiInput;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Runs inside the native HUD-editor fixture with real fonts, item models and OpenGL. */
public final class HudOverlaysRenderCheck {
    private HudOverlaysRenderCheck(){}
    public static void verify(Minecraft mc,HudManager manager,HudModule hud,Path output)throws Exception{
        mc.displayWidth=1280;mc.displayHeight=720;mc.gameSettings.guiScale=1;
        String[] ids={HudManager.CHAT,HudManager.HOTBAR,HudManager.CUSTOM_HEALTH,HudManager.TAB};
        int[][] positions={{28,130},{430,622},{40,400},{770,105}};
        for(int i=0;i<ids.length;i++){
            HudManager.HudElement e=manager.getElement(ids[i]);e.setScale(1);manager.drawPreview(e,mc.fontRendererObj);e.moveTo(positions[i][0],positions[i][1],new ScaledResolution(mc));
        }
        for(String theme:new String[]{"Minecraft","Fortnite","Skeet","Vibe","LiquidGlass","Lavalamp"}){
            manager.getElement(HudManager.HOTBAR).setTheme(theme);frame(1280,720);for(String id:ids)manager.drawPreview(manager.getElement(id),mc.fontRendererObj);
            save(output.resolve("overlays-hotbar-"+theme.toLowerCase(java.util.Locale.ROOT)+".png"),1280,720);
        }
        manager.getElement(HudManager.HOTBAR).setTheme("Vibe");
        for(String mode:hud.customHealth.mode.getModes()){
            hud.customHealth.mode.setValue(mode);frame(1280,720);for(String id:ids)manager.drawPreview(manager.getElement(id),mc.fontRendererObj);
            save(output.resolve("overlays-health-"+mode.toLowerCase(java.util.Locale.ROOT)+".png"),1280,720);
        }
        mc.gameSettings.guiScale=3;HudManager.HudElement tab=manager.getElement(HudManager.TAB);tab.setScale(.75F);
        ScaledResolution scaled=new ScaledResolution(mc);frame(1280,720);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,scaled.getScaledWidth(),scaled.getScaledHeight(),0,-1000,1000);GL11.glMatrixMode(GL11.GL_MODELVIEW);
        manager.drawPreview(tab,mc.fontRendererObj);check(tab.getHeight()==153,"Scaled Tab layout discarded players despite available screen space");save(output.resolve("custom-tab-gui-scale-3.png"),1280,720);
        tab.setScale(1);mc.gameSettings.guiScale=1;
        HudManager.HudElement clock=manager.getElement(HudManager.CLOCK);hud.clock.display.setValue("Analog");hud.clock.date.setEnabled(false);hud.clock.digital.setEnabled(false);
        manager.drawPreview(clock,mc.fontRendererObj);clock.moveTo(440,220,new ScaledResolution(mc));clock.setTheme("Transparent");
        frame(1280,720);manager.drawPreview(clock,mc.fontRendererObj);check(clock.getHeight()==hud.clock.diameter.getInt(),"Analog clock retained space for a digital readout");
        save(output.resolve("clock-transparent.png"),1280,720);
        HudEditorGui editor=new HudEditorGui(manager);editor.setWorldAndResolution(mc,1280,720);
        Field selected=HudEditorGui.class.getDeclaredField("selected");selected.setAccessible(true);selected.set(editor,manager.getElement(HudManager.CHAT));
        frame(1280,720);editor.drawScreen(-1,-1,0);save(output.resolve("hud-editor-custom-chat.png"),1280,720);
        Method bounds=HudEditorGui.class.getDeclaredMethod("bounds",HudManager.HudElement.class);bounds.setAccessible(true);
        int[] b=(int[])bounds.invoke(editor,manager.getElement(HudManager.CHAT));double oldWidth=hud.chat.width.getDouble(),oldHeight=hud.chat.height.getDouble();
        GuiInput.mouseClicked(editor,b[2],(b[1]+b[3])/2,0);editor.drawScreen(b[2]+20,(b[1]+b[3])/2,0);release(editor,b[2]+20,(b[1]+b[3])/2);
        check(hud.chat.width.getDouble()>oldWidth&&hud.chat.height.getDouble()==oldHeight,"Side handle changed height or failed to resize chat width");
        b=(int[])bounds.invoke(editor,manager.getElement(HudManager.CHAT));oldWidth=hud.chat.width.getDouble();
        GuiInput.mouseClicked(editor,(b[0]+b[2])/2,b[3],0);editor.drawScreen((b[0]+b[2])/2,b[3]+16,0);release(editor,(b[0]+b[2])/2,b[3]+16);
        check(hud.chat.height.getDouble()>oldHeight&&hud.chat.width.getDouble()==oldWidth,"Bottom handle changed width or failed to resize chat height");
        double height=hud.chat.height.getDouble();hud.chat.height.setValue(72D);frame(1280,720);manager.drawPreview(manager.getElement(HudManager.CHAT),mc.fontRendererObj);check(manager.getElement(HudManager.CHAT).getHeight()==72,"Chat height is coupled to its width");hud.chat.height.setValue(height);
        // Render cost includes all four replacement widgets, real item geometry and readback synchronization.
        for(int i=0;i<8;i++){frame(1280,720);for(String id:ids)manager.drawPreview(manager.getElement(id),mc.fontRendererObj);}
        GL11.glFinish();long start=System.nanoTime();for(int i=0;i<30;i++){frame(1280,720);for(String id:ids)manager.drawPreview(manager.getElement(id),mc.fontRendererObj);}GL11.glFinish();
        System.out.printf(java.util.Locale.ROOT,"Four custom HUD widgets: %.2f ms/frame on %s%n",(System.nanoTime()-start)/30e6,GL11.glGetString(GL11.GL_RENDERER));
        editor.onGuiClosed();
        net.minecraft.util.ResourceLocation ascii=new net.minecraft.util.ResourceLocation("textures/font/ascii.png");mc.getTextureManager().bindTexture(ascii);
        int min=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER),mag=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER);
        frame(1280,720);
        try(HudFontFilter filter=HudFontFilter.begin(1.25F)){
            HudFontFilter.bind(mc.getTextureManager(),ascii);check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER)==GL11.GL_LINEAR,"Fractional HUD glyphs are unfiltered");
            net.minecraft.client.renderer.GlStateManager.pushMatrix();net.minecraft.client.renderer.GlStateManager.scale(1.25F,1.25F,1);mc.fontRendererObj.drawStringWithShadow("Scaled Minecraft text 0123456789",24,24,0xFFFFFFFF);net.minecraft.client.renderer.GlStateManager.popMatrix();
        }
        mc.getTextureManager().bindTexture(ascii);check(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER)==min&&GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER)==mag,"HUD filtering leaked into shared font textures");save(output.resolve("hud-font-fractional.png"),1280,720);
        liveChat(mc,manager,hud,output);
        editorMovementAndThemes(mc,manager,hud,output);
        dev.vibe.ui.effect.LavaLampRenderCheck.verify(mc,output);
        lavaWidgets(mc,manager,hud,output);
        check(GL11.glGetError()==0,"Custom HUD overlays left a GL error");
        System.out.println("Custom HUD pixels passed: six hotbar themes, three health modes, transparent analog clock, custom Tab, chat side/bottom resizing, native item previews and Lavalamp widgets.");
    }
    private static void lavaWidgets(Minecraft mc,HudManager manager,HudModule hud,Path output)throws Exception{
        dev.vibe.path.PathTestFixture.set(dev.vibe.Vibe.class,dev.vibe.Vibe.getInstance(),"hudManager",manager);
        hud.targetHud.mode.setValue("Lavalamp");
        hud.chat.width.setValue(280D);hud.chat.height.setValue(110D);
        hud.tab.columns.setValue(1D);hud.tab.rows.setValue(5D);hud.tab.width.setValue(200D);
        hud.clock.display.setValue("Analog");hud.clock.date.setEnabled(false);
        String[] ids={HudManager.WATERMARK,HudManager.COORDINATES,HudManager.TARGET_HUD,HudManager.CLOCK,
                HudManager.SESSION_INFO,HudManager.MOTION_GRAPH,HudManager.STALKER,HudManager.ARMOR,
                HudManager.INVENTORY,HudManager.HEALTH,HudManager.CPS,HudManager.CPS_GRAPH,
                HudManager.SCOREBOARD,HudManager.NOTIFICATIONS,HudManager.ARRAY_LIST,HudManager.MUSIC,
                HudManager.CHAT,HudManager.HOTBAR,HudManager.TAB};
        frame(1280,720);
        for(int i=0;i<ids.length;i++){
            HudManager.HudElement e=manager.getElement(ids[i]);e.setTheme("Lavalamp");e.setScale(.9F);
            manager.drawPreview(e,mc.fontRendererObj);
            e.moveTo(15+(i%4)*315,12+(i/4)*140,new ScaledResolution(mc));
        }
        frame(1280,720);
        for(String id:ids)manager.drawPreview(manager.getElement(id),mc.fontRendererObj);
        save(output.resolve("lavalamp-all-hud-elements.png"),1280,720);
        HudEditorGui editor=new HudEditorGui(manager);editor.setWorldAndResolution(mc,1280,720);
        Field selected=HudEditorGui.class.getDeclaredField("selected");selected.setAccessible(true);
        selected.set(editor,manager.getElement(HudManager.SCOREBOARD));
        frame(1280,720);editor.drawScreen(-1,-1,0);save(output.resolve("hud-editor-lavalamp.png"),1280,720);
        editor.onGuiClosed();
        check(GL11.glGetError()==0,"Lavalamp HUD widgets left an OpenGL error");
    }
    private static void release(HudEditorGui gui,int x,int y)throws Exception{Method method=HudEditorGui.class.getDeclaredMethod("mouseReleased",int.class,int.class,int.class);method.setAccessible(true);method.invoke(gui,x,y,0);}
    private static void editorMovementAndThemes(Minecraft mc,HudManager manager,HudModule hud,Path output)throws Exception{
        HudManager.HudElement chat=manager.getElement(HudManager.CHAT);hud.chat.width.setValue(320D);hud.chat.height.setValue(150D);chat.setScale(.65F);
        frame(1280,720);manager.drawPreview(chat,mc.fontRendererObj);chat.moveTo(260,20,new ScaledResolution(mc));
        frame(1280,720);manager.drawPreview(chat,mc.fontRendererObj);
        check(Math.abs(chat.getTop()-20)<=1,"Fractional-scale chat reset its position near the top of the screen");
        HudEditorGui gui=new HudEditorGui(manager);gui.setWorldAndResolution(mc,1280,720);
        Field selected=HudEditorGui.class.getDeclaredField("selected");selected.setAccessible(true);selected.set(gui,manager.getElement(HudManager.TARGET_HUD));
        hud.targetHud.mode.setValue("Novoline");frame(1280,720);gui.drawScreen(-1,-1,0);
        Field canvas=HudEditorGui.class.getDeclaredField("canvasY");canvas.setAccessible(true);int before=canvas.getInt(gui);
        Method bounds=HudEditorGui.class.getDeclaredMethod("bounds",HudManager.HudElement.class);bounds.setAccessible(true);int[] box=(int[])bounds.invoke(gui,chat);
        int x=(box[0]+box[2])/2,y=(box[1]+box[3])/2,oldX=chat.getLeft(),oldY=chat.getTop();
        GuiInput.mouseClicked(gui,x,y,0);frame(1280,720);gui.drawScreen(x,y,0);release(gui,x,y);
        check(selected.get(gui)==chat,"Chat could not be selected in the preview");check(Math.abs(chat.getLeft()-oldX)<=1&&Math.abs(chat.getTop()-oldY)<=1,"Clicking chat moved it without a drag");
        check(canvas.getInt(gui)==before,"Selecting a differently sized theme list moved the preview canvas");
        selected.set(gui,manager.getElement(HudManager.TARGET_HUD));frame(1280,720);gui.drawScreen(-1,-1,0);
        Field left=HudEditorGui.class.getDeclaredField("themeLeft"),top=HudEditorGui.class.getDeclaredField("themeTop"),scroll=HudEditorGui.class.getDeclaredField("themeScroll");
        left.setAccessible(true);top.setAccessible(true);scroll.setAccessible(true);
        Method wheel=HudEditorGui.class.getDeclaredMethod("scrollAt",int.class,int.class,int.class);wheel.setAccessible(true);
        for(int i=0;i<10;i++)wheel.invoke(gui,left.getInt(gui)+20,top.getInt(gui)+5,1);
        check(scroll.getInt(gui)>0,"Theme panel did not scroll independently");
        frame(1280,720);gui.drawScreen(-1,-1,0);save(output.resolve("hud-editor-scrolled-themes.png"),1280,720);
        Field hits=HudEditorGui.class.getDeclaredField("hits");hits.setAccessible(true);boolean selectedLast=false;
        // The last theme must remain a real clickable control after scrolling.
        for(Object hit:(java.util.List<?>)hits.get(gui)){
            Class<?> type=hit.getClass();Field l=type.getDeclaredField("left"),t=type.getDeclaredField("top"),r=type.getDeclaredField("right"),b=type.getDeclaredField("bottom");
            l.setAccessible(true);t.setAccessible(true);r.setAccessible(true);b.setAccessible(true);
            if(l.getInt(hit)>=left.getInt(gui)&&t.getInt(hit)>=top.getInt(gui)){
                GuiInput.mouseClicked(gui,(l.getInt(hit)+r.getInt(hit))/2,(t.getInt(hit)+b.getInt(hit))/2,0);
                if(hud.targetHud.mode.is("LiquidBounce")){selectedLast=true;break;}
            }
        }
        check(selectedLast,"Scrolled Target HUD themes could not select the final mode");
        gui.onGuiClosed();chat.setScale(1);
    }
    private static void liveChat(Minecraft mc,HudManager manager,HudModule hud,Path output)throws Exception{
        dev.vibe.path.PathTestFixture.set(dev.vibe.Vibe.class,dev.vibe.Vibe.getInstance(),"hudManager",manager);
        net.minecraft.client.multiplayer.WorldClient world=mc.theWorld;net.minecraft.client.entity.EntityPlayerSP player=mc.thePlayer;
        net.minecraft.client.gui.GuiIngame ingame=mc.ingameGUI;net.minecraft.client.gui.GuiScreen screen=mc.currentScreen;
        boolean selected=hud.getHudElements().isSelectedIgnoreCase(HudManager.CHAT);
        try{
            mc.theWorld=dev.vibe.path.PathTestFixture.allocate(net.minecraft.client.multiplayer.WorldClient.class);
            mc.thePlayer=dev.vibe.path.PathTestFixture.allocate(net.minecraft.client.entity.EntityPlayerSP.class);
            mc.ingameGUI=dev.vibe.path.PathTestFixture.allocate(net.minecraft.client.gui.GuiIngame.class);
            net.minecraft.client.gui.GuiNewChat chat=new net.minecraft.client.gui.GuiNewChat(mc);
            dev.vibe.path.PathTestFixture.set(net.minecraft.client.gui.GuiIngame.class,mc.ingameGUI,"persistantChatGUI",chat);
            mc.currentScreen=new net.minecraft.client.gui.GuiChat();if(!selected)hud.getHudElements().toggle(HudManager.CHAT);
            dev.vibe.path.PathTestFixture.set(dev.vibe.module.Module.class,hud,"enabled",true);
            hud.chat.width.setValue(320D);hud.chat.height.setValue(150D);HudManager.HudElement e=manager.getElement(HudManager.CHAT);e.setScale(1);e.setTheme("Vibe");
            chat.printChatMessage(new net.minecraft.util.ChatComponentText("First message"));chat.printChatMessage(new net.minecraft.util.ChatComponentText("Second message"));
            frame(1280,720);manager.drawChat();Thread.sleep(200);frame(1280,720);manager.drawChat();
            check(e.getHeight()==34,"Open chat reserved empty space instead of fitting two messages");
            net.minecraft.client.gui.GuiTextField input=new net.minecraft.client.gui.GuiTextField(0,mc.fontRendererObj,0,0,320,14);input.setMaxStringLength(256);input.setFocused(true);input.writeText("Hello world");HudChat.drawInput(input,1280,720);
            check(input.getText().equals("Hello world")&&input.xPosition>=4&&input.yPosition==700,"Styled input changed editing or escaped its panel");save(output.resolve("custom-chat-live.png"),1280,720);
            // Hold a half-open animation. The glass shader ignores modelview translations,
            // so its scissor and the geometry border must both use the animated coordinates.
            e.setTheme("LiquidGlass");mc.currentScreen=null;
            freezeMotion("motion",.5F,1);freezeMotion("openMotion",0,0);freezeMotion("heightMotion",34,34);
            frame(1280,720);int background=pixel(2,2,720);manager.drawChat();
            int center=e.getLeft()+e.getWidth()/2;
            for(int delta=2;delta<=8;delta++){check(pixel(center,e.getTop()-delta,720)==background,"Glass chat drew a second top edge outside its animated bounds");check(pixel(center,e.getTop()+e.getHeight()+delta,720)==background,"Glass chat drew a second bottom edge outside its animated bounds");}
            save(output.resolve("custom-chat-glass-moving.png"),1280,720);mc.currentScreen=new net.minecraft.client.gui.GuiChat();e.setTheme("Vibe");
            chat.clearChatMessages();frame(1280,720);manager.drawChat();Thread.sleep(200);frame(1280,720);manager.drawChat();
            Field height=HudChat.class.getDeclaredField("shownHeight");height.setAccessible(true);check(height.getInt(null)==0,"Empty chat kept an expanded panel");
        }finally{mc.theWorld=world;mc.thePlayer=player;mc.ingameGUI=ingame;mc.currentScreen=screen;if(!selected)hud.getHudElements().toggle(HudManager.CHAT);}
    }
    private static void frame(int w,int h){GL11.glViewport(0,0,w,h);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glClearColor(.045F,.055F,.075F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,w,h,0,-1000,1000);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();}
    private static void freezeMotion(String name,float from,float target)throws Exception{Field field=HudChat.class.getDeclaredField(name);field.setAccessible(true);Object motion=field.get(null);for(String key:new String[]{"from","value","target"}){Field value=OverlayMotion.class.getDeclaredField(key);value.setAccessible(true);value.setFloat(motion,key.equals("target")?target:from);}Field time=OverlayMotion.class.getDeclaredField("started");time.setAccessible(true);time.setLong(motion,System.nanoTime()+1000000000L);}
    private static int pixel(int x,int y,int h){ByteBuffer value=BufferUtils.createByteBuffer(4);GL11.glReadPixels(x,h-1-y,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,value);return (value.get(0)&255)<<16|(value.get(1)&255)<<8|value.get(2)&255;}
    private static void save(Path file,int w,int h)throws Exception{ByteBuffer data=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=((h-1-y)*w+x)*4;image.setRGB(x,y,(data.get(i)&255)<<16|(data.get(i+1)&255)<<8|data.get(i+2)&255);}ImageIO.write(image,"png",file.toFile());}
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
}
