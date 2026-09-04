#!/usr/bin/env python3
"""Verify 07 ancestry, ownership and the user-requested dependency retention boundary."""

import json
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[7]
OUT = Path(__file__).resolve().parents[1] / "scope.json"
BASE = "722aad3fa11f7075576b760ab3d6deae83cd1480"
DELIVERY = "46260b93c98c7403bdf3263ea475b281804bae04"
COMMITS = [
    "380e76ed55e8ba7e8918e0ff848fd0d0e0e57607",
    "c2ae8ed1d0f251c5b8b0cadc44624654101bd8f5",
    "df1d6064648343bb3df97e615ea5aadfa81a490c",
    DELIVERY,
]


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def main():
    assert git("rev-parse", "--show-toplevel") == str(ROOT)
    assert git("rev-parse", "HEAD") == DELIVERY
    subprocess.check_call(["git", "merge-base", "--is-ancestor", BASE, DELIVERY], cwd=ROOT)
    actual = git("rev-list", "--reverse", BASE + ".." + DELIVERY).splitlines()
    assert actual == COMMITS, actual
    changed = git("diff", "--name-only", BASE, DELIVERY).splitlines()
    assert not any(name.startswith("backend-master/") for name in changed)
    dependency_files = [
        "backend-github/pom.xml",
        "backend-github/jeecg-module-system/jeecg-system-biz/pom.xml",
        "backend-github/jeecg-module-system/jeecg-system-start/pom.xml",
        "frontend-vue/package.json",
        "frontend-vue/package-lock.json",
        "deploy/backend/install-private-jars.sh",
        "deploy/backend/rebuild-opencv-java8.sh",
    ]
    assert not git("diff", "--name-only", BASE, DELIVERY, "--", *dependency_files)
    result = {
        "status": "PASS",
        "base": BASE,
        "delivery": DELIVERY,
        "serialCommits": COMMITS,
        "changedPathCount": len(changed),
        "backendMasterUnchanged": True,
        "dependencyAndBuildScriptsRetainedUnchanged": True,
        "noMajorUpgrade": True,
        "realProviderValidated": False,
    }
    OUT.write_text(json.dumps(result, indent=2) + "\n")
    print("PASS: 07 ancestry, ownership and retained dependency boundary verified")


if __name__ == "__main__":
    main()
