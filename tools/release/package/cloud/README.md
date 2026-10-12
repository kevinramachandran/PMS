# PMS Cloud - 2026-10-12

Latest working-tree build, including KPI/date fixes, user search and area controls,
resizable user columns, configurable Daily Performance, permissions, editable KPI
units, cloud edit routing, and BIOS-bound license validation/generation.

Java 17+ and MySQL are required. Extract the ZIP before launching.
Copy config/brewery-pms.env.example to config/brewery-pms.env and configure your
port, database credentials, and persistent application secrets.
Run service/launch-app.bat for a direct launch, or install as a Windows service:
  powershell -ExecutionPolicy Bypass -File service/install-service.ps1 -InstallRoot C:\PMS-Cloud -ServiceName pms-cloud
Then configure the installed environment file and start the service.
The installer downloads WinSW if needed. Use separate folders, ports and databases
when running multiple instances. Existing multi-instance launch helpers are retained.

## Upgrade
Stop the existing instance and back up its database and application files.
Replace app/app.jar. Preserve the existing environment file, uploads and secrets.
Add/update these entries in the EXISTING config/brewery-pms.env before restarting:
CLOUD_MANAGED_EDITS=false
LICENSE_BIOS_BINDING_ENABLED=false
Do not rely on replacing the example file to change an installed environment file.
Remove any conflicting external application properties or command-line overrides.

Cloud: editable user/master/report configuration; no server BIOS enforcement.
OnPrem: configured edit actions open the saved Cloud PMS URL; local writes to those
APIs are blocked, including for admins. Set the URL in Cloud Sync Configuration.
OnPrem normal-user login requires a matching license BIOS ID. Generate a new license
with biosId if the existing license lacks it. License generation requires biosId and
returns it with the encrypted token. Existing recovery-admin and 0000 universal-license
exceptions remain. Each package contains its corresponding application build.

No customer database, uploads, logs or live environment files are included.
This package is prepared for deployment; no server was deployed or restarted.