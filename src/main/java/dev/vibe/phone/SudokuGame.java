package dev.vibe.phone;
import com.google.gson.Gson;
import java.util.*;
/** Valid puzzle transformations, pencil masks and bounded per-cell undo. */
public final class SudokuGame {
    private static final String BASE="530070000600195000098000060800060003400803001700020006060000280000419005000080079";
    public enum Difficulty {Easy,Normal,Hard}
    private static final class Change{int cell,value,pencil;Change(int c,int v,int p){cell=c;value=v;pencil=p;}}
    private final Random random;private int[] values,givens,solution,pencils;private final Deque<Change> undo=new ArrayDeque<>();
    public boolean menu=true,pencilMode,won,hintsEnabled=true;public int menuSelection=2,x,y,picker=-1,hints;public Difficulty difficulty=Difficulty.Normal;public double seconds;
    public SudokuGame(){this(new Random());}public SudokuGame(Random random){this.random=random;}
    public boolean hasGame(){return values!=null;}public int value(int x,int y){return values[y*9+x];}public boolean given(int x,int y){return givens[y*9+x]!=0;}public int pencil(int x,int y){return pencils[y*9+x];}
    public boolean conflict(int x,int y){if(values==null||value(x,y)==0)return false;int at=y*9+x,n=values[at];for(int i=0;i<81;i++)if(i!=at&&values[i]==n&&(i/9==y||i%9==x||i/27==y/3&&i%9/3==x/3))return true;return false;}
    public void start(Difficulty d){difficulty=d;int[] digits={1,2,3,4,5,6,7,8,9};for(int i=8;i>0;i--){int k=random.nextInt(i+1),v=digits[i];digits[i]=digits[k];digits[k]=v;}
        int[] rows=order(),cols=order();boolean transpose=random.nextBoolean();givens=new int[81];for(int yy=0;yy<9;yy++)for(int xx=0;xx<9;xx++){int source=transpose?cols[xx]*9+rows[yy]:rows[yy]*9+cols[xx],v=BASE.charAt(source)-'0';givens[yy*9+xx]=v==0?0:digits[v-1];}
        solution=givens.clone();if(!solve(solution))throw new IllegalStateException("Invalid Sudoku template");List<Integer> empty=new ArrayList<>();for(int i=0;i<81;i++)if(givens[i]==0)empty.add(i);Collections.shuffle(empty,random);int extra=d==Difficulty.Easy?16:d==Difficulty.Normal?6:0;for(int i=0;i<extra;i++)givens[empty.get(i)]=solution[empty.get(i)];
        values=givens.clone();pencils=new int[81];undo.clear();menu=won=pencilMode=false;picker=-1;x=y=hints=0;seconds=0;
    }
    private int[] order(){List<Integer> bands=Arrays.asList(0,1,2);Collections.shuffle(bands,random);int[] out=new int[9];int at=0;for(int band:bands){List<Integer> row=Arrays.asList(0,1,2);Collections.shuffle(row,random);for(int r:row)out[at++]=band*3+r;}return out;}
    public void advance(double dt){if(!menu&&!won&&hasGame())seconds+=Math.max(0,Math.min(.1,dt));}
    public void move(int dx,int dy){if(menu){menuSelection=Math.max(0,Math.min(3,menuSelection+(dy==0?dx:dy)));return;}if(picker>=0){picker=Math.max(0,Math.min(10,picker+dx+dy*3));return;}x=Math.max(0,Math.min(8,x+dx));y=Math.max(0,Math.min(8,y+dy));}
    public void select(){if(menu){if(menuSelection==0&&hasGame()&&!won)menu=false;else start(Difficulty.values()[Math.max(0,menuSelection-1)]);return;}if(won){menu=true;return;}if(given(x,y))return;if(picker<0){picker=0;return;}int selected=picker;picker=-1;if(selected==10){if(hintsEnabled)hint();}else set(selected==9?0:selected+1);}
    public void eraseOrPencil(){if(menu)return;if(picker>=0){picker=-1;set(0);}else pencilMode=!pencilMode;}
    public void back(){if(picker>=0){picker=-1;return;}if(!undo.isEmpty()){Change c=undo.removeLast();values[c.cell]=c.value;pencils[c.cell]=c.pencil;won=false;}else menu=true;}
    public void set(int n){if(values==null||n<0||n>9||given(x,y))return;int i=y*9+x;remember(i);if(pencilMode&&n>0){pencils[i]^=1<<(n-1);values[i]=0;}else{values[i]=n;pencils[i]=0;}checkWin();}
    public void hint(){if(values==null||given(x,y))return;int i=y*9+x;remember(i);values[i]=solution[i];pencils[i]=0;hints++;checkWin();}
    private void remember(int at){if(undo.size()>=128)undo.removeFirst();undo.addLast(new Change(at,values[at],pencils[at]));}
    private void checkWin(){won=true;for(int i=0;i<81;i++)if(values[i]!=solution[i]){won=false;break;}}
    private static boolean solve(int[] board){int at=-1,best=10,mask=0;for(int i=0;i<81;i++)if(board[i]==0){int possible=possibilities(board,i),count=Integer.bitCount(possible);if(count==0)return false;if(count<best){at=i;best=count;mask=possible;}}if(at<0)return true;for(int n=1;n<=9;n++)if((mask&(1<<(n-1)))!=0){board[at]=n;if(solve(board))return true;}board[at]=0;return false;}
    private static int possibilities(int[] b,int at){int x=at%9,y=at/9,mask=511;for(int i=0;i<81;i++)if(b[i]>0&&(i/9==y||i%9==x||i/27==y/3&&i%9/3==x/3))mask&=~(1<<(b[i]-1));return mask;}
    private static final class Save {int[] values,givens,solution,pencils;int difficulty,x,y,hints;double seconds;boolean pencil;}
    public String save(){if(values==null)return "";Save s=new Save();s.values=values;s.givens=givens;s.solution=solution;s.pencils=pencils;s.difficulty=difficulty.ordinal();s.x=x;s.y=y;s.hints=hints;s.seconds=seconds;s.pencil=pencilMode;return new Gson().toJson(s);}
    public boolean restore(String json){if(json==null||json.isEmpty()||json.length()>12000)return false;try{Save s=new Gson().fromJson(json,Save.class);if(s.values.length!=81||s.givens.length!=81||s.solution.length!=81||s.pencils.length!=81||s.difficulty<0||s.difficulty>2||!Double.isFinite(s.seconds)||s.seconds<0)return false;
        for(int i=0;i<81;i++){if(s.values[i]<0||s.values[i]>9||s.givens[i]<0||s.givens[i]>9||s.solution[i]<1||s.solution[i]>9||s.pencils[i]<0||s.pencils[i]>511||s.givens[i]!=0&&(s.givens[i]!=s.solution[i]||s.values[i]!=s.givens[i]))return false;int v=s.solution[i];s.solution[i]=0;boolean valid=(possibilities(s.solution,i)&1<<(v-1))!=0;s.solution[i]=v;if(!valid)return false;}
        values=s.values;givens=s.givens;solution=s.solution;pencils=s.pencils;difficulty=Difficulty.values()[s.difficulty];x=Math.max(0,Math.min(8,s.x));y=Math.max(0,Math.min(8,s.y));hints=Math.max(0,s.hints);seconds=s.seconds;pencilMode=s.pencil;picker=-1;menu=true;undo.clear();checkWin();return true;
        }catch(RuntimeException invalid){return false;}}
}
