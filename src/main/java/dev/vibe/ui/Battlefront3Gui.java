package dev.vibe.ui;

import dev.vibe.game.battlefront.*;
import dev.vibe.module.impl.Battlefront3Module;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import static dev.vibe.game.battlefront.BattlefrontContent.*;

/** A scalable command deck and third-person battlefield, isolated from Minecraft input. */
public final class Battlefront3Gui extends GuiScreen {
    private static final int WHITE=0xFFE9F3F8,MUTED=0xFF94AAB9,CYAN=0xFF7BDCEF,GOLD=0xFFFFD08C,RED=0xFFFF8390;
    private static final int ROSE=0xFFFFA1C9,MENU_TEXT=0xFFF0F0EB,MENU_MUTED=0xFF9B9C96;
    private final BattlefrontIcons icons=new BattlefrontIcons();
    private final BattlefrontCombatHud combatHud=new BattlefrontCombatHud();
    private final Battlefront3Module module;
    private BattlefrontProgress progress;private BattlefrontGame game,previewGame;private BattlefrontRenderer renderer;
    private Scenario scenario=Scenario.GEONOSIS;private int side,tier=1,kit,page,armoryTab;private Mode mode=Mode.CONQUEST;
    private Weapon selectedWeapon=Weapon.DC15A;private Armor selectedArmor=Armor.FIELD;
    private int armyTab,appearanceTarget;private Role selectedRole=Role.ASSAULT;private boolean atlas;
    private boolean hub=true,paused,captured,attackReleased,help,closed;
    private long lastFrame;private double uiTime,ambientDelay;private String feedback="";private double feedbackUntil;
    private float scale=1,offsetX,offsetY;private int mouseX,mouseY;private final List<Action> actions=new ArrayList<Action>();
    private static final class Action {int x,y,w,h;Runnable run;Action(int x,int y,int w,int h,Runnable r){this.x=x;this.y=y;this.w=w;this.h=h;run=r;}boolean contains(int mx,int my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}}
    public Battlefront3Gui(Battlefront3Module module){this.module=module;}
    @Override public void initGui(){
        if(progress==null){progress=BattlefrontProgress.load(mc.mcDataDir.toPath().resolve("vibe/battlefront3-progress.json"));selectedWeapon=progress.weapon();selectedArmor=progress.armor();renderer=new BattlefrontRenderer(mc);makePreview();}
        lastFrame=System.nanoTime();capture(!hub&&!paused&&!game.finished&&Display.isActive());
    }
    private void makePreview(){previewGame=new BattlefrontGame(progress,scenario,side,mode,tier,kit,77);}
    private void capture(boolean value){if(!Mouse.isCreated())return;if(value!=captured||Mouse.isGrabbed()!=value){Mouse.setGrabbed(value);Mouse.getDX();Mouse.getDY();captured=value;attackReleased=false;}}
    private static boolean down(KeyBinding b){int k=b.getKeyCode();return k<0?Mouse.isCreated()&&k+100>=0&&k+100<Mouse.getButtonCount()&&Mouse.isButtonDown(k+100):Keyboard.isCreated()&&k>0&&k<Keyboard.KEYBOARD_SIZE&&Keyboard.isKeyDown(k);}
    private static String key(KeyBinding b){return GameSettings.getKeyDisplayString(b.getKeyCode());}
    @Override public void drawScreen(int mx,int my,float partial){
        long now=System.nanoTime();double dt=Math.min(.1,Math.max(0,(now-lastFrame)/1e9));lastFrame=now;uiTime+=dt;
        boolean aim=false;
        if(!hub&&!game.finished){
            if(!Display.isActive()){paused=true;capture(false);}
            if(!paused&&captured){
                BattlefrontGame.Input input=new BattlefrontGame.Input();float s=mc.gameSettings.mouseSensitivity*.6f+.2f;double look=s*s*s*8*.15;
                game.yaw=(game.yaw+Mouse.getDX()*look)%360;game.pitch=Math.max(-85,Math.min(85,game.pitch-Mouse.getDY()*look*(mc.gameSettings.invertMouse?-1:1)));
                input.forward=down(mc.gameSettings.keyBindForward);input.back=down(mc.gameSettings.keyBindBack);input.left=down(mc.gameSettings.keyBindLeft);input.right=down(mc.gameSettings.keyBindRight);
                input.sprint=down(mc.gameSettings.keyBindSprint);input.jump=down(mc.gameSettings.keyBindJump);input.aim=down(mc.gameSettings.keyBindUseItem);aim=input.aim;
                if(!down(mc.gameSettings.keyBindAttack))attackReleased=true;input.attack=attackReleased&&down(mc.gameSettings.keyBindAttack);
                int before=game.ammo,oldKills=game.kills,shots=game.shots;game.advance(dt,input);ambientDelay-=dt;
                if(game.ammo<before)sound("fire",game.kit==2?.72f:1.1f);
                else if(game.shots>shots&&ambientDelay<=0){sound("distant",.8f);ambientDelay=.25;}
                if(game.kills>oldKills)sound("confirm",1);
            }
            if(game.finished)capture(false);
        }
        if(!hub)renderer.render(game,aim,false);drawInterface(mx,my);
    }
    private void sound(String name,float pitch){if(mc.getSoundHandler()!=null)mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(new net.minecraft.util.ResourceLocation("vibe:battlefront."+name),pitch));}
    /** Separate UI pass also used by the offscreen integration check. */
    public void drawInterface(int mx,int my){
        actions.clear();scale=Math.min(width/960f,height/540f);offsetX=(width-960*scale)/2;offsetY=(height-540*scale)/2;
        mouseX=(int)((mx-offsetX)/scale);mouseY=(int)((my-offsetY)/scale);
        GlStateManager.disableDepth();GlStateManager.depthMask(false);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        if(hub)rect(0,0,width,height,0xFF0C0D0F);
        GlStateManager.pushMatrix();GlStateManager.translate(offsetX,offsetY,0);GlStateManager.scale(scale,scale,1);
        if(hub)hub();else{hud();if(game.finished)debrief();else if(atlas)tacticalMap();else if(paused)pause();}
        if(progress.error()!=null){rect(12,518,948,538,0xEE221924);label(progress.error(),22,524,.85f,RED);}
        GlStateManager.popMatrix();GlStateManager.depthMask(true);GlStateManager.enableDepth();
    }
    private void hub(){
        rect(0,0,960,540,0xFF0C0D0F);rect(0,0,960,73,0xFF101113);rect(0,72,960,73,0xFF2D2C2D);
        tile("arsenal",28,21,34,ROSE);label("Battlefront 3",76,23,1.95f,WHITE);label("S T A R  W A R S  /  C O M M A N D  D E C K",77,51,.72f,MUTED);
        card(665,23,112,32,0xFF191A1B,0xFF333334);icons.draw("record",676,30,17,ROSE);label("RANK "+String.format(Locale.ROOT,"%02d",progress.rank()),703,35,.9f,WHITE);
        card(789,23,143,32,0xFF241C21,0xFF654250);icons.draw("credits",801,30,18,ROSE);label(format(progress.credits())+" CR",830,34,1.05f,ROSE);
        rect(191,92,192,496,0xFF252628);label("COMMAND",30,96,.76f,MUTED);
        String[] tabs={"Operations","Your army","Armory","Service record","Field manual"},glyphs={"operations","army","arsenal","record","manual"};
        String[] sub={"Choose a theater","Build your battalion","Equipment & research","Campaign progress","Controls & intel"};
        for(int i=0;i<tabs.length;i++){final int tab=i;int y=116+i*52;boolean active=page==i,hover=over(23,y,153,44);
            card(23,y,153,44,active?0xFF292027:hover?0xFF202122:0xFF121315,active?0xFF65424F:0xFF242528);
            icons.draw(glyphs[i],34,y+12,20,active?ROSE:MENU_MUTED);label(tabs[i],65,y+11,.98f,active?WHITE:MUTED);label(sub[i],65,y+29,.70f,MUTED);
            actions.add(new Action(23,y,153,44,()->{page=tab;feedback="";}));}
        card(23,394,153,52,0xFF171819,0xFF2D2E2E);label(progress.wins()+" victories",36,409,.94f,WHITE);label(progress.deployed()+" / "+progress.capacity()+" army slots",36,430,.81f,MUTED);
        button(23,463,153,32,"Exit to Minecraft",false,MUTED,()->exit());
        if(page==0)operations();else if(page==1)army();else if(page==2)armory();else if(page==3)record();else manual();
        rect(23,505,932,506,0xFF262729);label("LOCAL CAMPAIGN",28,521,.73f,MUTED);
        label(uiTime<feedbackUntil?feedback:"Your campaign saves automatically.",211,521,.83f,uiTime<feedbackUntil?ROSE:MUTED);
    }
    private void operations(){
        section("Choose your battlefield.",211,97);label("01 / THEATER     02 / ALLEGIANCE     03 / OPERATION",211,120,.76f,MUTED);
        for(int i=0;i<2;i++){final Scenario s=Scenario.values()[i];int x=211+i*365;boolean selected=scenario==s;
            card(x,137,350,108,selected?0xFF242021:0xFF1B1C1C,selected?0xFF805668:0xFF343534);
            tile(i==0?"planet":"forest",x+278,155,48,selected?ROSE:MENU_MUTED);
            label(s.era,x+16,151,.74f,MUTED);label(i==0?"Geonosis":"Endor",x+16,174,2.05f,WHITE);
            label("448m theater / "+(i==0?"12":"14")+" enterable locations",x+16,205,.85f,MUTED);
            icons.draw(selected?"check":"arrow",x+16,224,13,selected?ROSE:MENU_MUTED);label(selected?"Ready to deploy":"Explore this theater",x+37,228,.76f,selected?ROSE:MUTED);
            actions.add(new Action(x,137,350,108,()->{scenario=s;makePreview();}));
        }
        label("02 / ALLEGIANCE",211,259,.8f,MUTED);
        for(int i=0;i<2;i++){final int team=i;Faction f=scenario.faction(i);int x=211+i*365;
            button(x,277,350,38,f.title.toUpperCase(Locale.ROOT),side==i,f.color,()->{side=team;makePreview();});}
        label("03 / OPERATION",211,330,.8f,MUTED);
        for(int i=0;i<3;i++){final Mode selected=Mode.values()[i];button(211+i*243,348,230,30,selected.title.toUpperCase(Locale.ROOT),mode==selected,CYAN,()->{mode=selected;makePreview();});}
        label(mode.description,211,388,.91f,MUTED);
        label("THREAT TIER  "+tier+" / "+progress.maxTier(),211,417,.9f,CYAN);
        button(367,410,27,25,"-",false,MUTED,()->{tier=Math.max(1,tier-1);});button(399,410,27,25,"+",false,MUTED,()->{tier=Math.min(progress.maxTier(),tier+1);});
        String[] kits={"ASSAULT","HEAVY","MARKSMAN"};button(447,410,214,25,"LOADOUT  /  "+kits[kit],false,CYAN,()->{kit=(kit+1)%3;});
        label(progress.weapon().title+" / "+progress.armor().title,211,451,.9f,CYAN);
        label("Equipment can be purchased and upgraded in the Armory.",211,472,.82f,MUTED);
        button(679,440,247,52,"Deploy to battle",true,scenario.color,()->deploy());
    }
    private void army(){
        section("BUILD YOUR BATTALION",211,99);label(scenario.faction(side).title+"  /  "+progress.deployed()+" of "+progress.capacity()+" berths assigned",211,122,1,MUTED);
        String[] tabs={"COMPOSITION","TACTICS","UNIT EQUIPMENT","APPEARANCE"};for(int i=0;i<4;i++){final int tab=i;button(211+i*181,143,172,27,tabs[i],armyTab==i,CYAN,()->armyTab=tab);}
        if(armyTab==1){tactics();return;}if(armyTab==2){unitEquipment();return;}if(armyTab==3){appearance();return;}
        String[] desc={"Versatile frontline infantry","Armor and sustained firepower","Heals nearby troops and you","Fast flanks; precision weapons","Captures posts at twice the rate"};
        for(int i=0;i<5;i++){final Role role=Role.values()[i];int y=185+i*56;panel(211,y,465,49,0xFF2C4957);
            icons.draw(roleIcon(role),224,y+12,23,ROSE);label(role.unit(scenario.faction(side)),261,y+10,1.05f,WHITE);label(desc[i],261,y+32,.82f,MUTED);
            button(551,y+15,28,27,"-",false,MUTED,()->{if(!progress.adjust(role,-1))feedback("Keep at least one soldier in your army.");});
            label(Integer.toString(progress.squad(role)),593,y+24,1,CYAN);
            button(631,y+15,28,27,"+",false,CYAN,()->{if(!progress.adjust(role,1))feedback("Transport full. Upgrade capacity in the Armory.");});}
        panel(696,185,230,273,0xFF315466);label("UNIT HOLOGRAM",714,199,.9f,CYAN);
        Role preview=Role.values()[(int)(uiTime/7)%5];renderer.customizedPreview(scenario.faction(side),preview,progress,false,809,403,83,uiTime*12);
        label(preview.title.toUpperCase(Locale.ROOT),714,424,1,WHITE);label("All units replenish during battle",710,445,.78f,MUTED);
        label("TAB cycles orders: advance / follow / hold position. Support heals within 7m.",211,486,.92f,MUTED);
    }
    private void tactics(){
        for(int i=0;i<Doctrine.values().length;i++){final Doctrine d=Doctrine.values()[i];int x=211+i%2*365,y=184+i/2*91;
            panel(x,y,350,79,progress.doctrine()==d?CYAN:0xFF304D59);icons.draw(new String[]{"army","arsenal","shield","scout"}[i],x+14,y+10,21,ROSE);label(d.title,x+47,y+13,1.15f,WHITE);label(d.description,x+14,y+39,.82f,MUTED);
            label(progress.doctrine()==d?"ACTIVE DOCTRINE":"SELECT DOCTRINE / FREE",x+14,y+60,.77f,CYAN);actions.add(new Action(x,y,350,79,()->{progress.doctrine(d);feedback("Doctrine applies to all allied roles on deployment.");}));}
        section("FORMATION / FOLLOW & HOLD ORDERS",211,383);
        for(int i=0;i<Formation.values().length;i++){final Formation f=Formation.values()[i];button(211+i*181,410,172,33,f.title,progress.formation()==f,CYAN,()->progress.formation(f));}
        label(progress.formation().description,211,462,.98f,MUTED);
        label("Open the tactical map with M to mark a location and rally your army there.",211,486,.91f,GOLD);
    }
    private void unitEquipment(){
        for(int i=0;i<5;i++){final Role r=Role.values()[i];button(211+i*145,183,135,28,r.title,selectedRole==r,CYAN,()->selectedRole=r);}
        Role r=selectedRole;panel(211,225,423,257,0xFF315466);label(r.unit(scenario.faction(side)),227,240,1.3f,WHITE);
        label((int)progress.armyHealth(r)+" HP  /  "+String.format(Locale.ROOT,"%.1f",progress.armyDamage(r))+" DMG  /  "+String.format(Locale.ROOT,"%.1f",progress.armySpeed(r))+"m/s",227,267,.91f,CYAN);
        Weapon w=progress.armyWeapon(r);button(227,289,390,33,"WEAPON / "+(w==null?"Role service weapon":w.title)+"  >",false,CYAN,()->cycleArmyWeapon());
        button(227,331,390,33,"ARMOR / "+progress.armyArmor(r).title+"  >",false,CYAN,()->cycleArmyArmor());
        label("Cycle owned equipment; unlock new items in the Armory.",227,377,.84f,MUTED);
        int level=progress.veteran(r);button(227,399,390,34,"VETERAN "+level+" / 5  -  "+(level==5?"FULLY TRAINED":progress.trainingCost(r)+" CR TO TRAIN"),false,GOLD,()->feedback(progress.train(selectedRole)?"Role trained. All soldiers of this role benefit.":"Training needs credits; maximum level is five."));
        label("Each level: +5% base health / +4% base damage",227,448,.86f,MUTED);label("Support training also improves nearby healing by 15%.",227,466,.82f,MUTED);
        panel(650,225,276,257,0xFF315466);renderer.customizedPreview(scenario.faction(side),r,progress,false,789,448,98,-25+uiTime*9);
        label("DEPLOYED EQUIPMENT",665,240,.88f,CYAN);label(progress.doctrine().title,665,465,.88f,MUTED);
        label("Weapons use balanced squad fire rates. Army armor trades speed for protection.",211,495,.84f,MUTED);
    }
    private void cycleArmyWeapon(){int start=progress.armyWeapon(selectedRole)==null?-1:progress.armyWeapon(selectedRole).ordinal();for(int i=1;i<=Weapon.values().length+1;i++){int next=(start+1+i)%(Weapon.values().length+1)-1;Weapon w=next<0?null:Weapon.values()[next];if(w==null||progress.owns(w)){progress.equipArmy(selectedRole,w);return;}}}
    private void cycleArmyArmor(){int start=progress.armyArmor(selectedRole).ordinal();for(int i=1;i<=Armor.values().length;i++){Armor a=Armor.values()[(start+i)%Armor.values().length];if(progress.owns(a)){progress.equipArmy(selectedRole,a);return;}}}
    private void appearance(){
        final Role r=appearanceTarget==5?null:Role.values()[appearanceTarget];
        button(211,183,715,29,"EDIT / "+(r==null?"YOUR CHARACTER":r.unit(scenario.faction(side)).toUpperCase(Locale.ROOT))+"    >",true,CYAN,()->appearanceTarget=(appearanceTarget+1)%6);
        panel(211,225,423,257,0xFF315466);
        for(int i=0;i<Cosmetic.values().length;i++){final Cosmetic c=Cosmetic.values()[i];int value=progress.cosmetic(r,c);button(227,238+i*39,390,32,c.title+" / "+c.choices[value]+"  >",false,c==Cosmetic.PAINT?0xFF000000|progress.appearance(r).color(scenario.faction(side)):CYAN,()->progress.customize(r,c,(progress.cosmetic(r,c)+1)%c.choices.length));}
        panel(650,225,276,225,0xFF315466);label("LIVE MODEL PREVIEW",665,239,.88f,CYAN);
        renderer.customizedPreview(scenario.faction(side),r==null?Role.ASSAULT:r,progress,r==null,789,432,83,uiTime*15-25);
        button(650,461,276,31,"COPY LOOK TO ALL ARMY ROLES",false,GOLD,()->feedback(progress.copyAppearanceToArmy(r)?"Appearance copied to all five army roles.":"Campaign is read-only."));
        label("Cosmetics are free and persist separately for each role and your character.",211,496,.86f,MUTED);
    }
    private void armory(){
        section("ARSENAL & RESEARCH",211,99);label("Permanent equipment, individual modifications and army research.",211,122,.95f,MUTED);
        String[] tabs={"ARMY & CHARACTER","WEAPONS","ARMOR"};for(int i=0;i<3;i++){final int tab=i;button(211+i*243,143,230,26,tabs[i],armoryTab==i,CYAN,()->armoryTab=tab);}
        if(armoryTab!=0){equipment();return;}
        for(int i=0;i<Upgrade.values().length;i++){final Upgrade u=Upgrade.values()[i];int x=211+i%3*243,y=182+i/3*75,l=progress.level(u);boolean max=l>=BattlefrontProgress.MAX_LEVEL,can=!max&&progress.credits()>=progress.cost(u);
            panel(x,y,230,68,can?0xFF386679:0xFF2A414F);icons.draw(new String[]{"weapon","shield","settings","deploy","operations","support","weapon","shield","army","pack","record","credits"}[i],x+11,y+8,17,can?ROSE:MENU_MUTED);label(u.title,x+37,y+10,.96f,WHITE);label(u.description,x+11,y+30,.83f,MUTED);
            label("LV "+l+" / 12",x+11,y+49,.8f,CYAN);label(max?"MAXED":format(progress.cost(u))+" CR",x+135,y+48,.9f,can?GOLD:MUTED);
            actions.add(new Action(x,y,230,72,()->{if(progress.purchase(u)){feedback(u.title+" upgraded.");sound("confirm",1);}else feedback(max?"This upgrade is fully researched.":"Earn more credits in battle to purchase this upgrade.");}));}
        label("Credits: eliminations + captures + field contracts + operation rewards",211,490,.9f,CYAN);
    }
    private void equipment(){
        boolean weapons=armoryTab==1;int count=weapons?Weapon.values().length:Armor.values().length;
        for(int i=0;i<count;i++){
            final int index=i;int y=183+i*(weapons?38:49);String title=weapons?Weapon.values()[i].title:Armor.values()[i].title;
            boolean owned=weapons?progress.owns(Weapon.values()[i]):progress.owns(Armor.values()[i]);boolean equipped=weapons?progress.weapon().ordinal()==i:progress.armor().ordinal()==i;
            int cost=weapons?Weapon.values()[i].cost:Armor.values()[i].cost;
            boolean selected=weapons?selectedWeapon.ordinal()==i:selectedArmor.ordinal()==i;
            panel(211,y,423,weapons?33:43,selected?CYAN:0xFF294A59);icons.draw(weapons?"weapon":"helmet",223,y+7,20,selected?ROSE:MENU_MUTED);label(title,255,y+7,1,selected?WHITE:MUTED);
            label(equipped?"EQUIPPED":owned?"OWNED":format(cost)+" CR",528,y+9,.78f,equipped?CYAN:owned?MUTED:GOLD);
            if(!weapons)label(Armor.values()[i].description,255,y+26,.76f,MUTED);
            actions.add(new Action(211,y,423,weapons?33:43,()->{if(weapons)selectedWeapon=Weapon.values()[index];else selectedArmor=Armor.values()[index];}));
        }
        panel(650,183,276,142,0xFF395D6C);
        renderer.equipmentPreview(scenario.faction(side),weapons?progress.armor():selectedArmor,weapons?selectedWeapon:progress.weapon(),weapons?795:789,weapons?255:315,weapons?108:58,-22+Math.sin(uiTime*.35)*12,weapons,
                progress.level(weapons?selectedWeapon:progress.weapon(),WeaponMod.POWER),progress.level(weapons?progress.armor():selectedArmor,ArmorMod.PLATING));
        label(weapons?selectedWeapon.title:selectedArmor.title,663,191,1.05f,WHITE);
        String stats=weapons?(int)progress.weaponDamage(selectedWeapon)+" DMG / "+progress.weaponMagazine(selectedWeapon)+" CELL / "+(int)selectedWeapon.range+"m":(int)(progress.maxHealth()+selectedArmor.health+12*progress.level(selectedArmor,ArmorMod.VITALS))+" HP / "+(int)(100*(selectedArmor.resistance+.02*progress.level(selectedArmor,ArmorMod.PLATING)))+"% RESIST";
        label(stats,663,310,.82f,CYAN);
        label(weapons?selectedWeapon.description:selectedArmor.description,651,335,.82f,MUTED);
        for(int i=0;i<3;i++){
            final int mod=i;int level=weapons?progress.level(selectedWeapon,WeaponMod.values()[i]):progress.level(selectedArmor,ArmorMod.values()[i]);
            int cost=weapons?progress.cost(selectedWeapon,WeaponMod.values()[i]):progress.cost(selectedArmor,ArmorMod.values()[i]);
            String title=weapons?WeaponMod.values()[i].title:ArmorMod.values()[i].title;
            button(650,356+i*34,276,29,title+" "+level+"/8   "+(level==8?"MAX":cost+" CR"),false,level==8?MUTED:CYAN,()->{
                boolean ok=weapons?progress.purchase(selectedWeapon,WeaponMod.values()[mod]):progress.purchase(selectedArmor,ArmorMod.values()[mod]);
                feedback(ok?"Equipment modification installed.":"Own this equipment and meet the credit cost. Maximum level: 8.");
            });
        }
        boolean owned=weapons?progress.owns(selectedWeapon):progress.owns(selectedArmor);int cost=weapons?selectedWeapon.cost:selectedArmor.cost;
        button(650,463,276,32,owned?"EQUIP SELECTED": "PURCHASE & EQUIP / "+cost+" CR",true,GOLD,()->{
            boolean ok=weapons?progress.purchase(selectedWeapon):progress.purchase(selectedArmor);feedback(ok?"Equipment ready for your next deployment.":"Insufficient credits for this equipment.");
        });
        label(weapons?"Power: +9% damage / Cell: +3 rounds / Cycling: +5% speed":"Plating: +2% resistance / Support: +12 HP / Servos: +2% speed",211,491,.77f,MUTED);
    }
    private void record(){
        section("YOUR SERVICE RECORD",211,99);String[] stats={"OPERATIONS","VICTORIES","ELIMINATIONS","REPUTATION"};int[] values={progress.battles(),progress.wins(),progress.kills(),progress.xp()};
        for(int i=0;i<4;i++){int x=211+i*183;panel(x,136,169,75,0xFF315264);icons.draw(new String[]{"operations","record","arsenal","upgrade"}[i],x+133,150,21,ROSE);label(stats[i],x+12,149,.8f,MUTED);label(format(values[i]),x+12,174,2,CYAN);}
        section("FACTION CAMPAIGNS",211,235);
        for(int i=0;i<4;i++){Faction f=Faction.values()[i];int y=262+i*37;panel(211,y,715,31,0xFF2C414D);label(f.title,226,y+11,1,f.color);label(progress.wins(f)+" victories",799,y+11,.95f,WHITE);}
        section("FIELD CONTRACTS / EVERY OPERATION",211,431);
        label("10 personal eliminations: +180 CR  /  3 captures: +220 CR  /  35 army kills: +250 CR",211,455,.95f,GOLD);
        label("Threat tiers raise enemy strength and payouts. All 8 tiers unlock through victories.",211,482,.92f,MUTED);
    }
    private void manual(){
        section("FIELD MANUAL",211,99);String[][] rows={
                {key(mc.gameSettings.keyBindForward)+" / "+key(mc.gameSettings.keyBindLeft)+" / "+key(mc.gameSettings.keyBindBack)+" / "+key(mc.gameSettings.keyBindRight),"Move. Mouse looks; hold "+key(mc.gameSettings.keyBindSprint)+" to sprint."},
                {key(mc.gameSettings.keyBindAttack)+" / "+key(mc.gameSettings.keyBindUseItem),"Fire / aim. Aim for the head to deal extra damage."},
                {"R / "+key(mc.gameSettings.keyBindJump),"Reload / jump. Beskar flight rig: hold jump in the air to fly."},
                {"G / Q / V","Ion detonator / bacta heal / orbital strike (6 kills required)."},
                {"TAB / M / ESC / F1","Army orders / tactical atlas / pause / help. Z cycles radar range."},
                {"COMMAND POSTS","Stand inside an 8m ring to capture. Enemy presence contests it."},
                {"REINFORCEMENTS","Deaths cost tickets. Hold posts to drain enemy reserves."},
                {"E / INTERIORS","Enter all buildings; search glowing supply caches for 85 credits."},
                {"THIRD-PERSON AIM","Aim over your shoulder. Walls retract the camera and block shots."}};
        for(int i=0;i<rows.length;i++){int y=139+i*36;label(rows[i][0],211,y,.91f,CYAN);label(rows[i][1],211,y+15,.91f,MUTED);}
        label("Each operation lasts up to 7 minutes. Escape pauses; retreat banks earned credits.",211,489,.9f,GOLD);
    }
    private void hud(){
        float boost=Math.min(1.2f,Math.max(1,960f/Math.max(1,mc.displayWidth)));
        int halfWidth=Math.round(width/scale/boost/2),halfHeight=Math.round(height/scale/boost/2);
        GlStateManager.pushMatrix();GlStateManager.translate(480,270,0);GlStateManager.scale(boost,boost,1);GlStateManager.translate(-480,-270,0);
        combatHud.draw(game,icons,480-halfWidth,270-halfHeight,480+halfWidth,270+halfHeight,!paused&&!game.finished);
        GlStateManager.popMatrix();
        if(help&&!paused){panel(230,120,500,114,0xFF33596B);label("Move / sprint / jump / fire / aim: your Minecraft bindings",245,133,.95f,WHITE);
            label("R reload  /  G ion detonator  /  Q bacta  /  V orbital support",245,154,.95f,MUTED);
            label("TAB army orders  /  M tactical atlas  /  Z minimap zoom",245,175,.95f,CYAN);
            label("E supply caches  /  ESC pause  /  F1 close help",245,196,.95f,MUTED);
            label("Contracts: 10 eliminations / 3 captures / 35 army kills / 5 caches",245,218,.84f,GOLD);}
    }
    private int gcolor(int s){return s==game.side?0xFF8BDDD8:0xFFFF8195;}
    private void dot(int x,int y,int size,double px,double pz,int c,int radius){int dx=mapX(x,size,px),dy=mapX(y,size,pz);rect(dx-radius,dy-radius,dx+radius+1,dy+radius+1,c);}
    private int mapX(int origin,int size,double value){return origin+size/2+(int)(value/(BattlefrontWorld.TERRAIN_EDGE*2)*(size-10));}
    private void tacticalMap(){
        rect(0,0,960,540,0xF006121E);section("TACTICAL ATLAS / "+game.scenario.title.toUpperCase(Locale.ROOT),24,29);label("OPERATION PAUSED / 448 x 448m",673,33,.89f,CYAN);
        panel(24,73,216,428,0xFF345768);label("BATTALION COMMAND",38,91,1.05f,WHITE);
        label("Click the map to place a marker.",38,122,.9f,MUTED);label("Rally sends troops to that location.",38,142,.87f,MUTED);
        label("NORTH IS UP",38,183,.83f,CYAN);label("A / B / C   Command posts",38,207,.88f,GOLD);label("Squares   Enterable buildings",38,231,.88f,0xFFB7D5B4);label("White   Your character",38,255,.88f,WHITE);label("Gold   Rally waypoint",38,279,.88f,GOLD);label("Triangles   Deployment bases",38,303,.86f,MUTED);
        label("RECON CONTRACT",38,331,.92f,CYAN);label("Supply caches: "+game.searchedCaches.size()+" / 5",38,355,.94f,WHITE);label(game.reconContract?"COMPLETE / +300 CR":"Search five for +300 CR",38,378,.87f,GOLD);
        label("Locations visited: "+game.discovered.size()+" / "+game.world.buildings.size(),38,420,.9f,MUTED);label(game.progress.formation().title,38,461,.91f,CYAN);
        final int mx=257,my=73,size=428;panel(mx,my,size,size,0xFF3E7780);
        double span=BattlefrontWorld.TERRAIN_EDGE*2;
        combatHud.terrain(game.world,mx+5,my+5,size-10);
        for(int i=1;i<8;i++){int offset=5+i*(size-10)/8;rect(mx+offset,my+5,mx+offset+1,my+size-5,0x202FB8C1);rect(mx+5,my+offset,mx+size-5,my+offset+1,0x202FB8C1);}
        for(BattlefrontArchitecture.Building b:game.world.buildings){int c=game.searchedCaches.contains(b.name)?0xFF657C75:0xFFB7D5B4;
            outline(mapX(mx,size,b.x-b.w),mapX(my,size,b.z-b.d),Math.max(4,(int)(b.w*2/span*(size-10))),Math.max(4,(int)(b.d*2/span*(size-10))),c);}
        for(int i=0;i<game.posts.length;i++){BattlefrontGame.Post p=game.posts[i];int c=p.owner<0?GOLD:gcolor(p.owner);dot(mx,my,size,p.x,p.z,c,5);label(""+(char)('A'+i),mapX(mx,size,p.x)+8,mapX(my,size,p.z)-3,.92f,c);}
        for(int i=0;i<2;i++){int bx=mapX(mx,size,game.world.bases[i][0]),bz=mapX(my,size,game.world.bases[i][1]);icons.draw("deploy",bx-8,bz-8,16,gcolor(i));label("HQ "+(i+1),bx+10,bz-3,.76f,gcolor(i));}
        for(BattlefrontGame.Soldier s:game.soldiers)if(s.alive())dot(mx,my,size,s.x,s.z,gcolor(s.side),2);
        dot(mx,my,size,game.x,game.z,WHITE,4);
        if(game.waypoint){int px=mapX(mx,size,game.waypointX),pz=mapX(my,size,game.waypointZ);outline(px-7,pz-7,15,15,GOLD);dot(mx,my,size,game.waypointX,game.waypointZ,GOLD,2);}
        actions.add(new Action(mx+5,my+5,size-10,size-10,()->{double wx=(mouseX-mx-size/2.0)*span/(size-10),wz=(mouseY-my-size/2.0)*span/(size-10);game.markWaypoint(wx,wz);}));
        label("LOCATIONS / SELECT TO MARK",702,86,.85f,CYAN);
        for(int i=0;i<game.world.buildings.size();i++){final BattlefrontArchitecture.Building b=game.world.buildings.get(i);int y=109+i*20;
            boolean searched=game.searchedCaches.contains(b.name);label((searched?"+ ":"  ")+b.name,702,y,.82f,searched?MUTED:WHITE);
            actions.add(new Action(700,y-3,236,19,()->game.markWaypoint(b.x,b.z+b.d+(b.floor>1?26:5))));}
        button(702,407,234,27,"CLEAR WAYPOINT",false,MUTED,()->game.waypoint=false);
        button(702,444,234,32,"RALLY ARMY TO MARKER",game.waypoint,GOLD,()->{game.rallyWaypoint();if(game.waypoint)closeAtlas();});
        button(702,486,234,28,"M / RETURN TO BATTLE",false,CYAN,()->closeAtlas());
    }
    private void closeAtlas(){atlas=false;paused=false;capture(true);lastFrame=System.nanoTime();}
    private void pause(){
        rect(0,0,960,540,0xBD06101C);panel(286,154,388,228,0xFF427285);label("OPERATION PAUSED",324,179,1.7f,WHITE);
        label("Your battle and all cooldowns are paused.",323,211,.95f,MUTED);
        button(315,242,330,35,"RESUME OPERATION",true,CYAN,()->{paused=false;capture(true);});
        button(315,286,330,35,"RETREAT / BANK CREDITS",false,GOLD,()->{game.retreat();paused=false;});
        button(315,331,330,28,"FIELD HELP  /  "+(help?"ON":"OFF"),false,MUTED,()->{help=!help;});
    }
    private void debrief(){
        rect(0,0,960,540,0xD706111C);panel(224,87,512,373,game.victory?CYAN:RED);
        label("O P E R A T I O N   D E B R I E F",263,112,.95f,MUTED);label(game.victory?"VICTORY":"REGROUP",263,145,3.1f,game.victory?CYAN:RED);
        label(game.scenario.title+" / "+game.faction.title,263,192,1,WHITE);
        label("CREDITS EARNED",263,236,.95f,MUTED);label("+"+game.earned+" CR",541,228,2,GOLD);
        label("Eliminations",263,279,1,MUTED);label(game.kills+" personal / "+game.armyKills+" army",480,279,1,WHITE);
        label("Objectives secured",263,305,1,MUTED);label(Integer.toString(game.captures),630,305,1,CYAN);
        label("Field contracts",263,331,1,MUTED);int n=0;for(boolean b:game.contracts)if(b)n++;label(n+" / 3",608,331,1,GOLD);
        label("Operation reward: "+game.completionReward+" CR included. Progress saved.",263,366,.85f,MUTED);
        button(263,402,202,36,"COMMAND DECK",true,CYAN,()->returnToHub());button(481,402,215,36,"DEPLOY AGAIN",false,GOLD,()->deploy());
    }
    private void deploy(){game=new BattlefrontGame(progress,scenario,side,mode,tier,kit,System.nanoTime());hub=false;paused=false;atlas=false;capture(true);lastFrame=System.nanoTime();}
    private void returnToHub(){hub=true;paused=false;atlas=false;capture(false);progress.save();makePreview();}
    private void feedback(String text){feedback=text;feedbackUntil=uiTime+4;}
    private void section(String s,int x,int y){label(s,x,y,hub?1.5f:1.2f,WHITE);}
    private static String format(long n){return String.format(Locale.ROOT,"%,d",n);}
    private void label(String s,int x,int y,float size,int color){if(hub){if(color==WHITE)color=MENU_TEXT;else if(color==MUTED)color=MENU_MUTED;else if(color==CYAN)color=ROSE;}GlStateManager.pushMatrix();GlStateManager.translate(x,y,0);GlStateManager.scale(size,size,1);(size>=1.2f?NeverLoseFont.BOLD:NeverLoseFont.REGULAR).draw(s,0,-2,color);GlStateManager.popMatrix();}
    private boolean over(int x,int y,int w,int h){return mouseX>=x&&mouseX<x+w&&mouseY>=y&&mouseY<y+h;}
    private void card(int x,int y,int w,int h,int fill,int border){RenderUtils.roundedRect(x,y,x+w,y+h,5,border);RenderUtils.roundedRect(x+1,y+1,x+w-1,y+h-1,4,fill);}
    private void tile(String icon,int x,int y,int size,int color){card(x,y,size,size,0xFF2B2226,0xFF514047);icons.draw(icon,x+size/5,y+size/5,size*3/5,color);}
    private static String roleIcon(Role r){return new String[]{"helmet","shield","support","scout","record"}[r.ordinal()];}
    private static String buttonIcon(String label){
        String s=label.toUpperCase(Locale.ROOT);
        if(s.equals("+"))return "plus";if(s.equals("-"))return "minus";
        if(s.contains("EXIT")||s.contains("RETREAT"))return "exit";
        if(s.contains("DEPLOY")||s.contains("RESUME")||s.contains("RETURN"))return "deploy";
        if(s.contains("WEAPON")||s.contains("POWER COUPLING"))return "weapon";
        if(s.contains("ARMOR")||s.contains("PLATING")||s.contains("SHOULDER")||s.equals("HEAVY"))return "shield";
        if(s.contains("TACTIC")||s.contains("FORMATION")||s.contains("WEDGE")||s.contains("COLUMN")||s.contains("LINE")||s.contains("PATROL")||s.contains("RALLY"))return "tactics";
        if(s.contains("APPEARANCE")||s.contains("COLORS")||s.contains("INSIGNIA")||s.contains("FINISH")||s.contains("COPY"))return "appearance";
        if(s.contains("HEAD")||s.contains("EDIT /")||s.contains("LOADOUT")||s.equals("ASSAULT")||s.contains("UNIT EQUIPMENT"))return "helmet";
        if(s.contains("PACK")||s.contains("CELL"))return "pack";
        if(s.contains("LIFE SUPPORT")||s.equals("SUPPORT"))return "support";
        if(s.contains("VETERAN")||s.equals("COMMANDER"))return "record";
        if(s.equals("SCOUT")||s.contains("MARKSMAN"))return "scout";
        if(s.contains("ARMY")||s.contains("COMPOSITION"))return "army";
        if(s.contains("PURCHASE")||s.contains("EQUIP SELECTED"))return "credits";
        if(s.contains("HELP")||s.contains("DECK"))return "manual";
        if(s.contains("CONQUEST")||s.contains("BREAKTHROUGH")||s.contains("SUPREMACY")||s.contains("WAYPOINT"))return "operations";
        if(s.contains("GALACTIC")||s.contains("ALLIANCE"))return "shield";
        return "settings";
    }
    private void button(int x,int y,int w,int h,String label,boolean selected,int accent,Runnable run){
        boolean hover=over(x,y,w,h),primary=label.equalsIgnoreCase("Deploy to battle")||label.startsWith("PURCHASE & EQUIP");
        int highlight=hub?ROSE:accent;
        card(x,y,w,h,primary?(hover?0xFFFFB7D6:ROSE):selected?(hub?0xFF31252D:0xFF203640):hover?(hub?0xFF292A29:0xFF223845):(hub?0xFF1B1C1D:0xEA10212C),primary?ROSE:selected?highlight:hover?(hub?0xFF64605E:0xFF678A99):(hub?0xFF383938:0xFF344E5B));
        int color=primary?0xFF20131B:selected?WHITE:hover?WHITE:MUTED,iconColor=primary?0xFF20131B:selected||hover?highlight:hub?0xFFC7A9B6:MUTED;
        if(w<45){icons.draw(buttonIcon(label),x+(w-15)/2,y+(h-15)/2,15,color);}
        else{
            int iconSize=h>=38?21:16,leftPadding=h>=38?42:35;
            icons.draw(buttonIcon(label),x+11,y+(h-iconSize)/2,iconSize,iconColor);
            boolean arrow=(primary&&w>190)||label.trim().endsWith(">");String title=label.replaceAll("\\s*>$","");float sz=Math.min(h>=38?1.02f:.92f,(w-leftPadding-(arrow?38:12))/Math.max(1,NeverLoseFont.REGULAR.width(title)));
            label(title,x+leftPadding,y+(h-8)/2,sz,color);if(arrow)icons.draw("arrow",x+w-30,y+(h-18)/2,18,color);
        }
        actions.add(new Action(x,y,w,h,run));
    }
    private void panel(int x,int y,int w,int h,int border){if(hub){boolean active=border==CYAN||border==GOLD;card(x,y,w,h,active?0xFF242022:0xFF1A1B1B,active?0xFF805568:over(x,y,w,h)?0xFF4A4848:0xFF353633);}else{rect(x,y,x+w,y+h,0xCF0C1B28);outline(x,y,w,h,border);}}
    private void outline(int x,int y,int w,int h,int c){rect(x,y,x+w,y+1,c);rect(x,y+h-1,x+w,y+h,c);rect(x,y,x+1,y+h,c);rect(x+w-1,y,x+w,y+h,c);}
    private void bar(int x,int y,int w,int h,double ratio,int c){rect(x,y,x+w,y+h,0xFF293E4A);int filled=(int)(w*Math.max(0,Math.min(1,ratio)));if(filled>0)rect(x,y,x+filled,y+h,c);}
    private static void rect(int x,int y,int xx,int yy,int c){drawRect(x,y,xx,yy,c);}
    @Override protected void mouseClicked(int mx,int my,int button)throws IOException{
        if(button!=0||(!hub&&!paused&&!game.finished))return;int x=(int)((mx-offsetX)/scale),y=(int)((my-offsetY)/scale);
        mouseX=x;mouseY=y;for(Action a:new ArrayList<Action>(actions))if(a.contains(x,y)){a.run.run();break;}
    }
    @Override public void handleMouseInput()throws IOException{if(!hub&&!paused&&!game.finished){if(Mouse.getEventButtonState())binding(Mouse.getEventButton()-100);return;}super.handleMouseInput();}
    private void binding(int code){if(code==Keyboard.KEY_Z)combatHud.cycleZoom();else if(code==Keyboard.KEY_R)game.startReload();else if(code==Keyboard.KEY_G){boolean ready=game.grenadeCooldown==0&&game.alive();game.grenade();if(ready)sound("blast",1);}else if(code==Keyboard.KEY_Q)game.heal();else if(code==Keyboard.KEY_V)game.strike();else if(code==Keyboard.KEY_E)game.interact();else if(code==Keyboard.KEY_TAB)game.cycleOrder();}
    @Override protected void keyTyped(char c,int code)throws IOException{
        if(!hub&&!game.finished&&code==Keyboard.KEY_M){if(atlas)closeAtlas();else{atlas=true;paused=true;capture(false);}return;}
        if(atlas&&code==Keyboard.KEY_ESCAPE){closeAtlas();return;}
        if(code==Keyboard.KEY_ESCAPE){if(hub){exit();return;}if(game.finished){returnToHub();return;}paused=!paused;capture(!paused);lastFrame=System.nanoTime();return;}
        if(code==Keyboard.KEY_F1){help=!help;return;}if(!hub&&!paused&&!game.finished)binding(code);
    }
    private void exit(){mc.displayGuiScreen(null);mc.setIngameFocus();}
    @Override public void onGuiClosed(){if(closed)return;closed=true;capture(false);if(progress!=null)progress.save();if(renderer!=null)renderer.close();icons.close();combatHud.close();if(module!=null&&module.isEnabled())module.setEnabled(false);super.onGuiClosed();}
    @Override public boolean doesGuiPauseGame(){return true;}
}
