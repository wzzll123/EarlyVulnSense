package utils;

import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.dot.DotGraph;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class CallGraphUtils {
    // get any path from a method to target method
    static public List<SootMethod> getPathOfTwoNodes(CallGraph callGraph, SootMethod start, SootMethod end){
        List<SootMethod> result=new ArrayList<>();
        // Create a list to keep track of visited methods to avoid cycles
        List<SootMethod> visitedMethods = new ArrayList<>();

        // Call the recursive helper function to find the path
        boolean pathFound = findPathRecursive(callGraph, start, end, visitedMethods, result);

        if (pathFound) {
            return result;
        } else {
            return null; // No path found
        }
    }
    static private boolean findPathRecursive(CallGraph callGraph, SootMethod currentMethod, SootMethod targetMethod,
                                             List<SootMethod> visitedMethods, List<SootMethod> result) {
        if (visitedMethods.contains(currentMethod)) {
            return false; // Cycle detected, stop searching this path
        }

        visitedMethods.add(currentMethod);
        result.add(currentMethod);

        if (currentMethod.equals(targetMethod)) {
            return true; // Path found
        }

        // Iterate through the successors of the current method
        for (Iterator<Edge> it = callGraph.edgesOutOf(currentMethod); it.hasNext(); ) {
            Edge edge = it.next();
            SootMethod successor=edge.tgt();
            boolean found = findPathRecursive(callGraph, successor, targetMethod, visitedMethods, result);
            if (found) {
                return true;
            }
        }

        // If the path was not found from this method, backtrack
        visitedMethods.remove(currentMethod);
        result.remove(result.size() - 1);

        return false;
    }
    static public void writeToFile(CallGraph callGraph, String directory){
        DotNamer<SootMethod> namer = new DotNamer<SootMethod>();
        DotGraph dot = new DotGraph("callgraph");
        Iterator<Edge> iteratorEdges = callGraph.iterator();
        for (Edge edge:callGraph){
            dot.drawEdge(namer.getName(edge.src()),namer.getName(edge.tgt()));
            dot.drawNode(edge.src().toString());
            dot.drawNode(edge.tgt().toString());
        }
        dot.plot(directory+ File.separator+"callgraph.dot");
    }
    private static class DotNamer<N> extends HashMap<N, Integer> {
        private int nodecount = 0;

        String getName(N node) {
            Integer index = (Integer)this.get(node);
            if (index == null) {
                index = this.nodecount++;
                this.put(node, index);
            }

            return index.toString();
        }
    }
}

