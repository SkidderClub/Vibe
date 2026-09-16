package dev.vibe.game.battlefront;

import java.util.*;

/** Shared, lazily sampled ground routes for long orders and soldiers stuck behind cover. */
public final class BattlefrontNavigation {
    private static final int STEP=6,COUNT=75,TOTAL=COUNT*COUNT;
    private static final double ORIGIN=-222;
    private static final int[] DX={-1,0,1,-1,1,-1,0,1},DZ={-1,-1,-1,0,0,1,1,1};
    private final BattlefrontWorld world;
    private final byte[] nodes=new byte[TOTAL];private final byte[][] edges=new byte[TOTAL][8];
    public BattlefrontNavigation(BattlefrontWorld world){this.world=world;}
    private double x(int n){return ORIGIN+(n%COUNT)*STEP;}private double z(int n){return ORIGIN+(n/COUNT)*STEP;}
    private boolean open(int n){if(nodes[n]==0)nodes[n]=(byte)(world.blocked(x(n),z(n),.55)?2:1);return nodes[n]==1;}
    private boolean segment(double ax,double az,double bx,double bz){int steps=(int)Math.ceil(Math.hypot(bx-ax,bz-az));for(int i=0;i<=steps;i++){double t=steps==0?0:(double)i/steps;if(world.blocked(ax+(bx-ax)*t,az+(bz-az)*t,.55))return false;}return true;}
    private boolean edge(int a,int b,int direction){if(edges[a][direction]==0){byte value=(byte)(open(b)&&segment(x(a),z(a),x(b),z(b))?1:2);edges[a][direction]=value;edges[b][7-direction]=value;}return edges[a][direction]==1;}
    private int nearest(double px,double pz){
        int cx=(int)Math.round((px-ORIGIN)/STEP),cz=(int)Math.round((pz-ORIGIN)/STEP),best=-1;double distance=Double.MAX_VALUE;
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){int nx=cx+dx,nz=cz+dz;if(nx<0||nz<0||nx>=COUNT||nz>=COUNT)continue;int n=nx+nz*COUNT;double d=Math.hypot(x(n)-px,z(n)-pz);if(d<distance&&open(n)&&segment(px,pz,x(n),z(n))){distance=d;best=n;}}
        return best;
    }
    private static final class Node implements Comparable<Node>{final int id;final double cost;Node(int id,double cost){this.id=id;this.cost=cost;}public int compareTo(Node o){return Double.compare(cost,o.cost);}}
    public List<double[]> route(double ax,double az,double bx,double bz){
        if(!Double.isFinite(ax+az+bx+bz))return Collections.emptyList();
        int start=nearest(ax,az),end=nearest(bx,bz);if(start<0||end<0)return Collections.emptyList();
        double[] costs=new double[TOTAL];Arrays.fill(costs,Double.POSITIVE_INFINITY);costs[start]=0;
        int[] previous=new int[TOTAL];Arrays.fill(previous,-1);boolean[] visited=new boolean[TOTAL];
        PriorityQueue<Node> queue=new PriorityQueue<Node>();queue.add(new Node(start,0));
        while(!queue.isEmpty()){
            int a=queue.poll().id;if(visited[a])continue;visited[a]=true;if(a==end)break;
            for(int d=0;d<8;d++){int nx=a%COUNT+DX[d],nz=a/COUNT+DZ[d];if(nx<0||nz<0||nx>=COUNT||nz>=COUNT)continue;int b=nx+nz*COUNT;
                if(visited[b]||!edge(a,b,d))continue;double cost=costs[a]+(DX[d]!=0&&DZ[d]!=0?STEP*Math.sqrt(2):STEP);
                if(cost<costs[b]){costs[b]=cost;previous[b]=a;queue.add(new Node(b,cost+Math.hypot(x(b)-x(end),z(b)-z(end))));}
            }
        }
        if(!visited[end])return Collections.emptyList();
        LinkedList<double[]> result=new LinkedList<double[]>();for(int n=end;n>=0;n=previous[n]){result.addFirst(new double[]{x(n),z(n)});if(n==start)break;}
        result.add(new double[]{bx,bz});return result;
    }
}
