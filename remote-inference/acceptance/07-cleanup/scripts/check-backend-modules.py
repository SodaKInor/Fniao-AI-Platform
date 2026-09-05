#!/usr/bin/env python3
"""Verify the feature-first backend layout and emit a reproducible dependency inventory."""

import json
import re
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[6]
AI = ROOT / "backend-github/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/ai"
EVIDENCE = Path(__file__).resolve().parents[1] / "group2-backend-modules.actual.json"
FEATURES = (
    "asset", "capability", "image", "job", "legacy",
    "operations", "provider", "result", "stream", "video",
)
OLD_ROOTS = {"api", "application", "client", "config", "domain", "persistence", "port", "storage"}
FORBIDDEN_EMPTY_FEATURES = {"audio", "chat", "training"}

# This matrix records intentional source dependencies. API/controller composition and the bounded
# job fault/state vocabulary are allowed seams; executable provider adapters remain one-way.
ALLOWED = {
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


def strongly_connected(graph):
    index = 0
    stack = []
    indices = {}
    low = {}
    on_stack = set()
    found = []

    def visit(node):
        nonlocal index
        indices[node] = low[node] = index
        index += 1
        stack.append(node)
        on_stack.add(node)
        for target in graph.get(node, ()):
            if target not in indices:
                visit(target)
                low[node] = min(low[node], low[target])
            elif target in on_stack:
                low[node] = min(low[node], indices[target])
        if low[node] == indices[node]:
            component = []
            while True:
                item = stack.pop()
                on_stack.remove(item)
                component.append(item)
                if item == node:
                    break
            if len(component) > 1:
                found.append(sorted(component))

    for node in graph:
        if node not in indices:
            visit(node)
    return found


def main():
    files = sorted(AI.rglob("*.java"))
    assert files, "No AI Java sources found"
    assert not (set(FEATURES) & FORBIDDEN_EMPTY_FEATURES)

    classes = {}
    records = []
    counts = defaultdict(int)
    for path in files:
        relative = path.relative_to(AI)
        feature = relative.parts[0]
        assert feature in FEATURES, f"old or unknown AI package root: {relative}"
        assert feature not in OLD_ROOTS, f"old layer-first root remains: {relative}"
        source = path.read_text()
        package_match = PACKAGE.search(source)
        assert package_match, f"missing package: {relative}"
        fqcn = package_match.group(1) + "." + path.stem
        imports = sorted(set(IMPORT.findall(source)))
        classes[fqcn] = {"feature": feature, "path": str(relative), "imports": imports}
        counts[feature] += 1

    class_graph = defaultdict(set)
    module_edges = defaultdict(set)
    violations = []
    for fqcn, record in classes.items():
        source_feature = record["feature"]
        for imported in record["imports"]:
            if imported in classes:
                class_graph[fqcn].add(imported)
            prefix = "org.jeecg.modules.ai."
            if not imported.startswith(prefix):
                continue
            target_feature = imported[len(prefix):].split(".", 1)[0]
            if target_feature == source_feature:
                continue
            module_edges[source_feature].add(target_feature)
            if target_feature not in ALLOWED[source_feature]:
                violations.append({"source": record["path"], "target": imported})

    provider_reverse = [
        {"source": record["path"], "target": imported}
        for record in classes.values()
        if record["feature"] not in {"provider", "operations"}
        for imported in record["imports"]
        if imported.startswith("org.jeecg.modules.ai.provider.")
    ]
    stream_job_reverse = [
        {"source": record["path"], "target": imported}
        for record in classes.values()
        if record["feature"] == "job"
        for imported in record["imports"]
        if imported.startswith("org.jeecg.modules.ai.stream.")
    ]
    class_cycles = strongly_connected(class_graph)

    assert all(counts[name] > 0 for name in FEATURES), counts
    assert not violations, violations
    assert not provider_reverse, provider_reverse
    assert not stream_job_reverse, stream_job_reverse
    assert not class_cycles, class_cycles

    for feature in FEATURES:
        records.append({
            "feature": feature,
            "javaFiles": counts[feature],
            "importsFeatures": sorted(module_edges[feature]),
            "allowedImports": sorted(ALLOWED[feature]),
        })
    result = {
        "status": "PASS",
        "scope": "backend production AI Java sources",
        "javaFiles": len(files),
        "oldLayerFirstRoots": [],
        "forbiddenEmptyFeatures": [],
        "classImportCycles": [],
        "providerReverseImports": [],
        "jobToStreamImports": [],
        "features": records,
    }
    EVIDENCE.write_text(json.dumps(result, indent=2) + "\n")
    print(f"PASS: {len(files)} Java files in {len(FEATURES)} feature roots; no class import cycle")


if __name__ == "__main__":
    main()
