package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.game.gta.Gta7Game;
import dev.vibe.game.gta.Gta7Progress;
import dev.vibe.game.gta.Gta7Renderer;
import dev.vibe.game.gta.Gta7World;
import dev.vibe.module.impl.EspModule;
import dev.vibe.module.impl.Gta7Module;
import java.io.IOException;
import java.math.BigInteger;
import dev.vibe.game.gta.Gta7Regions;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/** Full-screen first-person GTA7. Minecraft bindings are sampled without forwarding gameplay input. */
public final class Gta7Gui extends GuiScreen {
    private static final int INK=0xCF14212B, MUTED=0xFFB2BDC4, WHITE=0xFFF2EDE0, ACCENT=0xFF99DCC5;
    private final Gta7Module module;
    private Gta7Game game;
    private Gta7Renderer renderer;
    private EspModule esp;
    private boolean paused, captured, attackReleased, previousDead, showHelp;
    private long lastFrame;
    private BigInteger soundAmmo=BigInteger.valueOf(30);
    private int shopTab, shopPage;
    private double soundHit;
    private final CustomCrosshairRenderer customCrosshair = new CustomCrosshairRenderer();
    private float crosshairMovement;

    public Gta7Gui(Gta7Module module) { this.module=module; }

    @Override public void initGui() {
        // Minecraft calls initGui again on F11/resize/GUI-scale changes. Never reset the run here.
        if(game==null){
            Gta7Progress progress=Gta7Progress.load(mc.mcDataDir.toPath().resolve("vibe/gta7-progress.json"));
            game=new Gta7Game(new Gta7World(),progress);
            renderer=new Gta7Renderer(mc);
            esp=Vibe.getInstance().getModuleManager().getModule(EspModule.class);
        }
        lastFrame=System.nanoTime();
        capture(!paused&&!game.dead&&Display.isActive());
    }

    private void capture(boolean value){
        if(!Mouse.isCreated())return;
        if(captured!=value||Mouse.isGrabbed()!=value){
            Mouse.setGrabbed(value);Mouse.getDX();Mouse.getDY();captured=value;
            attackReleased=false;
        }
    }

    private static boolean down(KeyBinding binding){
        int key=binding.getKeyCode();
        return key<0 ? Mouse.isCreated()&&key+100>=0&&key+100<Mouse.getButtonCount()&&Mouse.isButtonDown(key+100)
                : key>0&&key<Keyboard.KEYBOARD_SIZE&&Keyboard.isKeyDown(key);
    }
    private static String key(KeyBinding binding){return GameSettings.getKeyDisplayString(binding.getKeyCode());}

    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){
        long now=System.nanoTime();double dt=Math.min(.1,Math.max(0,(now-lastFrame)/1e9));lastFrame=now;
        if(!Display.isActive()&&!paused&&!game.dead){paused=true;capture(false);}
        Gta7Game.Input input=new Gta7Game.Input();
        if(!paused&&!game.dead&&captured){
            float sensitivity=mc.gameSettings.mouseSensitivity*.6f+.2f;
            double scale=sensitivity*sensitivity*sensitivity*8*.15;
            int lookX=Mouse.getDX(),lookY=Mouse.getDY();if(lookX!=0||lookY!=0)game.activity();
            game.yaw=(game.yaw+lookX*scale)%360;
            game.pitch=Math.max(-89,Math.min(89,game.pitch-lookY*scale*(mc.gameSettings.invertMouse?-1:1)));
            input.forward=down(mc.gameSettings.keyBindForward);input.back=down(mc.gameSettings.keyBindBack);
            input.left=down(mc.gameSettings.keyBindLeft);input.right=down(mc.gameSettings.keyBindRight);
            input.jump=down(mc.gameSettings.keyBindJump);input.sneak=down(mc.gameSettings.keyBindSneak);
            input.sprint=down(mc.gameSettings.keyBindSprint);input.aim=down(mc.gameSettings.keyBindUseItem);
            if(!down(mc.gameSettings.keyBindAttack))attackReleased=true;
            input.attack=attackReleased&&down(mc.gameSettings.keyBindAttack);
            double beforeX = game.x, beforeZ = game.z;
            game.advance(dt,input);
            crosshairMovement = (float)(Math.hypot(game.x-beforeX, game.z-beforeZ) / Math.max(.001, dt) / 8);
            sounds();
        }
        if(game.dead&&!previousDead){capture(false);game.progress.save();}
        previousDead=game.dead;
        renderer.render(game,esp,input.aim);
        GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1f);
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        GlStateManager.disableDepth();GlStateManager.depthMask(false);
        renderer.overlay();
        atmosphere();
        hud(input.aim);
        if (Vibe.getInstance().getHudManager() != null) Vibe.getInstance().getHudManager().drawMusic(false);
        if(paused||game.dead)menu(mouseX,mouseY);
        GlStateManager.depthMask(true);GlStateManager.enableDepth();
    }

    private void sounds(){
        if(game.ammo.compareTo(soundAmmo)<0)mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(
                new net.minecraft.util.ResourceLocation("random.explode"),1.8f));
        if(game.hitMarker>soundHit)mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(
                new net.minecraft.util.ResourceLocation("random.successful_hit"),1.2f));
        soundAmmo=game.ammo;soundHit=game.hitMarker;
    }

    private void hud(boolean aim){
        int margin=12;
        panel(margin,margin,Math.min(width-12,margin+214),margin+37);
        text("GTA",margin+9,margin+7,WHITE);text("7",margin+29,margin+7,0xFFE6B98D);
        text("O P E N   W O R L D",margin+46,margin+8,MUTED);
        text(game.clock(),margin+174,margin+8,ACCENT);
        text(fontRendererObj.trimStringToWidth(game.district(),194),margin+9,margin+23,WHITE);
        int wantedWidth=87;
        if(width>360){
            panel(width-margin-wantedWidth,margin,width-margin,margin+37);
            text(game.wanted>0?"POLICE SEARCH":"LOW PROFILE",width-margin-wantedWidth+7,margin+7,game.wanted>0?0xFFECB38B:MUTED);
            for(int i=0;i<5;i++)wantedStar(width-margin-wantedWidth+12+i*14,margin+26,i<game.wanted);
        }else if(game.wanted>0)text("WANTED "+game.wanted,margin+9,margin+42,0xFFF0BB84);
        int hpWidth=Math.min(190,width/2-16),hpX=width-margin-hpWidth,hpY=height-76;
        panel(hpX,hpY,width-margin,height-margin);
        text("HEALTH",hpX+9,hpY+8,MUTED);
        String health=Gta7Progress.format(game.health.toBigInteger())+" / "+Gta7Progress.format(game.progress.maxHealth().toBigInteger());
        rightText(health,width-margin-8,hpY+8,WHITE);
        Gui.drawRect(hpX+9,hpY+22,width-margin-9,hpY+28,0xFF32434C);
        int filled=hpX+9+Math.round((hpWidth-18)*game.healthRatio());
        if(filled>hpX+9)drawGradientRect(hpX+9,hpY+22,filled,hpY+28,game.healthRatio()<.3?0xFFF1B59B:0xFFB9EBCC,game.healthRatio()<.3?0xFFCF796E:0xFF70BCA7);
        text("XP BANK",hpX+9,hpY+38,MUTED);
        rightText(Gta7Progress.format(game.progress.xp()),width-margin-8,hpY+38,ACCENT);
        text(fontRendererObj.trimStringToWidth(game.kills+" eliminations this life",hpWidth-18),hpX+9,hpY+51,0xFF8CA6A1);
        int slotWidth=Math.min(94,(width-hpWidth-42)/2),slotY=height-46;
        slot(12,slotY,slotWidth,0,key(mc.gameSettings.keyBindsHotbar[0]),"KNIFE");
        slot(16+slotWidth,slotY,slotWidth,1,key(mc.gameSettings.keyBindsHotbar[1]),"AK47");
        if(width>=480&&height>=300)minimap(12,height-153,86);
        if(!game.dead&&!paused){
            int cx=width/2,cy=height/2,gap=aim?3:5+(int)(game.recoil*4);
            int c=game.hitMarker>0?0xFFE7B786:0xEEE8EBD8;
            if(!customCrosshair.renderGta7(width,height,crosshairMovement) && (!aim||game.weapon==0)){
                Gui.drawRect(cx-gap-4,cy-1,cx-gap+1,cy+2,0x800B171C);Gui.drawRect(cx+gap-1,cy-1,cx+gap+4,cy+2,0x800B171C);
                Gui.drawRect(cx-1,cy-gap-4,cx+2,cy-gap+1,0x800B171C);Gui.drawRect(cx-1,cy+gap-1,cx+2,cy+gap+4,0x800B171C);
                Gui.drawRect(cx-gap-4,cy,cx-gap,cy+1,c);Gui.drawRect(cx+gap,cy,cx+gap+4,cy+1,c);
                Gui.drawRect(cx,cy-gap-4,cx+1,cy-gap,c);Gui.drawRect(cx,cy+gap,cx+1,cy+gap+4,c);
            }
            if(game.hitMarker>0)text("x",cx-2,cy-4,0xFFFFD7A3);
            if(game.reload>0){
                centerText("RELOADING  "+(int)Math.ceil(game.reload)+"s",cy+20,WHITE);
                Gui.drawRect(cx-30,cy+32,cx+30,cy+35,0xB0223641);
                Gui.drawRect(cx-30,cy+32,cx-30+(int)(60*(1-game.reload/2.1)),cy+35,ACCENT);
            }
            if(game.time<game.noticeUntil&&width>380){
                String notice=fontRendererObj.trimStringToWidth(game.notice,width-48);
                int nw=fontRendererObj.getStringWidth(notice),nx=(width-nw)/2;
                panel(nx-9,height-95,nx+nw+9,height-75);text(notice,nx,height-89,WHITE);
            }
            if(game.hintsVisible()||showHelp)text("ESC  Pause  /  F1  Controls",12,55,0xFFCED6CF);
            if(showHelp||game.hintsVisible())controls(12,70);
            if(game.world.elevatorAt(game.x,game.z)!=null)centerText("E  Floor up / Shift+E  Down / Ctrl+E  Top",height/2+40,ACCENT);
            else if(game.world.ladderAt(game.x,game.y,game.z)!=null)centerText("SPACE  Climb / SHIFT  Descend",height/2+40,ACCENT);
            if(game.progress.jetCapacity().signum()>0) {
                text("JET "+(game.jetFuel.compareTo(java.math.BigDecimal.valueOf(1000000))>=0?Gta7Progress.format(game.jetFuel.toBigInteger()):game.jetFuel.setScale(1,java.math.RoundingMode.DOWN).toPlainString())+"s"+(game.jetting?" / BOOST":""),12,height-60,ACCENT);
            }
            if(game.healthRatio()<.15)centerText("CRITICAL HEALTH",height/2+32,0xFFF28888);
        }
        if(game.progress.saveError()!=null)text(fontRendererObj.trimStringToWidth(game.progress.saveError(),width-24),12,height-9,0xFFFFAF90);
    }

    private void slot(int x,int y,int w,int index,String binding,String label){
        panel(x,y,x+w,height-12);
        if(game.weapon==index)Gui.drawRect(x,y,x+w,y+2,ACCENT);
        text(fontRendererObj.trimStringToWidth(binding+"  "+label,w-12),x+6,y+7,game.weapon==index?WHITE:MUTED);
        String info=index==0?"MELEE":game.reload>0?"RELOADING":Gta7Progress.format(game.ammo)+" / "+Gta7Progress.format(game.progress.magazineSize());
        text(fontRendererObj.trimStringToWidth(info,w-12),x+6,y+21,game.weapon==index?ACCENT:0xFF8CA6A1);
    }

    private void controls(int x,int y){
        String[] rows={key(mc.gameSettings.keyBindForward)+" "+key(mc.gameSettings.keyBindLeft)+" "+key(mc.gameSettings.keyBindBack)+" "+key(mc.gameSettings.keyBindRight)+"  Move   /   Mouse  Look",
                key(mc.gameSettings.keyBindJump)+"  Jump / hold in air: jet   /   "+key(mc.gameSettings.keyBindSprint)+"  Sprint",
                key(mc.gameSettings.keyBindSneak)+"  Crouch   /   "+key(mc.gameSettings.keyBindUseItem)+"  Aim",
                key(mc.gameSettings.keyBindAttack)+"  Attack   /   "+"R  Reload",
                key(mc.gameSettings.keyBindsHotbar[0])+"  Knife   /   "+key(mc.gameSettings.keyBindsHotbar[1])+"  AK47"};
        int w=Math.min(width-x-14,310);
        panel(x,y,x+w,y+rows.length*13+12);
        for(int i=0;i<rows.length;i++)text(fontRendererObj.trimStringToWidth(rows[i],w-14),x+7,y+7+i*13,MUTED);
    }

    private void minimap(int x,int y,int size){
        panel(x-2,y-2,x+size+2,y+size+2);
        double radius=65,scale=(size-8)/(radius*2),left=game.x-radius,top=game.z-radius;
        for(Gta7Regions.Region region:Gta7Regions.Region.values())
            mapRect(region.x()-90,region.z()-90,region.x()+90,region.z()+90,region.color,x,y,size,left,top,scale);
        for(Gta7Regions.Region region:Gta7Regions.Region.values())if(region!=Gta7Regions.Region.CITY){
            mapRect(region.x()-3,region.z()-90,region.x()+3,region.z()+90,0x878B7F,x,y,size,left,top,scale);
            mapRect(region.x()-90,region.z()-3,region.x()+90,region.z()+3,0x878B7F,x,y,size,left,top,scale);
        }
        for(int i=0;i<=Gta7World.BLOCKS;i++){
            double road=i*Gta7World.BLOCK;
            mapRect(road-4.5,0,road+4.5,180,0x3B5057,x,y,size,left,top,scale);
            mapRect(0,road-4.5,180,road+4.5,0x3B5057,x,y,size,left,top,scale);
        }
        for(Gta7World.Building b:game.world.buildings)mapRect(b.x,b.z,b.x+b.width,b.z+b.depth,0xCAD0BC,x,y,size,left,top,scale);
        for(Gta7Game.Drop d:game.drops)mapDot(d.x,d.z,ACCENT,x,y,size,left,top,scale);
        for(Gta7Game.Npc npc:game.npcs)if(npc.health>0&&game.hostile(npc))mapDot(npc.x,npc.z,npc.cop?0xFFF0BC84:0xFFD97D85,x,y,size,left,top,scale);
        int px=x+size/2,pz=y+size/2;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glPushMatrix();GL11.glTranslated(px,pz,0);GL11.glRotated(game.yaw,0,0,1);
        GL11.glColor3f(.12f,.22f,.25f);GL11.glBegin(GL11.GL_TRIANGLES);GL11.glVertex2d(0,-5);GL11.glVertex2d(-3.7,3.5);GL11.glVertex2d(3.7,3.5);GL11.glEnd();
        GL11.glColor3f(.94f,.99f,.89f);GL11.glBegin(GL11.GL_TRIANGLES);GL11.glVertex2d(0,-3.5);GL11.glVertex2d(-2.2,2.2);GL11.glVertex2d(2.2,2.2);GL11.glEnd();
        GL11.glPopMatrix();GL11.glPopAttrib();
        text("N",x+size/2-2,y-11,MUTED);
        Gui.drawRect(x+4,y+size-14,x+43,y+size-4,0xDC233B42);
        Gui.drawRect(x+6,y+size-8,x+18,y+size-6,WHITE);text("20m",x+21,y+size-12,WHITE);
    }
    private void mapRect(double x0,double z0,double x1,double z1,int color,int x,int y,int size,double left,double top,double scale){
        int ax=Math.max(x+4,x+4+(int)((x0-left)*scale)),az=Math.max(y+4,y+4+(int)((z0-top)*scale));
        int bx=Math.min(x+size-4,x+4+(int)((x1-left)*scale)),bz=Math.min(y+size-4,y+4+(int)((z1-top)*scale));
        if(bx>ax&&bz>az)Gui.drawRect(ax,az,bx,bz,0xFF000000|color);
    }
    private void mapDot(double px,double pz,int color,int x,int y,int size,double left,double top,double scale){
        int sx=x+4+(int)((px-left)*scale),sz=y+4+(int)((pz-top)*scale);
        if(sx>x+4&&sx<x+size-5&&sz>y+4&&sz<y+size-5)Gui.drawRect(sx-1,sz-1,sx+1,sz+1,color);
    }

    private int menuWidth(){return Math.min(600,width-20);}
    private int menuHeight(){return Math.min(game.dead?320:280,height-16);}
    private int menuTop(){return (height-menuHeight())/2;}
    private int shopColumns(){return menuWidth()>=450?2:1;}
    private int shopRows(){return Math.max(1,(menuHeight()-137)/43);}
    private int shopCount(){return shopTab==0?Gta7Progress.Upgrade.values().length:Gta7Progress.Skin.values().length*2;}
    private int shopPages(){return (shopCount()+shopRows()*shopColumns()-1)/(shopRows()*shopColumns());}
    private void title(String text,int x,int y,float scale,int color) {
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,0);GlStateManager.scale(scale,scale,1);
        this.text(text,0,0,color);GlStateManager.popMatrix();
    }
    private void menu(int mouseX,int mouseY){
        drawGradientRect(0,0,width,height,0xCF080F19,game.dead?0xE330111D:0xE10A202A);
        int w=menuWidth(),left=(width-w)/2,top=menuTop(),bottom=top+menuHeight();
        int accent=game.dead?0xFFF07483:ACCENT;
        // Offset frame, striped side rail and a large title give the menu its own visual hierarchy.
        Gui.drawRect(left-3,top+12,left+1,bottom-12,accent);
        drawGradientRect(left,top,left+w,bottom,0xEF172A36,0xF00C1823);
        Gui.drawRect(left,top,left+w,top+2,accent);
        for(int i=0;i<5;i++)Gui.drawRect(left+w-36+i*5,top+12,left+w-34+i*5,top+27,0x706B8993);
        text(game.dead?"GTA 7 / RUN COMPLETE":"GTA 7 / CITY ON HOLD",left+14,top+10,accent);
        title(game.dead?"WASTED":"PAUSED",left+12,top+25,2.6f,WHITE);
        rightText("XP "+Gta7Progress.format(game.progress.xp()),left+w-14,top+37,ACCENT);
        text(game.kills+" ELIMINATIONS  /  "+game.runXp+" XP THIS LIFE",left+14,top+55,MUTED);
        if(game.dead) {
            int half=(w-30)/2;
            button(left+10,top+70,left+10+half,top+87,"UPGRADES",mouseX,mouseY,shopTab==0);
            button(left+20+half,top+70,left+w-10,top+87,"WEAPON SKINS",mouseX,mouseY,shopTab==1);
            shopPage=Math.min(shopPage,shopPages()-1);
            int cols=shopColumns(),rows=shopRows(),cw=(w-20-(cols-1)*8)/cols;
            for(int cell=0;cell<cols*rows;cell++) {
                int index=shopPage*cols*rows+cell;if(index>=shopCount())break;
                int cx=left+10+(cell%cols)*(cw+8),cy=top+91+(cell/cols)*43;
                boolean hover=inside(mouseX,mouseY,cx,cy,cx+cw,cy+39);
                Gui.drawRect(cx,cy,cx+cw,cy+39,hover?0xFF284551:0xFF1B313F);
                if(shopTab==0) {
                    Gta7Progress.Upgrade upgrade=Gta7Progress.Upgrade.values()[index];
                    boolean capped=game.progress.capped(upgrade),afford=!capped&&game.progress.xp().compareTo(game.progress.cost(upgrade))>=0;
                    text(upgrade.title.toUpperCase(java.util.Locale.ROOT),cx+7,cy+5,WHITE);
                    rightText("LV "+Gta7Progress.format(game.progress.level(upgrade))+(upgrade.limited?"/10":""),cx+cw-7,cy+5,MUTED);
                    text(upgrade.bonus,cx+7,cy+16,MUTED);
                    text(upgrade.limited?"MAX LEVEL 10":"UNLIMITED LEVELS",cx+7,cy+28,0xFF718E9B);
                    rightText(capped?"MAX":Gta7Progress.format(game.progress.cost(upgrade))+" XP",cx+cw-7,cy+28,afford?ACCENT:MUTED);
                } else {
                    int weapon=index/Gta7Progress.Skin.values().length;
                    Gta7Progress.Skin skin=Gta7Progress.Skin.values()[index%Gta7Progress.Skin.values().length];
                    boolean owned=game.progress.owns(weapon,skin),equipped=game.progress.skin(weapon)==skin;
                    Gui.drawRect(cx+7,cy+7,cx+12,cy+32,0xFF000000|skin.primary);
                    Gui.drawRect(cx+12,cy+7,cx+17,cy+32,0xFF000000|skin.secondary);
                    text((weapon==0?"KNIFE / ":"AK47 / ")+skin.title,cx+24,cy+7,WHITE);
                    text(equipped?"EQUIPPED":owned?"CLICK TO EQUIP":"UNLOCK & EQUIP",cx+24,cy+23,equipped?ACCENT:MUTED);
                    if(!owned)rightText(skin.price+" XP",cx+cw-7,cy+23,ACCENT);
                }
            }
            int footer=bottom-32;
            button(left+10,footer,left+w/2-4,bottom-8,"RESPAWN",mouseX,mouseY,true);
            button(left+w/2+4,footer,left+w-10,bottom-8,"SAVE & EXIT",mouseX,mouseY,false);
            if(shopPages()>1) {
                int py=bottom-49;
                text("<",left+12,py,ACCENT);rightText(">",left+w-12,py,ACCENT);
                String page="PAGE "+(shopPage+1)+" / "+shopPages();text(page,left+(w-fontRendererObj.getStringWidth(page))/2,py,MUTED);
            }
        } else {
            int bw=w>=450?200:w-28;
            button(left+14,top+81,left+14+bw,top+109,"RESUME",mouseX,mouseY,true);
            button(left+14,top+117,left+14+bw,top+145,"CONTROLS  "+(showHelp?"ON":"OFF"),mouseX,mouseY,false);
            button(left+14,top+153,left+14+bw,top+181,"SAVE & EXIT",mouseX,mouseY,false);
            if(w>=450) {
                if(showHelp)controls(left+230,top+85);
                else {
                    text("EXPLORE / N",left+232,top+81,ACCENT);
                    int tw=(w-246)/3,th=Math.min(40,(menuHeight()-112)/3);
                    String[] names={"EMOJI","ARCTIC","MIRAGE","WIZARDS","CITY","GOBLINS","CANDY","DESERT","EMPIRE"};
                    int index=0;
                    for(Gta7Regions.Region region:Gta7Regions.Region.values()) {
                        int tx=left+230+(index%3)*tw,ty=top+96+(index/3)*th;
                        Gui.drawRect(tx,ty,tx+tw-3,ty+th-3,0x80000000|region.color);
                        text(names[index],tx+5,ty+th/2-5,WHITE);index++;
                    }
                }
            } else if(showHelp)text("F1: show controls when you resume",left+14,top+190,MUTED);
            if(menuHeight()>235)text("LOCAL PROGRESS SAVES AUTOMATICALLY",left+14,bottom-19,0xFF7C9AA6);
        }
    }

    private void button(int left,int top,int right,int bottom,String label,int mx,int my,boolean primary){
        Gui.drawRect(left,top,right,bottom,primary?(inside(mx,my,left,top,right,bottom)?0xFFCDEDBF:ACCENT):(inside(mx,my,left,top,right,bottom)?0xFF3C5855:0xFF294449));
        String localized=dev.vibe.language.LanguageManager.translate(label);
        text(localized,(left+right-fontRendererObj.getStringWidth(localized))/2,(top+bottom-8)/2,primary?0xFF193430:WHITE);
    }
    private static boolean inside(int x,int y,int left,int top,int right,int bottom){return x>=left&&x<right&&y>=top&&y<bottom;}
    private void panel(int left,int top,int right,int bottom){
        Gui.drawRect(left+2,bottom,right+2,bottom+2,0x28061018);
        Gui.drawRect(left,top+2,right,bottom-2,INK);
        Gui.drawRect(left+2,top,right-2,top+2,INK);Gui.drawRect(left+2,bottom-2,right-2,bottom,INK);
        Gui.drawRect(left+3,top,right-3,top+1,0x60859B9E);
    }
    private void atmosphere(){
        // Subtle screen-edge shading; text and status panels are drawn afterwards at native resolution.
        if(!game.dead&&(game.hurtFlash>0||game.healthRatio()<.15)) {
            double hurt=Math.min(1,game.hurtFlash/.32),critical=game.healthRatio()<.15?1:0;
            double pulse=critical*(.88+.12*Math.sin(game.time*6));
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_LIGHTING_BIT);
            GL11.glShadeModel(GL11.GL_SMOOTH);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glColor4d(.55,.015,.035,hurt*.15+pulse*.12);GL11.glVertex2d(width/2.0,height/2.0);
            GL11.glColor4d(critical>0?.22:.7,.006,.018,Math.min(.87,hurt*.65+pulse*.77));
            for(int i=0;i<=48;i++){double a=i*Math.PI/24;GL11.glVertex2d(width/2.0+Math.cos(a)*width*.72,height/2.0+Math.sin(a)*height*.72);}
            GL11.glEnd();GL11.glPopAttrib();GlStateManager.resetColor();
        }
        int edge=Math.max(12,height/9);
        drawGradientRect(0,0,width,edge,0x24081725,0x00081725);
        drawGradientRect(0,height-edge,width,height,0x00081725,0x35081725);
    }
    private void wantedStar(int x,int y,boolean active){
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        int color=active?0xF0BD88:0x52636B;
        GL11.glColor4f((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,1);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glVertex2f(x,y);
        for(int i=0;i<=10;i++){double a=-Math.PI/2+i*Math.PI/5,r=(i%2==0)?4.1:1.9;GL11.glVertex2d(x+Math.cos(a)*r,y+Math.sin(a)*r);}GL11.glEnd();
        GL11.glPopAttrib();GlStateManager.resetColor();
    }
    private void text(String s,int x,int y,int color){fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate(s),x,y,color);}
    private void rightText(String s,int right,int y,int color){String localized=dev.vibe.language.LanguageManager.translate(s);text(localized,right-fontRendererObj.getStringWidth(localized),y,color);}
    private void centerText(String s,int y,int color){String localized=dev.vibe.language.LanguageManager.translate(s);text(localized,(width-fontRendererObj.getStringWidth(localized))/2,y,color);}

    @Override public void handleMouseInput() throws IOException {
        if(paused||game.dead){super.handleMouseInput();return;}
        int button=Mouse.getEventButton();
        if(button>=0&&Mouse.getEventButtonState())bindingPressed(button-100);
    }

    private void bindingPressed(int code){
        if(code==0)return;
        game.activity();
        if(code==Keyboard.KEY_R){game.startReload();return;}
        if(code==Keyboard.KEY_E){game.useElevator(down(mc.gameSettings.keyBindSneak),Keyboard.isCreated()&&(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)||Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)));return;}
        if(code==mc.gameSettings.keyBindsHotbar[0].getKeyCode())game.equip(0);
        else if(code==mc.gameSettings.keyBindsHotbar[1].getKeyCode())game.equip(1);
        else if(code==mc.gameSettings.keyBindDrop.getKeyCode())game.startReload();
        else if(esp!=null&&esp.getKey()!=0&&code==esp.getKey())esp.toggle();
    }

    @Override protected void keyTyped(char typedChar,int keyCode) throws IOException {
        if(keyCode==Keyboard.KEY_ESCAPE){
            if(game.dead){exitGame();return;}
            paused=!paused;capture(!paused);lastFrame=System.nanoTime();return;
        }
        if(keyCode==Keyboard.KEY_F1){showHelp=!showHelp;return;}
        if(paused||game.dead)return;
        bindingPressed(keyCode);
    }

    @Override protected void mouseClicked(int mx,int my,int button) throws IOException {
        if(button!=0||!paused&&!game.dead)return;
        int w=menuWidth(),left=(width-w)/2,top=menuTop();
        int bottom=top+menuHeight();
        if(game.dead){
            if(inside(mx,my,left+10,top+70,left+w/2,top+87)){shopTab=0;shopPage=0;return;}
            if(inside(mx,my,left+w/2,top+70,left+w-10,top+87)){shopTab=1;shopPage=0;return;}
            if(inside(mx,my,left+10,bottom-53,left+35,bottom-34)){shopPage=(shopPage+shopPages()-1)%shopPages();return;}
            if(inside(mx,my,left+w-35,bottom-53,left+w-10,bottom-34)){shopPage=(shopPage+1)%shopPages();return;}
            int cols=shopColumns(),rows=shopRows(),cw=(w-20-(cols-1)*8)/cols;
            for(int cell=0;cell<cols*rows;cell++) {
                int index=shopPage*cols*rows+cell,cx=left+10+(cell%cols)*(cw+8),cy=top+91+(cell/cols)*43;
                if(index>=shopCount()||!inside(mx,my,cx,cy,cx+cw,cy+39))continue;
                if(shopTab==0)game.progress.purchase(Gta7Progress.Upgrade.values()[index]);
                else game.progress.purchaseSkin(index/Gta7Progress.Skin.values().length,Gta7Progress.Skin.values()[index%Gta7Progress.Skin.values().length]);
                return;
            }
            if(inside(mx,my,left+10,bottom-32,left+w/2-4,bottom-8)){
                game.respawn();previousDead=false;paused=false;capture(true);lastFrame=System.nanoTime();soundAmmo=game.ammo;shopPage=0;
            }else if(inside(mx,my,left+w/2+4,bottom-32,left+w-10,bottom-8))exitGame();
        }else{
            int bw=w>=450?200:w-28;
            if(inside(mx,my,left+14,top+81,left+14+bw,top+109)){paused=false;capture(true);lastFrame=System.nanoTime();}
            else if(inside(mx,my,left+14,top+117,left+14+bw,top+145))showHelp=!showHelp;
            else if(inside(mx,my,left+14,top+153,left+14+bw,top+181))exitGame();
        }
    }

    private void exitGame(){mc.displayGuiScreen(null);mc.setIngameFocus();}
    @Override public void onGuiClosed(){
        capture(false);
        if(game!=null)game.progress.save();
        if(renderer!=null)renderer.close();
        if(module.isEnabled())module.setEnabled(false);
        super.onGuiClosed();
    }
    @Override public boolean doesGuiPauseGame(){return true;}
}
