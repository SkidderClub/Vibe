package dev.vibe.phone;
import java.util.*;
/** Small first-click-safe boards; flood fill and flags run only on input. */
public final class MinesweeperGame {
    public enum Difficulty { Easy(6,6),Normal(8,10),Hard(9,16);public final int size,mines;Difficulty(int s,int m){size=s;mines=m;}}
    private final Random random;private int[] cells;private boolean[] revealed,flags;private boolean planted;public Difficulty difficulty=Difficulty.Normal;public boolean menu=true,over,won;public int menuSelection=2,x,y;public double seconds;
    public MinesweeperGame(){this(new Random());}public MinesweeperGame(Random random){this.random=random;}
    public boolean hasGame(){return cells!=null;}public int size(){return difficulty.size;}public int cell(int x,int y){return cells[y*size()+x];}public boolean revealed(int x,int y){return revealed[y*size()+x];}public boolean flagged(int x,int y){return flags[y*size()+x];}
    public int flags(){if(flags==null)return 0;int n=0;for(boolean b:flags)if(b)n++;return n;}
    public void start(Difficulty d){difficulty=d;cells=new int[d.size*d.size];revealed=new boolean[cells.length];flags=new boolean[cells.length];planted=over=won=false;x=y=0;seconds=0;menu=false;}
    public void advance(double dt){if(!menu&&planted&&!over)seconds+=Math.max(0,Math.min(.1,dt));}
    public void move(int dx,int dy){if(menu){menuSelection=Math.max(0,Math.min(3,menuSelection+(dy==0?dx:dy)));return;}x=Math.max(0,Math.min(size()-1,x+dx));y=Math.max(0,Math.min(size()-1,y+dy));}
    public void select(){if(menu){if(menuSelection==0&&hasGame()&&!over)menu=false;else start(Difficulty.values()[Math.max(0,menuSelection-1)]);return;}if(over){menu=true;return;}reveal(x,y);}
    public void flag(){if(menu||over||revealed(x,y))return;flags[y*size()+x]=!flags[y*size()+x];}
    public void reveal(int cx,int cy){if(over||flags[cy*size()+cx]||revealed[cy*size()+cx])return;if(!planted)plant(cx,cy);int index=cy*size()+cx;
        if(cells[index]<0){revealed[index]=true;over=true;return;}ArrayDeque<Integer> open=new ArrayDeque<>();open.add(index);
        while(!open.isEmpty()){int at=open.removeFirst();if(revealed[at]||flags[at])continue;revealed[at]=true;if(cells[at]!=0)continue;int ax=at%size(),ay=at/size();for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int bx=ax+dx,by=ay+dy;if(bx>=0&&by>=0&&bx<size()&&by<size()&&!revealed[by*size()+bx]&&cells[by*size()+bx]>=0)open.add(by*size()+bx);}}
        int count=0;for(boolean b:revealed)if(b)count++;if(count==cells.length-difficulty.mines)won=over=true;
    }
    private void plant(int safeX,int safeY){List<Integer> candidates=new ArrayList<>();for(int i=0;i<cells.length;i++)if(Math.abs(i%size()-safeX)>1||Math.abs(i/size()-safeY)>1)candidates.add(i);Collections.shuffle(candidates,random);for(int i=0;i<difficulty.mines;i++)cells[candidates.get(i)]=-1;
        for(int i=0;i<cells.length;i++)if(cells[i]>=0){int cx=i%size(),cy=i/size();for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int bx=cx+dx,by=cy+dy;if(bx>=0&&by>=0&&bx<size()&&by<size()&&cells[by*size()+bx]<0)cells[i]++;}}planted=true;
    }
}
