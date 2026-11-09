package dev.vibe.ui.menu;


import java.io.IOException;
import java.util.Locale;
import net.minecraft.client.gui.*;
import org.lwjgl.input.*;

/** Compact editor; drafts do not change the selected theme until Save is pressed. */
public final class MenuThemeEditorGui extends GuiScreen {
    private final GuiScreen parent;private final MainMenuShaderManager shaders;private final MenuTheme draft;private final boolean existing;
    private final MainMenuShaderManager previewShaders=new MainMenuShaderManager();
    private MenuTextField name;private int left,top,w,h,step,speedY;private boolean dragging,confirmDelete;private String status="";
    MenuThemeEditorGui(GuiScreen parent,MainMenuShaderManager shaders,MenuTheme draft,boolean existing){this.parent=parent;this.shaders=shaders;this.draft=draft;this.existing=existing;}
    @Override public void initGui(){
        Keyboard.enableRepeatEvents(true);if(name!=null)draft.name=name.getText();w=Math.min(480,width-24);h=Math.min(300,height-20);left=(width-w)/2;top=(height-h)/2;step=(h-120)/4;
        name=new MenuTextField(0,fontRendererObj,left+92,top+40,w-114,22);name.setEnableBackgroundDrawing(false);name.setMaxStringLength(40);name.setText(draft.name);buttonList.clear();
        buttonList.add(new AccountScreenStyle.Button(1,left+84,top+40+step,w-154,23,draft.shader.replace(".frag",""),"",false,false));
        buttonList.add(new AccountScreenStyle.Button(8,left+w-62,top+40+step,48,23,draft.background?"On":"Off","",draft.background,false));
        String[] modes={"Solid","Rainbow","Fade"};for(int i=0;i<3;i++){int a=left+84+(w-98)*i/3,b=left+84+(w-98)*(i+1)/3;boolean selected=draft.colorMode.equals(modes[i]);buttonList.add(new AccountScreenStyle.Button(10+i,a,top+40+step*2,b-a-4,23,(selected?"✓ ":"")+modes[i],"",selected,false));}
        int cw=(w-106)/2;buttonList.add(new AccountScreenStyle.Button(2,left+84,top+40+step*3,cw,23,"Accent colour","",false,false));buttonList.add(new AccountScreenStyle.Button(3,left+92+cw,top+40+step*3,cw,23,"Fade colour","",false,false));buttonList.get(buttonList.size()-1).enabled=draft.colorMode.equals("Fade");
        speedY=top+40+step*4;int footer=top+h-36,bw=(w-36)/2;
        if(existing){int third=(w-44)/3;buttonList.add(new AccountScreenStyle.Button(5,left+14,footer,third,24,confirmDelete?"Confirm delete":"Delete","",false,true));buttonList.add(new AccountScreenStyle.Button(0,left+22+third,footer,third,24,"Cancel","",false,false));buttonList.add(new AccountScreenStyle.Button(4,left+30+third*2,footer,third,24,"Save","",true,false));}
        else{buttonList.add(new AccountScreenStyle.Button(0,left+14,footer,bw,24,"Cancel","",false,false));buttonList.add(new AccountScreenStyle.Button(4,left+22+bw,footer,bw,24,"Save theme","",true,false));}
    }
    @Override public void drawScreen(int mx,int my,float partial){
        if(dragging&&Mouse.isButtonDown(0))speed(mx);MenuThemes.preview(draft);previewShaders.preview(draft.shader,draft.background);previewShaders.draw(width,height);MenuGlass.begin(this,previewShaders,width,height);
        try{AccountScreenStyle.window(left,top,w,h);AccountScreenStyle.title(existing?"Edit theme":"New theme",left+14,top+13);AccountScreenStyle.SMALL_FONT.draw("Live preview",left+w-80,top+16,AccountScreenStyle.ACCENT);
            String[] labels={"Name","Shader","Colours","Palette"};for(int i=0;i<labels.length;i++)AccountScreenStyle.SMALL_FONT.middle(labels[i],left+14,top+40+i*step,22,AccountScreenStyle.MUTED);
            AccountScreenStyle.panel(left+84,top+40,w-98,22,AccountScreenStyle.SURFACE,name.isFocused()?AccountScreenStyle.ACCENT:AccountScreenStyle.BORDER);name.drawTextBox();AccountScreenStyle.SMALL_FONT.middle("Speed",left+14,speedY,20,AccountScreenStyle.MUTED);
            int sx=left+84,sw=w-160,fill=Math.max(3,(int)Math.round(sw*(draft.speed-.1)/2.9));dev.vibe.ui.MenuRoundedRenderer.rect(sx,speedY+8,sw,4,2,AccountScreenStyle.BORDER);dev.vibe.ui.MenuRoundedRenderer.rect(sx,speedY+8,fill,4,2,AccountScreenStyle.ACCENT);dev.vibe.ui.MenuRoundedRenderer.rect(sx+fill-3,speedY+5,6,10,3,AccountScreenStyle.TEXT);
            AccountScreenStyle.SMALL_FONT.middle(String.format(Locale.ROOT,"%.1f×",draft.speed),left+w-62,speedY,20,AccountScreenStyle.TEXT);
            AccountScreenStyle.SMALL_FONT.draw(AccountScreenStyle.SMALL_FONT.fit(status,w-28),left+14,top+h-50,AccountScreenStyle.ERROR);
            super.drawScreen(mx,my,partial);int cy=top+40+step*3;
            int colourWidth=(w-106)/2-8;dev.vibe.ui.MenuRoundedRenderer.rect(left+88,cy+20,colourWidth,2,1,draft.palette().sample(System.currentTimeMillis()).accent);if(draft.colorMode.equals("Fade"))dev.vibe.ui.MenuRoundedRenderer.rect(left+96+(w-106)/2,cy+20,colourWidth,2,1,draft.second);
        }finally{MenuGlass.end();}
    }
    private void speed(int mx){draft.speed=Math.round((.1+2.9*Math.max(0,Math.min(1,(mx-left-84)/(double)(w-160))))*10)/10D;}
    @Override protected void actionPerformed(GuiButton button){draft.name=name.getText();
        if(button.id==0){MenuThemes.preview(null);previewShaders.close();mc.displayGuiScreen(parent);}
        else if(button.id==1)mc.displayGuiScreen(new MenuThemesGui(this,shaders,value->{draft.shader=value;draft.background=true;}));
        else if(button.id==2||button.id==3){boolean first=button.id==2;mc.displayGuiScreen(new ThemeColourGui(this,previewShaders,first?"Accent colour":"Fade colour",first?draft.first:draft.second,color->{if(first)draft.first=color;else draft.second=color;},true));}
        else if(button.id>=10&&button.id<=12){draft.colorMode=new String[]{"Solid","Rainbow","Fade"}[button.id-10];initGui();}
        else if(button.id==8){draft.background=!draft.background;initGui();}
        else if(button.id==4)try{if(MenuThemes.saveCustom(draft,shaders)){previewShaders.close();mc.displayGuiScreen(parent);}else status="Could not save the theme.";}catch(IllegalArgumentException invalid){status=invalid.getMessage();}
        else if(button.id==5){if(confirmDelete){if(MenuThemes.deleteCustom(draft.id,shaders)){MenuThemes.preview(null);previewShaders.close();mc.displayGuiScreen(parent);}else status="Could not delete the theme.";}else{confirmDelete=true;initGui();}}
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{name.mouseClicked(x,y,button);if(button==0&&x>=left+80&&x<=left+w-76&&y>=speedY&&y<=speedY+20){dragging=true;speed(x);return;}super.mouseClicked(x,y,button);}
    @Override protected void mouseReleased(int x,int y,int button){dragging=false;super.mouseReleased(x,y,button);}
    @Override protected void keyTyped(char c,int key)throws IOException{if(key==Keyboard.KEY_ESCAPE){MenuThemes.preview(null);previewShaders.close();mc.displayGuiScreen(parent);return;}if(name.textboxKeyTyped(c,key))return;super.keyTyped(c,key);}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);}
    @Override public void updateScreen(){name.updateCursorCounter();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
