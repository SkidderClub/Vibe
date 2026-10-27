package dev.vibe.phone;

import java.util.Arrays;
import java.util.Random;

/** Local falling-block game. Board simulation and autoplay have no rendering or Minecraft dependencies. */
public final class TetrisGame {
    public static final int WIDTH=10, HEIGHT=20;
    public enum Phase { READY, PLAYING, CLEARING, OVER }
    private static final int[][] BASE={
            {0,1,1,1,2,1,3,1}, {1,0,2,0,1,1,2,1}, {1,0,0,1,1,1,2,1},
            {1,0,2,0,0,1,1,1}, {0,0,1,0,1,1,2,1}, {0,0,0,1,1,1,2,1}, {2,0,0,1,1,1,2,1}};
    private static final int[][][] SHAPES=new int[7][4][8];
    public static final int[] COLORS={0xFF58D8ED,0xFFF4D46B,0xFFBB8CF5,0xFF70DCA1,0xFFF27B91,0xFF7C9FF4,0xFFF0AB69};
    private static final int[][] KICKS={{0,0},{-1,0},{1,0},{-2,0},{2,0},{0,-1},{-1,-1},{1,-1},{0,-2}};
    static {
        for(int piece=0;piece<7;piece++){
            SHAPES[piece][0]=BASE[piece].clone();
            for(int rotation=1;rotation<4;rotation++)for(int cell=0;cell<4;cell++){
                int[] previous=SHAPES[piece][rotation-1];
                SHAPES[piece][rotation][cell*2]=piece==1?BASE[piece][cell*2]:(piece==0?3:2)-previous[cell*2+1];
                SHAPES[piece][rotation][cell*2+1]=piece==1?BASE[piece][cell*2+1]:previous[cell*2];
            }
        }
    }
    private final int[][] board=new int[HEIGHT][WIDTH], scratch=new int[HEIGHT][WIDTH];
    private final int[] bag=new int[7], next=new int[3];
    private final Random random;
    private int bagAt=7,type,rotation,x,y,score,lines,combo=-1,pieces,clearMask,cleared;
    private Phase phase;
    private boolean autoplay,planValid;
    private int goalX,goalRotation,failedActions,lockResets;
    private double gravity,grounded,clearTime,autoTime,overTime;

    public TetrisGame(){this(new Random());}
    public TetrisGame(Random random){this.random=random;reset(false);}
    private int take(){
        if(bagAt==7){for(int i=0;i<7;i++)bag[i]=i;for(int i=6;i>0;i--){int j=random.nextInt(i+1),old=bag[i];bag[i]=bag[j];bag[j]=old;}bagAt=0;}
        return bag[bagAt++];
    }
    public void reset(){reset(false);}
    private void reset(boolean automatic){
        for(int[] row:board)Arrays.fill(row,0);
        score=lines=pieces=clearMask=cleared=0;combo=-1;bagAt=7;gravity=grounded=clearTime=autoTime=overTime=0;
        autoplay=automatic;for(int i=0;i<next.length;i++)next[i]=take();spawn();phase=automatic?Phase.PLAYING:Phase.READY;
    }
    private void spawn(){
        type=next[0];System.arraycopy(next,1,next,0,next.length-1);next[next.length-1]=take();rotation=0;x=3;y=-1;
        gravity=grounded=0;lockResets=failedActions=0;planValid=false;
        phase=fits(type,rotation,x,y)?Phase.PLAYING:Phase.OVER;
    }
    public void toggleAutoplay(){
        boolean wanted=!autoplay;
        if(wanted&&phase==Phase.OVER)reset(true);
        else {autoplay=wanted;if(wanted&&phase==Phase.READY)phase=Phase.PLAYING;}
        autoTime=0;planValid=false;
    }
    public void stopAutoplay(){autoplay=false;planValid=false;}
    private boolean beginManual(){if(phase==Phase.READY)phase=Phase.PLAYING;return phase==Phase.PLAYING;}
    public boolean move(int direction){planValid=false;return beginManual()&&shift(Integer.signum(direction));}
    private boolean shift(int direction){
        if(!fits(type,rotation,x+direction,y))return false;x+=direction;resetLock();return true;
    }
    public boolean rotate(){planValid=false;return beginManual()&&turn();}
    private boolean turn(){
        int nextRotation=(rotation+1)%4;
        for(int[] kick:KICKS)if(fits(type,nextRotation,x+kick[0],y+kick[1])){rotation=nextRotation;x+=kick[0];y+=kick[1];resetLock();return true;}
        return false;
    }
    private void resetLock(){if(lockResets++<15)grounded=0;}
    public boolean softDrop(){
        planValid=false;if(!beginManual()||!fits(type,rotation,x,y+1))return false;y++;score++;gravity=grounded=0;return true;
    }
    public void hardDrop(){
        if(phase==Phase.OVER){reset(false);phase=Phase.PLAYING;return;}
        if(!beginManual())return;
        int landing=ghostY();score+=Math.max(0,landing-y)*2;y=landing;lock();
    }
    public void advance(double seconds){
        if(!Double.isFinite(seconds)||seconds<=0)return;
        double dt=Math.min(.1,seconds);
        if(phase==Phase.OVER){if(autoplay&&(overTime+=dt)>1.5)reset(true);return;}
        if(phase==Phase.READY)return;
        if(phase==Phase.CLEARING){clearTime+=dt;if(clearTime>=.18){collapse(board,clearMask);clearMask=0;spawn();}return;}
        if(autoplay){autoTime+=dt;if(autoTime>=.085){autoTime=0;autoStep();if(phase!=Phase.PLAYING)return;}}
        gravity+=dt;double interval=Math.max(.075,.8*Math.pow(.82,level()-1));
        while(gravity>=interval){gravity-=interval;if(fits(type,rotation,x,y+1)){y++;grounded=0;}else break;}
        if(!fits(type,rotation,x,y+1)){grounded+=dt;if(grounded>=.35)lock();}else grounded=0;
    }
    private void lock(){
        boolean above=false;
        for(int cell=0;cell<4;cell++){int px=x+cellX(type,rotation,cell),py=y+cellY(type,rotation,cell);if(py<0)above=true;else board[py][px]=type+1;}
        pieces++;planValid=false;
        if(above){phase=Phase.OVER;overTime=0;return;}
        clearMask=fullRows(board);cleared=Integer.bitCount(clearMask);
        if(cleared>0){
            int[] reward={0,100,300,500,800};score+=(reward[cleared]+50*Math.max(0,++combo))*level();lines+=cleared;
            clearTime=0;phase=Phase.CLEARING;
        }else{combo=-1;spawn();}
    }
    private boolean fits(int piece,int r,int px,int py){return fits(board,piece,r,px,py);}
    private static boolean fits(int[][] field,int piece,int r,int px,int py){
        for(int cell=0;cell<4;cell++){int col=px+cellX(piece,r,cell),row=py+cellY(piece,r,cell);
            if(col<0||col>=WIDTH||row>=HEIGHT||row>=0&&field[row][col]!=0)return false;
        }return true;
    }
    public int ghostY(){int result=y;while(fits(type,rotation,x,result+1))result++;return result;}
    private static int fullRows(int[][] field){int mask=0;for(int row=0;row<HEIGHT;row++){boolean full=true;for(int col=0;col<WIDTH;col++)if(field[row][col]==0){full=false;break;}if(full)mask|=1<<row;}return mask;}
    private static void collapse(int[][] field,int mask){
        int destination=HEIGHT-1;for(int row=HEIGHT-1;row>=0;row--)if((mask&(1<<row))==0){if(destination!=row)System.arraycopy(field[row],0,field[destination],0,WIDTH);destination--;}
        while(destination>=0)Arrays.fill(field[destination--],0);
    }
    /** Evaluate legal placements once per piece, using one reusable scratch board. */
    private void plan(){
        double best=-Double.MAX_VALUE;goalX=x;goalRotation=rotation;
        for(int r=0;r<(type==1?1:4);r++)for(int col=-3;col<WIDTH;col++){
            if(!fits(type,r,col,y))continue;int landing=y;while(fits(type,r,col,landing+1))landing++;
            boolean above=false;for(int cell=0;cell<4;cell++)if(landing+cellY(type,r,cell)<0)above=true;if(above)continue;
            for(int row=0;row<HEIGHT;row++)System.arraycopy(board[row],0,scratch[row],0,WIDTH);
            for(int cell=0;cell<4;cell++)scratch[landing+cellY(type,r,cell)][col+cellX(type,r,cell)]=type+1;
            int mask=fullRows(scratch),removed=Integer.bitCount(mask);collapse(scratch,mask);
            int height=0,holes=0,bumps=0,previous=-1,highest=0;
            for(int column=0;column<WIDTH;column++){
                int top=HEIGHT;for(int row=0;row<HEIGHT;row++)if(scratch[row][column]!=0){top=row;break;}
                int columnHeight=HEIGHT-top;height+=columnHeight;highest=Math.max(highest,columnHeight);
                if(previous>=0)bumps+=Math.abs(columnHeight-previous);previous=columnHeight;
                for(int row=top+1;row<HEIGHT;row++)if(scratch[row][column]==0)holes++;
            }
            double value=removed*5.0-height*.52-holes*8.5-bumps*.24-highest*.35-Math.abs(col-x)*.002;
            if(value>best){best=value;goalX=col;goalRotation=r;}
        }
        planValid=true;
    }
    private void autoStep(){
        if(!planValid)plan();
        boolean success=true;
        if(rotation!=goalRotation)success=turn();
        else if(x!=goalX)success=shift(Integer.signum(goalX-x));
        else {hardDrop();return;}
        if(!success&&++failedActions>=2){hardDrop();failedActions=0;}
    }
    public static int cellX(int piece,int rotation,int cell){return SHAPES[piece][rotation&3][cell*2];}
    public static int cellY(int piece,int rotation,int cell){return SHAPES[piece][rotation&3][cell*2+1];}
    public int cell(int col,int row){return board[row][col];}
    public int type(){return type;}public int rotation(){return rotation;}public int x(){return x;}public int y(){return y;}
    public int next(int index){return next[index];}public int score(){return score;}public int lines(){return lines;}public int level(){return 1+lines/10;}
    public int pieces(){return pieces;}public int cleared(){return cleared;}public int clearMask(){return clearMask;}public float clearProgress(){return (float)(clearTime/.18);}
    public boolean autoplay(){return autoplay;}public Phase phase(){return phase;}
}
