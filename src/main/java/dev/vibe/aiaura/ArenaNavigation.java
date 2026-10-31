package dev.vibe.aiaura;

import java.util.*;
import net.minecraft.util.Vec3;

/** Bounded local height-map routing for practice opponents, with one-block jump transitions. */
public final class ArenaNavigation {
    private ArenaNavigation(){}
    public static List<RecorderArena.Pillar> trainingMap(){
        List<RecorderArena.Pillar> map=new ArrayList<>();
        add(map,-6,5,1.4,3.5);add(map,7,7,1,4);add(map,-8,-6,1.2,3);add(map,6,-7,2,.75);add(map,0,11,3,3);add(map,-12,0,1,4);
        for(int i=0;i<3;i++){add(map,4+i,2,1,i+1);add(map,-4-i,-4,1,i+1);add(map,-9-i,0,1,i+1);}
        add(map,8,2,3,3);add(map,-7,-4,2,3);add(map,-12,-1,1,4);add(map,-11,-1,1,3);
        add(map,-5,5,1,1);add(map,-5,6,1,2);add(map,-6,6,1,3);
        add(map,7,4,1,1);add(map,7,5,1,2);add(map,7,6,1,3);
        add(map,0,8,1,1);add(map,0,9,1,2);add(map,0,10,1,3);
        add(map,0,-4,1,1);add(map,0,-5,1,2);add(map,0,-7,3,2);
        add(map,-7,-6,1,2);add(map,-6,-6,1,1);
        return Collections.unmodifiableList(map);
    }
    private static void add(List<RecorderArena.Pillar> map,double x,double z,double width,double height){map.add(new RecorderArena.Pillar(x,z,width,height));}
    public static double height(double x,double z,List<RecorderArena.Pillar> map){double result=0;for(RecorderArena.Pillar p:map)if(Math.abs(x-p.x)<p.width/2+.2999&&Math.abs(z-p.z)<p.width/2+.2999)result=Math.max(result,p.height);return result;}
    public static List<Vec3> route(double fromX,double fromZ,double toX,double toZ,List<RecorderArena.Pillar> map){
        int sx=(int)Math.round(fromX),sz=(int)Math.round(fromZ),gx=(int)Math.round(toX),gz=(int)Math.round(toZ);if(Math.abs(sx)>23||Math.abs(sz)>23||Math.abs(gx)>23||Math.abs(gz)>23)return Collections.emptyList();
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(n->n.cost+n.estimate));Map<Integer,Node> nodes=new HashMap<>();Node first=new Node(sx,sz,height(sx,sz,map),0,heuristic(sx,sz,gx,gz),null);open.add(first);nodes.put(key(sx,sz),first);
        for(int expanded=0;!open.isEmpty()&&expanded<1400;expanded++){
            Node current=open.poll();if(current.closed)continue;current.closed=true;if(current.x==gx&&current.z==gz){LinkedList<Vec3> path=new LinkedList<>();for(Node n=current;n!=null;n=n.parent)path.addFirst(new Vec3(n.x,n.y,n.z));return path;}
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                if(dx==0&&dz==0)continue;int x=current.x+dx,z=current.z+dz;if(Math.abs(x)>22||Math.abs(z)>22)continue;double y=height(x,z,map),rise=y-current.y;if(rise>1.01||rise< -4)continue;
                if(dx!=0&&dz!=0&&(height(current.x+dx,current.z,map)>current.y+.6||height(current.x,current.z+dz,map)>current.y+.6))continue;
                double cost=current.cost+Math.hypot(dx,dz)+Math.max(0,rise)*1.5;int key=key(x,z);Node old=nodes.get(key);if(old!=null&&old.cost<=cost)continue;Node next=new Node(x,z,y,cost,heuristic(x,z,gx,gz),current);nodes.put(key,next);open.add(next);
            }
        }return Collections.emptyList();
    }
    private static int key(int x,int z){return (x+32)*64+z+32;}
    private static double heuristic(int x,int z,int gx,int gz){return Math.hypot(gx-x,gz-z);}
    private static final class Node {final int x,z;final double y,cost,estimate;final Node parent;boolean closed;Node(int x,int z,double y,double cost,double estimate,Node parent){this.x=x;this.z=z;this.y=y;this.cost=cost;this.estimate=estimate;this.parent=parent;}}
}
