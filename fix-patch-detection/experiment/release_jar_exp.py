import json
import os
import sys
from pathlib import Path
import git
import requests
import subprocess
import zipfile
from compile_jar_exp import timer_func
from diff_utils import get_commit_diff_output
failed_jar_url=[]
def parse_exp_json(file_path):
    with open(file_path, 'r') as f:
        exp = json.load(f)
    return exp
def is_class_in_jar(jar_file_path, class_name):
    try:
        with zipfile.ZipFile(jar_file_path, 'r') as jar:
            class_name_list=[get_class_name_from_path(class_name_path) for class_name_path in jar.namelist()]
            # print(class_name_list)
            return class_name in class_name_list
    except FileNotFoundError:
        # print(f"JAR file '{jar_file_path}' not found.")
        return False
    except Exception as e:
        # print(f"An error occurred: {str(e)}")
        return False
def get_class_name_from_path(file_path:str):
    # 'components/camel-snakeyaml/src/main/java/org/apache/camel/component/snakeyaml/SnakeYAMLDataFormat.java'
    # or 'org/apache/camel/spi/RestConfiguration$RestHostNameResolver.class'
    if file_path.endswith("java"):
        return file_path.split('/')[-1][:-5].split('$')[0]
    else:
        return file_path.split('/')[-1][:-6].split('$')[0] 
def get_included_jars(jar_file_paths:list, git_repo_path, commit_hash) -> list:
    result=[]
    diff = get_commit_diff_output(git_repo_path, commit_hash)
    class_names=[get_class_name_from_path(item['file_path']) for item in diff]
    for jar_path in jar_file_paths:
        for class_name in class_names:
            if is_class_in_jar(jar_path, class_name):
                result.append(jar_path.split("/")[-1])
                break
    return result
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
def download_release(release_path,jar_lists, github_repo_local_path, patch_commit_hash):
    ensure_directory_exists(release_path) 
    full_process_jar=""
    for jar_url in jar_lists:
        jar_name=os.path.basename(jar_url)
        if jar_url in failed_jar_url:
            continue
        if os.path.exists(os.path.join(release_path,jar_name)):
            continue
        response = requests.get(jar_url)
        if response.status_code == 200:
            # Save the content of the response to the local file
            with open(os.path.join(release_path,jar_name), 'wb') as file:
                file.write(response.content)
        else:
            failed_jar_url.append(jar_url)
            # print(f"Failed to download file {jar_url}. Status code: {response.status_code}")
    result = subprocess.run("du -sh {}".format(release_path), shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True) 
    size=result.stdout.split('\t')[0]
    if size[-1]=='M' and float(size[:-1])>20:
        included_jars=get_included_jars([ os.path.join(release_path,os.path.basename(jar_url)) for jar_url in jar_lists], 
                                    github_repo_local_path, patch_commit_hash)
        for jar_url in jar_lists:
            jar_name=os.path.basename(jar_url)
            # if os.path.exists(os.path.join(release_path,jar_name)) and (jar_name in included_jars or 'starter' in jar_name or 'core' in jar_name):
            if os.path.exists(os.path.join(release_path,jar_name)):
                full_process_jar+=os.path.join(release_path,jar_name)+":"
    else:
        for jar_url in jar_lists:
            jar_name=os.path.basename(jar_url)
            if os.path.exists(os.path.join(release_path,jar_name)):
                full_process_jar+=os.path.join(release_path,jar_name)+":"
    return full_process_jar
@timer_func
def run_command(analysis_command:str, print_all=True):
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
    download_github_repo(github_repo_local_path,exp_config['url'])
    release_path=os.path.join(projects_root_path,exp_config['project'],exp_config['release_commit']+"_release")
    if len(exp_config['dependencies'])==0:
        full_process_jar=download_release(release_path,exp_config['jar_url'],github_repo_local_path,exp_config['patch_commit'])
    else:
        full_process_jar=download_release(release_path,exp_config['jar_url']+exp_config['dependencies'],github_repo_local_path,exp_config['patch_commit'])
    # java -cp $JAR_PATH SanityCheckCommitDetector $process_jar ../projects/$PROJECT/$PROJECT $RELEASE_COMMIT $PATCH_COMMIT
    analysis_command=["java","-Xms8g","-Xmx12g","-cp",get_java_class_path(),module,full_process_jar,github_repo_local_path,exp_config['release_commit'],exp_config['patch_commit']]
    # print(' '.join(analysis_command))
    run_command(analysis_command,print_all)
if __name__ == '__main__':
    exp_configs=parse_exp_json("experiment/release_jar_exp.json") 
    projects_root_path=Path("../projects/")
    if len(sys.argv) == 1:
        for i in range(len(exp_configs)):
            exp_config=exp_configs[i]
            do_exp_for_one(projects_root_path,exp_config,True)
    if len(sys.argv) == 2 and sys.argv[1] == 'permission':
        for i in range(len(exp_configs)):
            exp_config=exp_configs[i]
            do_exp_for_one(projects_root_path,exp_config,True,'BlockListCommitDetector')