package dfg.element;

import soot.Local;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.jimple.spark.pag.SparkField;
import soot.util.ArrayNumberer;
import utils.DataFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NodeManager {
    protected ArrayNumberer<ValNode> valNodeNumberer = new ArrayNumberer<>();
    protected ArrayNumberer<Local> localNumberer = new ArrayNumberer<>();
    protected ArrayNumberer<FieldRefNode> fieldRefNodeNumberer = new ArrayNumberer<>();
    protected ArrayNumberer<AllocNode> allocNodeNumberer = new ArrayNumberer<>();

    protected Map<SootMethod, List<Parm>> method2Param=new HashMap<>();
    protected Map<SootMethod, VarNode> method2this=new HashMap<>();

    protected final Set<SootField> globals;
    protected final Set<Local> locals;


    protected final Map<Object, ValNode> valToValNode;
    protected final Map<Object, AllocNode> valToAllocNode;
    public final Map<Object, SootMethod> valToMethod;

    public NodeManager() {
        this.valToValNode = DataFactory.createMap();
        this.valToAllocNode=DataFactory.createMap();
        this.valToMethod=DataFactory.createMap();
        this.globals=DataFactory.createSet();
        this.locals=DataFactory.createSet();
    }
    public AllocNode makeAllocNode(Object newExpr, Type type, SootMethod m) {
        AllocNode ret=valToAllocNode.get(newExpr);
        if (ret==null){
            valToAllocNode.put(newExpr,ret=new AllocNode(newExpr,type,m));
            allocNodeNumberer.add(ret);
        }else if (!(ret.getType().equals(type))) {
            throw new RuntimeException("NewExpr " + newExpr + " of type " + type + " previously had type " + ret.getType());
        }
        return ret;
    }
    public GlobalVarNode makeGlobalVarNode(Object value, Type type) {
        GlobalVarNode ret = (GlobalVarNode) valToValNode.get(value);
        if (ret == null) {
            ret = (GlobalVarNode) valToValNode.computeIfAbsent(value, k -> new GlobalVarNode(value, type));
            valNodeNumberer.add(ret);
            if (value instanceof SootField) {
                globals.add((SootField) value);
            }
        } else if (!(ret.getType().equals(type))) {
            throw new RuntimeException("Value " + value + " of type " + type + " previously had type " + ret.getType());
        }
        return ret;
    }
    public LocalVarNode makeLocalVarNode(Object value, Type type, SootMethod method) {
        LocalVarNode ret = (LocalVarNode) valToValNode.get(value);
        if (ret == null) {
            valToValNode.put(value, ret = new LocalVarNode(value, type, method));
            valToMethod.put(value,method);
            valNodeNumberer.add(ret);
            if (value instanceof Local) {
                Local local=(Local) value;
                if (local.getNumber() == 0) {
                    localNumberer.add(local);
                }
                locals.add(local);
            }
        } else if (!(ret.getType().equals(type))) {
            throw new RuntimeException("Value " + value + " of type " + type + " previously had type " + ret.getType());
        }
        return ret;
    }
    public FieldRefNode makeFieldRefNode(VarNode base, SparkField field) {
        FieldRefNode ret = base.dot(field);
        if (ret == null) {
            ret = new FieldRefNode(base, field);
            fieldRefNodeNumberer.add(ret);
        }
        return ret;
    }
    public FieldValNode makeFieldValNode(SparkField field) {
        FieldValNode ret = (FieldValNode) valToValNode.get(field);
        if (ret == null) {
            valToValNode.put(field, ret = new FieldValNode(field));
            valNodeNumberer.add(ret);
        }
        return ret;
    }
    public Map<SootMethod,VarNode> getMethod2this(){
        return this.method2this;
    }
}
