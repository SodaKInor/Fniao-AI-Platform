#!/bin/sh
# Run inside the Java 8 validation container; worktree mounted read-only at /workspace.
set -eu
ai_source=/workspace/backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai
java -version
javac -version
mkdir -p /validation/domain /validation/dto
find "$ai_source/domain" "$ai_source/port" -name '*.java' | sort > /validation/domain-sources.txt
find "$ai_source/api/dto" -name '*.java' | sort > /validation/dto-sources.txt
javac -encoding UTF-8 -source 8 -target 8 -d /validation/domain @/validation/domain-sources.txt
javac -encoding UTF-8 -source 8 -target 8 -cp /validation/domain:/validation/jackson-annotations.jar -d /validation/dto @/validation/dto-sources.txt
jdeps -verbose:class -recursive /validation/domain > /validation/domain-dependencies.txt
jdeps -verbose:class -cp /validation/domain:/validation/jackson-annotations.jar /validation/dto > /validation/dto-dependencies.txt
