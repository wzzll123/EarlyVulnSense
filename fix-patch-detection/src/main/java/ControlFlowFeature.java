import soot.*;
import soot.jimple.Stmt;
import soot.jimple.StmtSwitch;
import soot.jimple.internal.ImmediateBox;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.BriefBlockGraph;
import utils.ControlFlowResult;
import utils.MethodTraversalUtility;
import utils.SinkCall;

import java.util.*;

//todo: refactor code for simplicity
public class ControlFlowFeature extends SceneTransformer {
    private List<String> patchLines;
    private List<SinkCall> sinkCalls;
    private Set<SootMethod> junctureMethods;
    private Map<SootMethod, Set<Unit>> method2junctureUnits;
    private Map<SootMethod, Set<SinkCall>> sootMethod2sinkCall;
    private Map<SootMethod, Set<SootMethod>> junctureMethod2SinkMethod;
    private Map<SootMethod, Set<Unit>> patchMethod2patchUnits;
    private Map<SootMethod, Set<Unit>> junctureMethod2unitBehindPatchUnitInSameMethod;
    private Map<SootMethod,Set<SootMethod>> junctureMethod2patchMethods;
    private Set<SootMethod> alreadySinkMethods=new HashSet<>();
    private Set<SootMethod> patchMethods=new HashSet<>();

    private Map<SootMethod,Set<Unit>> patchMethod2patchStmt=new HashMap<>();
    Map<SootMethod, Set<SootMethod>> patchMethod2InvokeMethodFromUnit=new HashMap<>();
    Map<SootMethod,Set<SootMethod>> patchMethod2ReachableMethods=new HashMap<>();
    private int maxDistance = 3;
    private boolean disableJuncture=false;
    public ControlFlowFeature(
            List<SinkCall> sinkCalls,
            List<String> patchLines,
            ControlFlowResult controlFlowResult
    ){
        this.sinkCalls=sinkCalls;
        this.patchLines=patchLines;
        this.junctureMethods = controlFlowResult.junctureMethods;
        this.method2junctureUnits=controlFlowResult.method2junctureUnits;
        this.sootMethod2sinkCall=controlFlowResult.sootMethod2sinkCall;
        this.junctureMethod2SinkMethod = controlFlowResult.junctureMethod2SinkMethod;
        this.patchMethod2patchUnits=controlFlowResult.patchMethod2patchUnits;
        this.junctureMethod2unitBehindPatchUnitInSameMethod =controlFlowResult.juncture2unitBehindPatchUnitInSameMethod;
        this.junctureMethod2patchMethods=controlFlowResult.junctureMethod2PatchMethods;
    }
    private void updateJunctureMethod2unitBehindPatchUnitInSameMethod(SootMethod junctureMethod,Block block, BlockGraph blockGraph){
        for (Block succBlock: MethodTraversalUtility.getSuccsBlocks(block,blockGraph)){
            for (Unit cur:succBlock)
                junctureMethod2unitBehindPatchUnitInSameMethod.get(junctureMethod).add(cur);
        }
    }
    private void processClass(SootClass sc){
        // for performance, only focus on the same file
        List<SinkCall> sameFileSinkCalls=new ArrayList<>();
        List<String> sameFilePatchLines=new ArrayList<>();
        String currentJavaFileName=sc.getName().split("\\$")[0]+".java:";
        String shortCurrentJavaFileName=sc.getShortName().split("\\$")[0]+".java:";
        for (SinkCall sinkCall:sinkCalls){
            // sinkCall getLocation example: src.main.org.h2.util.Utils.java:395:58:68
            if (sinkCall.getLocation().contains(currentJavaFileName)) {
                sameFileSinkCalls.add(sinkCall);
            }
        }
        for (String patchLine:patchLines){
            if (patchLine.contains(sc.getShortName().split("\\$")[0])){
                sameFilePatchLines.add(patchLine);
            }
        }
        for(SootMethod m : sc.getMethods()){
            Boolean isPatchMethod=false;
            if (!m.hasActiveBody())
                continue;
            Body methodBody=m.retrieveActiveBody();
            BlockGraph blockGraph = new BriefBlockGraph(methodBody);
            for(Block block:blockGraph){
                Boolean isPatchBlock=false;
                for (Unit unit:block){
                    if (!unit.hasTag("LineNumberTag")){
                        continue;
                    }

                    int lineNumber=unit.getJavaSourceStartLineNumber();

                    int ValueLineNumber=-1;

                    for (ValueBox valueBox:unit.getUseAndDefBoxes()){
                        if (valueBox.hasTag("LineNumberTag") && valueBox instanceof ImmediateBox){ //prevent constant optimization
                            ValueLineNumber= valueBox.getJavaSourceStartLineNumber();
                        }
                    }
                    //update patchBlock2patchStmt
                    //example patchline: JdbcUtils.java:318
                    for(String patchLine:sameFilePatchLines){
                        if(patchLine.equals(sc.getShortJavaStyleName().split("\\$")[0]+".java:"+lineNumber)
                                || patchLine.equals(sc.getShortJavaStyleName().split("\\$")[0]+".java:"+ValueLineNumber)){
                            patchMethod2patchStmt.computeIfAbsent(m,k->new HashSet<>()).add(unit);
                            Unit tmpIter=unit;
                            while (tmpIter!=null){
                                junctureMethod2unitBehindPatchUnitInSameMethod.computeIfAbsent(m, k->new HashSet<>()).add(tmpIter);
                                tmpIter=block.getSuccOf(tmpIter);
                            }

                            isPatchMethod=true;
                            isPatchBlock=true;
                        }
                    }

                    updateSootMethod2sinkCallFromUnit(sameFileSinkCalls,m,sc.getName().split("\\$")[0]+".java:"+lineNumber);
                }
                if (isPatchBlock){
                    updateJunctureMethod2unitBehindPatchUnitInSameMethod(m,block,blockGraph);
                }

            }
            if (isPatchMethod)  patchMethods.add(m);
        }
    }
    private void updatePatchMethod2ReachableMethods(CallGraph callGraph){
        // when obtaining reachable methods, only consider method behind the patch units

        //todo:here junctureMethod2unitBehindPatchUnitInSameMethod is exactly patchMethod2...
        for (SootMethod patchMethod: junctureMethod2unitBehindPatchUnitInSameMethod.keySet()){
            for (Unit unit: junctureMethod2unitBehindPatchUnitInSameMethod.get(patchMethod)){
                if (((Stmt)unit).containsInvokeExpr()){
                    for (Iterator<Edge> it = callGraph.edgesOutOf(unit); it.hasNext(); ) {
                        Edge edge = it.next();
                        patchMethod2InvokeMethodFromUnit.computeIfAbsent(patchMethod,k->new HashSet<>()).add(edge.tgt());
                    }
                }
            }

            if (patchMethod2InvokeMethodFromUnit.get(patchMethod)==null){
                Set<SootMethod> tmp=new HashSet<>();
                tmp.add(patchMethod);
                patchMethod2ReachableMethods.put(patchMethod,tmp);
                continue;
            }
            patchMethod2ReachableMethods.put(patchMethod,
                    MethodTraversalUtility.getReachableClosure(patchMethod2InvokeMethodFromUnit.get(patchMethod),callGraph, maxDistance));
            patchMethod2ReachableMethods.get(patchMethod).add(patchMethod); // add self to reachable

        }
    }
    private void getPatchUnitControlFlowResult(){
        String formattedOutput="The patch method %s is connected with sink method %s, corresponding vulnerability is %s";
        Boolean isConnected=false; // patch method is connected with a sink method
        for (Map.Entry<SootMethod,Set<SootMethod>> entry:patchMethod2ReachableMethods.entrySet()) {
            SootMethod patchMethod=entry.getKey();
            Set<SootMethod> reachables=entry.getValue();

            for (SootMethod sinkMethod:sootMethod2sinkCall.keySet()){
                Set<String> uniqueTextValues = new HashSet<>();
                if (reachables.contains(sinkMethod)) {
                    for (SinkCall sinkCall : sootMethod2sinkCall.get(sinkMethod)) {
                        String text = sinkCall.getMessage().getText();
                        uniqueTextValues.add(text);
                    }
                    StringBuilder sb = new StringBuilder();
                    for (String text : uniqueTextValues) {

                        junctureMethods.add(patchMethod);
                        method2junctureUnits.computeIfAbsent(patchMethod,k->new HashSet<>()).addAll(patchMethod2patchStmt.get(patchMethod));
                        junctureMethod2SinkMethod.computeIfAbsent(patchMethod, k->new HashSet<>()).add(sinkMethod);
                        sb.append(text+", ");

                    }
                    if (sb.length()!=0){
                        isConnected=true;
                        patchMethod2patchUnits.computeIfAbsent(patchMethod, k->new HashSet<>()).addAll(patchMethod2patchStmt.get(patchMethod));
                        if (FixCommitDetector.verbose>0){
                            System.out.println(String.format(formattedOutput,patchMethod.toString(),sinkMethod.toString(), sb.toString()));
                        }
                    }
                }
            }
        }
    }
    private void getJunctureUnitsControlFlowResult(CallGraph callGraph){
        String formattedJuncutureOutput="There exists a juncture method %s, can reach patch method %s and sink method %s, corresponding vulnerability is %s";
//        if (isConnected==false){
        // compute back reachable methods for each method
        Map<SootMethod, Set<SootMethod>> method2backReachableMethods=new HashMap<>();
        Map<SootMethod, Map<SootMethod,Integer>> method2backReachableMethodsWithDistance=new HashMap<>();
        for (SootMethod patchMethod:patchMethods){
            if (!Scene.v().getReachableMethods().contains(patchMethod)) continue;
//                Set<SootMethod> patchBackReachable=MethodTraversalUtility.getBackReachableMethods(patchMethod,callGraph);
//                method2backReachableMethods.put(patchMethod,patchBackReachable);
            Map<SootMethod,Integer> method2distance=MethodTraversalUtility.getBackReachableMethodsWithDistance(patchMethod,callGraph, maxDistance);
            method2distance.remove(patchMethod); // does not consider patch method here
            Set<SootMethod> patchBackReachable = method2distance.keySet();
            method2backReachableMethods.put(patchMethod,patchBackReachable);
            method2backReachableMethodsWithDistance.put(patchMethod,method2distance);

        }
        for (SootMethod sinkMethod:sootMethod2sinkCall.keySet()){

            Set<SootMethod> patchBackReachable=MethodTraversalUtility.getBackReachableMethods(sinkMethod,callGraph);
            if (!method2backReachableMethods.containsKey(sinkMethod)){
                method2backReachableMethods.put(sinkMethod,patchBackReachable);
            }


//                method2backReachableMethodsWithDistance.put(sinkMethod,MethodTraversalUtility.getBackReachableMethodsWithDistance(sinkMethod,callGraph));
        }

        for (SootMethod patchMethod:patchMethods) {
            if (!Scene.v().getReachableMethods().contains(patchMethod)) continue;
            for (SootMethod sinkMethod : sootMethod2sinkCall.keySet()) {
                if (alreadySinkMethods.contains(sinkMethod)){
                    continue;
                }
                Set<SootMethod> intersectSet=new HashSet<>();
                //copy
                for (SootMethod method:method2backReachableMethods.get(patchMethod)){
                    intersectSet.add(method);
                }

                intersectSet.retainAll(method2backReachableMethods.get(sinkMethod));
                if (!intersectSet.isEmpty()) {
                    alreadySinkMethods.add(sinkMethod);
                    Map<SootMethod,Integer> intersectMethod2Dis=new HashMap<>();
                    for (SootMethod intersectMethod:intersectSet){
                        intersectMethod2Dis.put(intersectMethod,method2backReachableMethodsWithDistance.get(patchMethod).get(intersectMethod));
                    }
                    Set<SootMethod> smallestMethods=MethodTraversalUtility.findSmallestSootMethods(intersectMethod2Dis);
                    for (SootMethod smallestMethod:smallestMethods){
                        Set<Unit> junctureUnits=getJunctureUnits(smallestMethod,patchMethod,sinkMethod);
                        if (junctureUnits.isEmpty())    continue;

                        junctureMethods.add(smallestMethod);
                        method2junctureUnits.computeIfAbsent(smallestMethod,k->new HashSet<>()).addAll(junctureUnits);
                        junctureMethod2SinkMethod.computeIfAbsent(smallestMethod, k->new HashSet<>()).add(sinkMethod);
                        junctureMethod2patchMethods.computeIfAbsent(smallestMethod,k->new HashSet<>()).add(patchMethod);
                        patchMethod2patchUnits.computeIfAbsent(patchMethod, k->new HashSet<>()).addAll(patchMethod2patchStmt.get(patchMethod));

                        Set<String> uniqueTextValues = new HashSet<>();
                        for (SinkCall sinkCall : sootMethod2sinkCall.get(sinkMethod)) {
                            String text = sinkCall.getMessage().getText();
                            uniqueTextValues.add(text);
                        }
                        StringBuilder sb = new StringBuilder();
                        for (String text : uniqueTextValues) {
                            sb.append(text);
                        }
                        if (FixCommitDetector.verbose>0){
                            System.out.println(String.format(formattedJuncutureOutput, smallestMethod.toString() ,patchMethod.toString(), sinkMethod.toString(), sb.toString()));
                        }
                    }


                }
            }
        }
    }
    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        CallGraph callGraph = Scene.v().getCallGraph();
        for(SootClass sc : Scene.v().getApplicationClasses()){
            processClass(sc);
        }
        updatePatchMethod2ReachableMethods(callGraph);
        getPatchUnitControlFlowResult();
        if (!disableJuncture){
            getJunctureUnitsControlFlowResult(callGraph);
        }

    }

    private Set<Unit> getJunctureUnits(SootMethod junctureMethod,SootMethod patchMethod, SootMethod sinkMethod){
        Set<Unit> result=new HashSet<>();
        Map<Block,Set<Unit>> patchInvokeStmts=MethodTraversalUtility.invokeStmtCanReachTarget(junctureMethod,patchMethod,Scene.v().getCallGraph(),maxDistance);
        Map<Block,Set<Unit>> sinkInvokeStmts=MethodTraversalUtility.invokeStmtCanReachTarget(junctureMethod,sinkMethod,Scene.v().getCallGraph(),maxDistance);
        Set<Unit> unionSet=new HashSet<>();
        for (Set<Unit> unitSet:sinkInvokeStmts.values()){
            unionSet.addAll(unitSet);
        }
        // make sure sinkInvokeStmts are behind of patchInvokeStmts
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



    private boolean isConnected(CallGraph callGraph, SootMethod method1, SootMethod method2){
        if (method1.equals(method2)){
            return true;
        }
        // Perform a depth-first search to find method2 starting from method1
        Set<SootMethod> visitedMethods = new HashSet<>();
        return depthFirstSearch(callGraph, method1, method2, visitedMethods);
    }
    private Set<SootMethod> getReachableInvokeMethod(Block block){
        Set<Block> visitedBlocks = new HashSet<>();
        Set<SootMethod> invokedMethods=new HashSet<>();
        depthFirstSearchCfg(block, visitedBlocks, invokedMethods);
        return invokedMethods;
    }
    private void depthFirstSearchCfg(Block block, Set<Block> visitedBlocks, Set<SootMethod> invokedMethods){
        visitedBlocks.add(block);
        for (Unit unit:block){
            if (((Stmt)unit).containsInvokeExpr()){
                CallGraph callGraph=Scene.v().getCallGraph();
                for (Iterator<Edge> it = callGraph.edgesOutOf(unit); it.hasNext(); ) {
                    Edge edge = it.next();
                    invokedMethods.add(edge.tgt());
                }
            }
        }
        for (Block succ:block.getSuccs()){
            if (!visitedBlocks.contains(succ)){
                depthFirstSearchCfg(succ,visitedBlocks,invokedMethods);
            }

        }
    }
    private static boolean depthFirstSearch(CallGraph callGraph, SootMethod currentMethod, SootMethod targetMethod, Set<SootMethod> visitedMethods) {
        // Base case: If the current method is the target method, return true
        if (currentMethod.equals(targetMethod)) {
            return true;
        }

        // Add the current method to the set of visited methods
        visitedMethods.add(currentMethod);
//        System.out.println(currentMethod+", "+targetMethod);

        // Get the callers of the current method
        Iterator<Edge> callers = callGraph.edgesOutOf(currentMethod);

        // Traverse the callers recursively
        while (callers.hasNext()) {
            Edge edge = callers.next();
            SootMethod callerMethod = edge.getTgt().method();

            // Check if the caller method has already been visited
            if (!visitedMethods.contains(callerMethod)) {
                // Perform DFS on the caller method
                if (depthFirstSearch(callGraph, callerMethod, targetMethod, visitedMethods)) {
                    return true;
                }
            }
        }

        // Method2 not found in any callers, return false
        return false;
    }
    public void updateSootMethod2sinkCallFromUnit(List<SinkCall> sameFileSinkCalls, SootMethod m, String lineName){
        for (SinkCall sinkCall:sameFileSinkCalls){
            // sinkCall getLocation example: src.main.org.h2.util.Utils.java:395:58:68
            if (sinkCall.getLocation().contains(lineName)){
                sootMethod2sinkCall.computeIfAbsent(m, k -> new HashSet<>()).add(sinkCall);
            }
        }
    }
}
