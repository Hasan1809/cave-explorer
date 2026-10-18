package tests;

import code.CaveExplorer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Private-style tests for Assignment 1 (Cave Explorer), organised by the
 * requirements in the assignment PDF. Each section quotes the requirement it checks.
 *
 *  - ID tests       : the plan must be valid (the PDF does not require ID to be cost-optimal).
 *  - UC / AS tests  : the plan must be valid AND have the optimal (lives, energy).
 *  - "No Solution"  : solve(...) must return exactly "No Solution".
 *
 * Every expected value was computed by an independent reference solver, and every
 * reference plan was accepted by the official checker (checker-obf.jar).
 */
public class PrivateTests {

    private static final Duration TIME_LIMIT = Duration.ofSeconds(120);
    private static final Set<String> OPERATORS =
            Set.of("left", "right", "climbup", "climbdown", "jumpdown", "collect", "unlock");

    /** The plan must be accepted by the official checker. */
    private static void expectValid(String grid, String strategy) {
        Assertions.assertTimeoutPreemptively(TIME_LIMIT, () -> {
            String sol = CaveExplorer.solve(grid, strategy);
            Checker.ValidationResult v = Checker.validateSolution(grid, sol);
            assertTrue(v.isValid, "Solution failed: " + v.errorMessage + "\n  returned: " + sol);
        });
    }

    /** The plan must be valid AND reach the goal with the optimal cost. */
    private static void expectOptimal(String grid, String strategy, int lives, int energy) {
        Assertions.assertTimeoutPreemptively(TIME_LIMIT, () -> {
            String sol = CaveExplorer.solve(grid, strategy);
            Checker.ValidationResult v = Checker.validateSolution(grid, sol);
            assertTrue(v.isValid, "Solution failed: " + v.errorMessage + "\n  returned: " + sol);
            String[] parts = sol.split(";");
            int actualLives = Integer.parseInt(parts[1].trim());
            int actualEnergy = Integer.parseInt(parts[2].trim());
            assertTrue(actualLives == lives && actualEnergy == energy,
                    "Not optimal: expected lives=" + lives + ", energy=" + energy
                            + " but got lives=" + actualLives + ", energy=" + actualEnergy
                            + "\n  returned: " + sol);
        });
    }

    /** Unsolvable grid: must return exactly "No Solution". */
    private static void expectNoSolution(String grid, String strategy) {
        Assertions.assertTimeoutPreemptively(TIME_LIMIT, () -> {
            String sol = CaveExplorer.solve(grid, strategy);
            assertEquals("No Solution", sol, "Grid is unsolvable, expected \"No Solution\"");
        });
    }

    /** Output must be "plan;lives;energy;nodes" with only the 7 operator names in the plan. */
    private static void expectOutputFormat(String grid, String strategy) {
        Assertions.assertTimeoutPreemptively(TIME_LIMIT, () -> {
            String sol = CaveExplorer.solve(grid, strategy);
            String[] parts = sol.split(";", -1);
            assertTrue(parts.length == 4, "Expected exactly 4 parts plan;lives;energy;nodes, got: " + sol);
            for (String op : parts[0].split(",")) {
                assertTrue(OPERATORS.contains(op), "Unknown operator \"" + op + "\" in: " + sol);
            }
            int lives = Integer.parseInt(parts[1]);
            int energy = Integer.parseInt(parts[2]);
            int nodes = Integer.parseInt(parts[3]);
            assertTrue(lives >= 0 && lives <= 2, "Used lives must be 0..2 (must keep 1 of 3), got " + lives);
            assertTrue(energy >= 0, "Used energy must be >= 0, got " + energy);
            assertTrue(nodes > 0, "Expanded nodes must be > 0, got " + nodes);
        });
    }

    private static int nodes(String grid, String strategy) {
        return Integer.parseInt(CaveExplorer.solve(grid, strategy).split(";")[3]);
    }

    /** A real (non-trivial) admissible heuristic makes A* expand fewer nodes than UC. */
    private static void expectAStarFewerNodesThanUC(String grid) {
        Assertions.assertTimeoutPreemptively(TIME_LIMIT, () -> {
            int uc = nodes(grid, "UC");
            int as = nodes(grid, "AS");
            assertTrue(as < uc, "A* expanded " + as + " nodes, UC expanded " + uc
                    + ": the heuristic should make A* expand fewer");
        });
    }


    // =====================================================================
    // 1. Input format
    // =====================================================================
    /*
     * PDF: "H,W; X,Y; EN,RL; c0,0,...; ..." -- H rows, W columns; x is the column, y is the row;
     * "All x and y positions assume 0-indexing. The coordinates (0, 0) are the top-left corner."
     */

    // the PDF's own example grid (expected 1 life, 65 energy)
    private static final String G01 = "3,4;0,1;100,2;0,3,0,0;4,12,0,0;0,7,23,8;3,2;1,0;";
    //   optimal: 1 lives, 65 energy
    @Test @DisplayName("[1] the PDF's own example grid (expected 1 life, 65 energy) -- ID valid") public void S1_pdfExample_ID() { expectValid(G01, "ID"); }
    @Test @DisplayName("[1] the PDF's own example grid (expected 1 life, 65 energy) -- UC optimal 1;65") public void S1_pdfExample_UC() { expectOptimal(G01, "UC", 1, 65); }
    @Test @DisplayName("[1] the PDF's own example grid (expected 1 life, 65 energy) -- AS optimal 1;65") public void S1_pdfExample_AS() { expectOptimal(G01, "AS", 1, 65); }

    // start X=4 is only valid if X is the column
    private static final String G02 = "3,5;4,0;50,0;1,1,1,1,1;0,0,0,0,0;0,0,0,0,0;0,0;2,0;";
    //   optimal: 0 lives, 4 energy
    @Test @DisplayName("[1] start X=4 is only valid if X is the column -- ID valid") public void S1_xIsColumnYIsRow_ID() { expectValid(G02, "ID"); }
    @Test @DisplayName("[1] start X=4 is only valid if X is the column -- UC optimal 0;4") public void S1_xIsColumnYIsRow_UC() { expectOptimal(G02, "UC", 0, 4); }
    @Test @DisplayName("[1] start X=4 is only valid if X is the column -- AS optimal 0;4") public void S1_xIsColumnYIsRow_AS() { expectOptimal(G02, "AS", 0, 4); }

    // 5 rows x 3 columns (H = rows, W = columns)
    private static final String G03 = "5,3;0,0;100,10;1,1,1;1,0,1;1,1,1;1,0,1;1,1,1;2,4;0,4;";
    //   optimal: 0 lives, 6 energy
    @Test @DisplayName("[1] 5 rows x 3 columns (H = rows, W = columns) -- ID valid") public void S1_tall5x3_ID() { expectValid(G03, "ID"); }
    @Test @DisplayName("[1] 5 rows x 3 columns (H = rows, W = columns) -- UC optimal 0;6") public void S1_tall5x3_UC() { expectOptimal(G03, "UC", 0, 6); }
    @Test @DisplayName("[1] 5 rows x 3 columns (H = rows, W = columns) -- AS optimal 0;6") public void S1_tall5x3_AS() { expectOptimal(G03, "AS", 0, 6); }

    // 3 x 10 grid
    private static final String G04 = "3,10;4,2;500,25;12,17,9,8,6,16,13,19,7,6;13,3,20,5,17,7,6,10,7,7;7,13,12,5,11,20,20,11,0,10;4,1,4,0,0,2;7,2,1,2,7,1,3,0,2,0;";
    //   optimal: 0 lives, 113 energy
    @Test @DisplayName("[1] 3 x 10 grid -- ID valid") public void S1_wide3x10_ID() { expectValid(G04, "ID"); }
    @Test @DisplayName("[1] 3 x 10 grid -- UC optimal 0;113") public void S1_wide3x10_UC() { expectOptimal(G04, "UC", 0, 113); }
    @Test @DisplayName("[1] 3 x 10 grid -- AS optimal 0;113") public void S1_wide3x10_AS() { expectOptimal(G04, "AS", 0, 113); }

    // 10 x 3 grid
    private static final String G05 = "10,3;0,2;500,25;4,12,2;3,17,5;18,16,10;3,10,6;3,18,2;20,9,14;2,11,5;19,14,14;2,10,20;17,16,11;2,7,0,6,1,5;0,0,2,4,1,2,0,9,1,0;";
    //   optimal: 0 lives, 168 energy
    @Test @DisplayName("[1] 10 x 3 grid -- ID valid") public void S1_tall10x3_ID() { expectValid(G05, "ID"); }
    @Test @DisplayName("[1] 10 x 3 grid -- UC optimal 0;168") public void S1_tall10x3_UC() { expectOptimal(G05, "UC", 0, 168); }
    @Test @DisplayName("[1] 10 x 3 grid -- AS optimal 0;168") public void S1_tall10x3_AS() { expectOptimal(G05, "AS", 0, 168); }

    // smallest allowed grid, 3 x 3
    private static final String G06 = "3,3;0,0;10,0;1,1,1;1,1,1;1,1,1;2,0;0,0;";
    //   optimal: 0 lives, 2 energy
    @Test @DisplayName("[1] smallest allowed grid, 3 x 3 -- ID valid") public void S1_startOnKey_ID() { expectValid(G06, "ID"); }
    @Test @DisplayName("[1] smallest allowed grid, 3 x 3 -- UC optimal 0;2") public void S1_startOnKey_UC() { expectOptimal(G06, "UC", 0, 2); }
    @Test @DisplayName("[1] smallest allowed grid, 3 x 3 -- AS optimal 0;2") public void S1_startOnKey_AS() { expectOptimal(G06, "AS", 0, 2); }

    // largest allowed grid, 10 x 10
    private static final String G07 = "10,10;4,4;500,25;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;0,0,9,0,0,9,9,9,5,5;1,1,8,1,1,8,8,8,4,5,2,2,7,7,3,6;";
    //   optimal: 0 lives, 43 energy
    @Test @DisplayName("[1] largest allowed grid, 10 x 10 -- ID valid") public void S1_allDifficulty1_10x10_ID() { expectValid(G07, "ID"); }
    @Test @DisplayName("[1] largest allowed grid, 10 x 10 -- UC optimal 0;43") public void S1_allDifficulty1_10x10_UC() { expectOptimal(G07, "UC", 0, 43); }
    @Test @DisplayName("[1] largest allowed grid, 10 x 10 -- AS optimal 0;43") public void S1_allDifficulty1_10x10_AS() { expectOptimal(G07, "AS", 0, 43); }

    // =====================================================================
    // 2. Initial configuration ranges
    // =====================================================================
    /*
     * PDF: "You always start with 3 lives", "0 <= energy <= 500", "1 <= numberOfDoors <= 5",
     * "1 <= numberOfKeys <= 8", "numberOfDoors <= numberOfKeys", "0 <= ropeLength <= 25"
     */

    // energy = 0: the explorer can't move, so No Solution
    private static final String G08 = "3,3;0,0;0,5;1,1,1;1,1,1;1,1,1;2,0;0,0;";
    //   no solution
    @Test @DisplayName("[2] energy = 0: the explorer can't move, so No Solution -- ID No Solution") public void S2_zeroEnergy_ID() { expectNoSolution(G08, "ID"); }
    @Test @DisplayName("[2] energy = 0: the explorer can't move, so No Solution -- UC No Solution") public void S2_zeroEnergy_UC() { expectNoSolution(G08, "UC"); }
    @Test @DisplayName("[2] energy = 0: the explorer can't move, so No Solution -- AS No Solution") public void S2_zeroEnergy_AS() { expectNoSolution(G08, "AS"); }

    // upper limits: 10 x 10, 5 doors, 8 keys, energy 500, rope 25
    private static final String G09 = "10,10;4,9;500,25;10,0,10,0,15,6,7,17,20,14;18,13,13,11,11,5,18,4,17,19;0,16,0,7,10,18,0,13,11,8;0,8,19,14,0,0,5,19,0,0;12,0,2,5,14,0,11,4,18,20;19,20,0,0,6,14,0,5,4,19;0,0,16,2,5,12,12,16,19,0;7,20,11,14,4,5,0,4,12,11;10,8,3,5,11,11,8,9,15,0;11,10,16,9,19,0,13,19,11,16;1,9,4,1,7,9,6,3,5,0;4,2,0,8,7,4,8,6,1,7,0,5,8,1,5,2;";
    //   optimal: 0 lives, 332 energy
    @Test @DisplayName("[2] upper limits: 10 x 10, 5 doors, 8 keys, energy 500, rope 25 -- ID valid") public void S2_random10x10max_ID() { expectValid(G09, "ID"); }
    @Test @DisplayName("[2] upper limits: 10 x 10, 5 doors, 8 keys, energy 500, rope 25 -- UC optimal 0;332") public void S2_random10x10max_UC() { expectOptimal(G09, "UC", 0, 332); }
    @Test @DisplayName("[2] upper limits: 10 x 10, 5 doors, 8 keys, energy 500, rope 25 -- AS optimal 0;332") public void S2_random10x10max_AS() { expectOptimal(G09, "AS", 0, 332); }

    // keys = doors (2 and 2)
    private static final String G10 = "3,5;2,0;100,0;1,1,1,1,1;0,0,0,0,0;0,0,0,0,0;3,0,4,0;0,0,1,0;";
    //   optimal: 0 lives, 10 energy
    @Test @DisplayName("[2] keys = doors (2 and 2) -- ID valid") public void S2_oneKeyAtATime_ID() { expectValid(G10, "ID"); }
    @Test @DisplayName("[2] keys = doors (2 and 2) -- UC optimal 0;10") public void S2_oneKeyAtATime_UC() { expectOptimal(G10, "UC", 0, 10); }
    @Test @DisplayName("[2] keys = doors (2 and 2) -- AS optimal 0;10") public void S2_oneKeyAtATime_AS() { expectOptimal(G10, "AS", 0, 10); }

    // keys > doors
    private static final String G11 = "3,5;2,0;100,0;1,30,1,1,1;0,0,0,0,0;0,0,0,0,0;4,0;0,0,3,0;";
    //   optimal: 0 lives, 2 energy
    @Test @DisplayName("[2] keys > doors -- ID valid") public void S2_moreKeysThanDoors_ID() { expectValid(G11, "ID"); }
    @Test @DisplayName("[2] keys > doors -- UC optimal 0;2") public void S2_moreKeysThanDoors_UC() { expectOptimal(G11, "UC", 0, 2); }
    @Test @DisplayName("[2] keys > doors -- AS optimal 0;2") public void S2_moreKeysThanDoors_AS() { expectOptimal(G11, "AS", 0, 2); }

    // rope = 0 and starting with 3 lives: exactly 2 jumps are possible
    private static final String G12 = "3,3;1,0;100,0;0,4,0;0,5,0;0,6,0;1,2;1,1;";
    //   optimal: 2 lives, 11 energy
    @Test @DisplayName("[2] rope = 0 and starting with 3 lives: exactly 2 jumps are possible -- ID valid") public void S2_zeroRopeMustJump_ID() { expectValid(G12, "ID"); }
    @Test @DisplayName("[2] rope = 0 and starting with 3 lives: exactly 2 jumps are possible -- UC optimal 2;11") public void S2_zeroRopeMustJump_UC() { expectOptimal(G12, "UC", 2, 11); }
    @Test @DisplayName("[2] rope = 0 and starting with 3 lives: exactly 2 jumps are possible -- AS optimal 2;11") public void S2_zeroRopeMustJump_AS() { expectOptimal(G12, "AS", 2, 11); }

    // =====================================================================
    // 3a. Actions: left / right
    // =====================================================================
    /*
     * PDF: "only if the adjacent cell is not a wall and is within the grid boundaries. It requires the
     * agent to have enough [energy] for the destination cell (>= its difficulty). This action does
     * not cost any life or rope."
     */

    // energy exactly equal to the difficulty is enough
    private static final String G13 = "3,3;0,0;12,0;5,2,3;0,0,0;0,0,0;0,0;2,0;";
    //   optimal: 0 lives, 12 energy
    @Test @DisplayName("[3a] energy exactly equal to the difficulty is enough -- ID valid") public void S3a_exactEnergy_ID() { expectValid(G13, "ID"); }
    @Test @DisplayName("[3a] energy exactly equal to the difficulty is enough -- UC optimal 0;12") public void S3a_exactEnergy_UC() { expectOptimal(G13, "UC", 0, 12); }
    @Test @DisplayName("[3a] energy exactly equal to the difficulty is enough -- AS optimal 0;12") public void S3a_exactEnergy_AS() { expectOptimal(G13, "AS", 0, 12); }

    // 1 energy short: No Solution
    private static final String G14 = "3,3;0,0;11,0;5,2,3;0,0,0;0,0,0;0,0;2,0;";
    //   no solution
    @Test @DisplayName("[3a] 1 energy short: No Solution -- ID No Solution") public void S3a_energyOneShort_ID() { expectNoSolution(G14, "ID"); }
    @Test @DisplayName("[3a] 1 energy short: No Solution -- UC No Solution") public void S3a_energyOneShort_UC() { expectNoSolution(G14, "UC"); }
    @Test @DisplayName("[3a] 1 energy short: No Solution -- AS No Solution") public void S3a_energyOneShort_AS() { expectNoSolution(G14, "AS"); }

    // walls can't be entered: No Solution
    private static final String G15 = "3,3;0,0;100,5;1,1,0;1,1,0;0,0,5;2,2;0,0;";
    //   no solution
    @Test @DisplayName("[3a] walls can't be entered: No Solution -- ID No Solution") public void S3a_doorWalledOff_ID() { expectNoSolution(G15, "ID"); }
    @Test @DisplayName("[3a] walls can't be entered: No Solution -- UC No Solution") public void S3a_doorWalledOff_UC() { expectNoSolution(G15, "UC"); }
    @Test @DisplayName("[3a] walls can't be entered: No Solution -- AS No Solution") public void S3a_doorWalledOff_AS() { expectNoSolution(G15, "AS"); }

    // can't move outside the grid
    private static final String G16 = "3,3;2,2;100,4;1,1,1;1,0,0;1,1,1;0,0;2,0;";
    //   optimal: 0 lives, 8 energy
    @Test @DisplayName("[3a] can't move outside the grid -- ID valid") public void S3a_gridBoundaries_ID() { expectValid(G16, "ID"); }
    @Test @DisplayName("[3a] can't move outside the grid -- UC optimal 0;8") public void S3a_gridBoundaries_UC() { expectOptimal(G16, "UC", 0, 8); }
    @Test @DisplayName("[3a] can't move outside the grid -- AS optimal 0;8") public void S3a_gridBoundaries_AS() { expectOptimal(G16, "AS", 0, 8); }

    // a long corridor with no rope uses 0 lives
    private static final String G17 = "3,10;0,0;100,0;1,1,1,1,1,1,1,1,1,1;0,0,0,0,0,0,0,0,0,0;0,0,0,0,0,0,0,0,0,0;9,0;5,0;";
    //   optimal: 0 lives, 9 energy
    @Test @DisplayName("[3a] a long corridor with no rope uses 0 lives -- ID valid") public void S3a_leftRightNoRopeNoLives_ID() { expectValid(G17, "ID"); }
    @Test @DisplayName("[3a] a long corridor with no rope uses 0 lives -- UC optimal 0;9") public void S3a_leftRightNoRopeNoLives_UC() { expectOptimal(G17, "UC", 0, 9); }
    @Test @DisplayName("[3a] a long corridor with no rope uses 0 lives -- AS optimal 0;9") public void S3a_leftRightNoRopeNoLives_AS() { expectOptimal(G17, "AS", 0, 9); }

    // =====================================================================
    // 3b. Actions: climbup / climbdown
    // =====================================================================
    /*
     * PDF: "This action consumes 1 meter of the rope length, no lives and the energy of the destination cell."
     */

    // key is above and rope = 0: No Solution (no way up)
    private static final String G18 = "3,3;1,2;100,0;0,4,0;0,5,0;0,6,0;1,2;1,0;";
    //   no solution
    @Test @DisplayName("[3b] key is above and rope = 0: No Solution (no way up) -- ID No Solution") public void S3b_keyAboveNoRope_ID() { expectNoSolution(G18, "ID"); }
    @Test @DisplayName("[3b] key is above and rope = 0: No Solution (no way up) -- UC No Solution") public void S3b_keyAboveNoRope_UC() { expectNoSolution(G18, "UC"); }
    @Test @DisplayName("[3b] key is above and rope = 0: No Solution (no way up) -- AS No Solution") public void S3b_keyAboveNoRope_AS() { expectNoSolution(G18, "AS"); }

    // rope 4: up 2 and down 2 with 0 lives
    private static final String G19 = "3,3;1,2;100,4;0,4,0;0,5,0;0,6,0;1,2;1,0;";
    //   optimal: 0 lives, 20 energy
    @Test @DisplayName("[3b] rope 4: up 2 and down 2 with 0 lives -- ID valid") public void S3b_ropeEnoughUpDown_ID() { expectValid(G19, "ID"); }
    @Test @DisplayName("[3b] rope 4: up 2 and down 2 with 0 lives -- UC optimal 0;20") public void S3b_ropeEnoughUpDown_UC() { expectOptimal(G19, "UC", 0, 20); }
    @Test @DisplayName("[3b] rope 4: up 2 and down 2 with 0 lives -- AS optimal 0;20") public void S3b_ropeEnoughUpDown_AS() { expectOptimal(G19, "AS", 0, 20); }

    // rope 3: the last move down must be a jump (1 life)
    private static final String G20 = "3,3;1,2;100,3;0,4,0;0,5,0;0,6,0;1,2;1,0;";
    //   optimal: 1 lives, 20 energy
    @Test @DisplayName("[3b] rope 3: the last move down must be a jump (1 life) -- ID valid") public void S3b_ropeShortByOne_ID() { expectValid(G20, "ID"); }
    @Test @DisplayName("[3b] rope 3: the last move down must be a jump (1 life) -- UC optimal 1;20") public void S3b_ropeShortByOne_UC() { expectOptimal(G20, "UC", 1, 20); }
    @Test @DisplayName("[3b] rope 3: the last move down must be a jump (1 life) -- AS optimal 1;20") public void S3b_ropeShortByOne_AS() { expectOptimal(G20, "AS", 1, 20); }

    // climbdown pays the destination's difficulty, 0 lives
    private static final String G21 = "3,3;1,0;100,1;0,5,0;0,9,0;0,0,0;1,1;1,0;";
    //   optimal: 0 lives, 9 energy
    @Test @DisplayName("[3b] climbdown pays the destination's difficulty, 0 lives -- ID valid") public void S3b_climbdownCostsDestination_ID() { expectValid(G21, "ID"); }
    @Test @DisplayName("[3b] climbdown pays the destination's difficulty, 0 lives -- UC optimal 0;9") public void S3b_climbdownCostsDestination_UC() { expectOptimal(G21, "UC", 0, 9); }
    @Test @DisplayName("[3b] climbdown pays the destination's difficulty, 0 lives -- AS optimal 0;9") public void S3b_climbdownCostsDestination_AS() { expectOptimal(G21, "AS", 0, 9); }

    // =====================================================================
    // 3c. Actions: jumpdown
    // =====================================================================
    /*
     * PDF: "This action can only be done if the agent has more than one life left (it costs one life
     * and no rope needed)."
     */

    // jumpdown pays the destination's difficulty and 1 life
    private static final String G22 = "3,3;1,0;100,0;0,5,0;0,9,0;0,0,0;1,1;1,0;";
    //   optimal: 1 lives, 9 energy
    @Test @DisplayName("[3c] jumpdown pays the destination's difficulty and 1 life -- ID valid") public void S3c_jumpdownCostsDestination_ID() { expectValid(G22, "ID"); }
    @Test @DisplayName("[3c] jumpdown pays the destination's difficulty and 1 life -- UC optimal 1;9") public void S3c_jumpdownCostsDestination_UC() { expectOptimal(G22, "UC", 1, 9); }
    @Test @DisplayName("[3c] jumpdown pays the destination's difficulty and 1 life -- AS optimal 1;9") public void S3c_jumpdownCostsDestination_AS() { expectOptimal(G22, "AS", 1, 9); }

    // 1 m of rope + 2 jumps
    private static final String G23 = "4,3;1,0;100,1;0,4,0;0,5,0;0,6,0;0,7,0;1,3;1,1;";
    //   optimal: 2 lives, 18 energy
    @Test @DisplayName("[3c] 1 m of rope + 2 jumps -- ID valid") public void S3c_ropeOneThenJumps_ID() { expectValid(G23, "ID"); }
    @Test @DisplayName("[3c] 1 m of rope + 2 jumps -- UC optimal 2;18") public void S3c_ropeOneThenJumps_UC() { expectOptimal(G23, "UC", 2, 18); }
    @Test @DisplayName("[3c] 1 m of rope + 2 jumps -- AS optimal 2;18") public void S3c_ropeOneThenJumps_AS() { expectOptimal(G23, "AS", 2, 18); }

    // both moves down are jumps (2 lives)
    private static final String G24 = "3,3;1,2;100,2;0,4,0;0,5,0;0,6,0;1,2;1,0;";
    //   optimal: 2 lives, 20 energy
    @Test @DisplayName("[3c] both moves down are jumps (2 lives) -- ID valid") public void S3c_ropeShortByTwo_ID() { expectValid(G24, "ID"); }
    @Test @DisplayName("[3c] both moves down are jumps (2 lives) -- UC optimal 2;20") public void S3c_ropeShortByTwo_UC() { expectOptimal(G24, "UC", 2, 20); }
    @Test @DisplayName("[3c] both moves down are jumps (2 lives) -- AS optimal 2;20") public void S3c_ropeShortByTwo_AS() { expectOptimal(G24, "AS", 2, 20); }

    // needs 3 jumps but the last life can't be used: No Solution
    private static final String G25 = "4,3;1,0;100,0;0,4,0;0,5,0;0,6,0;0,7,0;1,3;1,1;";
    //   no solution
    @Test @DisplayName("[3c] needs 3 jumps but the last life can't be used: No Solution -- ID No Solution") public void S3c_threeJumpsNeeded_ID() { expectNoSolution(G25, "ID"); }
    @Test @DisplayName("[3c] needs 3 jumps but the last life can't be used: No Solution -- UC No Solution") public void S3c_threeJumpsNeeded_UC() { expectNoSolution(G25, "UC"); }
    @Test @DisplayName("[3c] needs 3 jumps but the last life can't be used: No Solution -- AS No Solution") public void S3c_threeJumpsNeeded_AS() { expectNoSolution(G25, "AS"); }

    // 10 x 10 with no rope and 9 rows to go down: No Solution
    private static final String G26 = "10,10;0,0;500,0;3,3,3,3,3,3,3,3,3,3;3,3,3,3,3,3,3,3,3,3;3,3,3,3,3,3,3,3,3,3;3,3,3,3,3,3,3,3,3,3;3,3,3,3,3,3,3,3,3,3;3,3,3,3,3,3,3,3,3,3;3,3,3,3,3,3,3,3,3,3;3,3,3,3,3,3,3,3,3,3;3,3,3,3,3,3,3,3,3,3;3,3,3,3,3,3,3,3,3,3;9,9;5,0;";
    //   no solution
    @Test @DisplayName("[3c] 10 x 10 with no rope and 9 rows to go down: No Solution -- ID No Solution") public void S3c_big10x10_tooManyJumps_ID() { expectNoSolution(G26, "ID"); }
    @Test @DisplayName("[3c] 10 x 10 with no rope and 9 rows to go down: No Solution -- UC No Solution") public void S3c_big10x10_tooManyJumps_UC() { expectNoSolution(G26, "UC"); }
    @Test @DisplayName("[3c] 10 x 10 with no rope and 9 rows to go down: No Solution -- AS No Solution") public void S3c_big10x10_tooManyJumps_AS() { expectNoSolution(G26, "AS"); }

    // =====================================================================
    // 3d. Actions: collect
    // =====================================================================
    /*
     * PDF: "The cave explorer picks up key from the current location. They can only carry up one key at a time."
     */

    // collect works on the start cell
    private static final String G27 = "3,3;0,0;10,0;1,1,1;1,1,1;1,1,1;2,0;0,0;";
    //   optimal: 0 lives, 2 energy
    @Test @DisplayName("[3d] collect works on the start cell -- ID valid") public void S3d_startOnKey_ID() { expectValid(G27, "ID"); }
    @Test @DisplayName("[3d] collect works on the start cell -- UC optimal 0;2") public void S3d_startOnKey_UC() { expectOptimal(G27, "UC", 0, 2); }
    @Test @DisplayName("[3d] collect works on the start cell -- AS optimal 0;2") public void S3d_startOnKey_AS() { expectOptimal(G27, "AS", 0, 2); }

    // two keys in a row: must unlock a door before taking the second key
    private static final String G28 = "3,5;2,0;100,0;1,1,1,1,1;0,0,0,0,0;0,0,0,0,0;3,0,4,0;0,0,1,0;";
    //   optimal: 0 lives, 10 energy
    @Test @DisplayName("[3d] two keys in a row: must unlock a door before taking the second key -- ID valid") public void S3d_oneKeyAtATime_ID() { expectValid(G28, "ID"); }
    @Test @DisplayName("[3d] two keys in a row: must unlock a door before taking the second key -- UC optimal 0;10") public void S3d_oneKeyAtATime_UC() { expectOptimal(G28, "UC", 0, 10); }
    @Test @DisplayName("[3d] two keys in a row: must unlock a door before taking the second key -- AS optimal 0;10") public void S3d_oneKeyAtATime_AS() { expectOptimal(G28, "AS", 0, 10); }

    // extra keys: the optimal plan picks the cheaper key
    private static final String G29 = "3,5;2,0;100,0;1,30,1,1,1;0,0,0,0,0;0,0,0,0,0;4,0;0,0,3,0;";
    //   optimal: 0 lives, 2 energy
    @Test @DisplayName("[3d] extra keys: the optimal plan picks the cheaper key -- ID valid") public void S3d_moreKeysThanDoors_ID() { expectValid(G29, "ID"); }
    @Test @DisplayName("[3d] extra keys: the optimal plan picks the cheaper key -- UC optimal 0;2") public void S3d_moreKeysThanDoors_UC() { expectOptimal(G29, "UC", 0, 2); }
    @Test @DisplayName("[3d] extra keys: the optimal plan picks the cheaper key -- AS optimal 0;2") public void S3d_moreKeysThanDoors_AS() { expectOptimal(G29, "AS", 0, 2); }

    // =====================================================================
    // 3e. Actions: unlock
    // =====================================================================
    /*
     * PDF: "This can only be done if the cave explorer is already carrying a key."
     */

    // starts on a door without a key: must fetch a key and come back
    private static final String G30 = "3,3;0,0;10,0;1,1,1;1,1,1;1,1,1;0,0;2,0;";
    //   optimal: 0 lives, 4 energy
    @Test @DisplayName("[3e] starts on a door without a key: must fetch a key and come back -- ID valid") public void S3e_startOnDoor_ID() { expectValid(G30, "ID"); }
    @Test @DisplayName("[3e] starts on a door without a key: must fetch a key and come back -- UC optimal 0;4") public void S3e_startOnDoor_UC() { expectOptimal(G30, "UC", 0, 4); }
    @Test @DisplayName("[3e] starts on a door without a key: must fetch a key and come back -- AS optimal 0;4") public void S3e_startOnDoor_AS() { expectOptimal(G30, "AS", 0, 4); }

    // walking over a locked door without a key is allowed
    private static final String G31 = "3,4;0,0;100,0;1,1,1,1;0,0,0,0;0,0,0,0;1,0,3,0;2,0,0,0;";
    //   optimal: 0 lives, 3 energy
    @Test @DisplayName("[3e] walking over a locked door without a key is allowed -- ID valid") public void S3e_walkOverLockedDoor_ID() { expectValid(G31, "ID"); }
    @Test @DisplayName("[3e] walking over a locked door without a key is allowed -- UC optimal 0;3") public void S3e_walkOverLockedDoor_UC() { expectOptimal(G31, "UC", 0, 3); }
    @Test @DisplayName("[3e] walking over a locked door without a key is allowed -- AS optimal 0;3") public void S3e_walkOverLockedDoor_AS() { expectOptimal(G31, "AS", 0, 3); }

    // =====================================================================
    // 4. Goal
    // =====================================================================
    /*
     * PDF: "The agent has succeeded when there are no more doors to be unlocked and the agent has at least
     * 1 remaining life."
     */

    // 2 doors but only 1 reachable key: No Solution
    private static final String G32 = "3,5;0,0;100,0;1,1,1,1,1;0,0,0,0,0;1,1,1,1,1;2,0,4,0;0,0,4,2;";
    //   no solution
    @Test @DisplayName("[4] 2 doors but only 1 reachable key: No Solution -- ID No Solution") public void S4_notEnoughKeysReachable_ID() { expectNoSolution(G32, "ID"); }
    @Test @DisplayName("[4] 2 doors but only 1 reachable key: No Solution -- UC No Solution") public void S4_notEnoughKeysReachable_UC() { expectNoSolution(G32, "UC"); }
    @Test @DisplayName("[4] 2 doors but only 1 reachable key: No Solution -- AS No Solution") public void S4_notEnoughKeysReachable_AS() { expectNoSolution(G32, "AS"); }

    // 10 x 10, 5 doors but only 4 reachable keys: No Solution
    private static final String G33 = "10,10;0,9;500,25;1,2,3,4,5,6,7,1,0,3;1,2,3,4,5,6,7,1,0,0;1,2,3,4,5,6,7,1,2,3;1,2,3,4,5,6,7,1,2,3;1,2,3,4,5,6,7,1,2,3;1,2,3,4,5,6,7,1,2,3;1,2,3,4,5,6,7,1,2,3;1,2,3,4,5,6,7,1,2,3;1,2,3,4,5,6,7,1,2,3;1,2,3,4,5,6,7,1,2,3;1,1,3,3,5,5,7,7,2,8;0,2,4,4,6,6,1,8,9,0;";
    //   no solution
    @Test @DisplayName("[4] 10 x 10, 5 doors but only 4 reachable keys: No Solution -- ID No Solution") public void S4_big10x10_keySealed_ID() { expectNoSolution(G33, "ID"); }
    @Test @DisplayName("[4] 10 x 10, 5 doors but only 4 reachable keys: No Solution -- UC No Solution") public void S4_big10x10_keySealed_UC() { expectNoSolution(G33, "UC"); }
    @Test @DisplayName("[4] 10 x 10, 5 doors but only 4 reachable keys: No Solution -- AS No Solution") public void S4_big10x10_keySealed_AS() { expectNoSolution(G33, "AS"); }

    // 10 x 10, the door is sealed by walls: No Solution
    private static final String G34 = "10,10;0,0;500,25;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,0,0;1,1,1,1,1,1,1,1,0,1;9,9;1,1,5,5,2,7;";
    //   no solution
    @Test @DisplayName("[4] 10 x 10, the door is sealed by walls: No Solution -- ID No Solution") public void S4_big10x10_doorSealed_ID() { expectNoSolution(G34, "ID"); }
    @Test @DisplayName("[4] 10 x 10, the door is sealed by walls: No Solution -- UC No Solution") public void S4_big10x10_doorSealed_UC() { expectNoSolution(G34, "UC"); }
    @Test @DisplayName("[4] 10 x 10, the door is sealed by walls: No Solution -- AS No Solution") public void S4_big10x10_doorSealed_AS() { expectNoSolution(G34, "AS"); }

    // 5 doors and 8 keys but 500 energy is not enough for all doors: No Solution
    private static final String G35 = "10,10;7,4;500,25;22,19,9,37,24,11,37,13,39,43;7,40,21,37,28,32,46,11,17,20;27,20,19,2,40,16,9,1,36,31;16,10,19,39,34,39,8,9,35,40;34,23,48,9,17,44,44,1,28,45;41,25,44,13,30,5,1,28,42,20;31,32,35,38,4,15,6,43,22,5;45,8,15,8,9,46,10,12,11,14;7,48,4,8,40,28,44,32,42,4;19,41,19,36,15,36,49,9,24,25;8,9,3,5,6,2,0,6,8,3;2,3,7,0,4,9,5,0,1,0,0,2,5,7,1,3;";
    //   no solution
    @Test @DisplayName("[4] 5 doors and 8 keys but 500 energy is not enough for all doors: No Solution -- ID No Solution") public void S4_maxDoorsKeys_noWalls_ID() { expectNoSolution(G35, "ID"); }
    @Test @DisplayName("[4] 5 doors and 8 keys but 500 energy is not enough for all doors: No Solution -- UC No Solution") public void S4_maxDoorsKeys_noWalls_UC() { expectNoSolution(G35, "UC"); }
    @Test @DisplayName("[4] 5 doors and 8 keys but 500 energy is not enough for all doors: No Solution -- AS No Solution") public void S4_maxDoorsKeys_noWalls_AS() { expectNoSolution(G35, "AS"); }

    // =====================================================================
    // 5. Optimality: fewest lives first, then least energy
    // =====================================================================
    /*
     * PDF: "Optimally, you would like to minimize the number of lives used and secondarily the energy
     * consumed (less lives used is better even if energy used is higher)."
     */

    // 0 lives / 271 energy must beat 1 life / 6 energy
    private static final String G36 = "3,5;0,1;300,1;1,1,1,1,1;1,90,90,90,1;0,0,0,0,0;4,1;0,1;";
    //   optimal: 0 lives, 271 energy
    @Test @DisplayName("[5] 0 lives / 271 energy must beat 1 life / 6 energy -- ID valid") public void S5_livesBeforeEnergy_ID() { expectValid(G36, "ID"); }
    @Test @DisplayName("[5] 0 lives / 271 energy must beat 1 life / 6 energy -- UC optimal 0;271") public void S5_livesBeforeEnergy_UC() { expectOptimal(G36, "UC", 0, 271); }
    @Test @DisplayName("[5] 0 lives / 271 energy must beat 1 life / 6 energy -- AS optimal 0;271") public void S5_livesBeforeEnergy_AS() { expectOptimal(G36, "AS", 0, 271); }

    // same grid, too little energy for the safe path: 1 life is optimal
    private static final String G37 = "3,5;0,1;200,1;1,1,1,1,1;1,90,90,90,1;0,0,0,0,0;4,1;0,1;";
    //   optimal: 1 lives, 6 energy
    @Test @DisplayName("[5] same grid, too little energy for the safe path: 1 life is optimal -- ID valid") public void S5_forcedJumpLowEnergy_ID() { expectValid(G37, "ID"); }
    @Test @DisplayName("[5] same grid, too little energy for the safe path: 1 life is optimal -- UC optimal 1;6") public void S5_forcedJumpLowEnergy_UC() { expectOptimal(G37, "UC", 1, 6); }
    @Test @DisplayName("[5] same grid, too little energy for the safe path: 1 life is optimal -- AS optimal 1;6") public void S5_forcedJumpLowEnergy_AS() { expectOptimal(G37, "AS", 1, 6); }

    // a longer but cheaper path is optimal
    private static final String G38 = "3,5;0,0;500,6;1,60,60,60,1;1,0,0,0,1;1,1,1,1,1;4,0;0,0;";
    //   optimal: 0 lives, 8 energy
    @Test @DisplayName("[5] a longer but cheaper path is optimal -- ID valid") public void S5_cheapDetour_ID() { expectValid(G38, "ID"); }
    @Test @DisplayName("[5] a longer but cheaper path is optimal -- UC optimal 0;8") public void S5_cheapDetour_UC() { expectOptimal(G38, "UC", 0, 8); }
    @Test @DisplayName("[5] a longer but cheaper path is optimal -- AS optimal 0;8") public void S5_cheapDetour_AS() { expectOptimal(G38, "AS", 0, 8); }

    // large grid where the optimum uses lives
    private static final String G39 = "8,8;3,4;312,2;13,8,5,1,5,13,14,0;10,1,5,16,6,1,9,14;14,14,0,9,0,3,19,0;11,14,3,6,18,8,8,14;9,6,7,19,6,0,13,6;14,11,3,4,19,8,17,13;0,8,6,18,2,6,3,7;0,0,1,3,13,14,6,0;3,7,6,5;2,6,5,5,6,2,3,6,2,5;";
    //   optimal: 1 lives, 80 energy
    @Test @DisplayName("[5] large grid where the optimum uses lives -- ID valid") public void S5_livesBig00_ID() { expectValid(G39, "ID"); }
    @Test @DisplayName("[5] large grid where the optimum uses lives -- UC optimal 1;80") public void S5_livesBig00_UC() { expectOptimal(G39, "UC", 1, 80); }
    @Test @DisplayName("[5] large grid where the optimum uses lives -- AS optimal 1;80") public void S5_livesBig00_AS() { expectOptimal(G39, "AS", 1, 80); }

    // large grid where the optimum uses lives
    private static final String G40 = "7,9;6,1;253,2;11,6,5,14,0,0,14,2,19;12,18,10,0,1,5,7,5,6;0,11,3,5,15,13,19,0,3;15,18,13,3,11,0,3,13,3;2,6,10,7,20,0,2,7,18;1,1,6,0,15,11,10,1,14;19,0,18,19,17,10,13,11,15;2,3,8,5;7,6,1,4,8,1,5,6,5,1,8,2,5,2;";
    //   optimal: 2 lives, 146 energy
    @Test @DisplayName("[5] large grid where the optimum uses lives -- ID valid") public void S5_livesBig03_ID() { expectValid(G40, "ID"); }
    @Test @DisplayName("[5] large grid where the optimum uses lives -- UC optimal 2;146") public void S5_livesBig03_UC() { expectOptimal(G40, "UC", 2, 146); }
    @Test @DisplayName("[5] large grid where the optimum uses lives -- AS optimal 2;146") public void S5_livesBig03_AS() { expectOptimal(G40, "AS", 2, 146); }

    // large grid where the optimum uses lives
    private static final String G41 = "9,8;4,4;279,3;16,12,13,15,6,8,0,0;9,0,4,0,0,0,9,13;13,5,20,0,6,17,0,8;12,20,19,16,10,2,20,0;12,19,10,6,5,16,20,20;8,0,14,14,12,14,8,8;18,0,4,8,6,0,18,3;4,19,8,11,12,14,14,0;12,17,8,19,0,20,10,6;2,4,3,6;5,3,4,6,6,6,5,8,3,3,7,5;";
    //   optimal: 1 lives, 75 energy
    @Test @DisplayName("[5] large grid where the optimum uses lives -- ID valid") public void S5_livesBig06_ID() { expectValid(G41, "ID"); }
    @Test @DisplayName("[5] large grid where the optimum uses lives -- UC optimal 1;75") public void S5_livesBig06_UC() { expectOptimal(G41, "UC", 1, 75); }
    @Test @DisplayName("[5] large grid where the optimum uses lives -- AS optimal 1;75") public void S5_livesBig06_AS() { expectOptimal(G41, "AS", 1, 75); }

    // large grid where the optimum uses lives
    private static final String G42 = "7,8;5,4;336,0;17,10,19,13,4,20,2,0;11,18,19,0,3,4,20,1;0,11,7,19,6,20,17,4;15,0,10,0,1,20,5,14;13,17,9,17,13,9,14,19;10,4,15,14,17,1,0,0;17,10,5,2,5,14,0,8;2,5,0,6,7,4;1,0,4,5,5,6,6,4,4,3;";
    //   optimal: 2 lives, 168 energy
    @Test @DisplayName("[5] large grid where the optimum uses lives -- ID valid") public void S5_livesBig09_ID() { expectValid(G42, "ID"); }
    @Test @DisplayName("[5] large grid where the optimum uses lives -- UC optimal 2;168") public void S5_livesBig09_UC() { expectOptimal(G42, "UC", 2, 168); }
    @Test @DisplayName("[5] large grid where the optimum uses lives -- AS optimal 2;168") public void S5_livesBig09_AS() { expectOptimal(G42, "AS", 2, 168); }

    // random grid within the PDF ranges
    private static final String G43 = "8,9;1,5;43,9;9,7,2,45,37,0,35,34,43;0,6,0,36,0,0,13,0,0;23,0,48,39,28,50,35,0,12;10,0,33,46,1,5,14,39,9;0,7,47,0,0,8,32,0,23;30,23,6,6,50,49,11,33,21;47,32,12,0,45,49,27,38,10;37,9,22,21,0,17,0,15,10;2,5;2,7,4,0,5,5;";
    //   no solution
    @Test @DisplayName("[5] random grid within the PDF ranges -- ID No Solution") public void S5_rand004_ID() { expectNoSolution(G43, "ID"); }
    @Test @DisplayName("[5] random grid within the PDF ranges -- UC No Solution") public void S5_rand004_UC() { expectNoSolution(G43, "UC"); }
    @Test @DisplayName("[5] random grid within the PDF ranges -- AS No Solution") public void S5_rand004_AS() { expectNoSolution(G43, "AS"); }

    // random grid within the PDF ranges
    private static final String G44 = "6,7;2,0;318,4;10,0,16,3,3,1,3;8,9,4,4,3,15,5;7,4,19,12,6,16,0;5,10,18,0,8,16,2;5,13,4,4,14,0,10;13,2,10,3,7,1,0;3,0,4,3,0,5,4,0;2,1,5,0,0,1,2,3,0,0,5,2,3,5,1,5;";
    //   no solution
    @Test @DisplayName("[5] random grid within the PDF ranges -- ID No Solution") public void S5_rand010_ID() { expectNoSolution(G44, "ID"); }
    @Test @DisplayName("[5] random grid within the PDF ranges -- UC No Solution") public void S5_rand010_UC() { expectNoSolution(G44, "UC"); }
    @Test @DisplayName("[5] random grid within the PDF ranges -- AS No Solution") public void S5_rand010_AS() { expectNoSolution(G44, "AS"); }

    // random grid within the PDF ranges
    private static final String G45 = "3,9;3,2;450,6;5,4,0,1,2,2,5,2,2;0,4,3,0,2,2,4,1,4;1,3,4,3,1,2,1,5,0;5,1;6,0,5,0,8,1,4,2,1,1,2,2,4,1,5,2;";
    //   optimal: 0 lives, 5 energy
    @Test @DisplayName("[5] random grid within the PDF ranges -- ID valid") public void S5_rand050_ID() { expectValid(G45, "ID"); }
    @Test @DisplayName("[5] random grid within the PDF ranges -- UC optimal 0;5") public void S5_rand050_UC() { expectOptimal(G45, "UC", 0, 5); }
    @Test @DisplayName("[5] random grid within the PDF ranges -- AS optimal 0;5") public void S5_rand050_AS() { expectOptimal(G45, "AS", 0, 5); }

    // =====================================================================
    // 6. Search strategies terminate within the time limits
    // =====================================================================
    /*
     * PDF: "make sure that your implementation of search minimizes or eliminates redundant states and that
     * all your search strategies terminate within the time limits"
     */

    // 8 x 8, 3 doors, 5 keys
    private static final String G46 = "8,8;6,3;450,15;9,14,14,13,11,16,10,4;0,1,2,9,1,12,20,8;16,2,1,15,9,0,20,5;3,4,15,3,5,17,16,7;0,6,1,14,2,4,2,4;5,12,0,4,4,2,14,0;19,5,3,6,9,0,8,16;11,12,8,2,9,11,11,6;1,1,5,4,6,5;0,3,6,2,2,6,1,4,3,2;";
    //   optimal: 0 lives, 117 energy
    @Test @DisplayName("[6] 8 x 8, 3 doors, 5 keys -- ID valid") public void S6_random8x8_ID() { expectValid(G46, "ID"); }
    @Test @DisplayName("[6] 8 x 8, 3 doors, 5 keys -- UC optimal 0;117") public void S6_random8x8_UC() { expectOptimal(G46, "UC", 0, 117); }
    @Test @DisplayName("[6] 8 x 8, 3 doors, 5 keys -- AS optimal 0;117") public void S6_random8x8_AS() { expectOptimal(G46, "AS", 0, 117); }

    // 10 x 10
    private static final String G47 = "10,10;2,2;500,20;0,12,5,4,16,5,8,11,1,19;0,9,0,15,3,0,5,9,13,9;0,10,20,20,14,9,7,4,15,8;6,4,12,16,17,2,13,10,16,16;11,10,8,18,3,4,0,0,18,11;15,6,4,11,19,20,15,13,15,0;7,11,4,0,14,10,0,0,3,0;19,4,19,13,14,12,16,7,7,4;0,11,20,9,2,11,14,8,9,5;8,19,13,1,12,11,19,18,0,7;0,7,5,6,5,9;3,2,4,6,6,0,7,3;";
    //   optimal: 0 lives, 280 energy
    @Test @DisplayName("[6] 10 x 10 -- ID valid") public void S6_random10x10a_ID() { expectValid(G47, "ID"); }
    @Test @DisplayName("[6] 10 x 10 -- UC optimal 0;280") public void S6_random10x10a_UC() { expectOptimal(G47, "UC", 0, 280); }
    @Test @DisplayName("[6] 10 x 10 -- AS optimal 0;280") public void S6_random10x10a_AS() { expectOptimal(G47, "AS", 0, 280); }

    // 10 x 10, 4 doors, 6 keys
    private static final String G48 = "10,10;3,2;500,25;10,7,14,14,0,19,0,3,19,2;0,0,0,12,4,20,8,1,10,15;14,15,18,16,15,17,12,11,0,16;1,7,3,17,15,4,9,14,11,17;13,10,7,16,19,15,1,8,2,9;19,0,19,6,17,0,0,11,12,20;0,18,7,6,16,5,4,0,10,3;3,2,8,0,0,4,9,10,15,6;7,0,0,2,15,4,0,13,13,14;0,0,11,16,8,18,8,17,16,20;3,5,9,3,3,8,0,7;7,7,2,5,1,3,1,4,9,6,6,9;";
    //   optimal: 0 lives, 311 energy
    @Test @DisplayName("[6] 10 x 10, 4 doors, 6 keys -- ID valid") public void S6_random10x10b_ID() { expectValid(G48, "ID"); }
    @Test @DisplayName("[6] 10 x 10, 4 doors, 6 keys -- UC optimal 0;311") public void S6_random10x10b_UC() { expectOptimal(G48, "UC", 0, 311); }
    @Test @DisplayName("[6] 10 x 10, 4 doors, 6 keys -- AS optimal 0;311") public void S6_random10x10b_AS() { expectOptimal(G48, "AS", 0, 311); }

    // 10 x 10 with many walls
    private static final String G49 = "10,10;0,9;500,25;8,0,12,12,0,20,0,5,0,10;8,6,0,19,1,0,0,12,6,10;0,15,0,0,2,14,9,0,0,14;0,0,17,4,8,16,0,8,16,0;0,0,0,0,4,0,6,13,19,0;4,17,6,7,15,16,0,17,1,10;8,5,0,0,13,0,0,0,0,0;0,8,6,8,0,20,0,0,0,0;0,18,0,0,5,15,0,1,14,0;13,11,0,0,0,10,0,18,0,0;8,5,9,0,4,1,1,8;4,4,1,5,5,0,8,4,5,7,2,7,2,5;";
    //   no solution
    @Test @DisplayName("[6] 10 x 10 with many walls -- ID No Solution") public void S6_heavyWalls10x10_ID() { expectNoSolution(G49, "ID"); }
    @Test @DisplayName("[6] 10 x 10 with many walls -- UC No Solution") public void S6_heavyWalls10x10_UC() { expectNoSolution(G49, "UC"); }
    @Test @DisplayName("[6] 10 x 10 with many walls -- AS No Solution") public void S6_heavyWalls10x10_AS() { expectNoSolution(G49, "AS"); }

    // 10 x 10 where every cell costs 1 (many equal-cost paths)
    private static final String G50 = "10,10;4,4;500,25;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;1,1,1,1,1,1,1,1,1,1;0,0,9,0,0,9,9,9,5,5;1,1,8,1,1,8,8,8,4,5,2,2,7,7,3,6;";
    //   optimal: 0 lives, 43 energy
    @Test @DisplayName("[6] 10 x 10 where every cell costs 1 (many equal-cost paths) -- ID valid") public void S6_allDifficulty1_10x10_ID() { expectValid(G50, "ID"); }
    @Test @DisplayName("[6] 10 x 10 where every cell costs 1 (many equal-cost paths) -- UC optimal 0;43") public void S6_allDifficulty1_10x10_UC() { expectOptimal(G50, "UC", 0, 43); }
    @Test @DisplayName("[6] 10 x 10 where every cell costs 1 (many equal-cost paths) -- AS optimal 0;43") public void S6_allDifficulty1_10x10_AS() { expectOptimal(G50, "AS", 0, 43); }

    // random grid that is slow for a naive ID
    private static final String G51 = "8,6;3,5;305,17;7,3,18,1,15,14;11,14,4,18,16,5;19,6,16,8,16,17;2,6,8,9,11,10;14,1,4,4,13,5;4,5,13,11,15,12;5,1,3,17,18,7;6,1,15,20,3,16;2,1,2,7,2,4,2,3,1,6;4,5,4,1,2,0,0,0,5,5,4,6;";
    //   optimal: 0 lives, 277 energy
    @Test @DisplayName("[6] random grid that is slow for a naive ID -- ID valid") public void S6_rand021_ID() { expectValid(G51, "ID"); }
    @Test @DisplayName("[6] random grid that is slow for a naive ID -- UC optimal 0;277") public void S6_rand021_UC() { expectOptimal(G51, "UC", 0, 277); }
    @Test @DisplayName("[6] random grid that is slow for a naive ID -- AS optimal 0;277") public void S6_rand021_AS() { expectOptimal(G51, "AS", 0, 277); }

    // random grid that is slow for a naive ID
    private static final String G52 = "10,9;4,0;306,17;13,7,18,17,3,17,9,18,5;6,14,5,19,19,10,1,0,17;17,9,2,8,15,0,10,2,19;0,1,6,5,0,17,18,13,4;16,10,6,9,8,15,10,11,1;5,15,0,20,4,14,18,14,0;10,9,6,2,19,4,18,20,0;9,8,0,9,20,0,0,9,2;6,4,6,0,6,4,16,5,10;4,7,20,0,18,11,10,17,7;4,4,0,4,3,4,2,4;7,8,5,6,8,9,2,9,7,4,0,7,0,0,7,5;";
    //   no solution
    @Test @DisplayName("[6] random grid that is slow for a naive ID -- ID No Solution") public void S6_rand081_ID() { expectNoSolution(G52, "ID"); }
    @Test @DisplayName("[6] random grid that is slow for a naive ID -- UC No Solution") public void S6_rand081_UC() { expectNoSolution(G52, "UC"); }
    @Test @DisplayName("[6] random grid that is slow for a naive ID -- AS No Solution") public void S6_rand081_AS() { expectNoSolution(G52, "AS"); }

    // random grid that is slow for a naive ID
    private static final String G53 = "7,5;1,2;333,11;1,3,4,5,2;1,2,1,4,5;4,3,1,3,3;5,4,2,5,4;4,1,5,5,3;4,2,2,4,2;4,2,1,3,3;3,2,1,4,1,0,4,4,1,3;2,6,2,5,2,3,3,4,3,3,3,1,4,1,0,1;";
    //   optimal: 0 lives, 45 energy
    @Test @DisplayName("[6] random grid that is slow for a naive ID -- ID valid") public void S6_rand236_ID() { expectValid(G53, "ID"); }
    @Test @DisplayName("[6] random grid that is slow for a naive ID -- UC optimal 0;45") public void S6_rand236_UC() { expectOptimal(G53, "UC", 0, 45); }
    @Test @DisplayName("[6] random grid that is slow for a naive ID -- AS optimal 0;45") public void S6_rand236_AS() { expectOptimal(G53, "AS", 0, 45); }

    // random grid with no solution
    private static final String G54 = "4,7;0,3;118,0;14,6,12,12,19,7,9;19,19,9,5,9,14,2;6,4,9,3,11,9,14;9,1,8,4,18,12,15;2,0;3,3,2,1,1,2,0,1;";
    //   no solution
    @Test @DisplayName("[6] random grid with no solution -- ID No Solution") public void S6_rand002_ID() { expectNoSolution(G54, "ID"); }
    @Test @DisplayName("[6] random grid with no solution -- UC No Solution") public void S6_rand002_UC() { expectNoSolution(G54, "UC"); }
    @Test @DisplayName("[6] random grid with no solution -- AS No Solution") public void S6_rand002_AS() { expectNoSolution(G54, "AS"); }

    // random grid with no solution
    private static final String G55 = "7,4;2,0;163,18;13,47,11,8;34,42,20,38;27,16,43,9;2,35,26,10;2,31,21,41;12,42,33,22;49,4,1,16;0,0,1,2,1,3;3,0,3,6,2,6,2,5,0,5,2,4;";
    //   no solution
    @Test @DisplayName("[6] random grid with no solution -- ID No Solution") public void S6_rand003_ID() { expectNoSolution(G55, "ID"); }
    @Test @DisplayName("[6] random grid with no solution -- UC No Solution") public void S6_rand003_UC() { expectNoSolution(G55, "UC"); }
    @Test @DisplayName("[6] random grid with no solution -- AS No Solution") public void S6_rand003_AS() { expectNoSolution(G55, "AS"); }

    // =====================================================================
    // 7. Output format
    // =====================================================================
    /*
     * PDF: "The function returns a String of 4 elements, in the following format: plan;lives;energy;nodes"
     *      "plan: the sequence of actions ... separated by commas" (only the 7 operator names),
     *      "lives: number of used lives", "energy: number of used energy",
     *      "nodes: is the number of nodes chosen for expansion during the search",
     *      "If there is no solution, it should return the string \"No Solution\"" (tested in sections 2-6)
     */
    @Test @DisplayName("[7] output format plan;lives;energy;nodes -- pdfExample ID") public void S7_format_pdfExample_ID() { expectOutputFormat(G01, "ID"); }
    @Test @DisplayName("[7] output format plan;lives;energy;nodes -- pdfExample UC") public void S7_format_pdfExample_UC() { expectOutputFormat(G01, "UC"); }
    @Test @DisplayName("[7] output format plan;lives;energy;nodes -- pdfExample AS") public void S7_format_pdfExample_AS() { expectOutputFormat(G01, "AS"); }
    @Test @DisplayName("[7] output format plan;lives;energy;nodes -- random10x10max ID") public void S7_format_random10x10max_ID() { expectOutputFormat(G09, "ID"); }
    @Test @DisplayName("[7] output format plan;lives;energy;nodes -- random10x10max UC") public void S7_format_random10x10max_UC() { expectOutputFormat(G09, "UC"); }
    @Test @DisplayName("[7] output format plan;lives;energy;nodes -- random10x10max AS") public void S7_format_random10x10max_AS() { expectOutputFormat(G09, "AS"); }
    @Test @DisplayName("[7] output format plan;lives;energy;nodes -- livesBig00 ID") public void S7_format_livesBig00_ID() { expectOutputFormat(G39, "ID"); }
    @Test @DisplayName("[7] output format plan;lives;energy;nodes -- livesBig00 UC") public void S7_format_livesBig00_UC() { expectOutputFormat(G39, "UC"); }
    @Test @DisplayName("[7] output format plan;lives;energy;nodes -- livesBig00 AS") public void S7_format_livesBig00_AS() { expectOutputFormat(G39, "AS"); }
    @Test @DisplayName("[7] output format plan;lives;energy;nodes -- random10x10a ID") public void S7_format_random10x10a_ID() { expectOutputFormat(G47, "ID"); }
    @Test @DisplayName("[7] output format plan;lives;energy;nodes -- random10x10a UC") public void S7_format_random10x10a_UC() { expectOutputFormat(G47, "UC"); }
    @Test @DisplayName("[7] output format plan;lives;energy;nodes -- random10x10a AS") public void S7_format_random10x10a_AS() { expectOutputFormat(G47, "AS"); }

    // =====================================================================
    // 8. A* heuristic
    // =====================================================================
    /*
     * PDF: "A* search with an admissible heuristic. A trivial heuristic (e.g. h(n) = 1) is not acceptable."
     * Admissibility is checked by the optimal-cost A* tests in sections 1-6 (A* must match UC).
     * Here we check the heuristic actually guides the search: A* must expand fewer nodes than UC
     * (with h(n) = 0 or a constant, A* would expand about as many as UC).
     */
    @Test @DisplayName("[8] A* expands fewer nodes than UC -- pdfExample") public void S8_heuristicGuidesSearch_pdfExample() { expectAStarFewerNodesThanUC(G01); }
    @Test @DisplayName("[8] A* expands fewer nodes than UC -- random10x10b") public void S8_heuristicGuidesSearch_random10x10b() { expectAStarFewerNodesThanUC(G48); }
    @Test @DisplayName("[8] A* expands fewer nodes than UC -- random10x10max") public void S8_heuristicGuidesSearch_random10x10max() { expectAStarFewerNodesThanUC(G09); }
    @Test @DisplayName("[8] A* expands fewer nodes than UC -- livesBig03") public void S8_heuristicGuidesSearch_livesBig03() { expectAStarFewerNodesThanUC(G40); }
    @Test @DisplayName("[8] A* expands fewer nodes than UC -- rand021") public void S8_heuristicGuidesSearch_rand021() { expectAStarFewerNodesThanUC(G51); }

    // =====================================================================
    // 9. Code structure (Deliverables)
    // =====================================================================
    /*
     * PDF: "You should implement an abstract data type for a search-tree node",
     *      "an abstract data type for a generic search problem",
     *      "a CaveExplorer subclass of the generic search problem. This class must contain solve as a
     *       static method", "A package called tests and a package called code. All your code should be
     *       located in the code package and the test cases posted should be in the tests package."
     */
    @Test @DisplayName("[9] CaveExplorer is in package code")
    public void S9_caveExplorerInPackageCode() {
        assertTrue("code".equals(CaveExplorer.class.getPackage().getName()),
                "CaveExplorer must be in package code");
    }

    @Test @DisplayName("[9] tests are in package tests")
    public void S9_testsInPackageTests() {
        assertTrue("tests".equals(PrivateTests.class.getPackage().getName()),
                "test classes must be in package tests");
    }

    @Test @DisplayName("[9] generic search problem exists and is abstract")
    public void S9_genericSearchProblemIsAbstract() throws Exception {
        Class<?> c = Class.forName("code.GenericSearchProblem");
        assertTrue(Modifier.isAbstract(c.getModifiers()), "GenericSearchProblem should be abstract");
    }

    @Test @DisplayName("[9] CaveExplorer is a subclass of the generic search problem")
    public void S9_caveExplorerExtendsGenericSearchProblem() throws Exception {
        Class<?> generic = Class.forName("code.GenericSearchProblem");
        assertTrue(generic.isAssignableFrom(CaveExplorer.class) && CaveExplorer.class != generic,
                "CaveExplorer must extend GenericSearchProblem");
    }

    @Test @DisplayName("[9] solve(String, String) is public static and returns String")
    public void S9_solveIsPublicStatic() throws Exception {
        Method m = CaveExplorer.class.getMethod("solve", String.class, String.class);
        assertTrue(Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers()),
                "solve must be public static");
        assertTrue(m.getReturnType() == String.class, "solve must return String");
    }

    @Test @DisplayName("[9] search-tree node class exists in package code")
    public void S9_searchTreeNodeExists() throws Exception {
        Class<?> node = Class.forName("code.Node");
        assertTrue(node.getDeclaredFields().length >= 5,
                "Node should hold state, parent, operator, depth and path cost");
    }
}
