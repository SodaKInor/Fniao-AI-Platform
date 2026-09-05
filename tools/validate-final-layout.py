#!/usr/bin/env python3
"""Fail-closed structural checks for the final Fniao AI Platform repository."""

import json
import re
import subprocess
from collections import defaultdict
from pathlib import Path


HERE = Path(__file__).resolve().parent
ROOT = Path(subprocess.check_output(
    ["git", "-C", str(HERE), "rev-parse", "--show-toplevel"], text=True
).strip())
BACKEND = ROOT / "apps/backend/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai"
FRONTEND = ROOT / "apps/frontend/src/modules/ai"
BACKEND_FEATURES = {
    "asset", "capability", "image", "job", "legacy",
    "operations", "provider", "result", "stream", "video",
}
FRONTEND_FEATURES = {
    "asset", "capability", "image", "job", "legacy", "result", "stream", "video",
}
BACKEND_ALLOWED = {
    "asset": {"image", "job", "video"},
    "capability": {"job"},
    "image": {"asset", "capability", "job", "result"},
    "job": {"asset", "capability", "image", "result", "video"},
    "legacy": {"job"},
    "operations": {"asset", "capability", "image", "job", "provider", "result", "stream", "video"},
    "provider": {"asset", "capability", "image", "job", "legacy", "result", "stream", "video"},
    "result": {"asset", "capability", "image", "job", "video"},
    "stream": {"asset", "capability", "job", "result"},
    "video": {"asset", "capability", "job", "result"},
}
PACKAGE = re.compile(r"^package\s+([\w.]+);", re.MULTILINE)
IMPORT = re.compile(r"^import\s+(?:static\s+)?([\w.]+)(?:\.\*)?;", re.MULTILINE)


def fail_if(condition, message, failures):
    if condition:
        failures.append(message)


def tracked_files():
    output = subprocess.check_output(["git", "-C", str(ROOT), "ls-files", "-z"])
    return [ROOT / item.decode() for item in output.split(b"\0") if item]


def strongly_connected(graph):
    index = 0
    stack, indices, low, active, found = [], {}, {}, set(), []

    def visit(node):
        nonlocal index
        indices[node] = low[node] = index
        index += 1
        stack.append(node)
        active.add(node)
        for target in graph.get(node, ()):
            if target not in indices:
                visit(target)
                low[node] = min(low[node], low[target])
            elif target in active:
                low[node] = min(low[node], indices[target])
        if low[node] == indices[node]:
            component = []
            while True:
                item = stack.pop()
                active.remove(item)
                component.append(item)
                if item == node:
                    break
            if len(component) > 1:
                found.append(sorted(component))

    for node in graph:
        if node not in indices:
            visit(node)
    return found


def check_backend(failures):
    files = sorted(BACKEND.rglob("*.java"))
    roots = {path.relative_to(BACKEND).parts[0] for path in files}
    fail_if(roots != BACKEND_FEATURES, f"backend feature roots changed: {sorted(roots)}", failures)
    classes, graph, violations = {}, defaultdict(set), []
    for path in files:
        source = path.read_text(errors="replace")
        match = PACKAGE.search(source)
        fail_if(match is None, f"missing Java package: {path.relative_to(ROOT)}", failures)
        if match:
            classes[match.group(1) + "." + path.stem] = (path, IMPORT.findall(source))
        fail_if(len(source.splitlines()) > 400, f"Java file exceeds 400 lines: {path.relative_to(ROOT)}", failures)
    for fqcn, (path, imports) in classes.items():
        source_feature = path.relative_to(BACKEND).parts[0]
        for target in imports:
            if target in classes:
                graph[fqcn].add(target)
            prefix = "org.jeecg.modules.ai."
            if target.startswith(prefix):
                target_feature = target[len(prefix):].split(".", 1)[0]
                if target_feature != source_feature and target_feature not in BACKEND_ALLOWED[source_feature]:
                    violations.append(f"{path.relative_to(ROOT)} -> {target}")
    fail_if(bool(violations), "backend dependency violations: " + "; ".join(violations[:8]), failures)
    cycles = strongly_connected(graph)
    fail_if(bool(cycles), f"backend class import cycles: {cycles[:3]}", failures)
    return len(files)


def check_frontend(failures):
    files = sorted(path for path in FRONTEND.rglob("*") if path.suffix in {".js", ".vue", ".ts"})
    roots = {path.relative_to(FRONTEND).parts[0] for path in files if path.name != "index.js"}
    fail_if(roots != FRONTEND_FEATURES, f"frontend feature roots changed: {sorted(roots)}", failures)
    forbidden = ("providersessionid", "gpuurl", "rtspurl", "authorization: bearer")
    for path in files:
        source = path.read_text(errors="replace")
        limit = 350 if path.suffix == ".vue" else 300
        fail_if(len(source.splitlines()) > limit, f"frontend file exceeds {limit} lines: {path.relative_to(ROOT)}", failures)
        lowered = source.lower()
        for marker in forbidden:
            fail_if(marker in lowered, f"browser module exposes provider detail: {path.relative_to(ROOT)} ({marker})", failures)
    return len(files)


def check_paths_and_secrets(files, failures):
    required = (
        "apps/backend", "apps/frontend", "database", "remote-inference",
        "deploy/remote-inference", "docs/remote-inference", "openspec", "tools",
    )
    for relative in required:
        fail_if(not (ROOT / relative).is_dir(), f"missing required directory: {relative}", failures)
    active = []
    for path in files:
        relative = path.relative_to(ROOT).as_posix()
        if (relative in {"README.md", "AGENTS.md", ".dockerignore"}
                or relative.startswith("tools/")
                or (relative.startswith("deploy/") and relative != "deploy/STATUS.md")
                or relative.startswith("apps/backend/")
                or relative.startswith("apps/frontend/")
                or relative in {"docs/remote-inference/README.md", "docs/remote-inference/ARCHITECTURE.md"}):
            active.append(path)
    retired = re.compile(
        r"/Users/[^\s]+/Documents/ChatGPT/(?:WGAI|Fniao-AI-Platform-worktrees)"
        r"|(?:^|[\s'\"`(])(?:backend-github|frontend-vue)/"
        r"|/workspace/(?:backend-github|frontend-vue)"
        r"|apps/backend/(?:development/remote-inference|integrations/ai-contracts)"
    )
    for path in active:
        if path == Path(__file__).resolve():
            continue
        if path.is_file() and path.stat().st_size < 2_000_000:
            text = path.read_text(errors="replace")
            fail_if(bool(retired.search(text)), f"active old path in {path.relative_to(ROOT)}", failures)

    allowed_private = {"database/private/README.md", "database/private/.gitignore"}
    secret_suffixes = {".pem", ".key", ".jks", ".p12", ".pfx", ".onnx", ".pt", ".pth", ".weights", ".engine", ".safetensors"}
    for path in files:
        relative = path.relative_to(ROOT).as_posix()
        fail_if(relative.startswith("database/private/") and relative not in allowed_private,
                f"tracked private database input: {relative}", failures)
        fail_if(path.suffix.lower() in secret_suffixes, f"tracked secret/model artifact: {relative}", failures)
        fail_if(path.name == ".env" or (".env." in path.name and not path.name.endswith(".example")),
                f"tracked environment secret: {relative}", failures)


def check_compose(failures):
    compose = (ROOT / "deploy/docker-compose.yml").read_text()
    fail_if(bool(re.search(r"^\s{2}remote-ai-stub:", compose, re.MULTILINE)), "default Compose declares stub service", failures)
    fail_if("WGAI_INFERENCE_MODE: ${WGAI_INFERENCE_MODE:-disabled}" not in compose,
            "default Compose does not fail closed to disabled", failures)
    fail_if("WGAI_INFERENCE_DEVELOPMENT_STUB: ${WGAI_INFERENCE_DEVELOPMENT_STUB:-false}" not in compose,
            "default Compose does not disable stub", failures)
    fail_if("VUE_APP_API_BASE_URL: /jeecg-boot" not in compose,
            "browser API is not constrained to the business backend", failures)


def main():
    failures = []
    files = tracked_files()
    backend_count = check_backend(failures)
    frontend_count = check_frontend(failures)
    check_paths_and_secrets(files, failures)
    check_compose(failures)
    result = {
        "status": "FAIL" if failures else "PASS",
        "repositoryRoot": str(ROOT),
        "backendAiJavaFiles": backend_count,
        "frontendAiFiles": frontend_count,
        "trackedFiles": len(files),
        "failures": failures,
    }
    print(json.dumps(result, indent=2, ensure_ascii=False))
    raise SystemExit(1 if failures else 0)


if __name__ == "__main__":
    main()
