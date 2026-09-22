# Cloud sync primary keys

Manual cloud sync treats each incoming record ID as authoritative in the destination database:

- An existing ID is updated with the supplied cloud fields, including reporting fields that are read-only in the normal editing form.
- A supplied ID that does not exist locally is inserted with that exact ID. Repeating an import with supplied IDs does not create duplicate parent records.
- A blank or `null` CSV ID creates a new row with a database-generated ID. Generated parent IDs and CSV row numbers are listed in the JSON run report. Repeating a row with a blank ID creates another new row; retain its generated ID for subsequent updates.
- For master data, the incoming category also wins at the supplied ID. For example, incoming Plant ID 1 replaces local Process Area ID 1. The report records this category replacement as a warning. Local references to that overwritten ID now refer to the incoming master.
- Rows not present in the CSV remain local. Fields absent from a partial CSV retain their existing values.
- Supplied cloud CSV IDs must be distinct and positive. Ordinary CSV imports still follow page editing rules.

Each file is transactional. Invalid explicit master IDs, duplicate IDs, database conflicts, or an observation ID owned by another walk reject the file and roll back its changes. Already completed files are not rolled back by a later file failure.

Cloud report exports may contain historical text with an explicitly blank master ID because the source no longer has a unique master match. Cloud import preserves that text, leaves the reference unlinked, and reports a warning. It never guesses an ID. This applies to reporting records and walk observations, not user or master creation. Ordinary CSV validation remains unchanged. Header-only cloud datasets are successful no-ops and do not delete local records.

Deploy the same application on cloud and local servers. Enable scheduled pulling only on the destination local server, pointing at the final cloud PMS URL with a cloud account permitted to export all selected datasets. Keep scheduling disabled on the cloud source. The schedule uses the destination server timezone and catches up after the configured time, at most once per day after an attempt; manual sync can retry failures. Keep the destination APP_SYNC_SECRET stable, or re-enter its stored cloud password after changing it.

Master files are processed before user and reporting files, with plants before departments and areas. Required master datasets and users are automatically added to the execution plan, including for older saved configurations. Gemba Walk observation IDs are preserved when supplied and generated when blank; IDs already owned by another walk are rejected. CarlEx dynamic observations are replaced from their exported JSON groups.

Cloud master IDs are authoritative even when historical source labels differ from current master names. Hierarchy checks use explicit department IDs when available. Explicitly blank source links remain blank rather than matching an unrelated local name. Ordinary manual CSV name/ID checks remain strict.

The versioned `/api/data-sync/{dataset}/export-for-sync` endpoint returns the CSV together with source mapping warnings; both servers need this build. Each run imports only files downloaded for that run. Older queued files are retained without replaying them automatically. The completed folder receives a `sync-report-<run-id>.json` containing per-file results, warnings, errors, generated IDs, and skipped dependent files. Runs with unresolved mappings or category replacements finish as `SUCCESS_WITH_WARNINGS`, distinct from clean success.

Account sync preserves existing local password hashes when the password column is blank. Exports omit credentials, so newly imported accounts need a local password reset. Reserved system accounts and license checks remain protected.

This imports database fields, not attachment file contents or assignment-history tables. Image paths require the corresponding files to exist on the destination.

The success message reports added, replaced, and unchanged rows. Clicking OK reloads the page to display the saved data. Deploy/restart the updated application before testing against a live database.

The Abnormality Reporting update failure logged on September 21, 2026 was a separate null comparison in empty reassignment fields. Optional field comparisons are now null-safe, with create/update regression coverage.
