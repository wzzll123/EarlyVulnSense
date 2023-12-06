#!/usr/bin/env python
import os
JAR_PATH = "target/fix-patch-detection-1.0-SNAPSHOT.jar"
dependency_dir = "target/dependency"

for filename in os.listdir(dependency_dir):
    if filename.endswith(".jar"):
        jar_path = os.path.join(dependency_dir, filename)
        JAR_PATH += ":" + jar_path
print(JAR_PATH)