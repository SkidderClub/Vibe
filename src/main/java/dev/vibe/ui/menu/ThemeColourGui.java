package dev.vibe.ui.menu;


import java.awt.Color;
import java.io.IOException;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.*;
import org.lwjgl.opengl.GL11;

/** HSV picker with a hexadecimal alternative, shared by both custom theme colours. */
final class ThemeColourGui extends GuiScreen {
    private final GuiScreen parent;private final MainMenuShaderManager shaders;private final String title;private final IntConsumer apply;private final float[] hsv;
    private final int original;private final boolean livePreview;
    private MenuTextField hex;private int left,top,w,h,size,drag=-1;private String status="";
    ThemeColourGui(GuiScreen parent,MainMenuShaderManager shaders,String title,int color,IntConsumer apply){this(parent,shaders,title,color,apply,false);}
    ThemeColourGui(GuiScreen parent,MainMenuShaderManager shaders,String title,int color,IntConsumer apply,boolean livePreview){this.parent=parent;this.shaders=shaders;this.title=title;this.apply=apply;this.original=color;this.livePreview=livePreview;hsv=Color.RGBtoHSB(color>>16&255,color>>8&255,color&255,null);}
    private int color(){return Color.HSBtoRGB(hsv[0],hsv[1],hsv[2]);}
    @Override public void initGui(){Keyboard.enableRepeatEvents(true);w=Math.min(330,width-24);h=Math.min(286,height-20);left=(width-w)/2;top=(height-h)/2;size=Math.min(160,h-118);hex=new MenuTextField(0,fontRendererObj,left+22,top+h-65,w-44,22);hex.setEnableBackgroundDrawing(false);hex.setMaxStringLength(7);hex.setText(String.format("#%06X",color()&0xFFFFFF));buttonList.clear();int bw=(w-36)/2;buttonList.add(new AccountScreenStyle.Button(0,left+14,top+h-33,bw,23,"Cancel","",false,false));buttonList.add(new AccountScreenStyle.Button(1,left+22+bw,top+h-33,bw,23,"Apply","",true,false));}
    @Override public void drawScreen(int mx,int my,float partial){if(drag>=0&&Mouse.isButtonDown(0))pick(mx,my);shaders.draw(width,height);MenuGlass.begin(this,shaders,width,height);try{AccountScreenStyle.window(left,top,w,h);AccountScreenStyle.title(title,left+14,top+14);int x=left+14,y=top+43;
        quad(x,y,size,size,0xFFFFFFFF,Color.HSBtoRGB(hsv[0],1,1),0xFF000000,0xFF000000);
        for(int i=0;i<6;i++)quad(x+size+12,y+size*i/6F,16,size/6F+1,Color.HSBtoRGB(i/6F,1,1),Color.HSBtoRGB(i/6F,1,1),Color.HSBtoRGB((i+1)/6F,1,1),Color.HSBtoRGB((i+1)/6F,1,1));
        int px=x+Math.round(hsv[1]*size),py=y+Math.round((1-hsv[2])*size);dev.vibe.ui.MenuRoundedRenderer.rect(px-4,py-4,8,8,4,0xFFFFFFFF);dev.vibe.ui.MenuRoundedRenderer.rect(px-2,py-2,4,4,2,color());Gui.drawRect(x+size+9,y+Math.round(hsv[0]*size)-1,x+size+31,y+Math.round(hsv[0]*size)+2,0xFFFFFFFF);
        int sx=x+size+42,sw=w-size-70;dev.vibe.ui.MenuRoundedRenderer.rect(sx,y,sw,Math.min(54,size),6,color());AccountScreenStyle.SMALL_FONT.draw(AccountScreenStyle.SMALL_FONT.fit(status,w-28),left+14,top+h-80,AccountScreenStyle.ERROR);AccountScreenStyle.panel(left+14,top+h-65,w-28,22,AccountScreenStyle.SURFACE,hex.isFocused()?AccountScreenStyle.ACCENT:AccountScreenStyle.BORDER);hex.drawTextBox();super.drawScreen(mx,my,partial);
    }finally{MenuGlass.end();}}
    private static void quad(float x,float y,float w,float h,int tl,int tr,int bl,int br){GlStateManager.disableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,771);GL11.glShadeModel(GL11.GL_SMOOTH);GL11.glBegin(GL11.GL_QUADS);tint(tl);GL11.glVertex2f(x,y);tint(tr);GL11.glVertex2f(x+w,y);tint(br);GL11.glVertex2f(x+w,y+h);tint(bl);GL11.glVertex2f(x,y+h);GL11.glEnd();GL11.glShadeModel(GL11.GL_FLAT);GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);}
    private static void tint(int c){GL11.glColor4f((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,1);}
    private void pick(int x,int y){if(drag==0){hsv[1]=Math.max(0,Math.min(1,(x-left-14)/(float)size));hsv[2]=1-Math.max(0,Math.min(1,(y-top-43)/(float)size));}else hsv[0]=Math.max(0,Math.min(.99999F,(y-top-43)/(float)size));hex.setText(String.format("#%06X",color()&0xFFFFFF));if(livePreview)apply.accept(color());}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{hex.mouseClicked(x,y,button);if(button==0&&y>=top+43&&y<=top+43+size){if(x>=left+14&&x<=left+14+size)drag=0;else if(x>=left+size+24&&x<=left+size+44)drag=1;if(drag>=0){pick(x,y);return;}}super.mouseClicked(x,y,button);}
    @Override protected void mouseReleased(int x,int y,int button){drag=-1;super.mouseReleased(x,y,button);}
    @Override protected void actionPerformed(GuiButton button){if(button.id==0){if(livePreview)apply.accept(original);mc.displayGuiScreen(parent);}else try{String text=hex.getText().replace("#","");if(!text.matches("[0-9a-fA-F]{6}"))throw new IllegalArgumentException();apply.accept(MenuPalette.parse(text));mc.displayGuiScreen(parent);}catch(IllegalArgumentException invalid){status="Use six hexadecimal digits.";}}
    @Override protected void keyTyped(char c,int key)throws IOException{if(key==Keyboard.KEY_ESCAPE){if(livePreview)apply.accept(original);mc.displayGuiScreen(parent);return;}if(hex.textboxKeyTyped(c,key)){if(livePreview&&hex.getText().matches("#[0-9a-fA-F]{6}")){int color=MenuPalette.parse(hex.getText());Color.RGBtoHSB(color>>16&255,color>>8&255,color&255,hsv);apply.accept(color);}return;}super.keyTyped(c,key);}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);}
    @Override public void updateScreen(){hex.updateCursorCounter();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
