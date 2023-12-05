package dfg.element;

import soot.jimple.spark.pag.SparkField;
import soot.util.Numberable;

/**
 * Represents a field reference node in the pointer assignment graph.
 *
 * @author Ondrej Lhotak
 */
public class FieldRefNode extends DataflowNode implements Numberable {
    protected VarNode base;
    protected SparkField field;

    public FieldRefNode(VarNode base, SparkField field) {
        super(field.getType());
        this.base = base;
        this.field = field;
        base.addField(this, field);
    }

    /**
     * Returns the base of this field reference.
     */
    public VarNode getBase() {
        return base;
    }

    /**
     * Returns the field of this field reference.
     */
    public SparkField getField() {
        return field;
    }

    public String toString() {
        return "FieldRefNode " + getNumber() + " " + base + "." + field;
    }
}
