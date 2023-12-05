package dfg;

import dfg.element.DataflowEdge;
import dfg.element.DataflowNode;
import dfg.element.EdgeKind;
import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;

public class LocalDataflowGraph {
    private SootMethod method;
    private Body body;
    private MethodNodeFactory nodeFactory;
//    public LocalDataflowGraph(DataflowGraph globalDataflowGraph, SootMethod m) {
//        this.method = m;
////        this.nodeFactory = new MethodNodeFactory(globalDataflowGraph, this);
//        this.body = m.getActiveBody();
//        build();
//    }
//    public DataflowEdge getOrAddEdge(EdgeKind kind, DataflowNode source, DataflowNode target) {
//        if (source==null){
//            return null;
//        }
//        return source.getOrAddEdge(kind, source, target);
//    }
//    private void build(){
//        for (Unit unit : body.getUnits()) {
//            try {
//                nodeFactory.handleStmt((Stmt) unit);
//            } catch (Exception e) {
//                System.out.println("Warning:" + e);
//            }
//        }
//    }

}
