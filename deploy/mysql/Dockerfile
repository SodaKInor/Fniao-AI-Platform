ARG MYSQL_IMAGE=mysql:8.0.36
FROM ${MYSQL_IMAGE}

# Initialization files are part of the local image so Docker Desktop does not
# need a runtime bind mount into the macOS Documents directory.
COPY backend-github/db/java_ai.sql /docker-entrypoint-initdb.d/001-java_ai.sql
COPY deploy/db/002-local-sanitize.sql /docker-entrypoint-initdb.d/002-local-sanitize.sql
