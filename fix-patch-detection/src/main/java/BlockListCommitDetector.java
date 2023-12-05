import blockListPack.ConditionGather;
import blockListPack.ControlDependency;
import blockListPack.PatchConfirm;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dfg.DataflowCreator;
import dfg.DataflowGraph;
import dfg.element.NodeManager;
import soot.PackManager;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import utils.ControlFlowResult;
import utils.DataFlowResult;
import utils.SinkCall;
import utils.SootInitUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
// This Detector determine whether there exists a sink are control dependent on the condition that uses blacklist variable
// todo: black list variable may be used in a condition that control the reture value of a boolean function, like isWhitelisted
// todo: for example: https://github.com/qos-ch/logback/commit/f46044b
public class BlockListCommitDetector {
    static String[] blockSinkCallTypes={"log-injection","file-content-store","logging","regex-use","path-injection"};
    static Map<SootMethod, Set<Unit>> patchMethod2addPatchUnit=new HashMap<>();
    static Boolean isPossibleBlockListOperation=false;
    static DataflowGraph dataflowGraph=new DataflowGraph(new NodeManager());
    static public List<SinkCall> sinkCalls=new ArrayList<>();
    static Map<SootMethod, Set<Unit>> conditionUnits=new HashMap<>();
    static ControlFlowResult controlFlowResult=new ControlFlowResult();
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
        return patchLines;
    }
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

        if (args.length==4 || args.length==2){
            ScanSink scanSink= new ScanSink(sinkCalls);
            ScanSink.getSinkCallFromCodeQlRules();
            PackManager.v().getPack("wjtp").add(new Transform("wjtp.scanSink", scanSink));
        }
        Set<SootMethod> initMethods=new HashSet<>();
        PatchConfirm patchConfirm=new PatchConfirm(patchLines,patchMethod2addPatchUnit,initMethods);
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.patchConfirm", patchConfirm));
//        ControlFlowFeature controlFlowFeature = new ControlFlowFeature(sinkCalls,
//                patchLines,controlFlowResult);
//        PackManager.v().getPack("wjtp").add(new Transform("wjtp.informationFlow", controlFlowFeature));
        DataflowCreator dataflowCreator=new DataflowCreator(initMethods,dataflowGraph);
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.dataflow", dataflowCreator));
        ConditionGather conditionGather=new ConditionGather(patchMethod2addPatchUnit,dataflowGraph,conditionUnits);
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.conditionGather", conditionGather));
        ControlDependency controlDependency=new ControlDependency(conditionUnits,sinkCalls);
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.controlDependency", controlDependency));
//        DataflowFeature dataflowFeature=new DataflowFeature(dataflowGraph,controlFlowResult,dataFlowResult);
//        PackManager.v().getPack("wjtp").add(new Transform("wjtp.dataflowFeature", dataflowFeature));
        // Start the generation
        PackManager.v().runPacks();
    }
}
