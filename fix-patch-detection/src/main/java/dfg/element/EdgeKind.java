package dfg.element;

public enum EdgeKind {
    LOCAL_ASSIGN,
    CAST,

    INSTANCE_LOAD,
    INSTANCE_STORE,

    ARRAY_LOAD,
    ARRAY_STORE,

    STATIC_LOAD,
    STATIC_STORE,

    THIS_PASSING,
    PARAMETER_PASSING,
    RETURN,
    TRANSFER,

    OTHER,
}
