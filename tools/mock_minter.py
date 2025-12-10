#!/usr/bin/env python3
"""
DARK Minter Mock Service

A simple Flask-based mock service that simulates the DARK minter API
for testing and demo purposes.

Endpoints:
- POST /load   - Register new PIDs (returns minted ARKs)
- POST /update - Update URLs for existing PIDs

Usage:
    pip install flask
    python mock_minter.py

The service runs on http://localhost:5000 by default.
"""

import hashlib
import time
import uuid
from datetime import datetime
from flask import Flask, request, jsonify

app = Flask(__name__)

# In-memory storage for registered PIDs
pid_registry = {}


def generate_ark(oai_id: str) -> str:
    """Generate a deterministic ARK from an OAI identifier."""
    hash_bytes = hashlib.sha256(oai_id.encode()).hexdigest()[:12]
    return f"99999/{hash_bytes}"


def generate_ark_hash(ark: str) -> str:
    """Generate a mock ARK hash."""
    return hashlib.sha256(ark.encode()).hexdigest()[:16]


def generate_tx_receipt() -> str:
    """Generate a mock transaction receipt."""
    return f"0x{uuid.uuid4().hex[:40]}"


@app.route('/load', methods=['POST'])
def register_pids():
    """
    Register new PIDs endpoint.
    
    Request:
        {
            "dnam_pk": "private-key",
            "items": [
                {"oai_id": "oai:repo:123", "url": "https://example.com/123"}
            ]
        }
    
    Response:
        {
            "ingested_pids": [
                {
                    "ark": "99999/abc123",
                    "ark_hash": "hash...",
                    "oai_id": "oai:repo:123",
                    "ark_url": "https://ark.dark-pid.net/99999/abc123",
                    "requested_url": "https://example.com/123",
                    "tx_recipt": "0x..."
                }
            ],
            "load_time": "0.5s",
            "verify_time": "0.1s",
            "wallet_addr": "0x1234..."
        }
    """
    start_time = time.time()
    
    data = request.get_json()
    items = data.get('items', [])
    private_key = data.get('dnam_pk', '')
    
    print(f"[REGISTER] Received {len(items)} items")
    
    ingested_pids = []
    
    for item in items:
        oai_id = item.get('oai_id')
        url = item.get('url')
        
        if not oai_id:
            continue
            
        ark = generate_ark(oai_id)
        ark_hash = generate_ark_hash(ark)
        ark_url = f"https://ark.dark-pid.net/{ark}"
        
        # Store in registry
        pid_registry[f"ark:/{ark}"] = {
            'oai_id': oai_id,
            'url': url,
            'registered_at': datetime.now().isoformat()
        }
        
        ingested_pids.append({
            'ark': ark,
            'ark_hash': ark_hash,
            'oai_id': oai_id,
            'ark_url': ark_url,
            'requested_url': url,
            'tx_recipt': generate_tx_receipt()
        })
        
        print(f"  -> Minted ark:/{ark} for {oai_id}")
    
    elapsed = time.time() - start_time
    
    response = {
        'ingested_pids': ingested_pids,
        'load_time': f"{elapsed:.3f}s",
        'verify_time': f"{elapsed * 0.2:.3f}s",
        'wallet_addr': f"0x{hashlib.sha256(private_key.encode()).hexdigest()[:40]}"
    }
    
    return jsonify(response)


@app.route('/update', methods=['POST'])
def update_urls():
    """
    Update URLs endpoint.
    
    Request:
        {
            "dnam_pk": "private-key",
            "items": [
                {"dark_id": "ark:/99999/abc123", "url": "https://new-url.com/123"}
            ]
        }
    
    Response:
        {
            "updated_pids": [
                {
                    "ark_hash": "hash...",
                    "dark_id": "ark:/99999/abc123",
                    "previous_url": "https://old-url.com/123",
                    "tx_recipt": "0x...",
                    "update_url": "https://new-url.com/123"
                }
            ],
            "not_updated_pids": []
        }
    """
    data = request.get_json()
    items = data.get('items', [])
    
    print(f"[UPDATE] Received {len(items)} items")
    
    updated_pids = []
    not_updated_pids = []
    
    for item in items:
        dark_id = item.get('dark_id')
        new_url = item.get('url')
        
        if not dark_id:
            continue
        
        if dark_id in pid_registry:
            previous_url = pid_registry[dark_id].get('url', '')
            pid_registry[dark_id]['url'] = new_url
            pid_registry[dark_id]['updated_at'] = datetime.now().isoformat()
            
            updated_pids.append({
                'ark_hash': generate_ark_hash(dark_id),
                'dark_id': dark_id,
                'previous_url': previous_url,
                'tx_recipt': generate_tx_receipt(),
                'update_url': new_url
            })
            
            print(f"  -> Updated {dark_id}: {previous_url} -> {new_url}")
        else:
            # For demo purposes, accept unknown PIDs too
            pid_registry[dark_id] = {
                'url': new_url,
                'registered_at': datetime.now().isoformat()
            }
            
            updated_pids.append({
                'ark_hash': generate_ark_hash(dark_id),
                'dark_id': dark_id,
                'previous_url': '',
                'tx_recipt': generate_tx_receipt(),
                'update_url': new_url
            })
            
            print(f"  -> Created and updated {dark_id}")
    
    response = {
        'updated_pids': updated_pids,
        'not_updated_pids': not_updated_pids
    }
    
    return jsonify(response)


@app.route('/status', methods=['GET'])
def status():
    """Health check and registry status."""
    return jsonify({
        'status': 'ok',
        'registered_pids': len(pid_registry),
        'timestamp': datetime.now().isoformat()
    })


@app.route('/registry', methods=['GET'])
def list_registry():
    """List all registered PIDs (for debugging)."""
    return jsonify(pid_registry)


if __name__ == '__main__':
    print("=" * 60)
    print("DARK Minter Mock Service")
    print("=" * 60)
    print("Endpoints:")
    print("  POST /load   - Register new PIDs")
    print("  POST /update - Update URLs")
    print("  GET  /status - Health check")
    print("  GET  /registry - List all PIDs")
    print("=" * 60)
    
    app.run(host='0.0.0.0', port=5000, debug=True)
