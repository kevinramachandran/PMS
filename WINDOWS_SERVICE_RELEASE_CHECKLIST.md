# Windows Service Release Checklist

Use this checklist when handing a Brewery PMS release to the Windows Server deployment owner.

## Build And Package

1. Run `./gradlew.bat packageWindowsService` from the repository root.
2. Confirm the ZIP exists at `dist/releases/brewery-pms-be-windows-service-<version>.zip`.
3. Confirm the unpacked bundle exists at `dist/windows-service`.

## Pre-Deploy Validation

1. Verify the release version matches the expected deployment version.
2. Confirm the target server has Java 21 available through `JAVA_HOME` or `PATH`.
3. Confirm the server can reach the MySQL host and port.
4. Confirm the production values are ready for `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SPRING_PROFILES_ACTIVE`, and `APP_EMAIL_CONFIG_SECRET`.
5. Confirm the installer will be run from an elevated PowerShell session.

## Server Deployment

1. Copy the ZIP to the target Windows server.
2. Extract the ZIP to a working folder on the server.
3. Open PowerShell as Administrator.
4. Run `install-service.ps1 -InstallRoot C:\Brewery-PMS` from the extracted `service` folder.
5. Update `C:\Brewery-PMS\config\brewery-pms.env` with production values if they were not set already.
6. Start or restart the service with `C:\Brewery-PMS\service\brewery-pms.exe start` or `restart`.

## Post-Deploy Checks

1. Run `C:\Brewery-PMS\service\brewery-pms.exe status` and confirm the service is running.
2. Confirm the application responds on the expected port.
3. Confirm database connectivity is working after startup.
4. Check `C:\Brewery-PMS\logs` for startup errors.
5. Confirm uploads still resolve under `C:\Brewery-PMS\uploads\footer-buttons`.

## Rollback Note

1. Keep the previous ZIP artifact until the new deployment is validated.
2. If rollback is required, stop the service, replace `C:\Brewery-PMS\app\app.jar` with the previous version, and restart the service.