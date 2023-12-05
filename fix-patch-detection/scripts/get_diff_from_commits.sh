PROJECT_DIR=$1
COMMIT_PRE=$2
COMMIT=$3
if [[ -z "$PROJECT_DIR" || -z "$COMMIT_PRE" || -z "$COMMIT" ]]; then
    echo "Please enter in project and two commits."
    exit 1
fi

# Figure out script absolute path
pushd `dirname $0` > /dev/null
SCRIPT_DIR=`pwd`
popd > /dev/null

pushd $PROJECT_DIR > /dev/null
    git diff -U0 $COMMIT_PRE $COMMIT | $SCRIPT_DIR/showlinenum.awk show_header=0 path=1
popd > /dev/null