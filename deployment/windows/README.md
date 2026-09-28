# Two PMS instances on one Windows server

Use the same release ZIP for both instances. Ports and database names are read
at startup; changing them does not require another build.

## Install once

Extract the ZIP to `C:\PMS-release`. Open PowerShell as Administrator:

```powershell
cd C:\PMS-release\service
.\install-service.ps1 -InstallRoot C:\PMS-client -ServiceName pms-client -DisplayName "PMS Client"
.\install-service.ps1 -InstallRoot C:\PMS-test -ServiceName pms-test -DisplayName "PMS Testing"
```

Do not start either service until its environment file has been configured.
Java 17 or newer and a running MySQL server are required. The installer downloads
WinSW when the service executable is missing.

## Configure each instance

Edit `C:\PMS-client\config\brewery-pms.env`:

```dotenv
SERVER_PORT=8081
DB_HOST=localhost
DB_PORT=3306
DB_NAME=pms_client
DB_USERNAME=your_mysql_user
DB_PASSWORD=your_mysql_password
```

Edit `C:\PMS-test\config\brewery-pms.env`:

```dotenv
SERVER_PORT=8082
DB_HOST=localhost
DB_PORT=3306
DB_NAME=pms_test
DB_USERNAME=your_mysql_user
DB_PASSWORD=your_mysql_password
```

Keep the remaining settings from the example. Set `APP_EMAIL_CONFIG_SECRET` and
`APP_SYNC_SECRET` for each installation and retain those values across restarts.
The default log and upload paths are relative to each installation folder.
Use separate absolute paths if you override those defaults.

If upgrading an old environment file, remove/comment out its `DB_URL` line to
use `DB_NAME`. An explicit `DB_URL` takes precedence over DB_HOST/DB_PORT/DB_NAME.
Existing custom application properties and command-line datasource/port
overrides must also be removed if you want the environment settings to apply.

Start the services:

```powershell
Start-Service pms-client
Start-Service pms-test
```

Open `http://localhost:8081` for the client and `http://localhost:8082` for testing.
For remote access, use the server hostname/IP and allow the chosen ports through
the server/cloud firewall. Browser session cookies use different names per port.

## Change a port or database name later

```powershell
Stop-Service pms-test
notepad C:\PMS-test\config\brewery-pms.env
Start-Service pms-test
```

Change `SERVER_PORT` or `DB_NAME` in that file. No regeneration is required.
MySQL Connector/J creates a missing database via `createDatabaseIfNotExist=true`,
and Hibernate's `ddl-auto=update` creates/updates the application tables. The
configured MySQL account must already exist and have CREATE and schema/data
permissions for that database; the application does not install MySQL or create
MySQL accounts. An explicit DB_URL must include the creation option itself.

Changing DB_NAME switches databases. It does not rename, move, or copy existing
data. Use different database names for client and testing. If either database
already has sync settings, ensure its cloud URL and sync folders point to the
intended environment. New sync settings use instance-relative folders; existing
saved paths are preserved. Update external sync URLs after changing a port.

The installed service name is recorded in `config\service-name.txt`, so each
folder's `service\start.bat` and `service\stop.bat` control that instance.
Do not edit service-name.txt manually. To rename a Windows service, remove its
registration using `remove-service.ps1`, then reinstall from the release folder
with the new `-ServiceName`. This also requires no rebuild and preserves config
and data. Keep each installation in its own folder.

For a direct launch without installing services, extract into two separate
folders, copy each environment example to `brewery-pms.env`, configure as above,
and use each folder's `service\launch-app.bat`. Stop with `service\stop.bat`.
If the legacy `brewery-pms` service is installed elsewhere, pass a unique
`-ServiceName pms-client` or `-ServiceName pms-test` to those batch commands.

## Generate a new release ZIP from source

```powershell
.\gradlew.bat zipWindowsService
```

Output: `dist\releases\PMS-4.zip`.
This updates the existing release ZIP; no additional release filename is created.
