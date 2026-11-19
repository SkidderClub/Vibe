package dev.vibe.hud;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
/** Adjust's Copy/Delete actions, retaining vanilla's styled original message and wrapped-line identities. */
public final class ChatActions {
    private static final Field DRAWN=ReflectionHelper.findField(GuiNewChat.class,"drawnChatLines","field_146253_i"),HISTORY=ReflectionHelper.findField(GuiNewChat.class,"chatLines","field_146252_h"),SCROLL=ReflectionHelper.findField(GuiNewChat.class,"scrollPos","field_146250_j");
    private static final Map<Object,IChatComponent> ROOTS=new WeakHashMap<>();
    private static final ResourceLocation COPY=new ResourceLocation("vibe","textures/chat/copy.png"),REMOVE=new ResourceLocation("vibe","textures/chat/delete.png");
    private static final List<Row> CUSTOM=new ArrayList<>();
    private static final FloatBuffer MATRIX=BufferUtils.createFloatBuffer(16);
    private static float sx=1,sy=1,tx,ty;
    private ChatActions(){}
    /** Injected immediately after ChatLine construction in GuiNewChat.setChatLine. */
    public static void track(Object line,Object original){if(original instanceof IChatComponent)ROOTS.put(line,(IChatComponent)original);}
    public static void beginCustom(){CUSTOM.clear();MATRIX.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,MATRIX);sx=MATRIX.get(0);sy=MATRIX.get(5);tx=MATRIX.get(12);ty=MATRIX.get(13);}
    public static void customRow(ChatLine line,float left,float top,float right,float bottom){if(line!=null&&bottom>top)CUSTOM.add(new Row(line,tx+left*sx,ty+top*sy,tx+right*sx,ty+bottom*sy,Math.max(.1F,sx)));}
    @SuppressWarnings("unchecked") private static List<ChatLine> list(Field f,GuiNewChat chat){try{return (List<ChatLine>)f.get(chat);}catch(IllegalAccessException e){throw new IllegalStateException("Cannot access chat history",e);}}
    private static int scroll(GuiNewChat chat){try{return SCROLL.getInt(chat);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    private static List<Row> rows(){
        Minecraft mc=Minecraft.getMinecraft();if(!(mc.currentScreen instanceof GuiChat)||mc.ingameGUI==null||mc.gameSettings.chatVisibility==EntityPlayer.EnumChatVisibility.HIDDEN)return Collections.emptyList();
        if(HudChat.active())return CUSTOM;
        GuiNewChat chat=mc.ingameGUI.getChatGUI();List<ChatLine> lines=list(DRAWN,chat);int start=scroll(chat),height=new ScaledResolution(mc).getScaledHeight();float scale=chat.getChatScale();
        List<Row> rows=new ArrayList<>();for(int i=0;i<chat.getLineCount()&&i+start<lines.size();i++){float top=height-28-(i+1)*9*scale;rows.add(new Row(lines.get(i+start),2,top,chat.getChatWidth()+5*scale,top+9*scale,scale));}return rows;
    }
    private static Row hovered(int mx,int my){for(Row row:rows())if(mx>=row.left&&mx<row.actionsX()+20*row.scale&&my>=row.top&&my<row.bottom)return row;return null;}
    public static List<String> draw(int mx,int my){
        Row row=hovered(mx,my);if(row==null)return Collections.emptyList();float x=row.actionsX(),size=9*row.scale;
        icon(COPY,x,row.top,size,mx,my);icon(REMOVE,x+10*row.scale,row.top,size,mx,my);
        if(mx>=x&&mx<x+size)return Arrays.asList("Copy message","Click: full message","Ctrl-click: this line","Alt: include formatting");
        if(mx>=x+10*row.scale&&mx<x+19*row.scale)return Arrays.asList("Remove from history","Click: full message","Ctrl-click: this line");
        return Collections.emptyList();
    }
    private static void icon(ResourceLocation image,float x,float y,float size,int mx,int my){
        Gui.drawRect((int)x,(int)y,(int)Math.ceil(x+size),(int)Math.ceil(y+size),mx>=x&&mx<x+size&&my>=y&&my<y+size?0x80FFFFFF:0x80000000);
        Minecraft mc=Minecraft.getMinecraft();GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.color(1,1,1,mc.gameSettings.chatOpacity*.9F+.1F);mc.getTextureManager().bindTexture(image);
        GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(0,0);GL11.glVertex2f(x,y);GL11.glTexCoord2f(0,1);GL11.glVertex2f(x,y+size);GL11.glTexCoord2f(1,1);GL11.glVertex2f(x+size,y+size);GL11.glTexCoord2f(1,0);GL11.glVertex2f(x+size,y);GL11.glEnd();GlStateManager.color(1,1,1,1);
    }
    public static boolean click(int mx,int my,int button){
        if(button!=0)return false;Row row=hovered(mx,my);if(row==null)return false;float x=row.actionsX(),size=9*row.scale;
        boolean keys=org.lwjgl.input.Keyboard.isCreated();
        if(mx>=x&&mx<x+size){GuiScreen.setClipboardString(copy(row.line,keys&&GuiScreen.isCtrlKeyDown(),keys&&GuiScreen.isAltKeyDown()));return true;}
        if(mx>=x+10*row.scale&&mx<x+19*row.scale){remove(Minecraft.getMinecraft().ingameGUI.getChatGUI(),row.line,keys&&GuiScreen.isCtrlKeyDown());return true;}return false;
    }
    public static String copy(ChatLine line,boolean single,boolean formatting){IChatComponent message=single?line.getChatComponent():root(line);return formatting?message.getFormattedText():EnumChatFormatting.getTextWithoutFormattingCodes(message.getUnformattedText());}
    private static IChatComponent root(ChatLine line){IChatComponent root=ROOTS.get(line);return root==null?line.getChatComponent():root;}
    public static boolean remove(GuiNewChat chat,ChatLine line,boolean single){
        List<ChatLine> drawn=list(DRAWN,chat);if(!drawn.contains(line))return false;
        IChatComponent original=root(line);if(single)drawn.remove(line);else{drawn.removeIf(l->l==line||root(l)==original);list(HISTORY,chat).removeIf(l->root(l)==original||l.getChatComponent()==original);}
        int max=Math.max(0,drawn.size()-chat.getLineCount());try{SCROLL.setInt(chat,Math.min(max,scroll(chat)));}catch(IllegalAccessException e){throw new IllegalStateException(e);}CUSTOM.clear();return true;
    }
    private static final class Row{
        final ChatLine line;final float left,top,right,bottom,scale;
        Row(ChatLine l,float x,float y,float r,float b,float s){line=l;left=x;top=y;right=r;bottom=b;scale=s;}
        float actionsX(){return Math.min(right+2,new ScaledResolution(Minecraft.getMinecraft()).getScaledWidth()-20*scale-2);}
    }
}
