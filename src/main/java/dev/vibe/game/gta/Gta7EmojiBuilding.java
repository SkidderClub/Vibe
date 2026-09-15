package dev.vibe.game.gta;

import java.util.ArrayList;
import java.util.List;

/** Extruded outline of the supplied 184-pixel emoji, with a separate entrance on the back. */
final class Gta7EmojiBuilding {
    static final double SIZE=70, DEPTH=22;
    static final int FLOORS=18;
    static final String TEXTURE="/assets/vibe/textures/gta7/emoji-facade.png";
    // Image coordinates: the rim follows the hair, glasses and rounded chin instead of a square billboard.
    static final double[][] OUTLINE={
        {25,5},{64,0},{94,2},{122,10},{146,23},{163,43},{175,64},{182,70},
        {182,91},{178,112},{170,127},{166,143},{155,160},{137,174},{115,181},{93,184},
        {69,180},{48,170},{31,156},{20,140},{15,125},{8,120},{4,109},{1,91},{0,77},
        {8,69},{23,66},{32,51},{37,46},{10,42},{17,30},{29,23},{43,20}
    };
    static final int[] TRIANGLES=triangulate();
    private Gta7EmojiBuilding() { }

    static double worldX(double cx,double pixel){return cx+(pixel/184-.5)*SIZE;}
    static double worldY(double pixel){return .14+(1-pixel/184)*SIZE;}
    static double[] span(double worldY){
        double row=(1-(worldY-.14)/SIZE)*184,left=184,right=0;
        for(int i=0;i<OUTLINE.length;i++){
            double[] a=OUTLINE[i],b=OUTLINE[(i+1)%OUTLINE.length];
            if((a[1]<=row&&b[1]>row)||(b[1]<=row&&a[1]>row)){
                double x=a[0]+(row-a[1])/(b[1]-a[1])*(b[0]-a[0]);left=Math.min(left,x);right=Math.max(right,x);
            }
        }
        return new double[]{left,right};
    }
    static void build(Gta7World w,double cx,double cz){
        w.buildings.add(new Gta7World.Building(cx-SIZE/2,cz-DEPTH/2,SIZE,DEPTH,SIZE+.14,"EMOJI DOME",cx,cz-DEPTH/2));
        // Thin collision strips match the circular silhouette, including the projecting glasses.
        for(double y=.14;y<SIZE;y+=.5){
            double[] limits=span(y+.25);if(limits[1]<=limits[0])continue;
            double left=worldX(cx,limits[0]),right=worldX(cx,limits[1]);
            w.collider(new Gta7Bounds(left,y,cz+10.7,right,y+.5,cz+11));
            w.collider(new Gta7Bounds(left,y,cz-11,left+.45,y+.5,cz+11));
            w.collider(new Gta7Bounds(right-.45,y,cz-11,right,y+.5,cz+11));
            if(y<2.9){
                if(left<cx-1.7)w.box(left,y,cz-11,cx-1.7,y+.5,cz-10.7,0xD5AB35,true);
                if(right>cx+1.7)w.box(cx+1.7,y,cz-11,right,y+.5,cz-10.7,0xD5AB35,true);
            }else w.box(left,y,cz-11,right,y+.5,cz-10.7,0xD5AB35,true);
        }
        // The small ground lobby supports the rounded base without cutting a doorway into the face.
        w.box(cx-6,.02,cz-12,cx+6,.14,cz+10.5,0x554E38,true);
        for(int floor=0;floor<FLOORS;floor++){
            double y=.14+floor*3.2;double[] limits=span(y+.5);
            double left=Math.min(cx-3,worldX(cx,limits[0])+.6),right=Math.max(cx+3,worldX(cx,limits[1])-.6);
            if(floor>0)w.box(left,y-.14,cz-10.65,right,y,cz+10.65,0xE3D3AB,true);
            w.box(cx-1.3,y+.005,cz-9,cx+1.3,y+.016,cz-6.4,0x776142,false);
            w.box(cx-1.45,y,cz-9.2,cx-1.3,y+2.5,cz-6.1,0x3E4037,false);
            w.box(cx+1.3,y,cz-9.2,cx+1.45,y+2.5,cz-6.1,0x3E4037,false);
            if(floor>0){
                w.box(left+1,y,cz+5,left+4,y+.8,cz+6.5,0x8E7150,true);
                w.box(right-4,y,cz+5,right-1,y+.8,cz+6.5,0x8E7150,true);
            }
            w.box(cx-1,y+2.8,cz-1,cx+1,y+2.9,cz+1,0xF8EDCF,false);
            w.lights.add(new Gta7World.Light(cx,y+2.7,cz,5,0xFFE0A0));
            if(floor%3==0)w.landmarks.add(new Gta7World.Landmark("EMOJI / FLOOR "+(floor+1),cx,y+2.2,cz-5.9,.024));
        }
        w.elevators.add(new Gta7World.Elevator(cx,cz-7.7,FLOORS));
        w.landmarks.add(new Gta7World.Landmark("EMOJI DOME / ENTRANCE",cx,3.15,cz-11.1,.035));
        w.landmarks.add(new Gta7World.Landmark("E: UP / SHIFT+E: DOWN",cx,2,cz-6,.025));
        for(int i=0;i<6;i++)w.lamp(cx-30+i*12,cz+30);
    }

    private static int[] triangulate(){
        List<Integer> polygon=new ArrayList<Integer>(),result=new ArrayList<Integer>();
        for(int i=0;i<OUTLINE.length;i++)polygon.add(i);
        while(polygon.size()>3){
            boolean clipped=false;
            for(int i=0;i<polygon.size();i++){
                int a=polygon.get((i+polygon.size()-1)%polygon.size()),b=polygon.get(i),c=polygon.get((i+1)%polygon.size());
                if(cross(OUTLINE[a],OUTLINE[b],OUTLINE[c])<=0)continue;
                boolean occupied=false;
                for(int p:polygon)if(p!=a&&p!=b&&p!=c&&cross(OUTLINE[a],OUTLINE[b],OUTLINE[p])>=0&&cross(OUTLINE[b],OUTLINE[c],OUTLINE[p])>=0&&cross(OUTLINE[c],OUTLINE[a],OUTLINE[p])>=0){occupied=true;break;}
                if(occupied)continue;result.add(a);result.add(b);result.add(c);polygon.remove(i);clipped=true;break;
            }
            if(!clipped)throw new IllegalStateException("Invalid emoji outline");
        }
        result.addAll(polygon);int[] triangles=new int[result.size()];for(int i=0;i<triangles.length;i++)triangles[i]=result.get(i);return triangles;
    }
    private static double cross(double[] a,double[] b,double[] c){return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);}
}
