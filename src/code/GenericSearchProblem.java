package code;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

// Generic search problem: initial state, operators, transition function,
// goal test and path cost. Subclasses fill these in, and search() works for any of them.
// States are used as HashMap keys, so they need equals() and hashCode().
public abstract class GenericSearchProblem {

    protected int nodesExpanded = 0;

    public abstract Object getInitialState();

    public abstract List<String> getOperators();

    // Should return null if the operator can't be applied in this state
    public abstract Object result(Object state, String operator);

    public abstract boolean goalTest(Object state);

    public abstract int stepCost(Object state, String operator, Object nextState);

    // Only used by A*, 0 by default
    public int heuristic(Object state) {
        return 0;
    }

    // strategy: "UC", "ID" or "AS". Returns the goal node or null if there is no solution.
    public Node search(String strategy) {
        nodesExpanded = 0;
        switch (strategy) {
            case "UC":
                // ordered by g(n)
                return bestFirstSearch(Comparator.comparingInt(Node::getPathCost));
            case "AS":
                // ordered by g(n) + h(n)
                return bestFirstSearch(Comparator.comparingInt(
                        n -> n.getPathCost() + heuristic(n.getState())));
            case "ID":
                return iterativeDeepeningSearch();
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    }

    // Applies every operator to the node's state and returns the valid children
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

    // Used for both UC and A*, the only difference is the queue order.
    // bestCost keeps the cheapest cost we reached each state with, so we
    // don't add a state again unless we found a cheaper way to it.
    private Node bestFirstSearch(Comparator<Node> order) {
        PriorityQueue<Node> queue = new PriorityQueue<>(order);
        HashMap<Object, Integer> bestCost = new HashMap<>();

        Node root = new Node(getInitialState(), null, null, 0, 0);
        queue.add(root);
        bestCost.put(root.getState(), 0);

        while (!queue.isEmpty()) {
            Node node = queue.poll();

            // skip old entries, a cheaper path to this state was found later
            if (node.getPathCost() > bestCost.get(node.getState())) {
                continue;
            }

            // goal test when removing from the queue (not when adding) so the result is optimal
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
        return null;
    }

    // Depth limited search with limit 0, 1, 2, ...
    // If an iteration finishes without hitting the limit anywhere, the whole
    // reachable space was searched, so there is no solution.
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

    private static class DepthLimitedResult {
        final Node goal;
        final boolean cutOff; // true if the limit stopped us from expanding some node

        DepthLimitedResult(Node goal, boolean cutOff) {
            this.goal = goal;
            this.cutOff = cutOff;
        }
    }

    // DFS using a stack, not going deeper than the limit.
    // shallowestDepth stores the smallest depth each state was seen at in this
    // iteration; seeing it again at the same depth or deeper is skipped.
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
                cutOff = true;
                continue;
            }

            // pushed in reverse so the first operator gets popped first
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
