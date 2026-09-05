ARG MAVEN_IMAGE=maven:3.8.8-eclipse-temurin-8
ARG JAVA_IMAGE=eclipse-temurin:8-jre-jammy

FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace/apps/backend
COPY apps/backend/ ./
COPY deploy/backend/install-private-jars.sh /usr/local/bin/install-private-jars.sh
COPY deploy/backend/rebuild-opencv-java8.sh /usr/local/bin/rebuild-opencv-java8.sh
RUN --mount=type=cache,target=/root/.m2 \
    chmod 0755 /usr/local/bin/install-private-jars.sh /usr/local/bin/rebuild-opencv-java8.sh \
    && /usr/local/bin/install-private-jars.sh /workspace/apps/backend \
    && mvn -B -Pprod,docker-core -DskipTests -pl jeecg-module-system/jeecg-system-start -am clean package

FROM ${JAVA_IMAGE} AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && apt-get clean \
    && find /var/lib/apt/lists -mindepth 1 -delete
WORKDIR /app
COPY --from=build /workspace/apps/backend/jeecg-module-system/jeecg-system-start/target/jeecg-system-start-*.jar /app/app.jar
COPY deploy/backend/application-docker.yml /app/config/application-docker.yml
COPY deploy/remote-inference/application-remote-ai.yml /app/config/application-remote-ai.yml
RUN mkdir -p /data/uploads /opt/wgai/models /opt/wgai/native
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
