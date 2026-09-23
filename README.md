# LA Referencia DARK Library

DARK (decentralized ARK) library for persistent identifier minting and management.

## 🎯 Functionality

Provides integration with the dARK v1 service for reserving ARKs, staging Level 1
and original metadata, reconciling remote state, and publishing confirmed ARKs
into harvested records.

Level 1 authors are read from `dc.creator`, with `dc.contributor.author` as a
fallback. Values formatted as `Name|||ORCID` are sent as `Name`.

The worker settings use nested configuration:

```properties
dark.minter.base-url=http://localhost:8001
dark.stage.page-size=100
dark.stage.max-pages-per-run=0
dark.reserve.batch-size=100
dark.reconcile.page-size=100
```

Set `dark.stage.max-pages-per-run=1` for a bounded first deployment; `0` means
unlimited. See [`tools/README.md`](tools/README.md) for the current mock API.

## Importing legacy ARK mappings

The administrative shell exposes `import-dark-legacy-csv` for mappings created
by the legacy integration. The command is part of this module and is available
from `lareferencia-shell` in every build profile.

The input must be a UTF-8 comma-separated CSV with this header row:

```csv
darkidentifier,oaiidentifier,datestamp,itemurl,lastmodified
```

Example:

```csv
ark:41046/001300001kq89,oai:repositorio.ufrn.br:123456789/46761,2025-07-31 12:28:05.320471,https://repositorio.ufrn.br/handle/123456789/46761,2025-09-27 00:18:26.628545
```

```text
import-dark-legacy-csv --path /data/legacy-dark.csv
import-dark-legacy-csv --path /data/legacy-dark.csv --apply
```

Without `--apply`, the command only validates the input and reports the rows it
would import. Imported rows preserve their existing ARK and enter `UPDATE`
state with no payload hash, so their first `DARK_STAGE_ACTION` sends the full
metadata to dARK without reserving a new ARK.

The importer extracts `ark_naan` from the ARK, maps the legacy dates to
`created_at` and `updated_at`, and preserves `itemurl` as `target_url`. It
rejects duplicate CSV mappings and stops on conflicts with an existing local
dARK record; an identical existing mapping is skipped, making a completed
import safe to run again.

Run the import before the first stage action for the affected network. Do not
run reconciliation first, because its purpose is to synchronize records that
have already been staged. The platform-level runbook is available at
[`docs/DARK_LEGACY_ARK_IMPORT.md`](../docs/DARK_LEGACY_ARK_IMPORT.md).

## 📄 License

Licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.  
See [LICENSE.txt](../LICENSE.txt) for complete terms.

## 📧 Support

**Email**: soporte@lareferencia.redclara.net

---

**LA Referencia** - Red Latinoamericana y de España de Ciencia Abierta  
Part of the LA Referencia Platform 5.0.0-rc2
