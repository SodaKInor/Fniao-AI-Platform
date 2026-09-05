ARG MYSQL_IMAGE=mysql:8.0.36
FROM ${MYSQL_IMAGE}

# The private baseline is mounted read-only at runtime by Compose because it can
# exceed BuildKit's 500 KiB secret limit. It remains outside the build context.
COPY database/bootstrap/002-local-sanitize.sql /docker-entrypoint-initdb.d/002-local-sanitize.sql
COPY database/migrations/ai-core/V001__04a_assets_jobs.sql /docker-entrypoint-initdb.d/003-V001__04a_assets_jobs.sql
COPY database/migrations/stream/V002__04a_video_stream.sql /docker-entrypoint-initdb.d/004-V002__04a_video_stream.sql
