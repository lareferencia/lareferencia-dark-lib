#!/usr/bin/env python3
"""Small in-memory mock of the dARK v1 API used by DarkMinterClient."""

import hashlib
from datetime import datetime, timezone

from flask import Flask, jsonify, request

app = Flask(__name__)
ark_registry = {}


def error(detail, status=400, code="VALIDATION_ERROR", retryable=False):
    response = jsonify({"detail": detail})
    response.status_code = status
    response.headers["X-DARK-Error-Code"] = code
    response.headers["X-DARK-Retryable"] = str(retryable).lower()
    return response


def validate_authority(payload):
    authority = (payload or {}).get("authority_id")
    header = request.headers.get("X-Authority-Id")
    if not authority or header != authority:
        return error("X-Authority-Id must match authority_id", 403, "AUTHORIZATION_FAILED")
    return None


def response_for(record):
    return {
        "ark": record["ark"],
        "state": record["state"],
        "target": record.get("target"),
        "metadata_schema": record.get("metadata_schema"),
        "minimal_metadata": record.get("minimal_metadata"),
        "client_item_id": record.get("client_item_id"),
    }


@app.post("/api/v1/arks/batch")
def reserve_batch():
    payload = request.get_json(silent=True) or {}
    authority_error = validate_authority(payload)
    if authority_error:
        return authority_error
    naan = str(payload.get("naan", "")).strip()
    if not naan:
        return error("naan is required")

    results = []
    errors = []
    for index, item in enumerate(payload.get("items") or []):
        client_item_id = str((item or {}).get("client_item_id", "")).strip()
        if not client_item_id:
            errors.append({"index": index, "client_item_id": None, "error": "client_item_id is required"})
            continue
        suffix = hashlib.sha256(client_item_id.encode()).hexdigest()[:12]
        ark = f"ark:/{naan}/{suffix}"
        record = ark_registry.setdefault(ark, {
            "ark": ark,
            "state": "R",
            "client_item_id": client_item_id,
            "reserved_at": datetime.now(timezone.utc).isoformat(),
        })
        results.append(response_for(record))
    return jsonify({"results": results, "errors": errors})


@app.put("/api/v1/arks/<path:ark>")
def stage_ark(ark):
    ark = ark if ark.startswith("ark:/") else f"ark:/{ark}"
    record = ark_registry.get(ark)
    if record is None:
        return error("ARK not found", 404, "ARK_NOT_FOUND")
    payload = request.get_json(silent=True) or {}
    authority_error = validate_authority(payload)
    if authority_error:
        return authority_error
    minimal = payload.get("minimal_metadata") or {}
    if not minimal.get("title") or not minimal.get("authors") or minimal.get("year") is None:
        return error("minimal_metadata requires title, authors and year")

    record.update({
        "state": "D",
        "target": payload.get("target"),
        "minimal_metadata": minimal,
        "original_metadata": payload.get("original_metadata"),
        "metadata_schema": payload.get("metadata_schema"),
        "metadata_media_type": payload.get("metadata_media_type"),
        "staged_at": datetime.now(timezone.utc).isoformat(),
    })
    return jsonify(response_for(record))


@app.get("/api/v1/arks/<path:ark>")
def get_ark(ark):
    ark = ark if ark.startswith("ark:/") else f"ark:/{ark}"
    record = ark_registry.get(ark)
    if record is None:
        return error("ARK not found", 404, "ARK_NOT_FOUND")
    return jsonify(response_for(record))


@app.get("/status")
def status():
    return jsonify({"status": "ok", "registered_arks": len(ark_registry)})


@app.get("/registry")
def registry():
    return jsonify(ark_registry)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
