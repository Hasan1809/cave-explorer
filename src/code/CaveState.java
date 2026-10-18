package code;

import java.util.Objects;

// The part of the problem that changes while searching (the grid itself is in CaveExplorer).
// keysCollected / doorsOpened are bitmasks: bit i set = key i taken / door i opened.
public class CaveState {

    final int x;
    final int y;
    final int energy;
    final int rope;
    final int lives;
    final boolean carrying;   // holding a key
    final int keysCollected;
    final int doorsOpened;

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

    // All fields are compared, otherwise different situations (e.g. the same
    // cell with less rope) would be treated as repeated states.
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CaveState)) return false;
        CaveState s = (CaveState) other;
        return x == s.x && y == s.y && energy == s.energy && rope == s.rope
                && lives == s.lives && carrying == s.carrying
                && keysCollected == s.keysCollected && doorsOpened == s.doorsOpened;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, energy, rope, lives, carrying, keysCollected, doorsOpened);
    }
}
