package dev.vibe.ui.clickgui;

/** Colors transcribed from the eight public previews at breeze.rip. */
enum BreezePalette {
    DEFAULT("Default",0xFF111111,0xFF191919,0xFF222222,0xFF303030,0xFF0D62E1,0xFFF5F5F5,0xFF999999),
    FANCY("Fancy",0xFF101014,0xFF19191C,0xFF222225,0xFF29223A,0xFF4020ED,0xFFF5F5F5,0xFF999999),
    SILENCE("Silence",0xFF22313D,0xFF2C3E50,0xFF60727C,0xFF91A3A8,0xFF5B6F7D,0xFFECF0F1,0xFFC3CED3),
    FLARIAL("Flarial",0xFF230606,0xFF2C1515,0xFF382020,0xFF6C170C,0xFFA71507,0xFFF5F5F5,0xFFB09B9B),
    LEAF("Leaf",0xFF0B1010,0xFF17251E,0xFF20322B,0xFF47935C,0xFF0BB2AF,0xFFC9FFD2,0xFF80A997),
    PINK("Pink",0xFF111014,0xFF19191C,0xFF222225,0xFF3B2840,0xFFCA61DF,0xFFFFC9F1,0xFFAA94A7),
    QUANTUM("Quantum",0xFF0C051F,0xFF150C31,0xFF20153F,0xFF00DFEA,0xFF00DFEA,0xFFCAC5FF,0xFF9C96C8),
    HELLO_KITTY("Hello Kitty",0xFF9A3974,0xD95B354C,0xCF4B3044,0xFFFF00E9,0xFFFF00E9,0xFFFFD7F4,0xFFE5BCCF);
    final String label;final int background,row,raised,border,accent,text,muted;
    BreezePalette(String label,int bg,int row,int raised,int border,int accent,int text,int muted){this.label=label;background=bg;this.row=row;this.raised=raised;this.border=border;this.accent=accent;this.text=text;this.muted=muted;}
    static BreezePalette from(String label){for(BreezePalette p:values())if(p.label.equals(label))return p;return DEFAULT;}
}
