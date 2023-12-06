import git
from git import Repo
from pathlib import Path
from release_jar_exp import parse_exp_json,ensure_directory_exists,do_exp_for_one
import os
import zipfile
import sys
def is_before_commit(commit1,commit2):
    return commit1.committed_datetime < commit2.committed_datetime

def select_closest_commit(repo:Repo, patch_commit, release_commit_hashes:list):
    # patch_commit=repo.commit(patch_commit_hash)
    release_commits=[repo.commit(release_commit_hash) for release_commit_hash in release_commit_hashes]
    closest_commit=None
    for release_commit in release_commits:
        if release_commit.committed_datetime >= patch_commit.committed_datetime:
            if closest_commit is None or closest_commit.committed_datetime>release_commit.committed_datetime:
                closest_commit=release_commit
    return closest_commit
def get_commits_between_release(repo:Repo, previous_release_hash, after_release_hash):
    commits = []
    after_commit = repo.commit(after_release_hash)
    previous_commit = repo.commit(previous_release_hash)
    for commit in repo.iter_commits(after_commit):
        if commit.committed_datetime > previous_commit.committed_datetime:
            commits.append(commit)
    return commits
def get_commits_around_commit(repo:Repo, commit_hash, num_before=100, num_after=100):
    target_commit = repo.commit(commit_hash)
    commits_before=[]
    for before_commit in repo.iter_commits(repo.head.commit):
        if commit_hash in str(before_commit):
            if len(commits_before) > num_before:
                commits_before=commits_before[-num_before:]
            break
        else:
            commits_before.append(before_commit)
    commits_after = list(repo.iter_commits(target_commit, max_count=num_after+1))[1:]
    return commits_before, commits_after
def exp_for_before_after_commit(before_after_commit,repo:Repo,commit2release:dict,exp_config,projects_root_path,print_all=False,module='SanityCheckCommitDetector'):
    closest_commit=select_closest_commit(repo,before_after_commit,commit2release['commit2jar'].keys())
    if closest_commit is None:
        print(f"{repo} {before_after_commit} does not have closest commit")
    # else:
    #     print(f"The closest commit of {before_after_commit} is {closest_commit}")
    current_exp_config=exp_config.copy()
    current_exp_config['patch_commit']=before_after_commit.hexsha[:7]
    current_exp_config['release_commit']=closest_commit.hexsha[:7]
    current_exp_config['jar_url']=[jar.format(jar_version=commit2release['commit2jar'][current_exp_config['release_commit']]) for jar in commit2release['jar_url']]
    do_exp_for_one(projects_root_path,current_exp_config,print_all,module)
    # do_exp_for_one(projects_root_path,current_exp_config)
    
if __name__ == '__main__':
    exp_configs=parse_exp_json("experiment/release_jar_exp.json") 
    commiit2release_dict=parse_exp_json("experiment/commit2release.json")
    projects_root_path=Path("../projects/")
    # already_exp=['plexus-utils','opentsdb','xm-commons','dashbuilder','spring-data-jpa','uaa','raml-module-builder','ec2-plugin','jackson-databind',
    # 'cassandra','pippo']
    if len(sys.argv) == 2 :
        exclude_project=['struts','ignite','jackson-databind','jenkins'] # have multiple vul fix in a release
        # commits between two release
        for i in range(len(exp_configs)):
            exp_config=exp_configs[i]
            if exp_config['project'] in exclude_project:
                continue
            if exp_config['project'] != 'camel':
                continue
            commit2release=commiit2release_dict[exp_config['project']]
            github_repo_local_path=os.path.join(projects_root_path,exp_config['project'],exp_config['project'])
            repo=git.Repo(github_repo_local_path)
            if 'after_release_commit' in exp_config:
                commit_list = get_commits_between_release(repo,exp_config['previous_release_commit'],exp_config['after_release_commit'])
            else:
                commit_list = get_commits_between_release(repo,exp_config['previous_release_commit'],exp_config['release_commit'])
            # print(exp_config['project'],len(commit_list)) 
            # for commit in commit_list:
            #     print(f"Commit list: {commit.hexsha}")
            fix_commit = repo.commit(exp_config['patch_commit'])
            if fix_commit not in commit_list:
                print(exp_config['project'], "fix commit not between two release")
            else:
                commit_list.remove(fix_commit)
            for commit in commit_list:
                if sys.argv[1]=="sanity":
                    exp_for_before_after_commit(commit,repo,commit2release,exp_config,projects_root_path)
                elif sys.argv[1]=='permission':
                    exp_for_before_after_commit(commit,repo,commit2release,exp_config,projects_root_path,True,'BlockListCommitDetector')
    elif len(sys.argv) == 3:
        project = sys.argv[1]
        commit_hash = sys.argv[2]
        for i in range(len(exp_configs)):
            exp_config=exp_configs[i] 
            if exp_config['project'] != project:
                continue
            
            commit2release=commiit2release_dict[exp_config['project']]
            github_repo_local_path=os.path.join(projects_root_path,exp_config['project'],exp_config['project'])
            repo=git.Repo(github_repo_local_path) 
            commit = repo.commit(commit_hash)
            exp_for_before_after_commit(commit,repo,commit2release,exp_config,projects_root_path,True)
            break
    elif len(sys.argv) == 4 and sys.argv[3] == 'permission':
        project = sys.argv[1]
        commit_hash = sys.argv[2]
        for i in range(len(exp_configs)):
            exp_config=exp_configs[i] 
            if exp_config['project'] != project:
                continue
            commit2release=commiit2release_dict[exp_config['project']]
            github_repo_local_path=os.path.join(projects_root_path,exp_config['project'],exp_config['project'])
            repo=git.Repo(github_repo_local_path) 
            commit = repo.commit(commit_hash)
            exp_for_before_after_commit(commit,repo,commit2release,exp_config,projects_root_path,True,'BlockListCommitDetector')
            break


             


     
    

        