COMMIT=$1
if test -z "$COMMIT"; then
    echo "commit is empty."
    exit 1
fi
# pushd $COMMIT/$PROJECT-$COMMIT

#     # remove the comment in ql/java/ql/lib/ext/java.lang.model.yml
#     codeql database analyze $PROJECT-$COMMIT-db --format=sarif-latest --output=../$COMMIT-sinks.sarif $CODEQL_DIR/custom/AllSinkWithoutLog.ql
# popd
pushd $COMMIT/$PROJECT
    codeql database analyze $PROJECT-$COMMIT-db --format=csv --output=../$PROJECT-$COMMIT.csv $CODEQL_DIR/codeql-suites/injection-custom.qls
popd