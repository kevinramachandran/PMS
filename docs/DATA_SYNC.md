# CSV data sync

Export CSV and Import CSV are available on Gemba Kaizen, Abnormality Reporting, Gemba Walk and Process Confirmation **configuration** pages, every master-data category, and User Management. View pages do not include these controls.

1. Export from the relevant page/category, even when it is empty, to obtain the CSV headers.
2. Keep `id` unchanged to update an existing record. Leave `id` blank to create a record with a generated ID. Unknown, inaccessible, duplicate, or cross-category IDs are rejected. This does not preserve foreign database IDs when migrating to a new installation.
3. Import the edited UTF-8 CSV on the same page/category. The page reloads and displays created/updated counts. A failure rolls back the entire file; records absent from the CSV are not deleted.

The maximum file size is 5 MB and the maximum count is 5,000 records. Quoted commas, quotes, line breaks and UTF-8 BOMs are supported. Dates use `yyyy-MM-dd`; times use `HH:mm:ss`. Exported formula-like text is prefixed with an apostrophe for spreadsheet safety, which is reversed on import. Keep spreadsheet ID columns as text to avoid changing large IDs.

An `id` column is mandatory. Other columns can be omitted to preserve their existing values. Empty cells in included columns clear the value where normal validation permits it. Unknown or duplicate headers are rejected. New rows must include the fields required by the normal entry form. Configure parent plants/departments before importing their children; master exports contain all records in that category, regardless of UI filters.

Imports use the existing editing rules and assignment validation. In particular, original Kaizen details and original Gemba Walk details remain read-only after creation; changeable status, images and assignment fields can be synced. An attempted change to a read-only field produces a row error instead of silently ignoring it. Normal assignment history and post-commit notifications continue to apply.

Gemba Walk `observations` is a JSON array within a quoted CSV cell. Existing observation IDs must be retained; the importer matches them by ID even if their order changes. Existing observations allow status/image updates. Process Confirmation dynamic observations use the exported `zmObservationsJson`, `pmObservationsJson` and `qmObservationsJson` columns. Attachment paths are exported; binary attachments are not transferred by CSV.

User passwords/hashes are never exported. The `password` column is blank: keep it blank to preserve an existing password, or supply a password for a new user. Existing usernames cannot be changed. `pageViewPermissions` and `pageEditPermissions` contain comma-separated page keys; use CSV quoting as in the export. System accounts are excluded. User validation and licence limits remain enforced.

Export requires View permission; Import requires Edit permission on the corresponding configuration page. Record-level editing restrictions also apply.

API: `GET /api/data-sync/{dataset}/export?category=...` and multipart `POST /api/data-sync/{dataset}/import?category=...` with field `file`. Supported datasets: `gemba-kaizen`, `abnormality`, `gemba-walk`, `process-confirmation`, `plant-master`, `kaizen-master`, `abnormality-master`, `walk-master`, `process-master`, `users`. A category is required for master datasets.
