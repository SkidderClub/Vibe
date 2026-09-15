package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.ScriptsModule;
import dev.vibe.script.ScriptRuntime.ScriptInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** Skeet-styled local script manager for Raven-compatible .java files. */
public final class ScriptsEditorGui extends GuiScreen {
    private final ScriptsModule module;
    private final List<ScriptInfo> scripts = new ArrayList<ScriptInfo>();
    private GuiTextField name;
    private String selected;
    private int left, top, windowWidth, windowHeight, scroll;

    public ScriptsEditorGui(ScriptsModule module) { this.module = module; }

    @Override public void initGui() {
        windowWidth = Math.max(520, Math.min(720, width - 18));
        windowHeight = Math.max(300, Math.min(420, height - 28));
        left = (width - windowWidth) / 2;
        top = Math.max(16, (height - windowHeight) / 2);
        name = new GuiTextField(0, fontRendererObj, left + 16, top + windowHeight - 34, 190, 17);
        name.setMaxStringLength(48); name.setEnableBackgroundDrawing(false);
        refresh();
    }

    private void refresh() {
        scripts.clear(); scripts.addAll(Vibe.getInstance().getScriptRuntime().getScripts());
        if (selected != null) { boolean found=false; for(ScriptInfo info:scripts)if(info.getName().equalsIgnoreCase(selected))found=true; if(!found)selected=null; }
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        SkeetEditorStyle.backdrop(this, BlurModule.SCRIPTS_EDITOR, partialTicks);
        SkeetEditorStyle.window(left, top, left + windowWidth, top + windowHeight, "Scripts", "Raven BS API • local Java files • visual, rotation and packet callbacks");
        int listLeft=left+13,listTop=top+40,listRight=left+windowWidth/2-5,listBottom=top+windowHeight-50;
        SkeetEditorStyle.panel(listLeft,listTop,listRight,listBottom,"Loaded scripts");
        int y=listTop+21,rows=Math.max(1,(listBottom-y-3)/23); scroll=Math.max(0,Math.min(Math.max(0,scripts.size()-rows),scroll));
        for(int index=scroll;index<scripts.size()&&y+20<=listBottom;index++){
            ScriptInfo info=scripts.get(index);boolean active=info.getName().equalsIgnoreCase(selected);SkeetEditorStyle.row(listLeft+4,y,listRight-4,y+20,active,false);
            String status=info.isEnabled()?"ON":info.isLoaded()?"OFF":"ERR";int statusColor=info.isEnabled()?0xFF4DE7B6:info.isLoaded()?SkeetEditorStyle.MUTED:0xFFFF6A82;
            fontRendererObj.drawStringWithShadow(trim(info.getName(),listRight-listLeft-48),listLeft+10,y+6,active?SkeetEditorStyle.accent(0.1F):SkeetEditorStyle.TEXT);fontRendererObj.drawStringWithShadow(status,listRight-10-fontRendererObj.getStringWidth(status),y+6,statusColor);y+=23;
        }
        if(scripts.isEmpty())fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("No .java scripts yet."),listLeft+10,listTop+27,SkeetEditorStyle.MUTED);
        if(scripts.size()>rows){int track=listBottom-listTop-26,thumb=Math.max(12,track*rows/scripts.size()),range=Math.max(1,scripts.size()-rows),thumbY=listTop+21+(track-thumb)*scroll/range;Gui.drawRect(listRight-4,thumbY,listRight-2,thumbY+thumb,SkeetEditorStyle.accent(0.2F));}
        int detailsLeft=left+windowWidth/2+7,detailsRight=left+windowWidth-13;SkeetEditorStyle.panel(detailsLeft,listTop,detailsRight,listBottom,"Script management");
if(selected==null){fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Select a script to toggle or manage it."),detailsLeft+12,listTop+28,SkeetEditorStyle.MUTED);}else{ScriptInfo info=selectedInfo();fontRendererObj.drawStringWithShadow(selected,detailsLeft+12,listTop+27,SkeetEditorStyle.accent(0.1F));fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate(info!=null&&info.isEnabled()?"Enabled — receiving callbacks":"Disabled"),detailsLeft+12,listTop+42,info!=null&&info.isEnabled()?0xFF4DE7B6:SkeetEditorStyle.MUTED);button(detailsLeft+12,listTop+64,info!=null&&info.isEnabled()?"DISABLE":"ENABLE",info!=null&&info.isEnabled()?0xFFFF6A82:0xFF2DE2C2);button(detailsLeft+96,listTop+64,"DELETE",0xFFFF6A82);fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Rename selected script"),detailsLeft+12,listTop+97,SkeetEditorStyle.MUTED);SkeetEditorStyle.input(detailsLeft+10,listTop+108,detailsRight-10,listTop+130);if(name!=null&&name.isFocused())name.drawTextBox();else fontRendererObj.drawStringWithShadow(name==null?"":name.getText(),detailsLeft+14,listTop+115,SkeetEditorStyle.TEXT);button(detailsLeft+12,listTop+140,"RENAME",0xFF60D5FF);}
        drawDiagnostics(detailsLeft+12, listTop+176, detailsRight-12, listBottom-8);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("New script"),left+15,top+windowHeight-48,SkeetEditorStyle.MUTED);SkeetEditorStyle.input(left+13,top+windowHeight-39,left+211,top+windowHeight-17);name.drawTextBox();button(left+220,top+windowHeight-39,"CREATE",0xFF2DE2C2);button(left+296,top+windowHeight-39,"RELOAD",0xFFA855F7);button(left+370,top+windowHeight-39,"OPEN FOLDER",0xFF5BE8A6);
        super.drawScreen(mouseX,mouseY,partialTicks);
    }
    private void drawDiagnostics(int x,int y,int right,int bottom){List<String> messages=Vibe.getInstance().getScriptRuntime().getDiagnostics();if(messages.isEmpty())return;fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Latest diagnostics"),x,y,SkeetEditorStyle.MUTED);int line=y+13;int start=Math.max(0,messages.size()-4);for(int index=start;index<messages.size()&&line+fontRendererObj.FONT_HEIGHT<=bottom;index++){String message=messages.get(index);String remaining=message;while(!remaining.isEmpty()&&line+fontRendererObj.FONT_HEIGHT<=bottom){String row=fontRendererObj.trimStringToWidth(remaining,Math.max(1,right-x));fontRendererObj.drawStringWithShadow(row,x,line,0xFFFF909B);remaining=remaining.substring(row.length());line+=fontRendererObj.FONT_HEIGHT+1;}}}
private ScriptInfo selectedInfo(){for(ScriptInfo info:scripts)if(info.getName().equalsIgnoreCase(selected))return info;return null;} private int buttonWidth(String text){return fontRendererObj.getStringWidth(dev.vibe.language.LanguageManager.translate(text))+16;} private void button(int x,int y,String text,int color){int w=buttonWidth(text);SkeetEditorStyle.button(x,y,x+w,y+20,text,color!=0xFFFF6A82);} private String trim(String value,int max){return fontRendererObj.trimStringToWidth(value,Math.max(0,max));} private boolean in(int x,int y,int w,int h,int mx,int my){return mx>=x&&mx<x+w&&my<y+h;}
    @Override protected void mouseClicked(int mouseX,int mouseY,int button) throws IOException {
        name.mouseClicked(mouseX,mouseY,button);if(button!=0){super.mouseClicked(mouseX,mouseY,button);return;}int listTop=top+40,listBottom=top+windowHeight-50,y=listTop+21;for(int i=scroll;i<scripts.size()&&y+20<=listBottom;i++){if(in(left+17,y,windowWidth/2-30,20,mouseX,mouseY)){selected=scripts.get(i).getName();name.setText(selected);return;}y+=23;}
int detailsLeft=left+windowWidth/2+7;if(selected!=null){ScriptInfo info=selectedInfo();if(in(detailsLeft+12,listTop+64,buttonWidth(info!=null&&info.isEnabled()?"DISABLE":"ENABLE"),20,mouseX,mouseY)){dev.vibe.script.ScriptModule script=Vibe.getInstance().getScriptRuntime().getModule(selected);if(script!=null)script.setEnabled(!script.isEnabled());refresh();return;}if(in(detailsLeft+96,listTop+64,buttonWidth("DELETE"),20,mouseX,mouseY)){Vibe.getInstance().getScriptRuntime().delete(selected);refresh();return;}if(in(detailsLeft+12,listTop+140,buttonWidth("RENAME"),20,mouseX,mouseY)){String renamed=name.getText().trim();if(!renamed.isEmpty()&&Vibe.getInstance().getScriptRuntime().rename(selected,renamed))selected=renamed;refresh();return;}}
String requested=name.getText().trim();if(in(left+220,top+windowHeight-39,buttonWidth("CREATE"),20,mouseX,mouseY)){String made=Vibe.getInstance().getScriptRuntime().create(requested);if(made!=null){selected=made;name.setText(made);}refresh();return;}if(in(left+296,top+windowHeight-39,buttonWidth("RELOAD"),20,mouseX,mouseY)){Vibe.getInstance().getScriptRuntime().reload();refresh();return;}if(in(left+370,top+windowHeight-39,buttonWidth("OPEN FOLDER"),20,mouseX,mouseY)){Vibe.getInstance().getScriptRuntime().openDirectory();return;}super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public void handleMouseInput() throws IOException {super.handleMouseInput();int wheel=org.lwjgl.input.Mouse.getEventDWheel();int x=org.lwjgl.input.Mouse.getEventX()*width/Math.max(1,mc.displayWidth);if(wheel!=0&&x>=left&&x<=left+windowWidth/2)scroll+=wheel<0?2:-2;}
    @Override protected void keyTyped(char character,int key) throws IOException {if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(null);return;}name.textboxKeyTyped(character,key);} @Override public void onGuiClosed(){if(module.isEnabled())module.setEnabled(false);super.onGuiClosed();} @Override public boolean doesGuiPauseGame(){return false;}
}
