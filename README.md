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

## 📄 License

Licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.  
See [LICENSE.txt](../LICENSE.txt) for complete terms.

## 📧 Support

**Email**: soporte@lareferencia.redclara.net

---

**LA Referencia** - Red Latinoamericana y de España de Ciencia Abierta  
Part of the LA Referencia Platform v4.2.6 / v5.0
