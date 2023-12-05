COMMIT=$1
echo $PWD
if test -z "$COMMIT"; then
    echo "commit is empty."
    exit 1
fi
if ! test -d "$PROJECT"; then
    git clone https://github.com/narkisr/nmap4j
fi
if [[ ! -d $COMMIT/$PROJECT ]]; then
    mkdir $COMMIT
    # cp -r $PROJECT $COMMIT/$PROJECT-$COMMIT
    # pushd $COMMIT/$PROJECT-$COMMIT
    cp -r $PROJECT $COMMIT/$PROJECT
    pushd $COMMIT/$PROJECT
    git checkout $COMMIT
    popd
fi

