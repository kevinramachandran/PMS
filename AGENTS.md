# Repository Guidelines

## PMS Development Agent

For cloud/on-premises development and releases, read `docs/pms-agent-handbook.md` first. Compare the parent checkout and cloud source snapshot for newer uncommitted changes before building; branch names alone do not establish freshness. Preserve existing work. The handbook records the workspace map, architecture, version differences, and packaging contract.

## Project Structure & Module Organization

This is a Java 17, Spring Boot 3.2 application using Spring MVC, Thymeleaf, JPA, and MySQL. Controllers in `src/main/java/org/example/controller` serve pages and JSON APIs; services contain business logic; repositories and entities handle persistence. MVC interceptors enforce request access. Templates and static JavaScript/CSS jointly implement the UI, so inspect both when changing a page.

This worktree is cloud. The sibling `../local-git` is on-premises. They have different branches and application behavior; do not copy complete source trees or reuse one version's JAR for both. For requests covering both versions, implement and verify the applicable change in each worktree.

## Build, Test, and Development Commands

Run commands from the intended worktree:

- `.\gradlew.bat test bootJar --no-daemon` builds and tests; `bootJar` also copies the executable JAR to `dist`.
- `.\gradlew.bat test --tests org.example.service.LocalPagesTest --no-daemon` selects an on-premises test. Cloud uses `org.example.service.CloudPagesTest`.
- `node --test src/test/js/*.test.cjs` runs the JavaScript tests.
- `.\gradlew.bat bootRun` starts development with that process's configuration; use a development database and an available port.

Windows build directories are isolated by worktree path. Keep that isolation. JUnit tests use Spring Boot's test starter and JUnit Platform. Run checks appropriate to the change; report browser validation only when actually performed.

## Release Rules

Follow the handbook's standard 15-entry ZIP contract. Final filenames are `PMS-4.zip` and `PMS-4-cloud.zip`. Preserve each version's service scripts and environment example; honor `config/brewery-pms.env` for port/database configuration. Never include live credentials, uploads, logs, or database exports. Existing dated packaging helpers require inspection before reuse.

## Commit & Pull Request Guidelines

Recent commits use short imperative subjects such as “Fix” and “Apply”; no strict prefix convention is established. Describe the affected version, behavior change, and actual verification. Update the handbook when branches, packaging, or version differences change.
