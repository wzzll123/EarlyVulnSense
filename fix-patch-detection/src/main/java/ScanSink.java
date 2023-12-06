import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import utils.CodeQlSink;
import utils.Message;
import utils.SinkCall;
import utils.SootInitUtils;

import java.util.*;

import static soot.SootClass.HIERARCHY;

public class ScanSink extends SceneTransformer {
    static List<CodeQlSink> codeQlSinks;
    static Map<SootClass, List<CodeQlSink>> class2sinkRules; // a soot class to sink rules that match methods of this class
    static Map<SootClass, Set<SootClass>> class2subClasses=new HashMap<>();
    private List<SinkCall> sinkCalls;
    public ScanSink(List<SinkCall> sinkCalls){
        this.sinkCalls=sinkCalls;
    }
    public static void main(String[] args){
        List<String> jarFiles= Arrays.asList(args[0].split(":"));
        SootInitUtils.sootInit(jarFiles);
        getSinkCallFromCodeQlRules();
        ScanSink scanSink = new ScanSink(new ArrayList<>());
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.scanSink", scanSink));

        // Start the generation
        PackManager.v().runPacks();
    }

    static public void getSinkCallFromCodeQlRules(){
        codeQlSinks=CodeQlSink.read();
        class2sinkRules =new HashMap<>();
        for (CodeQlSink codeQlSink:codeQlSinks){
            try {
                SootClass sinkClass=Scene.v().getSootClass(codeQlSink.packageName+"."+codeQlSink.typeName);
                class2sinkRules.computeIfAbsent(sinkClass,
                        k->new ArrayList<>()).add(codeQlSink);
            }catch (RuntimeException runtimeException){
                continue;
            }
        }
    }
    protected void processYmlVul(Stmt stmt, Body b){
        InvokeExpr invokeExpr = stmt.getInvokeExpr();
        SootClass invokeMethodDeclaringClass = invokeExpr.getMethodRef().getDeclaringClass();
        String methodName;
        if (invokeExpr.getMethodRef().isConstructor()){
            methodName= invokeMethodDeclaringClass.getShortName();
        }else {
            methodName = invokeExpr.getMethodRef().getName();
        }

        List<Type> argTypes = invokeExpr.getMethodRef().getParameterTypes();

        CodeQlSink matchesSinkModel=matchesSinkModel(invokeMethodDeclaringClass,methodName,argTypes);
        if (matchesSinkModel != null){
            StringBuilder callNameBuilder=new StringBuilder();
            if (invokeExpr.getMethodRef().isConstructor()){
                callNameBuilder.append("new ");
                callNameBuilder.append(invokeMethodDeclaringClass.getShortName());

            }else {
                callNameBuilder.append(methodName);
            }
            callNameBuilder.append("(...)");

            String location= b.getMethod().getDeclaringClass().getName().split("\\$")[0]+".java" +":"+stmt.getJavaSourceStartLineNumber()+":"+stmt.getJavaSourceStartColumnNumber()+":"+0;
            String message=matchesSinkModel.kind+";"+callNameBuilder.toString()+";"+matchesSinkModel.input;

            boolean isBlockKind=false;
            for (String blockType: SanityCheckCommitDetector.blockSinkCallTypes){
                if (message.contains(blockType)){
                    isBlockKind=true;
                }
            }
            if (!isBlockKind){
                addSinkCalls(location,message);
//                System.out.println(location);
//                System.out.println(message);
//                sinkCalls.add(new SinkCall(new Message(message),location));
            }

        }
    }
    void addSinkCalls(String location,String message){
        if(SanityCheckCommitDetector.verbose>0){
            System.out.println(location);
            System.out.println(message);
        }

        sinkCalls.add(new SinkCall(new Message(message),location));

    }
    protected void processOtherVulTypes(Stmt stmt, Body b){
        // some vulnerability type is not included in the codeQl rules in yml files
        // for example: XSS.qll in codeQl repository
        InvokeExpr invokeExpr = stmt.getInvokeExpr();
        SootClass invokeMethodDeclaringClass = invokeExpr.getMethodRef().getDeclaringClass();
        String methodName = invokeExpr.getMethodRef().getName();
        StringBuilder callNameBuilder=new StringBuilder();

//        if (invokeMethodDeclaringClass.getName().contains("javax.servlet.jsp")){
//            if (methodName.startsWith("print") || methodName.equals("write") || methodName.equals("append") || methodName.equals("format")){
//                callNameBuilder.append(methodName);
//                callNameBuilder.append("(...)");
//
//                String location= b.getMethod().getDeclaringClass().getName().split("\\$")[0]+".java" +":"+stmt.getJavaSourceStartLineNumber()+":"+stmt.getJavaSourceStartColumnNumber()+":"+0;
//                String message="xss"+";"+callNameBuilder.toString()+";"+"Argument[0]";
//                addSinkCalls(location,message);
//            }
//        }

//        private class ObjectInputStreamReadObjectMethod extends Method {
//            ObjectInputStreamReadObjectMethod() {
//                this.getDeclaringType().getASourceSupertype*() instanceof TypeObjectInputStream and
//                (this.hasName("readObject") or this.hasName("readUnshared"))
//            }
//        }
        SootClass typeObjectInputStream=Scene.v().getSootClass("java.io.ObjectInputStream");
        Set<SootClass> subObjInputClasses=class2subClasses.computeIfAbsent(typeObjectInputStream,k->getAllSubSootClass(typeObjectInputStream));
        if (subObjInputClasses.contains(invokeMethodDeclaringClass)){

            if (methodName.equals("readObject") || methodName.equals("readUnshared") || methodName.equals("resolveClass") ||
            methodName.equals("readUTF") || methodName.endsWith("ReadObject")){
                callNameBuilder.append(methodName);
                callNameBuilder.append("(...)");
                String location= b.getMethod().getDeclaringClass().getName().split("\\$")[0]+".java" +":"+stmt.getJavaSourceStartLineNumber()+":"+stmt.getJavaSourceStartColumnNumber()+":"+0;

                String message="deserialization"+";"+callNameBuilder.toString()+";"+"Argument[this]";
                addSinkCalls(location,message);
            }
            if ( methodName.equals("resolveClass") ){
                callNameBuilder.append(methodName);
                callNameBuilder.append("(...)");
                String location= b.getMethod().getDeclaringClass().getName().split("\\$")[0]+".java" +":"+stmt.getJavaSourceStartLineNumber()+":"+stmt.getJavaSourceStartColumnNumber()+":"+0;

                String message="deserialization"+";"+callNameBuilder.toString()+";"+"Argument[0]";
                addSinkCalls(location,message);
            }
        }



//        private class SnakeYamlParse extends MethodAccess {
//            SnakeYamlParse() {
//                exists(Method m |
//                        m.getDeclaringType() instanceof Yaml and
//                        m.hasName(["compose", "composeAll", "load", "loadAll", "loadAs", "parse"]) and
//                        m = this.getMethod()
//    )
//            }
//        }
        // using contains because maven-shade-plugin
        if (invokeMethodDeclaringClass.getName().contains("org.yaml.snakeyaml.Yaml")){
            if (methodName.equals("compose") || methodName.equals("composeAll") || methodName.equals("parse") ||
                    methodName.equals("load") || methodName.equals("loadAll") || methodName.equals("loadAs")){
                callNameBuilder.append(methodName);
                callNameBuilder.append("(...)");

                String location= b.getMethod().getDeclaringClass().getName().split("\\$")[0]+".java" +":"+stmt.getJavaSourceStartLineNumber()+":"+stmt.getJavaSourceStartColumnNumber()+":"+0;
                String message="deserialization"+";"+callNameBuilder.toString()+";"+"Argument[0]";
                addSinkCalls(location,message);
                // sometimes patch change a Yaml to a safer Yaml
                // -       org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                // +       org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(new SafeConstructor());
                //        return Json.mapper().convertValue(yaml.load(contents), expectedType);
                message="deserialization"+";"+callNameBuilder.toString()+";"+"Argument[this]";
                addSinkCalls(location,message);
            }
        }
        if (invokeMethodDeclaringClass.getName().equals("java.lang.ClassLoader") || invokeMethodDeclaringClass.getName().equals("java.lang.Class")){
            if (methodName.equals("loadClass") || methodName.equals("forName")){
                callNameBuilder.append(methodName);
                callNameBuilder.append("(...)");

                String location= b.getMethod().getDeclaringClass().getName().split("\\$")[0]+".java" +":"+stmt.getJavaSourceStartLineNumber()+":"+stmt.getJavaSourceStartColumnNumber()+":"+0;
                String message="deserialization"+";"+callNameBuilder.toString()+";"+"Argument[0]";
                addSinkCalls(location,message);
            }
        }
        // using contains because maven-shade-plugin
        if (invokeMethodDeclaringClass.getName().contains("com.thoughtworks.xstream.XStream")){
            if (methodName.equals("fromXML") || methodName.equals("unmarshal")){
                callNameBuilder.append(methodName);
                callNameBuilder.append("(...)");
                String location= b.getMethod().getDeclaringClass().getName().split("\\$")[0]+".java" +":"+stmt.getJavaSourceStartLineNumber()+":"+stmt.getJavaSourceStartColumnNumber()+":"+0;
                String message="deserialization"+";"+callNameBuilder.toString()+";"+"Argument[0]";
                addSinkCalls(location,message);
                message="deserialization"+";"+callNameBuilder.toString()+";"+"Argument[this]";
                addSinkCalls(location,message);
            }
        }
        if (invokeMethodDeclaringClass.getName().equals("io.vertx.ext.sql.SQLConnection")){
            if (methodName.equals("update")){
                callNameBuilder.append(methodName);
                callNameBuilder.append("(...)");
                String location= b.getMethod().getDeclaringClass().getName().split("\\$")[0]+".java" +":"+stmt.getJavaSourceStartLineNumber()+":"+stmt.getJavaSourceStartColumnNumber()+":"+0;
                String message="sql-injection"+";"+callNameBuilder.toString()+";"+"Argument[0]";
                addSinkCalls(location,message);
            }
        }


    }
    protected void processBody(Body b) {
        for (Unit unit:b.getUnits()){
            Stmt stmt = (Stmt) unit;
            if (stmt.containsInvokeExpr()) {
                processYmlVul(stmt,b);
                processOtherVulTypes(stmt,b);
            }
        }
    }
    static public Set<SootClass> getAllSubSootClass(SootClass sootClass){
        Hierarchy hierarchy=Scene.v().getActiveHierarchy();
        Set<SootClass> result=new HashSet<>();
        Stack<SootClass> classToProcess = new Stack<>();
        classToProcess.push(sootClass);

        while(!classToProcess.isEmpty()){
            SootClass currentClass = classToProcess.pop();
            result.add(currentClass);
            try {
                currentClass.checkLevel(HIERARCHY);
            }catch (RuntimeException runtimeException){
                continue;
            }
            if (currentClass.isInterface()){
                classToProcess.addAll(hierarchy.getSubinterfacesOf(currentClass));
                classToProcess.addAll(hierarchy.getDirectImplementersOf(currentClass));
            } else {
                // did not add to stack
                result.addAll(hierarchy.getSubclassesOf(currentClass));
            }
        }
        return result;
    }
    // parse string"(Method,URI)" to list.of("Method", "URI")
    static private List<String> parseSignature(String signature){
        List<String> result=new ArrayList<>();
        // Remove the parentheses at the beginning and end
        signature = signature.substring(1, signature.length() - 1);

        // Split the remaining string by comma
        String[] parts = signature.split(",");

        for (String part : parts) {
            result.add(part.trim()); // Add each part to the result list
        }

        return result;
    }
    static private CodeQlSink matchesSinkModel(SootClass declaringClass, String methodName, List<Type> argTypes) {

        for (SootClass sinkClass: class2sinkRules.keySet()){
            Set<SootClass> subClasses=class2subClasses.computeIfAbsent(sinkClass,k->getAllSubSootClass(sinkClass));
            if (!subClasses.contains(declaringClass)){
                continue;
            }
            List<CodeQlSink> classCodeQlSinks= class2sinkRules.get(sinkClass);
            for (CodeQlSink codeQlSink:classCodeQlSinks){
                if (!methodName.equals(codeQlSink.methodName)){
                    continue;
                }
                if (codeQlSink.signature.equals("")) {
                    return codeQlSink;
                }

                // match types
                List<String> sinkTypes=parseSignature(codeQlSink.signature);
                if (sinkTypes.size()!= argTypes.size()){
                    continue;
                }
                int i;
                for (i=0;i<sinkTypes.size();i++){
                    if (!argTypes.get(i).toString().contains(sinkTypes.get(i))){
                        break;
                    }
                }
                //type matches
                if (i==sinkTypes.size())    return codeQlSink;
            }
            //if no method match
            break;
        }


        return null;

    }

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        for (SootClass sootClass:Scene.v().getApplicationClasses()){
            for (SootMethod sootMethod:sootClass.getMethods()){
                if (sootMethod.hasActiveBody()){
                    processBody(sootMethod.retrieveActiveBody());
                }

            }
        }
    }
}
