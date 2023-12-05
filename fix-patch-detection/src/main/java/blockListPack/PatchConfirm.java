package blockListPack;

import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import utils.SinkCall;

import java.util.*;

public class PatchConfirm extends SceneTransformer {
    private List<String> patchLines;
    private Map<SootMethod, Set<Unit>> patchMethod2addPatchUnit;
    static String[] addMethodSignatureArray={"<java.util.Set: boolean add(java.lang.Object)>","<java.util.Arrays: java.util.List asList(java.lang.Object[])>",
                                            "<java.util.List: boolean add(java.lang.Object)>"};
    private List<String> addMethodSignatureList;
    private Set<SootMethod> correspondingMethods; // used for dataflow graph creation, now using all methods in the same classes with patch methods
    public PatchConfirm(List<String> patchLines, Map<SootMethod, Set<Unit>> patchMethod2addPatchUnit, Set<SootMethod> initMethods){
        this.patchLines=patchLines;
        this.patchMethod2addPatchUnit=patchMethod2addPatchUnit;
        this.correspondingMethods=initMethods;
        this.addMethodSignatureList=Arrays.asList(addMethodSignatureArray);
    }
    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        Set<SootClass> patchClasses=new HashSet<>();
        for(SootClass sc : Scene.v().getApplicationClasses()) {
            // for performance, only focus on the same file
            String currentJavaFileName = sc.getName().split("\\$")[0] + ".java:";
            for (SootMethod m : sc.getMethods()) {
                Boolean isPatchMethod = false;
                if (!m.hasActiveBody())
                    continue;
                Body methodBody = m.retrieveActiveBody();
                for (Unit unit : methodBody.getUnits()) {
                    for(String patchLine:patchLines){
                        if (!unit.hasTag("LineNumberTag")){
                            continue;
                        }

                        int lineNumber=unit.getJavaSourceStartLineNumber();
                        if(patchLine.equals(sc.getShortJavaStyleName().split("\\$")[0]+".java:"+lineNumber)){
                            if (((Stmt)unit).containsInvokeExpr()){
                                InvokeExpr invokeExpr = ((Stmt)unit).getInvokeExpr();
                                if (addMethodSignatureList.contains(invokeExpr.getMethod().toString())){
                                    patchMethod2addPatchUnit.computeIfAbsent(m,k->new HashSet<>()).add(unit);
                                    patchClasses.add(sc);
                                    System.out.println("have add operation");
                                }

                            }
                        }
                    }
                }
            }
        }
        for (SootClass patchClass: patchClasses){
            correspondingMethods.addAll(patchClass.getMethods());
        }
    }
}
