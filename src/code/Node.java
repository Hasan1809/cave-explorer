package code;

import java.util.LinkedList;

// Search tree node: state, parent, operator, depth and path cost.
public class Node {

    private final Object state;
    private final Node parent;     // null for the root
    private final String operator; // null for the root
    private final int depth;
    private final int pathCost;    // g(n)

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

    // Follows the parents back to the root to get the list of actions;
    // addFirst puts them back in the right order.
    public LinkedList<String> getPlan() {
        LinkedList<String> plan = new LinkedList<>();
        Node current = this;
        while (current.parent != null) {
            plan.addFirst(current.operator);
            current = current.parent;
        }
        return plan;
    }
}
