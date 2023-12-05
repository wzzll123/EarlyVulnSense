package dfg.element;

import soot.jimple.toolkits.callgraph.Edge;

public class DataflowEdge {
    private DataflowNode source;
    private DataflowNode target;
    private EdgeKind edgeKind;
    public DataflowEdge(EdgeKind edgeKind,DataflowNode source,DataflowNode target){
        this.source=source;
        this.target=target;
        this.edgeKind=edgeKind;
    }
    public EdgeKind kind() {
        return edgeKind;
    }

    public DataflowNode source() {
        return source;
    }

    public DataflowNode target() {
        return target;
    }
}
