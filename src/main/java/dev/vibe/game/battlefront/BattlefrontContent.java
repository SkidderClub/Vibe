package dev.vibe.game.battlefront;

/** Shared, renderer-independent rules and content for the local campaign. */
public final class BattlefrontContent {
    private BattlefrontContent() {}
    public enum Scenario {
        GEONOSIS("Battle of Geonosis", "22 BBY / OUTER RIM", "Dust seas, canyon ridges and the droid foundry", 0xFFEAB879),
        ENDOR("Battle of Endor", "4 ABY / FOREST MOON", "Redwood valleys, an Ewok village and the shield bunker", 0xFF89E8B5);
        public final String title, era, description; public final int color;
        Scenario(String t,String e,String d,int c){title=t;era=e;description=d;color=c;}
        public Faction faction(int side){return Faction.values()[ordinal()*2+side];}
    }
    public enum Faction {
        REPUBLIC("Galactic Republic", "Clone legions", 0xFF65CFFF),
        SEPARATISTS("Separatist Alliance", "Droid battalions", 0xFFFFAC66),
        EMPIRE("Galactic Empire", "Imperial stormtroopers", 0xFFFF748C),
        REBELS("Endor Alliance", "Ewoks & Rebel resistance", 0xFF8CE7A8);
        public final String title, subtitle; public final int color;
        Faction(String t,String s,int c){title=t;subtitle=s;color=c;}
    }
    public enum Role {
        ASSAULT("Assault", 100, 15, .82, 5.2), HEAVY("Heavy", 185, 24, 1.05, 3.7),
        MEDIC("Support", 95, 11, 1.0, 4.7), SCOUT("Scout", 75, 32, 1.45, 6.1),
        COMMANDER("Commander", 150, 20, .82, 4.8);
        public final String title; public final double health,damage,interval,speed;
        Role(String t,double h,double d,double i,double s){title=t;health=h;damage=d;interval=i;speed=s;}
        public String unit(Faction f){
            String[][] names={{"Clone trooper","Clone heavy","Clone medic","ARF scout","Clone commander"},
                    {"B1 battle droid","B2 super battle droid","Repair droid","Commando droid","B1 command droid"},
                    {"Stormtrooper","Shock trooper","Imperial medic","Scout trooper","Imperial officer"},
                    {"Rebel pathfinder","Rebel heavy","Rebel medic","Ewok hunter","Rebel captain"}};
            return names[f.ordinal()][ordinal()];
        }
    }
    public enum Order { ADVANCE, FOLLOW, HOLD }
    public enum Formation {
        WEDGE("Spearhead wedge", "An expanding V, with the point facing your heading."),
        LINE("Firing line", "Wide ranks keep overlapping fields of fire."),
        COLUMN("Marching column", "A narrow two-file column for paths and settlements."),
        DISPERSED("Dispersed patrol", "Spread out to reduce losses from area attacks.");
        public final String title,description;Formation(String t,String d){title=t;description=d;}
        /** Local right/behind offsets, rotated by the commander's heading in the simulation. */
        public double[] offset(int index){
            int row=index/2+1;double sign=index%2==0?-1:1;
            if(this==WEDGE)return new double[]{sign*row*1.5,2+row*1.65};
            if(this==LINE)return new double[]{(index%7-3)*2.4,3+(index/7)*2.8};
            if(this==COLUMN)return new double[]{sign*1.1,3+row*2.2};
            double radius=4+Math.sqrt(index+1)*1.7;return new double[]{Math.sin(index*2.4)*radius,Math.cos(index*2.4)*radius};
        }
    }
    public enum Doctrine {
        BALANCED("Combined arms", "Standard health, damage, movement and capture weight.",1,1,1,1),
        SPEARHEAD("Shock assault", "+18% damage / +8% speed / -15% health",.85,1.18,1.08,1),
        BULWARK("Defensive bulwark", "+25% health / -15% speed / -8% damage",1.25,.92,.85,1),
        RECON("Recon expedition", "+20% speed / +25% capture weight / -15% damage",1,.85,1.2,1.25);
        public final String title,description;public final double health,damage,speed,capture;
        Doctrine(String t,String d,double h,double a,double s,double c){title=t;description=d;health=h;damage=a;speed=s;capture=c;}
    }
    public enum Cosmetic {
        PAINT("Unit colors",new String[]{"Faction issue","Azure legion","Crimson guard","Desert sand","Forest ranger","Arctic white","Obsidian","Violet squadron"}),
        MARKING("Insignia",new String[]{"Clean plates","Center stripe","Twin stripes","Veteran chevrons"}),
        HELMET("Head equipment",new String[]{"Standard","Rangefinder","Macrobinoculars","Comms antenna"}),
        PACK("Field pack",new String[]{"Standard","Expedition pack","Signal relay","Bacta canisters"}),
        SHOULDER("Shoulder gear",new String[]{"Standard","Officer pauldron","Reinforced plates"}),
        WEAR("Finish",new String[]{"Factory fresh","Campaign worn","Battle scarred"});
        public final String title;public final String[] choices;Cosmetic(String t,String[] c){title=t;choices=c;}
    }
    public static final class Appearance {
        public final int paint,marking,helmet,pack,shoulder,wear;
        public Appearance(int[] values){paint=values[0];marking=values[1];helmet=values[2];pack=values[3];shoulder=values[4];wear=values[5];}
        public int color(Faction f){return paint==0?f.color&0xFFFFFF:new int[]{0,0x3D91D4,0xAC4653,0xBA9966,0x668B64,0xDAE4E3,0x394653,0x937AC5}[paint];}
    }
    public enum Weapon {
        DC15A("DC-15A rifle", "Reliable service rifle",0,28,.19,18,145,1.9,1),
        E11("E-11 carbine", "Fast, compact patrol weapon",320,24,.15,22,120,1.6,1),
        A280("A280 blaster", "Hard-hitting Rebel assault rifle",750,37,.24,20,165,2.0,1),
        DL44("DL-44 pistol", "Heavy precision sidearm",1000,66,.48,10,100,1.7,1),
        DC15LE("DC-15LE repeater", "Sustained suppression / drum cell",1300,22,.105,48,155,2.9,1),
        DLT19X("DLT-19X marksman", "Long barrel, scope and high damage",2000,112,.95,8,210,2.6,1),
        BOWCASTER("Wookiee bowcaster", "Three-bolt spread per trigger pull",2400,42,.75,12,105,2.5,3),
        ION("Ion pulse repeater", "60% bonus damage against droids",3200,28,.13,32,160,2.1,1);
        public final String title,description;public final int cost,magazine,pellets;public final double damage,interval,range,reload;
        Weapon(String t,String d,int c,double damage,double interval,int mag,double range,double reload,int pellets){title=t;description=d;cost=c;this.damage=damage;this.interval=interval;magazine=mag;this.range=range;this.reload=reload;this.pellets=pellets;}
    }
    public enum Armor {
        FIELD("Field issue", "Standard faction armor",0,0,0,1.0,0xAEBBBE),
        RECON("Recon harness", "Light plates and sensor visor",550,-10,.05,1.12,0x95B59B),
        ASSAULT("Assault cuirass", "Layered chest and shoulder armor",950,35,.10,.99,0x709CBC),
        HEAVY("Heavy siege armor", "Thick plates / blast protection",1600,75,.22,.88,0xB99670),
        COMMANDO("ARC commando rig", "Tactical visor, backpack and kama",2600,50,.18,1.05,0x7FD5D2),
        BESKAR("Beskar flight rig", "Armored jetpack / hold jump to fly",4200,95,.28,.96,0xA3A9C9);
        public final String title,description;public final int cost,color;public final double health,resistance,speed;
        Armor(String t,String d,int c,double hp,double r,double s,int color){title=t;description=d;cost=c;health=hp;resistance=r;speed=s;this.color=color;}
    }
    public enum WeaponMod {
        POWER("Power coupling", "+9% weapon damage"), CELL("Extended cell", "+3 magazine rounds"), HANDLING("Cycling assembly", "+5% fire / reload speed");
        public final String title,description;WeaponMod(String t,String d){title=t;description=d;}
    }
    public enum ArmorMod {
        PLATING("Layered plating", "+2% damage reduction"), VITALS("Life support", "+12 maximum health"), SERVOS("Powered servos", "+2% movement speed");
        public final String title,description;ArmorMod(String t,String d){title=t;description=d;}
    }
    public enum Mode {
        CONQUEST("Conquest", "Hold a majority of command posts to drain enemy reserves."),
        BREAKTHROUGH("Breakthrough", "Your army must capture all three posts in sequence."),
        SUPREMACY("Supremacy", "Eliminations drain reserves twice as fast. Posts grant field support.");
        public final String title,description; Mode(String t,String d){title=t;description=d;}
    }
    public enum Upgrade {
        DAMAGE("Blaster amplifiers", "+4 blaster damage", 160), HEALTH("Composite armor", "+20 maximum health", 140),
        COOLING("Thermal regulators", "+2 shots before overheating", 160), MOBILITY("Servo assistance", "+4% movement speed", 180),
        GRENADE("Ion detonators", "+18 grenade damage", 200), RECOVERY("Bacta injector", "+8 field healing", 170),
        ARMY_DAMAGE("Weapon supply", "+8% squad damage", 210), ARMY_HEALTH("Reinforced plating", "+10% squad health", 190),
        CAPACITY("Transport capacity", "+2 deployed soldiers", 260), REINFORCEMENTS("Reserve garrison", "+12 reinforcement tickets", 180),
        TRAINING("Tactical academy", "+10% squad capture speed", 180), LOGISTICS("Supply network", "+5% elimination credits", 220);
        public final String title,description; public final int baseCost;
        Upgrade(String t,String d,int c){title=t;description=d;baseCost=c;}
    }
}
