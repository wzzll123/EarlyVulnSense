package dfg.element;

import soot.Type;
import soot.util.Numberable;

public class ValNode extends DataflowNode implements Comparable, Numberable {

    protected ValNode(Type t) {
        super(t);
    }

    public int compareTo(Object o) {
        ValNode other = (ValNode) o;
        return other.getNumber() - this.getNumber();
    }
}