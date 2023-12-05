package utils;

import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;

import java.util.*;

public class DataFlowResult {
    public Map<SootMethod, Set<ParsedSinkCall>> junctureMethod2SinkMethod=new HashMap<>();
    public Map<SootMethod, Set<SootMethod>> junctureMethod2PatchMethod=new HashMap<>();
    public Set<ResultItem> dataflowResults=new HashSet<>();
    public void addResultItem(SootMethod junctureMethod,
                              ParsedSinkCall parsedSinkCall,Unit junctureUnit,ValueBox junctureValue){
        dataflowResults.add(new ResultItem(junctureMethod,parsedSinkCall,junctureUnit,junctureValue));
    }
    public void addResultItem(SootMethod junctureMethod,
                              ParsedSinkCall parsedSinkCall,Unit junctureUnit,ValueBox junctureValue, SootMethod patchMethod){
        dataflowResults.add(new ResultItem(junctureMethod,parsedSinkCall,junctureUnit,junctureValue,patchMethod));
    }
    public void printResult(){
        String formattedOutput="The newly introduced statements in method %s have data flow connection with sink of %s vulnerability";
        for (ResultItem item:dataflowResults){
            if (item.patchMethod==null){
                System.out.println(
                        String.format(formattedOutput,
                                item.junctureMethod,item.parsedSinkCall.vulType));
            }else {
                System.out.println(
                        String.format(formattedOutput,
                                item.patchMethod,item.parsedSinkCall.vulType));
            }

        }
//        String formattedOutput="The expr in %s can flow into sink method %s, juncture unit is %s";
//        String formattedOutputWithPatch="The expr in %s can flow into sink method %s and patch method %s, juncture unit is %s";
//        for (ResultItem item:dataflowResults){
//            if (item.patchMethod==null){
//                System.out.println(
//                        String.format(formattedOutput,
//                                item.junctureMethod.toString()+":"+item.junctureUnit.getJavaSourceStartLineNumber()+":"+item.junctureValue.getValue(),
//                                item.parsedSinkCall.sootMethod.getDeclaringClass()+";"+item.parsedSinkCall.sootMethod.getName()+";"+item.parsedSinkCall.sinkCall.getMessage(),
//                                item.junctureUnit));
//            }
//            else {
//                System.out.println(
//                        String.format(formattedOutputWithPatch,
//                                item.junctureMethod.toString()+":"+item.junctureUnit.getJavaSourceStartLineNumber()+":"+item.junctureValue.getValue(),
//                                item.parsedSinkCall.sootMethod.getDeclaringClass()+";"+item.parsedSinkCall.sootMethod.getName()+";"+item.parsedSinkCall.sinkCall.getMessage(),
//                                item.patchMethod.toString(), item.junctureUnit)
//                                );
//            }
//
//        }

    }
}
class ResultItem{
    public SootMethod junctureMethod;
    public ParsedSinkCall parsedSinkCall;
    public Unit junctureUnit;
    public ValueBox junctureValue;
    public SootMethod patchMethod;
//    public Unit patchUnit;
//    public Value patchValue;
    public ResultItem(SootMethod junctureMethod,
                      ParsedSinkCall parsedSinkCall,Unit junctureUnit,ValueBox junctureValue){
        this.junctureMethod=junctureMethod;
        this.parsedSinkCall=parsedSinkCall;
        this.junctureUnit=junctureUnit;
        this.junctureValue=junctureValue;
    }
    public ResultItem(SootMethod junctureMethod,
                      ParsedSinkCall parsedSinkCall,Unit junctureUnit,ValueBox junctureValue, SootMethod patchMethod){
        this.junctureMethod=junctureMethod;
        this.parsedSinkCall=parsedSinkCall;
        this.junctureUnit=junctureUnit;
        this.junctureValue=junctureValue;
        this.patchMethod=patchMethod;
    }
}
