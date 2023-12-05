import dfg.DataflowGraph;
import dfg.MethodNodeFactory;
import dfg.element.DataflowNode;
import dfg.element.NodeManager;
import soot.*;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Stmt;
import utils.ControlFlowResult;
import utils.DataFlowResult;
import utils.ParsedSinkCall;
import utils.SinkCall;

import javax.xml.soap.Node;
import java.util.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataflowFeature extends SceneTransformer {
    private DataflowGraph dataflowGraph;
    private Map<SootMethod,Set<Unit>> method2junctureUnits;
    private Map<SootMethod, Set<SinkCall>> sootMethod2sinkCall;
    private List<ParsedSinkCall> parsedSinkCalls;
    private NodeManager nodeManager;
    private MethodNodeFactory nodeFactory;
    private Map<SootMethod, Set<SootMethod>> junctureMethod2SinkMethod;
    private Map<SootMethod, Set<SootMethod>> junctureMethod2PatchMethods;
    private Map<SootMethod, Set<Unit>> juncture2unitBehindPatchUnitInSameMethod;
    private Map<SootMethod,Set<Unit>> patchMethod2patchUnit;
    private DataFlowResult dataFlowResult;
    private Set<Unit> patchUnits;
    private Set<ParsedSinkCall> alreadyFlowSink=new HashSet<>();
    public DataflowFeature(DataflowGraph dataflowGraph, ControlFlowResult controlFlowResult, DataFlowResult dataFlowResult){
        this.dataflowGraph=dataflowGraph;
        this.method2junctureUnits=controlFlowResult.method2junctureUnits;
        this.sootMethod2sinkCall=controlFlowResult.sootMethod2sinkCall;
        this.parsedSinkCalls=new ArrayList<>();
        this.nodeManager=dataflowGraph.getNodeManager();
        this.nodeFactory=dataflowGraph.getMethodNodeFactory();
        this.junctureMethod2SinkMethod=controlFlowResult.junctureMethod2SinkMethod;
        this.junctureMethod2PatchMethods=controlFlowResult.junctureMethod2PatchMethods;
        this.juncture2unitBehindPatchUnitInSameMethod=controlFlowResult.juncture2unitBehindPatchUnitInSameMethod;
        this.dataFlowResult=dataFlowResult;
        this.patchMethod2patchUnit=controlFlowResult.patchMethod2patchUnits;


    }
    private Set<Unit> getPatchUnits(){
        Set<Unit> mergedSet = new HashSet<>();

        for (Set<Unit> unitSet : patchMethod2patchUnit.values()) {
            mergedSet.addAll(unitSet);
        }

        return mergedSet;
    }
    private boolean analyzePatchUnitsDataflow() {
        boolean existDataflow=false;
        for (SootMethod junctureMethod : method2junctureUnits.keySet()) {
            if (!patchMethod2patchUnit.containsKey(junctureMethod)) {
                continue;
            }
            for (Unit junctureUnit : method2junctureUnits.get(junctureMethod)) {
                for (ValueBox valueBox : junctureUnit.getUseAndDefBoxes()) {
                    DataflowNode node = nodeFactory.getNode(valueBox.getValue(), junctureMethod);
                    if (node != null) {
                        for (ParsedSinkCall parsedSinkCall : parsedSinkCalls) {
//                            if (parsedSinkCall.vulType.equals("deserialization")){
//                                System.out.println("debug catch sink");
//                            }
                            // filter
                            if (!junctureMethod2SinkMethod.get(junctureMethod).contains(parsedSinkCall.sootMethod))
                                continue;
                            if (parsedSinkCall.dataflowNode == null) {
                                continue;
                            }
                            //remove sink call in front of juncture unit
                            if (junctureMethod.equals(parsedSinkCall.sootMethod)) {
                                if (juncture2unitBehindPatchUnitInSameMethod.containsKey(junctureMethod)) {
                                    if (!juncture2unitBehindPatchUnitInSameMethod.get(junctureMethod).contains(parsedSinkCall.invokeUnit))
                                        continue;
                                }

                            }
                            if (dataflowGraph.hasConnection(node, parsedSinkCall.dataflowNode)) {
                                dataFlowResult.junctureMethod2SinkMethod.computeIfAbsent(junctureMethod, k -> new HashSet<>()).add(parsedSinkCall);
                                if (patchUnits.contains(junctureUnit)) {
                                    existDataflow=true;
                                    dataFlowResult.addResultItem(junctureMethod, parsedSinkCall, junctureUnit, valueBox);
                                }
                            }
                        }
                    }
                }
            }
        }
        return existDataflow;
    }
    private void analyzeNonPatchUnitsDataflow() {
        for (SootMethod junctureMethod : method2junctureUnits.keySet()) {
            if (patchMethod2patchUnit.containsKey(junctureMethod)) {
                continue;
            }
            for (Unit junctureUnit : method2junctureUnits.get(junctureMethod)) {
                for (ValueBox valueBox : junctureUnit.getUseAndDefBoxes()) {
                    DataflowNode node = nodeFactory.getNode(valueBox.getValue(), junctureMethod);
                    if (node != null) {
                        for (ParsedSinkCall parsedSinkCall : parsedSinkCalls) {
                            if (alreadyFlowSink.contains(parsedSinkCall)){
                                continue;
                            }
                            // filter
                            if (!junctureMethod2SinkMethod.get(junctureMethod).contains(parsedSinkCall.sootMethod))
                                continue;
                            if (parsedSinkCall.dataflowNode == null) {
                                continue;
                            }
                            //remove sink call in front of juncture unit
                            if (junctureMethod.equals(parsedSinkCall.sootMethod)) {
                                if (juncture2unitBehindPatchUnitInSameMethod.containsKey(junctureMethod)) {
                                    if (!juncture2unitBehindPatchUnitInSameMethod.get(junctureMethod).contains(parsedSinkCall.invokeUnit))
                                        continue;
                                }

                            }
                            if (dataflowGraph.hasConnection(node, parsedSinkCall.dataflowNode)) {
                                dataFlowResult.junctureMethod2SinkMethod.computeIfAbsent(junctureMethod, k -> new HashSet<>()).add(parsedSinkCall);
                                for (SootMethod patchMethod:junctureMethod2PatchMethods.get(junctureMethod)){
                                    for (Unit patchUnit:patchMethod2patchUnit.get(patchMethod)){
                                        for (ValueBox patchValueBox:patchUnit.getUseAndDefBoxes()){
                                            DataflowNode patchNode=nodeFactory.getNode(patchValueBox.getValue(),patchMethod);
                                            if (node!=null && patchNode!=null && (dataflowGraph.hasConnection(node,patchNode) || dataflowGraph.hasConnection(patchNode,node))){
                                                alreadyFlowSink.add(parsedSinkCall);
                                                dataFlowResult.addResultItem(junctureMethod,parsedSinkCall,junctureUnit, valueBox,patchMethod);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        this.patchUnits=getPatchUnits();
        collectParsedSinkCall();
        boolean existPatchDataflow=analyzePatchUnitsDataflow();
        if (!existPatchDataflow){
            analyzeNonPatchUnitsDataflow();
        }
//        for (SootMethod junctureMethod:method2junctureUnits.keySet()){
//            for (Unit junctureUnit:method2junctureUnits.get(junctureMethod)){
//                for (ValueBox valueBox:junctureUnit.getUseAndDefBoxes()){
//                    DataflowNode node= nodeFactory.getNode(valueBox.getValue(),junctureMethod);
//                    if (node!=null){
//                        for (ParsedSinkCall parsedSinkCall:parsedSinkCalls){
//                            // filter
//                            if (!junctureMethod2SinkMethod.get(junctureMethod).contains(parsedSinkCall.sootMethod))
//                                continue;
//                            if (parsedSinkCall.dataflowNode==null){
//                                continue;
//                            }
//                            //remove sink call in front of juncture unit
//                            if (junctureMethod.equals(parsedSinkCall.sootMethod)){
//                                if (juncture2unitBehindPatchUnitInSameMethod.containsKey(junctureMethod)){
//                                    if (!juncture2unitBehindPatchUnitInSameMethod.get(junctureMethod).contains(parsedSinkCall.invokeUnit))
//                                        continue;
//                                }
//
//                            }
//                            if (dataflowGraph.hasConnection(node,parsedSinkCall.dataflowNode)){
//                                dataFlowResult.junctureMethod2SinkMethod.computeIfAbsent(junctureMethod,k->new HashSet<>()).add(parsedSinkCall);
//                                if (patchUnits.contains(junctureUnit)){
//                                    dataFlowResult.addResultItem(junctureMethod,parsedSinkCall,junctureUnit, valueBox.getValue());
//                                }else {
//                                    for (SootMethod patchMethod:junctureMethod2PatchMethods.get(junctureMethod)){
//                                        for (Unit patchUnit:patchMethod2patchUnit.get(patchMethod)){
//                                            for (ValueBox patchValueBox:patchUnit.getUseAndDefBoxes()){
//                                                DataflowNode patchNode=nodeFactory.getNode(patchValueBox.getValue(),patchMethod);
//                                                if (node!=null && (dataflowGraph.hasConnection(node,patchNode) || dataflowGraph.hasConnection(patchNode,node))){
//                                                    dataFlowResult.addResultItem(junctureMethod,parsedSinkCall,junctureUnit, valueBox.getValue(),patchMethod);
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
        dataFlowResult.printResult();



    }
    private void collectParsedSinkCall(){
        for (SootMethod sinkMethod:sootMethod2sinkCall.keySet()){
            for (SinkCall sinkCall:sootMethod2sinkCall.get(sinkMethod)){
                ParsedSinkCall tmpParsed=simpleParseString(sinkCall,sinkMethod);

                for (Unit unit:sinkMethod.retrieveActiveBody().getUnits()){
                    boolean isSinkCall=false;
                    if (unit.getJavaSourceStartLineNumber()!= tmpParsed.startLine)    continue;
                    if (((Stmt) unit).containsInvokeExpr()){
                        InvokeExpr invokeExpr= ((Stmt) unit).getInvokeExpr();
                        //constructor
                        if (tmpParsed.isConstructor){
                            if (invokeExpr.getMethod().isConstructor()
                                    && invokeExpr.getMethod().toString().contains(tmpParsed.methodName)){
                                isSinkCall=true;
                            }
                        }
                        else if (tmpParsed.methodName.equals(invokeExpr.getMethod().getName())){
                            isSinkCall=true;
                        }
                        if (isSinkCall){
                            tmpParsed.invokeUnit=unit;
                            tmpParsed.invokeExpr=invokeExpr;
                            if (tmpParsed.argIndex==-1){
                                tmpParsed.argValue=((InstanceInvokeExpr) invokeExpr).getBase();
                            }else {
//                                System.out.println(invokeExpr);

                                try{
                                    tmpParsed.argValue=invokeExpr.getArg(tmpParsed.argIndex);
                                }catch (ArrayIndexOutOfBoundsException e){
                                    // maybe a soot bug for virtualinvoke r0.<org.apache.struts2.views.jsp.iterator.IteratorGeneratorTag: java.lang.String findString(java.lang.String)>($r18)
                                    break;
                                }

                            }

                            tmpParsed.dataflowNode=nodeFactory.getNode(tmpParsed.argValue,sinkMethod);
                            parsedSinkCalls.add(tmpParsed);
                            break;
                        }
                    }

                }
            }

        }
    }
    private ParsedSinkCall simpleParseString(SinkCall sinkCall,SootMethod sinkMethod){
        ParsedSinkCall result=new ParsedSinkCall(sinkCall);
        result.sootMethod=sinkMethod;
        String[] tmp=sinkCall.getMessage().getText().split(";");
        result.vulType=tmp[0];
        result.methodName=tmp[1].split("\\(")[0];
        if (result.methodName.contains("new ")){
            result.methodName= result.methodName.substring(4);
            result.isConstructor=true;
        }
        if (tmp[2].contains("this")){
            result.argIndex=-1;
        }else {
            result.argIndex=Integer.parseInt(tmp[2].replaceAll("[^0-9]", ""));
        }

        tmp=sinkCall.getLocation().split(":");
        result.startLine= Integer.parseInt(tmp[1]);
        result.startColumn=Integer.parseInt(tmp[2]);
        result.endColumn=Integer.parseInt(tmp[3]);
        return result;
    }
}
