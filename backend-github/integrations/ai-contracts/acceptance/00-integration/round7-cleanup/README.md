# Round 7 cleanup integration acceptance

Status: **PASS — local simulated/disabled baseline only**

00 fast-forwarded the serial 07 delivery from `722aad3fa11f7075576b760ab3d6deae83cd1480`
through the four recorded cleanup commits to `46260b93c98c7403bdf3263ea475b281804bae04`.
The result is acceptable as the common starting point for the separate round-8 directory
restructure. It is not a real-provider release candidate.

## Accepted behavior

- The retained workspace has four focused pages: image detection, uploaded-video analysis,
  real-time event analysis, and task history.
- Image, video, stream-event, screenshot, download, history, authorization and cross-owner
  behavior passed against the explicit HTTP stub. Every simulated result remains visibly
  labelled in both API data and the browser.
- Disabled mode exposes zero available capabilities, rejects new inference with HTTP 409,
  creates no task, and still permits the owner to read previously stored jobs and results.
- All six retired local execution routes reject anonymous calls with 401 and authenticated
  calls with 409 `CAPABILITY_UNAVAILABLE`; retained management/history table counts do not
  change.
- V001 followed by V002 applies twice in a disposable database without changing the live
  integration database or rewriting historical tables.
- Backend Java 8 tests pass 60/60, frontend tests pass 27/27, scoped lint and both module
  boundary checks pass, both images build, and both OpenSpec changes validate strictly.
- Graphify was refreshed to 28,982 nodes, 73,435 edges and 1,157 communities. Its six
  pre-existing Vue parser warnings remain unchanged and do not fail the frontend build.

## Preserved for the next-directory refactor

Native and model dependencies (including OpenCV, ONNX Runtime, JavaCV, ASRT, RapidOCR and
Tess4J), their supporting build scripts, generic WebSocket/player assets, management CRUD,
database history and existing data volumes are intentionally retained. Round 07 does not
perform a repository-wide directory move. The top-level `apps`, `database`, `deploy`, `docs`
and `tools` layout remains round-8 work in its own worktree/new directory.

## Unfinished real integration

`realProviderValidated` is `false`. RTX 5070/4090 service authentication, TLS/CA, request and
session schemas, registered source IDs, limits, result retrieval, query/cancel/stop support,
RTSP behavior and real container-to-provider calls remain unverified. Production defaults to
disabled. These items must stay incomplete and capability-disabled until the provider owner
supplies and validates the real contract.

## Evidence

- `scope.json`: exact serial commits, ownership boundary and retained dependency checks.
- `migration.json`: V001/V002 checksums, repeat execution and live row-count guard.
- `java8-tests.json`, `build.json`: test/build/runtime compatibility receipts.
- `runtime.json`: authenticated stub image/video/stream/history and cross-owner flow.
- `disabled.json`: fail-closed mode with readable stored history/results.
- `direct-retirement.json`: direct-call rejection and retained table counts.
- `browser.json`: fresh-image browser verification and visible simulation labels.
- `docker-image-cleanup.json`: exact abandoned-image cleanup and preserved resources.

OpenSpec remains active and is not archived. `remote-video-streaming` task 7.3 stays open for
round 08, together with all real-provider work.
