package utils;

import soot.SootMethod;
import soot.Unit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ControlFlowResult {
    public Set<SootMethod> junctureMethods =new HashSet<>();
    public Map<SootMethod,Set<Unit>> method2junctureUnits=new HashMap<>();
    public Map<SootMethod, Set<SinkCall>> sootMethod2sinkCall=new HashMap<>();
    public Map<SootMethod, Set<SootMethod>> junctureMethod2SinkMethod=new HashMap<>();
    public Map<SootMethod, Set<Unit>> juncture2unitBehindPatchUnitInSameMethod=new HashMap<>();
    public Map<SootMethod, Set<SootMethod>> junctureMethod2PatchMethods=new HashMap<>();
    public Map<SootMethod, Set<Unit>> patchMethod2patchUnits=new HashMap<>();
}
