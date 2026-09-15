package dev.vibe.game.gta;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Deterministic fixed-step gameplay, isolated from the Minecraft world and its network. */
public final class Gta7Game {
    public final Gta7World world;
    public final Gta7Progress progress;
    public final List<Npc> npcs = new ArrayList<Npc>();
    public final List<Car> cars = new ArrayList<Car>();
    public final List<Drop> drops = new ArrayList<Drop>();
    public final List<Tracer> tracers = new ArrayList<Tracer>();
    public final List<Particle> particles=new ArrayList<Particle>();
    private final Random effectsRandom=new Random(70916);
    private int pathBudget;
    public int lastPathRequests;
    private final Random random = new Random(771207);
    public double x, y, z, yaw, pitch, velocityY, time, walkCycle, speed, recoil, flash, hitMarker, hurtFlash;
    public BigDecimal health, jetFuel;
    public BigInteger ammo;
    public double lastActivity, dayTime;
    public boolean jetting;
    public int weapon, wanted, kills, runXp;
    public boolean dead, grounded = true, crouching;
    public double reload, attackCooldown;
    public String notice = "Explore Northside. Open doorways lead inside.";
    public double noticeUntil = 8;
    private double dispatch, population, crimeTime, trafficHurt, accumulator;
    private int crimes;

    public Gta7Game(Gta7World world, Gta7Progress progress) {
        this.world = world; this.progress = progress; respawn();
    }

    public void respawn() {
        npcs.clear(); cars.clear(); drops.clear(); tracers.clear();particles.clear();
        x = 90; y = .14; z = 77; yaw = 180; pitch = 0;
        health = progress.maxHealth(); weapon = 0; ammo = progress.magazineSize();
        jetFuel=progress.jetCapacity();jetting=false;lastActivity=0;world.resetWindows();
        wanted = kills = runXp = crimes = 0;
        velocityY = reload = attackCooldown = flash = hitMarker = hurtFlash = recoil = speed = accumulator = 0;
        time = dispatch = population = crimeTime = trafficHurt = walkCycle = 0;
        dead = crouching = false; grounded = true;
        notice = "NORTHSIDE / Walk through open doorways to explore interiors."; noticeUntil = 8;
        for (int i = 0; i < 85; i++) npcs.add(spawn(false, false));
        for (int i = 0; i < 8; i++) npcs.add(spawn(true, i == 0));
        for(Gta7World.Spawn resident:world.residents) {
            double[] point=freePoint(resident.x,resident.z);
            npcs.add(resident(resident,point));
        }
        for (int bz = 0; bz < Gta7World.BLOCKS; bz++) for (int bx = 0; bx < Gta7World.BLOCKS; bx++) {
            if ((bx + bz) % 2 == 0) cars.add(new Car(bx, bz, false, random.nextDouble() * 144, random.nextInt(6)));
            if ((bx + bz) % 3 == 0) cars.add(new Car(bx, bz, true, random.nextDouble() * 144, random.nextInt(6)));
        }
        progress.save();
    }

    public void advance(double seconds, Input input) {
        if (dead || !Double.isFinite(seconds) || seconds<=0 || input==null) return;
        pathBudget=2;lastPathRequests=0;
        accumulator += Math.max(0, Math.min(.1, seconds));
        final double step = 1.0 / 120;
        while (accumulator >= step && !dead) { tick(step, input); accumulator -= step; }
    }

    private void tick(double dt, Input input) {
        time += dt; dayTime=(dayTime+dt)%600;
        if(input.forward||input.back||input.left||input.right||input.jump||input.sneak||input.attack||input.aim)activity();
        flash = Math.max(0, flash - dt); hitMarker = Math.max(0, hitMarker - dt);
        hurtFlash = Math.max(0, hurtFlash - dt); recoil *= Math.exp(-dt * 15);
        attackCooldown = Math.max(input.attack ? -dt : 0, attackCooldown - dt);
        if (reload > 0) { reload = Math.max(0, reload - dt); if (reload == 0) ammo = progress.magazineSize(); }
        crouching = input.sneak || world.blocked(x, y, z, .28, 1.78);
        double forward = (input.forward ? 1 : 0) - (input.back ? 1 : 0);
        double strafe = (input.right ? 1 : 0) - (input.left ? 1 : 0);
        double length = Math.hypot(forward, strafe);
        speed = length > 0 ? progress.walkSpeed() * (crouching ? .42 : input.sprint && !input.aim ? 1.5 : 1) * (input.aim ? .65 : 1) : 0;
        if (length > 0) {
            double radians = Math.toRadians(yaw);
            double dx = (Math.sin(radians) * forward + Math.cos(radians) * strafe) / length * speed * dt;
            double dz = (-Math.cos(radians) * forward + Math.sin(radians) * strafe) / length * speed * dt;
            movePlayer(dx, dz);
            if (grounded) walkCycle += speed * dt * 1.9;
        }
        Gta7World.Ladder ladder=world.ladderAt(x,y+(input.sneak?-.03:.03),z);
        jetting=false;
        if(ladder!=null&&(input.jump||input.sneak)) {
            double climb=input.sneak?-2.8:2.8;
            double next=Math.max(ladder.bottom,Math.min(ladder.top,y+climb*dt));
            if(!world.blocked(x,next,z,.28,bodyHeight()))y=next;
            velocityY=0;grounded=false;
        } else {
            boolean wasGrounded=grounded;
            if (input.jump && grounded && !crouching) { velocityY = 6.6; grounded = false; }
            if(!wasGrounded&&input.jump&&!crouching&&jetFuel.signum()>0) {
                jetting=true;velocityY=Math.min(5.5,velocityY+28*dt);
                jetFuel=jetFuel.subtract(BigDecimal.valueOf(dt)).max(BigDecimal.ZERO);
            }
            velocityY -= 19 * dt;
            double nextY=y+velocityY*dt;
            if(velocityY>0){double capped=world.ceiling(x,z,y,.28,bodyHeight(),nextY);if(capped<nextY){nextY=capped;velocityY=0;}}
            double floor=world.support(x,z,y+.001,.28);
            if(velocityY<=0&&nextY<=floor) { y=floor;velocityY=0;grounded=true; }
            else if (!world.blocked(x,nextY,z,.28,bodyHeight())&&!carBlocked(x,nextY,z,.28,bodyHeight())) {y=nextY;grounded=false;}
            else {grounded=velocityY<0;velocityY=0;}
        }
        if(grounded)jetFuel=jetFuel.add(BigDecimal.valueOf(dt*1.8)).min(progress.jetCapacity());
        if (input.attack) attack();
        for (Iterator<Tracer> it = tracers.iterator(); it.hasNext();) { Tracer tracer = it.next(); tracer.life -= dt; if (tracer.life <= 0) it.remove(); }
        for(Iterator<Particle> it=particles.iterator();it.hasNext();){
            Particle p=it.next();p.life-=dt;if(p.life<=0){it.remove();continue;}
            p.vy-=9*dt;p.x+=p.vx*dt;p.y+=p.vy*dt;p.z+=p.vz*dt;
            if(p.y<.16){p.y=.16;p.vy=Math.abs(p.vy)*.22;p.vx*=.9;p.vz*=.9;}
        }
        updateCars(dt);
        for (Iterator<Npc> it = npcs.iterator(); it.hasNext();) {
            Npc npc = it.next();
            if (npc.health <= 0) { npc.death += dt; if (npc.death > 2.5) it.remove(); continue; }
            if(Math.hypot(npc.x-x,npc.z-z)<85)updateNpc(npc, dt);
            if (dead) break;
        }
        if(dead)return;
        collectDrops();
        if (time >= population) {
            population = time + 3;
            int civilians = 0; for (Npc npc : npcs) if (npc.kind==Kind.CIVILIAN && npc.health > 0) civilians++;
            if (civilians < 85) npcs.add(spawn(false, false));
            for(Gta7World.Spawn resident:world.residents) {
                if(Math.hypot(resident.x-x,resident.z-z)>75)continue;
                boolean present=false;
                for(Npc npc:npcs)if(npc.resident==resident&&npc.health>0){present=true;break;}
                if(!present){double[] point=freePoint(resident.x,resident.z);npcs.add(resident(resident,point));break;}
            }
        }
        if (wanted > 0 && time >= dispatch) {
            dispatch = time + 3.5;
            int cops = 0; for (Npc npc : npcs) if (npc.cop && npc.health > 0 && Math.hypot(npc.x-x,npc.z-z)<85) cops++;
            if (cops < 8 + wanted * 2) npcs.add(spawn(true, true));
        }
        if (wanted > 0 && time - crimeTime > 28) {
            boolean seen = false;
            for (Npc npc : npcs) if (npc.cop && npc.health > 0 && npc.seesPlayer && Math.hypot(npc.x-x,npc.z-z)<52) { seen = true; break; }
            if (!seen) { wanted--; crimes = wanted * 3; crimeTime = time - 16; if (wanted == 0) message("Search called off. Keep a low profile."); }
        }
    }

    public double eyeY() {
        return y + (crouching ? 1.08 : 1.62) + (grounded && speed > 0 ? Math.sin(walkCycle * 2) * .022 : 0);
    }
    public double bodyHeight() { return crouching ? 1.25 : 1.78; }
    public float healthRatio() { return health.max(BigDecimal.ZERO).divide(progress.maxHealth(), MathContext.DECIMAL32).min(BigDecimal.ONE).floatValue(); }
    public void equip(int slot) { activity(); if (slot == 0 || slot == 1) weapon = slot; }
    public void activity(){lastActivity=time;}
    public boolean hintsVisible(){return time<7||time-lastActivity>=25;}
    public double daylight(){return .5+.5*Math.cos(dayTime/600*Math.PI*2);}
    public String clock(){int minutes=(int)((12+dayTime/600*24)%24*60);return String.format(java.util.Locale.ROOT,"%02d:%02d",minutes/60,minutes%60);}
    public void useElevator(boolean down,boolean express){
        Gta7World.Elevator lift=world.elevatorAt(x,z);if(dead||lift==null)return;
        int floor=(int)Math.round((y-.14)/3.2);
        floor=express?(down?0:lift.floors-1):Math.max(0,Math.min(lift.floors-1,floor+(down?-1:1)));
        y=.14+floor*3.2;velocityY=0;grounded=true;activity();message("Elevator / Floor "+(floor+1));
    }
    public void startReload() {
        activity();
        if (!dead && weapon == 1 && ammo.compareTo(progress.magazineSize()) < 0 && reload <= 0) { reload = 2.1; message("Reloading AK47..."); }
    }
    public void message(String text) { notice = text; noticeUntil = time + 3.5; }

    private void movePlayer(double dx, double dz) {
        moveAxis(x+dx,z);moveAxis(x,z+dz);
    }
    private void moveAxis(double nx,double nz) {
        if(!world.blocked(nx,y,nz,.28,bodyHeight())&&!carBlocked(nx,y,nz,.28,bodyHeight())) {x=nx;z=nz;return;}
        // A 20 cm riser is walkable; high furniture and walls still block movement.
        double step=world.support(nx,nz,y+.23,.28);
        if(grounded&&step>y+.001&&step<=y+.23&&!world.blocked(nx,step+.001,nz,.28,bodyHeight())&&!carBlocked(nx,step,nz,.28,bodyHeight())) {x=nx;z=nz;y=step;}
    }
    private boolean carBlocked(double px, double py, double pz, double radius, double height) {
        for (Car car : cars) if (car.intersects(px, py, pz, radius, height)) return true;
        return false;
    }

    public double sceneRay(double px, double py, double pz, double dx, double dy, double dz, double range) {
        double result = world.ray(px, py, pz, dx, dy, dz, range);
        for (Car car : cars) result = Math.min(result, car.bounds().ray(px, py, pz, dx, dy, dz, result));
        return result;
    }

    public void attack() {
        if (dead || attackCooldown > 0 || weapon == 1 && reload > 0) return;
        if (weapon == 1 && ammo.signum() == 0) { startReload(); return; }
        attackCooldown += weapon == 0 ? .46 : progress.fireInterval();
        activity();
        recoil = weapon == 0 ? 1 : .6;
        if (weapon == 1) { ammo=ammo.subtract(BigInteger.ONE); flash = .055; }
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
        double dx = Math.sin(ry) * Math.cos(rp), dy = -Math.sin(rp), dz = -Math.cos(ry) * Math.cos(rp);
        double range = weapon == 0 ? 2.65 : 115;
        boolean penetrates=weapon==1 && random.nextDouble()<progress.penetrationChance();
        double closest = penetrates?range:sceneRay(x, eyeY(), z, dx, dy, dz, range);
        if(penetrates) {
            if(dy<0)closest=Math.min(closest,-eyeY()/dy);
            for(Car car:cars)closest=Math.min(closest,car.bounds().ray(x,eyeY(),z,dx,dy,dz,closest));
        }
        Npc hit = null;
        for (Npc npc : npcs) {
            if (npc.health <= 0) continue;
            double distance = npc.bounds().ray(x, eyeY(), z, dx, dy, dz, closest);
            if (distance < closest) { closest = distance; hit = npc; }
        }
        if(weapon==1&&shatter(x,eyeY(),z,dx,dy,dz,closest)>0)message("Glass shattered");
        if (weapon == 1) tracers.add(new Tracer(x + Math.cos(ry) * .18, eyeY() - .13, z + Math.sin(ry) * .18,
                x + dx * closest, eyeY() + dy * closest, z + dz * closest, false));
        if(weapon==1&&closest<range)burst(x+dx*closest,eyeY()+dy*closest,z+dz*closest,hit==null?0xE4C599:0xC3826A,hit==null?7:5,.035);
        if (hit != null) {
            boolean headshot = weapon == 1 && eyeY() + dy * closest > hit.y+hit.kind.height*.78;
            damageNpc(hit, progress.damage(weapon).multiply(BigDecimal.valueOf(headshot ? 1.7 : 1)));
            hitMarker = .18;
        }
        if (weapon == 1 && ammo.signum() == 0) startReload();
    }

    /** Only damage caused by this player creates a wanted level or XP drops. */
    public void damageNpc(Npc npc, BigDecimal amount) {
        if (dead || npc.health <= 0 || amount.signum() <= 0) return;
        npc.health = Math.max(0, npc.health - amount.min(BigDecimal.valueOf(npc.health)).doubleValue());
        npc.hurt = .22; npc.panic = 12;npc.provoked=true;
        if(npc.kind==Kind.COUNTER_TERRORIST)for(Npc other:npcs)if(other.kind==Kind.COUNTER_TERRORIST)other.provoked=true;
        boolean firstCrime = wanted == 0;
        crimes += npc.cop ? 2 : 1;
        wanted = Math.min(5, 1 + crimes / 3); crimeTime = time;
        if (firstCrime) {
            message("WANTED / Police are responding. Break their line of sight.");
            // The first responding unit is guaranteed to be nearby, even in an empty district.
            npcs.add(spawn(true, true)); dispatch = time + 4;
        }
        for (Npc other : npcs) if (!other.cop && Math.hypot(other.x - npc.x, other.z - npc.z) < 16) other.panic = 12;
        if (npc.health == 0) {
            kills++;
            if(drops.size()>=256){Drop oldest=drops.remove(0);progress.award(oldest.value);runXp+=oldest.value.intValue();progress.save();}
            drops.add(new Drop(npc.x,npc.y,npc.z,BigInteger.valueOf(npc.cop?55:npc.kind.aggressive?35:18)));
        }
    }

    public void hurt(double damage) {
        if (dead || damage <= 0 || !Double.isFinite(damage)) return;
        health = health.subtract(BigDecimal.valueOf(damage)).max(BigDecimal.ZERO);
        hurtFlash = .32;
        if (health.signum() == 0) {
            dead = true;
            // Loose XP is banked at death so a final kill can fund the upgrade screen.
            for (Drop drop : drops) {progress.award(drop.value);runXp+=drop.value.intValue();}
            drops.clear(); progress.save();
        }
    }

    private void collectDrops() {
        boolean changed = false;
        for (Iterator<Drop> it = drops.iterator(); it.hasNext();) {
            Drop drop = it.next();
            double distance = Math.hypot(drop.x - x, drop.z - z);
            if (distance < 1.65 && Math.abs(y-drop.y) < 2.3 && world.ray(x, y+.7, z, (drop.x-x)/Math.max(.001,distance), 0, (drop.z-z)/Math.max(.001,distance), distance) >= distance - .05) {
                progress.award(drop.value); runXp += drop.value.intValue(); message("+" + drop.value + " XP collected"); it.remove(); changed = true;
            }
        }
        if (changed) progress.save();
    }

    private Npc spawn(boolean cop, boolean nearby) {
        double[] point = null;
        for (int attempt = 0; attempt < 150; attempt++) {
            double[] candidate = world.sidewalks.get(random.nextInt(world.sidewalks.size()));
            double d = Math.hypot(candidate[0] - x, candidate[1] - z);
            if (d < (nearby ? 16 : 8) || nearby && d > 34 || !nearby && (candidate[0]<0||candidate[0]>180||candidate[1]<0||candidate[1]>180)) continue;
            point = candidate; break;
        }
        if (point == null) point = freePoint(x+18,z+18);
        double[] safe=freePoint(point[0],point[1]);
        Kind kind=cop?(Gta7Regions.at(x,z)==Gta7Regions.Region.SNOW?Kind.ARCTIC_COP:Gta7Regions.at(x,z)==Gta7Regions.Region.DESERT?Kind.SHERIFF:Kind.COP):Kind.CIVILIAN;
        Npc npc = new Npc(safe[0],safe[1],kind,random.nextInt(8));
        npc.think = time + random.nextDouble() * .5;
        npc.shoot = time + 1.5;
        npc.targetX = npc.x; npc.targetZ = npc.z;
        return npc;
    }

    private Npc resident(Gta7World.Spawn resident,double[] point){
        Npc npc=new Npc(point[0],point[1],resident.kind,random.nextInt(8));npc.resident=resident;
        npc.think=time+random.nextDouble()*.6;npc.shoot=time+1.5;return npc;
    }
    private List<double[]> route(double fromX,double fromZ,double toX,double toZ){
        pathBudget--;lastPathRequests++;return world.path(fromX,fromZ,toX,toZ,4096);
    }
    private int shatter(double px,double py,double pz,double dx,double dy,double dz,double limit){
        int count=0;
        for(Gta7World.Window window:world.windows)if(!window.broken){
            double d=window.bounds.ray(px,py,pz,dx,dy,dz,limit);
            if(d<limit){window.broken=true;count++;burst(px+dx*d,py+dy*d,pz+dz*d,0xA9DCE6,12,.065);}
        }
        return count;
    }
    private void burst(double x,double y,double z,int color,int count,double size){
        for(int i=0;i<count;i++){
            if(particles.size()>=256)particles.remove(0);
            particles.add(new Particle(x,y,z,(effectsRandom.nextDouble()-.5)*3,1+effectsRandom.nextDouble()*2,(effectsRandom.nextDouble()-.5)*3,color,size,.35+effectsRandom.nextDouble()*.8,effectsRandom.nextInt(360)));
        }
    }
    private double[] freePoint(double px,double pz) {
        for(int radius=0;radius<12;radius++)for(int i=0;i<12;i++) {
            double nx=px+Math.cos(i*Math.PI/6)*radius,nz=pz+Math.sin(i*Math.PI/6)*radius;
            if(!world.blocked(nx,.14,nz,.34,1.9))return new double[]{nx,nz};
        }
        return new double[]{90,77};
    }
    public boolean hostile(Npc npc) {return npc.kind.aggressive||npc.cop&&wanted>0||npc.provoked&&npc.kind==Kind.COUNTER_TERRORIST;}
    private void updateNpc(Npc npc, double dt) {
        npc.hurt=Math.max(0,npc.hurt-dt);npc.panic=Math.max(0,npc.panic-dt);
        double distance=Math.hypot(npc.x-x,npc.z-z);
        boolean pursuing=hostile(npc)&&distance<65;
        double eye=npc.y+npc.kind.height*.8;
        if(time>=npc.think) {
            npc.think=time+.4+random.nextDouble()*.2;npc.seesPlayer=false;
            if(pursuing&&distance<52) {
                double dy=eyeY()-eye,len=Math.sqrt(distance*distance+dy*dy);
                npc.seesPlayer=len>.001&&sceneRay(npc.x,eye,npc.z,(x-npc.x)/len,dy/len,(z-npc.z)/len,len)>=len-.15;
            }
            if(pursuing) {
                npc.targetX=x;npc.targetZ=z;
                if(time>=npc.repath&&(!npc.seesPlayer||distance>12||npc.kind.melee)) {
                    if(pathBudget>0){npc.route=route(npc.x,npc.z,x,z);npc.routeIndex=0;npc.repath=time+2+random.nextDouble();}
                    else npc.think=time+.04;
                }
            } else if(pathBudget>0&&time>=npc.repath&&(npc.routeIndex>=npc.route.size()||Math.hypot(npc.targetX-npc.x,npc.targetZ-npc.z)<1)) {
                double[] target=chooseDestination(npc);npc.targetX=target[0];npc.targetZ=target[1];
                npc.route=route(npc.x,npc.z,npc.targetX,npc.targetZ);npc.routeIndex=0;npc.repath=time+4;
            }
        }
        boolean firing=pursuing&&npc.seesPlayer&&distance<(npc.kind.melee?1.65:34);
        if(firing) {
            npc.yaw=Math.toDegrees(Math.atan2(x-npc.x,-(z-npc.z)));
            if(time>=npc.shoot&&Math.abs(y-npc.y)<(npc.kind.melee?1.8:1000)) {
                npc.shoot=time+(npc.kind.melee?.7:.85)+random.nextDouble()*.5;
                double dy=eyeY()-.12-eye,len=Math.sqrt(distance*distance+dy*dy);
                if(len>.001&&sceneRay(npc.x,eye,npc.z,(x-npc.x)/len,dy/len,(z-npc.z)/len,len)>=len-.1) {
                    if(!npc.kind.melee) {
                        shatter(npc.x,eye,npc.z,(x-npc.x)/len,dy/len,(z-npc.z)/len,len);
                        tracers.add(new Tracer(npc.x,eye,npc.z,x,eyeY()-.12,z,true));
                    }
                    if(npc.kind.melee||random.nextDouble()<Math.max(.22,.86-distance/65-(speed>3?.18:0)))hurt(npc.kind==Kind.POLAR_BEAR?19:npc.kind.melee?12:7+wanted*1.5);
                }
            }
        }
        double tx=npc.targetX,tz=npc.targetZ;
        if(npc.routeIndex<npc.route.size()) {
            double[] target=npc.route.get(npc.routeIndex);tx=target[0];tz=target[1];
            if(Math.hypot(tx-npc.x,tz-npc.z)<.22)npc.routeIndex++;
        }
        double dx=tx-npc.x,dz=tz-npc.z,len=Math.hypot(dx,dz);
        if(len>.12&&!(firing&&distance<(npc.kind.melee?1.2:12))) {
            double step=Math.min(len,(pursuing?3.25:npc.panic>0?3.6:1.1+npc.variant*.06)*dt);
            double nx=npc.x+dx/len*step,nz=npc.z+dz/len*step;
            double floor=world.support(nx,nz,npc.y+.23,.27);
            if(!world.blocked(nx,floor+.001,npc.z,.27,npc.kind.height)&&!carBlocked(nx,floor,npc.z,.27,npc.kind.height))npc.x=nx;
            if(!world.blocked(npc.x,floor+.001,nz,.27,npc.kind.height)&&!carBlocked(npc.x,floor,nz,.27,npc.kind.height))npc.z=nz;
            double support=world.support(npc.x,npc.z,npc.y+.23,.27);
            npc.y=Math.max(support,npc.y-dt*6);
            if(!firing)npc.yaw=Math.toDegrees(Math.atan2(dx,-dz));npc.walk+=step*5;
        }
    }

    private double[] chooseDestination(Npc npc) {
        double[] best = new double[]{npc.homeX,npc.homeZ};
        double score = Double.NEGATIVE_INFINITY;
        for (double[] point : world.sidewalks) {
            double trip = Math.hypot(point[0] - npc.x, point[1] - npc.z);
            if (trip < 3 || trip > 30 || Math.hypot(point[0]-npc.homeX,point[1]-npc.homeZ)>45) continue;
            double value = npc.panic > 0 ? Math.hypot(point[0] - x, point[1] - z) - trip * .3 : -trip + random.nextDouble() * 20;
            if (value > score) { score = value; best = point; }
        }
        return best;
    }

    private void updateCars(double dt) {
        for (Car car : cars) {
            double oldRoute = car.route;
            double nearCorner = car.route % car.side;
            boolean red = ((int)(time / 7) + (car.segment() % 2)) % 2 == 0;
            double desired = red && nearCorner > car.side - 7 && nearCorner < car.side - 4 ? 0 : 5.5 + car.color * .35;
            for (Car other : cars) if (other != car) {
                double dx = other.x - car.x, dz = other.z - car.z;
                double forward = dx * Math.sin(Math.toRadians(car.yaw)) - dz * Math.cos(Math.toRadians(car.yaw));
                double side = dx * Math.cos(Math.toRadians(car.yaw)) + dz * Math.sin(Math.toRadians(car.yaw));
                if (forward > 0 && forward < 6 && Math.abs(side) < 1.7) desired = 0;
            }
            car.speed += Math.max(-dt * 12, Math.min(dt * 3, desired - car.speed));
            car.route = (car.route + car.speed * dt) % (car.side * 4); car.locate();
            if (car.intersects(x, y, z, .32, bodyHeight())) {
                if (car.speed > 2 && time > trafficHurt) { hurt(22); trafficHurt = time + 1; }
                car.route = oldRoute; car.locate(); car.speed = 0;
            }
            for (Npc npc : npcs) if (npc.health > 0 && car.intersects(npc.x, npc.y, npc.z, .3, npc.kind.height)) {
                car.route = oldRoute; car.locate(); car.speed = 0; break;
            }
        }
    }

    public String district() {
        for (Gta7World.Building building : world.buildings) if (building.contains(x,z)) return building.name;
        int bx = (int)x / Gta7World.BLOCK, bz = (int)z / Gta7World.BLOCK;
        if(Gta7Regions.at(x,z)!=Gta7Regions.Region.CITY)return Gta7Regions.at(x,z).title.toUpperCase(java.util.Locale.ROOT);
        return bx == 2 && bz == 2 ? "NORTHSIDE / CIVIC GARDENS" : bz <= 1 ? "NORTHSIDE / OLD TOWN" : bx >= 3 ? "NORTHSIDE / MARKET DISTRICT" : "NORTHSIDE / RESIDENTIAL";
    }
    public static final class Input {
        public boolean forward, back, left, right, jump, sneak, sprint, attack, aim;
    }
    public enum Kind {
        CIVILIAN(false,false,false,1.8,0), COP(true,false,false,1.8,0x344D65),
        COWBOY(false,false,false,1.8,0xA17C50), SHERIFF(true,false,false,1.8,0xA88E60), OUTLAW(false,true,false,1.8,0x5A4843),
        INUIT(false,false,false,1.8,0xA88B72), ARCTIC_COP(true,false,false,1.8,0x456E89),
        PENGUIN(false,false,true,.85,0x263B46), POLAR_BEAR(false,true,true,1.5,0xE7E9DE),
        GOBLIN(false,true,true,1.35,0x746240), WIZARD(false,false,false,1.8,0x7C659F), WIZARD_GUARD(true,false,false,1.8,0x4B7297),
        STORMTROOPER(false,true,false,1.8,0xE5E8DF), IMPERIAL_OFFICER(false,true,false,1.8,0x68705F),
        VADER(false,true,true,1.95,0x242D34), IMPERIAL_DROID(false,true,false,1.6,0x404B52),
        TERRORIST(false,true,false,1.8,0xB49773), COUNTER_TERRORIST(false,false,false,1.8,0x3D566D),
        CANDY_KID(false,true,false,1.15,0xF193BD), VISITOR(false,false,false,1.8,0x51636C);
        public final boolean law,aggressive,melee;public final double height;public final int color;
        Kind(boolean law,boolean aggressive,boolean melee,double height,int color){this.law=law;this.aggressive=aggressive;this.melee=melee;this.height=height;this.color=color;}
    }
    public static final class Npc {
        public double x,y=.14,z,yaw,health,maxHealth,walk,panic,hurt,death,think,shoot,repath,targetX,targetZ;
        public final double homeX,homeZ;
        public Gta7World.Spawn resident;
        public final boolean cop; public final int variant; public final Kind kind;
        public boolean seesPlayer,provoked; public List<double[]> route=Collections.emptyList(); public int routeIndex;
        public Npc(double x,double z,boolean cop,int variant){this(x,z,cop?Kind.COP:Kind.CIVILIAN,variant);}
        public Npc(double x,double z,Kind kind,int variant) {
            this.x=homeX=targetX=x;this.z=homeZ=targetZ=z;this.kind=kind;this.cop=kind.law;this.variant=variant;
            health=maxHealth=kind==Kind.POLAR_BEAR?230:cop?150:100;
        }
        public Gta7Bounds bounds() {double r=kind==Kind.POLAR_BEAR?.62:.32;return new Gta7Bounds(x-r,y,z-r,x+r,y+kind.height,z+r);}
    }
    public static final class Drop {
        public final double x,y,z; public final BigInteger value;
        public Drop(double x,double z,BigInteger value){this(x,.14,z,value);}
        public Drop(double x,double y,double z,BigInteger value){this.x=x;this.y=y;this.z=z;this.value=value;}
    }
    public static final class Particle {
        public double x,y,z,vx,vy,vz,life;public final double size;public final int color,seed;
        Particle(double x,double y,double z,double vx,double vy,double vz,int color,double size,double life,int seed){
            this.x=x;this.y=y;this.z=z;this.vx=vx;this.vy=vy;this.vz=vz;this.color=color;this.size=size;this.life=life;this.seed=seed;
        }
    }
    public static final class Tracer {
        public final double x,y,z,xx,yy,zz; public final boolean police; public double life = .075;
        Tracer(double x,double y,double z,double xx,double yy,double zz,boolean police) { this.x=x;this.y=y;this.z=z;this.xx=xx;this.yy=yy;this.zz=zz;this.police=police; }
    }
    public static final class Car {
        public final int bx,bz,color; public final boolean reverse; public final double side;
        public double route, x, z, yaw, speed;
        Car(int bx,int bz,boolean reverse,double route,int color) {
            this.bx=bx;this.bz=bz;this.reverse=reverse;this.color=color;side=Gta7World.BLOCK+(reverse?3.6:-3.6);this.route=route%(side*4);locate();
        }
        public int segment() { return (int)(route/side); }
        void locate() {
            double lane=reverse?-1.8:1.8, left=bx*Gta7World.BLOCK+lane, top=bz*Gta7World.BLOCK+lane;
            double t=route%side;
            int segment=segment();
            if (!reverse) {
                if(segment==0){x=left+t;z=top;yaw=90;} else if(segment==1){x=left+side;z=top+t;yaw=180;}
                else if(segment==2){x=left+side-t;z=top+side;yaw=270;} else{x=left;z=top+side-t;yaw=0;}
            } else {
                if(segment==0){x=left;z=top+t;yaw=180;} else if(segment==1){x=left+t;z=top+side;yaw=90;}
                else if(segment==2){x=left+side;z=top+side-t;yaw=0;} else{x=left+side-t;z=top;yaw=270;}
            }
        }
        public boolean intersects(double px,double py,double pz,double radius,double height){
            boolean horizontal=(Math.round(yaw)%180)!=0;double rx=horizontal?2.05:.9,rz=horizontal?.9:2.05;
            return px+radius>x-rx&&px-radius<x+rx&&pz+radius>z-rz&&pz-radius<z+rz&&py+height>.1&&py<1.55;
        }
        public Gta7Bounds bounds() {
            boolean horizontal = (Math.round(yaw)%180)!=0;
            return new Gta7Bounds(x-(horizontal?2.05:.9),.1,z-(horizontal?.9:2.05),x+(horizontal?2.05:.9),1.55,z+(horizontal?.9:2.05));
        }
    }
}
