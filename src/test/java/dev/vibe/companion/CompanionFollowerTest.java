package dev.vibe.companion;

import dev.vibe.path.PathTestFixture;
import net.minecraft.init.Blocks;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompanionFollowerTest {
    @Test public void strongHitsDoNotSnapBackAtTheFollowRadius()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            f.floor(-27,27,-27,27,63);CompanionFollower c=new CompanionFollower();for(int i=0;i<20;i++)c.tick(f.world,f.player,2,6,1,.95);
            c.impact(0,.5,-.9);for(int i=0;i<80;i++){double x=c.x,z=c.z;c.tick(f.world,f.player,2,6,1,.95);assertTrue("Companion snapped after knockback",Math.hypot(c.x-x,c.z-z)<1);}
            assertTrue(distance(c,f.player.posX,64,f.player.posZ)<4);
        }
    }
    @Test public void knockbackMovesBeforeGravityAndReturnsToFollowingAfterLanding()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            CompanionFollower c=new CompanionFollower();for(int i=0;i<20;i++)c.tick(f.world,f.player,2,6,1,.95);
            double x=c.x,y=c.y;c.impact(.4,.4,0);c.tick(f.world,f.player,2,6,1,.95);
            assertEquals(x+.4,c.x,.0001);assertEquals(y+.4,c.y,.0001);assertEquals(.4*.6*.91,c.motionX(),.0001);assertEquals((.4-.08)*.98,c.motionY(),.0001);assertFalse(c.onGround);
            for(int i=0;i<100;i++)c.tick(f.world,f.player,2,6,1,.95);assertTrue(c.active);assertTrue(c.onGround);assertEquals(64,c.y,.0001);assertTrue(distance(c,f.player.posX,64,f.player.posZ)<4);
        }
    }
    @Test public void knockbackCannotPassThroughBlocks()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            CompanionFollower c=new CompanionFollower();c.tick(f.world,f.player,2,6,1,.95);int wallX=(int)Math.floor(c.x)+1;
            for(int y=64;y<69;y++)for(int z=-4;z<2;z++)f.block(wallX,y,z,Blocks.stone.getDefaultState());
            c.impact(1.5,.4,0);for(int i=0;i<8;i++){c.tick(f.world,f.player,2,6,1,.95);assertTrue(c.x+.28<=wallX+.0001);}
        }
    }
    @Test public void rotatingToLookAtHerDoesNotMoveTheFollowAnchor()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            CompanionFollower c=new CompanionFollower();for(int i=0;i<80;i++)c.tick(f.world,f.player,2,6,1,.95);
            double x=c.x,z=c.z;for(int i=0;i<80;i++){f.player.rotationYaw=i*13;c.tick(f.world,f.player,2,6,1,.95);}
            assertEquals(x,c.x,.02);assertEquals(z,c.z,.02);
            f.player.lastTickPosX=f.player.posX;f.player.lastTickPosZ=f.player.posZ;f.player.posX+=.2;c.tick(f.world,f.player,2,6,1,.95);assertTrue(c.active);
        }
    }
    @Test public void followsBehindAndRecoversFromTeleportsWithinTheCloseRadius()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            f.floor(-27,27,-27,27,63);CompanionFollower c=new CompanionFollower();
            c.tick(f.world,f.player,2,6,1,.95);assertTrue(c.active);assertTrue(c.z<f.player.posZ);assertTrue(c.y>=63.99);
            for(int i=0;i<60;i++){f.player.lastTickPosZ=f.player.posZ;f.player.posZ+=.14;c.tick(f.world,f.player,2,6,1,.95);assertTrue(distance(c,f.player.posX,f.player.posY,f.player.posZ)<6.5);}
            assertTrue(c.z<f.player.posZ);assertTrue(c.walkAmount>.1);assertTrue(c.y>=63.99);
            f.player.posX=20;f.player.posZ=20;c.tick(f.world,f.player,2,6,1,.95);assertTrue(c.active);assertTrue(distance(c,20,64,20)<6);
            c.clear();assertFalse(c.active);
        }
    }
    @Test public void walksAroundSolidWallsAndHidesInUnloadedTerrain()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            CompanionFollower c=new CompanionFollower();c.tick(f.world,f.player,2,6,1,.95);
            for(int x=-2;x<=2;x++)for(int y=64;y<=65;y++)f.block(x,y,1,Blocks.stone.getDefaultState());
            f.player.posZ=f.player.lastTickPosZ=5.5;
            for(int i=0;i<150;i++){c.tick(f.world,f.player,2,9,1,.95);assertTrue("Tick "+i+": "+c.x+", "+c.y+", "+c.z,c.z+.28<=1.0001||c.z-.28>=2-.0001||c.x+.28<=-2.0001||c.x-.28>=3-.0001);}
            assertTrue("Companion remained behind the wall",c.z>2.5);
            f.world.loaded=false;c.tick(f.world,f.player,2,9,1,.95);assertFalse(c.active);
        }
    }
    private static double distance(CompanionFollower c,double x,double y,double z){return Math.sqrt((c.x-x)*(c.x-x)+(c.y-y)*(c.y-y)+(c.z-z)*(c.z-z));}
}
