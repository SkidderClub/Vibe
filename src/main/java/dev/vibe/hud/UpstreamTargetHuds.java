package dev.vibe.hud;

import dev.vibe.ui.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.*;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** 1.8.9 ports of ThunderHack's four layouts and LiquidBounce Nextgen's Svelte TargetHUD. See LICENSES/TARGET-HUD-PORTS.md. */
final class UpstreamTargetHuds {
    private final NeverLoseFont body=new NeverLoseFont(java.awt.Font.PLAIN,18),bold=new NeverLoseFont(java.awt.Font.BOLD,18),mini=new NeverLoseFont(java.awt.Font.PLAIN,14);
    // The original frontend uses 16px / 14px text. Whole-line fonts also avoid
    // clipping large glyphs against NeverLoseFont's fixed 32px atlas cells.
    private final dev.vibe.ui.menu.MenuFont lbName=new dev.vibe.ui.menu.MenuFont(java.awt.Font.PLAIN,16),lbStats=new dev.vibe.ui.menu.MenuFont(java.awt.Font.PLAIN,14);
    private static final ResourceLocation ART=new ResourceLocation("vibe","targethud/thunderhack/thud.png");
    private ItemStack[] samples;
    static boolean supports(String mode){return mode.equals("ThunderHack")||mode.equals("NurikZapen")||mode.equals("NurikZapen Mini")||mode.equals("CelkaPasta")||mode.equals("LiquidBounce");}
    private static float baseWidth(String mode){return mode.equals("NurikZapen")?137:mode.equals("NurikZapen Mini")?95:mode.equals("CelkaPasta")?160:150;}
    private static float baseHeight(String mode){return mode.equals("NurikZapen")?47.5F:mode.equals("NurikZapen Mini")?35.5F:mode.equals("CelkaPasta")?47:50;}
    static int height(TargetHudSettings s){return (int)Math.ceil((s.mode.is("LiquidBounce")?78*.5F:baseHeight(s.mode.getValue()))*s.width.getFloat()/154);}
    int width(TargetHudSettings s,TargetHudData d){return (int)Math.ceil((s.mode.is("LiquidBounce")?liquidWidth(s,d)*.5F:baseWidth(s.mode.getValue()))*s.width.getFloat()/154);}
    private int armorCount(TargetHudData d){if(d.entity==null)return 4;int count=0;for(int i=0;i<4;i++)if(armor(d,i)!=null)count++;return count;}
    private float liquidWidth(TargetHudSettings s,TargetHudData d){int n=armorCount(d);float stats=statWidth((int)Math.floor(d.health))+10+statWidth(armorValue(d));if(s.absorption.isEnabled()&&d.absorption>0)stats+=10+statWidth((int)Math.floor(d.absorption));return 30+50+20+Math.max(stats,Math.min(240,lbName.width(d.name)))+5+(n==0?0:n*40+(n-1)*10);}
    void draw(TargetHudSettings s,TargetHudData d,int x,int y,int w,float health,float hurt){
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,0);float scale=s.mode.is("LiquidBounce")?w/liquidWidth(s,d):w/baseWidth(s.mode.getValue());GlStateManager.scale(scale,scale,1);
        try{
            if(s.mode.is("LiquidBounce")){liquid(s,d,health,hurt);return;}
            int color=s.originalColours.isEnabled()?dev.vibe.ui.menu.AccountScreenStyle.ACCENT:s.health.getArgb(),end=s.originalColours.isEnabled()?RenderUtils.blend(color,0xFFFFFFFF,.35F):s.healthEnd.getArgb();
            String hp=s.upstreamHealth.is("Percent")?Math.round(d.health/d.maxHealth*100)+"%":String.format(Locale.ROOT,"%.1f",d.total(s.absorption.isEnabled()));
            if(s.mode.is("ThunderHack")){
                HudShapes.round(0,0,70,50,6,0x8B000000);HudShapes.round(50,0,100,50,6,0xFF000000);
                if(s.thunderImage.isEnabled())try(GuiClip clip=GuiClip.local(50,0,100,50)){image(ART,50,0,100,50,.3F);}
                HudShapes.face(d.skin,2.5F,2.5F,45,5,hurt);
                bold.draw(bold.fit(d.name,90),55,5,s.text.getArgb());
                glow(55,21,90,10,color);HudShapes.round(55,21,90,10,2,0xAA09131E);HudShapes.gradient(55,21,Math.max(8,90*health),10,2,color,end);
                body.draw(hp,100-body.width(hp)/2,22,0xFFFFFFFF);armorRow(d,55,34,.75F,13,false);
            }else if(s.mode.is("NurikZapen")||s.mode.is("NurikZapen Mini")){
                boolean small=s.mode.is("NurikZapen Mini");float bw=small?95:137,bh=small?35.5F:47.5F,face=small?30:40,start=small?38:48,barY=small?25:32,barW=small?53:85,barH=small?7:11;
                HudShapes.round(0,0,bw,bh,small?7:9,0xDC000000);RenderUtils.roundedOutline(0,0,(int)bw,(int)Math.ceil(bh),small?7:9,.7F,RenderUtils.alpha(color,90));
                HudShapes.face(d.skin,small?2.5F:3.5F,small?2.5F:3.5F,face,small?5:7,hurt);
                (small?mini:bold).draw((small?mini:bold).fit(d.name,bw-start-4),start,small?5:7,0xFFFFFFFF);
                armorRow(d,start,small?12:16,small?.5F:.75F,small?9:13,true);
                HudShapes.round(start,barY,barW,barH,4,0xFF111921);HudShapes.gradient(start,barY,Math.max(8,barW*health),barH,4,color,end);
                NeverLoseFont f=small?mini:bold;f.draw(hp,start+(barW-f.width(hp))/2,barY+(barH-(small?7:9))/2,0xFFFFFFFF);
            }else{
                HudShapes.gradient(0,0,160,47,0,RenderUtils.alpha(color,190),RenderUtils.alpha(end,190));HudShapes.face(d.skin,3.5F,3.5F,40,0,hurt);
                for(int i=0;i<4;i++){int xx=i<2?117:137,yy=i%2==0?4:25;Gui.drawRect(xx,yy,xx+18,yy+18,0x4D000000);item(armor(d,i),xx+1,yy+1,1);}
                body.draw(body.fit(d.name,62),50,7,0xFFFFFFFF);item(held(d),50,14,.75F);
                glow(50,30,60,10,color);Gui.drawRect(50,30,110,40,0x9E000000);Gui.drawRect(50,30,50+Math.round(60*health),40,RenderUtils.blend(color,0xFFFFFFFF,.35F));body.draw(hp,81-body.width(hp)/2,31,0xFFFFFFFF);
            }
        }finally{GlStateManager.popMatrix();GlStateManager.color(1,1,1,1);}
    }
    private void liquid(TargetHudSettings s,TargetHudData d,float health,float hurt){
        float w=liquidWidth(s,d);int n=armorCount(d);int bg=s.originalColours.isEnabled()?0xAD000000:s.background.getArgb(),accent=s.originalColours.isEnabled()?0xFF4677FF:s.healthEnd.getArgb(),hpColor=s.originalColours.isEnabled()?0xFFFC4130:s.health.getArgb();
        HudShapes.round(0,0,w,78,5,bg);HudShapes.face(d.skin,15,10,50,5,hurt);
        float equip=5+(n==0?0:n*40+(n-1)*10),content=w-30-50-20-equip;
        lbName.draw(lbName.fit(d.name,content),75,18,s.text.getArgb());float xx=75;
        xx=stat((int)Math.floor(d.health),"icon-health",xx,44);
        if(s.absorption.isEnabled()&&d.absorption>0)xx=stat((int)Math.floor(d.absorption),"icon-absorption",xx,44);
        stat(armorValue(d),"icon-armor",xx,44);
        float ax=w-15-equip+5;
        for(int i=0;i<4;i++){ItemStack stack=armor(d,i);if(stack==null)continue;item(stack,ax,20,30/16F);
            double fraction=stack.isItemStackDamageable()?1-stack.getItemDamage()/(double)stack.getMaxDamage():1;int active=(int)Math.ceil(fraction*10);
            for(int j=0;j<10;j++)HudShapes.round(ax+35,15.5F+j*4,5,3,1,j>=10-active?accent:0x4D000000);ax+=50;
        }
        float total=d.total(s.absorption.isEnabled()),maximum=d.maxHealth+(s.absorption.isEnabled()?d.absorption:0),ratio=(health*d.maxHealth+(s.absorption.isEnabled()?d.absorption:0))/maximum;
        try(GuiClip clip=GuiClip.local(0,70,w,8)){HudShapes.round(0,70,w*Math.min(1,ratio),8,0,hpColor);}
    }
    private int armorValue(TargetHudData d){return d.entity==null?20:d.entity.getTotalArmorValue();}
    private float statWidth(int value){return Math.max(18,lbStats.width(String.valueOf(value)))+12;}
    private float stat(int value,String icon,float x,float y){String text=String.valueOf(value);lbStats.draw(text,x,y,0xFFD3D3D3);float advance=Math.max(18,lbStats.width(text));TargetHudIcons.draw(icon,x+advance+3,y+3,9,9);return x+statWidth(value)+10;}
    private ItemStack armor(TargetHudData d,int i){if(d.entity==null)return sample()[i];try{return d.entity.getCurrentArmor(3-i);}catch(RuntimeException invalid){return null;}}
    private ItemStack held(TargetHudData d){if(d.entity==null)return sample()[4];try{return d.entity.getHeldItem();}catch(RuntimeException invalid){return null;}}
    private ItemStack[] sample(){if(samples==null){samples=new ItemStack[]{new ItemStack(Items.diamond_helmet),new ItemStack(Items.diamond_chestplate),new ItemStack(Items.diamond_leggings),new ItemStack(Items.diamond_boots),new ItemStack(Items.diamond_sword)};samples[1].addEnchantment(net.minecraft.enchantment.Enchantment.protection,2);samples[4].addEnchantment(net.minecraft.enchantment.Enchantment.sharpness,2);}return samples;}
    private void armorRow(TargetHudData d,float x,float y,float scale,float gap,boolean hand){if(hand){item(held(d),x,y,scale);x+=gap;}for(int i=0;i<4;i++){item(armor(d,i),x,y,scale);x+=gap;}}
    private void item(ItemStack stack,float x,float y,float scale){if(stack==null)return;GlStateManager.pushMatrix();GlStateManager.translate(x,y,0);GlStateManager.scale(scale,scale,1);
        try(dev.vibe.ui.render.HudItemGeometryRenderer.Scope scope=dev.vibe.ui.render.HudItemGeometryRenderer.begin()){
            dev.vibe.ui.render.HudItemGeometryRenderer.prepareItem();Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack,0,0);
        }finally{RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();GlStateManager.enableBlend();GlStateManager.popMatrix();GlStateManager.color(1,1,1,1);}}
    private static void glow(float x,float y,float w,float h,int c){HudShapes.round(x-3,y-3,w+6,h+6,5,RenderUtils.alpha(c,18));HudShapes.round(x-1,y-1,w+2,h+2,3,RenderUtils.alpha(c,35));}
    private static void image(ResourceLocation resource,float x,float y,float w,float h,float brightness){Minecraft.getMinecraft().getTextureManager().bindTexture(resource);GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,771);GlStateManager.color(brightness,brightness,brightness,1);GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(0,0);GL11.glVertex2f(x,y);GL11.glTexCoord2f(0,1);GL11.glVertex2f(x,y+h);GL11.glTexCoord2f(1,1);GL11.glVertex2f(x+w,y+h);GL11.glTexCoord2f(1,0);GL11.glVertex2f(x+w,y);GL11.glEnd();GlStateManager.color(1,1,1,1);}
}
