package utils;

import dfg.element.DataflowNode;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.InvokeExpr;

import java.util.List;

public class ParsedSinkCall {
    public SinkCall sinkCall;
    public SootMethod sootMethod;
    public String vulType;
    public int argIndex; // -1 means this
    public int startLine;
    public int startColumn;
    public int endColumn;
    public Unit invokeUnit;
    public InvokeExpr invokeExpr;
    public String methodName;
    public Value argValue;
    public DataflowNode dataflowNode;
    public boolean isConstructor=false;
    public ParsedSinkCall(SinkCall sinkCall){
        this.sinkCall=sinkCall;
    }
}
