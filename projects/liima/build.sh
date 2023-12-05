COMMIT=$1
if test -z "$COMMIT"; then
    echo "commit is empty."
    exit 1
fi
# sudo npm install -g @angular/cli
pushd $COMMIT/$PROJECT-$COMMIT
    rm -rf $PROJECT-$COMMIT-db
    codeql database create $PROJECT-$COMMIT-db --language=java --source-root=. --command="mvn clean package -pl AMW_business -am"
popd

