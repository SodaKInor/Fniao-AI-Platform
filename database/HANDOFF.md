# Database layout handoff

## Package boundary

- Base commit: `07f970bd475a70554946140427c15635e0802cca`.
- Branch: `codex/database-layout`.
- Owned output: `database/**` plus the four `git mv` source removals recorded in `MIGRATIONS.json`.
- V001/V002 and the bootstrap/seed SQL are byte-for-byte moves.
- All 26 tracked `*_menu_insert.sql` files remain in their application-owned locations.
- No real dump, real row data, credential, model artifact or machine-specific path is included.

## Integration-stage path fixes

The final integration package must update these active consumers after merging both parallel branches:

1. the MySQL image build still names the old private baseline and bootstrap cleanup paths;
2. the root Docker ignore rules must exclude `database/private/**` before any build context can consume a locally supplied baseline;
3. the deployment README still documents the old baseline and cleanup paths;
4. the remote deployment README must point to the new V001, V002 and stub-seed locations after the remote-boundary move;
5. root Compose/Docker contexts and final release validation must consume `MIGRATIONS.json` in its declared order;
6. historical 01–07 evidence and scripts retain their recorded paths and must not be rewritten as current execution evidence.

OpenSpec task tables, shared tools, root deployment files and application business source are intentionally untouched in this package. Integration should mark the aggregate tasks complete only after verifying this commit together with the remote-boundary handoff.

## Verification evidence

`validation/last-run.json` records the disposable MySQL restore, repeated bootstrap, V001→V002 ordering, repeated migrations, repeated stub seed, isolation properties and cleanup result. Re-run `verify_database.py` with an authorized external baseline if integration changes any active database path.
