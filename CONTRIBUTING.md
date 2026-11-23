# Contributing

Thanks for contributing to Remnants Minimap.

## Before you start

- Open an issue or discussion for substantial changes before investing in a large implementation.
- Keep changes focused. Avoid unrelated refactors in the same pull request.
- Preserve compatibility-sensitive identifiers (`modId`, package names, registry IDs) unless a change explicitly requires them.

## Development workflow

1. Fork the repository and create a branch from `main`.
2. Make the smallest coherent change that solves the problem, in the relevant Minecraft version workspace
   (`1.20.1/`, `1.21.1/`, `26.2/` or `26.3/`).
3. Build the workspace you changed (`./gradlew build` from that folder: Gradle on Java 21 for 1.20.1 and 1.21.1,
   Java 25 for 26.x), or all four with `./gradlew buildAll` from the root (the 8 loader jars land in `releases/`).
   This also runs the unit tests.
4. Update documentation when behavior, configuration, or release steps change.
5. Open a pull request with a clear summary, verification notes, and any compatibility risks.

## Coding expectations

- Follow the existing patterns in the targeted version root. The version folders are intentionally isolated.
- Keep `core/` packages free of Minecraft classes so they can be copied into future version roots unchanged.
- Do not port changes across versions with shared sources or symlinks.
- Avoid committing generated jars, logs, run directories, secrets, or local IDE files.

## Reporting issues

- Use GitHub issues for bug reports and feature requests.
- Include the Minecraft version, loader, mod version, reproduction steps, and relevant logs when possible.

By contributing, you agree that your contributions are licensed under the Apache License 2.0 unless stated otherwise.
