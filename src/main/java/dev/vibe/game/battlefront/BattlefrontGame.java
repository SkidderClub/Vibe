package dev.vibe.game.battlefront;

import java.util.*;
import static dev.vibe.game.battlefront.BattlefrontContent.*;

/** Fixed-step offline war simulation. No Minecraft entities, packets, or world mutations. */
public final class BattlefrontGame {
    public final BattlefrontProgress progress; public final BattlefrontWorld world;
    public final Scenario scenario; public final Faction faction; public final Mode mode; public final int side,tier;
    public final List<Soldier> soldiers=new ArrayList<Soldier>();
    public final List<Bolt> bolts=new ArrayList<Bolt>(); public final List<Particle> particles=new ArrayList<Particle>();
    public final List<DamageNumber> damageNumbers=new ArrayList<DamageNumber>();
    public final List<DamageDirection> damageDirections=new ArrayList<DamageDirection>();
    public final Post[] posts=new Post[3]; public final double[] tickets=new double[2];
    public final boolean[] contracts=new boolean[3];
    public final Set<String> searchedCaches=new HashSet<String>();
    public double x,y,z,yaw,pitch,health,time,respawn,protection=3,reload,fireDelay,grenadeCooldown,healCooldown,strikeCooldown;
    public double recoil,hitMarker,hurtFlash,jumpVelocity,noticeTime,orderX,orderZ,walk,jetFuel=4;
    public boolean lastHitHead,lastHitKill;
    public double reloadDuration;
    public boolean jetting,aiming;
    public double waypointX,waypointZ,orderYaw;public boolean waypoint;
    public final Set<String> discovered=new HashSet<String>();public boolean reconContract;
    public int ammo,kills,armyKills,captures,deaths,earned,completionReward,kit,shots;
    public boolean finished,victory; public Order order=Order.ADVANCE;
    public String notice="Capture the command posts. Your army is ready.";
    private double accumulator,ticketTimer,saveTimer; private boolean jumpHeld;
    private final Random random; public final long seed;
    private final BattlefrontNavigation navigation;
    public static final class Input { public boolean forward,back,left,right,sprint,jump,attack,aim; }
    public static final class Camera {public final double x,y,z,dx,dy,dz;Camera(double x,double y,double z,double dx,double dy,double dz){this.x=x;this.y=y;this.z=z;this.dx=dx;this.dy=dy;this.dz=dz;}}
    public static final class Soldier {
        public final int side,index; public final Role role; public double x,y,z,yaw,health,maxHealth,cooldown,respawn,walk,hurt;
        private double stuck,repath,routeX,routeZ;private List<double[]> route;private int routeStep;
        public String guardLocation;private double guardX,guardZ;
        public Soldier(int side,Role role,int index){this.side=side;this.role=role;this.index=index;}
        public boolean alive(){return health>0;}
    }
    public static final class Post {
        public final double x,z;public final String name; public double control;public int owner=-1;
        Post(double[] p,String n){x=p[0];z=p[1];name=n;}
        public double captureRatio(){return Math.abs(control)/100;}
    }
    public static final class Bolt {
        public final double x,y,z,tx,ty,tz;public final int side;public double life=.13;
        Bolt(double x,double y,double z,double tx,double ty,double tz,int s){this.x=x;this.y=y;this.z=z;this.tx=tx;this.ty=ty;this.tz=tz;side=s;}
    }
    public static final class Particle {
        public double x,y,z,vx,vy,vz,life,max;public final int color;
        Particle(double x,double y,double z,double vx,double vy,double vz,double life,int c){this.x=x;this.y=y;this.z=z;this.vx=vx;this.vy=vy;this.vz=vz;this.life=life;max=life;color=c;}
    }
    public static final class DamageNumber {
        public final Soldier target;public double x,y,z,amount,life=1.05,age;public boolean head,kill;
        DamageNumber(Soldier s,double amount,boolean head,double height){target=s;x=s.x;y=s.y+height+.25;z=s.z;this.amount=amount;this.head=head;kill=!s.alive();}
    }
    public static final class DamageDirection {
        public final double yaw;public double life=1.15,strength;
        DamageDirection(double yaw,double strength){this.yaw=yaw;this.strength=strength;}
    }
    public BattlefrontGame(BattlefrontProgress p,Scenario scenario,int side,Mode mode,int tier,int kit,long seed){
        if(side<0||side>1||tier<1||tier>p.maxTier()||kit<0||kit>2)throw new IllegalArgumentException("Invalid deployment");
        progress=p;this.scenario=scenario;this.side=side;this.mode=mode;this.tier=tier;this.kit=kit;this.seed=seed;random=new Random(seed);
        faction=scenario.faction(side);world=new BattlefrontWorld(scenario);navigation=new BattlefrontNavigation(world);
        for(int i=0;i<3;i++)posts[i]=new Post(world.posts[i],world.names[i]);
        if(mode!=Mode.BREAKTHROUGH){posts[0].control=100;posts[0].owner=0;posts[2].control=-100;posts[2].owner=1;}
        else for(Post post:posts){post.owner=1-side;post.control=side==0?-100:100;}
        tickets[side]=145+12*p.level(Upgrade.REINFORCEMENTS);tickets[1-side]=145+(tier-1)*15;
        int index=0;for(Role role:Role.values())for(int i=0;i<p.squad(role);i++)addSoldier(side,role,index++);
        int enemies=13+(tier-1)*2;for(int i=0;i<enemies;i++)addSoldier(1-side,Role.values()[i%10==0?4:i%5==0?2:i%4==0?1:i%3==0?3:0],i);
        for(BattlefrontArchitecture.Building b:world.buildings)if(Math.max(Math.abs(b.x),Math.abs(b.z))>112)for(int i=0;i<2;i++){
            Soldier guard=new Soldier(1-side,i==0?Role.ASSAULT:Role.SCOUT,enemies++);guard.guardLocation=b.name;guard.guardX=b.x+(i==0?-2:2);guard.guardZ=b.z+b.d+(b.floor>1?27:6);
            guard.maxHealth=guard.role.health*(1+.075*(tier-1));spawn(guard);soldiers.add(guard);
        }
        spawnPlayer();
    }
    private void addSoldier(int team,Role role,int index){Soldier s=new Soldier(team,role,index);s.maxHealth=team==side?progress.armyHealth(role):role.health*(1+.075*(tier-1));spawn(s);soldiers.add(s);}
    private void spawn(Soldier s){double[] loc=world.spawn(s.side,s.index);
        if(s.guardLocation!=null)for(int i=0;i<64;i++){double radius=i*.16,nx=s.guardX+Math.sin(i*2.4)*radius,nz=s.guardZ+Math.cos(i*2.4)*radius;if(!world.blocked(nx,nz,.6)){loc=new double[]{nx,nz};break;}}
        s.x=loc[0];s.z=loc[1];s.y=world.height(s.x,s.z);s.health=s.maxHealth;s.cooldown=random.nextDouble()*2;s.respawn=0;s.yaw=s.side==0?0:180;s.route=null;s.stuck=0;s.repath=2+s.index*.12;}
    private void spawnPlayer(){double[] loc=world.spawn(side,3);x=loc[0];z=loc[1];y=world.height(x,z);Post home=posts[side==0?0:2];yaw=Math.toDegrees(Math.atan2(home.x-x,z-home.z));pitch=0;health=maxHealth();ammo=magazine();respawn=0;protection=3;reload=0;jumpVelocity=0;jetFuel=4;hitMarker=0;damageDirections.clear();damageNumbers.clear();hurtFlash=0;}
    public double maxHealth(){return progress.equippedHealth()*(kit==1?1.15:kit==2?.9:1);}
    public int magazine(){return progress.weaponMagazine();}
    public String weapon(){return progress.weapon().title.toUpperCase(Locale.ROOT);}
    public double eyeY(){return y+1.68;}
    public boolean alive(){return health>0;}
    /** Orbit over the shoulder; collision retracts the camera before walls, ceilings and terrain. */
    public Camera camera(boolean aim){
        double a=Math.toRadians(yaw),b=Math.toRadians(pitch),dx=Math.sin(a)*Math.cos(b),dy=-Math.sin(b),dz=-Math.cos(a)*Math.cos(b);
        double distance=aim?2.25:4.6,shoulder=aim?.62:.85;
        double ox=-dx*distance+Math.cos(a)*shoulder,oy=-dy*distance+.38,oz=-dz*distance+Math.sin(a)*shoulder;
        double length=Math.sqrt(ox*ox+oy*oy+oz*oz),hit=world.ray(x,eyeY(),z,ox/length,oy/length,oz/length,length);
        // Four near-plane guard rays keep the shoulder camera from clipping door frames.
        for(int i=-1;i<=1;i+=2){hit=Math.min(hit,world.ray(x+i*.16,eyeY(),z,ox/length,oy/length,oz/length,length));hit=Math.min(hit,world.ray(x,eyeY()+i*.16,z,ox/length,oy/length,oz/length,length));}
        double t=Math.max(0,Math.min(1,(hit-.22)/length));
        return new Camera(x+ox*t,eyeY()+oy*t,z+oz*t,dx,dy,dz);
    }
    public void advance(double seconds,Input input){
        if(finished||!Double.isFinite(seconds)||seconds<=0)return;
        accumulator+=Math.min(.2,seconds);while(accumulator>=1.0/30){step(1.0/30,input);accumulator-=1.0/30;}
    }
    private void step(double dt,Input in){
        if(finished)return;time+=dt;saveTimer+=dt;
        fireDelay=Math.max(0,fireDelay-dt);recoil=Math.max(0,recoil-dt*4);hitMarker=Math.max(0,hitMarker-dt);hurtFlash=Math.max(0,hurtFlash-dt);
        grenadeCooldown=Math.max(0,grenadeCooldown-dt);healCooldown=Math.max(0,healCooldown-dt);strikeCooldown=Math.max(0,strikeCooldown-dt);protection=Math.max(0,protection-dt);
        if(reload>0){reload=Math.max(0,reload-dt);if(reload==0)ammo=magazine();}
        for(Iterator<DamageNumber> it=damageNumbers.iterator();it.hasNext();){DamageNumber n=it.next();n.age+=dt;n.life-=dt;if(n.life<=0)it.remove();}
        for(Iterator<DamageDirection> it=damageDirections.iterator();it.hasNext();)if((it.next().life-=dt)<=0)it.remove();
        aiming=in.aim;if(alive()){
            movePlayer(dt,in);if(in.attack)fire(in.aim);
            for(BattlefrontArchitecture.Building b:world.buildings)if(Math.hypot(x-b.x,z-b.z)<20&&discovered.add(b.name))say("Location discovered / "+b.name);
            if(time-lastDamage>7)health=Math.min(maxHealth(),health+dt*3);
        }else{respawn-=dt;if(respawn<=0&&tickets[side]>0)spawnPlayer();}
        for(Soldier s:soldiers)updateSoldier(s,dt);
        updatePosts(dt);ticketTimer+=dt;
        if(ticketTimer>=1){ticketTimer-=1;int a=owned(0),b=owned(1);
            if(mode==Mode.CONQUEST){if(a>b)tickets[1]-=.8+(a-b)*.8;if(b>a)tickets[0]-=.8+(b-a)*.8;}
            if(mode==Mode.BREAKTHROUGH){tickets[side]-=.12;if(owned(side)==3){finish(true);return;}}
            if(mode==Mode.SUPREMACY){if(a>b)tickets[1]-=.25;if(b>a)tickets[0]-=.25;}
        }
        for(Iterator<Bolt> it=bolts.iterator();it.hasNext();)if((it.next().life-=dt)<=0)it.remove();
        for(Iterator<Particle> it=particles.iterator();it.hasNext();){Particle p=it.next();p.life-=dt;if(p.life<=0){it.remove();continue;}p.x+=p.vx*dt;p.y+=p.vy*dt;p.z+=p.vz*dt;p.vy-=dt*8;}
        checkContracts();if(tickets[0]<=0||tickets[1]<=0)finish(tickets[1-side]<=0&&tickets[side]>0);
        else if(time>=420)finish(mode==Mode.BREAKTHROUGH?owned(side)==3:tickets[side]>tickets[1-side]);
        if(saveTimer>=25){saveTimer=0;progress.save();}
    }
    private double lastDamage=-10;
    private void movePlayer(double dt,Input in){
        double f=(in.forward?1:0)-(in.back?1:0),s=(in.right?1:0)-(in.left?1:0),len=Math.hypot(f,s);
        if(len>0){double speed=(in.sprint&&!in.aim?9:6)*(1+.04*progress.level(Upgrade.MOBILITY))*progress.equippedSpeed()*(kit==1?.85:kit==2?1.12:1);double r=Math.toRadians(yaw);
            double dx=(Math.sin(r)*f+Math.cos(r)*s)*speed*dt/len,dz=(-Math.cos(r)*f+Math.sin(r)*s)*speed*dt/len;
            move(dx,0);move(0,dz);walk+=Math.hypot(dx,dz)*2;
        }
        double floor=world.floor(x,z,y);boolean grounded=y<=floor+.15;if(in.jump&&!jumpHeld&&grounded)jumpVelocity=9.6;
        jetting=progress.armor()==Armor.BESKAR&&in.jump&&!grounded&&jetFuel>0;
        if(jetting){jumpVelocity=Math.min(7,jumpVelocity+dt*28);jetFuel=Math.max(0,jetFuel-dt);}else if(grounded)jetFuel=Math.min(4,jetFuel+dt*1.4);
        jumpHeld=in.jump;jumpVelocity-=18*dt;double ceiling=world.ceiling(x,z,y);y+=jumpVelocity*dt;
        if(y+1.85>ceiling&&jumpVelocity>0){y=ceiling-1.85;jumpVelocity=0;}
        if(y<floor){y=floor;jumpVelocity=0;}
    }
    private void move(double dx,double dz){double nx=x+dx,nz=z+dz,f=world.floor(nx,nz,y),feet=Math.max(y,f);if(!world.blocked(nx,feet,nz,.38,1.85)){x=nx;z=nz;if(y<f){y=f;jumpVelocity=Math.max(0,jumpVelocity);}}}
    public void startReload(){if(!finished&&alive()&&reload==0&&ammo<magazine()){reloadDuration=progress.weapon().reload/(1+.05*progress.level(progress.weapon(),WeaponMod.HANDLING));reload=reloadDuration;}}
    /** -1 means idle; animation and both HUD progress indicators share this clock. */
    public double reloadProgress(){return reload>0?Math.max(0,Math.min(1,1-reload/Math.max(.01,reloadDuration))):-1;}
    public double fieldOfView(boolean aim){return aim?(progress.weapon()==Weapon.DLT19X?38:58):80;}
    public void fire(boolean aim){
        if(finished||!alive()||fireDelay>0||reload>0)return;if(ammo<=0){startReload();return;}
        Weapon weapon=progress.weapon();ammo--;fireDelay=weapon.interval/(1+.05*progress.level(weapon,WeaponMod.HANDLING));recoil=1;
        Camera c=camera(aim);double range=world.ray(c.x,c.y,c.z,c.dx,c.dy,c.dz,weapon.range);
        for(Soldier s:soldiers)if(s.side!=side&&s.alive()){double hit=actorRay(s,c.x,c.y,c.z,c.dx,c.dy,c.dz,range);range=Math.min(range,hit);}
        double a=Math.toRadians(yaw),mx=x+Math.sin(a)*.58+Math.cos(a)*.25,my=y+1.28,mz=z-Math.cos(a)*.58+Math.sin(a)*.25;
        double muzzleLength=Math.hypot(mx-x,mz-z),muzzleLimit=world.ray(x,my,z,(mx-x)/muzzleLength,0,(mz-z)/muzzleLength,muzzleLength);
        if(muzzleLimit<muzzleLength){double factor=Math.max(0,(muzzleLimit-.03)/muzzleLength);mx=x+(mx-x)*factor;mz=z+(mz-z)*factor;}
        boolean attackHit=false,attackHead=false,attackKill=false;
        for(int pellet=0;pellet<weapon.pellets;pellet++){
        double spread=weapon.pellets>1?.026:aim?.0015:.008;
        double tx=c.x+c.dx*range,ty=c.y+c.dy*range,tz=c.z+c.dz*range,dx=tx-mx,dy=ty-my,dz=tz-mz,length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        dx=dx/length+(random.nextDouble()-.5)*spread;dy=dy/length+(random.nextDouble()-.5)*spread;dz=dz/length+(random.nextDouble()-.5)*spread;length=Math.sqrt(dx*dx+dy*dy+dz*dz);dx/=length;dy/=length;dz/=length;
        double distance=world.ray(mx,my,mz,dx,dy,dz,weapon.range);
        Soldier target=null;boolean head=false;
        for(Soldier s:soldiers)if(s.side!=side&&s.alive()){
            double along=actorRay(s,mx,my,mz,dx,dy,dz,distance);if(along<distance){distance=along;target=s;head=my+dy*along>s.y+unitHeight(s)*.8;}
        }
        bolt(mx,my,mz,mx+dx*distance,my+dy*distance,mz+dz*distance,side);
        if(target!=null){double ion=weapon==Weapon.ION&&scenario.faction(target.side)==Faction.SEPARATISTS?1.6:1;damage(target,progress.weaponDamage()*(head?1.65:1)*ion,true,head);attackHit=true;attackHead|=head;attackKill|=!target.alive();}
        burst(mx+dx*distance,my+dy*distance,mz+dz*distance,0xFFFFBA69,5);
        }
        if(attackHit)confirmHit(attackHead,attackKill);
    }
    private double actorRay(Soldier s,double x,double y,double z,double dx,double dy,double dz,double max){double h=unitHeight(s),along=(s.x-x)*dx+(s.y+h*.55-y)*dy+(s.z-z)*dz;if(along<0||along>=max)return max;double py=y+along*dy;return Math.hypot(x+along*dx-s.x,z+along*dz-s.z)<(s.role==Role.HEAVY?.65:.46)&&py>=s.y&&py<=s.y+h?along:max;}
    public double unitHeight(Soldier s){return scenario.faction(s.side)==Faction.REBELS&&s.role==Role.SCOUT?1.4:2.05;}
    private void updateSoldier(Soldier s,double dt){
        s.hurt=Math.max(0,s.hurt-dt);if(!s.alive()){if(s.guardLocation!=null&&searchedCaches.contains(s.guardLocation))return;s.respawn-=dt;if(s.respawn<=0&&tickets[s.side]>0)spawn(s);return;}
        s.cooldown-=dt;s.y=world.floor(s.x,s.z,s.y);
        double tx,tz;int target=assignment(s);
        if(s.guardLocation!=null){double patrol=Math.floor(time/12)*1.4+s.index*2.4;tx=s.guardX+Math.sin(patrol)*5;tz=s.guardZ+Math.cos(patrol)*5;}
        else if(s.side==side&&((order==Order.FOLLOW&&alive())||order==Order.HOLD)){
            double[] offset=progress.formation().offset(s.index);double heading=Math.toRadians(order==Order.FOLLOW?yaw:orderYaw);
            tx=(order==Order.FOLLOW?x:orderX)+offset[0]*Math.cos(heading)-offset[1]*Math.sin(heading);
            tz=(order==Order.FOLLOW?z:orderZ)+offset[0]*Math.sin(heading)+offset[1]*Math.cos(heading);
            tx=Math.max(-BattlefrontWorld.LIMIT+2,Math.min(BattlefrontWorld.LIMIT-2,tx));tz=Math.max(-BattlefrontWorld.LIMIT+2,Math.min(BattlefrontWorld.LIMIT-2,tz));
        }
        else{tx=posts[target].x+Math.sin(s.index*2.4)*3.5;tz=posts[target].z+Math.cos(s.index*2.4)*3.5;}
        Soldier enemy=null;double nearest=60*60;boolean playerTarget=false;
        for(Soldier other:soldiers)if(other.side!=s.side&&other.alive()){double d=dist2(s.x,s.z,other.x,other.z);if(d<nearest){nearest=d;enemy=other;}}
        if(s.side!=side&&alive()&&dist2(s.x,s.z,x,z)<nearest){nearest=dist2(s.x,s.z,x,z);playerTarget=true;}
        double ex=playerTarget?x:enemy==null?tx:enemy.x,ez=playerTarget?z:enemy==null?tz:enemy.z;
        double ey=playerTarget?eyeY()-.25:enemy==null?0:enemy.y+unitHeight(enemy)*.55;
        boolean sees=(enemy!=null||playerTarget)&&nearest<2500&&world.visible(s.x,s.y+1.45,s.z,ex,ey,ez);
        if(sees&&s.cooldown<=0){
            s.yaw=Math.toDegrees(Math.atan2(ex-s.x,s.z-ez));s.cooldown=(s.side==side?progress.armyInterval(s.role):s.role.interval)*(.85+random.nextDouble()*.45);
            bolt(s.x,s.y+1.35,s.z,ex,ey,ez,s.side);
            double damage=s.side==side?progress.armyDamage(s.role):s.role.damage*(1+.06*(tier-1));
            if(s.side==side&&progress.armyWeapon(s.role)==Weapon.ION&&scenario.faction(1-side)==Faction.SEPARATISTS)damage*=1.6;
            if(random.nextDouble()<.52-Math.sqrt(nearest)*.004){if(playerTarget)hurt(damage*.75,s.x,s.z);else damage(enemy,damage,false);}
        }
        s.repath=Math.max(0,s.repath-dt);
        if(s.route!=null&&Math.hypot(tx-s.routeX,tz-s.routeZ)>18){s.route=null;s.repath=Math.min(s.repath,1);}
        if(s.repath==0&&(s.stuck>1.2||(Math.hypot(tx-s.x,tz-s.z)>40&&s.route==null))){
            s.route=navigation.route(s.x,s.z,tx,tz);s.routeStep=0;s.routeX=tx;s.routeZ=tz;s.repath=10+s.index*.15;s.stuck=0;
        }
        if(s.route!=null){while(s.routeStep<s.route.size()&&Math.hypot(s.x-s.route.get(s.routeStep)[0],s.z-s.route.get(s.routeStep)[1])<1.2)s.routeStep++;
            if(s.routeStep<s.route.size()){tx=s.route.get(s.routeStep)[0];tz=s.route.get(s.routeStep)[1];}else s.route=null;}
        if(!sees||nearest>17*17){double dx=tx-s.x,dz=tz-s.z,d=Math.hypot(dx,dz);if(d>1){
            double angle=Math.atan2(dx,dz),speed=(s.side==side?progress.armySpeed(s.role):s.role.speed)*dt;
            // Bounded steering tries both sides of cover, with persistent handedness for each soldier.
            for(int i=0;i<9;i++){double turn=i==0?0:((i+1)/2)*.42*(i%2==0?-1:1)*(s.index%2==0?1:-1),a=angle+turn;
                double nx=s.x+Math.sin(a)*speed,nz=s.z+Math.cos(a)*speed;
                double feet=world.floor(nx,nz,s.y);if(!world.blocked(nx,feet,nz,.48,unitHeight(s))){s.x=nx;s.z=nz;s.y=feet;s.walk+=speed*2;s.yaw=Math.toDegrees(Math.atan2(Math.sin(a),-Math.cos(a)));break;}
            }
            double gain=d-Math.hypot(tx-s.x,tz-s.z);s.stuck=gain<speed*.12?s.stuck+dt:Math.max(0,s.stuck-dt*.35);
        }}
        if(s.role==Role.MEDIC){double training=s.side==side?1+.15*progress.veteran(Role.MEDIC):1;
            for(Soldier ally:soldiers)if(ally.side==s.side&&ally.alive()&&dist2(ally.x,ally.z,s.x,s.z)<49)ally.health=Math.min(ally.maxHealth,ally.health+dt*3*training);
            if(s.side==side&&alive()&&dist2(x,z,s.x,s.z)<49)health=Math.min(maxHealth(),health+dt*2*training);}
    }
    private int targetPost(int team){
        if(mode==Mode.BREAKTHROUGH){for(int i=0;i<3;i++){int p=side==0?i:2-i;if(posts[p].owner!=side)return p;}return 1;}
        int start=team==0?0:2,step=team==0?1:-1;for(int i=start;i>=0&&i<3;i+=step)if(posts[i].owner!=team)return i;
        return 1;
    }
    private int assignment(Soldier s){
        if(mode==Mode.BREAKTHROUGH)return targetPost(s.side);
        int home=s.side==0?0:2,preferred=s.index%4==0?home:s.index%4==1?2-home:1;
        if(posts[preferred].owner!=s.side||(preferred==home&&time<50))return preferred;
        int result=preferred;double nearest=Double.MAX_VALUE;
        for(int i=0;i<posts.length;i++)if(posts[i].owner!=s.side){double d=dist2(s.x,s.z,posts[i].x,posts[i].z);if(d<nearest){nearest=d;result=i;}}
        return result;
    }
    private void updatePosts(double dt){
        for(int i=0;i<posts.length;i++){
            Post p=posts[i];if(mode==Mode.BREAKTHROUGH&&i!=targetPost(side))continue;
            double a=0,b=0;for(Soldier s:soldiers)if(s.alive()&&dist2(s.x,s.z,p.x,p.z)<64){double weight=s.role==Role.COMMANDER?2:1;if(s.side==side)weight*=(1+.1*progress.level(Upgrade.TRAINING))*progress.doctrine().capture;if(s.side==0)a+=weight;else b+=weight;}
            if(alive()&&dist2(x,z,p.x,p.z)<64){if(side==0)a+=2.5;else b+=2.5;}
            if(a>0&&b>0)continue;if(a+b==0)continue;
            p.control=Math.max(-100,Math.min(100,p.control+(a>b?1:-1)*Math.min(16,4+Math.abs(a-b)*1.6)*dt));
            int old=p.owner;if(p.control>=100)p.owner=0;else if(p.control<=-100)p.owner=1;else if(Math.abs(p.control)<1)p.owner=-1;
            if(old!=p.owner&&p.owner==side){captures++;earned+=60;progress.award(60,35);say(p.name+" secured  +60 credits");}
        }
    }
    public int owned(int team){int n=0;for(Post p:posts)if(p.owner==team)n++;return n;}
    private static double dist2(double x,double z,double tx,double tz){double dx=x-tx,dz=z-tz;return dx*dx+dz*dz;}
    public void damage(Soldier s,double amount,boolean player){
        damage(s,amount,player,false);
    }
    private void damage(Soldier s,double amount,boolean player,boolean head){
        if(finished||!s.alive()||amount<=0||!Double.isFinite(amount))return;double dealt=Math.min(s.health,amount*(s.side==side?1-progress.armyResistance(s.role):1));s.health-=dealt;s.hurt=.12;
        if(player&&s.side!=side){confirmHit(head,s.health<=0);DamageNumber combined=null;
            for(DamageNumber n:damageNumbers)if(n.target==s&&n.life>.83){combined=n;break;}
            if(combined!=null){combined.amount+=dealt;combined.head|=head;combined.kill|=!s.alive();combined.life=1.05;combined.age=0;combined.x=s.x;combined.y=s.y+unitHeight(s)+.25;combined.z=s.z;}
            else{if(damageNumbers.size()>=16)damageNumbers.remove(0);damageNumbers.add(new DamageNumber(s,dealt,head,unitHeight(s)));}
        }
        if(s.health<=0){s.health=0;s.respawn=s.guardLocation==null?6+random.nextDouble()*3:45;tickets[s.side]-=mode==Mode.SUPREMACY?2:1;
            burst(s.x,s.y+1,s.z,0xFFFFBE74,12);if(s.side!=side){armyKills++;if(player)kills++;int credit=progress.elimination(player,tier);earned+=credit;if(player)say("Target eliminated  +"+credit+" credits");}}
    }
    private void confirmHit(boolean head,boolean kill){
        // fire() aggregates one trigger pull; a later shot starts a fresh confirmation.
        lastHitHead=head;lastHitKill=kill;
        hitMarker=lastHitKill?.36:lastHitHead?.26:.20;
    }
    public void hurt(double damage){hurt(damage,Double.NaN,Double.NaN);}
    public void hurt(double damage,double sourceX,double sourceZ){
        if(!alive()||finished||protection>0||damage<=0||!Double.isFinite(damage))return;
        double dealt=Math.min(health,damage*(1-progress.resistance()));health=Math.max(0,health-dealt);lastDamage=time;hurtFlash=.45;
        if(Double.isFinite(sourceX)&&Double.isFinite(sourceZ)){
            double direction=Math.toDegrees(Math.atan2(sourceX-x,z-sourceZ));DamageDirection same=null;
            for(DamageDirection cue:damageDirections)if(Math.abs(Math.IEEEremainder(direction-cue.yaw,360))<24){same=cue;break;}
            if(same!=null){same.life=1.15;same.strength=Math.min(1,same.strength+dealt/maxHealth());}
            else{if(damageDirections.size()>=4)damageDirections.remove(0);damageDirections.add(new DamageDirection(direction,Math.min(1,dealt/maxHealth())));}
        }
        if(health==0){deaths++;tickets[side]-=4;respawn=5;reload=0;say("Signal lost. Reinserting in 5 seconds.");}
    }
    public void interact(){if(!alive()||finished)return;for(BattlefrontArchitecture.Building b:world.buildings)if(Math.hypot(x-b.cacheX(),z-b.cacheZ())<2.8&&Math.abs(y+1-b.cacheY())<2){if(searchedCaches.add(b.name)){earned+=85;progress.award(85,35);health=Math.min(maxHealth(),health+40);ammo=magazine();reload=0;say("Supply cache recovered / +85 CR / power cell and bacta");}else say("This supply cache has already been recovered.");return;}}
    public void grenade(){
        if(finished||!alive()||grenadeCooldown>0)return;grenadeCooldown=16;
        Camera c=camera(aiming);double range=world.ray(c.x,c.y,c.z,c.dx,c.dy,c.dz,29),dx=c.x+c.dx*range-x,dy=c.y+c.dy*range-eyeY(),dz=c.z+c.dz*range-z;
        double length=Math.sqrt(dx*dx+dy*dy+dz*dz);dx/=length;dy/=length;dz/=length;
        double d=world.ray(x,eyeY(),z,dx,dy,dz,25),gx=x+dx*d,gz=z+dz*d,gy=Math.max(world.height(gx,gz)+.3,eyeY()+dy*d);
        explosion(gx,gy,gz,8,95+18*progress.level(Upgrade.GRENADE));say("Ion detonation");
    }
    public void heal(){if(finished||!alive()||healCooldown>0||health>=maxHealth())return;healCooldown=26;health=Math.min(maxHealth(),health+55+8*progress.level(Upgrade.RECOVERY));say("Bacta injector activated");}
    public void strike(){
        if(finished||!alive()||strikeCooldown>0||kills<6){say("Orbital support requires 6 eliminations this battle.");return;}
        strikeCooldown=70;double a=Math.toRadians(yaw),gx=x+Math.sin(a)*36,gz=z-Math.cos(a)*36;
        for(int i=0;i<5;i++){double px=gx+Math.sin(i*2.4)*6,pz=gz+Math.cos(i*2.4)*6,py=world.height(px,pz);bolt(px,py+70,pz,px,py,pz,side);explosion(px,py+.5,pz,9,140);}
        say("Orbital support inbound");
    }
    private void explosion(double gx,double gy,double gz,double radius,double damage){
        burst(gx,gy,gz,0xFFFFBA64,50);
        for(Soldier s:soldiers)if(s.side!=side&&s.alive()){
            double d=Math.sqrt(dist2(s.x,s.z,gx,gz)+Math.pow(s.y+1-gy,2));
            if(d<radius&&world.visible(gx,gy,gz,s.x,s.y+1,s.z))damage(s,damage*(1-d/(radius*1.4)),true);
        }
    }
    public void cycleOrder(){order=Order.values()[(order.ordinal()+1)%3];orderX=x;orderZ=z;orderYaw=yaw;say("Squad order: "+order.name()+" / "+progress.formation().title);}
    public boolean markWaypoint(double px,double pz){
        if(!Double.isFinite(px)||!Double.isFinite(pz)||Math.abs(px)>BattlefrontWorld.LIMIT-2||Math.abs(pz)>BattlefrontWorld.LIMIT-2)return false;
        for(int i=0;i<64;i++){double radius=i==0?0:1+(i/8)*1.6,angle=i*Math.PI/4,nx=px+Math.sin(angle)*radius,nz=pz+Math.cos(angle)*radius;
            if(!world.blocked(nx,nz,.7)){waypointX=nx;waypointZ=nz;waypoint=true;return true;}}
        return false;
    }
    public void rallyWaypoint(){if(!waypoint||finished)return;order=Order.HOLD;orderX=waypointX;orderZ=waypointZ;orderYaw=yaw;say("Battalion moving to marked rally point / "+progress.formation().title);}
    public void say(String text){notice=text;noticeTime=time+4;}
    private void checkContracts(){
        int[] counts={kills,captures,armyKills},goals={10,3,35},rewards={180,220,250};
        for(int i=0;i<3;i++)if(!contracts[i]&&counts[i]>=goals[i]){contracts[i]=true;progress.award(rewards[i],80);earned+=rewards[i];say("Field contract completed  +"+rewards[i]+" credits");}
        if(!reconContract&&searchedCaches.size()>=5){reconContract=true;progress.award(300,120);earned+=300;say("Recon contract / five supply caches recovered / +300 CR");}
    }
    public void finish(boolean won){if(finished)return;finished=true;victory=won;completionReward=progress.complete(won,faction,tier,time>=30&&(armyKills>0||captures>0));earned+=completionReward;}
    public void retreat(){finish(false);}
    private void bolt(double x,double y,double z,double tx,double ty,double tz,int side){shots++;if(bolts.size()>=180)bolts.remove(0);bolts.add(new Bolt(x,y,z,tx,ty,tz,side));}
    private void burst(double x,double y,double z,int color,int count){for(int i=0;i<count&&particles.size()<400;i++)particles.add(new Particle(x,y,z,(random.nextDouble()-.5)*8,random.nextDouble()*7,(random.nextDouble()-.5)*8,.3+random.nextDouble()*.65,color));}
}
