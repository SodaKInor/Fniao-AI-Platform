# Fniao AI Platform final integration report

Date: 2026-09-04 (America/Los_Angeles)

## Release-candidate scope

This repository is a local release candidate for the final directory layout. The RC proves only:

- deterministic `simulated/stub` image, uploaded-video, and live-stream behavior; and
- fail-closed `disabled` behavior when no real provider is configured.

It does **not** prove a real GPU release. The RTX 5070 LAN service and the single-GPU expanded 48 GB RTX 4090 production service remain external delivery and acceptance gates.

## Integration lineage

- Common stage-A base (`08A_SHA`): `07f970bd475a70554946140427c15635e0802cca`
- Database package: `a9d9a73a697dd20e7956d72f9edecfe2c9940f03`
- Remote-boundary package: `a50621ede81dd1c6c5d3681f229bec10df022706`
- Database merge: `2a35ed8`
- Remote-boundary merge: `892f73c`
- Package file ownership overlap relative to `08A_SHA`: zero

The package branches were merged in database-then-remote order. No directory copy or whole-directory overwrite was used.

## Final repository boundaries

- `apps/backend`: Java 8 JEECG application and feature-organized AI modules
- `apps/frontend`: Vue application and feature-organized AI modules
- `database`: sanitized bootstrap, immutable V001/V002 migrations, stub seed, private-input policy, and verification
- `remote-inference`: versioned business/provider contracts, fixtures, standalone modular stub, historical acceptance, and handoff records
- `deploy/remote-inference`: disabled-by-default provider profile, explicit stub overlay, templates, and validators
- `docs/remote-inference`: active architecture, operations, ownership, and preparation material
- `openspec`: the single in-repository OpenSpec source
- `tools`: root-resolving Graphify, Serena, build, test, and layout validation entry points

The backend AI code remains organized by `capability`, `asset`, `job`, `result`, `image`, `video`, `stream`, `provider`, `operations`, and `legacy`. Feature internals retain their actual `api`, `application`, `domain`, `port`, `persistence`, `storage`, `client`, and `config` responsibilities. No algorithm-named controller/service/page copies and no empty audio/chat/training modules were introduced.

## Validation summary

| Area | Result | Evidence summary |
| --- | --- | --- |
| Java 8 reactor | PASS | Full Maven reactor package completed successfully in the Java 8 build image. |
| Java AI tests | PASS | 20 JUnit classes, 110 tests, disposable MySQL/TLS fixture, no failures. |
| Vue | PASS | Node 16 production build; AI-module ESLint; 27 frozen frontend tests. Build warnings are limited to existing CSS-order and asset-size notices. |
| Database | PASS | Authorized baseline restored into an isolated copy; 122 baseline tables; V001 then V002; repeated migrations and repeated stub seed stable. |
| Migrations | PASS | V001 SHA-256 `0e50ad45101cc92bff877aa63ae60bb42fbf9720f2dc5d93c604a5f682f9c026`; V002 SHA-256 `40e190fea24cdd476ef7bbd00520fe1b859082a8e8440b7ae4fe3c3845b54a15`; bytes and order unchanged. |
| Compose | PASS | Default expansion contains only MySQL, Redis, backend, and frontend; inference is `disabled`, development stub is `false`, provider key is `remote`, and provider URL is empty. Core deployment health, authentication, sanitized data, same-origin proxying, restart, and persistence checks pass. |
| Explicit stub overlay | PASS | Adds only the profiled standalone stub; backend mode becomes `remote`, `development-stub=true`, `provider-key=stub`. |
| Login and permission | PASS | Actual password-plus-captcha login; anonymous AI access returns 401; a user without `ai:infer` receives 403; an explicitly permitted owner can execute. |
| Simulated image | PASS | Remote HTTP stub result succeeded, remained marked simulated, returned one detection, and stored/downloaded artifact hash matched. |
| Simulated video | PASS | Remote HTTP stub result succeeded with one event and one snapshot; snapshot persisted and downloaded. |
| Simulated stream | PASS | Source lookup, start, running state, cursor events, snapshot, stop, and `STOPPED` state passed. |
| Disabled and history | PASS | All registered capabilities unavailable; new execution rejected with 409 without adding a job; prior job, file, and three historical management reads remained accessible after backend recreation. |
| Contracts and fixtures | PASS | Four OpenAPI documents, 34 positive/negative JSON cases, two PNG fixtures, 38-file fixture inventory, and the modular stub contract validate. Stub unit tests: 7/7. |
| Layout and dependencies | PASS | 182 backend AI Java files, 31 frontend AI files, required roots present, allowed dependency matrix satisfied, no Java import cycle, and file-size limits satisfied. |
| Secrets and old active paths | PASS | No tracked private database, environment secret, private key, model weight, temporary worktree path, or legacy WGAI absolute path in active runtime configuration. The fixed stub token is documented public development-only data and cannot enable production mode. |
| OpenSpec | PASS | `remote-inference-platform` and `remote-video-streaming` both pass `openspec validate --strict`. |

The local RC test intentionally returned the application to the default disabled mode and stopped the explicit stub service. Real provider evidence validation is therefore pending by design, not counted as a local failure.

## Runtime and security decisions

- The browser uses the same-origin `/jeecg-boot` path and does not receive provider URL, RTSP URL, bearer token, CA, or provider session identity.
- The business backend is the only provider HTTP client.
- Default Compose neither declares nor references the stub service.
- Real URL, bearer-token file, CA file, and TLS choices are supplied only through deployment templates and local/deployment secret material.
- AI private input/output lives on a dedicated persistent volume; backend recreation and disabled-mode transition do not orphan historical files.
- MySQL initialization mounts the authorized private baseline read-only at runtime. The baseline is excluded from Git, Docker build context, and image layers; V001/V002 remain baked in their fixed order.
- Missing real-provider facts keep the matching capabilities unavailable. There is no fallback from a real-provider configuration to the stub.

## Tool state

- Graphify: final-root scripts resolve with `git rev-parse`; the final graph contains 28,287 nodes, 72,383 links, and 1,087 communities, and a scoped query completed successfully.
- Serena: lifecycle scripts resolve the current Git root; the sole registered project points to `/Users/twowt88/Documents/ChatGPT/Fniao-AI-Platform`. Its MCP endpoint listens on `127.0.0.1:9121`, and Java, TypeScript, and Vue language-service startup checks pass.
- OpenSpec: used directly from this repository's `openspec` directory; it is not copied and neither OpenSpec nor Graphify is registered as MCP.

Final tool verification: PASS. The Codex MCP inventory contains the built-in tools and the single `serena_fniao` endpoint; Graphify and OpenSpec remain command-line/repository tools rather than MCP servers.

## Local runtime cleanup

- Legacy `wgai` and `wgai-ri-00-integration` containers were stopped and removed; their legacy application networks and six obsolete WGAI image tags were removed.
- Only the current Fniao AI Platform MySQL, Redis, backend, and frontend containers remain running. The explicit current stub image is retained for reproducible RC checks, but no stub container runs in the default deployment.
- Legacy named database/data volumes were intentionally retained as recoverable data. No old WGAI repository or volume was deleted.
- The old `com.local.wgai-serena` login service is disabled and unloaded; only the current Fniao Serena session is active.
- The verified database and remote-boundary commits are present on `main`; both temporary sibling worktrees were clean and were removed through Git after the first successful `origin/main` push.
- The trace-only `source-wgai` remote was removed after that push. `origin` is the sole remaining remote; the old WGAI repository itself remains untouched.

## Historical-path retention

Old absolute paths are retained only where they are part of immutable or explanatory history:

- `remote-inference/acceptance/**` for batches 01-07 acceptance evidence and its archived scripts;
- `docs/remote-inference/work-packages.json`, `local-artifacts.json`, `REPOSITORY_BASELINE.md`, `PROMPTS.md`, `PARALLEL_PLAN.md`, and related migration/ownership narrative;
- `openspec/changes/**` planning/audit statements that describe the source layout or migration sequence;
- `apps/PATH_MIGRATION.md`, package handoff records, `SETUP_REPORT.md`, and `deploy/STATUS.md` as explicit provenance/migration records.

These records are not runtime inputs. Active Compose, Dockerfiles, environment templates, backup/restore entry points, build/test commands, and tool launchers resolve from the current Git root.

## Open real-service gates

1. RTX 5070 LAN: receive the colleague-owned service, then verify method/path, TLS/CA, authentication, capability/source mapping, limits, image/video/stream behavior, queries, cancellation/stop, failures, deduplication, and retention from the business backend container.
2. RTX 4090 48 GB production: receive the single-card expanded service and repeat production authorization, limits, results, persistence/recovery, disabled rollback, release artifact, and image-digest acceptance.
3. Only after both gates have real evidence may the main specs be synchronized and the two OpenSpec changes archived.
