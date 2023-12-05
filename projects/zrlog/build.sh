COMMIT=$1
if test -z "$COMMIT"; then
    echo "commit is empty."
    exit 1
fi

# cp $PROJECT/pom.xml $COMMIT/$PROJECT-$COMMIT
# old_version="2.2.5-SNAPSHOT"
# new_version="2.0.6"
# file_path="$COMMIT/$PROJECT-$COMMIT/pom.xml"
# # Use sed to replace old_version with new_version in the file
# sed -i "s/$old_version/$new_version/g" "$file_path"

pushd $COMMIT/$PROJECT
    mvn package -pl common,data,service -am
    # rm -rf $PROJECT-$COMMIT-db
    # codeql database create $PROJECT-$COMMIT-db --language=java --source-root=.
popd

