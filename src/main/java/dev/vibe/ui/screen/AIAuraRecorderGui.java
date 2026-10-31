package dev.vibe.ui.screen;

import dev.vibe.aiaura.*;
import dev.vibe.module.impl.combat.AIAuraModule;
import dev.vibe.ui.*;
import dev.vibe.ui.render.AuraArenaRenderer;
import java.awt.Font;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.*;
import org.lwjgl.opengl.*;

/** Local mouse-grabbed practice arena and its recorder/training dashboard. */
public final class AIAuraRecorderGui extends GuiScreen {
    private static final NeverLoseFont TITLE=new NeverLoseFont(Font.BOLD,28),SUBTITLE=new NeverLoseFont(Font.BOLD,24),BODY=new NeverLoseFont(Font.PLAIN,21);
    private static final int INK=0xFFE8E8EB,MUTED=0xFF9A9BA1,ACCENT=0xFFADC46A;
    private final AIAuraModule module;
    private final GuiScreen parent;
    private final AuraLearning learning;
    private final AuraArenaRenderer renderer=new AuraArenaRenderer();
    private RecorderArena arena;
    private boolean running,preview,attackDown;
    private long lastTick;
    private float scale;private int left,top;
    private final List<Action> actions=new ArrayList<>();
    public AIAuraRecorderGui(AIAuraModule module,GuiScreen parent){this.module=module;this.parent=parent;learning=module.learning();}
    @Override public boolean doesGuiPauseGame(){return true;}
    @Override public void initGui(){layout();}
    private void layout(){scale=Math.max(.2F,Math.min(1,Math.min((width-16)/780F,(height-16)/500F)));left=(Math.round(width/scale)-780)/2;top=(Math.round(height/scale)-500)/2;}
    public void start(boolean modelPreview){
        if(learning.busy||modelPreview&&learning.model()==null)return;
        try{preview=modelPreview;arena=new RecorderArena(learning,System.nanoTime());configure();arena.begin(preview);arena.perspective=mc.gameSettings.thirdPersonView;arena.input(0,0,mc.gameSettings.invertMouse,Float.floatToIntBits(arena.sensitivity),Float.floatToIntBits(arena.fov),(int)Math.round(arena.reach*1000));arena.input(3,mc.gameSettings.keyBindTogglePerspective.getKeyCode(),true,arena.perspective,0,0);running=true;attackDown=false;lastTick=System.nanoTime();if(Mouse.isCreated())Mouse.setGrabbed(true);}catch(IOException e){learning.status=e.getMessage();}
    }
    private void configure(){arena.reach=module.reach.getDouble();arena.sensitivity=mc.gameSettings.mouseSensitivity;arena.fov=mc.gameSettings.fovSetting;arena.aspect=mc.displayWidth/(double)Math.max(1,mc.displayHeight);}
    public void pause(){if(!running)return;running=false;attackDown=false;if(Mouse.isCreated())Mouse.setGrabbed(false);arena.finish();}
    @Override public void onGuiClosed(){pause();}
    @Override public void updateScreen(){
        if(!running)return;long now=System.nanoTime();if(Display.isCreated()&&!Display.isActive()||now-lastTick>350000000L){pause();return;}lastTick=now;configure();arena.tick(keys(),module.boosterAiming.getDouble(),module.boosterClicking.getDouble());
    }
    private boolean down(KeyBinding key){return dev.vibe.input.VanillaClicks.physicallyDown(key);}
    private int keys(){int k=0;if(down(mc.gameSettings.keyBindForward))k|=RecorderArena.FORWARD;if(down(mc.gameSettings.keyBindBack))k|=RecorderArena.BACK;if(down(mc.gameSettings.keyBindLeft))k|=RecorderArena.LEFT;if(down(mc.gameSettings.keyBindRight))k|=RecorderArena.RIGHT;if(down(mc.gameSettings.keyBindJump))k|=RecorderArena.JUMP;if(down(mc.gameSettings.keyBindSneak))k|=RecorderArena.SNEAK;if(down(mc.gameSettings.keyBindSprint))k|=RecorderArena.SPRINT;if(down(mc.gameSettings.keyBindAttack))k|=RecorderArena.ATTACK;if(down(mc.gameSettings.keyBindUseItem))k|=RecorderArena.USE;return k;}
    @Override public void handleMouseInput()throws IOException{
        if(!running){super.handleMouseInput();return;}
        int dx=Mouse.getEventDX(),dy=Mouse.getEventDY(),wheel=Mouse.getEventDWheel(),button=Mouse.getEventButton();boolean state=Mouse.getEventButtonState();
        try{arena.input(1,button-100,state,dx,dy,wheel);}catch(IOException e){learning.status="Input log failed: "+e.getMessage();pause();return;}
        arena.look(dx,-dy*(mc.gameSettings.invertMouse?-1:1));if(wheel!=0)arena.slot=(arena.slot+(wheel>0?8:1))%9;
        if(button>=0&&state)perspectivePressed(button-100);
        if(button>=0&&button-100==mc.gameSettings.keyBindAttack.getKeyCode()){if(state&&!attackDown){attackDown=true;arena.attackDown();}else if(!state){attackDown=false;arena.releaseAttack();}}
    }
    @Override public void handleKeyboardInput()throws IOException{
        int code=Keyboard.getEventKey();boolean state=Keyboard.getEventKeyState();
        if(running){
            if(code==Keyboard.KEY_ESCAPE&&state){pause();return;}
            try{arena.input(2,code,state,0,0,0);}catch(IOException e){learning.status="Input log failed: "+e.getMessage();pause();return;}
            if(state&&!Keyboard.isRepeatEvent())perspectivePressed(code);
            if(code==mc.gameSettings.keyBindAttack.getKeyCode()&&!Keyboard.isRepeatEvent()){if(state&&!attackDown){attackDown=true;arena.attackDown();}else if(!state){attackDown=false;arena.releaseAttack();}}
            if(state)for(int i=0;i<9;i++)if(code==mc.gameSettings.keyBindsHotbar[i].getKeyCode())arena.slot=i;
        }else super.handleKeyboardInput();
    }
    @Override protected void keyTyped(char character,int key){if(key==Keyboard.KEY_ESCAPE)mc.displayGuiScreen(parent);}
    private void changePerspective()throws IOException{arena.cyclePerspective();arena.input(3,mc.gameSettings.keyBindTogglePerspective.getKeyCode(),true,arena.perspective,0,0);}
    void perspectivePressed(int code)throws IOException{if(running&&code==mc.gameSettings.keyBindTogglePerspective.getKeyCode())changePerspective();}
    @Override protected void mouseClicked(int x,int y,int button){if(button!=0||running)return;int mx=(int)(x/scale),my=(int)(y/scale);for(Action action:actions)if(action.enabled&&mx>=action.x&&mx<=action.x+action.w&&my>=action.y&&my<=action.y+action.h){action.run.run();return;}}
    @Override public void drawScreen(int mouseX,int mouseY,float partial){
        if(arena!=null)renderer.render(arena,running?partial:1);else{Gui.drawRect(0,0,width,height,0xFF0B0D10);for(int x=0;x<width;x+=32)Gui.drawRect(x,0,x+1,height,0xFF11151A);for(int y=0;y<height;y+=32)Gui.drawRect(0,y,width,y+1,0xFF11151A);}
        GuiRenderState.prepare(false);if(running){live();return;}
        Gui.drawRect(0,0,width,height,0xCD08090B);layout();GL11.glPushMatrix();GL11.glScalef(scale,scale,1);try{dashboard((int)(mouseX/scale),(int)(mouseY/scale));}finally{GL11.glPopMatrix();}
    }
    private void dashboard(int mx,int my){
        actions.clear();SkeetEditorStyle.window(left,top,left+780,top+500,"AIAura Recorder","Manual demonstrations and local training");
        card(left+18,top+38,355,207,"Recording library");card(left+389,top+38,373,207,"Personal model");
        AuraStatistics stats=learning.statistics();stat("Frames",String.valueOf(stats.frames),left+31,top+65);stat("Clicks",String.valueOf(stats.clicks),left+146,top+65);stat("Sessions",String.valueOf(stats.sessions),left+258,top+65);
        int[] coverage=learning.coverage();String[] names={"Near reach","Edge of reach","Out of reach"};
        for(int i=0;i<3;i++){int y=top+141+i*27;BODY.draw(names[i],left+32,y,MUTED);Gui.drawRect(left+150,y+4,left+300,y+9,0xFF242629);Gui.drawRect(left+150,y+4,left+150+Math.min(150,Math.round(coverage[i]/20F*150)),y+9,coverage[i]>=20?ACCENT:0xFF52585C);BODY.draw(String.valueOf(coverage[i]),left+313,y-1,INK);}
        TrainedAura model=learning.model();SUBTITLE.draw(learning.busy?"Training in progress":model==null?"No trained model":"Personal model ready",left+405,top+66,INK);
        BODY.draw(model==null?"Record your play, then train and preview.":model.records+" training frames",left+405,top+96,MUTED);
        BODY.draw(learning.busy?"Epoch "+learning.epoch+" of 60":model==null?"Training runs locally on your CPU.":model.epochs+" completed epochs",left+405,top+124,MUTED);
        BODY.draw(learning.busy||model!=null?String.format(Locale.ROOT,"Training loss  %.4f",learning.trainingLoss):"Your model stays on this computer.",left+405,top+151,INK);
        BODY.draw(learning.busy||model!=null?String.format(Locale.ROOT,"Validation loss  %.4f",learning.validationLoss):"Preview actions are never recorded.",left+405,top+176,MUTED);
        Gui.drawRect(left+405,top+217,left+746,top+221,0xFF26282B);if(learning.busy||model!=null)Gui.drawRect(left+405,top+217,left+405+(learning.busy?Math.round(341*learning.epoch/60F):341),top+221,ACCENT);
        card(left+18,top+260,744,112,"Statistics from manual play");
        metric("Recorded time",duration(stats.seconds),left+32,top+287);metric("Click rate",decimal(stats.cps," CPS"),left+207,top+287);metric("Aim reaction",decimal(stats.aimReactionMs," ms"),left+390,top+287);metric("Click reaction",decimal(stats.clickReactionMs," ms"),left+575,top+287);
        BODY.draw("Tracked aimpoints  "+stats.aimpoints,left+32,top+341,MUTED);BODY.draw(String.format(Locale.ROOT,"Mean aim error  %.1f°",stats.angularError),left+286,top+341,MUTED);BODY.draw("Height samples  "+stats.heightSamples,left+531,top+341,MUTED);
        card(left+18,top+386,744,47,arena==null?"Practice arena":"Last practice");
        String practice=arena==null?"Nonlethal practice with climbable blocks and elevated opponents.":String.format(Locale.ROOT,"Wave %d    %d eliminations    %.0f%% hit accuracy    %.1f dealt    %.1f received",arena.wave,arena.kills,accuracy(),arena.damageDealt,arena.damageTaken);
        BODY.draw(BODY.fit(practice,712),left+32,top+410,INK);
        String status=!learning.busy&&model==null&&!learning.ready()?learning.requirement():learning.status;BODY.draw(BODY.fit(status,735),left+21,top+441,learning.status.toLowerCase(Locale.ROOT).contains("failed")?0xFFE7867B:MUTED);
        button(left+18,top+467,186,"Record manually",!learning.busy,()->start(false),mx,my,true);
        button(left+214,top+467,170,learning.busy?"Training…":"Train model",!learning.busy&&learning.ready(),()->learning.train(),mx,my,false);
        button(left+394,top+467,170,"Preview model",!learning.busy&&model!=null,()->start(true),mx,my,false);
        button(left+632,top+467,130,"Back",true,()->mc.displayGuiScreen(parent),mx,my,false);
    }
    private void live(){
        int originalWidth=width,originalHeight=height;float hudScale=Math.min(1,width/540F);GL11.glPushMatrix();GL11.glScalef(hudScale,hudScale,1);width=Math.round(width/hudScale);height=Math.round(height/hudScale);
        try{
            card(10,10,238,63,preview?"Model preview":"Manual recording");BODY.draw("Wave "+arena.wave+"    "+arena.kills+" eliminations",22,34,INK);BODY.draw(String.format(Locale.ROOT,"%.0f%% accuracy    %.1f damage",accuracy(),arena.damageDealt),22,53,MUTED);
            int cx=width/2,cy=height/2,color=arena.selected!=null&&AuraFeatures.distance(arena.observation(arena.selected))<=arena.reach?ACCENT:0xFF9AA9B6;
            Gui.drawRect(cx-7,cy-1,cx-3,cy+1,color);Gui.drawRect(cx+3,cy-1,cx+7,cy+1,color);Gui.drawRect(cx-1,cy-7,cx+1,cy-3,color);Gui.drawRect(cx-1,cy+3,cx+1,cy+7,color);
            if(width>800){String text=preview?"Preview is excluded from your data":learning.frames()+" recorded frames";BODY.draw(text,width-BODY.width(text)-14,18,MUTED);}
            BODY.draw("Nonlethal practice",width-126,42,MUTED);
        }finally{width=originalWidth;height=originalHeight;GL11.glPopMatrix();}
    }
    private double accuracy(){return arena.hits+arena.misses==0?0:100D*arena.hits/(arena.hits+arena.misses);}
    private static String duration(double seconds){int value=(int)seconds;return value/60+"m "+value%60+"s";}
    private static String decimal(double value,String suffix){return value==0?"—":String.format(Locale.ROOT,"%.1f%s",value,suffix);}
    private static void stat(String label,String value,int x,int y){TITLE.draw(value,x,y,INK);BODY.draw(label,x,y+25,MUTED);}
    private static void metric(String label,String value,int x,int y){BODY.draw(label,x,y,MUTED);SUBTITLE.draw(value,x,y+20,INK);}
    private static void card(int x,int y,int w,int h,String caption){SkeetEditorStyle.panel(x,y,x+w,y+h,caption);}
    private void button(int x,int y,int w,String text,boolean enabled,Runnable action,int mx,int my,boolean primary){
        boolean hover=mx>=x&&mx<=x+w&&my>=y&&my<=y+25;Gui.drawRect(x,y,x+w,y+25,!enabled?0xFF191A1C:hover?0xFF303334:0xFF222326);SkeetEditorStyle.border(x,y,x+w,y+25,enabled&&primary?ACCENT:0xFF3A3B3E);
        BODY.draw(text,x+(w-BODY.width(text))/2,y+7,!enabled?0xFF66696F:primary?ACCENT:INK);actions.add(new Action(x,y,w,25,enabled,action));
    }
    private static final class Action {final int x,y,w,h;final boolean enabled;final Runnable run;Action(int x,int y,int w,int h,boolean enabled,Runnable run){this.x=x;this.y=y;this.w=w;this.h=h;this.enabled=enabled;this.run=run;}}
}
