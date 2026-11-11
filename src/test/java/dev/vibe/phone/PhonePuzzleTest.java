package dev.vibe.phone;

import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PhonePuzzleTest {
    @Test public void firstClickIsSafeAndMinesRespectEveryDifficulty(){for(MinesweeperGame.Difficulty d:MinesweeperGame.Difficulty.values())for(int seed=0;seed<60;seed++){MinesweeperGame g=new MinesweeperGame(new Random(seed));g.start(d);int x=seed%d.size,y=(seed/3)%d.size;g.reveal(x,y);assertFalse(g.over&&!g.won);assertEquals(0,g.cell(x,y));int count=0;for(int yy=0;yy<d.size;yy++)for(int xx=0;xx<d.size;xx++)if(g.cell(xx,yy)<0)count++;assertEquals(d.mines,count);}}
    @Test public void flagsPreventAccidentalRevealAndAllSafeCellsWin(){MinesweeperGame g=new MinesweeperGame(new Random(42));g.start(MinesweeperGame.Difficulty.Hard);g.flag();g.select();assertFalse(g.revealed(0,0));g.flag();g.select();for(int y=0;y<g.size();y++)for(int x=0;x<g.size();x++)if(g.cell(x,y)>=0)g.reveal(x,y);assertTrue(g.won);assertTrue(g.over);}
    @Test public void sudokuPencilsUndoAndSavedGameRoundTrip(){SudokuGame g=new SudokuGame(new Random(3));g.start(SudokuGame.Difficulty.Hard);outer:for(int y=0;y<9;y++)for(int x=0;x<9;x++)if(!g.given(x,y)){g.x=x;g.y=y;break outer;}g.eraseOrPencil();g.set(4);String saved=g.save();SudokuGame copy=new SudokuGame();assertTrue(copy.restore(saved));assertEquals(saved,copy.save());g.back();assertNotEquals(saved,g.save());assertFalse(copy.restore("{broken"));assertFalse(copy.restore(saved.replace("\"difficulty\":2","\"difficulty\":7")));}
    @Test public void sudokuHintsCanCompleteValidBoardsInEveryDifficulty(){for(SudokuGame.Difficulty d:SudokuGame.Difficulty.values())for(int seed=0;seed<12;seed++){SudokuGame g=new SudokuGame(new Random(seed));g.start(d);for(int y=0;y<9;y++)for(int x=0;x<9;x++)if(!g.given(x,y)){g.x=x;g.y=y;g.hint();}assertTrue(g.won);SudokuGame copy=new SudokuGame();assertTrue(copy.restore(g.save()));assertTrue(copy.won);}}
}
