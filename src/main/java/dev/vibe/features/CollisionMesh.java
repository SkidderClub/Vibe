package dev.vibe.features;

import java.util.*;
import net.minecraft.util.*;

/** Boundary edges of collision unions. Adjacent coplanar faces have no artificial seam. */
public final class CollisionMesh {
    public static final class Edge {public final Vec3 a,b;public Edge(Vec3 a,Vec3 b){this.a=a;this.b=b;}int axis(){return a.xCoord!=b.xCoord?0:a.yCoord!=b.yCoord?1:2;}}
    private CollisionMesh(){}
    public static List<Edge> build(List<AxisAlignedBB> boxes,boolean minimal){Map<String,Edge> candidates=new LinkedHashMap<>();Map<BlockPos,List<AxisAlignedBB>> cells=new HashMap<>();
        for(AxisAlignedBB b:boxes){for(int axis=0;axis<3;axis++)for(int j=0;j<2;j++)for(int k=0;k<2;k++){
            double[] lo={b.minX,b.minY,b.minZ},hi={b.maxX,b.maxY,b.maxZ};int p=(axis+1)%3,q=(axis+2)%3;double[] a=lo.clone(),end=lo.clone();a[p]=end[p]=j==0?lo[p]:hi[p];a[q]=end[q]=k==0?lo[q]:hi[q];end[axis]=hi[axis];Edge edge=new Edge(vec(a),vec(end));candidates.put(key(a)+":"+key(end),edge);}
            if(minimal)for(int x=MathHelper.floor_double(b.minX);x<=MathHelper.floor_double(b.maxX-1e-7);x++)for(int y=MathHelper.floor_double(b.minY);y<=MathHelper.floor_double(b.maxY-1e-7);y++)for(int z=MathHelper.floor_double(b.minZ);z<=MathHelper.floor_double(b.maxZ-1e-7);z++)cells.computeIfAbsent(new BlockPos(x,y,z),p->new ArrayList<>()).add(b);
        }
        List<Edge> result=new ArrayList<>();for(Edge edge:candidates.values())if(!minimal||corner(edge,cells))result.add(edge);if(!minimal)return result;
        Map<String,List<Edge>> groups=new LinkedHashMap<>();for(Edge edge:result){int axis=edge.axis();double[] a=arr(edge.a);a[axis]=0;groups.computeIfAbsent(axis+":"+key(a),v->new ArrayList<>()).add(edge);}
        result.clear();for(List<Edge> group:groups.values()){int axis=group.get(0).axis();group.sort(Comparator.comparingDouble(e->arr(e.a)[axis]));double[] start=null,end=null;
            for(Edge e:group){double[] a=arr(e.a),b=arr(e.b);if(start==null){start=a;end=b;}else if(a[axis]<=end[axis]+1e-6)end[axis]=Math.max(end[axis],b[axis]);else{result.add(new Edge(vec(start),vec(end)));start=a;end=b;}}
            if(start!=null)result.add(new Edge(vec(start),vec(end)));}return result;
    }
    private static boolean corner(Edge e,Map<BlockPos,List<AxisAlignedBB>> cells){int axis=e.axis(),p=(axis+1)%3,q=(axis+2)%3;
        for(double t:new double[]{.25,.5,.75}){double[] mid={e.a.xCoord+(e.b.xCoord-e.a.xCoord)*t,e.a.yCoord+(e.b.yCoord-e.a.yCoord)*t,e.a.zCoord+(e.b.zCoord-e.a.zCoord)*t};int mask=0;
            for(int i=0;i<4;i++){double[] at=mid.clone();at[p]+=(i&1)==0?-.0001:.0001;at[q]+=(i&2)==0?-.0001:.0001;if(solid(vec(at),cells))mask|=1<<i;}
            int count=Integer.bitCount(mask);if(count==1||count==3||count==2&&(mask==6||mask==9))return true;}return false;
    }
    private static boolean solid(Vec3 p,Map<BlockPos,List<AxisAlignedBB>> cells){List<AxisAlignedBB> boxes=cells.get(new BlockPos(p));if(boxes!=null)for(AxisAlignedBB b:boxes)if(b.isVecInside(p))return true;return false;}
    private static double[] arr(Vec3 p){return new double[]{p.xCoord,p.yCoord,p.zCoord};}private static Vec3 vec(double[] a){return new Vec3(a[0],a[1],a[2]);}
    private static String key(double[] a){return Math.round(a[0]*4096)+","+Math.round(a[1]*4096)+","+Math.round(a[2]*4096);}
}
