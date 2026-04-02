# Deployment Guide

## Key Files

```text
.github/workflows/ci.yml
deployment/linux/install-service.sh
deployment/systemd/brewery-pms.service
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
build/libs/brewery-pms-be-1.0.0.jar
```

Windows note for this repository:

```text
%LOCALAPPDATA%/Brewery-PMS-BE/gradle-build/libs/brewery-pms-be-1.0.0.jar
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
java -jar $env:LOCALAPPDATA/Brewery-PMS-BE/gradle-build/libs/brewery-pms-be-1.0.0.jar
```

Linux server:

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:mysql://db-host:3306/brewery_pms
export DB_USERNAME=brewery_user
export DB_PASSWORD=replace-me
java -jar build/libs/brewery-pms-be-1.0.0.jar
```

Use an external config directory when needed:

```bash
java -jar build/libs/brewery-pms-be-1.0.0.jar --spring.config.additional-location=file:/etc/brewery-pms/
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