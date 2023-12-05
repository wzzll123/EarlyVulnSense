package blockListPack;

import dfg.DataflowGraph;
import dfg.MethodNodeFactory;
import dfg.element.*;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.CompleteUnitGraph;
import soot.toolkits.scalar.*;

import java.util.*;

public class ConditionGather extends SceneTransformer {
    private Map<SootMethod, Set<Unit>> patchMethod2addPatchUnit;
    private DataflowGraph dataflowGraph;
    private Set<Value> possibleBlackLists=new HashSet<>();
    private Set<Value> possibleLocalBlackLists=new HashSet<>();
    private MethodNodeFactory nodeFactory;
    private Map<Object, SootMethod> valToMethod;
    private Map<SootMethod, Set<Unit>> conditionUnits;
    private Map<Unit, List<Local>> usedLocalCache=new HashMap<>();
    private Map<Local, List<Unit>> defCache=new HashMap<>();

    public ConditionGather(Map<SootMethod, Set<Unit>> patchMethod2addPatchUnit, DataflowGraph dataflowGraph,
                           Map<SootMethod, Set<Unit>> conditionUnits){
        this.patchMethod2addPatchUnit=patchMethod2addPatchUnit;
        this.dataflowGraph=dataflowGraph;
        this.nodeFactory=dataflowGraph.getMethodNodeFactory();
        this.conditionUnits=conditionUnits;
        this.valToMethod=dataflowGraph.getNodeManager().valToMethod;
    }
    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
//        for (SootMethod sm:dataflowGraph.getMethod2InvokeStmt().keySet()){
//            if (sm.toString().contains("<jodd.json.MapToBean: java.lang.Object map2bean(java.util.Map,java.lang.Class)>")){
//                for (Unit unit:sm.getActiveBody().getUnits()){
//                    for (ValueBox valueBox:unit.getUseBoxes()){
//                        Value value = valueBox.getValue();
//                        if (value.toString().contains("classnameWhitelist")){
//                            System.out.println(1);
//                        }
//                    }
//                }
//            }
//        }
        for (SootMethod patchMethod: patchMethod2addPatchUnit.keySet()){
            for (Unit addUnit: patchMethod2addPatchUnit.get(patchMethod)){
                InvokeExpr invokeExpr = ((Stmt)addUnit).getInvokeExpr();
                if (invokeExpr instanceof InstanceInvokeExpr){
                    possibleBlackLists.add( ((InstanceInvokeExpr) invokeExpr).getBase() );
                } else if (invokeExpr instanceof StaticInvokeExpr && ((Stmt)addUnit) instanceof AssignStmt) {
                    AssignStmt addStmt=(AssignStmt) addUnit;
                    // List<String> ALLOWED_PACKAGES = Arrays.asList()
                    possibleBlackLists.add(addStmt.getLeftOp());
                }
            }
        }
        for (Value possibleBlackListVar:possibleBlackLists){
            DataflowNode possibleBlackListNode=dataflowGraph.getMethodNodeFactory().getNode(possibleBlackListVar,valToMethod.get(possibleBlackListVar));
//            Set<DataflowNode> successors = dataflowGraph.getSuccsClosureOf(possibleBlackListNode);
            Set<DataflowNode> successors = dataflowGraph.getAliases(possibleBlackListNode);
            handleSuccessor(successors);
        }

    }
    private Map<SootMethod,Set<Value>> processSuccessors(Set<DataflowNode> successors){
        // add method information for every successor
        Map<SootMethod,Set<Value>> result=new HashMap<>();
        for (DataflowNode node:successors){
            if (node instanceof LocalVarNode){
                LocalVarNode localValNode=(LocalVarNode) node;
                if (localValNode.getVariable() instanceof Parm){
                    // all params finally are assigned to a local variable
                    continue;
                }
                SootMethod blackCheckMethod = localValNode.getMethod();
                result.computeIfAbsent(blackCheckMethod,k->new HashSet<>()).add((Value) localValNode.getVariable());
            }
        }
        return result;
    }
    private Map<SootMethod,Set<Unit>> getConditionUnitDependentOn(Value blackListNode, SootMethod blackCheckMethod, boolean traversalMethod){
        Map<SootMethod,Set<Unit>> result=new HashMap<>();
        if (!(blackListNode instanceof Local)){
            return result;
        }
        Body blackCheckBody= blackCheckMethod.retrieveActiveBody();
        LocalDefs localDefs = new SimpleLocalDefs(new CompleteUnitGraph(blackCheckMethod.retrieveActiveBody()));
        LocalUses localUses = new SimpleLocalUses(blackCheckBody,localDefs);
        Local blacklistLocal=(Local) blackListNode;
        Unit blacklistDef=localDefs.getDefsOf(blacklistLocal).get(0);


//        int defUseSearchDepth=8; // for performance
        Set<Unit> visitedUnits = new HashSet<>();
        Queue<Unit> worklist = new LinkedList<>();
        Queue<Integer> distanceList= new LinkedList<>();
        worklist.add(blacklistDef); distanceList.add(1);
        while (!worklist.isEmpty()) {
            Unit currentUnit = worklist.poll();
            int currentDistance = distanceList.poll();
            // Mark this local as visited.
            visitedUnits.add(currentUnit);
            // Get the definitions of the current local.
            if (currentUnit instanceof IfStmt){
                result.computeIfAbsent(blackCheckMethod,k->new HashSet<>()).add(currentUnit);
            }
            if (currentUnit instanceof ReturnStmt){
                if (traversalMethod){
                    VarNode returnNode = nodeFactory.caseRet(blackCheckMethod);  // get fake return node
                    Set<EdgeKind> allowedKind=new HashSet<>(); allowedKind.add(EdgeKind.RETURN);
                    Set<DataflowNode> returnLeftNodes=dataflowGraph.getSuccsOfByKind(returnNode,allowedKind);
                    for (DataflowNode node:returnLeftNodes){
                        if (node instanceof LocalVarNode){
                            LocalVarNode localVarNode=(LocalVarNode) node;
                            Map<SootMethod,Set<Unit>> travelResult=getConditionUnitDependentOn((Value) localVarNode.getVariable(),localVarNode.getMethod(),false);
                            for (SootMethod sm:travelResult.keySet()){
                                result.computeIfAbsent(sm,k->new HashSet<>()).addAll(travelResult.get(sm));
                            }
                        }

                    }
                }
                else {
                    continue;
                }
            }
            List<UnitValueBoxPair> localUsedList=localUses.getUsesOf(currentUnit);
            for (UnitValueBoxPair pair : localUsedList) {
                Unit usedUnit=pair.getUnit();
                if (!visitedUnits.contains(usedUnit)){
                    worklist.add(usedUnit);
                    distanceList.add(currentDistance+1);
                }
            }
        }
        return result;
    }
    private boolean checkDataDependent(Local conditionLocal, Set<Value> blacklistNodes, LocalUses localUses, LocalDefs localDefs){
        for (Value blacklistNode:blacklistNodes){
            if (! (blacklistNode instanceof Local)){
                continue;
            }
            Local blacklistLocal=(Local) blacklistNode;
            List<Unit> blacklistNodeDefs=localDefs.getDefsOf(blacklistLocal);

        }

        return false;
    }
    private boolean checkDataDependent(Local conditionLocal, Set<Value> blacklistNodes, LocalDefs localDefs){
        // check whether conditionLocal are data dependent on value in blacklistNodes
        int defUseSearchDepth=10; // for performance
        Set<Local> visitedLocals = new HashSet<>();
        Queue<Local> worklist = new LinkedList<>();
        Queue<Integer> distanceList= new LinkedList<>();
        worklist.add(conditionLocal); distanceList.add(1);
        while (!worklist.isEmpty()) {
            Local currentLocal = worklist.poll();
            int currentDistance = distanceList.poll();
            // Check if the current local is in the blacklistNodes set.
            if (blacklistNodes.contains(currentLocal)) {
                return true; // It is data-dependent, so return true.
            }
            if (currentDistance > defUseSearchDepth){
                return false;
            }
            // Mark this local as visited.
            visitedLocals.add(currentLocal);
            // Get the definitions of the current local.

            if (!defCache.containsKey(currentLocal)) {
                defCache.put(currentLocal, localDefs.getDefsOf(currentLocal).subList(0,1));
            }
            List<Unit> localDefList=defCache.get(currentLocal);


            for (Unit unit : localDefList) {
                // Extract the locals used in the unit.
                List<Local> usedLocals = extractUsedLocals(unit);

                // Add the usedLocals to the worklist for further exploration.
                for (Local usedLocal : usedLocals) {
                    if (!visitedLocals.contains(usedLocal)) {
                        worklist.add(usedLocal);
                        distanceList.add(currentDistance+1);
                    }
                }
            }
        }
        return false;

    }
    private List<Local> extractUsedLocals(Unit unit) {
        if (usedLocalCache.containsKey(unit)){
            return usedLocalCache.get(unit);
        }
        List<Local> usedLocals = new ArrayList<>();

        if (unit instanceof Stmt) {
            Stmt stmt = (Stmt) unit;
            Iterator<ValueBox> defBoxes = stmt.getUseBoxes().iterator();

            while (defBoxes.hasNext()) {
                ValueBox defBox = defBoxes.next();
                Value defValue = defBox.getValue();

                if (defValue instanceof Local) {
                    usedLocals.add((Local) defValue);
                }
            }
        }
        usedLocalCache.put(unit,usedLocals);

        return usedLocals;
    }
    private void handleSuccessor(Set<DataflowNode> successors){
        Map<SootMethod,Set<Value>> method2successor= processSuccessors(successors);
        for (SootMethod blackCheckMethod: method2successor.keySet()){
            if (!blackCheckMethod.hasActiveBody()){
                continue;
            }
            for (Value permissionListValue:method2successor.get(blackCheckMethod)){
                Map<SootMethod,Set<Unit>> result=getConditionUnitDependentOn(permissionListValue,blackCheckMethod,true);
                for (SootMethod sm:result.keySet()){
                    conditionUnits.computeIfAbsent(sm,k->new HashSet<>()).addAll(result.get(sm));
                }

            }

//            Body blackCheckBody= blackCheckMethod.retrieveActiveBody();
//            LocalDefs localDefs = new SimpleLocalDefs(new CompleteUnitGraph(blackCheckMethod.retrieveActiveBody()));
//            LocalUses localUses = new SimpleLocalUses(blackCheckBody,localDefs);
//            Set<Unit> defUnitForCondition=new HashSet<>();
//            for (Unit unit: blackCheckBody.getUnits()) {
//                if (! (unit instanceof IfStmt)){
//                    continue;
//                }
//                for (ValueBox valueBox : unit.getUseBoxes()) {
//                    Value value = valueBox.getValue();
//                    if (value instanceof ConditionExpr) {
//                        // get condition expr and check whether it is data dependent on blacklist
//                        ConditionExpr conditionExpr = (ConditionExpr) value;
//
//                        if (conditionExpr.getOp1() instanceof Local) {
//                            if (checkDataDependent((Local) conditionExpr.getOp1(), method2successor.get(blackCheckMethod),localDefs)) {
//                                conditionUnits.computeIfAbsent(blackCheckMethod,k->new HashSet<>()).add(unit);
//                            }
//
//                        }
//                        if (conditionExpr.getOp2() instanceof Local) {
//                            if (checkDataDependent((Local) conditionExpr.getOp2(), method2successor.get(blackCheckMethod),localDefs)) {
//                                conditionUnits.computeIfAbsent(blackCheckMethod,k->new HashSet<>()).add(unit);
//                            }
//                        }
//                    }
//                }
//            }
        }

    }
}
