package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.module.impl.client.NameProtectModule;
import dev.vibe.ui.RenderUtils;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.scoreboard.*;
import net.minecraft.util.*;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Shared native-item and text drawing. Liquid glass is composed once per widget, never per slot or row. */
final class HudOverlayRenderer {
    private final Minecraft mc=Minecraft.getMinecraft();
    private ItemStack[] samples;
    private static final String[] SAMPLE_NAMES={"Diamond Sword","Bow","Golden Apple","Ender Pearl","Diamond Pickaxe","","","",""};
    private final List<NetworkPlayerInfo> players=new ArrayList<>();
    private long nextPlayers;
    private final TabKeyState tabKey=new TabKeyState();
    private boolean visible;
    private final OverlayMotion tabMotion=new OverlayMotion();
    private float tabAmount;
    private float selection=-1;
    private long lastSelection;
    private Object healthOwner;
    private float healthFraction=-1,absorptionFraction;
    private long healthAt;
    private static final ResourceLocation WIDGETS=new ResourceLocation("textures/gui/widgets.png");
    private static final Field HEADER=ReflectionHelper.findField(GuiPlayerTabOverlay.class,"header","field_175256_i");
    private static final Field FOOTER=ReflectionHelper.findField(GuiPlayerTabOverlay.class,"footer","field_175255_h");
    private static final Field HIGHLIGHT=ReflectionHelper.findField(GuiIngame.class,"remainingHighlightTicks","field_92017_k");

    void updateTabKey(HudModule hud){
        boolean allowed=hud!=null&&hud.isEnabled()&&hud.getHudElements().isSelectedIgnoreCase(HudManager.TAB)&&mc.theWorld!=null&&mc.currentScreen==null&&mc.inGameHasFocus;
        boolean pressed=dev.vibe.input.VanillaClicks.physicallyDown(mc.gameSettings.keyBindPlayerList);
        visible=tabKey.update(pressed,hud!=null&&hud.tab.toggle.isEnabled(),allowed,mc.theWorld);
        tabAmount=tabMotion.update(visible);
    }
    boolean tabVisible(){return tabAmount>.001F;}

    static void panel(HudManager manager,HudModule hud,HudManager.HudElement e,OverlaySettings settings,int x,int y,int w,int h){
        String theme=manager.theme(e,hud);if("Transparent".equals(theme))return;
        if("LiquidGlass".equals(theme)||"Lavalamp".equals(theme)){manager.drawOverlayGlass(hud,e,settings,x,y,w,h,1);return;}
        HudShapes.round(x,y,w,h,"Skeet".equals(theme)?1:settings.radius.getFloat(),settings.background.getArgb());
        if(settings.border.isEnabled())RenderUtils.roundedOutline(x,y,x+w,y+h,"Skeet".equals(theme)?1:settings.radius.getFloat(),1,RenderUtils.alpha(settings.accent.getArgb(),90));
        if("Skeet".equals(theme))Gui.drawRect(x+2,y+2,x+w-2,y+3,settings.accent.getArgb());
    }
    private void text(String s,float x,float y,int color,boolean shadow){mc.fontRendererObj.drawString(s,x,y,color,shadow);}

    void hotbar(HudManager manager,HudModule hud,HudManager.HudElement e,ScaledResolution res,boolean preview){
        OverlaySettings.Hotbar s=hud.hotbar;String theme=manager.theme(e,hud);boolean vanilla="Minecraft".equals(theme),fortnite="Fortnite".equals(theme);
        int cell=vanilla?20:s.slotSize.getInt(),gap=vanilla?0:s.gap.getInt(),pad=vanilla?1:5,label=vanilla?0:s.numbers.isEnabled()?10:0;
        boolean horse=mc.thePlayer!=null&&mc.thePlayer.isRidingHorse();
        boolean xp=s.experience.isEnabled()&&(preview||mc.playerController==null||mc.playerController.shouldDrawHUD()||horse);
        int extra=xp?14:0,w=vanilla?182:pad*2+9*cell+8*gap,h=(vanilla?22:cell+pad*2+label)+extra;
        e.ensureOnScreen(res,w,h);int x=e.left(res,w),widgetTop=e.top(res,h),y=widgetTop+extra,slot=mc.thePlayer==null||mc.thePlayer.inventory==null?0:mc.thePlayer.inventory.currentItem;
        if(vanilla){mc.getTextureManager().bindTexture(WIDGETS);GlStateManager.color(1,1,1,1);Gui.drawModalRectWithCustomSizedTexture(x,y,0,0,182,22,256,256);Gui.drawModalRectWithCustomSizedTexture(x-1+slot*20,y-1,0,22,24,22,256,256);}
        else{
            panel(manager,hud,e,s,x,y,w,h-extra);
            long now=System.nanoTime();double dt=lastSelection==0?.05:Math.min(.1,(now-lastSelection)/1e9);lastSelection=now;
            if(selection<0||!s.animate.isEnabled())selection=slot;else selection+=(slot-selection)*(1-(float)Math.exp(-dt*22));
            for(int i=0;i<9;i++){
                int xx=x+pad+i*(cell+gap),yy=y+pad;ItemStack stack=stack(i,preview);int color=fortnite?rarity(stack,s):s.empty.getArgb();
                HudShapes.round(xx,yy,cell,cell,fortnite?1:3,fortnite?RenderUtils.alpha(color,145):color);
                if(fortnite)Gui.drawRect(xx,yy+cell-3,xx+cell,yy+cell,color);
                if(s.numbers.isEnabled())text(net.minecraft.client.settings.GameSettings.getKeyDisplayString(mc.gameSettings.keyBindsHotbar[i].getKeyCode()),xx+2,yy+cell+2,s.text.getArgb(),s.shadow.isEnabled());
            }
            float sx=x+pad+selection*(cell+gap);RenderUtils.roundedOutline(Math.round(sx),y+pad,Math.round(sx)+cell,y+pad+cell,fortnite?1:3,1.4F,s.selected.getArgb());
        }
        RenderHelper.enableGUIStandardItemLighting();GlStateManager.enableDepth();
        try{for(int i=0;i<9;i++){ItemStack item=stack(i,preview);if(item==null)continue;int xx=x+pad+i*(cell+gap)+(cell-16)/2,yy=y+pad+(cell-16)/2;
            mc.getRenderItem().renderItemAndEffectIntoGUI(item,xx,yy);
        }}finally{RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.enableBlend();GlStateManager.color(1,1,1,1);}
        if(s.counts.isEnabled())for(int i=0;i<9;i++){ItemStack item=stack(i,preview);if(item==null||item.stackSize==1)continue;String value=(item.stackSize<1?"§c":"")+item.stackSize;int xx=x+pad+i*(cell+gap)+(cell-16)/2,yy=y+pad+(cell-16)/2;text(value,xx+17-mc.fontRendererObj.getStringWidth(value),yy+9,0xFFFFFFFF,true);}
        if(s.durability.isEnabled())for(int i=0;i<9;i++){ItemStack item=stack(i,preview);if(item==null||!item.isItemDamaged())continue;int xx=x+pad+i*(cell+gap),yy=y+pad+cell-2;Gui.drawRect(xx+2,yy,xx+cell-2,yy+1,0xB0000000);Gui.drawRect(xx+2,yy,xx+2+Math.round((cell-4)*(1-item.getItemDamage()/(float)item.getMaxDamage())),yy+1,s.accent.getArgb());}
        dev.vibe.module.impl.world.AutoToolModule tool=Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.world.AutoToolModule.class);
        if(tool!=null&&tool.hasSilentSlot()){int xx=x+pad+tool.getSpoofedSlot()*(cell+gap);RenderUtils.roundedOutline(xx,y+pad,xx+cell,y+pad+cell,2,1,tool.getSilentColor().getArgb());}
        if(xp){int level=mc.thePlayer==null?17:mc.thePlayer.experienceLevel;float value=horse?mc.thePlayer.getHorseJumpPower():mc.thePlayer==null?.6F:mc.thePlayer.experience;
            Gui.drawRect(x+pad,widgetTop+10,x+w-pad,widgetTop+13,s.empty.getArgb());Gui.drawRect(x+pad,widgetTop+10,x+pad+Math.round((w-pad*2)*Math.max(0,Math.min(1,value))),widgetTop+13,horse?s.selected.getArgb():s.experienceColor.getArgb());
            if(!horse&&level>0){String caption=String.valueOf(level);text(caption,x+(w-mc.fontRendererObj.getStringWidth(caption))/2F,widgetTop,s.experienceColor.getArgb(),true);}
        }
        int ticks=preview?40:highlightTicks();ItemStack selected=stack(slot,preview);
        if(s.itemName.isEnabled()&&selected!=null&&ticks>0){String title=samples!=null&&selected==samples[slot]?SAMPLE_NAMES[slot]:(selected.hasDisplayName()?"§o":"")+selected.getDisplayName();int opacity=Math.min(255,ticks*256/10);text(title,x+(w-mc.fontRendererObj.getStringWidth(title))/2F,widgetTop-12,opacity<<24|s.text.getArgb()&0xFFFFFF,s.shadow.isEnabled());}
        e.setBounds(x,widgetTop,w,h);
    }
    private int highlightTicks(){try{return mc.ingameGUI==null?0:HIGHLIGHT.getInt(mc.ingameGUI);}catch(IllegalAccessException e){return 0;}}
    private ItemStack stack(int slot,boolean preview){
        ItemStack actual=mc.thePlayer==null||mc.thePlayer.inventory==null?null:mc.thePlayer.inventory.mainInventory[slot];
        if(actual!=null||!preview)return actual;
        if(samples==null)samples=new ItemStack[]{new ItemStack(Items.diamond_sword),new ItemStack(Items.bow),new ItemStack(Items.golden_apple,8),new ItemStack(Items.ender_pearl,16),new ItemStack(Items.diamond_pickaxe),null,null,null,null};
        return samples[slot];
    }
    private static int rarity(ItemStack stack,OverlaySettings.Hotbar s){if(stack==null)return s.empty.getArgb();switch(stack.getRarity()){case EPIC:return s.epic.getArgb();case RARE:return s.rare.getArgb();case UNCOMMON:return s.uncommon.getArgb();default:return s.common.getArgb();}}

    void health(HudManager manager,HudModule hud,HudManager.HudElement e,ScaledResolution res,boolean preview){
        OverlaySettings.Health s=hud.customHealth;EntityPlayer p=mc.thePlayer;
        float hp=p==null?16:Math.max(0,p.getHealth()),max=p==null?20:Math.max(1,p.getMaxHealth()),abs=s.absorption.isEnabled()?(p==null?4:Math.max(0,p.getAbsorptionAmount())):0;
        int armor=p==null?15:p.inventory==null?0:p.getTotalArmorValue(),food=p==null?17:p.getFoodStats().getFoodLevel();
        boolean fortnite=s.mode.is("Fortnite"),nativeIcons=s.mode.is("Fancy");int w=fortnite?s.width.getInt():182,bar=s.height.getInt();
        long now=System.nanoTime();float amount=healthAt==0?1:1-(float)Math.exp(-Math.min(.15,(now-healthAt)/1e9)*s.speed.getDouble());healthAt=now;
        if(healthOwner!=p||healthFraction<0||!s.smooth.isEnabled()){healthFraction=hp/max;absorptionFraction=abs/Math.max(20,max);healthOwner=p;}
        else{healthFraction+=(hp/max-healthFraction)*amount;absorptionFraction+=(abs/Math.max(20,max)-absorptionFraction)*amount;}
        int hearts=Math.min(100,(int)Math.ceil(max/2)),extra=Math.min(20,(int)Math.ceil(abs/2)),rows=Math.max(1,(hearts+extra+9)/10);
        int armorHeight=s.armor.isEnabled()&&armor>0?11:0;
        int h=fortnite?bar*2+17+armorHeight:rows*10+(armorHeight>0?10:0);
        e.ensureOnScreen(res,w,h);int x=e.left(res,w),y=e.top(res,h),color=RenderUtils.blend(s.low.getArgb(),s.high.getArgb(),Math.min(1,hp/max));
        if(fortnite){
            int by=y+armorHeight;
            HudShapes.round(x,by,w,bar,2,s.empty.getArgb());HudShapes.round(x,by,w*Math.min(1,absorptionFraction),bar,2,s.shield.getArgb());
            HudShapes.round(x,by+bar+3,w,bar,2,s.empty.getArgb());HudShapes.gradient(x,by+bar+3,w*Math.min(1,healthFraction),bar,2,RenderUtils.blend(color,0xFFFFFFFF,.12F),color);
            if(s.numbers.isEnabled())text(format(hp),x+4,by+bar+3+(bar-8)/2F,s.text.getArgb(),true);
        }else{
            int base=y+h-9;
            for(int i=0;i<hearts+extra;i++){
                int xx=x+i%10*8,yy=base-i/10*10;float value=Math.max(0,Math.min(1,(i<hearts?hp:abs)/2-(i<hearts?i:i-hearts)));
                if(nativeIcons){icon(xx,yy,16,0);if(value>0)icon(xx,yy,(i<hearts?52:160)+(value<1?9:0),0);}
                else heart(xx+1,yy+1,value,i<hearts?color:s.shield.getArgb(),s.empty.getArgb());
            }
            if(s.numbers.isEnabled())text(format(hp),x+84,base,s.text.getArgb(),true);
        }
        int foodY=y+h-9;
        for(int i=0;i<10;i++){int xx=x+w-9-i*8;icon(xx,foodY,16,27);int value=food-i*2;if(value>0)icon(xx,foodY,value>=2?52:61,27);}
        if(s.armor.isEnabled()&&armor>0)for(int i=0;i<10;i++)icon(x+i*8,y,armor>=i*2+2?34:armor==i*2+1?25:16,9);
        e.setBounds(x,y,w,h);GlStateManager.color(1,1,1,1);
    }
    private void icon(int x,int y,int u,int v){mc.getTextureManager().bindTexture(Gui.icons);GlStateManager.color(1,1,1,1);Gui.drawModalRectWithCustomSizedTexture(x,y,u,v,9,9,256,256);}
    private static String format(float n){return n==Math.round(n)?String.valueOf(Math.round(n)):String.format(Locale.ROOT,"%.1f",n);}
    private static final int[] HEART={0x36,0x7F,0x7F,0x7F,0x3E,0x1C,0x08};
    private static void heart(int x,int y,float value,int filled,int empty){for(int row=0;row<7;row++)for(int col=0;col<7;col++)if((HEART[row]&(1<<col))!=0)Gui.drawRect(x+col,y+row,x+col+1,y+row+1,col<Math.round(7*value)?filled:empty);}

    void tab(HudManager manager,HudModule hud,HudManager.HudElement e,ScaledResolution res,boolean preview){
        OverlaySettings.Tab s=hud.tab;refreshPlayers();FontRenderer font=mc.fontRendererObj;int count=preview&&players.isEmpty()?12:players.size();
        int viewportWidth=Math.round(res.getScaledWidth()/e.getScale()),viewportHeight=Math.round(res.getScaledHeight()/e.getScale());
        int columns=Math.max(1,Math.min(Math.min(s.columns.getInt(),Math.max(1,(count+s.rows.getInt()-1)/s.rows.getInt())),(viewportWidth-24)/80));
        int colWidth=Math.min(s.width.getInt(),Math.max(60,(viewportWidth-24)/columns));
        int rowHeight=s.rowHeight.getInt(),w=columns*colWidth+12;
        List<String> header=s.header.isEnabled()?lines(component(HEADER),w-16):Collections.emptyList(),footer=s.footer.isEnabled()?lines(component(FOOTER),w-16):Collections.emptyList();
        int titleHeight=s.title.getValue().isEmpty()?0:18,headHeight=header.size()*10,footHeight=footer.size()*10;
        int maxRows=Math.max(1,Math.min(s.rows.getInt(),(viewportHeight-48-titleHeight-headHeight-footHeight)/rowHeight));
        int shown=Math.min(count,maxRows*columns),rows=Math.max(1,(shown+columns-1)/columns),h=12+titleHeight+headHeight+rows*rowHeight+footHeight+(shown<count?12:0);
        e.ensureOnScreen(res,w,h);int x=e.left(res,w),y=e.top(res,h);
        float opening=preview?1:tabAmount;
        GlStateManager.pushMatrix();GlStateManager.translate(x+w/2F,y-8*(1-opening),0);GlStateManager.scale(.96F+.04F*opening,opening,1);GlStateManager.translate(-x-w/2F,-y,0);
        panel(manager,hud,e,s,x,y,w,h);
        int top=y+6;if(titleHeight>0){text(s.title.getValue()+"  "+count,x+7,top,s.text.getArgb(),s.shadow.isEnabled());top+=18;}
        for(String line:header){text(line,x+(w-font.getStringWidth(line))/2F,top,s.text.getArgb(),s.shadow.isEnabled());top+=10;}
        ScoreObjective objective=mc.theWorld==null?null:mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(0);
        NameProtectModule protect=Vibe.getInstance().getModuleManager().getModule(NameProtectModule.class);
        for(int i=0;i<shown;i++){
            int column=i/rows,row=i%rows,xx=x+6+column*colWidth,yy=top+row*rowHeight;NetworkPlayerInfo info=i<players.size()?players.get(i):null;
            Gui.drawRect(xx,yy,xx+colWidth-3,yy+rowHeight-1,(row&1)==0?0x242A3445:0x1218202C);int textX=xx+4;
            if(s.heads.isEnabled()){ResourceLocation skin=info==null?new ResourceLocation("textures/entity/steve.png"):info.getLocationSkin();mc.getTextureManager().bindTexture(skin);GlStateManager.color(1,1,1,1);Gui.drawScaledCustomSizeModalRect(textX,yy+(rowHeight-10)/2,8,8,8,8,10,10,64,64);textX+=14;}
            String name=info==null?"Player"+(i+1):info.getDisplayName()!=null?info.getDisplayName().getFormattedText():ScorePlayerTeam.formatPlayerName(info.getPlayerTeam(),info.getGameProfile().getName());
            if(protect!=null&&protect.isEnabled())name=protect.protectText(name);
            int right=xx+colWidth-7;if(s.ping.isEnabled()){int ping=info==null?24+i*7:info.getResponseTime();String p=ping<0?"--":ping+"ms";right-=font.getStringWidth(p);text(p,right,yy+(rowHeight-8)/2F,ping<0?0xFF8691A1:ping<100?0xFF80D8A3:ping<200?0xFFE4C57B:0xFFF28A8A,s.shadow.isEnabled());right-=7;}
            if(s.serverScores.isEnabled()&&objective!=null&&info!=null){String score=String.valueOf(objective.getScoreboard().getValueFromObjective(info.getGameProfile().getName(),objective).getScorePoints());right-=font.getStringWidth(score);text(score,right,yy+(rowHeight-8)/2F,s.accent.getArgb(),s.shadow.isEnabled());right-=5;}
            text(font.trimStringToWidth(name,Math.max(1,right-textX)),textX,yy+(rowHeight-8)/2F,s.text.getArgb(),s.shadow.isEnabled());
        }
        top+=rows*rowHeight+2;for(String line:footer){text(line,x+(w-font.getStringWidth(line))/2F,top,s.text.getArgb(),s.shadow.isEnabled());top+=10;}
        if(shown<count)text("+"+(count-shown)+" more players",x+7,top,s.accent.getArgb(),s.shadow.isEnabled());e.setBounds(x,y,w,h);GlStateManager.popMatrix();GlStateManager.color(1,1,1,1);
    }
    private void refreshPlayers(){long now=System.nanoTime();if(now<nextPlayers)return;nextPlayers=now+250000000L;players.clear();if(mc.getNetHandler()!=null)players.addAll(mc.getNetHandler().getPlayerInfoMap());players.sort(Comparator.comparing((NetworkPlayerInfo p)->p.getGameType()==WorldSettings.GameType.SPECTATOR).thenComparing(p->p.getPlayerTeam()==null?"":p.getPlayerTeam().getRegisteredName()).thenComparing(p->p.getGameProfile().getName(),String.CASE_INSENSITIVE_ORDER));}
    private String component(Field field){try{if(mc.ingameGUI==null)return "";IChatComponent c=(IChatComponent)field.get(mc.ingameGUI.getTabList());return c==null?"":c.getFormattedText();}catch(IllegalAccessException e){return "";}}
    private List<String> lines(String value,int width){if(value.isEmpty())return Collections.emptyList();List<String> result=mc.fontRendererObj.listFormattedStringToWidth(value,width);return result.subList(0,Math.min(6,result.size()));}
}
