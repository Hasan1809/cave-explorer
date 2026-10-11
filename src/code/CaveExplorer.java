package code;

import java.util.List;

/**
 * The cave explorer problem: a concrete GenericSearchProblem.
 *
 * This class holds the fixed parts of the cave (the grid, doors and keys)
 * and defines the rules of the game: what each of the 7 actions does,
 * when it is allowed, what it costs, and when the goal is reached.
 */
public class CaveExplorer extends GenericSearchProblem {

    /** All operators, in the order the search tries them. */
    private static final List<String> OPERATORS = List.of(
            "left", "right", "climbup", "climbdown", "jumpdown", "collect", "unlock");

    private static final int STARTING_LIVES = 3;

    /**
     * One life must always outweigh any amount of energy (energy is at most 500),
     * so the path cost is livesUsed * LIFE_COST + energyUsed. This makes the
     * search minimise lives first and energy second, as the assignment requires.
     */
    static final int LIFE_COST = 1000;

    private final int[][] grid;     // grid[y][x] = difficulty of the cell, 0 = wall
    private final int height;       // number of rows
    private final int width;        // number of columns
    private final int[][] doorAt;   // doorAt[y][x] = index of the door in that cell, or -1
    private final int[][] keyAt;    // keyAt[y][x]  = index of the key in that cell, or -1
    private final int numDoors;
    private final int[][] doors;    // doors[i] = {x, y} of door i
    private final int[][] keys;     // keys[i]  = {x, y} of key i
    private final CaveState initialState;

    /**
     * Parses the cave description string. Its format is
     * H,W;X,Y;EN,RL;row0;row1;...;row(H-1);doors;keys;
     * where every row lists the difficulty of each cell from left to right
     * and doors/keys are flat lists of x,y pairs.
     */
    public CaveExplorer(String initString) {
        String[] parts = initString.split(";");

        int[] size = parseInts(parts[0]);
        height = size[0];
        width = size[1];

        int[] start = parseInts(parts[1]);
        int[] resources = parseInts(parts[2]); // {energy, rope}

        grid = new int[height][];
        for (int row = 0; row < height; row++) {
            grid[row] = parseInts(parts[3 + row]);
        }

        doors = parsePairs(parts[3 + height]);
        keys = parsePairs(parts[4 + height]);
        numDoors = doors.length;

        // Lookup tables so the rules can ask "is there a door/key here?" instantly
        doorAt = indexTable(doors);
        keyAt = indexTable(keys);

        initialState = new CaveState(start[0], start[1], resources[0], resources[1],
                STARTING_LIVES, false, 0, 0);
    }

    /** Turns "4,0,7" into {4, 0, 7}. */
    private static int[] parseInts(String text) {
        String[] tokens = text.split(",");
        int[] values = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            values[i] = Integer.parseInt(tokens[i].trim());
        }
        return values;
    }

    /** Turns "3,2,1,0" into {{3, 2}, {1, 0}}: a list of (x, y) coordinates. */
    private static int[][] parsePairs(String text) {
        int[] flat = parseInts(text);
        int[][] pairs = new int[flat.length / 2][];
        for (int i = 0; i < pairs.length; i++) {
            pairs[i] = new int[]{flat[2 * i], flat[2 * i + 1]};
        }
        return pairs;
    }

    /** Builds a height x width table holding the index of the item in each cell, or -1. */
    private int[][] indexTable(int[][] positions) {
        int[][] table = new int[height][width];
        for (int[] row : table) {
            java.util.Arrays.fill(row, -1);
        }
        for (int i = 0; i < positions.length; i++) {
            table[positions[i][1]][positions[i][0]] = i;
        }
        return table;
    }

    // ------------------------------------------------------------------
    // The problem definition required by GenericSearchProblem
    // ------------------------------------------------------------------

    @Override
    public Object getInitialState() {
        return initialState;
    }

    @Override
    public List<String> getOperators() {
        return OPERATORS;
    }

    /**
     * Applies an operator, or returns null if the rules do not allow it.
     *
     *  left / right      : move 1 cell sideways; no rope or lives needed
     *  climbup/climbdown : move 1 cell up/down; uses 1 metre of rope
     *  jumpdown          : move 1 cell down; costs 1 life, only allowed with more than 1 life
     *  collect           : pick up the key here; only if not already holding one
     *  unlock            : open the door here using the key being held (the key is used up)
     *
     * Every move costs the difficulty of the destination cell in energy, and is
     * only allowed if the explorer has at least that much energy.
     */
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

    /**
     * Shared logic for all 5 movement actions: moves by (dx, dy) and pays the
     * destination cell's difficulty in energy. The caller has already decided
     * the new rope and lives values for its own action.
     * Returns null if the destination is outside the grid, a wall, or too expensive.
     */
    private CaveState move(CaveState s, int dx, int dy, int newRope, int newLives) {
        int nx = s.x + dx;
        int ny = s.y + dy;
        if (nx < 0 || ny < 0 || ny >= height || nx >= grid[ny].length) {
            return null; // outside the grid
        }
        int difficulty = grid[ny][nx];
        if (difficulty == 0 || s.energy < difficulty) {
            return null; // wall, or not enough energy to enter
        }
        return new CaveState(nx, ny, s.energy - difficulty, newRope, newLives,
                s.carrying, s.keysCollected, s.doorsOpened);
    }

    /** Goal: every door is unlocked and at least one life remains. */
    @Override
    public boolean goalTest(Object stateObject) {
        CaveState s = (CaveState) stateObject;
        int allDoors = (1 << numDoors) - 1;
        return s.doorsOpened == allDoors && s.lives >= 1;
    }

    /** Cost of one step: lives lost weigh LIFE_COST each, plus the energy spent. */
    @Override
    public int stepCost(Object stateObject, String operator, Object nextObject) {
        CaveState s = (CaveState) stateObject;
        CaveState next = (CaveState) nextObject;
        int livesLost = s.lives - next.lives;
        int energySpent = s.energy - next.energy;
        return livesLost * LIFE_COST + energySpent;
    }

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    /**
     * Solves the cave described by initString using the given strategy
     * ("UC", "ID" or "AS") and returns "plan;lives;energy;nodes",
     * or "No Solution" if the doors cannot all be opened.
     */
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
