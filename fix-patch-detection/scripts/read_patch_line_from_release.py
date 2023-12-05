import re
import subprocess
import sys
def is_irrelevant_line(line:str):
    if line.strip() == '{' or line.strip() == '}':
        return True
    if line.strip().startswith('//') or line.strip().startswith('/*'):
        return True
    return False

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
            if is_irrelevant_line(content):
                continue
            if current_file is not None and (file_path != current_file or line_number != current_line_number):
                if not file_path.endswith(".java"):
                    continue
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

def test_parse_git_diff_output():
    # Replace this string with your actual Git diff output
    git_diff_output = """
pom.xml:  :-    <version>1.38-SNAPSHOT</version>
pom.xml:37:+    <version>1.39-SNAPSHOT</version>
src/main/java/hudson/plugins/ec2/AmazonEC2Cloud.java:26:+import com.amazonaws.SdkClientException;
src/main/java/hudson/plugins/ec2/AmazonEC2Cloud.java:   :-            AWSCredentialsProvider credentialsProvider = createCredentialsProvider(useInstanceProfileForCredentials, credentialsId);
src/main/java/hudson/plugins/ec2/AmazonEC2Cloud.java:156:+            try {
        """

    changes = parse_git_diff_output(git_diff_output)

    for change in changes:
        print(f"File Path: {change['file_path']}")
        print(f"Line Number: {change['line_number']}")
        print(f"Content:\n{change['content']}\n")

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Usage: python read_patch_line_from_release.py <path/to/project> <release commit> <patch commit>")
        sys.exit(1)
    project_path=sys.argv[1]
    release_commit=sys.argv[2]
    patch_commit=sys.argv[3]
    get_previous_commit_script="""
#!/bin/bash
PROJECT_DIR={}
COMMIT={}
pushd $PROJECT_DIR > /dev/null
    git log --pretty=%H -n 2 $COMMIT | tail -n 1
popd > /dev/null
"""
    
    previous_commit=subprocess.run(['bash', "-c", get_previous_commit_script.format(project_path,patch_commit)], text=True, capture_output=True).stdout.strip()
    diff_str_patch=subprocess.run(['bash','scripts/get_diff_from_commits.sh', project_path, previous_commit, patch_commit], text=True, capture_output=True).stdout.strip()
    
    # diff_str_patch=subprocess.run(['bash','scripts/get_diff_from_commits.sh', project_path, previous_commit, patch_commit], capture_output=True).stdout.decode('utf-8', 'ignore').strip()
    # diff_str_release=subprocess.run(['bash','scripts/get_diff_from_commits.sh', project_path, previous_commit, release_commit], text=True, capture_output=True).stdout.strip()
    diff_str_release=subprocess.run(['bash','scripts/get_diff_from_commits.sh', project_path, previous_commit, release_commit],capture_output=True).stdout.decode('utf-8', 'ignore').strip()
    diff_dic_patch = parse_git_diff_output(diff_str_patch)
    content_list = [item['content'] for item in diff_dic_patch]
    diff_dic_release = parse_git_diff_output(diff_str_release)
    diff_dic_release_updated=[]
    for item in diff_dic_release:
        for item_patch in diff_dic_patch:
            if item['content']==item_patch['content'] and item['file_path']==item_patch['file_path'] and item['file_path'].endswith('.java'):
                diff_dic_release_updated.append(item)
                break
    diff_location_release_updated=[item['file_path'].split('/')[-1]+':'+str(item['line_number']) for item in diff_dic_release_updated]
    for location in diff_location_release_updated:
        print(location)

    # print(content_list)
    # previous_commit=subprocess.run(['bash', "scripts/get_previous_commit.sh", project_path, patch_commit], text=True, capture_output=True).stdout.strip()


    
