package dev.vibe.game.battlefront;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static dev.vibe.game.battlefront.BattlefrontContent.*;

/** Versioned local save; validated on load, atomic replacement and recovery copy. */
public final class BattlefrontProgress {
    public static final int MAX_LEVEL=12;
    private static final long MAX_CREDITS=1000000000L;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private Path file; private String error; private boolean writable=true;
    private Data data=new Data();
    private static final class Data {
        int version=3; long credits=650; int kills,wins,battles,xp;
        int[] upgrades=new int[Upgrade.values().length];
        int[] squad={6,2,1,2,1}; int[] factionWins=new int[4];
        boolean[] weapons=starter(Weapon.values().length),armors=starter(Armor.values().length);
        int weapon,armor;
        int[][] weaponMods=new int[Weapon.values().length][3],armorMods=new int[Armor.values().length][3];
        int doctrine,formation;
        int[] veterans=new int[5],armyWeapons={-1,-1,-1,-1,-1},armyArmor=new int[5];
        int[][] appearances=new int[6][Cosmetic.values().length];
    }
    private static boolean[] starter(int size){boolean[] a=new boolean[size];a[0]=true;return a;}
    public static BattlefrontProgress load(Path file){
        BattlefrontProgress p=new BattlefrontProgress();p.file=file;
        if(!Files.exists(file)&&!Files.exists(p.backup()))return p;
        try{p.data=read(file);}catch(Exception primary){
            try{p.data=read(p.backup());p.error="Recovered campaign from backup.";}
            catch(Exception backup){p.writable=false;p.error="Campaign unreadable; originals preserved. Progress cannot be saved.";}
        }
        return p;
    }
    private static Data read(Path path)throws IOException{
        if(Files.size(path)>32768)throw new IOException("Oversized campaign");
        String json=new String(Files.readAllBytes(path),StandardCharsets.UTF_8);
        JsonObject object=new JsonParser().parse(json).getAsJsonObject();
        for(String key:new String[]{"version","credits","kills","wins","battles","xp","upgrades","squad","factionWins"})if(!object.has(key))throw new IOException("Incomplete campaign");
        Data d=JSON.fromJson(object,Data.class);
        if(d==null||(d.version<1||d.version>3)||d.credits<0||d.credits>MAX_CREDITS||d.kills<0||d.wins<0||d.battles<0||d.xp<0
                ||d.upgrades==null||d.upgrades.length!=Upgrade.values().length||d.squad==null||d.squad.length!=5
                ||d.factionWins==null||d.factionWins.length!=4)throw new IOException("Invalid campaign");
        for(int n:d.upgrades)if(n<0||n>MAX_LEVEL)throw new IOException("Invalid upgrade");
        int total=0;for(int n:d.squad){if(n<0||n>36)throw new IOException("Invalid squad");total+=n;}
        if(total<1||total>12+2*d.upgrades[Upgrade.CAPACITY.ordinal()])throw new IOException("Invalid army capacity");
        for(int n:d.factionWins)if(n<0)throw new IOException("Invalid service record");
        if(d.version==1){d.version=2;d.weapons=starter(Weapon.values().length);d.armors=starter(Armor.values().length);d.weapon=d.armor=0;d.weaponMods=new int[Weapon.values().length][3];d.armorMods=new int[Armor.values().length][3];}
        else for(String key:new String[]{"weapons","armors","weapon","armor","weaponMods","armorMods"})if(!object.has(key))throw new IOException("Incomplete equipment");
        validateGear(d.weapons,d.weapon,d.weaponMods,Weapon.values().length);validateGear(d.armors,d.armor,d.armorMods,Armor.values().length);
        if(d.version<3){d.doctrine=d.formation=0;d.veterans=new int[5];d.armyWeapons=new int[]{-1,-1,-1,-1,-1};d.armyArmor=new int[5];d.appearances=new int[6][Cosmetic.values().length];d.version=3;}
        else for(String key:new String[]{"doctrine","formation","veterans","armyWeapons","armyArmor","appearances"})if(!object.has(key))throw new IOException("Incomplete battalion");
        if(d.doctrine<0||d.doctrine>=Doctrine.values().length||d.formation<0||d.formation>=Formation.values().length||d.veterans==null||d.veterans.length!=5||d.armyWeapons==null||d.armyWeapons.length!=5||d.armyArmor==null||d.armyArmor.length!=5||d.appearances==null||d.appearances.length!=6)throw new IOException("Invalid battalion");
        for(int i=0;i<5;i++)if(d.veterans[i]<0||d.veterans[i]>5||d.armyWeapons[i]<-1||d.armyWeapons[i]>=d.weapons.length||(d.armyWeapons[i]>=0&&!d.weapons[d.armyWeapons[i]])||d.armyArmor[i]<0||d.armyArmor[i]>=d.armors.length||!d.armors[d.armyArmor[i]])throw new IOException("Invalid unit equipment");
        for(int[] row:d.appearances){if(row==null||row.length!=Cosmetic.values().length)throw new IOException("Invalid appearance");for(Cosmetic c:Cosmetic.values())if(row[c.ordinal()]<0||row[c.ordinal()]>=c.choices.length)throw new IOException("Invalid cosmetic");}
        return d;
    }
    private static void validateGear(boolean[] owned,int equipped,int[][] mods,int count)throws IOException{
        if(owned==null||owned.length!=count||equipped<0||equipped>=count||!owned[0]||!owned[equipped]||mods==null||mods.length!=count)throw new IOException("Invalid equipment");
        for(int[] row:mods){if(row==null||row.length!=3)throw new IOException("Invalid attachments");for(int n:row)if(n<0||n>8)throw new IOException("Invalid attachment level");}
    }
    private Path backup(){return file.resolveSibling(file.getFileName()+".bak");}
    public boolean save(){
        if(file==null)return true;if(!writable)return false;
        try{
            Files.createDirectories(file.toAbsolutePath().getParent());
            byte[] bytes=JSON.toJson(data).getBytes(StandardCharsets.UTF_8);
            // Both copies contain the latest completed transaction; never copy an unreadable primary over recovery.
            atomic(backup(),bytes);atomic(file,bytes);error=null;return true;
        }catch(IOException ex){error="Campaign save failed; retry by returning to the hub.";return false;}
    }
    private static void atomic(Path path,byte[] bytes)throws IOException{
        Path tmp=path.resolveSibling(path.getFileName()+".tmp");
        Files.write(tmp,bytes,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        try{Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException ex){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
    public String error(){return error;}
    public long credits(){return data.credits;} public int kills(){return data.kills;}
    public int wins(){return data.wins;} public int battles(){return data.battles;}
    public int xp(){return data.xp;} public int rank(){return 1+(int)Math.sqrt(data.xp/120.0);}
    public int wins(Faction f){return data.factionWins[f.ordinal()];}
    public int maxTier(){return Math.min(8,1+data.wins/2);}
    public int level(Upgrade u){return data.upgrades[u.ordinal()];}
    public int cost(Upgrade u){int l=level(u);return u.baseCost*(l+1)+(l*l*35);}
    public boolean purchase(Upgrade u){
        if(level(u)>=MAX_LEVEL||credits()<cost(u)||!writable)return false;
        data.credits-=cost(u);data.upgrades[u.ordinal()]++;save();return true;
    }
    public int capacity(){return 12+level(Upgrade.CAPACITY)*2;}
    public int squad(Role r){return data.squad[r.ordinal()];}
    public int deployed(){int n=0;for(int c:data.squad)n+=c;return n;}
    public boolean adjust(Role r,int delta){
        if(!writable||(delta!=1&&delta!=-1)||squad(r)+delta<0||deployed()+delta<1||deployed()+delta>capacity())return false;
        data.squad[r.ordinal()]+=delta;save();return true;
    }
    public void award(long credits,int xp){data.credits=Math.min(MAX_CREDITS,data.credits+Math.max(0,credits));data.xp=(int)Math.min(100000000L,(long)data.xp+Math.max(0,xp));}
    public int elimination(boolean player,int tier){int value=(int)Math.round((player?24:7)*(1+.25*(tier-1))*(1+.05*level(Upgrade.LOGISTICS)));award(value,player?16:4);if(player)data.kills++;return value;}
    public int complete(boolean victory,Faction faction,int tier,boolean engaged){data.battles++;if(victory){data.wins++;data.factionWins[faction.ordinal()]++;}int reward=(victory?360:engaged?90:0)*tier;award(reward,victory?180:engaged?45:0);save();return reward;}
    public double maxHealth(){return 120+20*level(Upgrade.HEALTH);}
    public double damage(){return 28+4*level(Upgrade.DAMAGE);}
    public int magazine(){return 18+2*level(Upgrade.COOLING);}
    public Weapon weapon(){return Weapon.values()[data.weapon];}public Armor armor(){return Armor.values()[data.armor];}
    public boolean owns(Weapon w){return data.weapons[w.ordinal()];}public boolean owns(Armor a){return data.armors[a.ordinal()];}
    public int level(Weapon w,WeaponMod m){return data.weaponMods[w.ordinal()][m.ordinal()];}
    public int level(Armor a,ArmorMod m){return data.armorMods[a.ordinal()][m.ordinal()];}
    public int cost(Weapon w,WeaponMod m){return (140+w.cost/12)*(level(w,m)+1);}
    public int cost(Armor a,ArmorMod m){return (140+a.cost/12)*(level(a,m)+1);}
    public boolean purchase(Weapon w){if(!writable||(!owns(w)&&credits()<w.cost))return false;if(!owns(w)){data.credits-=w.cost;data.weapons[w.ordinal()]=true;}data.weapon=w.ordinal();save();return true;}
    public boolean purchase(Armor a){if(!writable||(!owns(a)&&credits()<a.cost))return false;if(!owns(a)){data.credits-=a.cost;data.armors[a.ordinal()]=true;}data.armor=a.ordinal();save();return true;}
    public boolean purchase(Weapon w,WeaponMod m){if(!writable||!owns(w)||level(w,m)>=8||credits()<cost(w,m))return false;data.credits-=cost(w,m);data.weaponMods[w.ordinal()][m.ordinal()]++;save();return true;}
    public boolean purchase(Armor a,ArmorMod m){if(!writable||!owns(a)||level(a,m)>=8||credits()<cost(a,m))return false;data.credits-=cost(a,m);data.armorMods[a.ordinal()][m.ordinal()]++;save();return true;}
    public double weaponDamage(){return weaponDamage(weapon());}
    public double weaponDamage(Weapon w){return (w.damage+4*level(Upgrade.DAMAGE))*(1+.09*level(w,WeaponMod.POWER));}
    public int weaponMagazine(){return weaponMagazine(weapon());}
    public int weaponMagazine(Weapon w){return w.magazine+2*level(Upgrade.COOLING)+3*level(w,WeaponMod.CELL);}
    public double resistance(){return Math.min(.6,armor().resistance+.02*level(armor(),ArmorMod.PLATING));}
    public double equippedHealth(){return maxHealth()+armor().health+12*level(armor(),ArmorMod.VITALS);}
    public double equippedSpeed(){return armor().speed*(1+.02*level(armor(),ArmorMod.SERVOS));}
    public Doctrine doctrine(){return Doctrine.values()[data.doctrine];}
    public Formation formation(){return Formation.values()[data.formation];}
    public boolean doctrine(Doctrine d){if(!writable||d==null)return false;data.doctrine=d.ordinal();save();return true;}
    public boolean formation(Formation f){if(!writable||f==null)return false;data.formation=f.ordinal();save();return true;}
    public int veteran(Role r){return data.veterans[r.ordinal()];}
    public int trainingCost(Role r){return (230+r.ordinal()*40)*(veteran(r)+1);}
    public boolean train(Role r){if(!writable||veteran(r)>=5||credits()<trainingCost(r))return false;data.credits-=trainingCost(r);data.veterans[r.ordinal()]++;save();return true;}
    /** Null weapon retains the role's original service weapon. */
    public Weapon armyWeapon(Role r){int w=data.armyWeapons[r.ordinal()];return w<0?null:Weapon.values()[w];}
    public Armor armyArmor(Role r){return Armor.values()[data.armyArmor[r.ordinal()]];}
    public boolean equipArmy(Role r,Weapon w){if(!writable||(w!=null&&!owns(w)))return false;data.armyWeapons[r.ordinal()]=w==null?-1:w.ordinal();save();return true;}
    public boolean equipArmy(Role r,Armor a){if(!writable||a==null||!owns(a))return false;data.armyArmor[r.ordinal()]=a.ordinal();save();return true;}
    public double armyHealth(Role r){return r.health*(1+.10*level(Upgrade.ARMY_HEALTH)+.05*veteran(r))*doctrine().health+armyArmor(r).health*.65;}
    public double armyDamage(Role r){Weapon w=armyWeapon(r);return r.damage*(1+.08*level(Upgrade.ARMY_DAMAGE)+.04*veteran(r))*doctrine().damage*(w==null?1:Math.pow(w.damage/28,.6)*(w.pellets>1?1.3:1)*(1+.04*level(w,WeaponMod.POWER)));}
    public double armyInterval(Role r){Weapon w=armyWeapon(r);return Math.max(.28,r.interval*(w==null?1:Math.pow(w.interval/.19,.55)/(1+.025*level(w,WeaponMod.HANDLING))));}
    public double armySpeed(Role r){return r.speed*doctrine().speed*armyArmor(r).speed;}
    public double armyResistance(Role r){return armyArmor(r).resistance*.7;}
    private static int lookIndex(Role r){return r==null?5:r.ordinal();}
    public int cosmetic(Role r,Cosmetic c){return data.appearances[lookIndex(r)][c.ordinal()];}
    public Appearance appearance(Role r){return new Appearance(data.appearances[lookIndex(r)]);}
    public boolean customize(Role r,Cosmetic c,int value){if(!writable||value<0||value>=c.choices.length)return false;data.appearances[lookIndex(r)][c.ordinal()]=value;save();return true;}
    public boolean copyAppearanceToArmy(Role r){if(!writable)return false;int[] look=data.appearances[lookIndex(r)].clone();for(int i=0;i<5;i++)data.appearances[i]=look.clone();save();return true;}
}
