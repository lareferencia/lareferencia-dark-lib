# DARK Minter Mock Service

A simple Python Flask service that simulates the DARK minter API for testing and demos.

## Requirements

```bash
pip install flask
```

## Usage

```bash
cd tools
python mock_minter.py
```

The service runs on `http://localhost:5000`.

## Configuration

Set `dark.minter.url=http://localhost:5000/` in your Spring application properties.

## Endpoints

### POST /load - Register new PIDs

```bash
curl -X POST http://localhost:5000/load \
  -H "Content-Type: application/json" \
  -d '{
    "dnam_pk": "test-key",
    "items": [
      {"oai_id": "oai:repo:123", "url": "https://example.com/123"}
    ]
  }'
```

### POST /update - Update URLs

```bash
curl -X POST http://localhost:5000/update \
  -H "Content-Type: application/json" \
  -d '{
    "dnam_pk": "test-key",
    "items": [
      {"dark_id": "ark:/99999/abc123", "url": "https://new-url.com/123"}
    ]
  }'
```

### GET /status - Health check

```bash
curl http://localhost:5000/status
```

### GET /registry - List all registered PIDs

```bash
curl http://localhost:5000/registry
```
