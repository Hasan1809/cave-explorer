package code;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.ToIntFunction;

// Generic search problem (initial state, operators, transition function, goal test,
// path cost) plus the generic search procedure. A subclass like CaveExplorer fills in
// the abstract methods and search() works for it without knowing anything about caves.
// States are used as HashMap keys, so they need equals() and hashCode().
public abstract class GenericSearchProblem {

    // how many nodes the last search chose for expansion (reported by solve)
    protected int nodesExpanded = 0;

    public abstract Object getInitialState();

    public abstract List<String> getOperators();

    // null if the operator can't be applied
    public abstract Object result(Object state, String operator);

    public abstract boolean goalTest(Object state);

    // cost of going from state to nextState using operator
    public abstract int stepCost(Object state, String operator, Object nextState);

    // estimated cost from state to the goal, only used by A*
    public int heuristic(Object state) {
        return 0;
    }

    // UC and A* treat two states with the same key as the same state and only keep
    // the cheaper one. By default the key is the whole state, but a subclass can
    // leave out parts that only matter through the path cost (CaveExplorer leaves
    // out energy, since a cheaper path always means more energy left).
    public Object costSearchKey(Object state) {
        return state;
    }

    // ID groups states by this key, then compares states in the same group using
    // their depth and resources() (see DominanceFilter below).
    public Object dominanceKey(Object state) {
        return state;
    }

    // numbers where having more is never worse (for the cave: energy, rope, lives)
    public int[] resources(Object state) {
        return new int[0];
    }

    // UC and A* run without a depth limit
    private static final int NO_LIMIT = -1;

    // Generic search: takes a problem and a strategy (UC, ID or AS) and returns
    // the goal node, or null if there is no solution. All strategies use the same
    // loop and only differ in the queuing function:
    //   UC: priority queue on g, AS: priority queue on g + h,
    //   ID: stack with depth limit 0, 1, 2, ...
    public static Node search(GenericSearchProblem problem, String strategy) {
        problem.nodesExpanded = 0;
        switch (strategy) {
            case "UC":
                return generalSearch(problem,
                        new PriorityFrontier(Node::getPathCost),
                        new CostFilter(problem), NO_LIMIT).goal;
            case "AS":
                return generalSearch(problem,
                        new PriorityFrontier(n -> n.getPathCost() + problem.heuristic(n.getState())),
                        new CostFilter(problem), NO_LIMIT).goal;
            case "ID":
                // Depth-limited search with limit 0, 1, 2, ... Each iteration gets the
                // previous iteration's filter so it knows the best ways states were
                // reached last time. If a whole iteration never hits the limit,
                // everything reachable was searched and there is no solution.
                DominanceFilter previous = null;
                for (int limit = 0; ; limit++) {
                    DominanceFilter filter = new DominanceFilter(problem, previous);
                    SearchResult result = generalSearch(problem, new StackFrontier(),
                            filter, limit);
                    if (result.goal != null) {
                        return result.goal;
                    }
                    if (!result.cutOff) {
                        return null;
                    }
                    previous = filter;
                }
            default:
                return null;
        }
    }

    // The general search loop. The goal test is done when a node is removed from
    // the frontier, so UC and A* return an optimal solution. Every node removed
    // (and tested) counts as chosen for expansion, including the goal and nodes
    // at the depth limit.
    private static SearchResult generalSearch(GenericSearchProblem problem, Frontier frontier,
                                              RepeatedStateFilter filter, int depthLimit) {
        boolean depthLimited = depthLimit != NO_LIMIT;
        boolean cutOff = false;

        Object initialState = problem.getInitialState();
        Node root = new Node(initialState, null, null, 0, 0);
        filter.tryAdd(root);
        frontier.add(root);

        while (!frontier.isEmpty()) {
            Node node = frontier.removeFront();

            // a better node for the same state was added after this one, so this
            // copy is stale and not worth expanding
            if (filter.isOutdated(node)) {
                continue;
            }
            problem.nodesExpanded++;

            if (problem.goalTest(node.getState())) {
                return new SearchResult(node, cutOff);
            }

            // ID only: don't go past the limit, but remember that we had to stop,
            // since a bigger limit might find more
            if (depthLimited && node.getDepth() == depthLimit) {
                cutOff = true;
                continue;
            }

            // only children that aren't repeated states go into the frontier
            List<Node> newNodes = new ArrayList<>();
            for (Node child : expand(problem, node)) {
                if (filter.tryAdd(child)) {
                    newNodes.add(child);
                }
            }
            frontier.addAll(newNodes);
        }
        return new SearchResult(null, cutOff);
    }

    // Tries every operator on the node's state and makes a child for each one that
    // is allowed. A child's path cost is the parent's cost plus the step cost.
    private static List<Node> expand(GenericSearchProblem problem, Node node) {
        List<Node> children = new ArrayList<>();
        Object state = node.getState();
        for (String operator : problem.getOperators()) {
            Object next = problem.result(state, operator);
            if (next != null) {
                int cost = node.getPathCost() + problem.stepCost(state, operator, next);
                children.add(new Node(next, node, operator, node.getDepth() + 1, cost));
            }
        }
        return children;
    }

    private static class SearchResult {
        final Node goal;
        final boolean cutOff; // the depth limit stopped the search somewhere

        SearchResult(Node goal, boolean cutOff) {
            this.goal = goal;
            this.cutOff = cutOff;
        }
    }

    // ---------- repeated states ----------

    // Decides which nodes count as repeated states. Each strategy uses a different one.
    private interface RepeatedStateFilter {
        // called before a node goes into the frontier; false means it's a repeated
        // state that is no better than what we already have, so it is not added
        boolean tryAdd(Node node);

        // called when a node comes out of the frontier; true means a better node
        // for the same state was added while this one was waiting, so skip it
        boolean isOutdated(Node node);
    }

    // UC / A*: remembers the cheapest path cost found for each state key. A node is
    // only added if it reaches its state more cheaply than anything before it.
    private static class CostFilter implements RepeatedStateFilter {
        private final GenericSearchProblem problem;
        private final HashMap<Object, Integer> bestCost = new HashMap<>();

        CostFilter(GenericSearchProblem problem) {
            this.problem = problem;
        }

        public boolean tryAdd(Node node) {
            Object key = problem.costSearchKey(node.getState());
            Integer previous = bestCost.get(key);
            if (previous != null && previous <= node.getPathCost()) {
                return false;
            }
            bestCost.put(key, node.getPathCost());
            return true;
        }

        public boolean isOutdated(Node node) {
            return node.getPathCost() > bestCost.get(problem.costSearchKey(node.getState()));
        }
    }

    /*
     * ID: instead of only skipping exact repeats, a node is skipped if another node
     * with the same dominanceKey was already reached at the same or smaller depth
     * with at least as many resources (for the cave: energy, rope and lives). That
     * node can do everything the skipped one can and has at least as many steps
     * left before the limit, so skipping loses nothing.
     *
     * One more problem: DFS can reach a state through a bad path first, search
     * everything below it, then find a better path and search it all again. To
     * avoid that, each iteration starts with the records the previous iteration
     * ended with, marked as "expected" (not reached yet). They are the best ways
     * the previous iteration found to reach each state, and the limit only got
     * bigger, so this iteration will reach them again. A node that is strictly
     * worse than an expected record is skipped right away.
     */
    private static class DominanceFilter implements RepeatedStateFilter {

        // one way of reaching a state: values = {depth, resources...}
        private static class Record {
            final int[] values;
            boolean reached; // false = only expected from the previous iteration

            Record(int[] values, boolean reached) {
                this.values = values;
                this.reached = reached;
            }
        }

        private final GenericSearchProblem problem;
        // for each dominanceKey, the best records so far (none of them beats another)
        private final HashMap<Object, List<Record>> records = new HashMap<>();

        DominanceFilter(GenericSearchProblem problem, DominanceFilter previous) {
            this.problem = problem;
            // copy what the previous iteration reached as "expected" records
            if (previous != null) {
                for (var entry : previous.records.entrySet()) {
                    List<Record> expected = new ArrayList<>();
                    for (Record r : entry.getValue()) {
                        if (r.reached) {
                            expected.add(new Record(r.values, false));
                        }
                    }
                    records.put(entry.getKey(), expected);
                }
            }
        }

        private int[] recordOf(Node node) {
            int[] res = problem.resources(node.getState());
            int[] record = new int[res.length + 1];
            record[0] = node.getDepth();
            System.arraycopy(res, 0, record, 1, res.length);
            return record;
        }

        // a is at least as good as b: not deeper and no fewer resources
        private static boolean dominates(int[] a, int[] b) {
            if (a[0] > b[0]) {
                return false;
            }
            for (int i = 1; i < a.length; i++) {
                if (a[i] < b[i]) {
                    return false;
                }
            }
            return true;
        }

        public boolean tryAdd(Node node) {
            Object key = problem.dominanceKey(node.getState());
            int[] record = recordOf(node);
            List<Record> list = records.computeIfAbsent(key, k -> new ArrayList<>());

            // something at least as good was already reached in this iteration
            for (Record old : list) {
                if (old.reached && dominates(old.values, record)) {
                    return false;
                }
            }

            // something at least as good is expected: if this node is exactly the
            // expected one, it's the one we were waiting for; otherwise skip it
            for (Record old : list) {
                if (!old.reached && dominates(old.values, record)) {
                    if (Arrays.equals(old.values, record)) {
                        old.reached = true;
                        return true;
                    }
                    return false; // a better node will be reached later
                }
            }

            // this node is new or better: drop the records it beats and keep it
            list.removeIf(old -> dominates(record, old.values));
            list.add(new Record(record, true));
            return true;
        }

        // outdated = its record was removed because a better node replaced it
        // while it was still waiting on the stack
        public boolean isOutdated(Node node) {
            int[] record = recordOf(node);
            for (Record r : records.get(problem.dominanceKey(node.getState()))) {
                if (r.reached && Arrays.equals(r.values, record)) {
                    return false;
                }
            }
            return true;
        }
    }

    // ---------- frontiers ----------

    // The queuing function: where new nodes go and which node comes out next.
    private interface Frontier {
        void add(Node node);

        void addAll(List<Node> nodes);

        Node removeFront();

        boolean isEmpty();
    }

    // Ordered insert: g(n) for UC, g(n) + h(n) for A*. The priority is computed
    // once when the node is added, so h is not recomputed on every comparison.
    private static class PriorityFrontier implements Frontier {

        // a node together with its priority, so the queue never has to recompute it
        private static class Entry {
            final Node node;
            final int priority;

            Entry(Node node, int priority) {
                this.node = node;
                this.priority = priority;
            }
        }

        private final ToIntFunction<Node> priorityOf;
        private final PriorityQueue<Entry> queue =
                new PriorityQueue<>(Comparator.comparingInt((Entry e) -> e.priority));

        PriorityFrontier(ToIntFunction<Node> priorityOf) {
            this.priorityOf = priorityOf;
        }

        public void add(Node node) {
            queue.add(new Entry(node, priorityOf.applyAsInt(node)));
        }

        public void addAll(List<Node> nodes) {
            for (Node node : nodes) {
                add(node);
            }
        }

        public Node removeFront() {
            return queue.poll().node;
        }

        public boolean isEmpty() {
            return queue.isEmpty();
        }
    }

    // LIFO; children pushed in reverse so the first operator is tried first
    private static class StackFrontier implements Frontier {
        private final Deque<Node> stack = new ArrayDeque<>();

        public void add(Node node) {
            stack.push(node);
        }

        public void addAll(List<Node> nodes) {
            for (int i = nodes.size() - 1; i >= 0; i--) {
                stack.push(nodes.get(i));
            }
        }

        public Node removeFront() {
            return stack.pop();
        }

        public boolean isEmpty() {
            return stack.isEmpty();
        }
    }
}
