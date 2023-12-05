COMMIT=$1
if test -z "$COMMIT"; then
    echo "commit is empty."
    exit 1
fi
pushd $COMMIT/$PROJECT-$COMMIT
    # codeql database run-queries $PROJECT-$COMMIT-db $CODEQL_DIR/AllSink.ql
    # codeql database analyze $PROJECT-$COMMIT-db --format=csv --output=../$COMMIT.csv $CODEQL_DIR/custom/AllSink.ql

    codeql database analyze h2/$PROJECT-$COMMIT-db --format=sarif-latest --output=../$COMMIT-sinks.sarif $CODEQL_DIR/custom/AllSinkWithoutLog.ql
    # codeql database analyze $PROJECT-$COMMIT-db --format=sarif-latest --output=../$COMMIT.sarif $CODEQL_DIR/codeql-suites/java-security-experimental.qls
popd