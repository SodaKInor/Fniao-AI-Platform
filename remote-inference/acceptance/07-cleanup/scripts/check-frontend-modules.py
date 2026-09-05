#!/usr/bin/env python3
"""Verify the feature-first Vue AI layout, local imports and dynamic menu targets."""

import json
import re
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[6]
SRC = ROOT / "frontend-vue/src"
AI = SRC / "modules/ai"
EVIDENCE = Path(__file__).resolve().parents[1] / "group2-frontend-modules.actual.json"
FEATURES = ("asset", "capability", "image", "job", "legacy", "result", "stream", "video")
FORBIDDEN_FEATURES = {"audio", "chat", "training", "provider", "operations"}
OLD_DIRS = (SRC / "api/ai", SRC / "components/ai", SRC / "services/ai", SRC / "views/ai")
ALLOWED = {
    "asset": {"result"},
    "capability": {"result"},
    "image": {"asset", "capability", "result", "public"},
    "job": {"result", "video", "public"},
    "legacy": set(),
    "result": {"image"},
    "stream": {"capability", "result", "public"},
    "video": {"asset", "capability", "result", "stream", "public"},
    "public": {"asset", "capability", "job", "stream"},
}
IMPORT = re.compile(r"(?:import\s+[^;\n]*?\s+from|export\s+[^;\n]*?\s+from)\s*['\"]([^'\"]+)['\"]")
MENU_COMPONENT = re.compile(r"aiMenu\([^\n]+?['\"](modules/ai/[^'\"]+)['\"]")


def resolve(source, target):
    if target == "@/modules/ai":
        base = AI / "index"
    elif target.startswith("@/"):
        base = SRC / target[2:]
    elif target.startswith("."):
        base = source.parent / target
    else:
        return None
    candidates = (base, Path(str(base) + ".js"), Path(str(base) + ".vue"), base / "index.js")
    return next((candidate.resolve() for candidate in candidates if candidate.is_file()), None)


def feature_for(path):
    relative = path.relative_to(AI)
    return "public" if relative.parts == ("index.js",) else relative.parts[0]


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
    files = sorted(path for path in AI.rglob("*") if path.suffix in {".js", ".vue"})
    assert files, "No frontend AI module files"
    assert all(not any(path.is_file() for path in directory.rglob("*"))
               for directory in OLD_DIRS if directory.exists())
    roots = {path.relative_to(AI).parts[0] for path in files if path.name != "index.js"}
    assert roots == set(FEATURES), roots
    assert not roots & FORBIDDEN_FEATURES

    graph = defaultdict(set)
    edges = defaultdict(set)
    unresolved = []
    counts = defaultdict(int)
    for path in files:
        source_feature = feature_for(path)
        counts[source_feature] += 1
        source = path.read_text()
        limit = 350 if path.suffix == ".vue" else 300
        assert len(source.splitlines()) <= limit, path
        lowered = source.lower()
        for forbidden in ("providersessionid", "provider_source_id", "gpuurl", "rtspurl",
                          "streamcredential", "authorization: bearer", "http://", "https://"):
            assert forbidden not in lowered, (path, forbidden)
        for target in IMPORT.findall(source):
            resolved = resolve(path, target)
            if target.startswith("@/modules/ai") or target.startswith("."):
                if resolved is None:
                    unresolved.append({"source": str(path.relative_to(ROOT)), "target": target})
                    continue
                if resolved.is_relative_to(AI):
                    target_feature = feature_for(resolved)
                    graph[str(path.relative_to(AI))].add(str(resolved.relative_to(AI)))
                    if source_feature != target_feature:
                        edges[source_feature].add(target_feature)
                        assert target_feature in ALLOWED[source_feature], (path, target)

    navigation = (AI / "legacy/navigation.js").read_text()
    menu_targets = sorted(set(MENU_COMPONENT.findall(navigation)))
    assert len(menu_targets) == 6, menu_targets
    for target in menu_targets:
        assert (SRC / (target + ".vue")).is_file(), target
    disabled = (AI / "legacy/legacyEntries.js").read_text()
    assert "modules/ai/legacy/DisabledEntryPage" in disabled
    loader = (SRC / "utils/util.js").read_text()
    assert 'item.component.indexOf("modules/")===0' in loader

    cycles = strongly_connected(graph)
    assert not unresolved, unresolved
    assert not cycles, cycles
    result = {
        "status": "PASS",
        "scope": "frontend production AI modules",
        "files": len(files),
        "oldLayerDirectoriesWithFiles": [],
        "forbiddenFeatureRoots": [],
        "unresolvedLocalImports": [],
        "fileImportCycles": [],
        "dynamicMenuTargets": menu_targets,
        "features": [
            {"feature": feature, "files": counts[feature], "importsFeatures": sorted(edges[feature])}
            for feature in (*FEATURES, "public")
        ],
    }
    EVIDENCE.write_text(json.dumps(result, indent=2) + "\n")
    print(f"PASS: {len(files)} frontend files in {len(FEATURES)} feature roots; 6 menu targets resolve")


if __name__ == "__main__":
    main()
