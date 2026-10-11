package code;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

/**
 * The generic search problem, as presented in class.
 *
 * A search problem is defined by:
 *  - an initial state
 *  - a set of operators (actions)
 *  - a transition function (what state results from applying an operator)
 *  - a goal test
 *  - a path cost function (here: the cost of each single step)
 *
 * This class knows NOTHING about caves, keys or doors. Any concrete problem
 * (like CaveExplorer) extends it and fills in the abstract methods. The
 * search procedure below then works for that problem without any changes.
 *
 * States are stored as plain Objects, so concrete problems MUST give their
 * state class proper equals() and hashCode() methods; that is how we
 * detect repeated states.
 */
public abstract class GenericSearchProblem {

    /** Number of nodes chosen for expansion during the last search. */
    protected int nodesExpanded = 0;

    // ------------------------------------------------------------------
    // The problem definition: implemented by each concrete problem
    // ------------------------------------------------------------------

    /** The state the agent starts in. */
    public abstract Object getInitialState();

    /** The names of all operators, in the order they should be tried. */
    public abstract List<String> getOperators();

    /**
     * The state that results from applying the operator to the state,
     * or null if the operator cannot be applied there.
     */
    public abstract Object result(Object state, String operator);

    /** True if the state satisfies the goal. */
    public abstract boolean goalTest(Object state);

    /** The cost of going from state to nextState using the operator. */
    public abstract int stepCost(Object state, String operator, Object nextState);

    /**
     * Estimated cost from the state to the goal; used only by A*.
     * The default of 0 means "no information"; problems that support A*
     * override it with a real admissible heuristic.
     */
    public int heuristic(Object state) {
        return 0;
    }

    // ------------------------------------------------------------------
    // The generic search procedure
    // ------------------------------------------------------------------

    /**
     * Runs the given strategy on this problem.
     *
     * @param strategy "UC" (uniform cost), "ID" (iterative deepening) or "AS" (A*)
     * @return the goal node, or null if there is no solution
     */
    public Node search(String strategy) {
        nodesExpanded = 0;
        switch (strategy) {
            case "UC":
                // Always expand the node with the cheapest path so far: g(n)
                return bestFirstSearch(Comparator.comparingInt(Node::getPathCost));
            case "AS":
                // Always expand the node with the lowest estimated total cost: g(n) + h(n)
                return bestFirstSearch(Comparator.comparingInt(
                        n -> n.getPathCost() + heuristic(n.getState())));
            case "ID":
                return iterativeDeepeningSearch();
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    }

    /**
     * Creates every child of a node by trying each operator on its state.
     * Operators that cannot be applied (result returns null) are skipped.
     */
    protected Deque<Node> expand(Node node) {
        nodesExpanded++;
        Deque<Node> children = new ArrayDeque<>();
        Object state = node.getState();
        for (String operator : getOperators()) {
            Object next = result(state, operator);
            if (next != null) {
                int cost = node.getPathCost() + stepCost(state, operator, next);
                children.add(new Node(next, node, operator, node.getDepth() + 1, cost));
            }
        }
        return children;
    }

    /**
     * Shared by UC and A*: the queue is a priority queue, and the order of
     * the queue is the only difference between the two strategies.
     *
     * Repeated states: bestCost remembers the cheapest path cost found so
     * far for each state. A node is only added if it reaches its state more
     * cheaply than before, and a node taken from the queue is skipped if a
     * cheaper path to its state was found after it was added.
     */
    private Node bestFirstSearch(Comparator<Node> order) {
        PriorityQueue<Node> queue = new PriorityQueue<>(order);
        HashMap<Object, Integer> bestCost = new HashMap<>();

        Node root = new Node(getInitialState(), null, null, 0, 0);
        queue.add(root);
        bestCost.put(root.getState(), 0);

        while (!queue.isEmpty()) {
            Node node = queue.poll();

            // An outdated copy: this state was already reached more cheaply
            if (node.getPathCost() > bestCost.get(node.getState())) {
                continue;
            }

            // Goal test happens when a node is removed from the queue, not when
            // it is added; otherwise UC and A* could return a non-optimal plan.
            if (goalTest(node.getState())) {
                return node;
            }

            for (Node child : expand(node)) {
                Integer previous = bestCost.get(child.getState());
                if (previous == null || child.getPathCost() < previous) {
                    bestCost.put(child.getState(), child.getPathCost());
                    queue.add(child);
                }
            }
        }
        return null; // queue empty: every reachable state was tried, no goal found
    }

    /**
     * Iterative deepening: run a depth-limited depth-first search with
     * limit 0, 1, 2, ... until a goal is found.
     *
     * The search stops with "no solution" once a whole iteration finishes
     * without ever being cut off by the limit. That means the full
     * reachable space fits under the limit and a deeper search would find
     * nothing new.
     */
    private Node iterativeDeepeningSearch() {
        for (int limit = 0; ; limit++) {
            DepthLimitedResult result = depthLimitedSearch(limit);
            if (result.goal != null) {
                return result.goal;
            }
            if (!result.cutOff) {
                return null;
            }
        }
    }

    /** What one depth-limited iteration found. */
    private static class DepthLimitedResult {
        final Node goal;      // the goal node, or null if none was found
        final boolean cutOff; // true if some nodes were not expanded because of the limit

        DepthLimitedResult(Node goal, boolean cutOff) {
            this.goal = goal;
            this.cutOff = cutOff;
        }
    }

    /**
     * Depth-first search that never goes deeper than the limit.
     * The queue is a stack (last in, first out), which is what makes it depth-first.
     *
     * Repeated states: shallowestDepth remembers the shallowest depth at which
     * each state was reached in THIS iteration. Reaching it again at the same
     * depth or deeper cannot lead anywhere new, so that node is skipped.
     */
    private DepthLimitedResult depthLimitedSearch(int limit) {
        Deque<Node> stack = new ArrayDeque<>();
        HashMap<Object, Integer> shallowestDepth = new HashMap<>();
        boolean cutOff = false;

        Node root = new Node(getInitialState(), null, null, 0, 0);
        stack.push(root);
        shallowestDepth.put(root.getState(), 0);

        while (!stack.isEmpty()) {
            Node node = stack.pop();

            if (goalTest(node.getState())) {
                return new DepthLimitedResult(node, cutOff);
            }

            if (node.getDepth() == limit) {
                cutOff = true; // there may be more below this node; a deeper iteration will look
                continue;
            }

            // Push children in reverse so the first operator is popped (tried) first
            for (var it = expand(node).descendingIterator(); it.hasNext(); ) {
                Node child = it.next();
                Integer previous = shallowestDepth.get(child.getState());
                if (previous == null || child.getDepth() < previous) {
                    shallowestDepth.put(child.getState(), child.getDepth());
                    stack.push(child);
                }
            }
        }
        return new DepthLimitedResult(null, cutOff);
    }
}
