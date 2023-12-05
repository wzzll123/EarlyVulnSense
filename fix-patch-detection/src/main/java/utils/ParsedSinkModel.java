package utils;

import soot.SootMethod;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParsedSinkModel {
    public String formatString;
    public String packageName;
    public String className;
    public String methodName;
    public String vulnerability;
    public String argumentType;
    public String methodSignature;
    public SootMethod sootMethod;
    public ParsedSinkModel(String formatString){
        this.formatString=formatString;
        parseFormatString();
    }
    private void parseFormatString(){
        String regex = "^\\s*(.*?);(.*?);(.*?);(.*?);\\((.*?)\\);;(.*?);(.*?);(.*?)$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(formatString);
        if (matcher.matches()){
            packageName=matcher.group(1);
            className=matcher.group(2);
            methodName=matcher.group(4);
            vulnerability=matcher.group(7);
            argumentType=matcher.group(5);
            methodSignature="<"+packageName+"."+className+": "+methodName+"("+argumentType+")"+">";
        }
    }


}
