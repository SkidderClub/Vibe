package dev.vibe.ui.server;

import java.util.*;

/** Inserting relative to a visible neighbour keeps filtered-out servers in their original order. */
public final class ServerOrder {
    private ServerOrder() { }
    public static <T> List<T> before(List<T> original, T dragged, T neighbour) {
        List<T> order = new ArrayList<>(original);
        if (!order.remove(dragged)) return order;
        int index = neighbour == null ? order.size() : order.indexOf(neighbour);
        order.add(index < 0 ? order.size() : index, dragged);
        return order;
    }
}
