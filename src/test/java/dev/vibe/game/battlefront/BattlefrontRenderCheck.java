package dev.vibe.game.battlefront;

import dev.vibe.ui.Battlefront3Gui;
import java.io.File;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.Collections;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import static dev.vibe.game.battlefront.BattlefrontContent.*;

/** Real LWJGL/Forge screenshot and interaction checks, without opening a Minecraft window. */
public final class BattlefrontRenderCheck {
    private static Minecraft mc;private static Battlefront3Gui gui;private static BattlefrontGame game;
    private static BattlefrontRenderer renderer;private static BattlefrontProgress progress;private static Path root;
    private static void set(Class<?> c,Object o,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    private static Object get(String n)throws Exception{Field f=Battlefront3Gui.class.getDeclaredField(n);f.setAccessible(true);return f.get(gui);}
    private static void call(String n,Class<?>[] types,Object...args)throws Exception{Method m=Battlefront3Gui.class.getDeclaredMethod(n,types);m.setAccessible(true);m.invoke(gui,args);}
    public static void main(String[] args)throws Exception{
        root=Paths.get(args.length==0?"build/battlefront-render-check":args[0]);Files.createDirectories(root);
        Pbuffer buffer=new Pbuffer(1280,720,new PixelFormat(8,24,8),null,null);buffer.makeCurrent();
        try{
            Class<?> uc=Class.forName("sun.misc.Unsafe");Field uf=uc.getDeclaredField("theUnsafe");uf.setAccessible(true);Object unsafe=uf.get(null);
            mc=(Minecraft)uc.getMethod("allocateInstance",Class.class).invoke(unsafe,Minecraft.class);set(Minecraft.class,null,"theMinecraft",mc);
            mc.displayWidth=1280;mc.displayHeight=720;mc.gameSettings=new GameSettings();mc.gameSettings.guiScale=2;mc.gameSettings.fovSetting=80;
            set(Minecraft.class,mc,"mcDataDir",Files.createTempDirectory(root,"profile-").toFile());
            IMetadataSerializer meta=new IMetadataSerializer();set(Minecraft.class,mc,"mcLanguageManager",new LanguageManager(meta,"en_US"));
            meta.registerMetadataSectionType(new TextureMetadataSectionSerializer(),TextureMetadataSection.class);meta.registerMetadataSectionType(new FontMetadataSectionSerializer(),FontMetadataSection.class);
            SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(meta);resources.reloadResourcePack(new DefaultResourcePack(Collections.<String,File>emptyMap()));
            set(Minecraft.class,mc,"mcResourceManager",resources);mc.renderEngine=new TextureManager(resources);
            mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.renderEngine,false);mc.fontRendererObj.onResourceManagerReload(resources);OpenGlHelper.initializeTextures();
            progress=BattlefrontProgress.load(mc.mcDataDir.toPath().resolve("campaign.json"));renderer=new BattlefrontRenderer(mc);gui=new Battlefront3Gui(null);gui.mc=mc;set(GuiScreen.class,gui,"fontRendererObj",mc.fontRendererObj);
            set(Battlefront3Gui.class,gui,"progress",progress);set(Battlefront3Gui.class,gui,"renderer",renderer);
            game=new BattlefrontGame(progress,Scenario.GEONOSIS,0,Mode.CONQUEST,1,0,42);set(Battlefront3Gui.class,gui,"previewGame",game);set(Battlefront3Gui.class,gui,"game",game);
            for(int p=0;p<5;p++){set(Battlefront3Gui.class,gui,"page",p);frame("hub-"+p,true);}
            click(76,138);if((Integer)get("page")!=0)throw new AssertionError("Operations navigation failed");frame("navigation-operations",true);click(76,190);if((Integer)get("page")!=1)throw new AssertionError("Army navigation failed");set(Battlefront3Gui.class,gui,"page",4);frame("navigation-manual",true);
            long before=progress.credits();click(230,164); // Field manual has no purchase action.
            if(before!=progress.credits())throw new AssertionError("Manual consumed credits");
            set(Battlefront3Gui.class,gui,"page",2);frame("armory-before-purchase",true);click(231,203);
            if(progress.level(Upgrade.DAMAGE)!=1)throw new AssertionError("Armory click did not purchase");
            set(Battlefront3Gui.class,gui,"page",1);frame("army-before-edit",true);click(561,213);if(progress.squad(Role.ASSAULT)!=5)throw new AssertionError("Army decrease failed");click(643,269);if(progress.squad(Role.HEAVY)!=3)throw new AssertionError("Army increase failed");
            progress.award(12000,0);set(Battlefront3Gui.class,gui,"page",2);set(Battlefront3Gui.class,gui,"armoryTab",1);set(Battlefront3Gui.class,gui,"selectedWeapon",Weapon.DLT19X);frame("weapon-shop",true);click(780,477);
            if(progress.weapon()!=Weapon.DLT19X)throw new AssertionError("Weapon purchase/equip failed");click(765,369);if(progress.level(Weapon.DLT19X,WeaponMod.POWER)!=1)throw new AssertionError("Weapon upgrade failed");
            set(Battlefront3Gui.class,gui,"armoryTab",2);set(Battlefront3Gui.class,gui,"selectedArmor",Armor.BESKAR);frame("armor-shop",true);click(780,477);if(progress.armor()!=Armor.BESKAR)throw new AssertionError("Armor purchase/equip failed");
            set(Battlefront3Gui.class,gui,"page",1);set(Battlefront3Gui.class,gui,"armyTab",1);frame("army-tactics",true);click(230,305);if(progress.doctrine()!=Doctrine.BULWARK)throw new AssertionError("Doctrine did not change");click(602,425);if(progress.formation()!=Formation.COLUMN)throw new AssertionError("Formation did not change");
            set(Battlefront3Gui.class,gui,"armyTab",2);frame("army-loadout-before",true);click(310,303);click(310,303);if(progress.armyWeapon(Role.ASSAULT)!=Weapon.DLT19X)throw new AssertionError("Role weapon cycle failed");click(310,346);if(progress.armyArmor(Role.ASSAULT)!=Armor.BESKAR)throw new AssertionError("Role armor cycle failed");click(310,413);if(progress.veteran(Role.ASSAULT)!=1)throw new AssertionError("Training purchase failed");frame("army-loadout",true);
            set(Battlefront3Gui.class,gui,"armyTab",3);frame("appearance-before",true);for(int i=0;i<6;i++)click(400,251+i*39);if(progress.appearance(Role.ASSAULT).helmet!=1||progress.appearance(Role.ASSAULT).pack!=1)throw new AssertionError("Appearance click failed");frame("army-appearance",true);click(770,475);if(progress.appearance(Role.SCOUT).paint!=1)throw new AssertionError("Copy appearance failed");
            set(Battlefront3Gui.class,gui,"appearanceTarget",5);frame("character-appearance-before",true);for(int i=0;i<6;i++)click(400,251+i*39);frame("character-appearance",true);
            for(Scenario s:Scenario.values()){
                game=new BattlefrontGame(progress,s,0,Mode.CONQUEST,1,0,42);set(Battlefront3Gui.class,gui,"game",game);
                for(int i=0;i<900;i++)game.advance(1.0/30,new BattlefrontGame.Input());game.x=0;game.z=52;game.y=game.world.height(game.x,game.z);game.pitch=-4;game.yaw=0;
                frame(s.name().toLowerCase()+"-battle",false);set(Battlefront3Gui.class,gui,"paused",true);frame(s.name().toLowerCase()+"-pause",false);set(Battlefront3Gui.class,gui,"paused",false);
                call("keyTyped",new Class[]{char.class,int.class},'m',org.lwjgl.input.Keyboard.KEY_M);frame(s.name().toLowerCase()+"-tactical-map",false);click(607,264);if(!game.waypoint)throw new AssertionError("Map marker failed");frame(s.name().toLowerCase()+"-tactical-marker",false);click(760,459);if(game.order!=Order.HOLD||(Boolean)get("atlas")||(Boolean)get("paused"))throw new AssertionError("Map rally did not resume");
                BattlefrontArchitecture.Building district=game.world.buildings.get(s==Scenario.GEONOSIS?4:5);game.x=district.x;game.z=district.z+district.d+(district.floor>1?25:8);game.y=game.world.height(game.x,game.z);game.yaw=0;game.pitch=-15;
                if(game.world.blocked(game.x,game.z,.4))throw new AssertionError("District render point is obstructed");frame(s.name().toLowerCase()+"-outer-district",false);
                for(BattlefrontArchitecture.Building b:game.world.buildings){game.x=b.x+.4;game.z=b.z+b.d-3;game.y=b.base+b.floor;game.yaw=0;game.pitch=1;frame("interior-"+b.name.toLowerCase().replaceAll("[^a-z]+","-"),false);}
            }
            game.finish(true);frame("victory",false);set(Battlefront3Gui.class,gui,"hub",true);set(Battlefront3Gui.class,gui,"page",0);
            mc.displayWidth=640;mc.displayHeight=480;frame("small-operations",true);set(Battlefront3Gui.class,gui,"page",2);set(Battlefront3Gui.class,gui,"armoryTab",0);frame("small-armory",true);set(Battlefront3Gui.class,gui,"armoryTab",1);frame("small-weapons",true);
            set(Battlefront3Gui.class,gui,"page",1);set(Battlefront3Gui.class,gui,"armyTab",3);frame("small-appearance",true);
            mc.displayWidth=1280;mc.displayHeight=720;
            for(Faction f:Faction.values())models(f);
            for(Faction f:Faction.values())customModels(f);
            equipment();
            combatFeedback();
            combatHud();
            // Fixed bindings must not let Minecraft's default Q/drop swallow field healing.
            game=new BattlefrontGame(progress,Scenario.ENDOR,0,Mode.CONQUEST,1,0,7);set(Battlefront3Gui.class,gui,"game",game);game.health=40;
            call("binding",new Class[]{int.class},org.lwjgl.input.Keyboard.KEY_Q);if(game.health<=40)throw new AssertionError("Q failed to heal");
            game.ammo=3;call("binding",new Class[]{int.class},org.lwjgl.input.Keyboard.KEY_R);if(game.reload<=0)throw new AssertionError("R failed to reload");
            set(Battlefront3Gui.class,gui,"hub",true);game.x=12.5;game.health=77;gui.initGui();if(game.x!=12.5||game.health!=77)throw new AssertionError("Resize reset game");
            mc.gameSettings.fboEnable=false;frame("framebuffer-fallback",false);mc.gameSettings.fboEnable=true;
            long credits=progress.credits();gui.onGuiClosed();if(BattlefrontProgress.load(mc.mcDataDir.toPath().resolve("campaign.json")).credits()!=credits)throw new AssertionError("Close did not save");
            System.out.println("Battlefront 3 render, controls, purchases, resize and save checks passed: "+GL11.glGetString(GL11.GL_RENDERER));
        }finally{if(renderer!=null)renderer.close();buffer.destroy();}
    }
    private static void click(int x,int y)throws Exception{float scale=Math.min(gui.width/960f,gui.height/540f);float ox=(gui.width-960*scale)/2,oy=(gui.height-540*scale)/2;call("mouseClicked",new Class[]{int.class,int.class,int.class},Math.round(x*scale+ox),Math.round(y*scale+oy),0);}
    private static void basis(){
        gui.width=mc.displayWidth/2;gui.height=mc.displayHeight/2;GL11.glViewport(0,0,mc.displayWidth,mc.displayHeight);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,gui.width,gui.height,0,1000,3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glTranslated(0,0,-2000);
        dev.vibe.ui.GuiRenderState.prepare(false);GlStateManager.color(1,1,1,1);
    }
    private static void frame(String name,boolean hub)throws Exception{
        basis();set(Battlefront3Gui.class,gui,"hub",hub);FloatBuffer before=BufferUtils.createFloatBuffer(16),after=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,before);
        int tex=GL11.glGenTextures();GlStateManager.bindTexture(tex);renderer.render(game,game.aiming,hub);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,after);for(int i=0;i<16;i++)if(before.get(i)!=after.get(i))throw new AssertionError("Projection leaked");
        if(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=tex)throw new AssertionError("Texture leaked");GlStateManager.deleteTexture(tex);
        gui.drawInterface(0,0);snapshot(name);
    }
    private static void models(Faction f)throws Exception{
        basis();GL11.glClearColor(.025f,.055f,.08f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        for(int i=0;i<5;i++){renderer.preview(f,Role.values()[i],65+i*127,270,95,-23);mc.fontRendererObj.drawString(Role.values()[i].unit(f),7+i*127,300,0xFFB1DCE5);}
        mc.fontRendererObj.drawString(f.title,15,30,0xFFE0F2F7);snapshot("models-"+f.name().toLowerCase());
    }
    private static void equipment()throws Exception{
        basis();GL11.glClearColor(.025f,.055f,.08f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        for(int i=0;i<Weapon.values().length;i++){Weapon w=Weapon.values()[i];int x=90+i%4*155,y=85+i/4*165;renderer.equipmentPreview(Faction.REPUBLIC,Armor.FIELD,w,x,y,75,-15,true,3,0);mc.fontRendererObj.drawString(w.title,x-70,y+55,0xFFB9DEDF);}snapshot("weapons-models");
        basis();GL11.glClearColor(.025f,.055f,.08f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        for(int i=0;i<Armor.values().length;i++){Armor a=Armor.values()[i];renderer.equipmentPreview(Faction.REPUBLIC,a,Weapon.E11,52+i*106,283,91,-23,false,0,2);mc.fontRendererObj.drawString(a.title,5+i*106,306,0xFFB9DEDF);}snapshot("armor-models");
    }
    private static void customModels(Faction f)throws Exception{
        basis();GL11.glClearColor(.025f,.055f,.08f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        for(int i=0;i<5;i++){Role r=Role.values()[i];progress.customize(r,Cosmetic.PAINT,1+i);progress.customize(r,Cosmetic.MARKING,1+i%3);progress.customize(r,Cosmetic.HELMET,1+i%3);progress.customize(r,Cosmetic.PACK,1+i%3);progress.customize(r,Cosmetic.SHOULDER,1+i%2);progress.customize(r,Cosmetic.WEAR,i%3);
            renderer.customizedPreview(f,r,progress,false,65+i*127,270,91,-23);mc.fontRendererObj.drawString(r.unit(f),7+i*127,300,0xFFB1DCE5);}
        mc.fontRendererObj.drawString(f.title+" / Customized battalion",15,30,0xFFE0F2F7);snapshot("custom-models-"+f.name().toLowerCase());
    }
    private static void combatFeedback()throws Exception{
        basis();GL11.glClearColor(.06f,.07f,.08f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);Gui.drawRect(0,181,640,360,0xFFBFAF92);
        BattlefrontGame demo=new BattlefrontGame(progress,Scenario.GEONOSIS,0,Mode.CONQUEST,1,0,42);Weapon previous=progress.weapon();progress.purchase(Weapon.DC15A);
        String[] titles={"Hip fire","Aim","Body hit","Headshot","Elimination","Reload"};
        for(int row=0;row<2;row++)for(int i=0;i<6;i++){
            demo.aiming=i==1;demo.recoil=0;demo.hitMarker=i>=2&&i<=4?.16:0;demo.lastHitHead=i==3;demo.lastHitKill=i==4;demo.reload=i==5?1:0;demo.reloadDuration=2;
            int x=54+i*106,y=87+row*179;dev.vibe.ui.BattlefrontReticle.draw(demo,x,y);mc.fontRendererObj.drawString(titles[i],x-titles[i].length()*3,y+48,row==0?0xFFE1E8EA:0xFF283238);
        }snapshot("combat-feedback");
        basis();GL11.glClearColor(.06f,.07f,.08f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);demo.hitMarker=0;demo.reload=0;demo.aiming=false;
        progress.purchase(Weapon.BOWCASTER);dev.vibe.ui.BattlefrontReticle.draw(demo,190,165);mc.fontRendererObj.drawString("Bowcaster spread",145,212,0xFFE1E8EA);
        progress.purchase(Weapon.DLT19X);demo.aiming=true;dev.vibe.ui.BattlefrontReticle.draw(demo,450,165);mc.fontRendererObj.drawString("Marksman aim",414,212,0xFFE1E8EA);snapshot("weapon-reticles");progress.purchase(previous);
        basis();GL11.glClearColor(.045f,.05f,.058f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);dev.vibe.ui.BattlefrontIcons icons=new dev.vibe.ui.BattlefrontIcons();
        try{String[] names={"operations","army","arsenal","record","manual","planet","forest","shield","tactics","appearance","weapon","helmet","upgrade","deploy","credits","arrow","exit","check","plus","minus","support","scout","pack","settings"};
            for(int i=0;i<names.length;i++){int x=48+i%6*106,y=22+i/6*87;icons.draw(names[i],x,y,30,0xFFFFA1C9);mc.fontRendererObj.drawString(names[i],x-9,y+43,0xFFB0ACA9);}snapshot("svg-icon-sheet");
        }finally{icons.close();}
    }
    private static void combatHud()throws Exception{
        progress.purchase(Weapon.DC15A);progress.purchase(Armor.FIELD);
        for(Scenario scenario:Scenario.values()){
            game=new BattlefrontGame(progress,scenario,0,Mode.CONQUEST,1,0,8);set(Battlefront3Gui.class,gui,"game",game);
            game.x=game.posts[0].x;game.z=game.posts[0].z+12;game.y=game.world.height(game.x,game.z);game.yaw=0;game.protection=0;game.soldiers.clear();
            for(int i=0;i<12;i++){BattlefrontGame.Soldier s=new BattlefrontGame.Soldier(i%3==0?0:1,Role.ASSAULT,i);s.x=game.x+(i%4-1.5)*6;s.z=game.z-16-i/4*9;s.y=game.world.height(s.x,s.z);s.health=s.maxHealth=100;game.soldiers.add(s);}
            game.damage(game.soldiers.get(1),35,true);game.hurt(16,game.x,game.z-20);game.hurt(12,game.x+15,game.z);game.earned=175;game.kills=3;
            frame(scenario.name().toLowerCase()+"-combat-hud",false);
            call("binding",new Class[]{int.class},org.lwjgl.input.Keyboard.KEY_Z);frame(scenario.name().toLowerCase()+"-radar-wide",false);
            call("binding",new Class[]{int.class},org.lwjgl.input.Keyboard.KEY_Z);frame(scenario.name().toLowerCase()+"-radar-near",false);
            call("binding",new Class[]{int.class},org.lwjgl.input.Keyboard.KEY_Z);
            game.damageDirections.clear();game.damageNumbers.clear();game.hurtFlash=0;game.ammo=2;game.startReload();
            for(int i=0;i<5;i++){game.reload=game.reloadDuration*(1-(i*.19+.05));frame(scenario.name().toLowerCase()+"-reload-"+i,false);}
            game.reload=0;game.ammo=game.magazine();game.aiming=true;frame(scenario.name().toLowerCase()+"-aim-hud",false);game.aiming=false;
        }
        mc.displayWidth=640;mc.displayHeight=480;frame("small-combat-hud",false);
        mc.displayWidth=1280;mc.displayHeight=540;frame("wide-combat-hud",false);
        mc.displayHeight=720;
        progress.award(30000,0);
        for(Weapon weapon:Weapon.values()){
            if(!progress.purchase(weapon)||progress.weapon()!=weapon)throw new AssertionError("Reload preview failed to equip "+weapon);
            basis();GL11.glClearColor(.035f,.045f,.06f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            double[] stages={0,.16,.42,.68,.87};String[] labels={"Ready","Raise & tilt","Remove cell","Seat cell","Return to aim"};
            for(int i=0;i<stages.length;i++){renderer.customizedPreview(weapon==Weapon.ION?Faction.SEPARATISTS:Faction.REPUBLIC,Role.ASSAULT,progress,true,78+i*126,278,86,-18,stages[i]);mc.fontRendererObj.drawString(labels[i],13+i*126,311,0xFFCDD8D5);}
            mc.fontRendererObj.drawString(weapon.title+" / Reload sequence",17,26,0xFFE6EADC);snapshot("reload-model-"+weapon.name().toLowerCase());
        }
        progress.purchase(Weapon.DC15A);
    }
    private static void snapshot(String name)throws Exception{
        GL11.glFinish();int error=GL11.glGetError();if(error!=0)throw new AssertionError(name+" GL error "+error);
        ByteBuffer pixels=BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);GL11.glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(mc.displayWidth,mc.displayHeight,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<mc.displayHeight;y++)for(int x=0;x<mc.displayWidth;x++){int i=(y*mc.displayWidth+x)*4;image.setRGB(x,mc.displayHeight-1-y,((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));}
        ImageIO.write(image,"png",root.resolve(name+".png").toFile());System.out.println("Captured "+name);
    }
}
