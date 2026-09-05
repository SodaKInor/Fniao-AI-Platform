# Development provider stub

This process implements the frozen provider drafts over real HTTP for local development. Every response carries
`X-WGAI-Simulated: true`; result bodies also expose `simulated` or `provider_version=stub-simulated-v1` where the
strict draft decoder allows it. It is not an algorithm, GPU service, production fallback, or evidence that the RTX
5070/4090 contracts work.

Run `npm test`, then `WGAI_STUB_TOKEN=... npm start`. The default token is a public development value and must not be
reused as a real credential. Requests select deterministic behavior with `X-WGAI-Stub-Scenario`; supported values are
listed in `contract/provider-stub.v1.json`.

The service uses only Node standard libraries, fixed synthetic PNG bytes and in-memory session/request state. It never
reads the WGAI database or upload storage and has no GPU, model, RTSP, CUDA, OpenCV, ONNX or legacy algorithm dependency.
The explicit development Compose override is the only supported integration path; production configuration does not
start or reference this service.
