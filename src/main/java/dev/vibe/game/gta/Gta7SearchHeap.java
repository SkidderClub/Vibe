package dev.vibe.game.gta;

import java.util.Arrays;

/** Reusable binary heap: path searches no longer allocate an object for each explored neighbour. */
final class Gta7SearchHeap {
    private int[] cells=new int[1024];
    private double[] scores=new double[1024];
    private int size;
    void clear(){size=0;}
    boolean isEmpty(){return size==0;}
    void add(int cell,double score){
        if(size==cells.length){cells=Arrays.copyOf(cells,size*2);scores=Arrays.copyOf(scores,size*2);}
        int i=size++;
        while(i>0){int parent=(i-1)/2;if(scores[parent]<=score)break;cells[i]=cells[parent];scores[i]=scores[parent];i=parent;}
        cells[i]=cell;scores[i]=score;
    }
    int poll(){
        int result=cells[0],last=cells[--size];double score=scores[size];int i=0;
        while(i<size/2){int child=i*2+1;if(child+1<size&&scores[child+1]<scores[child])child++;
            if(score<=scores[child])break;cells[i]=cells[child];scores[i]=scores[child];i=child;}
        if(size>0){cells[i]=last;scores[i]=score;}return result;
    }
}
