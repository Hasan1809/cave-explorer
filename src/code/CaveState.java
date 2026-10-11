package code;

import java.util.Objects;

/**
 * One snapshot of the cave explorer's situation.
 *
 * The grid itself (walls, difficulties, where the doors and keys are) never
 * changes, so it lives in CaveExplorer, not here. A state only holds what
 * CAN change while the explorer acts.
 *
 * Keys and doors are stored as bitmasks: bit i of keysCollected is 1 if key
 * number i has been picked up, and bit i of doorsOpened is 1 if door number i
 * has been unlocked. With at most 8 keys and 5 doors, a single int is enough
 * and comparing two states stays fast.
 *
 * A state is immutable: actions never modify a state, they create a new one.
 * That is important because many nodes in the search tree share states.
 */
public class CaveState {

    final int x;              // column of the explorer (0 = leftmost)
    final int y;              // row of the explorer (0 = top)
    final int energy;         // energy left
    final int rope;           // metres of rope left
    final int lives;          // lives left
    final boolean carrying;   // is the explorer holding a key right now?
    final int keysCollected;  // bitmask of keys already picked up
    final int doorsOpened;    // bitmask of doors already unlocked

    public CaveState(int x, int y, int energy, int rope, int lives,
                     boolean carrying, int keysCollected, int doorsOpened) {
        this.x = x;
        this.y = y;
        this.energy = energy;
        this.rope = rope;
        this.lives = lives;
        this.carrying = carrying;
        this.keysCollected = keysCollected;
        this.doorsOpened = doorsOpened;
    }

    /**
     * Two states are equal only if EVERY field matches. Leaving one out
     * (e.g. rope) would make the search treat genuinely different situations
     * as repeats and wrongly discard them.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CaveState)) return false;
        CaveState s = (CaveState) other;
        return x == s.x && y == s.y && energy == s.energy && rope == s.rope
                && lives == s.lives && carrying == s.carrying
                && keysCollected == s.keysCollected && doorsOpened == s.doorsOpened;
    }

    /** Must use the same fields as equals(), so HashMap can find repeated states. */
    @Override
    public int hashCode() {
        return Objects.hash(x, y, energy, rope, lives, carrying, keysCollected, doorsOpened);
    }
}
