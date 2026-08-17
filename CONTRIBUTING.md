# Contributing to XClip

Thanks for considering a contribution to XClip.

XClip is a local-first Windows clipboard workspace built with Java 17, JavaFX 21, SQLite, Gradle, and Windows integration through JNA. Contributions should preserve the product's safety, privacy, persistence, and Windows lifecycle guarantees rather than trading them for convenience.

## Before you start

For bug fixes and small, well-scoped improvements, opening a pull request directly is fine. For large features, data-model changes, new dependencies, major UI changes, or changes to documented product behavior, open an issue first so the scope can be agreed before implementation.

Security vulnerabilities should **not** be reported through a public issue. Follow [SECURITY.md](SECURITY.md).

## Development environment

Recommended baseline:

- Windows 10 or Windows 11 x64;
- JDK 17;
- Git;
- the repository Gradle Wrapper.

Clone and verify the project:

```powershell
git clone https://github.com/End1essspace/XClip.git
cd XClip
.\gradlew.bat clean test --no-daemon
.\gradlew.bat build --no-daemon
```

The Gradle build configures Java 17 through the toolchain and JavaFX 21 through the OpenJFX plugin.

## Engineering constraints

Please preserve these contracts unless a change explicitly revises the documented product behavior:

- clipboard data stays local by default;
- clipboard commands are never executed automatically;
- executable or script paths are not launched as commands;
- privacy and sensitive-content processing remains local;
- PINNED clips are protected from ordinary retention cleanup;
- long clipboard contents remain bounded in UI previews;
- SQLite changes are migration-compatible and preserve existing user data;
- config changes have safe defaults and migration behavior;
- Windows lifecycle failures should degrade safely rather than silently lose clipboard data;
- long-running database or system work must not block the JavaFX Application Thread unnecessarily.

Avoid adding telemetry, cloud synchronization, hidden network behavior, automatic command execution, or destructive defaults.

## Code changes

Keep changes focused. Prefer existing package boundaries:

```text
config
data
domain
system
ui
validation
```

Business rules should stay out of JavaFX controls where a domain or policy object is appropriate. Windows-native behavior belongs under `system`. Persistence belongs under `data` and should have tests around migration and failure behavior.

New dependencies should be justified by a concrete requirement and kept minimal.

## Tests and validation

For normal changes, run:

```powershell
.\gradlew.bat clean test --no-daemon
.\gradlew.bat build --no-daemon
git diff --check
```

`build` also runs the repository verification graph, including current packaged-resource and frozen regression-asset checks.

For packaging-related changes, also run:

```powershell
.\gradlew.bat clean packageMsi --no-daemon
```

UI, Direct Paste, tray/hotkey, display/DPI, database recovery, backup/restore, and lifecycle changes require relevant manual validation in addition to automated tests.

## Pull requests

A good pull request should:

- solve one coherent problem;
- explain the user or engineering impact;
- include tests for changed behavior where practical;
- call out migration, persistence, privacy, lifecycle, and data-loss risk;
- update user-facing documentation when behavior changes;
- avoid unrelated formatting or refactoring;
- contain no generated build output, private clipboard data, local databases, config files, or secrets.

CI runs the Windows Java 17 build on pushes and pull requests to `main`.

## Commit style

Use short imperative commit messages that describe the completed change, for example:

```text
Fix tray recovery after Explorer restart
Add regression coverage for tag filtering
Document backup restore safety
```

## License

By contributing, you agree that your contribution will be distributed under the repository's [GNU General Public License v3.0](LICENSE).
