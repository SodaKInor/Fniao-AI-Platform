#!/usr/bin/env python3
"""Prepare ignored Java 8 test inputs from a baseline runtime and pinned existing test dependencies."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[6]
VALIDATION = ROOT / 'backend-github/jeecg-module-system/jeecg-system-biz/target/04a-validation'
DEPENDENCIES = {'junit/junit/4.13.2/junit-4.13.2.jar': '8e495b634469d64fb8acfa3495a065cbacc8a0fff55ce1e31007be4c16dc57d3', 'org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar': '66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9', 'org/springframework/spring-test/5.3.18/spring-test-5.3.18.jar': 'fe85fae5b232508ce3bed69307f092ca97142072555730b0d8a60278ef5a968f', 'com/jayway/jsonpath/json-path/2.6.0/json-path-2.6.0.jar': 'c175df1eb0cb14dc5adc9f19a1566c7d16d7e419c48dc1771aec8d1852790f4b', 'net/minidev/json-smart/2.4.8/json-smart-2.4.8.jar': '174a9ad578b56644e62b3965d8bf94ac3a76e707c6343b8abac9d3671438b4b2', 'net/minidev/accessors-smart/2.4.8/accessors-smart-2.4.8.jar': '7dd705aa1ac0e030f8ee2624e8e77239ae1eef6ccc2621c0b8c189866ee1c42c', 'org/ow2/asm/asm/9.1/asm-9.1.jar': 'cda4de455fab48ff0bcb7c48b4639447d4de859a7afc30a094a986f0936beba2'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime-image', default='wgai-integration-backend:round1-5a55ca5')
    args = parser.parse_args()
    assert ROOT.name == 'code' and ROOT.parent.name == '04a-assets-jobs'
    VALIDATION.mkdir(parents=True, exist_ok=True)
    image = subprocess.check_output(['docker', 'image', 'inspect', '--format', '{{.Id}}', args.runtime_image], text=True).strip()
    container = subprocess.check_output(['docker', 'create', image], text=True).strip()
    try:
        subprocess.run(['docker', 'cp', container + ':/app/app.jar', str(VALIDATION / 'baseline.jar')], check=True)
    finally:
        subprocess.run(['docker', 'rm', container], check=True, stdout=subprocess.DEVNULL)
    lib = VALIDATION / 'lib'
    lib.mkdir(exist_ok=True)
    with zipfile.ZipFile(VALIDATION / 'baseline.jar') as archive:
        for name in archive.namelist():
            if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                (lib / Path(name).name).write_bytes(archive.read(name))
    for coordinate, expected in DEPENDENCIES.items():
        destination = lib / coordinate.split('/')[-1]
        if not destination.exists():
            urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/' + coordinate, destination)
        assert hashlib.sha256(destination.read_bytes()).hexdigest() == expected, 'Test dependency checksum mismatch'
    print('Java test inputs ready; source runtime:', image)


if __name__ == '__main__':
    main()
