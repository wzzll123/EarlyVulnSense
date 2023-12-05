import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fj.P;
import soot.*;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.tagkit.LineNumberTag;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.BriefBlockGraph;
import soot.util.dot.DotGraph;
import utils.ParsedSinkModel;
import utils.SecurityEntry;
import utils.SinkModel;

@Deprecated
public class FixPatchDetection {
    static LinkedList<String> excludeList;
    static List<SecurityEntry> securityEntries;
    static List<SinkModel> sinkModels;
    static List<ParsedSinkModel> parsedSinkModels=new ArrayList<>();
    public static void main(String[] args) throws IOException {
//        /Users/wzz/Desktop/Research/fuzz/codeql-diff/projects/zt-zip/759b72f/zt-zip-759b72f/target/zt-zip-1.13-SNAPSHOT.jar
//        /Users/wzz/Desktop/Research/fuzz/codeql-diff/projects/zt-zip/759b72f/report/759b72f.sarif
//        patch.diff
        List<String> jarFiles= Arrays.asList(args[0].split(":"));
        String sarifPath = args[1];
        readSarif(sarifPath);

        for (SinkModel sinkModel:sinkModels){
            parsedSinkModels.add(new ParsedSinkModel(sinkModel.getMessage().getText()));
        }

        String projectPath = args[2];
        List<String> patchLines=readTarget(projectPath);


        // Set basic settings for the call graph generation
        Options.v().set_process_dir(jarFiles);
        Options.v().set_prepend_classpath(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().set_keep_line_number(true);
        Options.v().set_no_writeout_body_releasing(true);

//        String mainClass="org.zeroturnaround.zip.ZipUtil";
//        SootClass appClass = Scene.v().loadClassAndSupport(mainClass);
//        appClass.setApplicationClass();

        excludeJDKLibrary();
        Scene.v().loadNecessaryClasses();

        List<SootMethod> entryPoints=new ArrayList<>();
        for (ParsedSinkModel parsedSinkModel:parsedSinkModels){
            SootClass sootClass=Scene.v().getSootClass(parsedSinkModel.packageName+"."+parsedSinkModel.className);
            SootMethod sootMethod=getSootMethodByCodeqlSig(parsedSinkModel);
//            SootMethod sootMethod=getSootMethodByCodeqlSig(sootClass,parsedSinkModel.methodName,parsedSinkModel.argumentType);
            if (sootMethod!=null){
                entryPoints.add(sootMethod);
            }
        }
        Scene.v().setEntryPoints(entryPoints);
//        for (SootClass sootClass:Scene.v().getApplicationClasses()){
//            System.out.println(sootClass.toString().split("\\$")[0]);
//        }

        enableSparkCallGraph();

        InformationFlowDetector informationFlowDetector =
                new InformationFlowDetector(
                        securityEntries,
                        parsedSinkModels,
                        patchLines);
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.informationFlow", informationFlowDetector));
        // Start the generation
        PackManager.v().runPacks();


    }
    //TODO: maybe have multiple soot methods
    private static SootMethod getSootMethodByCodeqlSig(ParsedSinkModel parsedSinkModel){
        SootClass sootClass=Scene.v().getSootClass(parsedSinkModel.packageName+"."+parsedSinkModel.className);
        String methodName=parsedSinkModel.methodName;
        String arguments=parsedSinkModel.argumentType;
        SootMethod result=null;
        for (SootMethod sootMethod: sootClass.getMethods()){
            if (!sootMethod.getName().equals(methodName))   continue;
            String[] argumentList=arguments.split(",");
            if (argumentList.length!=sootMethod.getParameterCount())    continue;
            int i;
            for (i=0;i<argumentList.length;i++){
                soot.Type type = sootMethod.getParameterType(i);
                if (!type.toString().contains(argumentList[i])) break;
            }
            if (i>=argumentList.length){
                if (result!=null){
                    throw new RuntimeException("get multiple methods by "+sootClass.getName()+" "+methodName+arguments);
                }
                result=sootMethod;
                parsedSinkModel.sootMethod=result;
            }
        }
        return result;
//        if (result==null){
//            throw new RuntimeException("soot method not found by "+sootClass.getName()+methodName+arguments);
//        }
//        return result;
    }
    public static void enableSparkCallGraph() {

        //Enable Spark
        HashMap<String,String> opt = new HashMap<>();
        opt.put("on-fly-cg","true");
        opt.put("enabled","true");


//        Transform sparkConfig = new Transform("cg.spark", null);
//        PhaseOptions.v().setPhaseOption(sparkConfig, "enabled:true");
//        PhaseOptions.v().setPhaseOption(sparkConfig, "rta:true");
//        PhaseOptions.v().setPhaseOption(sparkConfig, "on-fly-cg:false");
//        Map phaseOptions = PhaseOptions.v().getPhaseOptions(sparkConfig);
        SparkTransformer.v().transform("",opt);
    }
    private static void excludeJDKLibrary()
    {
        //exclude jdk classes
        Options.v().set_exclude(excludeList());
        //this option must be disabled for a sound call graph
        Options.v().set_no_bodies_for_excluded(true);
    }
    private static LinkedList<String> excludeList()
    {
        if(excludeList==null)
        {
            excludeList = new LinkedList<String> ();

            excludeList.add("java.*");
            excludeList.add("javax.*");
            excludeList.add("sun.*");
            excludeList.add("sunw.*");
            excludeList.add("com.sun.*");
            excludeList.add("com.ibm.*");
            excludeList.add("com.apple.*");
            excludeList.add("apple.awt.*");
        }
        return excludeList;
    }
    static private void readSarif(String reportPath) {

        try {
            ProcessBuilder pb = new ProcessBuilder("python", "scripts/parse_sarif.py",reportPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Gson gson = new Gson();
                Type listType = new TypeToken<List<SecurityEntry>>(){}.getType();
                securityEntries = gson.fromJson(line, listType);
            }

            pb = new ProcessBuilder("python", "scripts/get_sink_model.py",reportPath);
            pb.redirectErrorStream(true);
            process = pb.start();

            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((line = reader.readLine()) != null) {
                Gson gson = new Gson();
                Type listType = new TypeToken<List<SinkModel>>(){}.getType();
                sinkModels = gson.fromJson(line, listType);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static List<String> readTarget(String projectPath) throws IOException {
        List<String> patchLines=new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "scripts/get_patch.sh",projectPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line, ruleId, srcLocation, sinkLocation;
            while ((line = reader.readLine()) != null) {
                patchLines.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

//        try (BufferedReader input = new BufferedReader(new FileReader(patchPath))) {
//            patchLines = input.lines().collect(Collectors.toList());
//        }
        return patchLines;
    }
}
@Deprecated
class InformationFlowDetector extends SceneTransformer{
    private List<SecurityEntry> securityEntries;
    private List<String> patchLines;
    private List<ParsedSinkModel> parsedSinkModels;
    public InformationFlowDetector(
            List<SecurityEntry> securityEntries,
            List<ParsedSinkModel> parsedSinkModels,
            List<String> patchLines

    ){
        this.securityEntries=securityEntries;
        this.parsedSinkModels=parsedSinkModels;
        this.patchLines=patchLines;
    }
    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        int numOfEdges =0;
        CallGraph callGraph = Scene.v().getCallGraph();

        Set<SootMethod> patchMethods=new HashSet<>();
        for(SootClass sc : Scene.v().getApplicationClasses()){
//                System.out.println(sc);
            for(SootMethod m : sc.getMethods()){
                Boolean isPatchMethod=false;
                if (!m.hasActiveBody())
                    continue;
                Body methodBody=m.retrieveActiveBody();
                BlockGraph blockGraph = new BriefBlockGraph(methodBody);
                for(Block block:blockGraph){
                    for (Unit unit:block){
                        if (!unit.hasTag("LineNumberTag")){
                            continue;
                        }
                        int lineNumber=unit.getJavaSourceStartLineNumber();
//                            System.out.println(sc.getShortJavaStyleName().split("\\$")[0]+".java:"+lineNumber);
                        for(String patchLine:patchLines){
                            if(patchLine.equals(sc.getShortJavaStyleName().split("\\$")[0]+".java:"+lineNumber)){
                                isPatchMethod=true;
//                                for (ValueBox valueBox:unit.getUseBoxes())
//                                    System.out.println(valueBox.getValue());
                            }
                        }
                    }
                }
                if (isPatchMethod)  patchMethods.add(m);
            }
        }
        String formattedOutput="The patch method %s is connected with sink method %s, corresponding vulneribility is %s";
        for (SootMethod patchMethod:patchMethods){
            if (!Scene.v().getReachableMethods().contains(patchMethod)) continue;
            for (ParsedSinkModel parsedSinkModel:parsedSinkModels){
                if (parsedSinkModel.sootMethod==null){
//                    System.out.println(parsedSinkModel.className+parsedSinkModel.methodName);
                    continue;
                }
                if (isConnected(callGraph,parsedSinkModel.sootMethod,patchMethod)){
                    System.out.println(String.format(formattedOutput,patchMethod.toString(),parsedSinkModel.sootMethod.toString(),parsedSinkModel.vulnerability));
                }
            }
        }

    }
    private boolean isConnected(CallGraph callGraph, SootMethod method1, SootMethod method2){
        if (method1.equals(method2)){
            return true;
        }
        // Perform a depth-first search to find method2 starting from method1
        Set<SootMethod> visitedMethods = new HashSet<>();
        return depthFirstSearch(callGraph, method1, method2, visitedMethods);
    }
    private static boolean depthFirstSearch(CallGraph callGraph, SootMethod currentMethod, SootMethod targetMethod, Set<SootMethod> visitedMethods) {
        // Base case: If the current method is the target method, return true
        if (currentMethod.equals(targetMethod)) {
            return true;
        }

        // Add the current method to the set of visited methods
        visitedMethods.add(currentMethod);
//        System.out.println(currentMethod+", "+targetMethod);

        // Get the callers of the current method
        Iterator<Edge> callers = callGraph.edgesOutOf(currentMethod);

        // Traverse the callers recursively
        while (callers.hasNext()) {
            Edge edge = callers.next();
            SootMethod callerMethod = edge.getTgt().method();

            // Check if the caller method has already been visited
            if (!visitedMethods.contains(callerMethod)) {
                // Perform DFS on the caller method
                if (depthFirstSearch(callGraph, callerMethod, targetMethod, visitedMethods)) {
                    return true;
                }
            }
        }

        // Method2 not found in any callers, return false
        return false;
    }
}
