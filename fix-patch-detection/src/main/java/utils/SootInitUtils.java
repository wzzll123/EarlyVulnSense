package utils;

import soot.*;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.options.Options;

import java.util.*;

import static soot.SootClass.SIGNATURES;

public class SootInitUtils {
    public static void excludeJDKLibrary()
    {
        List<String> excludeList=new ArrayList<>();
        excludeList.add("java.*");
        excludeList.add("javax.*");
        excludeList.add("sun.*");
        excludeList.add("sunw.*");
        excludeList.add("com.sun.*");
        excludeList.add("com.ibm.*");
        excludeList.add("com.apple.*");
        excludeList.add("apple.awt.*");
        //exclude jdk classes
        Options.v().set_exclude(excludeList);
        //this option must be disabled for a sound call graph
        Options.v().set_no_bodies_for_excluded(true);
    }
    public static void setBasicSetting(List<String> jarFiles){
        if (!jarFiles.isEmpty()){
            Options.v().set_process_dir(jarFiles);
        }

        Options.v().set_prepend_classpath(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().set_keep_line_number(true);
        Options.v().set_no_writeout_body_releasing(true);
    }
    public static void sootInit(List<String> jarFiles){
        setBasicSetting(jarFiles);
        excludeJDKLibrary();
        Scene.v().loadNecessaryClasses();
    }
    public static void sootInitWithoutExclude(List<String> jarFiles){
        setBasicSetting(jarFiles);
        Scene.v().loadNecessaryClasses();
    }
    private static void setAllEntryPoints(List<SootMethod> entryPoints){
        for (SootClass sootClass:Scene.v().getApplicationClasses()){
            for (SootMethod sootMethod:sootClass.getMethods()){
//                System.out.println(sootMethod);
                if (sootMethod.isConcrete() && (sootMethod.isPublic() || sootMethod.isProtected()) && sootMethod.getDeclaringClass().isPublic()){
                    entryPoints.add(sootMethod);
                }
            }
        }
    }
    private static boolean includedPackage(String packageName,Set<String> packagesInPatch){
        // packagesInPatch = {"org.apache.camel","org.apache.camel.component.snakeyaml"}
        // org.apache.camel.component.snakeyaml.springboot returns true
        // org.apache.camel.* return false
        if (packagesInPatch.contains(packageName)){
            return true;
        }
        for (String packageInPatch:packagesInPatch){
            if (packageInPatch.split(".").length<=3){
                continue;
            }
            if (packageName.contains(packageInPatch)){
                return true;
            }
        }
        return false;
    }
    private static void setRelevantEntryPoints(List<SootMethod> entryPoints,List<String> patchLines){
        Set<String> packageInPatch=new HashSet<>();
        Set<String> excludePackage=new HashSet<>();
        List<SootClass> processClasses=new ArrayList<>();
        for (SootClass sootClass:Scene.v().getApplicationClasses()){
            for (String patchLine:patchLines){
                if (patchLine.contains(sootClass.getShortName().split("\\$")[0])){
                    packageInPatch.add(sootClass.getPackageName());
                }
            }
        }
        for (SootClass sootClass:Scene.v().getApplicationClasses()){
            if (!includedPackage(sootClass.getPackageName(),packageInPatch)){
                excludePackage.add(sootClass.getPackageName()+".*");
            }else {
                processClasses.add(sootClass);
            }
        }
        List<String> excludeList=new ArrayList<>();
        excludeList.add("java.*");
        excludeList.add("javax.*");
        excludeList.add("sun.*");
        excludeList.add("sunw.*");
        excludeList.add("com.sun.*");
        excludeList.add("com.ibm.*");
        excludeList.add("com.apple.*");
        excludeList.add("apple.awt.*");
        excludeList.addAll(excludePackage);
        Options.v().set_exclude(excludeList);

        //todo: first scan sink and include methods that contains sink calls

        for (SootClass sootClass:processClasses){
            for (SootMethod sootMethod:sootClass.getMethods()){
//                System.out.println(sootMethod);
                if (sootMethod.isConcrete()
                        && (sootMethod.isPublic() || sootMethod.isProtected()) && sootMethod.getDeclaringClass().isPublic()){
                    entryPoints.add(sootMethod);
                }
            }
        }
    }
    public static void sootInitWithCHA(List<String> jarFiles, List<String> patchLines){
        sootInit(jarFiles);
        List<SootMethod> entryPoints=new ArrayList<>();
        setAllEntryPoints(entryPoints);
//        if (Scene.v().getApplicationClasses().size()<=9000){
//            setAllEntryPoints(entryPoints);
//        } else {
//            // prevent out of memory
//            setRelevantEntryPoints(entryPoints,patchLines);
//        }

        Scene.v().setEntryPoints(entryPoints);
        CHATransformer.v().transform();
    }
    public static void sootInitWithRTA(List<String> jarFiles){
        sootInit(jarFiles);
        List<SootMethod> entryPoints=new ArrayList<>();
        for (SootClass sootClass:Scene.v().getApplicationClasses()){
            for (SootMethod sootMethod:sootClass.getMethods()){
//                System.out.println(sootMethod);
                if (sootMethod.isConcrete() && sootMethod.isPublic()){
                    entryPoints.add(sootMethod);
                }
            }
        }
        Scene.v().setEntryPoints(entryPoints);
        Transform sparkConfig = new Transform("cg.spark", null);
        PhaseOptions.v().setPhaseOption(sparkConfig, "enabled:true");
        PhaseOptions.v().setPhaseOption(sparkConfig, "rta:true");
        PhaseOptions.v().setPhaseOption(sparkConfig, "on-fly-cg:false");
        Map phaseOptions = PhaseOptions.v().getPhaseOptions(sparkConfig);
        SparkTransformer.v().transform(sparkConfig.getPhaseName(), phaseOptions);
    }
}
