package dev.vibe.notebot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Match exact pitches first, then allocate unused blocks with the fewest tune clicks. */
public final class NoteBlockPlan {
    private NoteBlockPlan() { }

    public static final class Block<T> {
        public final T position;
        public final NoteSong.Instrument instrument;
        public final int pitch;

        public Block(T position, NoteSong.Instrument instrument, int pitch) {
            this.position = position;
            this.instrument = instrument;
            this.pitch = pitch;
        }
    }

    public static int clicks(int pitch, int target) {
        return Math.floorMod(target - pitch, 25);
    }

    public static <T> Map<Integer, T> assign(Iterable<Integer> keys, List<Block<T>> blocks, boolean anyInstrument,
                                             boolean autoTune) {
        List<Block<T>> available = new ArrayList<>(blocks);
        Map<Integer, T> result = new LinkedHashMap<>();
        for (int pass = 0; pass < (autoTune ? 2 : 1); pass++) {
            for (int key : keys) {
                if (result.containsKey(key)) continue;
                int best = pass == 0 ? 1 : 25;
                int index = -1;
                for (int i = 0; i < available.size(); i++) {
                    Block<T> block = available.get(i);
                    if (!anyInstrument && block.instrument.ordinal() != key / 25) continue;
                    int cost = clicks(block.pitch, key % 25);
                    if (cost >= best) continue;
                    index = i;
                    best = cost;
                    if (cost == 0) break;
                }
                if (index >= 0) result.put(key, available.remove(index).position);
            }
        }
        return result;
    }
}
