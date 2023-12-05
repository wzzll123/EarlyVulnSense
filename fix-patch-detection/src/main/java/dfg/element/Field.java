package dfg.element;

import soot.SootField;
import soot.Type;
import soot.jimple.spark.pag.SparkField;

import java.util.Objects;

public class Field implements SparkField {
    private final SootField field;

    public Field(SootField sf) {
        this.field = sf;
    }

    @Override
    public int getNumber() {
        return field.getNumber();
    }

    @Override
    public void setNumber(int number) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Type getType() {
        return field.getType();
    }

    public SootField getField() {
        return field;
    }

    @Override
    public String toString() {
        return "FieldNode " + getNumber() + " " + field;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Field field1 = (Field) o;
        return field.equals(field1.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field);
    }
}
