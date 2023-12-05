package dfg.element;

import soot.RefType;
import soot.SootMethod;
import soot.Type;
import soot.util.Numberable;

public class AllocNode extends DataflowNode implements Numberable {
    protected Object newExpr;
    private final SootMethod method;
    public AllocNode(Object newExpr, Type t, SootMethod m) {
        super(t);
        this.method = m;
        if (t instanceof RefType) {
            RefType rt=(RefType) t;
//            if (rt.getSootClass().isAbstract()) {
//                boolean usesReflectionLog = CoreConfig.v().getAppConfig().REFLECTION_LOG != null;
//                if (!usesReflectionLog) {
//                    throw new RuntimeException("Attempt to create allocnode with abstract type " + t);
//                }
//            }
        }
        this.newExpr = newExpr;
    }
    public Object getNewExpr() {
        return newExpr;
    }

    public String toString() {
        return "AllocNode " + getNumber() + " " + newExpr + " in method " + method;
    }

    public SootMethod getMethod() {
        return method;
    }

    public AllocNode base() {
        return this;
    }
}
