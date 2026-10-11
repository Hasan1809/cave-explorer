package code;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// The cave problem. Stores the grid, doors and keys (these never change)
// and defines the actions, goal test and costs for the generic search.
public class CaveExplorer extends GenericSearchProblem {

    private static final List<String> OPERATORS = List.of(
            "left", "right", "climbup", "climbdown", "jumpdown", "collect", "unlock");

    private static final int STARTING_LIVES = 3;

    // Energy is at most 500, so making a life cost 1000 means losing fewer
    // lives is always better, and energy only breaks ties.
    static final int LIFE_COST = 1000;

    private final int[][] grid;     // grid[y][x] is the cell difficulty, 0 means wall
    private final int height;
    private final int width;
    private final int[][] doors;    // doors[i] = {x, y}
    private final int[][] keys;     // keys[i] = {x, y}
    private final int numDoors;
    private final int[][] doorAt;   // index of the door at (x, y), or -1
    private final int[][] keyAt;    // index of the key at (x, y), or -1
    private final CaveState initialState;

    // Input format: H,W;X,Y;EN,RL;row0;row1;...;doors;keys;
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

    // "4,0,7" -> {4, 0, 7}
    private static int[] parseInts(String text) {
        String[] tokens = text.split(",");
        int[] values = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            values[i] = Integer.parseInt(tokens[i].trim());
        }
        return values;
    }

    // "3,2,1,0" -> {{3, 2}, {1, 0}}
    private static int[][] parsePairs(String text) {
        int[] flat = parseInts(text);
        int[][] pairs = new int[flat.length / 2][];
        for (int i = 0; i < pairs.length; i++) {
            pairs[i] = new int[]{flat[2 * i], flat[2 * i + 1]};
        }
        return pairs;
    }

    // Grid-sized table so we can check if a cell has a door/key without looping
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

    // Returns the new state after applying the operator, or null if it is not allowed.
    // climbup/climbdown use 1 metre of rope, jumpdown uses 1 life (needs more than 1 left).
    // You can only hold one key at a time, and unlocking a door uses up the key.
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
                return s.lives > 1 ? move(s, 0, 1, s.rope, s.lives - 1) : null;
            case "collect": {
                int key = keyAt[s.y][s.x];
                boolean keyHereNotTaken = key != -1 && (s.keysCollected & (1 << key)) == 0;
                if (!keyHereNotTaken || s.carrying) {
                    return null;
                }
                return new CaveState(s.x, s.y, s.energy, s.rope, s.lives,
                        true, s.keysCollected | (1 << key), s.doorsOpened);
            }
            case "unlock": {
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

    // Used by all the movement actions. Moving into a cell costs its difficulty
    // in energy, so we can't enter walls or cells we don't have enough energy for.
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

    // Goal: all doors unlocked and still alive
    @Override
    public boolean goalTest(Object stateObject) {
        CaveState s = (CaveState) stateObject;
        int allDoors = (1 << numDoors) - 1;
        return s.doorsOpened == allDoors && s.lives >= 1;
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
     * A* heuristic: a lower bound on the energy we still have to spend.
     *
     * Entering a cell always costs its difficulty, and some cells can't be avoided:
     * every locked door has to be entered at least once to unlock it, so we add the
     * difficulty of each locked door (unless we are already standing on it).
     * Each unlock also uses up a key, so we still need (locked doors - 1 if we are
     * carrying a key) more keys, each from a different key cell. We add the
     * difficulties of the cheapest uncollected key cells, as many as we need
     * (a key we are standing on counts as 0).
     *
     * It is admissible because keys and doors are never on the same cell, so each
     * counted cell needs its own move into it, and the real path has to pay for all
     * of them plus the cells in between. Taking the cheapest keys and assuming no
     * lives are lost can only make the estimate smaller, never bigger than the real cost.
     *
     * It is based on energy (the actual cell difficulties) and not on distance.
     */
    @Override
    public int heuristic(Object stateObject) {
        CaveState s = (CaveState) stateObject;

        // locked doors
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

        // cheapest keys still needed
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

    // Returns "plan;livesUsed;energyUsed;nodesExpanded" or "No Solution"
    public static String solve(String initString, String strategy) {
        CaveExplorer problem = new CaveExplorer(initString);
        Node goal = problem.search(strategy);
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
