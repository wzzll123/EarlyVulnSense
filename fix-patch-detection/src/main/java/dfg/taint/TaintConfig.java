package dfg.taint;

import org.yaml.snakeyaml.Yaml;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootMethod;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;

public class TaintConfig {
    public Set<TaintTransfer> taintTransfers;
    public TaintConfig(String configPath){
        taintTransfers=readYml(configPath);
    }
    public static void main(String[] args) {
        // for test purpose
        new TaintConfig("src/main/resources/taint-config.yml");

    }
    private String covert2String(Object object){
        if (object instanceof String){
            return (String) object;
        }
        else {
            return Integer.toString((Integer) object);
        }
    }
    private Set<TaintTransfer> readYml(String configPath){
        Set<TaintTransfer> result=new HashSet<>();

        try {

            // Create a Yaml object
            Yaml yaml = new Yaml();

            // Load the data from the YAML file
            FileInputStream fis = new FileInputStream(configPath);
            Map<String, List<Map<String, Object>>> data = yaml.load(fis);

            // Process the data
            if (data != null && data.containsKey("transfers")) {
                List<Map<String, Object>> transfers = data.get("transfers");
                for (Map<String, Object> transfer : transfers) {
                    String methodSignature = (String) transfer.get("method");
                    String className=methodSignature.split(": ")[0].substring(1);
                    String subSignature=methodSignature.split(": ")[1];
                    subSignature=subSignature.substring(0,subSignature.length()-1);

                    String from=covert2String(transfer.get("from"));
                    String to = covert2String(transfer.get("to"));
                    try{
                        SootMethod sootMethod=Scene.v().getSootClass(className).getMethod(subSignature);

                        result.add(new TaintTransfer(sootMethod,TaintTransfer.toInt(from),TaintTransfer.toInt(to)));
                    }catch (RuntimeException e){
//                        e.printStackTrace();
                        continue;
                    }

//                    System.out.println("Method: " + method + ", From: " + from + ", To: " + to);
                }
            } else {
                System.out.println("No 'transfers' key found in the YAML file.");
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return result;
    }
    public Set<Integer> getArgsToResult(SootMethod sootMethod){
        Set<Integer> result=new HashSet<>();
        for(TaintTransfer taintTransfer:taintTransfers){
            if(taintTransfer.getMethod().equals(sootMethod) && taintTransfer.getFrom()>=0 && taintTransfer.getTo()==TaintTransfer.RESULT ){
                result.add(taintTransfer.getFrom());
            }
        }
        return result;
    }
    public Set<Integer> getArgsToBase(SootMethod sootMethod){
        Set<Integer> result=new HashSet<>();
        // if (sootMethod.isConstructor() && sootMethod.getDeclaringClass().getName().endsWith("InputStream") &&
        // sootMethod.getParameterCount()>=1){
        //     result.add(0);
        //     return result;
        // }
        for(TaintTransfer taintTransfer:taintTransfers){
            if(taintTransfer.getMethod().equals(sootMethod) && taintTransfer.getFrom()>=0 && taintTransfer.getTo()==TaintTransfer.BASE ){
                result.add(taintTransfer.getFrom());
            }
        }
        return result;
    }
    public boolean isBaseToResult(SootMethod sootMethod){
        for(TaintTransfer taintTransfer:taintTransfers){
            if(taintTransfer.getMethod().equals(sootMethod) && taintTransfer.getFrom()==TaintTransfer.BASE && taintTransfer.getTo()==TaintTransfer.RESULT ){
                return true;
            }
        }
//        if (sootMethod.toString().contains("get")){
//            return true;
//        }
        return false;
    }
}
