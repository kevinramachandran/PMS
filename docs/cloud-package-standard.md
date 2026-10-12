# Cloud ZIP packaging contract

Use `dist/releases/PMS-4-cloud.zip` in the parent PMS repository as the established packaging baseline (8 October 2026), not `dist/cloud-configurable/service` or the older cloud source deployment scripts.

The archive root must contain `app/`, `config/`, `service/`, `README.md`, and `SHA256SUMS.txt`. Do not add a containing directory or source tree to the standard deployment ZIP.

Preserve the baseline service scripts and environment example, including `port-config.ps1`, `instance-config.ps1`, service ownership checks, and instance-specific stop behavior. Both launch paths must honor SERVER_PORT, DB_URL, DB_USERNAME, and DB_PASSWORD from `config/brewery-pms.env`. Never force port 165 after reading the environment, overwrite existing credentials, or stop another instance.

For app updates, replace `app/app.jar`, refresh its SHA256SUMS entry, and update release notes. Preserve existing customer environment files, persistent secrets, uploads, and databases. Do not ship live configuration or customer data. Validate archive paths and compare all service/config entries with the standard baseline before delivery.
