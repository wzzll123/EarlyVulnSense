package utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CodeQlSink {
    public String packageName;
    public String typeName;
    public boolean subtypes;
    public String methodName;
    public String signature;
    public String ext;

    public String input;
    public String kind;
    public String provenance;
    public static List<CodeQlSink> read(){
        List<CodeQlSink> result = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "scripts/read_codeql_rules.py");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Gson gson = new Gson();
                Type listType = new TypeToken<List<CodeQlSink>>(){}.getType();
                result = gson.fromJson(line, listType);
            }
        }catch (Exception e) {
            e.printStackTrace();
        }

//        for (CodeQlSink sink:result){
//            System.out.println("Package: " + sink.packageName);
//            System.out.println("Type: " + sink.typeName);
//            System.out.println("Has Subtypes: " + sink.subtypes);
//            System.out.println("Method Name: " + sink.methodName);
//            System.out.println("Method Signature: " + sink.signature);
//            System.out.println("Input: " + sink.input);
//            System.out.println("Kind: " + sink.kind);
//            System.out.println("-------------------------");
//        }

        return result;
    }
    public static void main(String[] args){
        CodeQlSink.read();
    }

}
