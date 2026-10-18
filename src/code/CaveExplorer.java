package code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// The cave explorer problem: parses the grid and defines the operators,
// goal test, step cost and heuristic used by the generic search.
public class CaveExplorer extends GenericSearchProblem {

    private static final List<String> OPERATORS = List.of(
            "left", "right", "climbup", "climbdown", "jumpdown", "collect", "unlock");

    private static final int STARTING_LIVES = 3;

    // energy is at most 500, so one life costs more than any amount of energy
    // (fewer lives first, energy only breaks ties)
    static final int LIFE_COST = 1000;

    private final int[][] grid;     // grid[y][x] = difficulty, 0 = wall
    private final int height;
    private final int width;
    private final int[][] doors;    // {x, y}
    private final int[][] keys;     // {x, y}
    private final int numDoors;
    private final int[][] doorAt;   // door index at each cell, -1 if none
    private final int[][] keyAt;    // key index at each cell, -1 if none
    private final CaveState initialState;

    // H,W;X,Y;EN,RL;row0;...;row(H-1);doors;keys;
    public CaveExplorer(String initString) {
        String[] parts = initString.split(";");

        int[] size = parseInts(parts[0]);
        height = size[0];
        width = size[1];

        int[] start = parseInts(parts[1]);
        int[] energyAndRope = parseInts(parts[2]);

        grid = new int[height][];
        for (int row = 0; row < height; row++) {
            grid[row] = parseInts(parts[3 + row]);
        }

        doors = parsePairs(parts[3 + height]);
        keys = parsePairs(parts[4 + height]);
        numDoors = doors.length;

        doorAt = indexTable(doors);
        keyAt = indexTable(keys);

        initialState = new CaveState(start[0], start[1], energyAndRope[0], energyAndRope[1],
                STARTING_LIVES, false, 0, 0);
    }

    private static int[] parseInts(String text) {
        String[] tokens = text.split(",");
        int[] values = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            values[i] = Integer.parseInt(tokens[i].trim());
        }
        return values;
    }

    // "3,2,1,0" -> {{3,2},{1,0}}
    private static int[][] parsePairs(String text) {
        int[] flat = parseInts(text);
        int[][] pairs = new int[flat.length / 2][];
        for (int i = 0; i < pairs.length; i++) {
            pairs[i] = new int[]{flat[2 * i], flat[2 * i + 1]};
        }
        return pairs;
    }

    private int[][] indexTable(int[][] positions) {
        int[][] table = new int[height][width];
        for (int[] row : table) {
            Arrays.fill(row, -1);
        }
        for (int i = 0; i < positions.length; i++) {
            table[positions[i][1]][positions[i][0]] = i;
        }
        return table;
    }

    @Override
    public Object getInitialState() {
        return initialState;
    }

    @Override
    public List<String> getOperators() {
        return OPERATORS;
    }

    // returns the next state, or null if the action isn't allowed here
    @Override
    public Object result(Object stateObject, String operator) {
        CaveState s = (CaveState) stateObject;
        switch (operator) {
            case "left":
                return move(s, -1, 0, s.rope, s.lives);
            case "right":
                return move(s, 1, 0, s.rope, s.lives);
            case "climbup":
                return s.rope > 0 ? move(s, 0, -1, s.rope - 1, s.lives) : null;
            case "climbdown":
                return s.rope > 0 ? move(s, 0, 1, s.rope - 1, s.lives) : null;
            case "jumpdown":
                // can't use the last life
                return s.lives > 1 ? move(s, 0, 1, s.rope, s.lives - 1) : null;
            case "collect": {
                // only one key can be carried at a time
                int key = keyAt[s.y][s.x];
                boolean keyHereNotTaken = key != -1 && (s.keysCollected & (1 << key)) == 0;
                if (!keyHereNotTaken || s.carrying) {
                    return null;
                }
                return new CaveState(s.x, s.y, s.energy, s.rope, s.lives,
                        true, s.keysCollected | (1 << key), s.doorsOpened);
            }
            case "unlock": {
                // needs a key, and the key is used up
                int door = doorAt[s.y][s.x];
                boolean lockedDoorHere = door != -1 && (s.doorsOpened & (1 << door)) == 0;
                if (!lockedDoorHere || !s.carrying) {
                    return null;
                }
                return new CaveState(s.x, s.y, s.energy, s.rope, s.lives,
                        false, s.keysCollected, s.doorsOpened | (1 << door));
            }
            default:
                return null;
        }
    }

    // moving into a cell costs its difficulty; walls and cells we can't afford are blocked
    private CaveState move(CaveState s, int dx, int dy, int newRope, int newLives) {
        int nx = s.x + dx;
        int ny = s.y + dy;
        if (nx < 0 || ny < 0 || ny >= height || nx >= grid[ny].length) {
            return null;
        }
        int difficulty = grid[ny][nx];
        if (difficulty == 0 || s.energy < difficulty) {
            return null;
        }
        return new CaveState(nx, ny, s.energy - difficulty, newRope, newLives,
                s.carrying, s.keysCollected, s.doorsOpened);
    }

    @Override
    public boolean goalTest(Object stateObject) {
        CaveState s = (CaveState) stateObject;
        int allDoors = (1 << numDoors) - 1;
        return s.doorsOpened == allDoors && s.lives >= 1;
    }

    // Repeated-state key for UC and A*: everything except energy.
    // Lives are in the key, so for the same key a lower path cost means more
    // energy left, and the more expensive copy can be dropped.
    // Bits: x(4) y(4) rope(5) lives(2) carrying(1) keys(8) doors(5)
    @Override
    public Object costSearchKey(Object stateObject) {
        CaveState s = (CaveState) stateObject;
        return s.x
                | s.y << 4
                | s.rope << 8
                | s.lives << 13
                | (s.carrying ? 1 : 0) << 15
                | s.keysCollected << 16
                | s.doorsOpened << 24;
    }

    // Repeated-state check for ID: same position, key and masks, where
    // more energy, rope or lives is never worse.
    // Bits: x(4) y(4) carrying(1) keys(8) doors(5)
    @Override
    public Object dominanceKey(Object stateObject) {
        CaveState s = (CaveState) stateObject;
        return s.x
                | s.y << 4
                | (s.carrying ? 1 : 0) << 8
                | s.keysCollected << 9
                | s.doorsOpened << 17;
    }

    @Override
    public int[] resources(Object stateObject) {
        CaveState s = (CaveState) stateObject;
        return new int[]{s.energy, s.rope, s.lives};
    }

    @Override
    public int stepCost(Object stateObject, String operator, Object nextObject) {
        CaveState s = (CaveState) stateObject;
        CaveState next = (CaveState) nextObject;
        int livesLost = s.lives - next.lives;
        int energySpent = s.energy - next.energy;
        return livesLost * LIFE_COST + energySpent;
    }

    /*
     * A* heuristic: a lower bound on the energy still needed.
     *
     * Every locked door has to be entered at least once to unlock it, so we add
     * the difficulty of each locked door cell (0 if we are standing on it).
     * Every unlock also uses a key, so we still need (locked doors - 1 if we are
     * carrying a key) keys from different key cells. We add the difficulties of
     * the cheapest uncollected key cells, as many as we need (0 for the cell we
     * are standing on).
     *
     * Admissible: a key and a door are never in the same cell, so each counted
     * cell needs its own move into it, and the real path pays for all of them
     * plus everything in between. Picking the cheapest keys and ignoring lives
     * (path cost = lives lost * LIFE_COST + energy) can only underestimate.
     * It is also consistent: entering a counted cell lowers h by exactly the
     * cost of that step, and collect/unlock don't change h.
     *
     * The estimate is based on the cells' energy costs, not on distance.
     */
    @Override
    public int heuristic(Object stateObject) {
        CaveState s = (CaveState) stateObject;

        int estimate = 0;
        int lockedDoors = 0;
        for (int i = 0; i < numDoors; i++) {
            if ((s.doorsOpened & (1 << i)) == 0) {
                lockedDoors++;
                boolean standingOnIt = doors[i][0] == s.x && doors[i][1] == s.y;
                if (!standingOnIt) {
                    estimate += grid[doors[i][1]][doors[i][0]];
                }
            }
        }

        int keysNeeded = lockedDoors - (s.carrying ? 1 : 0);
        if (keysNeeded > 0) {
            List<Integer> keyCosts = new ArrayList<>();
            for (int i = 0; i < keys.length; i++) {
                if ((s.keysCollected & (1 << i)) == 0) {
                    boolean standingOnIt = keys[i][0] == s.x && keys[i][1] == s.y;
                    keyCosts.add(standingOnIt ? 0 : grid[keys[i][1]][keys[i][0]]);
                }
            }
            Collections.sort(keyCosts);
            for (int i = 0; i < keysNeeded && i < keyCosts.size(); i++) {
                estimate += keyCosts.get(i);
            }
        }
        return estimate;
    }

    // returns "plan;livesUsed;energyUsed;nodesExpanded" or "No Solution"
    public static String solve(String initString, String strategy) {
        CaveExplorer problem = new CaveExplorer(initString);
        Node goal = GenericSearchProblem.search(problem, strategy);
        if (goal == null) {
            return "No Solution";
        }

        CaveState start = problem.initialState;
        CaveState end = (CaveState) goal.getState();
        String plan = String.join(",", goal.getPlan());
        int livesUsed = start.lives - end.lives;
        int energyUsed = start.energy - end.energy;

        return plan + ";" + livesUsed + ";" + energyUsed + ";" + problem.nodesExpanded;
    }
}
