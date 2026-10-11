package code;

import java.util.LinkedList;

/**
 * A node in the search tree, as presented in class.
 *
 * A node is NOT the same thing as a state:
 *  - a state describes a situation in the world (where the explorer is, how much energy is left, ...)
 *  - a node wraps a state together with HOW we reached it (parent, action, depth, cost).
 * The same state can therefore appear in several nodes if it is reachable by different paths.
 */
public class Node {

    private final Object state;    // the world state this node represents
    private final Node parent;     // the node we expanded to get here (null for the root)
    private final String operator; // the action applied to the parent to reach this node (null for the root)
    private final int depth;       // number of actions from the root to this node
    private final int pathCost;    // total cost of all actions from the root to this node

    public Node(Object state, Node parent, String operator, int depth, int pathCost) {
        this.state = state;
        this.parent = parent;
        this.operator = operator;
        this.depth = depth;
        this.pathCost = pathCost;
    }

    public Object getState() {
        return state;
    }

    public Node getParent() {
        return parent;
    }

    public String getOperator() {
        return operator;
    }

    public int getDepth() {
        return depth;
    }

    public int getPathCost() {
        return pathCost;
    }

    /**
     * Rebuilds the plan by walking from this node back up to the root,
     * collecting the operators along the way, then reversing them so they
     * are in the order they were performed.
     */
    public LinkedList<String> getPlan() {
        LinkedList<String> plan = new LinkedList<>();
        Node current = this;
        while (current.parent != null) {
            plan.addFirst(current.operator); // addFirst reverses the order for us
            current = current.parent;
        }
        return plan;
    }
}
