# EarlyVulnFix
EarlyVulnFix is a tool for silent taint-style vulnerability fixes identification.

### Installation
To build the EarlyVulnFix, run `mvn package` in the "fix-patch-detection" subdirectory.

### Usage
In the "fix-patch-detection" subdirectory, run following command:
```java -cp $(scripts/classpath.py) FixCommitDetector <jars_path> <git_repo_path> <jar_commit_hash> <detected_commit_hash>```

If you are using IDEs like IntelliJ IDEA, you can input four arguments <jars_path> <git_repo_path> <jar_commit_hash> <detected_commit_hash> in `Edit configurations`.

### commits dataset
The dataset for vulnerability-fix commits employed in our experiment is available in "fix-patch-detection/experiment/compile_exp.json" and "fix-patch-detection/experiment/release_jar_exp.json." The dataset is divided into two files due to the varied sources of obtaining JARs for fixes. In some instances, we acquire JARs by directly downloading the released JARs from repositories, while for others, we compile the projects to generate the corresponding JARs. The non-fix commits were automatically generated during the experiment by retrieving the remaining commits within the same release.
