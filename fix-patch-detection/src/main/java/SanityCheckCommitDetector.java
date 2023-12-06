import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dfg.DataflowCreator;
import dfg.DataflowGraph;
import dfg.element.NodeManager;
import soot.*;
import soot.jimple.spark.SparkTransformer;
import utils.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class SanityCheckCommitDetector {
    // 0: only print dataflow result
    // 1: also print sink and control flow
    public static int verbose=0;
    static String[] blockSinkCallTypes={"log-injection","file-content-store","logging","regex-use","path-injection","response-splitting",
    "trust-boundary-violation"};
    // static String[] blockSinkCallTypes={"log-injection","file-content-store","logging","regex-use","response-splitting",
    // "trust-boundary-violation"};
    static DataflowGraph dataflowGraph=new DataflowGraph(new NodeManager());
    static List<SinkCall> sinkCalls=new ArrayList<>();
    static ControlFlowResult controlFlowResult=new ControlFlowResult();
    static DataFlowResult dataFlowResult=new DataFlowResult();
    public static void main(String[] args) throws IOException {
//        /Users/wzz/Desktop/Research/fuzz/codeql-diff/projects/zt-zip/759b72f/zt-zip-759b72f/target/zt-zip-1.13-SNAPSHOT.jar
//        /Users/wzz/Desktop/Research/fuzz/codeql-diff/projects/zt-zip/759b72f/report/759b72f-sink.sarif
//        /Users/wzz/Desktop/Research/fuzz/codeql-diff/projects/zt-zip/759b72f/zt-zip-759b72f
        List<String> jarFiles= Arrays.asList(args[0].split(":"));
        List<String> patchLines = null;
        if (args.length==3){
            String sarifPath = args[1];
            readSinkSarif(sarifPath);
            filterSinkCall();
            String projectPath = args[2];
            patchLines=readTarget(projectPath);

        }else if (args.length==4){
            // jar_path project_dir release_commit patch_commit
            patchLines=readPatchLineFromRelease(args[1],args[2],args[3]);

        } else if (args.length==2) {
            // jar_path project_dir
            patchLines=readTarget(args[1]);
        } else {
            throw new IllegalArgumentException("wrong arguments");
        }


        SootInitUtils.sootInitWithCHA(jarFiles,patchLines);
//        SootInitUtils.sootInitWithRTA(jarFiles);
        if (args.length==4 || args.length==2){
            ScanSink scanSink= new ScanSink(sinkCalls);
            ScanSink.getSinkCallFromCodeQlRules();
            PackManager.v().getPack("wjtp").add(new Transform("wjtp.scanSink", scanSink));
        }
        ControlFlowFeature controlFlowFeature = new ControlFlowFeature(sinkCalls,
                patchLines,controlFlowResult);
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.informationFlow", controlFlowFeature));
        DataflowCreator dataflowCreator=new DataflowCreator(controlFlowResult.junctureMethods,dataflowGraph);
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.dataflow", dataflowCreator));
        DataflowFeature dataflowFeature=new DataflowFeature(dataflowGraph,controlFlowResult,dataFlowResult);
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.dataflowFeature", dataflowFeature));
        // Start the generation
        PackManager.v().runPacks();
    }
    public static void filterSinkCall(){
        for (int i=sinkCalls.size()-1;i>-0;i--){
            SinkCall sinkCall=sinkCalls.get(i);
            for (String blockType:blockSinkCallTypes){
                if (sinkCall.getMessage().getText().contains(blockType)){
                    sinkCalls.remove(i);
                }
            }
        }
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



    static private void readSinkSarif(String reportPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "scripts/get_sink_call.py",reportPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Gson gson = new Gson();
                java.lang.reflect.Type listType = new TypeToken<List<SinkCall>>(){}.getType();
                sinkCalls = gson.fromJson(line, listType);
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
//    static private void readSarif(String reportPath) {

//        try {
//            ProcessBuilder pb = new ProcessBuilder("python", "scripts/parse_sarif.py",reportPath);
//            pb.redirectErrorStream(true);
//            Process process = pb.start();
//
//            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//            String line;
//            while ((line = reader.readLine()) != null) {
//                Gson gson = new Gson();
//                Type listType = new TypeToken<List<SecurityEntry>>(){}.getType();
//                securityEntries = gson.fromJson(line, listType);
//            }
//
//            pb = new ProcessBuilder("python", "scripts/get_sink_model.py",reportPath);
//            pb.redirectErrorStream(true);
//            process = pb.start();
//
//            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//            while ((line = reader.readLine()) != null) {
//                Gson gson = new Gson();
//                Type listType = new TypeToken<List<SinkModel>>(){}.getType();
//                sinkModels = gson.fromJson(line, listType);
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
    private static List<String> readPatchLineFromRelease(String projectPath, String releaseCommit, String patchCommit){
        List<String> patchLines=new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "scripts/read_patch_line_from_release.py",projectPath,releaseCommit,patchCommit);
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
        if (patchLines.isEmpty()){
            throw new RuntimeException("No patchlines in the commit");
        }
        return patchLines;
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
        if (patchLines.isEmpty()){
            throw new RuntimeException("No patchlines in the commit");
        }

//        try (BufferedReader input = new BufferedReader(new FileReader(patchPath))) {
//            patchLines = input.lines().collect(Collectors.toList());
//        }
        return patchLines;
    }
}

