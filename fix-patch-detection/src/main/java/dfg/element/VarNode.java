package dfg.element;

import soot.AnySubType;
import soot.RefLikeType;
import soot.Type;
import soot.jimple.spark.pag.SparkField;
import utils.DataFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public abstract class VarNode extends ValNode{
    protected Object variable;
    protected Map<SparkField, FieldRefNode> fields;

    protected boolean interProcTarget = false;
    protected boolean interProcSource = false;

    protected VarNode(Object variable, Type t) {
        super(t);
//        if (!(t instanceof RefLikeType) || t instanceof AnySubType) {
//            throw new RuntimeException("Attempt to create VarNode of type " + t);
//        }
        this.variable = variable;
    }
    public Collection<FieldRefNode> getAllFieldRefs() {
        if (fields == null) {
            return Collections.emptyList();
        }
        return fields.values();
    }
    /**
     * Registers a frn as having this node as its base.
     */
    void addField(FieldRefNode frn, SparkField field) {
        if (fields == null) {
            synchronized (this) {
                if (fields == null) {
                    fields = DataFactory.createMap();
                }
            }
        }
        fields.put(field, frn);
    }
    public Object getVariable() {
        return variable;
    }
    /**
     * Returns the field ref node having this node as its base, and field as its field; null if nonexistent.
     */
    public FieldRefNode dot(SparkField field) {
        return fields == null ? null : fields.get(field);
    }
    public abstract VarNode base();
}
