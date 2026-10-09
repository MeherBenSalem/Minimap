# Exact-source publication recovery

The publication controller can recover one destination independently with the `platform` workflow input: `both` (default), `modrinth`, or `curseforge`. It reads and uploads only the selected destination. Modrinth needs `MODRINTH_TOKEN`; CurseForge needs `CURSEFORGE_TOKEN` for upload and `CURSEFORGE_API_KEY` for metadata reads. Credentials must be configured securely, never included in receipt JSON, source, workflow inputs or logs.

Artifact provenance remains bound to the original `source_sha` and immutable `ci_run_id`. The reviewed controller runs from its own workflow commit, while `VERSION` and changelog are read from the exact release-source checkout. A controller-only update does not rebuild or substitute release JARs.

A file named `{version}-{source_sha}.json` in this directory is automatically imported before network access. Its receipt bundle must match the repository, version, source SHA, CI run, platform/project and every artifact's filename, loader, game, size and hashes. Additional exact-source receipts can be imported with the optional `browser_receipts_json` workflow input, or locally through `BROWSER_RECEIPTS_JSON`/`BROWSER_RECEIPTS_FILE`. Complete imports validate before writing and only merge journal upgrades. Arbitrary input fields are discarded.

For release 1.3.1 / source `682314d63e452e092ee33749fb6d49a7531c7031`, seven CurseForge IDs have been accepted and Fabric 26.2 remains uncertain. Accepted IDs are immutable even if moderation hides them; public absence cannot trigger another POST. The uncertain target blocks CurseForge/both publication until a definite outcome is reconciled and this recovery record is explicitly reviewed. It does not block Modrinth-only publication.

Use `preflight_only: true` first. It checks selected credentials, destination ownership, tags, dependencies, duplicate inventory and receipts without uploads. `verify_only: true` prevents new uploads. Each attempted POST is journaled before sending, is never automatically retried, and records the returned immutable ID immediately. Verification requires public metadata plus downloaded size, SHA-256 and SHA-512 matching the original CI artifact.

A selected-platform completion record covers only its listed `platforms`; it does not imply that all destinations are complete. Keep every receipt artifact from every attempt.
