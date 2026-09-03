#!/bin/sh
set -eu

source_jar=${1:?OpenCV source JAR path is required}
output_jar=${2:?Output JAR path is required}
work_dir=$(mktemp -d)
source_dir="$work_dir/source"
class_dir="$work_dir/classes"
manifest_file="$work_dir/MANIFEST.MF"
source_list="$work_dir/sources.list"

mkdir -p "$source_dir" "$class_dir"
(
  cd "$source_dir"
  jar xf "$source_jar"
)

if [ ! -f "$source_dir/org/opencv/core/Mat.java" ]; then
  echo "OpenCV JAR does not contain the embedded Java sources required for Java 8 rebuilding" >&2
  exit 1
fi

cp "$source_dir/META-INF/MANIFEST.MF" "$manifest_file"
find "$source_dir/org" -type f -name '*.java' | sort > "$source_list"
javac -encoding UTF-8 -source 8 -target 8 -d "$class_dir" @"$source_list"

# Keep the original manifest. The supplied source JAR remains unchanged and is
# the provenance record for the generated bindings.
jar cfm "$output_jar" "$manifest_file" -C "$class_dir" .

major_version=$(od -An -t u1 -N 8 "$class_dir/org/opencv/core/Mat.class" | awk '{print $8}')
if [ "$major_version" != "52" ]; then
  echo "Rebuilt OpenCV JAR is not Java 8 bytecode (major=$major_version)" >&2
  exit 1
fi

echo "Rebuilt OpenCV 4.10.0 Java bindings for Java 8: $output_jar"
