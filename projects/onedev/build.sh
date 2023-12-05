COMMIT=$1
if test -z "$COMMIT"; then
    echo "commit is empty."
    exit 1
fi
pushd $COMMIT/$PROJECT
    mvn package -DskipTests
    # rm -rf $PROJECT-$COMMIT-db
    # codeql database create $PROJECT-$COMMIT-db --language=java --source-root=. 
    # --command='mvn clean package -DskipTests'
popd

