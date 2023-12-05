import soot.*;
import soot.jimple.spark.builder.ContextInsensitiveBuilder;
import soot.jimple.spark.pag.PAG;
import soot.options.Options;
import soot.options.SparkOptions;
import soot.util.dot.DotGraph;

import java.util.*;

@Deprecated
public class PAGGenerator extends SceneTransformer {
    static PAG pag;
    static LinkedList<String> excludeList;
    public static void main(String[] args){
        List<String> jarFiles= Arrays.asList(args[0].split(":"));
        // Set basic settings for the call graph generation
        Options.v().set_process_dir(jarFiles);
        Options.v().set_prepend_classpath(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().set_keep_line_number(true);
//        Options.v().set_no_writeout_body_releasing(true);

        excludeJDKLibrary();
        Scene.v().loadNecessaryClasses();

        List<SootMethod> entryPoints=new ArrayList<>();
//        SootClass MainClass=Scene.v().getSootClass("Main");
        entryPoints.add(Scene.v().getSootClass("Main").getMethodByName("main"));
        Scene.v().setEntryPoints(entryPoints);

        PAGGenerator pagGenerator =
                new PAGGenerator();
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.pagGenerator", pagGenerator));
        // Start the generation
        PackManager.v().runPacks();
    }
    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        SparkOptions opts = new SparkOptions(options);
        ContextInsensitiveBuilder b = new ContextInsensitiveBuilder();
        pag = b.setup(opts);
        b.build();
        System.out.println("VarNodes: " + pag.getVarNodeNumberer().size());
        System.out.println("FieldRefNodes: " + pag.getFieldRefNodeNumberer().size());
        System.out.println("AllocNodes: " + pag.getAllocNodeNumberer().size());
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
}
