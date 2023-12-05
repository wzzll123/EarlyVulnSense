COMMIT=$1
if test -z "$COMMIT"; then
    echo "commit is empty."
    exit 1
fi
if ! test -d "$PROJECT"; then
    git clone https://github.com/spring-projects/spring-data-jpa.git
fi
if ! test -d $COMMIT; then
    mkdir $COMMIT
    cp -r $PROJECT $COMMIT/$PROJECT
    pushd $COMMIT/$PROJECT
    git checkout $COMMIT
    popd
fi

