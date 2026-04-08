# Jawwy Automation

Jawwy Automation is an end-to-end test automation framework for the Jawwy activation flow in EOC. It creates orders, validates backend integration steps, sends external-system callbacks, and completes the required manual task in the UI.

## What changed in this refactor

- Environment-aware configuration moved into `src/main/resources/config`
- Reusable framework code moved into `src/main/java`
- Payload templates moved into `src/main/resources/payloads`
- Action logging added for API, DB, workflow, and UI steps
- Old report folders are deleted automatically at suite start
- Test classes now stay thin and orchestrate the business journey only
- CI/CD entry points added for GitHub Actions and Jenkins

## Run from terminal

Local defaults:

```powershell
mvn clean test
```

Run against a specific environment profile:

```powershell
mvn clean test -Denv=sit
mvn clean test -Denv=uat
```

Override endpoints or secrets without changing files:

```powershell
mvn clean test -Denv=sit -Dapi.base-url=https://my-host:8081 -Ddb.url=jdbc:oracle:thin:@//my-db:1521/orcl -Ddb.user=EOC -Ddb.password=secret
```

You can also use environment variables in local shells or CI:

```powershell
$env:JAWWY_ENV='sit'
$env:JAWWY_API_BASE_URL='https://my-host:8081'
$env:JAWWY_UI_BASE_URL='https://my-host:8081'
$env:JAWWY_UI_USERNAME='upadmin'
$env:JAWWY_UI_PASSWORD='upadmin'
$env:JAWWY_DB_URL='jdbc:oracle:thin:@//my-db:1521/orcl'
$env:JAWWY_DB_USER='EOC'
$env:JAWWY_DB_PASSWORD='secret'
mvn clean test
```

## Project structure

- `src/main/java`: config, payload loading, reporting, API clients, DB access, UI/page objects, workflow services
- `src/main/resources`: environment property files, logging config, and payload templates
- `src/test/java`: TestNG test classes and suite bootstrap

## CI/CD

- GitHub Actions workflow: `.github/workflows/test-automation.yml`
- Jenkins pipeline: `Jenkinsfile`
