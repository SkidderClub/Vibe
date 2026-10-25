package dev.vibe.hud;

import dev.vibe.ui.effect.EffectState;
import java.util.*;
import java.util.function.IntUnaryOperator;
import org.lwjgl.opengl.GL11;

/** One closed, antialiased stroke: no disconnected row edges or overlapping line caps. */
public final class ArrayListOutline {
    private ArrayListOutline() { }
    public static final class Box {
        public final float left,top,right,bottom;
        public Box(float left,float top,float right,float bottom) {this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
    }
    public static final class Point {
        public final float x,y;public final int row;
        Point(float x,float y,int row) {this.x=x;this.y=y;this.row=row;}
    }
    public static List<Point> path(List<Box> rows,boolean rectangle) {
        List<Point> p=new ArrayList<Point>();if(rows.isEmpty())return p;
        float left=Float.MAX_VALUE,right=-Float.MAX_VALUE;
        for(Box b:rows) {left=Math.min(left,b.left);right=Math.max(right,b.right);}
        for(int i=0;i<rows.size();i++) {
            Box b=rows.get(i);float x=rectangle?left:b.left;
            float top=i==0?b.top:(rows.get(i-1).bottom+b.top)/2;
            float bottom=i+1==rows.size()?b.bottom:(b.bottom+rows.get(i+1).top)/2;
            add(p,x,top,i);add(p,x,bottom,i);
        }
        for(int i=rows.size()-1;i>=0;i--) {
            Box b=rows.get(i);float x=rectangle?right:b.right;
            float top=i==0?b.top:(rows.get(i-1).bottom+b.top)/2;
            float bottom=i+1==rows.size()?b.bottom:(b.bottom+rows.get(i+1).top)/2;
            add(p,x,bottom,i);add(p,x,top,i);
        }
        return p;
    }
    private static void add(List<Point> p,float x,float y,int row) {
        if(p.isEmpty()||distance(p.get(p.size()-1),x,y)>.001F)p.add(new Point(x,y,row));
    }
    private static float distance(Point p,float x,float y) {return (float)Math.hypot(p.x-x,p.y-y);}
    private static List<Point> rounded(List<Point> input,float radius) {
        if(radius<=.05F||input.size()<3)return input;
        List<Point> result=new ArrayList<Point>();
        for(int i=0;i<input.size();i++) {
            Point a=input.get((i+input.size()-1)%input.size()),b=input.get(i),c=input.get((i+1)%input.size());
            float before=distance(a,b.x,b.y),after=distance(b,c.x,c.y);
            float r=Math.min(radius,Math.min(before,after)*.45F);
            if(r<.05F) {add(result,b.x,b.y,b.row);continue;}
            float sx=b.x+(a.x-b.x)/before*r,sy=b.y+(a.y-b.y)/before*r;
            float ex=b.x+(c.x-b.x)/after*r,ey=b.y+(c.y-b.y)/after*r;
            for(int j=0;j<=6;j++) {
                float t=j/6F,u=1-t;add(result,u*u*sx+2*u*t*b.x+t*t*ex,u*u*sy+2*u*t*b.y+t*t*ey,b.row);
            }
        }
        return result;
    }
    public static void draw(List<Point> input,float width,float radius,IntUnaryOperator colors,float opacity) {
        if(input.size()<3||width<=0||opacity<=0)return;
        List<Point> p=rounded(input,radius);int n=p.size();float[][] normals=new float[n][2];
        for(int i=0;i<n;i++) {
            Point a=p.get((i+n-1)%n),b=p.get(i),c=p.get((i+1)%n);
            float l1=Math.max(.001F,distance(a,b.x,b.y)),l2=Math.max(.001F,distance(b,c.x,c.y));
            float nx1=-(b.y-a.y)/l1,ny1=(b.x-a.x)/l1,nx2=-(c.y-b.y)/l2,ny2=(c.x-b.x)/l2;
            float denominator=Math.max(.2F,1+nx1*nx2+ny1*ny2);
            normals[i][0]=(nx1+nx2)/denominator;normals[i][1]=(ny1+ny2)/denominator;
        }
        boolean clipped=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try(EffectState ignored=new EffectState()) {
            if(clipped)GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glShadeModel(GL11.GL_SMOOTH);
            float[] offsets={-width/2-.6F,-width/2,width/2,width/2+.6F};
            for(int band=0;band<3;band++) {
                GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
                for(int j=0;j<=n;j++) {
                    int i=j%n;Point b=p.get(i);int color=colors.applyAsInt(b.row);
                    for(int edge=band;edge<=band+1;edge++) {
                        GL11.glColor4f((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,
                                (edge==0||edge==3?0:(color>>>24)/255F*opacity));
                        GL11.glVertex2f(b.x+normals[i][0]*offsets[edge],b.y+normals[i][1]*offsets[edge]);
                    }
                }
                GL11.glEnd();
            }
        }
    }
}
