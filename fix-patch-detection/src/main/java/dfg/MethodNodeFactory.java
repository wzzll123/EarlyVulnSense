package dfg;

import dfg.element.*;
import dfg.taint.TaintConfig;
import dfg.taint.TaintTransfer;
import soot.*;
import soot.jimple.*;
import soot.jimple.spark.pag.ArrayElement;
import utils.Constant;

import javax.xml.crypto.Data;
import java.util.Set;
import java.util.stream.Stream;

import static utils.Constant.THIS_NODE;

public class MethodNodeFactory {
    protected DataflowGraph globalDataflowGraph;

//    protected SootMethod method;
    protected NodeManager nodeManager;
    protected TaintConfig taintConfig;
    public MethodNodeFactory(DataflowGraph globalDataflowGraph){
        this.globalDataflowGraph=globalDataflowGraph;
        this.nodeManager=globalDataflowGraph.getNodeManager();
        this.taintConfig=new TaintConfig("src/main/resources/taint-config.yml");
    }
    public void handleStmt(Stmt s, SootMethod method) {

        if (s.containsInvokeExpr()) {
//            localDataflowGraph.addCallStmt(s);
            handleInvokeStmt(s, method);
        } else {
            handleIntraStmt(s,method);
        }
    }
    private void handleInvokeStmt(Stmt s,SootMethod method){
        InvokeExpr ie = s.getInvokeExpr();
        int numArgs = ie.getArgCount();
        for (int i = 0; i < numArgs; i++) {
            Value arg = ie.getArg(i);
            if (!(arg.getType() instanceof RefLikeType) || arg instanceof NullConstant) {
                continue;
            }
            getNode(arg,method);
        }
        if (s instanceof AssignStmt) {
            Value l = ((AssignStmt) s).getLeftOp();
            if ((l.getType() instanceof RefLikeType)) {
                DataflowNode dest=getNode(l,method);

                // args to result
                Set<Integer> argIndexes=taintConfig.getArgsToResult(ie.getMethod());
                for (int argIndex:argIndexes){
                    DataflowNode src=getNode(ie.getArg(argIndex),method);
                    globalDataflowGraph.getOrAddEdge(EdgeKind.TRANSFER,src,dest);
                }

                // base to result
                if (ie instanceof InstanceInvokeExpr && taintConfig.isBaseToResult(ie.getMethod())){
                    DataflowNode src=getNode(((InstanceInvokeExpr) ie).getBase(),method);
                    globalDataflowGraph.getOrAddEdge(EdgeKind.TRANSFER,src,dest);
                }

            }
        }
        if (ie instanceof InstanceInvokeExpr) {
            // args to base
            DataflowNode dest = getNode(((InstanceInvokeExpr) ie).getBase(),method);
            Set<Integer> argIndexes=taintConfig.getArgsToBase(ie.getMethod());

            for(int argIndex:argIndexes) {
                DataflowNode src=getNode(ie.getArg(argIndex),method);
                globalDataflowGraph.getOrAddEdge(EdgeKind.TRANSFER, src,dest);
            }
        }
    }
    private void handleIntraStmt(Stmt s, SootMethod method) {

        s.apply(new AbstractStmtSwitch() {
            public void caseAssignStmt(AssignStmt as) {
                Value l = as.getLeftOp();
                Value r = as.getRightOp();

                if (!(l.getType() instanceof RefLikeType))
                    return;
                // check for improper casts, with mal-formed code we might get
                // l = (refliketype)int_type, if so just return
                if (r instanceof CastExpr && (!(((CastExpr) r).getOp().getType() instanceof RefLikeType))) {
                    return;
                }

                if (!(r.getType() instanceof RefLikeType))
                    throw new RuntimeException("Type mismatch in assignment (rhs not a RefLikeType) " + as
                            + " in method " + method.getSignature());
                DataflowNode dest = getNode(l,method);
                DataflowNode src = getNode(r,method);
                globalDataflowGraph.getOrAddEdge(EdgeKind.LOCAL_ASSIGN,src,dest);
            }
            public void caseReturnStmt(ReturnStmt rs) {
                if (!(rs.getOp().getType() instanceof RefLikeType))
                    return;
                DataflowNode retNode = getNode(rs.getOp(),method);
                globalDataflowGraph.getOrAddEdge(EdgeKind.LOCAL_ASSIGN,retNode, caseRet(method));
            }
            public void caseIdentityStmt(IdentityStmt is) {
                if (!(is.getLeftOp().getType() instanceof RefLikeType)) {
                    return;
                }
                DataflowNode dest = getNode(is.getLeftOp(),method);
                DataflowNode src = getNode(is.getRightOp(),method);
                globalDataflowGraph.getOrAddEdge(EdgeKind.LOCAL_ASSIGN, src, dest);
            }



        });
    }
    public DataflowNode getNode(Value v,SootMethod method) {
        if (v instanceof Local) {
            Local l=(Local) v;
            return caseLocal(l,method);
        } else if (v instanceof CastExpr) {
            CastExpr castExpr=(CastExpr) v;
            return caseCastExpr(castExpr,method);
        } else if (v instanceof StaticFieldRef) {
            StaticFieldRef sfr=(StaticFieldRef) v;
            return caseStaticFieldRef(sfr);
        } else if (v instanceof ParameterRef) {
            ParameterRef pr=(ParameterRef) v;
            return caseParameterRef(pr,method);
        } else if (v instanceof InstanceFieldRef){
            InstanceFieldRef ifr=(InstanceFieldRef) v;
            return caseInstanceFieldRef(ifr,method);
        } else if (v instanceof ArrayRef) {
            ArrayRef ar=(ArrayRef) v;
            return caseArrayRef(ar,method);
        } else if (v instanceof ThisRef) {
            return caseThis(method);
        } else if (v instanceof Immediate){
            Immediate immediate=(Immediate) v;
            return caseImmediate(immediate,method);
        }
        else {
            return null;
        }
    }

    private DataflowNode caseImmediate(Immediate immediate,SootMethod method){
        return nodeManager.makeLocalVarNode(immediate, immediate.getType(), method);
    }
    private DataflowNode caseThis(SootMethod method) {
        Type type = method.isStatic() ? RefType.v("java.lang.Object") : method.getDeclaringClass().getType();
        VarNode ret = nodeManager.makeLocalVarNode(new Parm(method, THIS_NODE), type, method);
        nodeManager.getMethod2this().put(method,ret);
        return ret;
    }

    private DataflowNode caseArrayRef(ArrayRef ar, SootMethod method) {
        return caseArray(caseLocal((Local) ar.getBase(),method));
    }

    private DataflowNode caseArray(VarNode base) {
        return nodeManager.makeFieldRefNode(base, ArrayElement.v());
    }

    private DataflowNode caseInstanceFieldRef(InstanceFieldRef ifr, SootMethod method) {
        SootField sf = ifr.getField();
        if (sf==null){
            System.out.println("Warnning:" + ifr + " is resolved to be a null field in Scene.");
        }
        return nodeManager.makeFieldValNode(new Field(sf));
//        return nodeManager.makeFieldRefNode(nodeManager.makeLocalVarNode(ifr.getBase(),ifr.getBase().getType(),method),new Field(sf));
    }

    private DataflowNode caseStaticFieldRef(StaticFieldRef sfr) {
        return nodeManager.makeGlobalVarNode(sfr.getField(),sfr.getField().getType());
    }

    private DataflowNode caseCastExpr(CastExpr castExpr, SootMethod method) {
        DataflowNode operandNode=getNode(castExpr.getOp(),method);
        VarNode castNode=nodeManager.makeLocalVarNode(castExpr,castExpr.getType(),method);
        globalDataflowGraph.getOrAddEdge(EdgeKind.CAST,operandNode,castNode);
        return castNode;
    }

    public VarNode caseLocal(Local l,SootMethod method) {
        return nodeManager.makeLocalVarNode(l, l.getType(), method);
    }
    public VarNode caseRet(SootMethod method) {
        VarNode ret = nodeManager.makeLocalVarNode(new Parm(method, Constant.RETURN_NODE), method.getReturnType(), method);
//        ret.setInterProcSource();
        return ret;
    }
    public VarNode caseParameterRef(ParameterRef pr,SootMethod method) {
        return caseParm(pr.getIndex(),method);
    }
    public VarNode caseParm(int index,SootMethod method) {
        VarNode ret = nodeManager.makeLocalVarNode(new Parm(method, index), method.getParameterType(index), method);
//        ret.setInterProcTarget();
        return ret;
    }

}
