package dev.vibe.ui.screen;

import dev.vibe.module.impl.visual.BlockESPModule;
import dev.vibe.ui.*;
import java.io.IOException;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.item.*;
import org.lwjgl.input.*;

/** Searchable native block catalogue with independent RGBA fill and outline editors. */
public final class BlockEspEditorGui extends GuiScreen {
    private final BlockESPModule module;private final List<Block> blocks=new ArrayList<>(),filtered=new ArrayList<>();
    private Block selected;private GuiTextField search,hex;private String query="",status="";private int left,top,w,h,side,gridTop,gridBottom,columns,scroll;
    private int colorPart=-1,color,drag=-1;private boolean widthDrag;
    public BlockEspEditorGui(BlockESPModule module){this.module=module;}
    @Override public void initGui(){
        w=Math.min(760,width-20);h=Math.min(460,height-24);left=(width-w)/2;top=(height-h)/2;side=left+w-Math.min(205,Math.max(150,w/3));
        search=new GuiTextField(0,fontRendererObj,left+12,top+31,side-left-24,18);search.setMaxStringLength(64);search.setText(query);
        gridTop=top+58;gridBottom=top+h-31;columns=Math.max(1,(side-left-18)/34);
        if(blocks.isEmpty()){for(Block b:Block.blockRegistry)if(b!=net.minecraft.init.Blocks.air)blocks.add(b);blocks.sort(Comparator.comparing(b->Block.blockRegistry.getNameForObject(b).toString()));}
        filter();if(selected==null&&!blocks.isEmpty())selected=blocks.get(0);
        buttonList.clear();buttonList.add(new GuiButton(0,left+w-60,top+3,50,18,"Done"));
    }
    private void filter(){filtered.clear();String q=query.toLowerCase(Locale.ROOT);for(Block b:blocks)if(id(b).toLowerCase(Locale.ROOT).contains(q)||name(b).toLowerCase(Locale.ROOT).contains(q))filtered.add(b);scroll=0;}
    private static String id(Block b){return Block.blockRegistry.getNameForObject(b).toString();}
    private static String name(Block b){String key=b.getUnlocalizedName()+".name",translated=net.minecraft.client.resources.I18n.format(key);return translated.equals(key)?id(b):translated;}
    private void text(String value,int x,int y,int color){fontRendererObj.drawString(value,x,y,color);}
    private void button(int x,int y,int width,String text,boolean active){SkeetEditorStyle.button(x,y,x+width,y+19,text,active);}
    @Override public void drawScreen(int mx,int my,float partial){
        SkeetEditorStyle.backdrop(this,dev.vibe.module.impl.client.BlurModule.ESP_EDITOR,partial);SkeetEditorStyle.window(left,top,left+w,top+h,"BlockESP editor"," ");
        search.drawTextBox();int visible=Math.max(1,(gridBottom-gridTop)/34),rows=(filtered.size()+columns-1)/columns;scroll=Math.max(0,Math.min(scroll,Math.max(0,rows-visible)));
        Block hovered=null;
        try(GuiClip clip=new GuiClip(left+8,gridTop,side-left-13,gridBottom-gridTop);dev.vibe.ui.render.HudItemGeometryRenderer.Scope items=dev.vibe.ui.render.HudItemGeometryRenderer.begin()){
            for(int row=0;row<visible;row++)for(int col=0;col<columns;col++){
                int index=(row+scroll)*columns+col;if(index>=filtered.size())break;Block b=filtered.get(index);int x=left+10+col*34,y=gridTop+row*34;
                BlockESPModule.Style saved=module.styles().get(b);boolean enabled=saved!=null&&saved.enabled;
                SkeetEditorStyle.input(x,y,x+30,y+30);if(b==selected)SkeetEditorStyle.border(x,y,x+30,y+30,SkeetEditorStyle.accent(.2F));
                Item item=Item.getItemFromBlock(b);
                if(item!=null){dev.vibe.ui.GuiRenderState.prepare(true);RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(item),x+7,y+5);RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();}
                else{Gui.drawRect(x+7,y+5,x+23,y+21,dev.vibe.ui.render.esp.BlockPaletteColors.average(b));}
                if(enabled)text("+",x+23,y+20,0xFF7CDEAB);if(mx>=x&&mx<x+30&&my>=y&&my<y+30)hovered=b;
            }
        }
        if(rows>visible){int track=gridBottom-gridTop,thumb=Math.max(14,track*visible/rows);dev.vibe.ui.menu.MenuScrollbar.draw(side-7,gridTop,2,track,gridTop+(track-thumb)*scroll/Math.max(1,rows-visible),thumb);}
        text(module.styles().values().stream().filter(s->s.enabled).count()+" selected",left+11,top+h-21,SkeetEditorStyle.MUTED);
        if(selected!=null){BlockESPModule.Style s=module.style(selected);int x=side+9,bw=left+w-x-10;
            text(fontRendererObj.trimStringToWidth(name(selected),bw),x,top+34,SkeetEditorStyle.TEXT);
            text(fontRendererObj.trimStringToWidth(id(selected),bw),x,top+47,SkeetEditorStyle.MUTED);
            button(x,top+65,bw,s.enabled?"Enabled":"Disabled",s.enabled);
            button(x,top+91,bw,"Fill colour",false);Gui.drawRect(x+5,top+96,x+16,top+105,s.fill);
            button(x,top+117,bw,"Outline colour",false);Gui.drawRect(x+5,top+122,x+16,top+131,s.outline);
            text("Outline width  "+String.format(Locale.ROOT,"%.1f",s.width),x,top+146,SkeetEditorStyle.TEXT);
            slider(x,top+162,bw,(s.width-.5F)/3.5F,0xFF8DD9D3);
            if(widthDrag&&Mouse.isButtonDown(0)){s.width=.5F+3.5F*Math.max(0,Math.min(1,(mx-x)/(float)bw));module.put(selected,s);}
            if(h>=240)button(x,top+188,bw,"Reset to block colour",false);
        }
        super.drawScreen(mx,my,partial);
        if(colorPart>=0)drawColor(mx,my);else if(hovered!=null)drawHoveringText(Arrays.asList(name(hovered),id(hovered),"Left click: select", "Right click: enable or disable"),mx,my);
    }
    private void slider(int x,int y,int width,float value,int color){Gui.drawRect(x,y,x+width,y+5,0xFF30343B);Gui.drawRect(x,y,x+Math.round(width*value),y+5,color);Gui.drawRect(x+Math.round(width*value)-1,y-3,x+Math.round(width*value)+2,y+8,0xFFECEFF5);}
    private void editColor(int part){colorPart=part;color=part==0?module.style(selected).fill:module.style(selected).outline;hex=new GuiTextField(1,fontRendererObj,(width-264)/2+12,(height-200)/2+143,240,18);hex.setMaxStringLength(9);hex.setText(String.format(Locale.ROOT,"#%08X",color));}
    private void drawColor(int mx,int my){
        Gui.drawRect(0,0,width,height,0xB0000000);int x=(width-264)/2,y=(height-200)/2;SkeetEditorStyle.window(x,y,x+264,y+200,colorPart==0?"Fill colour":"Outline colour","RGBA");
        int[] shifts={16,8,0,24};String[] labels={"Red","Green","Blue","Alpha"};int[] tints={0xFFEF7777,0xFF77DD99,0xFF77B3EF,0xFFE0E5ED};
        if(drag>=0&&Mouse.isButtonDown(0)){int v=Math.round(255*Math.max(0,Math.min(1,(mx-x-64)/184F)));color=color&~(255<<shifts[drag])|v<<shifts[drag];hex.setText(String.format(Locale.ROOT,"#%08X",color));}
        for(int i=0;i<4;i++){int yy=y+34+i*25;text(labels[i],x+12,yy-3,SkeetEditorStyle.TEXT);slider(x+64,yy,184,(color>>>shifts[i]&255)/255F,tints[i]);}
        hex.drawTextBox();button(x+12,y+172,115,"Apply",true);button(x+137,y+172,115,"Cancel",false);
    }
    @Override protected void mouseClicked(int mx,int my,int key)throws IOException{
        if(colorPart>=0){int x=(width-264)/2,y=(height-200)/2;hex.mouseClicked(mx,my,key);if(key!=0)return;
            for(int i=0;i<4;i++)if(mx>=x+58&&mx<=x+252&&my>=y+26+i*25&&my<=y+45+i*25){drag=i;return;}
            if(my>=y+172&&my<y+192){if(mx>=x+12&&mx<x+127)try{String value=hex.getText().replace("#","");if(value.length()!=8)return;int c=(int)Long.parseLong(value,16);BlockESPModule.Style s=module.style(selected);if(colorPart==0)s.fill=c;else s.outline=c;module.put(selected,s);colorPart=-1;}catch(NumberFormatException ignored){}else if(mx>=x+137&&mx<x+252)colorPart=-1;}return;
        }
        search.mouseClicked(mx,my,key);if(my>=gridTop&&my<gridBottom&&mx>=left+10&&mx<side-8){int col=(mx-left-10)/34,row=(my-gridTop)/34,index=(row+scroll)*columns+col;if(col<columns&&index<filtered.size()){selected=filtered.get(index);if(key==1){BlockESPModule.Style s=module.style(selected);s.enabled=!s.enabled;module.put(selected,s);}}return;}
        if(selected!=null&&mx>=side+9&&mx<left+w-10&&key==0){BlockESPModule.Style s=module.style(selected);int yy=my-top;
            if(yy>=65&&yy<84){s.enabled=!s.enabled;module.put(selected,s);}else if(yy>=91&&yy<110)editColor(0);else if(yy>=117&&yy<136)editColor(1);else if(yy>=156&&yy<=173)widthDrag=true;else if(h>=240&&yy>=188&&yy<207){BlockESPModule.Style reset=new BlockESPModule.Style(dev.vibe.ui.render.esp.BlockPaletteColors.average(selected));reset.enabled=s.enabled;module.put(selected,reset);}}
        super.mouseClicked(mx,my,key);
    }
    @Override protected void mouseReleased(int x,int y,int key){drag=-1;widthDrag=false;super.mouseReleased(x,y,key);}
    @Override protected void keyTyped(char c,int key)throws IOException{
        if(colorPart>=0){if(key==Keyboard.KEY_ESCAPE){colorPart=-1;return;}hex.textboxKeyTyped(c,key);return;}
        if(search.textboxKeyTyped(c,key)){if(!query.equals(search.getText())){query=search.getText();filter();}return;}super.keyTyped(c,key);
    }
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel!=0&&colorPart<0)scroll+=wheel>0?-1:1;}
    @Override protected void actionPerformed(GuiButton button){if(button.id==0)mc.displayGuiScreen(null);}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void onGuiClosed(){if(dev.vibe.Vibe.getInstance().getConfig()!=null)dev.vibe.Vibe.getInstance().getConfig().save(dev.vibe.Vibe.getInstance().getModuleManager());}
}
