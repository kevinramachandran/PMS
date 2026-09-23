# Cloud version

The `cloud-minimal-ui` branch keeps the existing backend and sync contracts, with a reduced web interface enabled by default. Build and deployment use the existing Gradle and Docker workflows described in `DEPLOYMENT.md`.

The menu contains four reporting configuration pages: Gemba Walk, Gemba Kaizen, CarlEx Process Confirmation, and Abnormality Reporting. Master Data retains Plant, Designation, the four module masters, User Management, Cloud Sync Configuration, Email Scheduler, SMTP Config, and License Management. Existing role, license and sync-user restrictions still apply.

Login opens Plant when permitted, otherwise the first permitted reporting configuration or Master Data page. Accounts without access to any cloud page receive an access message with a sign-out link. Old dashboard, reporting and other hidden page URLs redirect to the same permitted landing page. API endpoints and sync datasets remain available under their existing authorization rules.

The page list and landing order live in `CloudNavigation.java`. All retained pages use the shared `fragments/cloud-navigation.html` menu.

Validation: `gradlew.bat test` runs navigation, rendered-page and existing backend regression tests. This branch does not provision a cloud host or change database credentials; supply deployment-specific environment variables through the existing deployment configuration.
