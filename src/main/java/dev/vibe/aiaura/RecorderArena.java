package dev.vibe.aiaura;

import java.io.IOException;
import java.util.*;

/** Standalone local practice simulation. It has no Minecraft world, entity ID or network handler. */
public final class RecorderArena extends ArenaPhysics.Body {
    public static final int FORWARD=1,BACK=2,LEFT=4,RIGHT=8,JUMP=16,SNEAK=32,SPRINT=64,ATTACK=128,USE=256;
    public static final class Pillar {public final double x,z,width,height;Pillar(double x,double z,double width,double height){this.x=x;this.z=z;this.width=width;this.height=height;}}
    public static final class Enemy extends ArenaPhysics.Body {
        public long id;public double health=20;public int age,deadAge,hurtTicks,immuneTicks,nextAttack,swingTicks,nextPlan,routeIndex;double phase,lastDamage;List<net.minecraft.util.Vec3> route=Collections.emptyList();
    }
    public final List<Enemy> enemies=new ArrayList<>();
    public final List<Pillar> pillars=ArenaNavigation.trainingMap();
    public float pitch,previousPitch,sensitivity=.5F,fov=70;
    public double aspect=16/9D,reach=3;
    public boolean preview;
    public int wave,kills,hits,misses,damageHits,receivedHits,keys,slot,mouseWheel,ticks,playerHurtTicks,playerImmuneTicks,perspective;
    public double playerHealth=20,damageDealt,damageTaken,lastDamage;
    public float swing;
    public float previousSwing,limbSwing,limbAmount,previousLimbAmount;
    private int armSwingTick;private boolean swinging;
    public Enemy selected;
    public double sparkX,sparkY,sparkZ;public int sparkTicks;
    private final AuraLearning learning;
    private final Random random;
    private final AuraFeatures.Memory memory=new AuraFeatures.Memory();
    private final AuraBrain previewBrain=new AuraBrain();
    private AuraDataset.Frame pending,acquisition,heldFrame;
    private Enemy pendingEnemy;
    private long session,originNanos,lastPressNanos,lastPressTarget=-1,heldNanos,idCounter,nextWave=20;
    private int pendingYaw,pendingPitch,presses,missCooldown,sprintTap;private boolean previousForward;
    public RecorderArena(AuraLearning learning,long seed){this.learning=learning;random=new Random(seed);}
    public void begin(boolean preview)throws IOException{
        this.preview=preview;x=y=z=vx=vz=fallDistance=0;vy=-.0784000015258789;previousX=previousY=previousZ=0;yaw=pitch=previousYaw=previousPitch=0;grounded=true;sprint=horizontalCollision=previousForward=swinging=false;jumpCooldown=sprintTap=armSwingTick=0;swing=previousSwing=limbSwing=limbAmount=previousLimbAmount=0;wave=kills=hits=misses=damageHits=receivedHits=ticks=keys=playerHurtTicks=playerImmuneTicks=0;playerHealth=20;damageDealt=damageTaken=lastDamage=0;enemies.clear();selected=pendingEnemy=null;memory.reset();previewBrain.reset();acquisition=heldFrame=pending=null;pendingYaw=pendingPitch=presses=mouseWheel=0;lastPressNanos=heldNanos=0;lastPressTarget=-1;nextWave=20;originNanos=System.nanoTime();session=preview?0:learning.begin();captureNext();
    }
    public void finish(){if(!preview){releaseAttack();commit();learning.finish();}memory.reset();previewBrain.reset();}
    public void input(int type,int code,boolean down,int dx,int dy,int wheel)throws IOException{
        if(preview)return;long nanos=System.nanoTime();learning.input(nanos-originNanos,type,code,down,dx,dy,wheel);mouseWheel+=wheel;
    }
    public void look(int dx,int downCounts){
        if(preview)return;pendingYaw+=dx;float beforeYaw=yaw,beforePitch=pitch;
        yaw=MouseMath.angle(yaw,dx,sensitivity);pitch=MouseMath.pitch(pitch,downCounts,sensitivity);
        pendingPitch+=Math.round((pitch-beforePitch)/MouseMath.step(sensitivity));
        if(acquisition!=null&&acquisition.mask[4]==0&&pendingEnemy!=null){
            double[] a=AuraFeatures.angles(x,y+1.62,z,pendingEnemy.x,pendingEnemy.y+1.08,pendingEnemy.z);
            double old=Math.hypot(MouseMath.wrap(a[0]-beforeYaw),a[1]-beforePitch),current=Math.hypot(MouseMath.wrap(a[0]-yaw),a[1]-pitch);
            if(current+.01<old&&(Math.abs(dx)+Math.abs(downCounts)>0)){acquisition.y[4]=(float)(eventTime()-acquisition.time)/1000;acquisition.mask[4]=1;}
        }
    }
    private long eventTime(){return Math.max(ticks*50L,Math.round((System.nanoTime()-originNanos)/1e6));}
    public void attackDown(){
        if(preview)return;long now=System.nanoTime();presses++;
        if(pending!=null){pending.press=true;if(lastPressNanos!=0&&pending.target==lastPressTarget){double interval=(now-lastPressNanos)/1e6;if(interval>=5&&interval<=10000){pending.y[3]=(float)Math.log(interval/100);pending.mask[3]=1;}}heldFrame=pending;heldNanos=now;lastPressTarget=pending.target;}
        lastPressNanos=now;memory.lastClick=eventTime();if(acquisition!=null&&acquisition.mask[5]==0){acquisition.y[5]=(float)(eventTime()-acquisition.time)/1000;acquisition.mask[5]=1;}attack();
    }
    public void releaseAttack(){if(heldFrame!=null&&heldNanos!=0){heldFrame.y[9]=(float)Math.log(Math.max(1,(System.nanoTime()-heldNanos)/1e6)/50);heldFrame.mask[9]=1;}heldFrame=null;heldNanos=0;}
    public void tick(int input,double aimBoost,double clickBoost){
        keys=input;commit();previousX=x;previousY=y;previousZ=z;previousYaw=yaw;previousPitch=pitch;if(playerHurtTicks>0)playerHurtTicks--;if(playerImmuneTicks>0)playerImmuneTicks--;
        if(preview){selected=choose();AuraFeatures.Observation obs=observation(selected);if(obs.visible&&learning.model()!=null){int[] count=previewBrain.advance(learning.model(),obs,ticks*50L,aimBoost,clickBoost);yaw=MouseMath.angle(yaw,count[0],sensitivity);pitch=MouseMath.pitch(pitch,count[1],sensitivity);if(previewBrain.due(ticks*50L))for(int n=previewBrain.poll(ticks*50L);n>0;n--)attack();}else previewBrain.reset();}
        movePlayer();for(Enemy enemy:enemies)moveEnemy(enemy);for(Enemy enemy:enemies)if(enemy.health>0)ArenaPhysics.push(this,enemy);enemies.removeIf(e->e.health<=0&&e.deadAge>16);
        if(missCooldown>0)missCooldown--;if(sparkTicks>0)sparkTicks--;previousSwing=swing;if(swinging){if(++armSwingTick>=6){armSwingTick=0;swinging=false;}}else armSwingTick=0;swing=armSwingTick/6F;
        previousLimbAmount=limbAmount;limbAmount+=(Math.min(1,(float)Math.hypot(x-previousX,z-previousZ)*4)-limbAmount)*.4F;limbSwing+=limbAmount;ticks++;
        boolean alive=false;for(Enemy e:enemies)if(e.health>0)alive=true;
        if(!alive&&ticks>=nextWave){spawn();nextWave=Long.MAX_VALUE;}else if(!alive&&nextWave==Long.MAX_VALUE)nextWave=ticks+30;
        captureNext();
    }
    private void commit(){
        if(preview||pending==null)return;pending.y[0]=pendingYaw/128F;pending.y[1]=pendingPitch/128F;pending.mask[0]=pending.mask[1]=1;pending.keys=keys;pending.slot=slot;pending.wheel=mouseWheel;pending.press|=presses>0;pending.pressCount=presses;
        if(pendingEnemy!=null){double[] hit=ray(pendingEnemy,10);if(hit!=null){pending.y[6]=(float)((hit[0]-pendingEnemy.x+.3)/.6);pending.y[7]=(float)((hit[1]-pendingEnemy.y)/1.8);pending.y[8]=(float)((hit[2]-pendingEnemy.z+.3)/.6);for(int j=6;j<=8;j++)pending.mask[j]=1;memory.pointX=pending.y[6];memory.pointY=pending.y[7];memory.pointZ=pending.y[8];}}
        learning.append(pending);memory.motion(pendingYaw,pendingPitch);pending=null;pendingYaw=pendingPitch=presses=mouseWheel=0;
    }
    private void captureNext(){
        selected=choose();if(preview)return;AuraFeatures.Observation observation=observation(selected);pending=new AuraDataset.Frame();pending.session=session;pending.time=ticks*50L;pending.target=observation.targetId;pending.sensitivity=sensitivity;
        boolean changed=memory.target!=observation.targetId;pending.x=AuraFeatures.build(observation,memory,pending.time);pendingEnemy=selected;if(changed)acquisition=selected==null?null:pending;
    }
    public AuraFeatures.Observation observation(Enemy enemy){
        AuraFeatures.Observation o=new AuraFeatures.Observation();o.eyeX=x;o.eyeY=y+1.62;o.eyeZ=z;o.yaw=yaw;o.pitch=pitch;o.vx=vx;o.vy=vy;o.vz=vz;o.grounded=grounded;o.sprint=sprint;o.leftHeld=(keys&ATTACK)!=0;o.jump=(keys&JUMP)!=0;o.sensitivity=sensitivity;o.reach=reach;
        o.selfHealth=playerHealth/20D;o.selfHurt=playerHurtTicks;if(enemy!=null){o.targetId=enemy.id;o.targetX=enemy.x;o.targetY=enemy.y;o.targetZ=enemy.z;o.targetVx=enemy.x-enemy.previousX;o.targetVy=enemy.y-enemy.previousY;o.targetVz=enemy.z-enemy.previousZ;o.visible=enemy.health>0;o.targetHealth=enemy.health/20D;o.targetHurt=enemy.hurtTicks;o.targetGrounded=enemy.grounded;o.targetYaw=enemy.yaw;}return o;
    }
    private Enemy choose(){
        Enemy best=null;double score=Double.MAX_VALUE;
        for(Enemy enemy:enemies){if(enemy.health<=0||!visible(enemy))continue;double[] angle=AuraFeatures.angles(x,y+1.62,z,enemy.x,enemy.y+1.08,enemy.z);double next=Math.hypot(MouseMath.wrap(angle[0]-yaw),angle[1]-pitch)+(Math.hypot(enemy.x-x,enemy.z-z)*2);if(enemy==selected)next*=.8;if(next<score){score=next;best=enemy;}}
        return best;
    }
    public boolean visible(Enemy enemy){AuraFeatures.Observation o=observation(enemy);if(!AuraFeatures.inView(o,fov,aspect))return false;double distance=Math.sqrt((enemy.x-x)*(enemy.x-x)+(enemy.y+1.08-y-1.62)*(enemy.y+1.08-y-1.62)+(enemy.z-z)*(enemy.z-z));return !occluded(enemy.x,enemy.y+1.08,enemy.z,distance);}
    private boolean occluded(double ex,double ey,double ez,double distance){return occludedBetween(x,y+1.62,z,ex,ey,ez);}
    private boolean occludedBetween(double sx,double sy,double sz,double ex,double ey,double ez){for(Pillar p:pillars)if(intersect(sx,sy,sz,ex,ey,ez,p.x-p.width/2,0,p.z-p.width/2,p.x+p.width/2,p.height,p.z+p.width/2)!=null)return true;return false;}
    private void attack(){
        if(missCooldown>0)return;if(!swinging||armSwingTick>=3||armSwingTick<0){armSwingTick=-1;swinging=true;}Enemy hitEnemy=null;double[] point=null;double best=Double.MAX_VALUE;
        for(Enemy enemy:enemies){if(enemy.health<=0)continue;double[] hit=ray(enemy,reach,.1);if(hit!=null){double d=Math.sqrt((hit[0]-x)*(hit[0]-x)+(hit[1]-y-1.62)*(hit[1]-y-1.62)+(hit[2]-z)*(hit[2]-z));if(d<best&&!occluded(hit[0],hit[1],hit[2],d)){hitEnemy=enemy;best=d;point=hit;}}}
        if(hitEnemy==null){misses++;missCooldown=10;return;}hits++;sparkX=point[0];sparkY=point[1];sparkZ=point[2];sparkTicks=8;
        double damage=fallDistance>0&&!grounded?1.5:1;boolean fresh=hitEnemy.immuneTicks<=10;
        if(!fresh&&damage<=hitEnemy.lastDamage)return;double applied=fresh?damage:damage-hitEnemy.lastDamage;hitEnemy.lastDamage=damage;hitEnemy.health-=applied;damageDealt+=applied;damageHits++;
        if(fresh){hitEnemy.hurtTicks=10;hitEnemy.immuneTicks=20;ArenaPhysics.knockback(hitEnemy,x,z);}
        if(sprint){double rad=Math.toRadians(yaw);hitEnemy.vx-=Math.sin(rad)*.5;hitEnemy.vy+=.1;hitEnemy.vz+=Math.cos(rad)*.5;vx*=.6;vz*=.6;sprint=false;}
        if(hitEnemy.health<=0)kills++;
    }
    private double[] ray(Enemy e,double length){return ray(e,length,0);}
    private double[] ray(Enemy e,double length,double border){double yawRad=Math.toRadians(yaw),pitchRad=Math.toRadians(pitch);return intersect(x,y+1.62,z,x-Math.sin(yawRad)*Math.cos(pitchRad)*length,y+1.62-Math.sin(pitchRad)*length,z+Math.cos(yawRad)*Math.cos(pitchRad)*length,e.x-.3-border,e.y-border,e.z-.3-border,e.x+.3+border,e.y+1.8+border,e.z+.3+border);}
    public static double[] intersect(double sx,double sy,double sz,double ex,double ey,double ez,double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        double[] start={sx,sy,sz},delta={ex-sx,ey-sy,ez-sz},min={minX,minY,minZ},max={maxX,maxY,maxZ};double low=0,high=1;
        for(int axis=0;axis<3;axis++){if(Math.abs(delta[axis])<1e-10){if(start[axis]<min[axis]||start[axis]>max[axis])return null;}else{double a=(min[axis]-start[axis])/delta[axis],b=(max[axis]-start[axis])/delta[axis];if(a>b){double temp=a;a=b;b=temp;}low=Math.max(low,a);high=Math.min(high,b);if(low>high)return null;}}
        return new double[]{sx+delta[0]*low,sy+delta[1]*low,sz+delta[2]*low};
    }
    private void movePlayer(){
        float forward=((keys&FORWARD)!=0?1:0)-((keys&BACK)!=0?1:0),strafe=((keys&LEFT)!=0?1:0)-((keys&RIGHT)!=0?1:0);boolean sneak=(keys&SNEAK)!=0;
        if(sprintTap>0)sprintTap--;if(forward>0&&!previousForward){if(sprintTap>0&&!sneak)sprint=true;else sprintTap=7;}previousForward=forward>0;
        if((keys&SPRINT)!=0&&forward>0&&!sneak)sprint=true;if(forward<=0||sneak||horizontalCollision)sprint=false;
        ArenaPhysics.tick(this,strafe,forward,(keys&JUMP)!=0,sneak,pillars);
    }
    private void spawn(){
        wave++;int count=Math.min(5,1+(wave-1)/2);for(int i=0;i<count;i++){Enemy e=new Enemy();e.id=++idCounter;for(int attempt=0;attempt<32;attempt++){double angle=Math.toRadians(yaw+(i==0?random.nextDouble()*70-35:random.nextDouble()*360-180)),radius=6+random.nextDouble()*5;e.x=Math.max(-22,Math.min(22,x-Math.sin(angle)*radius));e.z=Math.max(-22,Math.min(22,z+Math.cos(angle)*radius));if(attempt==0&&(wave%2==0||i%2==1)){Pillar p=pillars.get(random.nextInt(pillars.size()));e.x=p.x;e.z=p.z;}e.y=ArenaNavigation.height(e.x,e.z,pillars);boolean clear=true;for(net.minecraft.util.AxisAlignedBB box:ArenaPhysics.geometry(pillars))if(box.intersectsWith(e.box())){clear=false;break;}if(clear)break;if(attempt==31){e.x=0;e.z=4;e.y=0;}}e.previousX=e.x;e.previousY=e.y;e.previousZ=e.z;e.vy=-.0784000015258789;e.phase=random.nextDouble()*6.28;e.nextAttack=ticks+6+random.nextInt(8);enemies.add(e);}
    }
    private void moveEnemy(Enemy e){
        e.age++;if(e.hurtTicks>0)e.hurtTicks--;if(e.immuneTicks>0)e.immuneTicks--;if(e.swingTicks>0)e.swingTicks--;e.previousX=e.x;e.previousY=e.y;e.previousZ=e.z;e.previousYaw=e.yaw;if(e.health<=0){e.deadAge++;return;}
        double dx=x-e.x,dz=z-e.z,distance=Math.hypot(dx,dz),moveX=x,moveZ=z,nextHeight=y;boolean routing=distance>3||Math.abs(y-e.y)>.6||e.horizontalCollision;
        if(routing){if(ticks>=e.nextPlan){e.route=ArenaNavigation.route(e.x,e.z,x,z,pillars);e.routeIndex=Math.min(1,e.route.size());e.nextPlan=ticks+10+(int)(e.id%3);}
            while(e.routeIndex<e.route.size()){net.minecraft.util.Vec3 p=e.route.get(e.routeIndex);if(Math.hypot(p.xCoord-e.x,p.zCoord-e.z)>.32||e.y<p.yCoord-.15)break;e.routeIndex++;}
            if(e.routeIndex<e.route.size()){net.minecraft.util.Vec3 p=e.route.get(e.routeIndex);moveX=p.xCoord;moveZ=p.zCoord;nextHeight=p.yCoord;}}
        float desired=(float)Math.toDegrees(Math.atan2(moveZ-e.z,moveX-e.x))-90;e.yaw+=Math.max(-18,Math.min(18,MouseMath.wrap(desired-e.yaw)));
        e.sprint=distance>4&&!routing;float forward=routing?1:distance>2.15?1:distance<1.35?-.35F:0,strafe=routing?0:(float)Math.sin(ticks*.045+e.phase)*.45F;
        ArenaPhysics.tick(e,strafe,forward,e.horizontalCollision||nextHeight>e.y+.45&&Math.hypot(moveX-e.x,moveZ-e.z)<1.6||e.age>20&&random.nextInt(170)==0,false,pillars);
        if(ticks<e.nextAttack)return;e.nextAttack=ticks+3+random.nextInt(5);
        double[] angle=AuraFeatures.angles(e.x,e.y+1.62,e.z,x,y+1.08,z);double vertical=y+1.08-e.y-1.62,length=Math.sqrt(dx*dx+vertical*vertical+dz*dz);if(length<1e-6)return;
        double[] contact=intersect(e.x,e.y+1.62,e.z,e.x+dx/length*3,e.y+1.62+vertical/length*3,e.z+dz/length*3,x-.4,y-.1,z-.4,x+.4,y+1.9,z+.4);
        if(contact==null||Math.abs(MouseMath.wrap(angle[0]-e.yaw))>35||occludedBetween(e.x,e.y+1.62,e.z,contact[0],contact[1],contact[2]))return;
        e.swingTicks=6;double damage=e.fallDistance>0&&!e.grounded?1.5:1;boolean fresh=playerImmuneTicks<=10;if(!fresh&&damage<=lastDamage)return;double applied=fresh?damage:damage-lastDamage;lastDamage=damage;damageTaken+=applied;receivedHits++;playerHealth=20;
        if(fresh){playerHurtTicks=10;playerImmuneTicks=20;ArenaPhysics.knockback(this,e.x,e.z);}
        if(e.sprint){double rad=Math.toRadians(e.yaw);vx-=Math.sin(rad)*.5;vy+=.1;vz+=Math.cos(rad)*.5;e.vx*=.6;e.vz*=.6;e.sprint=false;}
    }
    public float cameraYaw(float partial){return preview?previousYaw+(float)MouseMath.wrap(yaw-previousYaw)*partial:yaw;}
    public float cameraPitch(float partial){return preview?previousPitch+(pitch-previousPitch)*partial:pitch;}
    public void cyclePerspective(){perspective=(perspective+1)%3;}
    public float swingProgress(float partial){float delta=swing-previousSwing;if(delta<0)delta++;return previousSwing+delta*partial;}
}
