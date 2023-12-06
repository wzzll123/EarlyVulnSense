import json
import os
import sys
from pathlib import Path
import git
import requests
import subprocess
from time import time
  
def timer_func(func):
    # This function shows the execution time of 
    # the function object passed
    def wrap_func(*args, **kwargs):
        t1 = time()
        result = func(*args, **kwargs)
        t2 = time()
        print(f'Function {func.__name__!r} executed in {(t2-t1):.4f}s')
        return result
    return wrap_func
failed_jar_url=[]
def parse_exp_json(file_path):
    with open(file_path, 'r') as f:
        exp = json.load(f)
    return exp

def get_java_class_path():
    JAR_PATH = "target/fix-patch-detection-1.0-SNAPSHOT.jar"
    dependency_dir = "target/dependency"

    for filename in os.listdir(dependency_dir):
        if filename.endswith(".jar"):
            jar_path = os.path.join(dependency_dir, filename)
            JAR_PATH += ":" + jar_path
    return JAR_PATH
def ensure_directory_exists(directory_path):
    if not os.path.exists(directory_path):
        try:
            os.makedirs(directory_path)
            # print(f"Created directory: {directory_path}")
        except OSError as e:
            pass
            # print(f"Error creating directory '{directory_path}': {e}")
    else:
        pass
        # print(f"Directory '{directory_path}' already exists.")
def download_github_repo(github_repo_local_path,repo_url):
    if not os.path.exists(github_repo_local_path):
        try:
            git.Repo.clone_from(repo_url, github_repo_local_path)
            print(f"Repository cloned to {github_repo_local_path}")
        except git.GitCommandError as e:
            print(f"Error cloning repository: {e}")
def compile_release(release_path,jar_lists,exp_config):
    get_previous_commit_script="""
PROJECT={}
COMMIT={}
if [[ -z "$PROJECT" || -z "$COMMIT" ]]; then
    echo "Please enter in project and commit."
    exit 1
fi
pushd ../projects/$PROJECT
    source setup.sh
    bash download.sh $COMMIT
    bash build.sh $COMMIT
popd
"""
    
    ensure_directory_exists(release_path) 
    
    full_process_jar=""
    for jar_url in jar_lists:
        jar_path=os.path.join(release_path,jar_url)
        # print(jar_path)
        # compile
        if not os.path.exists(jar_path):
            print("jar path not exist"+jar_path)
            sys.exit()
            with open('tmp.sh', 'w') as file:
                file.write(get_previous_commit_script.format(exp_config['project'],exp_config['release_commit']))
            os.system("bash tmp.sh")
        full_process_jar+=(jar_path+":")
        
    return full_process_jar
@timer_func
def run_command(analysis_command:str,print_all=True):
    result = subprocess.run(' '.join(analysis_command), shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if result.returncode == 0:
        # print("Command output:")
        if print_all:
            print(result.stdout)
        elif  "The expr in" in result.stdout:
            print("detected dataflow")
    else:
        print("Command failed with error:")
        print(result.stderr)   

def do_exp_for_one(projects_root_path,exp_config,print_all=True,module='SanityCheckCommitDetector'):
    print("project",exp_config['project'],exp_config['patch_commit'])
    ensure_directory_exists(os.path.join(projects_root_path,exp_config['project']))
    github_repo_local_path=os.path.join(projects_root_path,exp_config['project'],exp_config['project'])
    # download_github_repo(github_repo_local_path,exp_config['url'])
    release_path=os.path.join(projects_root_path,exp_config['project'],exp_config['release_commit'])
    if 'dependency_url' in exp_config:
        ensure_directory_exists(release_path)
        for jar_url in exp_config['dependency_url']:
            jar_name=os.path.basename(jar_url)
            if os.path.exists(os.path.join(release_path,jar_name)):
                continue
            response = requests.get(jar_url)
            if response.status_code == 200:
                # Save the content of the response to the local file
                with open(os.path.join(release_path,jar_name), 'wb') as file:
                    file.write(response.content)
            else:
                print(f"Failed to download file {jar_url}. Status code: {response.status_code}")
    if 'dependencies' in exp_config:
        full_process_jar=compile_release(release_path,exp_config['jar_url']+exp_config['dependencies'],exp_config)
    else:
        full_process_jar=compile_release(release_path,exp_config['jar_url'],exp_config)
    # java -cp $JAR_PATH SanityCheckCommitDetector $process_jar ../projects/$PROJECT/$PROJECT $RELEASE_COMMIT $PATCH_COMMIT
    analysis_command=["java","-cp",get_java_class_path(),module,full_process_jar,github_repo_local_path,exp_config['release_commit'],exp_config['patch_commit']]
    # print(' '.join(analysis_command))
    run_command(analysis_command,print_all)
if __name__ == '__main__':
    exp_configs=parse_exp_json("experiment/compile_exp.json") 
    projects_root_path=Path("../projects/")
    if len(sys.argv) == 1:
        for i in range(len(exp_configs)):
            exp_config=exp_configs[i]
            do_exp_for_one(projects_root_path,exp_config,True)
    if len(sys.argv) == 2 and sys.argv[1] == 'permission':
        for i in range(len(exp_configs)):
            exp_config=exp_configs[i]
            if exp_config['project']!='opentsdb':
                continue
            do_exp_for_one(projects_root_path,exp_config,True,'BlockListCommitDetector')