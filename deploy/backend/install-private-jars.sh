#!/bin/sh
set -eu

project_root=${1:-/workspace/apps/backend}
resource_dir="$project_root/jeecg-module-system/jeecg-system-start/src/main/resources"
asrt_jar="$resource_dir/asrt_sdk_maven-1.0-alpha1.jar"
opencv_jar="$resource_dir/opencv-4.10.0.jar"
opencv_java8_jar="$(mktemp /tmp/opencv-4.10.0-java8.XXXXXX.jar)"

for jar_file in "$asrt_jar" "$opencv_jar"; do
  if [ ! -f "$jar_file" ]; then
    echo "Required private JAR is missing: $jar_file" >&2
    exit 1
  fi
done

private_jar_count=$(find "$resource_dir" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d ' ')
if [ "$private_jar_count" != "2" ]; then
  echo "Unexpected private JAR set; audit the resources directory before building" >&2
  find "$resource_dir" -maxdepth 1 -type f -name '*.jar' -print >&2
  exit 1
fi

metadata_dir=$(mktemp -d)
(
  cd "$metadata_dir"
  jar xf "$asrt_jar" META-INF/maven/net.ailemon.asrt/asrt_sdk_maven/pom.xml
)

mvn -B org.apache.maven.plugins:maven-install-plugin:3.1.1:install-file \
  -Dfile="$asrt_jar" \
  -DpomFile="$metadata_dir/META-INF/maven/net.ailemon.asrt/asrt_sdk_maven/pom.xml"

# The supplied OpenCV JAR was produced by JDK 11 although this project targets
# Java 8. It embeds its generated Java sources, so rebuild only those bindings
# with the Java 8 compiler. The original private JAR remains untouched.
/usr/local/bin/rebuild-opencv-java8.sh "$opencv_jar" "$opencv_java8_jar"

# This JAR has no Maven metadata. Its coordinate is derived from the org.opencv
# package plus Manifest Implementation-Version 4.10.0 and matches the POM.
# The native library remains an optional future mount.
mvn -B org.apache.maven.plugins:maven-install-plugin:3.1.1:install-file \
  -Dfile="$opencv_java8_jar" \
  -DgroupId=org.opencv \
  -DartifactId=opencv \
  -Dversion=4.10.0 \
  -Dpackaging=jar \
  -DgeneratePom=true
