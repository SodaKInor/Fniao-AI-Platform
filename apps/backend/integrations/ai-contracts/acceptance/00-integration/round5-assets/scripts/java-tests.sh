#!/bin/sh
set -eu
rm -rf /validation/classes /validation/test-classes
mkdir -p /validation/classes /validation/test-classes
javac -encoding UTF-8 -parameters -source 8 -target 8 -cp '/validation/lib/*' \
  -d /validation/classes @/validation/main-sources.txt
javac -encoding UTF-8 -parameters -source 8 -target 8 -cp '/validation/classes:/validation/lib/*' \
  -d /validation/test-classes @/validation/test-sources.txt
java -Dlogback.configurationFile=/validation/logback-test.xml \
  "-Dai.test.jdbc=${AI_TEST_JDBC:?Explicit temporary schema is required}" \
  -cp '/validation/test-classes:/validation/classes:/validation/lib/*' org.junit.runner.JUnitCore \
  org.jeecg.modules.ai.assetsjobs.PersistenceTest \
  org.jeecg.modules.ai.assetsjobs.WorkflowTest \
  org.jeecg.modules.ai.assetsjobs.VideoWorkflowTest \
  org.jeecg.modules.ai.assetsjobs.ApiTest \
  org.jeecg.modules.ai.assetsjobs.VideoApiTest \
  org.jeecg.modules.ai.assetsjobs.StreamWorkflowTest \
  org.jeecg.modules.ai.assetsjobs.StreamApiTest \
  org.jeecg.modules.ai.assetsjobs.StorageTest \
  org.jeecg.modules.ai.assetsjobs.ConfigurationTest
