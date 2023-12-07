# EarlyVulnFix
EarlyVulnFix is a tool for silent taint-style vulnerability fixes identification.

### Installation
To build the EarlyVulnFix, run `mvn package` in the "fix-patch-detection" subdirectory using jdk 1.8.

### Usage
To begin, please modify the fix-patch-detection/config.json file on your local machine to specify the path for the CodeQL rules. You can acquire the CodeQL rules repository by executing the command `git clone https://github.com/github/codeql.git`.

EarlyVulnFix comprises two modules designed to identify two distinct types of fixes: sanity check fixes and permission list fixes. The tool provides two classes, namely, SanityCheckCommitDetector and BlockListCommitDetector, serving as the entry points for these modules.

For command-line usage, execute the following command in the "fix-patch-detection" subdirectory:
```java -cp $(scripts/classpath.py) <class_name> <jars_path> <git_repo_path> <jar_commit_hash> <detected_commit_hash>```

As an illustration, suppose you have cloned the plexus-utils repository and downloaded the plexus-utils-3.0.16.jar file locally. In this case, you can run the following command: ```java -cp $(scripts/classpath.py) SanityCheckCommitDetector ../projects/plexus-utils/cf317f9_release/plexus-utils-3.0.16.jar ../projects/plexus-utils/plexus-utils cf317f9 b38a1b3``` . Here, cf317f9 represents the commit associated with the release of version 3.0.16, and b38a1b3 corresponds to a fix addressing a command injection vulnerability.

If you prefer using IDEs such as IntelliJ IDEA, you can input four arguments, namely, <jars_path> <git_repo_path> <jar_commit_hash> <detected_commit_hash>, in the Edit Configurations settings.

### Commits dataset
The dataset for vulnerability-fix commits employed in our experiment is available in "fix-patch-detection/experiment/compile_exp.json" and "fix-patch-detection/experiment/release_jar_exp.json." 

The dataset is divided into two files due to the varied sources of obtaining JARs for fixes. In some instances, we acquire JARs by directly downloading the released JARs from repositories, while for others, we compile the projects to generate the corresponding JARs. 

The non-fix commits were automatically generated during the experiment by retrieving the remaining commits within the same release.

### Experiment
Before the experiment, ensure you download JDK 8 and JDK 11 onto your local machine and set the environment variables $JAVA8_HOME and $JAVA11_HOME accordingly. Note that various projects may require different JDK versions for compilation.


The scripts in the "fix-patch-detection/experiment" subdirectory facilitate experiment execution. For vulnerability-fix commits, use "release_jar_exp.py" and "compile_jar_exp.py". To analyze non-fix commits, employ "release_jar_before_after.py" and "compile_jar_before_after.py". These scripts automate all necessary experiment operations, encompassing repository download, compilation, release JAR download, and tool execution.

