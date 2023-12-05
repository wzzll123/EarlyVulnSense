#!/bin/bash

# Function to process a single Maven project
process_maven_project() {
    local project_dir="$1"
    local jars=("$project_dir"/target/*.jar)
    for jar in "${jars[@]}"
    do
        if [[ -f "$jar" ]]; then
            echo $jar
        fi
    done
}

# Function to process a single Gradle project
process_gradle_project() {
    local project_dir="$1"
    local jars=("$project_dir"/build/libs/*.jar)
    for jar in "${jars[@]}"
    do
        if [[ -f "$jar" ]]; then
            echo $jar
        fi
    done

}

# Function to process a project (Maven or Gradle)
process_project() {
    local project_file="$1"
    
    local project_dir=$(dirname "$project_file")

    if [[ -f "$project_dir/pom.xml" ]]; then
        process_maven_project "$project_dir"
    fi

    if [[ -f "$project_dir/build.gradle" ]]; then
        process_gradle_project "$project_dir"
    fi
}

# Set the root directory to search for Maven/Gradle projects
# root_directory="../projects/zrlog/bd2dba1/zrlog-bd2dba1/"
root_directory=$1
# Find Maven and Gradle projects and their submodules
find "$root_directory" -type f -name "pom.xml" -o -name "build.gradle" | while read -r project_file; do
    process_project "$project_file"
done | paste -s -d ':'