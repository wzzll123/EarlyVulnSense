COMMIT=$1
if test -z "$COMMIT"; then
    echo "commit is empty."
    exit 1
fi

pushd $COMMIT/$PROJECT/build
    mvn package -DskipTests
popd

