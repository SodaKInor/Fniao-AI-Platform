# Database package

This directory is the single version-controlled database boundary for the final repository.

Execution order is defined only by [`MIGRATIONS.json`](MIGRATIONS.json):

1. restore an approved private baseline into a disposable database;
2. apply `bootstrap/002-local-sanitize.sql`;
3. apply `migrations/ai-core/V001__04a_assets_jobs.sql`;
4. apply `migrations/stream/V002__04a_video_stream.sql`;
5. optionally apply `seeds/stub/stub-bindings.example.sql` in an explicitly simulated development environment.

V001 and V002 retain their original bytes, global version numbers, checksums and order. The stub seed is not a migration and must never be applied implicitly in production.

Run the isolated verification with an approved private dump that is outside Git:

```sh
python3 database/verify_database.py \
  --base 07f970bd475a70554946140427c15635e0802cca \
  --baseline database/private/java_ai.sql \
  --report database/validation/last-run.json
```

The verifier starts a network-isolated MySQL container backed by temporary memory, never attaches an existing volume, applies bootstrap and V001→V002 twice, exercises the stub seed twice, then removes the container.

Code-generator and feature-owned `*_menu_insert.sql` files remain in `apps/backend` or `apps/frontend`; they are not deployment migrations.
