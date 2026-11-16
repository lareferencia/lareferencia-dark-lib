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
Part of the LA Referencia Platform; Maven version inherited from `lareferencia-parent`.


## Autenticación DARK2 y gestión de claves

El módulo dARK del Admin del cosechador integra conexión, claves y activación en **Configuración del minter**. El administrador del cosechador y el del minter pueden ser personas distintas.

1. Guardar la URL HTTP o HTTPS del minter y el modo DARK2. El ID de autoridad puede quedar vacío durante el registro inicial.
2. Generar el par Ed25519, copiar la pública o descargar `dark-minter.pub`. La privada permanece en el servidor.
3. Enviar esa pública al administrador del minter para que registre la instalación y proporcione el ID de autoridad. La huella DARK2 está disponible en «Detalles de la clave».
4. Introducir y guardar el ID recibido. El par generado se conserva.
5. Pulsar **Confirmar registro y reanudar** después de recibir la confirmación del administrador del minter para esa clave, autoridad y destino. Esto habilita los envíos; no representa una prueba remota de la firma.

Los parámetros operativos se agrupan en **Configuración avanzada**.

Regenerar reemplaza el único par vigente y pausa las reservas y los envíos de metadatos hasta completar nuevamente el registro externo. La pausa se guarda en la base de datos y sobrevive a reinicios. Las lecturas y consultas de estado continúan disponibles. Cambiar la autoridad o el destino también exige confirmar de nuevo el registro correspondiente.

### Almacenamiento del servidor

Las claves se guardan automáticamente en `${store.basepath}/keys`, usando la misma propiedad `store.basepath` que el harvester emplea para el catálogo y los metadatos. La interfaz muestra una nota breve indicando que se almacenan en el directorio `keys` del almacenamiento de la aplicación. La ubicación es fija y la API rechaza cambios.

```properties
# Almacenamiento existente del harvester. Las claves quedarán en /srv/lareferencia/data/keys.
store.basepath=/srv/lareferencia/data
```

No hay una propiedad independiente para la ubicación de las claves. Si ya se habían generado con la ubicación anterior, trasladar su directorio de credencial (conservando el ID y los permisos) al nuevo subdirectorio `keys` antes de reanudar. El sistema mantiene la referencia vigente y señala archivos no disponibles si todavía no se han trasladado.

El directorio de claves debe tener permisos 0700. El backend crea cada privada PKCS#8 con permisos 0600 y exporta la pública como OpenSSH Ed25519 y raw base64. La huella DARK2 es SHA-256 hexadecimal de los 32 bytes públicos raw; no es la huella de `ssh-keygen -lf`.

Esta implementación usa permisos POSIX y está prevista para una única instancia administrativa/firmante. Mantener el volumen fuera de las rutas HTTP públicas y de los repositorios, incluirlo en las copias de seguridad junto con la base de datos y conservarlo al recrear contenedores. No se genera una clave nueva automáticamente si falta un archivo. Las credenciales anteriores pueden permanecer como archivos de recuperación; la aplicación solo utiliza la referencia vigente.

### Peticiones y recuperación

Las reservas batch y los PUT de metadatos llevan firma DARK2. El cuerpo JSON se serializa una sola vez y se firma el buffer enviado. Las modificaciones DARK2 no se reintentan en el cliente HTTP; las lecturas conservan el backoff configurado. El proxy debe preservar path y query y el reloj del servidor debe estar sincronizado.

Una respuesta perdida de stage se registra como `MODIFICATION_OUTCOME_UNKNOWN`. Ese registro queda excluido de nuevos envíos automáticos hasta una revisión y un envío manual explícitos; leer el estado remoto no confirma qué payload se aceptó. La interfaz advierte antes del envío manual. Las reservas batch conservan los OAI IDs como `client_item_id` para recuperar las reservas activas en una ejecución posterior.

La gestión está disponible bajo `/api/v5/dark/authentication`, con acceso ADMIN. Ningún endpoint devuelve la privada. La implementación se valida offline mediante el vector de la guía DARK2; el despliegue debe completar una operación firmada en su minter autorizado para comprobar la integración real.

DARK2 admite HTTP y HTTPS. Si se utiliza HTTP, la interfaz muestra una advertencia: la firma autentica la petición, pero el transporte no cifra los datos. Se recomienda HTTPS.

Las modificaciones DARK2 envían los seis encabezados de firma y también `X-Authority-Id` con exactamente la misma autoridad. Esto permite la identidad explícita que el minter exige cuando su verificación de firmas está desactivada. En el servidor, `MINTER_SIGNED_AUTH_ENABLED` determina si se verifica la firma.
