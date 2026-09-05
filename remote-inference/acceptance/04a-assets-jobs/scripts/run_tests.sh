#!/bin/sh
# Java 8 container: read-only source, isolated MySQL network, writable /validation only.
set -eu
source_root=/workspace/backend-github/jeecg-module-system/jeecg-system-biz/src
rm -rf /validation/classes /validation/test-classes
mkdir -p /validation/classes /validation/test-classes
find "$source_root/main/java/org/jeecg/modules/ai" -name '*.java' > /validation/sources.txt
find "$source_root/test/java/org/jeecg/modules/ai/assetsjobs" -name '*.java' > /validation/test-sources.txt
javac -encoding UTF-8 -parameters -cp '/validation/lib/*' -d /validation/classes @/validation/sources.txt
javac -encoding UTF-8 -parameters -cp '/validation/classes:/validation/lib/*' -d /validation/test-classes @/validation/test-sources.txt
cat > /validation/logback.xml <<'XML'
<configuration><appender name="console" class="ch.qos.logback.core.ConsoleAppender"><encoder><pattern>%level %logger - %msg%n</pattern></encoder></appender><root level="ERROR"><appender-ref ref="console"/></root></configuration>
XML
java -Dlogback.configurationFile=/validation/logback.xml -cp '/validation/test-classes:/validation/classes:/validation/lib/*' org.junit.runner.JUnitCore \
 org.jeecg.modules.ai.assetsjobs.PersistenceTest \
 org.jeecg.modules.ai.assetsjobs.WorkflowTest \
 org.jeecg.modules.ai.assetsjobs.VideoWorkflowTest \
 org.jeecg.modules.ai.assetsjobs.ApiTest \
 org.jeecg.modules.ai.assetsjobs.VideoApiTest \
 org.jeecg.modules.ai.assetsjobs.StreamWorkflowTest \
 org.jeecg.modules.ai.assetsjobs.StreamApiTest \
 org.jeecg.modules.ai.assetsjobs.StorageTest \
 org.jeecg.modules.ai.assetsjobs.ConfigurationTest
