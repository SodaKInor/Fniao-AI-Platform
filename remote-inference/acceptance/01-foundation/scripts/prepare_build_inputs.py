#!/usr/bin/env python3
"""Restore verified private build inputs into this worktree's ignored paths only."""
import hashlib
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[6]
EVIDENCE = Path(__file__).resolve().parents[1]
ARCHIVE = Path('/Users/twowt88/Documents/ChatGPT/WGAI/wgai-github.zip')
RESOURCE = 'backend-github/jeecg-module-system/jeecg-system-start/src/main/resources/'
HASHES = {
    'asrt_sdk_maven-1.0-alpha1.jar': '10b56560251cec9bac5a92a6ab058b84ee3b43c438ac9cab5a257fbf96981330',
    'opencv-4.10.0.jar': '794e79dc1b77bc849d60081f0fad069403a01abb1d1255e984bdd8b9e1bb2d81',
}


def install(relative, data):
    target = ROOT / relative
    subprocess.run(['git', '-C', str(ROOT), 'check-ignore', '-q', relative], check=True)
    if target.exists():
        assert target.read_bytes() == data, 'Existing private input differs; not overwritten'
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    print('Verified ignored build input:', relative)


def main():
    actual = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', '--show-toplevel']).decode().strip()
    assert actual == str(ROOT) and ROOT.name == 'code' and ROOT.parent.parent.name == 'WGAI-parallel'
    assert hashlib.sha256(ARCHIVE.read_bytes()).hexdigest() == '350e6da553ded3966313424a9638537976b6a4a2911f5754432b34b814ecef0c'
    with zipfile.ZipFile(ARCHIVE) as source:
        for filename, expected in HASHES.items():
            data = source.read('wgai-github/' + RESOURCE.removeprefix('backend-github/') + filename)
            assert hashlib.sha256(data).hexdigest() == expected
            install(RESOURCE + filename, data)
    install(RESOURCE + 'jeecg/jeecg_database.properties',
            (EVIDENCE / 'templates/jeecg_database.properties.example').read_bytes())


if __name__ == '__main__':
    main()
