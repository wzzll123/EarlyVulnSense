package dfg.element;

import soot.Type;
import soot.util.Numberable;
import utils.DataflowUtils;

import javax.xml.crypto.Data;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DataflowNode implements Numberable {
    protected Type type;
    private int number = 0;
    private Set<DataflowNode> successors=new HashSet<>();
    private Set<DataflowNode> predecessor=new HashSet<>();
    private Set<DataflowEdge> inEdges=new HashSet<>();
    private Set<DataflowEdge> outEdges=new HashSet<>();

    protected DataflowNode(Type type) {
        if (DataflowUtils.isUnresolved(type)) {
            throw new RuntimeException("Unresolved type " + type);
        }
        this.type = type;
    }
    public DataflowEdge addInEdge(EdgeKind kind, DataflowNode source, DataflowNode target){
        if (predecessor.add(source)){
            DataflowEdge edge = new DataflowEdge(kind, source, target);
            inEdges.add(edge);
            return edge;
        }
        return null;
    }
    public DataflowEdge getOrAddEdge(EdgeKind kind, DataflowNode source, DataflowNode target) {
        if (successors.add(target)) {
            DataflowEdge edge = new DataflowEdge(kind, source, target);
            outEdges.add(edge);
            return edge;
        }
        return null;

    }
    public Set<DataflowEdge> getOutEdges(){
        return outEdges;
    }
    public Set<DataflowEdge> getInEdges(){
        return inEdges;
    }
    public Set<DataflowNode> getSuccsOf(){
        Set<DataflowNode> result=new HashSet<>();
        for (DataflowEdge edge:getOutEdges()){
            result.add(edge.target());
        }
        return result;
    }
    public Set<DataflowNode> getSuccsOfByKind(Set<EdgeKind> allowedKind){
        Set<DataflowNode> result=new HashSet<>();
        for (DataflowEdge edge:getOutEdges()){
            if (allowedKind.contains(edge.kind())){
                result.add(edge.target());
            }
        }
        return result;
    }
    public Set<DataflowNode> getPredecessorsOfByKind(Set<EdgeKind> allowedKind){
        Set<DataflowNode> result=new HashSet<>();
        for (DataflowEdge edge:getInEdges()){
            if (allowedKind.contains(edge.kind())){
                result.add(edge.source());
            }
        }
        return result;
    }
    public Set<DataflowNode> getPredecessorsOf(){
        return predecessor;
    }

    public Type getType() {
        return type;
    }

    @Override
    public final int hashCode() {
        return number;
    }


    public final int getNumber() {
        return number;
    }

    public final void setNumber(int number) {
        this.number = number;
    }
}
