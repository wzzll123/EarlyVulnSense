COMMIT=$1
if test -z "$COMMIT"; then
    echo "commit is empty."
    exit 1
fi
if ! test -d "$PROJECT"; then
    git clone https://github.com/dhis2/dhis2-core.git
fi
if ! test -d $COMMIT; then
    mkdir $COMMIT
    cp -r $PROJECT $COMMIT/$PROJECT-$COMMIT
    pushd $COMMIT/$PROJECT-$COMMIT
    git checkout $COMMIT
    popd
fi

