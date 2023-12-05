package dfg;

import dfg.element.DataflowNode;
import dfg.element.EdgeKind;
import dfg.element.NodeManager;
import dfg.element.Parm;
import polyglot.ast.NodeFactory;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Edge;
import utils.DataFactory;
import utils.MethodTraversalUtility;
import utils.SinkCall;
import utils.SootInitUtils;

import javax.xml.crypto.Data;
import java.util.*;

public class DataflowCreator extends SceneTransformer {
    Set<SootMethod> junctures;
    DataflowGraph dataflowGraph;
    MethodNodeFactory nodeFactory;
    private int maxDistance=30;

    public static void main(String[] args){
        // for test
        // Set basic settings for the call graph generation
//        List<String> jarFiles=new ArrayList<>();
//        jarFiles.add("../projects/pgjdbc/0afaa71d/pgjdbc-0afaa71d/pgjdbc/build/libs/postgresql-42.3.7-SNAPSHOT.jar");
//        SootInitUtils.sootInitWithCHA(jarFiles);
//
//        List<String> junctureSigs=new ArrayList<>();
//        List<SootMethod> junctures=new ArrayList<>();
//        junctureSigs.add("");
//        for (String junctureSig:junctureSigs){
//            Scene.v().getSootClass().getMethod();
//        }
//        DataflowCreator dataflowCreator =
//                new DataflowCreator(
//                        junctures,
//                        patchLines);

//        PackManager.v().getPack("wjtp").add(new Transform("wjtp.informationFlow", controlFlowFeature));
//        // Start the generation
//        PackManager.v().runPacks();
    }
    public DataflowCreator(Set<SootMethod> junctures, DataflowGraph dataflowGraph){
        this.junctures=junctures;
        this.nodeFactory=new MethodNodeFactory(dataflowGraph);
        this.dataflowGraph=dataflowGraph;
        this.dataflowGraph.setMethodNodeFactory(nodeFactory);
    }
    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        //TODO: find all methods based on the result of Control flow detection
        Set<SootMethod> allReachables= MethodTraversalUtility.getReachableClosure(junctures,Scene.v().getCallGraph(),maxDistance);

        for (SootMethod sootMethod:allReachables){
            if (!sootMethod.hasActiveBody()){
                continue;
            }
            Body body=sootMethod.retrieveActiveBody();
            for (Unit unit : body.getUnits()) {
                try {
                    if (((Stmt) unit).containsInvokeExpr()) {
                        dataflowGraph.addCallStmt(sootMethod,unit);
                    }
                    nodeFactory.handleStmt((Stmt) unit,sootMethod);
                } catch (Exception e) {
                    e.printStackTrace(System.err);
//                    System.out.println("Warning:" + e);
                }
            }
        }
        handleInvokeMethod();

    }

    private void handleInvokeMethod(){
        for (SootMethod sootMethod:dataflowGraph.getMethod2InvokeStmt().keySet()){
            for (Unit invokeStmt:dataflowGraph.getMethod2InvokeStmt().get(sootMethod)){
                InvokeExpr invokeExpr=((Stmt)invokeStmt).getInvokeExpr();
                List<DataflowNode> argNodes=new ArrayList<>();






                for (Value argValue:invokeExpr.getArgs()){
                    argNodes.add(nodeFactory.getNode(argValue,sootMethod));
                }
                for (Iterator<Edge> it = Scene.v().getCallGraph().edgesOutOf(invokeStmt); it.hasNext(); ) {
                    SootMethod calledMethod = it.next().tgt();
                    if (calledMethod.getParameterCount()!=invokeExpr.getArgCount()){
                        continue;
                    }
                    for (int i=0;i<invokeExpr.getArgCount();i++){
                        DataflowNode src=argNodes.get(i);

                        DataflowNode target= dataflowGraph.getNodeManager().makeLocalVarNode(new Parm(calledMethod, i), calledMethod.getParameterType(i),calledMethod);
                        dataflowGraph.getOrAddEdge(EdgeKind.PARAMETER_PASSING,src,target);
                    }


                    if (invokeExpr instanceof InstanceInvokeExpr){
                        DataflowNode base =nodeFactory.getNode(((InstanceInvokeExpr) invokeExpr).getBase(),sootMethod);
                        DataflowNode thisMethodNode=nodeFactory.nodeManager.getMethod2this().get(calledMethod);
                        dataflowGraph.getOrAddEdge(EdgeKind.THIS_PASSING,base,thisMethodNode);
                    }
                    if (invokeStmt instanceof AssignStmt){

                        Value l = ((AssignStmt) invokeStmt).getLeftOp();
                        DataflowNode dest = nodeFactory.getNode(l,sootMethod);
                        dataflowGraph.getOrAddEdge(EdgeKind.RETURN, nodeFactory.caseRet(calledMethod), dest);
                    }
                }
//                ((Stmt)invokeStmt).getInvokeExpr()
            }
        }
    }
}
