# PMS cloud and on-premises agent handbook

Updated during release recovery on 12 October 2026. The initial release audit was incomplete: the release worktrees were missing newer parent/cloud-source changes. This is persistent context, not evidence of deployment. Read `docs/release-recovery-20261012.md` and recheck all source locations before building.

**Current release procedure:** use `docs/release-workflow.md` and `tools/release/release.cjs`. This committed-input workflow supersedes the dated manual scripts described below. The designated worktrees are the development sources; retained parent/cloud snapshots are monitored recovery references. Do not bypass a failed gate or mark an unperformed visual review as passed.

## Workspace and branch map

All paths below are relative to `C:/Users/WIND/Downloads/PMS` unless absolute.

| Purpose | Source location | Verified branch | Deliverable |
| --- | --- | --- | --- |
| On-premises | `dist/local-git` | `fix/pms-dashboard-permissions-license-20261009` | `PMS-4.zip` |
| Cloud | `dist/cloud-minimal-ui` | `cloud-minimal-ui` | `PMS-4-cloud.zip` |
| Recovery source | `.` | `recover-ft-correct` plus uncommitted changes | Newer JSON/API sync, attachment, scheduling, and other fixes recovered into the on-premises worktree |
| Cloud recovery source | `dist/cloud-configurable/source` | Files within the parent checkout, not an independent branch | Matching cloud JSON/API implementation recovered into the cloud worktree |

Use `git worktree list`, `git branch --show-current`, and `git status --short` to confirm the map. The release sources are Git worktrees, not interchangeable build copies. Inspect uncommitted source changes in the parent and `dist/cloud-configurable/source` too; these contained newer work despite older branch commits. Do not switch branches, reset changes, or replace complete source trees just to prepare a release. The outer `dist/cloud-configurable/service` scripts are not the standard packaging baseline. If Git reports dubious ownership, use the approved execution context; do not change global trust settings casually.

## Application structure

- `src/main/java/org/example/BreweryPmsApplication.java` starts Spring Boot.
- `controller/` contains Thymeleaf page routes and REST endpoints. `WebController` handles page routing; feature controllers handle dashboard configuration, Gemba, abnormalities, process confirmation, users, and related workflows.
- `service/` contains business rules, authentication, configuration, and synchronization logic. `repository/` and `entity/` implement JPA persistence; `model/` carries supporting models.
- `config/`, `interceptor/`, and `util/` support request handling and shared behavior. Preserve existing access checks and their regression coverage when changing endpoints or forms.
- `src/main/resources/templates/` and `static/js/`, `static/css/` form the frontend. There is no separate frontend compilation step in the Gradle build; these resources must be present in the executable JAR.
- `src/test/java/` contains JUnit/Spring tests; `src/test/js/` contains Node tests. `deployment/windows/` contains deployment assets, but the standard ZIP's verified scripts are the release baseline.
- `build.gradle.kts` declares Java 17, Spring Boot 3.2.0, Thymeleaf, JPA, MySQL, H2, mail, and test dependencies. The older Windows checklist's Java 21 wording does not describe the configured build toolchain.

## Version-aware development

For a change requested in both versions, inspect each implementation, apply compatible edits to each, and build/test each independently. Do not assume identical source or features. Preserve cloud-specific configuration and on-premises licensing/synchronization behavior.

The normal sync run must use structured JSON records through `/api/cloud-sync/data/{dataset}` and `applyJsonSnapshots`, with optional report pushes through `/api/cloud-sync/push`. It must not fetch CSV exports or stage CSV files for normal API pulls. Legacy/manual CSV helpers can remain for compatibility; their presence alone does not prove the active sync flow is CSV-based. Check the actual run method and execute the HTTP sync regression tests. Validate the embedded release classes, not just source strings.

The four register-page layout changes from 11 October are currently cloud-specific: `gemba-walk-config.html`, `gemba-kaizen-config.html`, `abnormality-reporting-config.html`, and `process-confirmation-config.html`. Cloud uses `static/css/report-layout.css`, `pms-report-register`, and `pms-mobile-scroll-only` to align Download/Add new controls and retain one horizontally scrollable table header on phones. Inspect CSS specificity and the mobile JavaScript together to avoid mixed table/card rendering. Do not tell the user these particular layout changes already exist in on-premises without checking or porting them.

When modifying cached assets, follow the templates' versioned asset references. Keep existing user data and configuration intact. Do not add internal implementation details or unrequested policy explanations to application text or release notes.

## Build and verification

Run from each target worktree, not the parent checkout:

```powershell
.\gradlew.bat test bootJar --no-daemon
node --test src/test/js/*.test.cjs
```

For a focused page check:

```powershell
# On-premises
.\gradlew.bat test --tests org.example.service.LocalPagesTest --no-daemon
# Cloud, from its own directory
.\gradlew.bat test --tests org.example.service.CloudPagesTest --no-daemon
```

Use `.\gradlew.bat bootRun` for development only after selecting appropriate process configuration and a development database. Package launchers load `config/brewery-pms.env`; do not assume `bootRun` loads that file automatically. Never assume a previously started local process runs a newly rebuilt JAR.

On Windows, output is isolated under `%LOCALAPPDATA%/<root-project-name>-<worktree-path-hash>/gradle-build`. Do not restore the old shared output directory: it could mix classes/resources from the two sources. `bootJar` finalizes `copyJar`, producing `dist/brewery-pms-be-2.0.0.jar` with current default properties. If `appVersion` or `appJarName` changes, update packaging paths accordingly.

Current on-premises helpers:

- `dist/verify-release-builds.ps1`: verifies both JARs' resources against their source trees and rejects top-level classes without matching source files. This does not prove Java bytecode freshness; a successful independent build is still required.
- `dist/test-standard-env.ps1` and `dist/test-standard-instance.ps1`: validate launcher behavior using the extracted `dist/releases/PMS-4-cloud-standard-20261011` fixture. They are fixture checks, not automatic checks of a newly generated ZIP. Point equivalent checks at the new package when scripts change.
- `dist/package-rebuilt-releases.ps1`: the last packaging recipe. It has a fixed date/output directory, fixed JAR paths, and create-only ZIP behavior. Adapt it to a new unused release directory/date before reuse; do not blindly rerun it or remove previous releases.

These helpers live under generated/ignored `dist`; do not assume a fresh clone contains them. Read the packaging contract below if they are absent. The October checks included focused Java/frontend tests and archive validation, but no completed browser visual validation. For UI work, check representative desktop/mobile sizes and all four affected registers when a browser is available.

## Standard package contract

Each version has a separate baseline. The canonical release copies are `C:/Users/WIND/Downloads/PMS/dist/releases/PMS-4.zip` and `PMS-4-cloud.zip`. Previous 8 October baselines are retained under the on-premises worktree's `dist/releases/previous-standard-20261008/`. The earlier `dist/releases/rebuilt-20261012/` builds are superseded: they omitted the newer API sync implementation. Consult the recovery report for corrected build evidence.

Keep exactly these 15 files at the ZIP root, with forward-slash archive paths and no enclosing directory:

```text
README.md
SHA256SUMS.txt
app/app.jar
config/brewery-pms.env.example
service/brewery-pms.xml
service/install-service.ps1
service/instance-config.ps1
service/launch-app.bat
service/launch-app.ps1
service/port-config.ps1
service/remove-service.ps1
service/start-service.ps1
service/start.bat
service/stop-app.ps1
service/stop.bat
```

1. Build the on-premises JAR from `local-git` and cloud JAR from `cloud-minimal-ui`. Never place the same binary in both packages merely because their sizes are similar.
2. Start from each corresponding verified standard ZIP. Preserve its `service/*` and `config/*` bytes, including variant-specific defaults. The generic Gradle `packageWindowsService` task exists but is not by itself proof of compliance with this established package contract.
3. Replace only `app/app.jar`, update its SHA-256 entry in `SHA256SUMS.txt`, and update relevant README release information. Keep release documentation concise.
4. Both launch paths must honor `SERVER_PORT`, `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` from the user's `config/brewery-pms.env`. Do not force port 165 after loading it. Preserve instance ownership and instance-specific stop behavior.
5. Do not ship a live `.env`, credentials, source trees, logs, uploads, or database exports. Preserve customer configuration and data during updates.
6. Validate the archive file list, embedded JAR hash, checksum text, and unchanged service/config hashes against the corresponding baseline. A fresh build and matching sources are required before delivery.
7. When authorized to deliver both releases, replace the canonical filenames in both `C:/Users/WIND/Downloads/` and `C:/Users/WIND/Downloads/PMS/dist/releases/`, retaining a previous release backup. Verify the copied hashes. Supply direct links so the user can find the ZIPs; dated alternatives alone caused confusion previously.

Packaging is not deployment. Do not claim the cloud server or an installed Windows service has been updated merely because ZIPs were created.

## Maintaining this agent context

Keep `AGENTS.md` and this handbook in both active worktrees. Update the branch/path map when it changes and record whether a feature is shared or version-specific. Keep secrets out of agent guidance. Existing `.agent.md` describes a general backend helper; this handbook supplies the PMS-specific cross-version context.
