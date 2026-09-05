#!/usr/bin/env python3
"""Validate the canonical OpenAPI documents and every synthetic fixture."""
import copy
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import struct
import zlib


ROOT = Path(__file__).resolve().parent
CONTRACTS = ROOT / "contracts"
FIXTURES = ROOT / "fixtures"


def resolve(schema, document):
    schema = copy.deepcopy(schema)
    if "$ref" in schema:
        parts = schema.pop("$ref").split("/")
        assert parts[:3] == ["#", "components", "schemas"] and len(parts) == 4
        target = resolve(document["components"]["schemas"][parts[3]], document)
        target.update(schema)
        schema = target
    for key, value in list(schema.items()):
        if isinstance(value, dict):
            schema[key] = resolve(value, document)
        elif isinstance(value, list):
            schema[key] = [resolve(item, document) if isinstance(item, dict) else item for item in value]
    if schema.pop("nullable", False):
        schema["type"] = [schema["type"], "null"]
    return schema


def validate_instance(value, schema, path="$", errors=None):
    """Validate the OpenAPI schema subset used by this frozen contract set."""
    errors = [] if errors is None else errors
    types = schema.get("type")
    if types:
        types = [types] if isinstance(types, str) else types
        matches = {
            "null": value is None,
            "object": isinstance(value, dict),
            "array": isinstance(value, list),
            "string": isinstance(value, str),
            "boolean": isinstance(value, bool),
            "integer": isinstance(value, int) and not isinstance(value, bool),
            "number": isinstance(value, (int, float)) and not isinstance(value, bool),
        }
        if not any(matches.get(kind, False) for kind in types):
            errors.append(f"{path}: expected {types}")
            return errors
    if "enum" in schema and value not in schema["enum"]:
        errors.append(f"{path}: value is outside enum")
    if isinstance(value, dict):
        properties = schema.get("properties", {})
        for name in schema.get("required", []):
            if name not in value:
                errors.append(f"{path}.{name}: required")
        for name, child in value.items():
            if name in properties:
                validate_instance(child, properties[name], f"{path}.{name}", errors)
            elif schema.get("additionalProperties") is False:
                errors.append(f"{path}.{name}: additional property")
            elif isinstance(schema.get("additionalProperties"), dict):
                validate_instance(child, schema["additionalProperties"], f"{path}.{name}", errors)
    if isinstance(value, list):
        if len(value) < schema.get("minItems", 0):
            errors.append(f"{path}: too few items")
        if "maxItems" in schema and len(value) > schema["maxItems"]:
            errors.append(f"{path}: too many items")
        if "items" in schema:
            for index, child in enumerate(value):
                validate_instance(child, schema["items"], f"{path}[{index}]", errors)
    if isinstance(value, str):
        if len(value) < schema.get("minLength", 0):
            errors.append(f"{path}: shorter than minLength")
        if "maxLength" in schema and len(value) > schema["maxLength"]:
            errors.append(f"{path}: longer than maxLength")
        if "pattern" in schema and re.search(schema["pattern"], value) is None:
            errors.append(f"{path}: pattern mismatch")
        if schema.get("format") == "date-time":
            try:
                datetime.fromisoformat(value.replace("Z", "+00:00"))
            except ValueError:
                errors.append(f"{path}: invalid date-time")
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        if "minimum" in schema and value < schema["minimum"]:
            errors.append(f"{path}: below minimum")
        if "maximum" in schema and value > schema["maximum"]:
            errors.append(f"{path}: above maximum")
    return errors


def validate_openapi(document):
    assert document["openapi"].startswith("3.0.")
    assert document["info"]["title"] and document["info"]["version"]
    assert document["paths"] and document["components"]["schemas"]
    for route, methods in document["paths"].items():
        assert route.startswith("/")
        for method, operation in methods.items():
            if method in {"get", "post", "put", "patch", "delete"}:
                assert operation["responses"]
    for schema in document["components"]["schemas"].values():
        resolve(schema, document)


def validate_image_job(job):
    assert job["simulated"] is True
    if job["state"] == "SUCCEEDED":
        assert "error" not in job and "result" in job
        result = job["result"]
        assert result["simulated"] is True
        assert len(result["data"]["detections"]) <= job["parameters"]["maxDetections"]
        for detection in result["data"]["detections"]:
            box = detection["box"]
            assert box["x"] + box["width"] <= 1
            assert box["y"] + box["height"] <= 1
        if not job["parameters"]["annotate"]:
            assert not result["artifacts"]
    else:
        assert "result" not in job
    if job["state"] in ["FAILED", "UNKNOWN"]:
        assert "error" in job


def validate_video_job(job):
    if job.get("jobType") != "VIDEO_FILE_ANALYSIS":
        return
    assert "parameters" not in job and "result" not in job
    assert "videoParameters" in job
    if job["state"] == "SUCCEEDED":
        result = job["videoResult"]
        assert result["resultType"] == "VIDEO_TIMELINE"
        offsets = [event["offsetMillis"] for event in result["events"]]
        assert offsets == sorted(offsets)
        assert len(offsets) <= job["videoParameters"]["maxEvents"]
        snapshot_ids = {asset["assetId"] for asset in result["snapshots"]}
        assert all(event.get("snapshotAssetId") in snapshot_ids
                   for event in result["events"] if event.get("snapshotAssetId"))
    else:
        assert "videoResult" not in job


def verify_png(path):
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    position, compressed, dimensions = 8, b"", None
    while position < len(data):
        length = struct.unpack("!I", data[position:position + 4])[0]
        kind = data[position + 4:position + 8]
        payload = data[position + 8:position + 8 + length]
        checksum = struct.unpack("!I", data[position + 8 + length:position + 12 + length])[0]
        assert zlib.crc32(kind + payload) & 0xffffffff == checksum
        if kind == b"IHDR":
            dimensions = struct.unpack("!2I", payload[:8])
        if kind == b"IDAT":
            compressed += payload
        position += 12 + length
    assert dimensions == (16, 16)
    assert len(zlib.decompress(compressed)) == 16 * (1 + 16 * 3)


def load_fixture(name):
    return json.loads((FIXTURES / name).read_text())


def main():
    docs = {name: json.loads((CONTRACTS / path).read_text()) for name, path in {
        "business": "business/v1/business.openapi.json",
        "provider": "provider/v0.1.openapi.json",
        "businessV11": "business/v1.1/business.openapi.json",
        "providerV02": "provider/v0.2.openapi.json",
    }.items()}
    for document in docs.values():
        validate_openapi(document)

    manifest = load_fixture("manifest.json")
    assert manifest["simulated"] is True
    for name, case in manifest["cases"].items():
        assert case["simulated"] is True
        value = load_fixture(name)
        document = docs[case["contract"]]
        schema = resolve(document["components"]["schemas"][case["schema"]], document)
        errors = validate_instance(value, schema)
        assert (not errors) == case["valid"], (name, errors)
        if case["valid"] and case["contract"] == "business":
            compatible = docs["businessV11"]["components"]["schemas"].get(case["schema"])
            if compatible is not None:
                compatible_errors = validate_instance(value, resolve(compatible, docs["businessV11"]))
                assert not compatible_errors, (name, compatible_errors)
        if case["valid"] and case["schema"] == "JobResponse":
            if case["contract"] == "business":
                validate_image_job(value["result"])
            else:
                validate_video_job(value["result"])

    for file_info in manifest["files"]:
        path = FIXTURES / file_info["path"]
        assert path.stat().st_size == file_info["sizeBytes"]
        assert hashlib.sha256(path.read_bytes()).hexdigest() == file_info["sha256"]
        verify_png(path)

    accepted, success = load_fixture("accepted.json"), load_fixture("success.json")
    assert accepted["code"] == 202 and success["code"] == 200
    assert accepted["result"]["requestId"] == success["result"]["requestId"]
    metadata, request = load_fixture("provider-metadata.json"), load_fixture("submit.json")
    assert metadata["request_id"] == success["result"]["requestId"]
    assert metadata["parameters"]["threshold"] == request["parameters"]["threshold"]
    assert metadata["parameters"]["max_detections"] == request["parameters"]["maxDetections"]
    artifact = success["result"]["result"]["artifacts"][0]
    provider_artifact = load_fixture("provider-success.json")["artifacts"][0]
    expected = manifest["files"][1]
    assert artifact["sha256"] == provider_artifact["sha256"] == expected["sha256"]
    assert artifact["sizeBytes"] == provider_artifact["size_bytes"] == expected["sizeBytes"]
    assert docs["provider"]["x-features"] == {"query": False, "cancel": False, "deduplication": False}
    assert list(docs["provider"]["paths"]) == ["/infer"]

    video_request = load_fixture("video-submit.json")
    video_success = load_fixture("video-success.json")["result"]
    assert video_success["videoParameters"] == video_request["parameters"]
    assert "annotatedVideo" not in video_success["videoResult"]
    assert load_fixture("video-empty.json")["result"]["videoResult"] == {
        "resultType": "VIDEO_TIMELINE", "simulated": True, "events": [], "snapshots": []}
    source = load_fixture("stream-sources.json")["result"][0]
    assert source["available"] is False and source["unavailableReason"]
    stopped = load_fixture("stream-stop-unknown.json")["result"]
    assert stopped["state"] != "STOPPED"
    assert stopped["unknownReason"] == "STOP_CONFIRMATION_UNKNOWN"
    assert docs["businessV11"]["info"]["version"] == "1.1.0"
    assert docs["providerV02"]["x-confirmation-status"] == "UNCONFIRMED"
    assert all(value is False for value in docs["providerV02"]["x-features"].values())
    stub_contract = json.loads((CONTRACTS / "provider/provider-stub.v1.json").read_text())
    assert stub_contract["simulated"] is True
    assert stub_contract["businessContractVersion"] == "1.1.0"

    print("PASS: 4 OpenAPI documents,", len(manifest["cases"]),
          "positive/negative JSON cases, 2 PNG fixtures, and simulated stub contract")


if __name__ == "__main__":
    main()
