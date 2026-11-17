package dev.vibe.ui.clickgui;

import dev.vibe.module.Category;

/** Palette and glyphs from Diablo 2.1's Category and Panel classes, mapped to Vibe categories. */
final class DiabloStyle {
    private DiabloStyle(){}
    static int color(Category category){
        switch(category){case COMBAT:return 0xFF8059B2;case MOVEMENT:return 0xFF5977B2;case WORLD:return 0xFF59A4B2;
            case VISUAL:return 0xFF595EB2;default:return 0xFF9A59B2;}
    }
    static String icon(Category category){switch(category){case COMBAT:return "c";case MOVEMENT:return "n";case WORLD:return "p";case VISUAL:return "i";default:return "e";}}
}
