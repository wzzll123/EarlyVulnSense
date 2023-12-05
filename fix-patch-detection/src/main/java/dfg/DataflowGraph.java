package dfg;

import dfg.element.DataflowEdge;
import dfg.element.DataflowNode;
import dfg.element.EdgeKind;
import dfg.element.NodeManager;
import soot.SootMethod;
import soot.Unit;

import java.util.*;

public class DataflowGraph {
    static Map<DataflowNode,Set<DataflowNode>> cachedReachableNodes=new HashMap<>();
    private Set<DataflowNode> dataflowNodes=new HashSet<>();
    private Map<SootMethod, LocalDataflowGraph> method2dfg;
    private NodeManager nodeManager;
    private MethodNodeFactory methodNodeFactory;
    private Map<SootMethod, Set<Unit>> method2InvokeStmt=new HashMap<>();

    public DataflowGraph(NodeManager nodeManager){
        this.nodeManager=nodeManager;
    }

    public DataflowEdge getOrAddEdge(EdgeKind kind, DataflowNode source, DataflowNode target) {
        if (source==null || target==null){
            return null;
        }
        target.addInEdge(kind,source,target);
        return source.getOrAddEdge(kind, source, target);
    }
    public NodeManager getNodeManager(){
        return nodeManager;
    }

    public void setMethodNodeFactory(MethodNodeFactory methodNodeFactory){
        this.methodNodeFactory=methodNodeFactory;
    }
    public MethodNodeFactory getMethodNodeFactory(){
        return this.methodNodeFactory;
    }
    public Set<DataflowEdge> getOutEdgesOf(DataflowNode node){
        return node.getOutEdges();
    }
    public Set<DataflowNode> getSuccsOf(DataflowNode node){
        if (node==null){
            return new HashSet<>();
        }
        return node.getSuccsOf();
    }
    public Set<DataflowNode> getSuccsOfByKind(DataflowNode node, Set<EdgeKind> allowedKind){
        if (node==null){
            return new HashSet<>();
        }
        return node.getSuccsOfByKind(allowedKind);
    }
    public Set<DataflowNode> getPredecessorsOfByKind(DataflowNode node, Set<EdgeKind> allowedKind){
        if (node==null){
            return new HashSet<>();
        }
        return node.getPredecessorsOfByKind(allowedKind);
    }
    public Set<DataflowNode> getPredecessorsOf(DataflowNode node){
        if (node==null){
            return new HashSet<>();
        }
        return node.getPredecessorsOf();
    }

    public Set<DataflowNode> getAliases(DataflowNode node){
        Set<EdgeKind> allowEdgeKind=new HashSet<>();
        allowEdgeKind.add(EdgeKind.LOCAL_ASSIGN);
        allowEdgeKind.add(EdgeKind.TRANSFER);
        allowEdgeKind.add(EdgeKind.PARAMETER_PASSING);
//        allowEdgeKind.add(EdgeKind.THIS_PASSING);
        Set<DataflowNode> result=new HashSet<>();
        if (node == null) {
            return result;
        }

        Set<DataflowNode> visited = new HashSet<>();
        Queue<DataflowNode> queue = new LinkedList<>();
        queue.add(node);

        while (!queue.isEmpty()) {
            DataflowNode current = queue.poll();
            if (!visited.contains(current) && current!=null) {
                visited.add(current);
                result.add(current);
//                Set<DataflowNode> successors = getSuccsOf(current);
                Set<DataflowNode> successors = getSuccsOfByKind(current,allowEdgeKind);
                for (DataflowNode successor : successors) {
                    queue.add(successor);
                }
                for (DataflowNode predecessor:getPredecessorsOfByKind(current,allowEdgeKind)){
                    queue.add(predecessor);
                }
            }
        }
        return result;
    }
    public Set<DataflowNode> getSuccsClosureOf(DataflowNode node) {
        Set<DataflowNode> closure = new HashSet<>();
        if (node == null) {
            return closure;
        }

        Set<DataflowNode> visited = new HashSet<>();
        Queue<DataflowNode> queue = new LinkedList<>();
        queue.add(node);

        while (!queue.isEmpty()) {
            DataflowNode current = queue.poll();
            if (!visited.contains(current) && current!=null) {
                visited.add(current);
                closure.add(current);
                Set<DataflowNode> successors = getSuccsOf(current);
                for (DataflowNode successor : successors) {
                    queue.add(successor);
                }
            }
        }

        return closure;
    }
    private Set<DataflowNode> reachableNodesOf(DataflowNode node){
        if (cachedReachableNodes.containsKey(node)){
            return cachedReachableNodes.get(node);
        }
        Set<DataflowNode> visited=new HashSet<>();
        Queue<DataflowNode> queue=new LinkedList<>();
        queue.offer(node);
        visited.add(node);
        while (!queue.isEmpty()){
            DataflowNode currentNode= queue.poll();
            for (DataflowNode succ:getSuccsOf(currentNode)){
                if (!visited.contains(succ)) {
                    queue.offer(succ);
                    visited.add(succ);
                }
            }
        }
        cachedReachableNodes.put(node,visited);
        return visited;
    }
    public boolean hasConnection(DataflowNode startNode, DataflowNode endNode){
        Set<DataflowNode> reachableNodes=reachableNodesOf(startNode);
        return reachableNodes.contains(endNode);
//        Set<DataflowNode> visited=new HashSet<>();
//        Queue<DataflowNode> queue=new LinkedList<>();
//        queue.offer(startNode);
//        visited.add(startNode);
//        while (!queue.isEmpty()){
//            DataflowNode currentNode= queue.poll();
//            if (currentNode==endNode){
//                return true;
//            }
//            for (DataflowNode succ:getSuccsOf(currentNode)){
//                if (!visited.contains(succ)) {
//                    queue.offer(succ);
//                    visited.add(succ);
//                }
//            }
//        }
//        return false;
    }
    //used for debug
    public List<DataflowNode> getPath(DataflowNode start, DataflowNode end) {
        // Create a queue for BFS
        Queue<DataflowNode> queue = new LinkedList<>();

        // Create a map to keep track of visited nodes and their predecessors
        Map<DataflowNode, DataflowNode> visited = new HashMap<>();

        // Initialize the queue with the starting node
        queue.add(start);
        visited.put(start, null);  // Mark the start node as visited

        // Perform BFS
        while (!queue.isEmpty()) {
            DataflowNode currentNode = queue.poll();

            // If we've reached the end node, reconstruct and return the path
            if (currentNode.equals(end)) {
                return reconstructPath(visited, start, end);
            }

            // Otherwise, explore its successors
            Set<DataflowNode> successors = getSuccsOf(currentNode);
            for (DataflowNode successor : successors) {
                // If the successor has not been visited, mark it as visited and enqueue it
                if (!visited.containsKey(successor) && successor!=null) {
                    visited.put(successor, currentNode);
                    queue.add(successor);
                }
            }
        }

        // If we reach here, there is no path from start to end
        return null;
    }

    private List<DataflowNode> reconstructPath(Map<DataflowNode, DataflowNode> visited, DataflowNode start, DataflowNode end) {
        List<DataflowNode> path = new ArrayList<>();
        DataflowNode currentNode = end;

        // Reconstruct the path by backtracking from end to start
        while (currentNode != null) {
            path.add(currentNode);
            currentNode = visited.get(currentNode);
        }

        // Reverse the path to get it in the correct order
        Collections.reverse(path);
        return path;
    }
    public boolean addCallStmt(SootMethod sootMethod,Unit invokeUnit){
        return this.method2InvokeStmt.computeIfAbsent(sootMethod,k->new HashSet<>()).add(invokeUnit);
    }
    public Map<SootMethod, Set<Unit>> getMethod2InvokeStmt(){
        return this.method2InvokeStmt;
    }
}
