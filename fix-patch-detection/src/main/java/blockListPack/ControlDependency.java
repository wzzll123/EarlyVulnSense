package blockListPack;

import soot.*;
import soot.jimple.InvokeStmt;
import soot.jimple.Stmt;
import soot.jimple.ThrowStmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.*;
import soot.toolkits.graph.pdg.HashMutablePDG;
import soot.toolkits.graph.pdg.PDGNode;
import soot.toolkits.graph.pdg.ProgramDependenceGraph;
import soot.util.cfgcmd.CFGToDotGraph;
import soot.util.dot.DotGraph;
import utils.MethodTraversalUtility;
import utils.SinkCall;

import java.util.*;

public class ControlDependency extends SceneTransformer {
    private Map<SootMethod, Set<Unit>> checkMethod2conditionUnits;
    private Map<SootMethod, Set<Unit>> checkMethod2unitBehindConditionInSameMethod =new HashMap<>();
    private Map<SootMethod, Set<SootMethod>> checkMethod2InvokeMethodBehindConditionUnit=new HashMap<>();
    private Map<SootMethod,Set<SootMethod>> checkMethod2ReachableMethods =new HashMap<>();
    private Map<SootMethod, Set<Unit>> sootMethod2sinkCallUnit=new HashMap<>();
    private List<SinkCall> sinkCalls;
    private int maxDistance=10;
    public ControlDependency(Map<SootMethod, Set<Unit>> conditionUnits, List<SinkCall> sinkCalls){
        this.checkMethod2conditionUnits =conditionUnits;
        this.sinkCalls=sinkCalls;
    }
    private void updateSootMethod2SinkCall(){
        for (SootClass sootClass:Scene.v().getApplicationClasses()){
            updateSootMethod2SinkCallInSc(sootClass);
        }
    }
    private void updateSootMethod2SinkCallInSc(SootClass sc){
        List<SinkCall> sameFileSinkCalls=new ArrayList<>();
        String currentJavaFileName=sc.getName().split("\\$")[0]+".java:";
        for (SinkCall sinkCall:sinkCalls){
            // sinkCall getLocation example: src.main.org.h2.util.Utils.java:395:58:68
            if (sinkCall.getLocation().contains(currentJavaFileName)) {
                sameFileSinkCalls.add(sinkCall);
            }
        }
        for(SootMethod m : sc.getMethods()){
            if (!m.hasActiveBody()) continue;
            for (Unit unit:m.retrieveActiveBody().getUnits()){
                if (!unit.hasTag("LineNumberTag")){
                    continue;
                }
                int lineNumber=unit.getJavaSourceStartLineNumber();
                updateSootMethod2sinkCallFromUnit(sootMethod2sinkCallUnit, unit, sameFileSinkCalls,m,sc.getName().split("\\$")[0]+".java:"+lineNumber);
            }
        }
    }

    private void updateSootMethod2sinkCallFromUnit(Map<SootMethod, Set<Unit>> sootMethod2sinkCall,Unit unit,
                                                   List<SinkCall> sameFileSinkCalls, SootMethod m, String lineName) {

        for (SinkCall sinkCall:sameFileSinkCalls){
            // sinkCall getLocation example: src.main.org.h2.util.Utils.java:395:58:68
            if (sinkCall.getLocation().contains(lineName) &&
                    ((Stmt)unit).containsInvokeExpr()){
                sootMethod2sinkCall.computeIfAbsent(m, k -> new HashSet<>()).add(unit);
            }
        }
    }

    private Block getBlockContainedUnit(BlockGraph blockGraph, Unit unit){
        for (Block block:blockGraph){
            for (Unit tmp:block){
                if (unit.equals(tmp)){
                    return block;
                }
            }
        }
        throw new RuntimeException("not found unit in block graph");
    }
    private void updateBlackCheckMethod2unitBehindConditionInSameMethod(){
        for (SootMethod checkMethod: checkMethod2conditionUnits.keySet()){
            BlockGraph blockGraph = new BriefBlockGraph(checkMethod.retrieveActiveBody());
            for (Unit conditionUnit: checkMethod2conditionUnits.get(checkMethod)){
                Block block=getBlockContainedUnit(blockGraph,conditionUnit);
                Unit tmpIter=conditionUnit;
                while (tmpIter!=null){
                    checkMethod2unitBehindConditionInSameMethod.computeIfAbsent(checkMethod, k->new HashSet<>()).add(tmpIter);
                    tmpIter=block.getSuccOf(tmpIter);
                }
                for (Block succBlock: MethodTraversalUtility.getSuccsBlocks(block,blockGraph)){
                    for (Unit cur:succBlock)
                        checkMethod2unitBehindConditionInSameMethod.get(checkMethod).add(cur);
                }

            }

        }
    }
    private Set<Unit> getIntersect(Set<Unit> units1, Set<Unit> units2){
        Set<Unit> intersectSet=new HashSet<>();
        intersectSet.addAll(units1);
        intersectSet.retainAll(units2);
        return intersectSet;
    }
    private Set<Unit> possibleReachableSinkCalls(SootMethod checkMethod){
        Set<Unit> result=new HashSet<>();
        // check CheckMethod2unitBehindConditionInSameMethod
        // don't consider same method, because already do it in intraAnalysis, so comment this code
//        if (sootMethod2sinkCallUnit.containsKey(checkMethod)){
//            result.addAll( getIntersect(checkMethod2unitBehindConditionInSameMethod.get(checkMethod),sootMethod2sinkCallUnit.get(checkMethod)) );
//        }


        // check CheckMethod2ReachableMethods
        for (SootMethod reachableMethod: checkMethod2ReachableMethods.get(checkMethod)){
            if (!sootMethod2sinkCallUnit.containsKey(reachableMethod)){
                continue;
            }
            result.addAll( reachableMethod.retrieveActiveBody().getUnits() );
        }

        return result;
    }
    private Set<Unit> getJunctureUnits(SootMethod junctureMethod,SootMethod patchMethod, SootMethod sinkMethod){
        Set<Unit> result=new HashSet<>();
        Map<Block,Set<Unit>> patchInvokeStmts=MethodTraversalUtility.invokeStmtCanReachTarget(junctureMethod,patchMethod,Scene.v().getCallGraph(),maxDistance);
        Map<Block,Set<Unit>> sinkInvokeStmts=MethodTraversalUtility.invokeStmtCanReachTarget(junctureMethod,sinkMethod,Scene.v().getCallGraph(),maxDistance);
        Set<Unit> unionSet=new HashSet<>();
        for (Set<Unit> unitSet:sinkInvokeStmts.values()){
            unionSet.addAll(unitSet);
        }
        for (Block patchBlock:patchInvokeStmts.keySet()){
            for (Unit patchUnit:patchInvokeStmts.get(patchBlock)){
                Set<Unit> succsUnits=MethodTraversalUtility.getSuccsUnitInSameMethod(patchUnit,patchBlock,new BriefBlockGraph(junctureMethod.retrieveActiveBody()));
                succsUnits.retainAll(unionSet);
                if (!succsUnits.isEmpty()){
                    // there exists a sink invoke behind a patch invoke
                    result.add(patchUnit);
                }
            }

        }
        return result;
    }
    void earlyHaltAnalysis(){
        updateBlackCheckMethod2unitBehindConditionInSameMethod();

        CallGraph callGraph= Scene.v().getCallGraph();

        for (SootMethod checkMethod: checkMethod2unitBehindConditionInSameMethod.keySet()){
            for (Unit unit: checkMethod2unitBehindConditionInSameMethod.get(checkMethod)){
                if (((Stmt)unit).containsInvokeExpr()){
                    for (Iterator<Edge> it = callGraph.edgesOutOf(unit); it.hasNext(); ) {
                        Edge edge = it.next();
                        checkMethod2InvokeMethodBehindConditionUnit.computeIfAbsent(checkMethod,k->new HashSet<>()).add(edge.tgt());
                    }
                }
            }
            if (checkMethod2InvokeMethodBehindConditionUnit.get(checkMethod)==null){
                checkMethod2ReachableMethods.put(checkMethod,new HashSet<>());
                continue;
            }
            checkMethod2ReachableMethods.put(checkMethod, MethodTraversalUtility.getReachableClosure(checkMethod2InvokeMethodBehindConditionUnit.get(checkMethod),callGraph,maxDistance));
//            patchMethod2ReachableMethods.get(patchMethod).add(patchMethod); // add self to reachable
        }


//        String formattedOutput="The patch method %s is connected with sink call unit %s";
//        String formattedOutput="Sink call unit %s in reachable method is control dependent on the condition unit %s in method %s";
//        for ( SootMethod checkMethod: checkMethod2conditionUnits.keySet() ){
//            Set<Unit> sinkCallsUnits=possibleReachableSinkCalls(checkMethod);
//            if (!sinkCallsUnits.isEmpty()){
//                for (Unit sinkUnit:sinkCallsUnits){
//                    System.out.println(String.format(formattedOutput, sinkUnit, ,checkMethod));
//                }
//
//            }
//        }


//        if (isConnected==false){
        // compute back reachable methods for each method
        Map<SootMethod, Set<SootMethod>> method2backReachableMethods=new HashMap<>();
        Map<SootMethod, Map<SootMethod,Integer>> method2backReachableMethodsWithDistance=new HashMap<>();
        for (SootMethod checkMethod:checkMethod2conditionUnits.keySet()){
            if (!Scene.v().getReachableMethods().contains(checkMethod)) continue;
            Map<SootMethod,Integer> method2distance=MethodTraversalUtility.getBackReachableMethodsWithDistance(checkMethod,callGraph, maxDistance);
            method2distance.remove(checkMethod); // does not consider patch method here
            Set<SootMethod> patchBackReachable = method2distance.keySet();
            method2backReachableMethods.put(checkMethod,patchBackReachable);
            method2backReachableMethodsWithDistance.put(checkMethod,method2distance);
        }
        for (SootMethod sinkMethod:sootMethod2sinkCallUnit.keySet()){
            Set<SootMethod> patchBackReachable=MethodTraversalUtility.getBackReachableMethods(sinkMethod,callGraph);
            method2backReachableMethods.put(sinkMethod,patchBackReachable);
        }

        for (SootMethod checkMethod:checkMethod2conditionUnits.keySet()) {
            if (!Scene.v().getReachableMethods().contains(checkMethod)) continue;
            for (SootMethod sinkMethod : sootMethod2sinkCallUnit.keySet()) {
                Set<SootMethod> intersectSet=new HashSet<>();
                //copy
                intersectSet.addAll(method2backReachableMethods.get(checkMethod));
//                for (SootMethod method:method2backReachableMethods.get(patchMethod)){
//                    intersectSet.add(method);
//                }

                intersectSet.retainAll(method2backReachableMethods.get(sinkMethod));
                if (!intersectSet.isEmpty()) {
                    Map<SootMethod,Integer> intersectMethod2Dis=new HashMap<>();
                    for (SootMethod intersectMethod:intersectSet){
                        intersectMethod2Dis.put(intersectMethod,method2backReachableMethodsWithDistance.get(checkMethod).get(intersectMethod));
                    }
                    SootMethod smallestMethod=MethodTraversalUtility.findSmallestSootMethod(intersectMethod2Dis);
                    Set<Unit> junctureUnits=getJunctureUnits(smallestMethod,checkMethod,sinkMethod);
                    if (junctureUnits.isEmpty())    continue;
                    String formattedOutput="Sink call unit %s is control dependent on the condition unit in method %s";
                    for (Unit sinkCallUnit:sootMethod2sinkCallUnit.get(sinkMethod)){
                        System.out.println(String.format(formattedOutput, sinkCallUnit, checkMethod));
                    }


                }
            }
        }

    }
    boolean containsUnit(Block block, Unit unitToFind){
        for (Unit unit:block) {
            if (unit.equals(unitToFind)) {
                return true; // Unit found in the block.
            }
        }
        return false; // Unit not found in the block.
    }
    public Block findBlockForUnit(Unit unit, BlockGraph blockGraph) {
        for (Block block : blockGraph.getBlocks()) {
            if (containsUnit(block,unit)) {
                return block;
            }
        }
        // If the unit is not found in any block, return null or handle the case accordingly.
        return null;
    }
    public List<Block> findExceptionBlock(BlockGraph blockGraph){
        List<Block> result = new ArrayList<>();
        for (Block block : blockGraph.getBlocks()) {
            boolean isExceptionBlock=false;
            for (Unit unit:block){
                if (unit instanceof ThrowStmt){
                    result.add(block);
                    isExceptionBlock=true;
                }
                if (unit instanceof InvokeStmt){

                    for (Iterator<Edge> it = Scene.v().getCallGraph().edgesOutOf(unit); it.hasNext(); ) {
                        Edge edge = it.next();
                        if (!edge.getTgt().method().getExceptions().isEmpty()){
                            result.add(block);
                            isExceptionBlock=true;
                            break;
                        }

                    }
                }
                if (isExceptionBlock)   break;
            }
        }
        return result;
    }
    Set<PDGNode> getAllDependents(ProgramDependenceGraph pdg, PDGNode node){
        Set<PDGNode> result=new HashSet<>();
        Stack<PDGNode> workList=new Stack<>();
        workList.add(node);
        while (!workList.isEmpty()){
            PDGNode tmp = workList.pop();
            if (result.contains(tmp)){
                continue;
            }
            result.add(tmp);
            workList.addAll(pdg.getDependents(tmp));
        }
        return result;
    }
    boolean isDependentOn(ProgramDependenceGraph pdg, PDGNode node, PDGNode node1){
        // determine whether node1 is dependent on node
        Set<PDGNode> dependents = getAllDependents(pdg,node);
        return dependents.contains(node1);
    }
    void intraConditionSinkDependency(ProgramDependenceGraph pdg,SootMethod checkMethod){
        String formattedOutput="Sink call unit %s is control dependent on the condition unit %s in method %s";
        // examine sink in the same method
        if (!sootMethod2sinkCallUnit.containsKey(checkMethod)){
            return;
        }
        BlockGraph blockGraph = pdg.getBlockGraph();
        Set<Unit> sinkCallUnits = sootMethod2sinkCallUnit.get(checkMethod);
        Set<Block> sinkCallBlocks = new HashSet<>();
        for (Unit sinkCallUnit:sinkCallUnits){
            sinkCallBlocks.add(findBlockForUnit(sinkCallUnit,blockGraph));
        }

        for (Unit conditionUnit:checkMethod2conditionUnits.get(checkMethod)){
            Block conditionBlock=findBlockForUnit(conditionUnit,blockGraph);
            for (Block sinkCallBlock:sinkCallBlocks){
                if (isDependentOn(pdg,pdg.getPDGNode(conditionBlock),pdg.getPDGNode(sinkCallBlock))){
                    for (Unit sinkCallUnit:sinkCallBlock){
                        if (sinkCallUnits.contains(sinkCallUnit)){
                            System.out.println(String.format(formattedOutput, sinkCallUnit ,conditionUnit, checkMethod));
                        }
                    }

                }
            }
        }
    }
    void intraControlDependency(){
        for (SootMethod checkMethod: checkMethod2conditionUnits.keySet()){
            if (!checkMethod.hasActiveBody()) continue;
            ProgramDependenceGraph pdg;
            try{
                pdg=new HashMutablePDG(new ExceptionalUnitGraph(checkMethod.retrieveActiveBody()));
            }catch (NullPointerException e){
                continue;
                //don't know why
            }

            intraConditionSinkDependency(pdg,checkMethod);

            // remove condition units that not have an exception unit dependent on
            BlockGraph blockGraph = pdg.getBlockGraph();
            List<Block> exceptionBlocks = findExceptionBlock(blockGraph);
            List<Unit> removedUnit = new ArrayList<>();
            for (Unit conditionUnit:checkMethod2conditionUnits.get(checkMethod)){
                boolean exceptionDependent = false;
                Block conditionBlock=findBlockForUnit(conditionUnit,blockGraph);
                PDGNode conditionNode=pdg.getPDGNode(conditionBlock);
                for (Block exceptionBlock: exceptionBlocks){
                    PDGNode exceptionNode=pdg.getPDGNode(exceptionBlock);

                    if (isDependentOn(pdg,conditionNode,exceptionNode)){
                        exceptionDependent = true;
                    }
                }
                if (!exceptionDependent){
                    removedUnit.add(conditionUnit);
                }
            }
            checkMethod2conditionUnits.get(checkMethod).removeAll(removedUnit);
        }
        checkMethod2conditionUnits = checkMethod2conditionUnits.entrySet()
                .stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);
    }
    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        updateSootMethod2SinkCall();
        intraControlDependency();
        earlyHaltAnalysis();
    }
}
