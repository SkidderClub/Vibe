package dev.vibe.launcher.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.swing.JPanel;

/**
 * A vertical or horizontal stack. Vertical stacks stretch children to the full
 * width and ask {@link Wrapping} children for their height at that width, so
 * wrapped text gets exactly the lines it needs. One child added with
 * {@link #FILL} takes the remaining space.
 */
public final class Stack implements LayoutManager2 {
    public static final String FILL = "fill";

    /** Components whose height depends on the width they get, such as wrapped text. */
    public interface Wrapping { int heightFor(int width); }

    /** A transparent panel laid out by a {@link Stack}. */
    public static class Panel extends JPanel implements Wrapping {
        private static final long serialVersionUID = 1L;
        Panel(Stack layout) {
            super(layout);
            setOpaque(false);
        }
        @Override public int heightFor(int width) { return ((Stack) getLayout()).heightFor(this, width); }
    }

    private final boolean vertical;
    private final int gap;
    private final boolean stretch;
    private final Map<Component, Object> constraints = new IdentityHashMap<Component, Object>();

    public Stack(boolean vertical, int gap, boolean stretch) {
        this.vertical = vertical;
        this.gap = gap;
        this.stretch = stretch;
    }

    public static Panel column(int gap) { return new Panel(new Stack(true, gap, true)); }
    public static Panel row(int gap) { return new Panel(new Stack(false, gap, false)); }
    /** A row whose children all take the full height. */
    public static Panel stretchedRow(int gap) { return new Panel(new Stack(false, gap, true)); }

    /** Transparent spacer; add it with {@link #FILL} to push later children to the end. */
    public static Component glue() {
        JPanel glue = new JPanel();
        glue.setOpaque(false);
        glue.setPreferredSize(new Dimension(0, 0));
        return glue;
    }

    public static Component space(int size) {
        JPanel space = new JPanel();
        space.setOpaque(false);
        space.setPreferredSize(new Dimension(size, size));
        return space;
    }

    static int heightOf(Component child, int width) {
        return child instanceof Wrapping ? ((Wrapping) child).heightFor(width) : child.getPreferredSize().height;
    }

    int heightFor(Container parent, int width) {
        Insets insets = parent.getInsets();
        int inner = Math.max(0, width - insets.left - insets.right);
        int result = 0;
        if (vertical) {
            int visible = 0;
            for (Component child : parent.getComponents()) {
                if (!child.isVisible()) continue;
                result += FILL.equals(constraints.get(child)) ? child.getPreferredSize().height : heightOf(child, inner);
                visible++;
            }
            result += Math.max(0, visible - 1) * gap;
        } else {
            int[] widths = widths(parent, inner);
            Component[] children = parent.getComponents();
            for (int index = 0; index < children.length; index++) {
                if (children[index].isVisible()) result = Math.max(result, heightOf(children[index], widths[index]));
            }
        }
        return result + insets.top + insets.bottom;
    }

    private int[] widths(Container parent, int inner) {
        Component[] children = parent.getComponents();
        int[] widths = new int[children.length];
        int used = 0, visible = 0, fill = -1;
        for (int index = 0; index < children.length; index++) {
            if (!children[index].isVisible()) continue;
            visible++;
            if (FILL.equals(constraints.get(children[index]))) { fill = index; continue; }
            widths[index] = children[index].getPreferredSize().width;
            used += widths[index];
        }
        used += Math.max(0, visible - 1) * gap;
        if (fill >= 0) widths[fill] = Math.max(0, inner - used);
        return widths;
    }

    @Override public void addLayoutComponent(Component component, Object constraint) {
        if (constraint != null) constraints.put(component, constraint);
    }
    @Override public void addLayoutComponent(String name, Component component) { if (name != null) constraints.put(component, name); }
    @Override public void removeLayoutComponent(Component component) { constraints.remove(component); }
    @Override public float getLayoutAlignmentX(Container target) { return 0; }
    @Override public float getLayoutAlignmentY(Container target) { return 0; }
    @Override public void invalidateLayout(Container target) { }
    @Override public Dimension maximumLayoutSize(Container target) { return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE); }
    @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, 0); }

    @Override public Dimension preferredLayoutSize(Container parent) {
        Insets insets = parent.getInsets();
        int main = 0, cross = 0, visible = 0;
        for (Component child : parent.getComponents()) {
            if (!child.isVisible()) continue;
            Dimension size = child.getPreferredSize();
            main += vertical ? size.height : size.width;
            cross = Math.max(cross, vertical ? size.width : size.height);
            visible++;
        }
        main += Math.max(0, visible - 1) * gap;
        Dimension result = vertical ? new Dimension(cross + insets.left + insets.right, main + insets.top + insets.bottom)
                : new Dimension(main + insets.left + insets.right, cross + insets.top + insets.bottom);
        if (parent.getWidth() > 0) result.height = heightFor(parent, parent.getWidth());
        return result;
    }

    @Override public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int width = parent.getWidth() - insets.left - insets.right;
        int height = parent.getHeight() - insets.top - insets.bottom;
        Component[] children = parent.getComponents();
        int[] sizes;
        if (vertical) {
            sizes = new int[children.length];
            int used = 0, visible = 0, fill = -1;
            for (int index = 0; index < children.length; index++) {
                if (!children[index].isVisible()) continue;
                visible++;
                if (FILL.equals(constraints.get(children[index]))) { fill = index; continue; }
                sizes[index] = heightOf(children[index], width);
                used += sizes[index];
            }
            used += Math.max(0, visible - 1) * gap;
            if (fill >= 0) sizes[fill] = Math.max(0, height - used);
        } else {
            sizes = widths(parent, width);
        }
        int position = vertical ? insets.top : insets.left;
        for (int index = 0; index < children.length; index++) {
            Component child = children[index];
            if (!child.isVisible()) continue;
            if (vertical) {
                child.setBounds(insets.left, position, width, sizes[index]);
            } else {
                int childHeight = stretch ? height : Math.min(height, heightOf(child, sizes[index]));
                child.setBounds(position, insets.top + (height - childHeight) / 2, sizes[index], childHeight);
            }
            position += sizes[index] + gap;
        }
    }
}
