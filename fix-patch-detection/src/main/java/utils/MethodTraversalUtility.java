package utils;

import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.BriefBlockGraph;

import java.util.*;

public class MethodTraversalUtility {
    static Map<SootMethod, Set<SootMethod>> memoizationCache = new HashMap<>();
    static Map<SootMethod, Set<SootMethod>> backReachableCache = new HashMap<>();
    public static Set<Block> getSuccsBlocks(Block block, BlockGraph blockGraph){
        Set<Block> reachableBlocks = new HashSet<>();
        Stack<Block> blockToProcess = new Stack<>();
        blockToProcess.push(block);

        while(!blockToProcess.isEmpty()){
            Block currentBlock = blockToProcess.pop();
            if(!reachableBlocks.contains(currentBlock)){
                reachableBlocks.add(currentBlock);
                for (Block succ:blockGraph.getSuccsOf(currentBlock)){
                    blockToProcess.push(succ);
                }
            }
        }
        reachableBlocks.remove(block);

        return reachableBlocks;
    }
    public static Set<Unit> getSuccsUnitInSameMethod(Unit unit, Block block, BlockGraph blockGraph){
        Set<Unit> result=new HashSet<>();
        Unit tmpIter=unit;
        while (tmpIter!=null){
            result.add(tmpIter);
            tmpIter=block.getSuccOf(tmpIter);
        }
        for (Block succBlock:MethodTraversalUtility.getSuccsBlocks(block,blockGraph)){
            for (Unit cur:succBlock)
                result.add(cur);
        }
        return result;
    }
    public static Set<SootMethod> getReachableClosure(Set<SootMethod> sootMethods, CallGraph callGraph,int maxDistance){
        Set<SootMethod> result=new HashSet<>();
        for (SootMethod method:sootMethods){
            result.addAll(getReachableMethods(method,callGraph,maxDistance));
        }
        return result;
//        Set<SootMethod> reachableMethods = new HashSet<>();
//        Queue<SootMethod> methodsToProcess = new LinkedList<>();
//        Queue<Integer> distanceToProcess = new LinkedList<>();
//        for (SootMethod startMethod:sootMethods){
//           methodsToProcess.offer(startMethod);
//           distanceToProcess.offer(0);
//        }
//        while(!methodsToProcess.isEmpty()){
//            SootMethod currentMethod = methodsToProcess.poll();
//            int currentDistance= distanceToProcess.poll();
//            if(!reachableMethods.contains(currentMethod)){
//                reachableMethods.add(currentMethod);
//                if (currentDistance>maxDistance){
//                    continue;
//                }
//                for (Iterator<Edge> it = callGraph.edgesOutOf(currentMethod); it.hasNext(); ) {
//                    SootMethod calledMethod = it.next().tgt();
//                    methodsToProcess.offer(calledMethod);
//                    distanceToProcess.offer(currentDistance+1);
//                }
//            }
//        }
//        return reachableMethods;
    }
    public static Set<SootMethod> getReachableMethods(SootMethod sootMethod, CallGraph callGraph,int maxDistance){
        if (memoizationCache.containsKey(sootMethod)){
            return memoizationCache.get(sootMethod);
        }
        Set<SootMethod> reachableMethods = new HashSet<>();
        Queue<SootMethod> methodsToProcess = new LinkedList<>();
        Queue<Integer> distanceToProcess = new LinkedList<>();
        methodsToProcess.offer(sootMethod);
        distanceToProcess.offer(0);
        while(!methodsToProcess.isEmpty()){
            SootMethod currentMethod = methodsToProcess.poll();
            int currentDistance= distanceToProcess.poll();
            if(!reachableMethods.contains(currentMethod)){
                reachableMethods.add(currentMethod);
                if (currentDistance>maxDistance){
                    continue;
                }
                for (Iterator<Edge> it = callGraph.edgesOutOf(currentMethod); it.hasNext(); ) {
                    SootMethod calledMethod = it.next().tgt();
                    methodsToProcess.offer(calledMethod);
                    distanceToProcess.offer(currentDistance+1);
                }
            }
        }
        memoizationCache.put(sootMethod,reachableMethods);

        return reachableMethods;
    }
    public static Map<SootMethod,Integer> getBackReachableMethodsWithDistance(SootMethod method, CallGraph callGraph, int maxDistance){
        Map<SootMethod,Integer> result=new HashMap<>();
        Set<SootMethod> reachableMethods = new HashSet<>();
        Queue<SootMethod> methodsToProcess = new LinkedList<>();
        Queue<Integer> distanceToProcess = new LinkedList<>();
        methodsToProcess.offer(method);distanceToProcess.offer(0);
        while (!methodsToProcess.isEmpty()){
            SootMethod currentMethod=methodsToProcess.poll();
            int currentDistance= distanceToProcess.poll();
            if(!reachableMethods.contains(currentMethod)){
                reachableMethods.add(currentMethod);
                result.put(currentMethod,currentDistance);
                if (currentDistance>maxDistance){
                    continue;
                }
                for (Iterator<Edge> it = callGraph.edgesInto(currentMethod); it.hasNext(); ) {
                    SootMethod callerMethod = it.next().src();
                    methodsToProcess.offer(callerMethod);
                    distanceToProcess.offer(currentDistance+1);
                }
            }

        }

        return result;
    }
    public static Set<SootMethod> getBackReachableMethods(SootMethod sootMethod, CallGraph callGraph){
        if (backReachableCache.containsKey(sootMethod)){
            return backReachableCache.get(sootMethod);
        }
        Set<SootMethod> reachableMethods = new HashSet<>();
        Stack<SootMethod> methodsToProcess = new Stack<>();
        methodsToProcess.push(sootMethod);

        while(!methodsToProcess.isEmpty()){
            SootMethod currentMethod = methodsToProcess.pop();
            if(!reachableMethods.contains(currentMethod)){
                reachableMethods.add(currentMethod);
                for (Iterator<Edge> it = callGraph.edgesInto(currentMethod); it.hasNext(); ) {
                    SootMethod callerMethod = it.next().src();
                    methodsToProcess.push(callerMethod);
                }
            }
        }
        backReachableCache.put(sootMethod,reachableMethods);
        return reachableMethods;
    }
    public static Set<SootMethod> findSmallestSootMethods(Map<SootMethod, Integer> map) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException("Map cannot be null or empty.");
        }

        Set<SootMethod> smallestMethods = new HashSet<>();
        int smallestValue = Integer.MAX_VALUE;

        for (Map.Entry<SootMethod, Integer> entry : map.entrySet()) {
            int value = entry.getValue();

            if (value < smallestValue) {
                smallestValue = value;
            }
        }
        for (Map.Entry<SootMethod, Integer> entry : map.entrySet()) {
            SootMethod method = entry.getKey();
            int value = entry.getValue();
            if (value == smallestValue){
                smallestMethods.add(method);
            }
        }
        return smallestMethods;
    }
    public static SootMethod findSmallestSootMethod(Map<SootMethod, Integer> map) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException("Map cannot be null or empty.");
        }

        SootMethod smallestMethod = null;
        int smallestValue = Integer.MAX_VALUE;

        for (Map.Entry<SootMethod, Integer> entry : map.entrySet()) {
            SootMethod method = entry.getKey();
            int value = entry.getValue();

            if (value < smallestValue) {
                smallestValue = value;
                smallestMethod = method;
            }
        }

        return smallestMethod;
    }
    public static Map<Block,Set<Unit>> invokeStmtCanReachTarget(SootMethod source, SootMethod target, CallGraph callGraph, int maxDistance){
        Map<Block,Set<Unit>> result=new HashMap<>();
        Body methodBody=source.retrieveActiveBody();
        BlockGraph blockGraph = new BriefBlockGraph(methodBody);
        for (Block block:blockGraph){
            for (Unit unit:block){
                if (((Stmt)unit).containsInvokeExpr()){
                    if (isConnect(unit,target,callGraph,maxDistance)){
                        result.computeIfAbsent(block,k->new HashSet<>()).add(unit);
                    }
                }
            }
        }

        return result;
    }
    private static boolean isConnect(Unit source, SootMethod target, CallGraph callGraph, int maxDistance){
        Set<SootMethod> initialMethods=new HashSet<>();
        for (Iterator<Edge> it = callGraph.edgesOutOf(source); it.hasNext(); ) {
            SootMethod callerMethod = it.next().tgt();
            initialMethods.add(callerMethod);
        }
        Set<SootMethod> allReachables=getReachableClosure(initialMethods,callGraph,maxDistance);
        return  allReachables.contains(target);
    }
}
