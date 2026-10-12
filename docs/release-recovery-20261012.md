# Release recovery, 12 October 2026

## Cause and preserved work

The first October 12 ZIPs were built correctly from incomplete worktrees. The on-premises branch contained the October dashboard, daily-performance, access, and licensing work, but diverged from the parent before newer sync fixes. Cloud's worktree likewise lacked later changes present in `dist/cloud-configurable/source`. Matching a JAR to one worktree did not establish that all requested changes were included.

The newer parent and cloud snapshot files were left intact. Before reconciliation, source snapshots of all four locations were saved under the on-premises worktree's `dist/reconcile-latest/backup/`. Nothing was reset or removed from Git branches.

## Reconciliation

- On-premises: compared current files with the parent working tree using common ancestor `1709bab`; reconciled 82 changed source/test paths, retaining October functionality where implementations overlapped.
- Cloud: compared current files with the newer cloud source snapshot using branch baseline `8b0c7a2`; reconciled 41 changed source/test paths, retaining mobile and four-register layout work.
- Recovered normal JSON/API pull and optional report push, attachment/history transfer, user synchronization, scheduling, login/navigation, export, and related fixes.
- Restored configurable database host/name/port defaults and per-port session cookies while retaining explicit `DB_URL`, `SERVER_PORT`, and cookie overrides.
- Preserved the separate variant configuration, current mobile assets, dashboard/daily-performance work, access checks, and standard ZIP service/environment contract.

The reconciliation inventory is `dist/reconcile-latest/report.json`. `dist/audit-recovery.cjs` checks that the core sync classes match the recovered parent source and verifies register identifiers and retained UI hooks. Historical candidate files are staging records, not a source to copy over later edits.

## Release gate

Run the full Java suite and frontend checks from each worktree. The HTTP sync test must demonstrate requests to `/api/cloud-sync/data/`, successful JSON snapshot application, attachment handling, and no invocation of the CSV importer during normal pulls. Database tests cover dataset-level rollback, transferred credentials/permissions, history, identifiers, and reference handling.

Verify embedded resources and compiled classes against each build, confirm the API sync classes exist in both ZIPs, and compare service/config files with the corresponding standard baseline. Do not reuse the earlier `rebuilt-20261012` ZIPs. Packaging does not deploy either server.

## Completed verification and delivery

Corrected ZIPs: `dist/releases/recovered-api-20261012/PMS-4.zip` and `PMS-4-cloud.zip` in the on-premises worktree. Canonical copies are delivered to Downloads and the parent repository's `dist/releases`. Earlier incomplete ZIPs are preserved under `dist/releases/before-api-recovery-20261012`.

- Java regression results: on-premises 155 passed / 2 skipped; cloud 139 passed / 2 skipped. Full suites were executed; after correcting the last stale rejected-login assertion, the entire affected HTTP sync test class was rerun successfully for each version. Consolidated XML and counts are in `dist/reconcile-latest/*-full-suite-results` and `test-summary.json`.
- Frontend checks: on-premises 18 passed; cloud 11 passed, run from their respective worktrees.
- JAR/source verification: 100 resources and 193 compiled classes on-premises; 98 resources and 188 compiled classes cloud. Compiled method inspection confirms normal execution calls JSON snapshot application without CSV import or file staging.
- Package verification: standard 15-entry archive layout, corresponding JAR hashes, unchanged service/config bytes, and copied ZIP hashes. Launcher custom port/database and instance isolation checks passed.
- No browser visual verification or live server deployment was performed during this recovery.

Recovery helpers: `dist/audit-recovery.cjs`, `dist/verify-recovered-artifacts.ps1`, and `dist/package-recovered-releases.ps1`. Like the other dated helpers, they contain local paths/output names and must be inspected before a future run. Legacy CSV download/import features remain available, but are not the normal API synchronization transport.
