package utils;

import soot.ArrayType;
import soot.RefType;
import soot.SootClass;
import soot.Type;

public class DataflowUtils {
    public static boolean isUnresolved(Type type) {
        if (type instanceof ArrayType) {
            ArrayType at=(ArrayType) type;
            type = at.getArrayElementType();
        }
        if (!(type instanceof RefType))
            return false;
        RefType rt=(RefType) type;
        if (!rt.hasSootClass()) {
            return true;
        }
        SootClass cl = rt.getSootClass();
        return cl.resolvingLevel() < SootClass.HIERARCHY;
    }
}
