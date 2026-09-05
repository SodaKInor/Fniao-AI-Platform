# 08C remote boundary handoff

## Identity and scope

- Base (`08A_SHA`): `07f970bd475a70554946140427c15635e0802cca`
- Branch: `codex/remote-boundary`
- Worktree: `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform-worktrees/remote-boundary`
- OpenSpec ownership: `remote-inference-platform` 8.3 and `remote-video-streaming` 7.3 remote half
- Migration inventory: [MIGRATION_MANIFEST.json](../MIGRATION_MANIFEST.json)
- Complete changed-file list: [08C-CHANGED-FILES.txt](08C-CHANGED-FILES.txt)

The package moves 353 existing files. It does not modify the root Compose file, application business source,
database content, shared tools, or OpenSpec task tables. Historical acceptance evidence remains byte-identical.

## Delivered boundary

| Destination | Existing files moved | Purpose |
|---|---:|---|
| `remote-inference/contracts` | 7 | Business 1.0/1.1, provider drafts 0.1/0.2, and the stub provider contract |
| `remote-inference/fixtures` | 38 | Synthetic image, video, stream, empty-result, invalid-input, and fault fixtures |
| `remote-inference/stub` | 14 | Independent HTTP stub before the new scenario module is counted |
| `remote-inference/acceptance` | 266 | Frozen acceptance evidence for rounds 01—07 and integration rounds |
| `remote-inference/handoff` | 2 | Existing development and production provider handoff templates |
| `docs/remote-inference` | 9 | Architecture, ownership, run/preparation, plan, prompts, and baseline records |
| `deploy/remote-inference` | 16 | Non-database provider profiles, configuration, validators, and templates |

The stub remains split across entry/start, server, configuration, authentication, request body, routes,
validation, scenarios, response encoding, fixtures, state, and tests. The scenario selector now has its own
`src/scenarios.cjs`; no business behavior changed.

The only files left under `apps/backend/deploy/remote-ai` are database-package owned:

- `migrations/V001__04a_assets_jobs.sql`
- `migrations/V002__04a_video_stream.sql`
- `stub-bindings.example.sql`

## Verification

- `python3 remote-inference/validate-contracts.py`: PASS — four OpenAPI documents, 34 positive/negative JSON
  fixtures, two PNG fixtures, cross-field semantics, and the simulated stub contract.
- `node remote-inference/validate-boundary.cjs`: PASS — directory uniqueness, 134 JSON acceptance records,
  active relative links, modular stub isolation, and production defaults.
- `npm test` in `remote-inference/stub`: PASS — 7/7 HTTP, authentication, image/video/stream, empty-result,
  response-loss, interrupted-artifact, and forbidden-field tests.
- Explicit stub Compose render: PASS — only the `remote-ai-stub` profile creates the stub and uses the new
  repository-root build context.
- Production/default Compose render: PASS — no stub service; mode is `disabled`, development stub is `false`,
  and provider key remains `remote`.
- Provider intake/evidence examples: PASS (fail-closed) — the unconfirmed intake and incomplete real evidence
  are rejected by the moved validators.
- Byte comparison to `08A_SHA`: PASS — all contracts, fixtures, 266 acceptance files, two provider handoff
  templates, nine docs, V001, V002, and the stub seed preserve their bytes.

## Stage D integration items

These are deliberately recorded here instead of changing another package's files:

1. Update `AGENTS.md` to read active architecture from `docs/remote-inference` after the merge.
2. Update `apps/PATH_MIGRATION.md` and backend test fixture discovery in
   `apps/backend/jeecg-module-system/jeecg-system-biz/src/test/java/org/jeecg/modules/ai/client/ClientTestInputs.java`
   from `integrations/ai-contracts/examples` to `remote-inference/fixtures`.
3. After the database branch is merged, update the deployment runbook's stub seed reference to
   `database/seeds/stub/stub-bindings.example.sql` and verify the final database migration paths.
4. Reconcile historical/current wording in `docs/remote-inference` (old `backend-github`, `frontend-vue`, and
   pre-migration worktree paths) while leaving 01—07 acceptance evidence untouched.
5. Update the two OpenSpec task tables only after both parallel handoffs are accepted; then validate both changes
   with strict mode.
6. Re-run the full Compose, backend, frontend, remote-to-stub, disabled-mode, and historical-result suite from the
   final root. Rebuild Graphify once and point the single Serena project at the final root only at that stage.

No real RTX 5070/4090 capability is claimed by this package. Those gates remain open and production remains
disabled without confirmed real-provider configuration and evidence.
