package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.ui.RenderUtils;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Draws vanilla's wrapped, styled chat history; GuiChat still owns editing, scrolling and link actions. */
public final class HudChat {
    private static final Field LINES=ReflectionHelper.findField(GuiNewChat.class,"drawnChatLines","field_146253_i");
    private static final Field SCROLL=ReflectionHelper.findField(GuiNewChat.class,"scrollPos","field_146250_j");
    private static final Field INPUT_WIDTH=ReflectionHelper.findField(GuiTextField.class,"width","field_146218_h");
    private static int wrappedWidth=-1;
    private static GuiNewChat wrappedChat;
    private static final List<IChatComponent> shown=new ArrayList<>();
    private static final List<IChatComponent> previewLines=new ArrayList<>();
    private static int previewWidth=-1;
    private static FontRenderer previewFont;
    private static final OverlayMotion motion=new OverlayMotion();
    private static int shownHeight;
    private static final OverlayMotion openMotion=new OverlayMotion();
    private static final OverlayMotion heightMotion=new OverlayMotion();
    private static float textOffset;
    private HudChat(){}
    public static void drawInput(GuiTextField field,int screenWidth,int screenHeight){
        HudManager manager=Vibe.getInstance().getHudManager();HudModule h=hud();
        HudManager.HudElement e=manager.getElement(HudManager.CHAT);OverlaySettings.Chat s=h.chat;
        int w=Math.min(screenWidth-8,Math.round(s.width.getFloat()*e.getScale())),x=Math.max(4,Math.min(screenWidth-w-4,e.getLeft())),y=screenHeight-28;
        HudOverlayRenderer.panel(manager,h,e,s,x,y,w,24);
        field.xPosition=x+8;field.yPosition=y+8;
        try{INPUT_WIDTH.setInt(field,w-16);}catch(IllegalAccessException error){throw new IllegalStateException(error);}
        field.setEnableBackgroundDrawing(false);field.setTextColor(s.text.getArgb());field.drawTextBox();
        if(field.getText().isEmpty())Minecraft.getMinecraft().fontRendererObj.drawString("Message",x+8,y+8,0xFF8A96A8,false);
    }
    public static boolean active(){Minecraft mc=Minecraft.getMinecraft();return HudManager.replaces(HudManager.CHAT)&&mc.gameSettings.chatVisibility!=EntityPlayer.EnumChatVisibility.HIDDEN;}
    private static HudModule hud(){return Vibe.getInstance().getModuleManager().getModule(HudModule.class);}
    private static float scale(){return Vibe.getInstance().getHudManager().getElement(HudManager.CHAT).getScale();}
    public static int width(int original){return active()?Math.round((hud().chat.width.getInt()-12)*scale()):original;}
    public static float scale(float original){return active()?scale():original;}
    public static int lines(int original){return active()?Math.max(1,(hud().chat.height.getInt()-12)/hud().chat.lineHeight.getInt()):original;}
    public static void restoreVanilla(){if(!active()&&wrappedChat!=null){GuiNewChat old=wrappedChat;wrappedChat=null;wrappedWidth=-1;shown.clear();old.refreshChat();}}

    @SuppressWarnings("unchecked") static void draw(HudManager manager,HudModule hud,HudManager.HudElement e,ScaledResolution res,boolean preview){
        Minecraft mc=Minecraft.getMinecraft();OverlaySettings.Chat s=hud.chat;FontRenderer font=mc.fontRendererObj;
        int w=s.width.getInt(),h=s.height.getInt(),lineHeight=s.lineHeight.getInt(),capacity=Math.max(1,(h-12)/lineHeight);
        e.ensureOnScreen(res,w,h);int x=e.left(res,w),y=e.top(res,h);e.setBounds(x,y,w,h);
        GuiNewChat chat=mc.ingameGUI==null?null:mc.ingameGUI.getChatGUI();List<ChatLine> history=Collections.emptyList();int scroll=0;
        if(chat!=null&&!preview){
            int width=w-12;if(chat!=wrappedChat||wrappedWidth!=width){wrappedChat=chat;wrappedWidth=width;chat.refreshChat();}
            try{history=(List<ChatLine>)LINES.get(chat);scroll=SCROLL.getInt(chat);}catch(IllegalAccessException error){throw new IllegalStateException("Cannot read vanilla chat history",error);}
        }
        boolean open=mc.currentScreen instanceof GuiChat;int counter=mc.ingameGUI==null?0:mc.ingameGUI.getUpdateCounter();
        List<IChatComponent> components=new ArrayList<>();List<Float> opacity=new ArrayList<>();float maxAlpha=0;
        if(preview&&(previewWidth!=w||previewFont!=font)){
            previewWidth=w;previewFont=font;previewLines.clear();
            for(String message:new String[]{"§bWelcome to Vibe.","§7Player: §fChat keeps its original colours.","§aClickable links and scrolling still work.","§7Drag the side handles to change width or height.","§fYour messages appear here."}){
                List<IChatComponent> split=GuiUtilRenderComponents.splitText(new ChatComponentText(message),w-12,font,false,false);Collections.reverse(split);previewLines.addAll(split);
            }
        }
        int count=preview?Math.min(capacity,previewLines.size()):Math.min(capacity,Math.max(0,history.size()-scroll));
        for(int i=0;i<count;i++){
            ChatLine line=preview?null:history.get(i+scroll);float fade=1;
            if(!preview&&!open&&s.fade.isEnabled()){float age=counter-line.getUpdatedCounter();fade=Math.max(0,Math.min(1,(1-age/(s.fadeSeconds.getFloat()*20))*10));fade*=fade;}
            fade*=s.opacity.getFloat()*(mc.gameSettings.chatOpacity*.9F+.1F);maxAlpha=Math.max(maxAlpha,fade);
            components.add(preview?previewLines.get(i):line.getChatComponent());opacity.add(fade);
        }
        while(!components.isEmpty()&&opacity.get(opacity.size()-1)<.01F){components.remove(components.size()-1);opacity.remove(opacity.size()-1);}
        if(!preview){shown.clear();shown.addAll(components);}
        float opening=preview?1:motion.update(open||!components.isEmpty());
        int desired=components.isEmpty()?0:Math.min(h,components.size()*lineHeight+12);
        int used=preview?h:Math.max(1,Math.round(heightMotion.update((float)desired))),top=y+h-used;
        if(maxAlpha<=.01F&&!preview){shownHeight=0;return;}
        maxAlpha*=opening;
        shownHeight=used;
        if(!preview)e.setBounds(x,top,w,used);
        textOffset=preview?0:3*(1-openMotion.update(open));
        GlStateManager.pushMatrix();GlStateManager.translate(0,textOffset+6*(1-opening),0);
        if("LiquidGlass".equals(manager.theme(e,hud)))manager.drawOverlayGlass(hud,s,x,top,w,used,maxAlpha);
        else if(!"Transparent".equals(manager.theme(e,hud))){
            boolean skeet="Skeet".equals(manager.theme(e,hud));float radius=skeet?1:s.radius.getFloat();
            HudShapes.round(x,top,w,used,radius,RenderUtils.alpha(s.background.getArgb(),Math.round((s.background.getArgb()>>>24)*maxAlpha)));
            if(s.border.isEnabled())RenderUtils.roundedOutline(x,top,x+w,y+h,radius,1,RenderUtils.alpha(s.accent.getArgb(),Math.round(65*maxAlpha)));
            if(skeet)Gui.drawRect(x+2,top+2,x+w-2,top+3,RenderUtils.alpha(s.accent.getArgb(),Math.round(255*maxAlpha)));
        }
        try(dev.vibe.ui.GuiClip clip=dev.vibe.ui.GuiClip.local(x+4,top+4,w-8,Math.max(0,used-8))){
        for(int i=0;i<components.size();i++){
            int alpha=Math.round(255*opacity.get(i)*opening);if(alpha<4)continue;
            String text=s.keepColors.isEnabled()?components.get(i).getFormattedText():EnumChatFormatting.getTextWithoutFormattingCodes(components.get(i).getFormattedText());
            font.drawString(text,x+6,y+h-6-(i+1)*lineHeight+(lineHeight-font.FONT_HEIGHT)/2F,alpha<<24|s.text.getArgb()&0xFFFFFF,s.shadow.isEnabled());
        }
        }
        if(s.scrollbar.isEnabled()&&open&&history.size()>capacity){float track=used-12,thumb=Math.max(12,track*capacity/history.size());float yy=top+6+(track-thumb)*(1-scroll/(float)Math.max(1,history.size()-capacity));HudShapes.round(x+w-3,yy,2,thumb,1,s.accent.getArgb());}
        GlStateManager.popMatrix();
    }

    public static Object component(int mouseX,int mouseY){
        if(!active())return null;Minecraft mc=Minecraft.getMinecraft();if(!(mc.currentScreen instanceof GuiChat))return null;
        HudManager.HudElement e=Vibe.getInstance().getHudManager().getElement(HudManager.CHAT);ScaledResolution res=new ScaledResolution(mc);float scale=e.getScale();
        float x=(mouseX/(float)res.getScaleFactor()-e.getLeft())/scale-6;
        float y=(res.getScaledHeight()-1-mouseY/(float)res.getScaleFactor()-e.getTop())/scale-textOffset;
        if(x<0||x>hud().chat.width.getInt()-12||y<6||y>shownHeight-6)return null;
        int line=(int)((shownHeight-6-1-y)/hud().chat.lineHeight.getFloat());if(line<0||line>=shown.size())return null;
        float advance=0;for(IChatComponent part:shown.get(line)){
            // getFormattedText includes siblings; measure only this span so a root component cannot swallow link hits.
            String span=hud().chat.keepColors.isEnabled()?part.getChatStyle().getFormattingCode()+part.getUnformattedTextForChat():EnumChatFormatting.getTextWithoutFormattingCodes(part.getUnformattedTextForChat());
            advance+=mc.fontRendererObj.getStringWidth(span);if(advance>x)return part;
        }
        return null;
    }
}
