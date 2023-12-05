PROJECT_DIR=$1
if [[ -z "$PROJECT_DIR" ]]; then
    echo "Please enter in project."
    exit 1
fi

# Figure out script absolute path
pushd `dirname $0` > /dev/null
SCRIPT_DIR=`pwd`
popd > /dev/null

pushd $PROJECT_DIR > /dev/null
    git diff -U0 HEAD^ HEAD | $SCRIPT_DIR/showlinenum.awk show_header=0 path=1 | grep -e "\.java:[0-9]*:+" | cut -d+ -f1 | rev | cut -c2- | rev | awk -F'/' '{print $NF}'
popd > /dev/null