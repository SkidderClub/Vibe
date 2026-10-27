package dev.vibe.phone;

import dev.vibe.module.impl.meme.PhoneModule;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import org.lwjgl.input.Keyboard;
import static org.junit.Assert.*;

public class TetrisGameTest {
    private TetrisGame game(){return new TetrisGame(new Random(17));}
    @Test public void allTetrominoRotationsHaveFourDistinctCellsInTheirBounds(){
        for(int type=0;type<7;type++)for(int rotation=0;rotation<4;rotation++){
            Set<Integer> cells=new HashSet<>();for(int cell=0;cell<4;cell++){int x=TetrisGame.cellX(type,rotation,cell),y=TetrisGame.cellY(type,rotation,cell);
                assertTrue(x>=0&&x<4);assertTrue(y>=0&&y<4);cells.add(y*4+x);}
            assertEquals(4,cells.size());
        }
    }
    @Test public void controlsRespectWallsAndGhostLandsOnTheLockedBoard(){
        TetrisGame game=game();for(int i=0;i<20;i++)game.move(-1);
        for(int i=0;i<4;i++)assertTrue(game.x()+TetrisGame.cellX(game.type(),game.rotation(),i)>=0);
        for(int i=0;i<20;i++)game.rotate();for(int i=0;i<20;i++)game.move(1);
        for(int i=0;i<4;i++)assertTrue(game.x()+TetrisGame.cellX(game.type(),game.rotation(),i)<TetrisGame.WIDTH);
        int ghost=game.ghostY();assertTrue(ghost>=game.y());game.hardDrop();assertEquals(1,game.pieces());assertEquals(4,count(game));
    }
    @Test public void sevenBagUsesEveryShapeBeforeRepeating()throws Exception{
        TetrisGame game=game();Set<Integer> bag=new HashSet<>();
        Method spawn=TetrisGame.class.getDeclaredMethod("spawn");spawn.setAccessible(true);
        for(int i=0;i<7;i++){bag.add(game.type());spawn.invoke(game);}assertEquals(7,bag.size());
    }
    @Test public void fourRowsFlashThenCollapseAndIncreaseTheLevel()throws Exception{
        TetrisGame game=game();int[][] board=board(game);
        for(int row=16;row<20;row++)for(int col=0;col<10;col++)if(col!=5)board[row][col]=2;
        set(game,"type",0);set(game,"rotation",1);set(game,"x",3);set(game,"y",-1);set(game,"phase",TetrisGame.Phase.PLAYING);set(game,"lines",9);
        game.hardDrop();assertEquals(TetrisGame.Phase.CLEARING,game.phase());assertEquals(4,game.cleared());assertEquals(13,game.lines());assertEquals(2,game.level());assertTrue(game.score()>=800);
        assertTrue(game.clearMask()!=0);game.advance(.1);assertEquals(TetrisGame.Phase.CLEARING,game.phase());game.advance(.1);
        assertEquals(TetrisGame.Phase.PLAYING,game.phase());assertEquals(0,count(game));assertEquals(0,game.clearMask());
    }
    @Test public void autoplayActuallyPlacesPiecesAndClearsLines(){
        TetrisGame game=game();game.toggleAutoplay();int total=0,cleared=0,previousPieces=0,previousLines=0;
        for(int i=0;i<12000;i++){
            game.advance(.1);total+=game.pieces()>=previousPieces?game.pieces()-previousPieces:game.pieces();
            cleared+=game.lines()>=previousLines?game.lines()-previousLines:game.lines();previousPieces=game.pieces();previousLines=game.lines();
        }
        assertTrue("Autoplay stalled",total>150);assertTrue("Autoplay never cleared lines",cleared>30);
        assertTrue(game.autoplay());game.toggleAutoplay();assertFalse(game.autoplay());
    }
    @Test public void gameOverCanRestartManuallyAndAutomatically()throws Exception{
        TetrisGame game=game();Arrays.fill(board(game)[0],1);Method spawn=TetrisGame.class.getDeclaredMethod("spawn");spawn.setAccessible(true);spawn.invoke(game);
        assertEquals(TetrisGame.Phase.OVER,game.phase());game.hardDrop();assertEquals(TetrisGame.Phase.PLAYING,game.phase());assertEquals(0,count(game));
        game.toggleAutoplay();set(game,"phase",TetrisGame.Phase.OVER);for(int i=0;i<16;i++)game.advance(.1);
        assertEquals(TetrisGame.Phase.PLAYING,game.phase());assertTrue(game.autoplay());
    }
    @Test public void phoneHasTetrisAndMiddleButtonTogglesAutoplayWithoutRepeating(){
        PhoneModule phone=new PhoneModule();phone.setEnabled(true);phone.open(PhoneModule.App.TETRIS);
        phone.handleKey(Keyboard.KEY_NUMPAD5,false);assertTrue(phone.tetris().autoplay());
        phone.handleKey(Keyboard.KEY_NUMPAD5,true);assertTrue(phone.tetris().autoplay());
        for(int i=0;i<40;i++)phone.advance(.1);assertTrue(phone.tetris().pieces()>0);assertTrue(phone.tetrisBest.getInt()>0);
        phone.handleKey(Keyboard.KEY_NUMPAD5,false);assertFalse(phone.tetris().autoplay());
        int y=phone.tetris().y();phone.open(PhoneModule.App.HOME);for(int i=0;i<20;i++)phone.advance(.1);assertEquals(y,phone.tetris().y());
        phone.open(PhoneModule.App.TETRIS);phone.act(PhoneModule.Action.SELECT);phone.setEnabled(false);assertFalse(phone.tetris().autoplay());
    }
    private static int count(TetrisGame game){int count=0;for(int y=0;y<20;y++)for(int x=0;x<10;x++)if(game.cell(x,y)!=0)count++;return count;}
    private static int[][] board(TetrisGame game)throws Exception{Field field=TetrisGame.class.getDeclaredField("board");field.setAccessible(true);return(int[][])field.get(game);}
    private static void set(TetrisGame game,String name,Object value)throws Exception{Field field=TetrisGame.class.getDeclaredField(name);field.setAccessible(true);field.set(game,value);}
}
