#!/bin/sh
# Inside Java 8 container. Source is read-only at /workspace, disposable artifacts at /validation.
set -eu
mkdir -p /validation/classes /validation/test-classes /validation/tmp
keytool -genkeypair -alias fixture -keyalg RSA -keysize 2048 -storetype PKCS12 \
  -keystore /validation/server.p12 -storepass fixture-only -keypass fixture-only \
  -dname CN=fixture.invalid -ext SAN=ip:127.0.0.1 -validity 3
keytool -exportcert -rfc -keystore /validation/server.p12 -storepass fixture-only \
  -alias fixture -file /validation/server.crt
javac -encoding UTF-8 -source 8 -target 8 -cp '/validation/libs/*' \
  -d /validation/classes @/validation/main-sources.txt
javac -encoding UTF-8 -source 8 -target 8 -cp '/validation/classes:/validation/libs/*' \
  -d /validation/test-classes @/validation/test-sources.txt
java -Djava.io.tmpdir=/validation/tmp -Dlogback.configurationFile=/validation/logback-test.xml \
  -cp '/validation/test-classes:/validation/classes:/validation/libs/*' org.junit.runner.JUnitCore \
  org.jeecg.modules.ai.client.DraftInferenceTest org.jeecg.modules.ai.client.DraftArtifactTest \
  org.jeecg.modules.ai.client.DraftVideoProviderTest org.jeecg.modules.ai.client.DraftStreamProviderTest \
  org.jeecg.modules.ai.application.capabilities.CapabilityTest org.jeecg.modules.ai.legacy.AiAccessTest
