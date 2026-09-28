# Deployment Guide

## Client and test instances on the same Windows server

See [the two-instance setup guide](deployment/windows/README.md), also included
as `README.md` in the release ZIP. Install into separate folders with unique
service names, then set `SERVER_PORT` and `DB_NAME` in each installation's
`config/brewery-pms.env`. Both values can be changed after deployment without
rebuilding. Remove a legacy `DB_URL` if using the separate database settings.
Missing databases are created on connection when the MySQL account has the
required permissions; changing DB_NAME does not migrate existing data.

The cloud variant from `dist/releases/PMS-4-cloud.zip` has also been updated and
rebuilt as `dist/releases/PMS-4-cloud.zip`. Its updated source is in
`dist/cloud-configurable/source` (an ignored release workspace, not tracked by
Git). To rebuild that variant from its source directory:

```powershell
.\gradlew.bat -I isolated-build.gradle zipWindowsService -PappJarName=brewery-pms-cloud
```

Both commands update the existing ZIPs in the main project's `dist/releases` folder.

## Change the Windows deployment port in one place

After extracting a release ZIP, copy `config/brewery-pms.env.example` to
`config/brewery-pms.env` if the latter does not exist. In an installed service,
edit the file under the installation directory instead.

Set the port in that file, for example:

```dotenv
SERVER_PORT=8081
```

Restart PMS after changing it. The Windows service, launcher's readiness check,
browser URL, and stop script read this setting. No source edits or rebuild are
needed for a different port. An explicit `-ApplicationUrl` overrides only the
browser/readiness URL, not the listening port. Stop the running app before
changing its port if you use the direct launcher.

Build a deployment ZIP from the project directory:

```powershell
.\gradlew.bat clean build zipWindowsService
```

Output: `dist/releases/PMS-4.zip`.
The same ZIP can be used on different servers with different `SERVER_PORT`
values. Configure database credentials in each server's environment file too.
The ZIP contains the environment example; it does not include your live config.

For a separate Cloud PMS application, configure its own listening port in its
deployment. When its address changes, update the cloud URL in PMS sync settings
to include the new externally reachable port. That URL is separate from this
PMS application's `SERVER_PORT`. Use `PMS-4.zip` for PMS and `PMS-4-cloud.zip` for Cloud PMS. Each build updates its corresponding ZIP.

For Docker Compose, set `SERVER_PORT` in the root `.env` file; this changes the
published host port while the container continues listening on port 8080.

## Key Files

```text
.github/workflows/ci.yml
deployment/linux/install-service.sh
deployment/systemd/brewery-pms.service
WINDOWS_SERVICE_RELEASE_CHECKLIST.md
docker-compose.yml
src/main/resources/
  application.yml
  application-dev.yml
  application-qa.yml
  application-prod.yml
.env.example
Dockerfile
build.gradle.kts
```

## Gradle Packaging

This project uses Spring Boot's `bootJar` task to create a runnable fat JAR with all runtime dependencies bundled inside it.

Default artifact:

```text
build/libs/brewery-pms-be-2.0.0.jar
```

Windows note for this repository:

```text
%LOCALAPPDATA%/Brewery-PMS-BE/gradle-build/libs/brewery-pms-be-2.0.0.jar
```

The Windows build path is redirected outside the workspace to avoid OneDrive file-lock issues during Gradle builds.

Customize the JAR name and version at build time:

```powershell
.\gradlew.bat clean build -PappJarName=brewery-pms-api -PappVersion=1.2.0
```

## Build Commands

Windows:

```powershell
.\gradlew.bat clean build
```

Linux/macOS:

```bash
./gradlew clean build
```

Generate only the executable JAR:

```bash
./gradlew bootJar
```

## Run the JAR

Windows PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:DB_URL="jdbc:mysql://db-host:3306/brewery_pms"
$env:DB_USERNAME="brewery_user"
$env:DB_PASSWORD="replace-me"
java -jar $env:LOCALAPPDATA/Brewery-PMS-BE/gradle-build/libs/brewery-pms-be-2.0.0.jar
```

Linux server:

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:mysql://db-host:3306/brewery_pms
export DB_USERNAME=brewery_user
export DB_PASSWORD=replace-me
java -jar build/libs/brewery-pms-be-2.0.0.jar
```

Use an external config directory when needed:

```bash
java -jar build/libs/brewery-pms-be-2.0.0.jar --spring.config.additional-location=file:/etc/brewery-pms/
```

## Profiles

- `dev`: local development defaults, SQL logging enabled.
- `qa`: validation-focused config with lower log noise.
- `prod`: graceful shutdown, stricter logging, no schema auto-update.

## Production Notes

- Do not hardcode secrets in source control; inject them as environment variables or from a secret manager.
- Use `.env.example` only as a template; keep real values in GitHub Actions secrets, Docker secrets, Vault, AWS Secrets Manager, Azure Key Vault, or your server environment.
- Keep `application-prod.yml` free of environment-specific credentials.
- Use externalized config for server-specific overrides.
- Route logs to stdout in containers or configure `LOG_FILE` for VM-based deployments.

## CI/CD

GitHub Actions workflow:

```text
.github/workflows/ci.yml
```

What it does:

- Checks out the code.
- Installs JDK 21.
- Runs `./gradlew clean build --no-daemon`.
- Uploads the generated JAR from `build/libs/*.jar` as a workflow artifact.

Recommended repository secrets for deployment jobs:

- `DEPLOY_HOST`
- `DEPLOY_USER`
- `DEPLOY_SSH_KEY`
- `DEPLOY_PATH`
- `SPRING_PROFILES_ACTIVE`
- `SERVER_PORT`
- `APP_TIMEZONE`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `LOG_LEVEL_ROOT`
- `LOG_LEVEL_WEB`
- `LOG_FILE`

The workflow deploys only when started manually with `workflow_dispatch` and the `deploy` input set to `true`.

## Docker Compose

Copy `.env.example` to `.env`, adjust the values, then run:

```bash
docker compose up -d --build
```

This starts:

- the Spring Boot application on port `8080`
- a MySQL 8.4 container with persistent storage
- container health checks for both services

## Linux Service

Install the systemd unit on the target server:

```bash
chmod +x deployment/linux/install-service.sh
./deployment/linux/install-service.sh
```

Expected server layout:

```text
/opt/brewery-pms/app.jar
/etc/brewery-pms/brewery-pms.env
/etc/systemd/system/brewery-pms.service
```

Start or restart the service:

```bash
sudo systemctl restart brewery-pms
sudo systemctl status brewery-pms
journalctl -u brewery-pms -f
```

## Windows Service With WinSW

This is the recommended path for a Windows Server deployment because the application stays in the background and restarts automatically if it exits.

Build the deployment bundle on a build machine:

```powershell
.\gradlew.bat clean bundleWindowsService
```

For a release-ready handoff, generate the bundle and a versioned ZIP in one command:

```powershell
.\gradlew.bat packageWindowsService
```

ZIP artifact output:

```text
dist/releases/PMS-4.zip
```

This creates:

```text
dist/windows-service/
  app/app.jar
  config/brewery-pms.env.example
  service/brewery-pms.xml
  service/install-service.ps1
  service/remove-service.ps1
  service/start-service.ps1
```

If you use the ZIP artifact, extract it on the server first and then run the installer from the extracted `service` folder.

Team release handoff checklist:

```text
WINDOWS_SERVICE_RELEASE_CHECKLIST.md
```

Copy the `dist/windows-service` folder to the Windows server, open an elevated PowerShell session, then run:

```powershell
Set-Location .\dist\windows-service\service
.\install-service.ps1 -InstallRoot C:\Brewery-PMS
```

Optional: start the service immediately after install:

```powershell
.\install-service.ps1 -InstallRoot C:\Brewery-PMS -StartAfterInstall
```

Optional: start the service, wait for the app to respond, then open the URL in the default browser:

```powershell
.\install-service.ps1 -InstallRoot C:\Brewery-PMS -StartAfterInstall -OpenBrowserAfterStart -ApplicationUrl http://localhost:8080
```

You can also increase the wait time if the server starts slowly:

```powershell
.\install-service.ps1 -InstallRoot C:\Brewery-PMS -StartAfterInstall -OpenBrowserAfterStart -ApplicationUrl http://localhost:8080 -StartupTimeoutSeconds 180
```

Server layout after installation:

```text
C:\Brewery-PMS\
  app\app.jar
  config\brewery-pms.env
  logs\
  service\brewery-pms.exe
  service\brewery-pms.xml
  service\start-service.ps1
  uploads\footer-buttons\
```

Update the environment file before starting in production:

```text
C:\Brewery-PMS\config\brewery-pms.env
```

At minimum, set these values:

- `SPRING_PROFILES_ACTIVE=prod`
- `DB_URL=...`
- `DB_USERNAME=...`
- `DB_PASSWORD=...`
- `APP_EMAIL_CONFIG_SECRET=...`

Manage the service:

```powershell
Set-Location C:\Brewery-PMS\service
.\brewery-pms.exe start
.\brewery-pms.exe stop
.\brewery-pms.exe restart
.\brewery-pms.exe status
```

Remove the service registration without deleting application files:

```powershell
Set-Location .\dist\windows-service\service
.\remove-service.ps1 -InstallRoot C:\Brewery-PMS
```

Windows prerequisites:

- Java 21 installed and available through `JAVA_HOME` or `PATH`
- Network access from the server to the MySQL host
- An elevated PowerShell session for service installation

## Docker

Build image:

```bash
docker build -t brewery-pms-be:latest .
```

Run container:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:mysql://host.docker.internal:3306/brewery_pms \
  -e DB_USERNAME=brewery_user \
  -e DB_PASSWORD=replace-me \
  brewery-pms-be:latest
```

The image includes a Docker `HEALTHCHECK` so orchestrators can detect unhealthy containers.
