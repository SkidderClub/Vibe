package dev.vibe.ui.server;

/** Logical GUI coordinates, shared by drawing, input and resize checks. */
public final class ServerBrowserLayout {
    public final int x, y, w, h, listX, listY, listW, listH, footer, row, step,icon;
    public final boolean compact;
    public ServerBrowserLayout(int width, int height) {
        int margin = width < 500 || height < 350 ? 10 : 24;
        w = Math.min(940, width - margin * 2); h = Math.min(540, height - margin * 2);
        x = (width - w) / 2; y = (height - h) / 2;
        compact = w < 760; row = height < 350 ? 46 : 52; step = row + 6;icon=28;
        listX = x + 16; listY = y + 70;
        footer = y + h - 60;
        listW = w - 32; listH = Math.max(20, footer - listY - 12);
    }
    public int maxScroll(int count) { return Math.max(0, count * step - 6 - listH); }
    public boolean inList(int px, int py) { return px >= listX && px < listX + listW && py >= listY && py < listY + listH; }
    public int firstVisible(float scroll){return Math.max(0,(int)(scroll/step)-1);}
    public int lastVisible(int count,float scroll){return Math.min(count,firstVisible(scroll)+(listH+step-1)/step+3);}
    /** Vanilla order and proportions, centered and scaled only when the viewport is narrow. */
    public FooterButton[] footerButtons(){
        float scale=Math.min(1,(w-24)/308F);int left=x+(w-Math.round(308*scale))/2;
        int[] ids={4,2,1,5,6,3,0},offsets={0,104,208,0,80,158,236};
        String[] labels={"Join Server","Direct Connect","Add Server","Edit","Delete","Refresh","Cancel"};
        FooterButton[] buttons=new FooterButton[7];
        for(int i=0;i<buttons.length;i++)buttons[i]=new FooterButton(ids[i],left+Math.round(offsets[i]*scale),footer+(i<3?0:24),Math.round((i<3?100:70)*scale),labels[i]);
        return buttons;
    }
    public static final class FooterButton{
        public final int id,x,y,w;public final String label;
        FooterButton(int id,int x,int y,int w,String label){this.id=id;this.x=x;this.y=y;this.w=w;this.label=label;}
    }
}
