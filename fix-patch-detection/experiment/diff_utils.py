import re
import subprocess
import sys
def get_previous_commit(github_project_path,patch_commit_hash):
    get_previous_commit_script="""
#!/bin/bash
PROJECT_DIR={}
COMMIT={}
pushd $PROJECT_DIR > /dev/null
    git log --pretty=%H -n 2 $COMMIT | tail -n 1
popd > /dev/null
"""
    previous_commit=subprocess.run(['bash', "-c", get_previous_commit_script.format(github_project_path,patch_commit_hash)], text=True, capture_output=True).stdout.strip()
    return previous_commit
def parse_git_diff_output(diff_output):
    changes = []
    lines = diff_output.strip().split('\n')

    current_file = None
    current_line_number = None
    current_content = []

    for line in lines:
        match = re.match(r'^(.*?):(\d+):([-+ ])(.*)$', line)
        if match:
            file_path, line_number, change, content = match.groups()

            if current_file is not None and (file_path != current_file or line_number != current_line_number):
                changes.append({
                    'file_path': current_file,
                    'line_number': int(current_line_number),
                    'content': '\n'.join(current_content)
                })
                current_content = []

            current_file = file_path
            current_line_number = line_number
            if change != '-':
                current_content.append(content)

    if current_file is not None:
        changes.append({
            'file_path': current_file,
            'line_number': int(current_line_number),
            'content': '\n'.join(current_content)
        })

    return changes
def get_commit_diff_output(github_project_path,patch_commit_hash):
    previous_commit = get_previous_commit(github_project_path,patch_commit_hash)
    diff_str_patch=subprocess.run(['bash','scripts/get_diff_from_commits.sh', github_project_path, previous_commit, patch_commit_hash], text=True, capture_output=True).stdout.strip()
    return parse_git_diff_output(diff_str_patch)