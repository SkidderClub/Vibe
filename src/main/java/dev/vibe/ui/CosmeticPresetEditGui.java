package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.cosmetic.CosmeticaAccessory;
import dev.vibe.cosmetic.CosmeticaCatalogService;
import dev.vibe.cosmetic.CosmeticPreset;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.CosmeticsEditorModule;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Skeet-styled editor for real public Cosmetica catalogue entries. */
public final class CosmeticPresetEditGui extends GuiScreen {
    private final CosmeticsEditorModule module;
    private final CosmeticsEditorGui parent;
    private final CosmeticPreset preset;
    private final CosmeticPreviewRenderer preview = new CosmeticPreviewRenderer();
    private GuiTextField name, skin, search;
    private int left, top, right, bottom, equippedScroll, catalogScroll;
    private float previewYaw;
    private boolean rotating;
    private int lastMouseX;

    public CosmeticPresetEditGui(CosmeticsEditorModule module, CosmeticPreset preset, CosmeticsEditorGui parent) { this.module=module; this.preset=preset; this.parent=parent; }
    @Override public void initGui() {
        int width=Math.min(this.width-16,1010),height=Math.min(this.height-18,575); left=(this.width-width)/2;right=left+width;top=Math.max(10,(this.height-height)/2);bottom=top+height;
        name=field(0,left+22,top+71,205,preset.getName(),24); skin=field(1,left+22,top+118,205,preset.getSkinName(),16); search=field(2,left+480,top+51,245,"",44);
        Vibe.getInstance().getCosmeticaCatalogService().search("",1);
    }
    private GuiTextField field(int id,int x,int y,int width,String value,int limit){GuiTextField field=new GuiTextField(id,fontRendererObj,x,y,width,16);field.setText(value);field.setMaxStringLength(limit);field.setEnableBackgroundDrawing(false);return field;}

    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks) {
        SkeetEditorStyle.backdrop(this, BlurModule.COSMETICS_EDITOR, partialTicks);
        SkeetEditorStyle.window(left,top,right,bottom,"Edit cosmetic preset","Cosmetica public catalog • authored JSON models and textures");
        drawIdentity(); drawEquipped(mouseX,mouseY); drawCatalog(mouseX,mouseY); super.drawScreen(mouseX,mouseY,partialTicks);
    }
    private void drawIdentity() {
        int x=left+14,y=top+30; SkeetEditorStyle.panel(x,y,x+250,bottom-14,"Preset preview");
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Preset name"),x+8,y+24,SkeetEditorStyle.MUTED);SkeetEditorStyle.input(x+6,y+37,x+238,y+59);name.drawTextBox();
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Skin (fallback: xHeist_)"),x+8,y+71,SkeetEditorStyle.MUTED);SkeetEditorStyle.input(x+6,y+84,x+238,y+106);skin.drawTextBox();
        SkeetEditorStyle.panel(x+12,y+124,x+238,y+342,"Skin preview");preview.draw(preset,x+125,y+331,66,previewYaw);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("The real accessories are rendered in-game"),x+18,y+351,SkeetEditorStyle.MUTED);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Drag model to rotate"),x+64,y+366,SkeetEditorStyle.MUTED);
        toggle(x+12,y+391,"Only third-person",preset.isOnlyThirdPerson());
        SkeetEditorStyle.button(x+12,bottom-42,x+238,bottom-20,"SAVE & BACK",true);
    }
    private void drawEquipped(int mouseX,int mouseY) {
        int x=left+278,y=top+30,w=180; SkeetEditorStyle.panel(x,y,x+w,bottom-14,"Equipped catalog accessories");
        List<CosmeticaAccessory> accessories=preset.getAccessories(); int rowY=y+21-equippedScroll;
        if(accessories.isEmpty())fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("None selected"),x+8,rowY+10,SkeetEditorStyle.MUTED);
for(CosmeticaAccessory accessory:accessories){if(rowY+49>=y+20&&rowY<bottom-17){SkeetEditorStyle.row(x+5,rowY,x+w-5,rowY+46,false,mouseX>=x+5&&mouseX<x+w-5&&mouseY>=rowY&&mouseY<rowY+46);fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(accessory.getName(),128),x+10,rowY+6,SkeetEditorStyle.TEXT);fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate(accessory.getAttachment()),x+10,rowY+20,SkeetEditorStyle.MUTED);fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate(accessory.isEnabled()?"EDIT":"OFF"),x+w-42,rowY+6,accessory.isEnabled()?0xFF43D89C:0xFFFF657A);fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("RMB ×"),x+w-44,rowY+28,0xFFFF657A);}rowY+=49;}
    }
    private void drawCatalog(int mouseX,int mouseY) {
        int x=left+474,y=top+30; SkeetEditorStyle.panel(x,y,right-14,bottom-14,"Cosmetica catalog");
        SkeetEditorStyle.input(x+6,y+17,x+257,y+39);search.drawTextBox();SkeetEditorStyle.button(x+264,y+17,x+333,y+39,"SEARCH",true);
        CosmeticaCatalogService catalog=Vibe.getInstance().getCosmeticaCatalogService();String summary=catalog.isLoading()?catalog.getStatus():catalog.getStatus()+"  •  scroll to browse";fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(summary,right-x-22),x+8,y+49,SkeetEditorStyle.MUTED);
        catalogScroll=Math.min(catalogScroll,catalogMaxScroll(catalog));
        int listTop=catalogListTop(),listBottom=catalogListBottom();
        beginScissor(x+6,listTop,right-x-27,listBottom-listTop);
        try {
            List<CosmeticaAccessory> entries=catalog.getResults();
            int first=Math.max(0,catalogScroll/57-2);
            int last=Math.min(entries.size()-1,(catalogScroll+listBottom-listTop)/57+2);
            for(int index=first;index<=last;index++){
                CosmeticaAccessory accessory=entries.get(index); int itemY=listTop+index*57-catalogScroll;
                SkeetEditorStyle.row(x+6,itemY,right-21,itemY+53,preset.contains(accessory.getId()),mouseX>=x+6&&mouseX<right-21&&mouseY>=itemY&&mouseY<itemY+53);
                drawThumbnail(catalog,accessory,x+10,itemY+5);
                fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(accessory.getName(),right-x-124),x+49,itemY+7,SkeetEditorStyle.TEXT);
                String info=accessory.getAttachment()+" • "+accessory.getDescription();
                fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(info,right-x-128),x+49,itemY+21,SkeetEditorStyle.MUTED);
fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate(preset.contains(accessory.getId())?"EQUIPPED":"EQUIP"),right-84,itemY+35,preset.contains(accessory.getId())?0xFF43D89C:SkeetEditorStyle.accent(.35F));
            }
        } finally { endScissor(); }
    }
    private void drawThumbnail(CosmeticaCatalogService catalog,CosmeticaAccessory accessory,int x,int y){
        // Gui draws inherit the current GL tint. Reset it around every catalog
        // thumbnail so an earlier Skeet accent or particle pass cannot leave
        // public Cosmetica artwork partly gray.
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try { GL11.glEnable(GL11.GL_BLEND); GL11.glColor4f(1F,1F,1F,1F); mc.getTextureManager().bindTexture(catalog.getTexture(accessory,true)); Gui.drawModalRectWithCustomSizedTexture(x,y,0,0,34,34,34,34); }
        finally { GL11.glPopAttrib(); GL11.glColor4f(1F,1F,1F,1F); }
    }
private void toggle(int x,int y,String text,boolean value){SkeetEditorStyle.row(x,y,x+226,y+20,value,false);fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate(text),x+7,y+6,SkeetEditorStyle.TEXT);fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate(value?"ON":"OFF"),x+190,y+6,value?0xFF43D89C:SkeetEditorStyle.MUTED);}

    @Override protected void mouseClicked(int mouseX,int mouseY,int button)throws IOException{
        name.mouseClicked(mouseX,mouseY,button);skin.mouseClicked(mouseX,mouseY,button);search.mouseClicked(mouseX,mouseY,button);int panel=left+14,previewTop=top+30;
        if(button==0&&hit(panel+12,bottom-42,226,22,mouseX,mouseY)){save();mc.displayGuiScreen(parent);return;}
        if(button==0&&hit(panel+12,previewTop+124,226,218,mouseX,mouseY)){rotating=true;lastMouseX=mouseX;return;}
        if(button==0&&hit(panel+12,previewTop+391,226,20,mouseX,mouseY)){preset.setOnlyThirdPerson(!preset.isOnlyThirdPerson());save();return;}
        int ex=left+278,ey=top+30,rowY=ey+21-equippedScroll;for(CosmeticaAccessory accessory:preset.getAccessories()){if(hit(ex+5,rowY,170,46,mouseX,mouseY)){if(button==1){preset.remove(accessory.getId());save();}else if(button==0){mc.displayGuiScreen(new CosmeticAccessoryOptionsGui(module,preset,accessory,this));}return;}rowY+=49;}
        int cx=left+474,cy=top+30;CosmeticaCatalogService catalog=Vibe.getInstance().getCosmeticaCatalogService();if(button==0&&hit(cx+264,cy+17,69,22,mouseX,mouseY)){catalog.search(search.getText(),1);catalogScroll=0;return;}
        int listTop=catalogListTop(),listBottom=catalogListBottom();
        if(mouseY>=listTop&&mouseY<listBottom){int itemY=listTop-catalogScroll;for(CosmeticaAccessory accessory:catalog.getResults()){if(hit(cx+6,itemY,right-cx-27,53,mouseX,mouseY)){if(preset.contains(accessory.getId()))preset.remove(accessory.getId());else{preset.equip(accessory);catalog.getModel(accessory);}save();return;}itemY+=57;}}
        super.mouseClicked(mouseX,mouseY,button);
    }
    @Override protected void mouseClickMove(int mouseX,int mouseY,int button,long time){if(rotating&&button==0){previewYaw+=mouseX-lastMouseX;lastMouseX=mouseX;}}
    @Override protected void mouseReleased(int mouseX,int mouseY,int state){rotating=false;super.mouseReleased(mouseX,mouseY,state);}
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel==0)return;int mouseX=Mouse.getEventX()*width/mc.displayWidth,mouseY=height-Mouse.getEventY()*height/mc.displayHeight-1;if(mouseX>=left+278&&mouseX<left+458){int maximum=Math.max(0,preset.getAccessories().size()*49-(bottom-(top+30)-38));equippedScroll=Math.max(0,Math.min(maximum,equippedScroll+(wheel<0?28:-28)));}else if(mouseX>=left+474&&mouseX<right-14&&mouseY>=catalogListTop()&&mouseY<catalogListBottom()){CosmeticaCatalogService catalog=Vibe.getInstance().getCosmeticaCatalogService();catalogScroll=Math.max(0,Math.min(catalogMaxScroll(catalog),catalogScroll+(wheel<0?38:-38)));}}
    @Override protected void keyTyped(char character,int keyCode)throws IOException{if(keyCode==Keyboard.KEY_ESCAPE){save();mc.displayGuiScreen(parent);return;}if(name.textboxKeyTyped(character,keyCode)){preset.setName(name.getText());save();}else if(skin.textboxKeyTyped(character,keyCode)){preset.setSkinName(skin.getText());save();}else if(search.textboxKeyTyped(character,keyCode)&&keyCode==Keyboard.KEY_RETURN){Vibe.getInstance().getCosmeticaCatalogService().search(search.getText(),1);catalogScroll=0;}}
    @Override public void onGuiClosed(){if(module.isEnabled()&&!(mc.currentScreen instanceof CosmeticsEditorGui)&&!(mc.currentScreen instanceof CosmeticAccessoryOptionsGui))module.setEnabled(false);super.onGuiClosed();}
    private int catalogListTop(){return top+98;}
    private int catalogListBottom(){return bottom-17;}
    private int catalogMaxScroll(CosmeticaCatalogService catalog){return Math.max(0,catalog.getResults().size()*57-(catalogListBottom()-catalogListTop()));}
    private void beginScissor(int x,int y,int width,int height){ScaledResolution resolution=new ScaledResolution(mc);int scale=resolution.getScaleFactor();GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(x*scale,(resolution.getScaledHeight()-(y+height))*scale,width*scale,height*scale);}
    private void endScissor(){GL11.glDisable(GL11.GL_SCISSOR_TEST);}
    @Override public boolean doesGuiPauseGame(){return false;} private void save(){preset.setName(name.getText());preset.setSkinName(skin.getText());Vibe.getInstance().getCosmeticPresetManager().save();}private static boolean hit(int x,int y,int width,int height,int mouseX,int mouseY){return mouseX>=x&&mouseX<x+width&&mouseY>=y&&mouseY<y+height;}
}
