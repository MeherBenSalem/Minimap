# Odyssey Map v1.2.1 Patch Notes

**Release Date:** July 16, 2026

## Bug Fixes
- Fixed MC 26.2 crash: Mixin failed to apply `GameRendererAccessor` because `mainCamera` was shadowed as a static interface field.
- Fixed the same static `@Shadow` pattern on `LevelRendererAccessor.submitNodeStorage` by switching to `@Accessor`.

## Technical
- Use public `GameRenderer.mainCamera()` instead of a custom accessor mixin.
- Version bump to 1.2.1 for all supported loaders/MC versions.

## Downloads
- odysseymap-fabric-1.20.1-1.2.1.jar
- odysseymap-fabric-1.21.1-1.2.1.jar
- odysseymap-fabric-26.1.2-1.2.1.jar
- odysseymap-fabric-26.2-1.2.1.jar
- odysseymap-forge-1.20.1-1.2.1.jar
- odysseymap-neoforge-1.21.1-1.2.1.jar
- odysseymap-neoforge-26.1.2-1.2.1.jar
- odysseymap-neoforge-26.2-1.2.1.jar

