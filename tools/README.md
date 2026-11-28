# dARK v1 mock service

This Flask service implements the endpoints used by `DarkMinterClient`.

```bash
pip install flask
python mock_minter.py
```

Configure the harvester with:

```properties
dark.minter.base-url=http://localhost:5000
dark.authority-id=test-authority
```

Reserve an ARK:

```bash
curl -X POST http://localhost:5000/api/v1/arks/batch \
  -H 'Content-Type: application/json' \
  -H 'X-Authority-Id: test-authority' \
  -d '{"authority_id":"test-authority","naan":"99999","items":[{"client_item_id":"oai:repo:123"}]}'
```

Stage a title-only L1 and its original XML using the ARK returned by the example above.

```bash
curl -X PUT http://localhost:5000/api/v1/arks/ark:/99999/af796ca6c26f \
  -H 'Content-Type: application/json' \
  -H 'X-Authority-Id: test-authority' \
  -d '{"authority_id":"test-authority","target":"https://example.org/123","minimal_metadata":{"title":"Demo"},"original_metadata":"<record><title>Demo</title></record>","metadata_schema":"local-xml","metadata_media_type":"application/xml"}'
```

`GET /api/v1/arks/{ark}` returns the remote state. `/status` and `/registry`
are mock-only diagnostic endpoints.
