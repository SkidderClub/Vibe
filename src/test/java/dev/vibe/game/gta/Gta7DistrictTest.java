package dev.vibe.game.gta;

import java.util.List;
import java.util.Random;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class Gta7DistrictTest {
    private static Gta7World world;
    @BeforeClass public static void createWorld(){world=new Gta7World();}

    @Test public void eastIsAnEntireGoblinSettlementAndNorthwestHasOneRoundLandmark(){
        assertEquals(Gta7Regions.Region.GOBLINS,Gta7Regions.at(270,90));
        assertEquals(Gta7Regions.Region.EMOJI,Gta7Regions.at(-90,-90));
        int goblins=0,landmarks=0;
        for(Gta7World.Spawn resident:world.residents)if(Gta7Regions.at(resident.x,resident.z)==Gta7Regions.Region.GOBLINS){
            assertEquals(Gta7Game.Kind.GOBLIN,resident.kind);goblins++;
        }
        for(Gta7World.Building b:world.buildings){
            assertTrue(b.height<80);
            if(Gta7Regions.at(b.x,b.z)==Gta7Regions.Region.EMOJI){assertEquals("EMOJI DOME",b.name);landmarks++;}
        }
        assertTrue(goblins>=28);assertEquals(1,landmarks);
        assertTrue(Gta7Game.Kind.GOBLIN.aggressive);assertTrue(Gta7Game.Kind.GOBLIN.melee);assertTrue(Gta7Game.Kind.GOBLIN.height<1.6);
    }
    @Test public void emojiEntranceAndEveryElevatorLandingRemainClear(){
        Gta7World.Elevator lift=world.elevators.get(0);assertEquals(Gta7EmojiBuilding.FLOORS,lift.floors);
        assertFalse(world.path(-90,-115,-90,-97.7).isEmpty());
        for(int floor=0;floor<lift.floors;floor++){
            double y=.14+floor*3.2;
            assertFalse("Obstructed floor "+floor,world.blocked(lift.x,y+.001,lift.z,.28,1.78));
            assertEquals(y,world.support(lift.x,lift.z,y+.01,.28),.000001);
        }
        assertTrue(world.ray(-90,32,-65,0,0,-1,40)<15);
        assertFalse(world.blocked(-125,.14,-90,.28,1.78)); // Empty space below the curved edge stays empty.
    }
    @Test public void concaveEmojiOutlineIsTriangulatedWithoutMissingOrOverlappingArea(){
        double[][] p=Gta7EmojiBuilding.OUTLINE;double area=0,triangles=0;
        for(int i=0;i<p.length;i++){double[] a=p[i],b=p[(i+1)%p.length];area+=a[0]*b[1]-a[1]*b[0];}
        int[] indices=Gta7EmojiBuilding.TRIANGLES;
        for(int i=0;i<indices.length;i+=3){double[] a=p[indices[i]],b=p[indices[i+1]],c=p[indices[i+2]];
            double cross=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);assertTrue(cross>0);triangles+=cross;
        }
        assertEquals(area,triangles,.000001);assertEquals((p.length-2)*3,indices.length);
    }
    @Test public void facadePreservesEveryOriginalReferencePixel() throws Exception {
        try(java.io.InputStream stream=Gta7EmojiBuilding.class.getResourceAsStream(Gta7EmojiBuilding.TEXTURE)){
            assertNotNull(stream);java.awt.image.BufferedImage image=javax.imageio.ImageIO.read(stream);
            assertEquals(184,image.getWidth());assertEquals(184,image.getHeight());
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            for(int y=0;y<184;y++)for(int x=0;x<184;x++){int rgb=image.getRGB(x,y);digest.update((byte)(rgb>>16));digest.update((byte)(rgb>>8));digest.update((byte)rgb);}
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            assertEquals("8d2d3ab9f0abe54161d5a2c014396b1ef50b356eb89b15e0afe1a48c63ac2570",hex.toString());
        }
    }
    @Test @SuppressWarnings("unchecked") public void fastRayTraversalMatchesExhaustiveGeometryAtGridEdgesAndAcrossDistricts() throws Exception {
        java.lang.reflect.Field field=Gta7World.class.getDeclaredField("solids");field.setAccessible(true);
        List<Gta7Bounds> solids=(List<Gta7Bounds>)field.get(world);Random random=new Random(910700);
        for(int i=0;i<1700;i++){
            double x=i%3==0?-180+random.nextInt(91)*6:-190+random.nextDouble()*570;
            double z=i%3==0?-180+random.nextInt(91)*6:-190+random.nextDouble()*570;
            double y=i%5==0?1.76:random.nextDouble()*85,a=i%7==0?Math.PI/4:random.nextDouble()*Math.PI*2;
            double dy=i%4==0?0:random.nextDouble()*1.6-.8,flat=Math.sqrt(1-dy*dy),dx=Math.sin(a)*flat,dz=Math.cos(a)*flat,range=1+random.nextDouble()*620;
            if(i%19==0){dx=dz=0;dy=-1;}
            double expected=dy< -1e-9?Math.min(range,Math.max(0,-y/dy)):range;
            for(Gta7Bounds box:solids)expected=Math.min(expected,box.ray(x,y,z,dx,dy,dz,expected));
            for(Gta7World.Relief r:world.reliefs)expected=Math.min(expected,r.ray(x,y,z,dx,dy,dz,expected));
            assertEquals("Ray "+i+" from "+x+","+y+","+z,expected,world.ray(x,y,z,dx,dy,dz,range),.0000001);
        }
        world.ray(72,1.76,72,0,0,-1,115);assertTrue(world.lastRayCells<=21);assertTrue(world.lastRayCandidates<solids.size()/10);
    }
    @Test public void reusableHeapKeepsPriorityOrderAfterGrowthAndClear(){
        Gta7SearchHeap heap=new Gta7SearchHeap();Random random=new Random(9);double[] score=new double[10000];
        for(int round=0;round<3;round++){
            heap.clear();for(int i=0;i<score.length;i++){score[i]=random.nextDouble();heap.add(i,score[i]);}
            double previous=-1;for(int i=0;i<score.length;i++){double current=score[heap.poll()];assertTrue(current>=previous);previous=current;}assertTrue(heap.isEmpty());
        }
    }
}
