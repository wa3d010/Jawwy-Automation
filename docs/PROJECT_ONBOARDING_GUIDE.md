# Jawwy Automation Onboarding Guide

This document is a practical guide for new developers and software automation testers joining the project.

It explains:

- what the project is
- how the code is organized
- how the main SP activation flow works
- how to read the code in the right order
- what parts are most important for testing

---

## 1. What This Project Is

`JawwyAutomation` is a Java-based automation framework for validating the Jawwy activation flow in EOC.

In simple terms, it behaves like an automated operator that:

1. creates an activation order
2. waits for backend systems to generate IDs and log records
3. sends callback events that simulate external systems
4. opens the EOC UI
5. finishes the required manual task

This is not the business application itself.
It is a test/integration automation project that talks to the real system under test.

---

## 2. Big Picture Architecture

The project is organized in layers.

```text
Tests
  |
  v
Workflow / Journey Layer
  |
  +--> API Clients  ------> EOC HTTP APIs
  |
  +--> DB Repository -----> Oracle DB (EOC.CWMESSAGELOG)
  |
  +--> UI Processor ------> EOC Web UI via Playwright
  |
  v
Reporting / Logging / Config
```

### Main idea

- Tests stay small
- The workflow layer contains business steps
- The API layer talks to backend endpoints
- The DB layer checks system progress
- The UI layer handles the remaining manual work

---

## 3. Folder Guide

## `src/main/java`

This is the main framework implementation.

### `com.jawwy.automation.config`

- [FrameworkConfig.java](../src/main/java/com/jawwy/automation/config/FrameworkConfig.java)

Purpose:
- loads config files
- resolves the active environment
- applies environment variable and JVM property overrides
- validates URLs and DB configuration

Why it matters:
- almost every class depends on it

### `com.jawwy.automation.api`

- [ApiSupport.java](../src/main/java/com/jawwy/automation/api/ApiSupport.java)
- [OrdersApiClient.java](../src/main/java/com/jawwy/automation/api/OrdersApiClient.java)

Purpose:
- shared API request setup
- order creation request logic

### `com.jawwy.automation.api.callbacks`

- [BiometricsClient.java](../src/main/java/com/jawwy/automation/api/callbacks/BiometricsClient.java)
- [LmdCallbackClient.java](../src/main/java/com/jawwy/automation/api/callbacks/LmdCallbackClient.java)
- [ComptelCallbackClient.java](../src/main/java/com/jawwy/automation/api/callbacks/ComptelCallbackClient.java)
- [ProvisioningCallbackClient.java](../src/main/java/com/jawwy/automation/api/callbacks/ProvisioningCallbackClient.java)

Purpose:
- simulate the external systems that send status updates back into EOC

### `com.jawwy.automation.db`

- [OrderMessageRepository.java](../src/main/java/com/jawwy/automation/db/OrderMessageRepository.java)

Purpose:
- reads Oracle logs from `EOC.CWMESSAGELOG`
- finds generated IDs
- checks whether certain backend steps already happened

### `com.jawwy.automation.payload`

- [PayloadLoader.java](../src/main/java/com/jawwy/automation/payload/PayloadLoader.java)
- [TemplateEngine.java](../src/main/java/com/jawwy/automation/payload/TemplateEngine.java)

Purpose:
- load JSON payloads from resources
- replace placeholders like `{{orderId}}`

### `com.jawwy.automation.reporting`

- [ActionLogger.java](../src/main/java/com/jawwy/automation/reporting/ActionLogger.java)
- [ReportCleaner.java](../src/main/java/com/jawwy/automation/reporting/ReportCleaner.java)

Purpose:
- log each business step in a readable way
- push steps into Allure
- clean old report artifacts before execution

### `com.jawwy.automation.ui`

- [PlaywrightManager.java](../src/main/java/com/jawwy/automation/ui/PlaywrightManager.java)
- [ManualTaskProcessor.java](../src/main/java/com/jawwy/automation/ui/ManualTaskProcessor.java)

Purpose:
- manage browser lifecycle
- process the manual EOC task

### `com.jawwy.automation.ui.pages`

- [LoginPage.java](../src/main/java/com/jawwy/automation/ui/pages/LoginPage.java)
- [WorklistPage.java](../src/main/java/com/jawwy/automation/ui/pages/WorklistPage.java)

Purpose:
- page objects for UI automation
- keep selectors and UI actions separate from workflow logic

### `com.jawwy.automation.workflow`

- [JawwyOrderJourney.java](../src/main/java/com/jawwy/automation/workflow/JawwyOrderJourney.java)

Purpose:
- the main business orchestrator
- the best file to understand the real flow

---

## `src/main/resources`

### `config`

- [application.properties](../src/main/resources/config/application.properties)
- [application-local.properties](../src/main/resources/config/application-local.properties)
- [application-sit.properties](../src/main/resources/config/application-sit.properties)
- [application-uat.properties](../src/main/resources/config/application-uat.properties)

Purpose:
- define environment-specific settings

Important note:
- `sit` and `uat` currently contain placeholder hosts and must be overridden with real values

### `payloads`

Order payload:
- [NewAct-Online.json](../src/main/resources/payloads/orders/NewAct-Online.json)

Callback payloads:
- [Biometrics.json](../src/main/resources/payloads/callbacks/Biometrics.json)
- [LMD106.json](../src/main/resources/payloads/callbacks/LMD106.json)
- [Comptel70.json](../src/main/resources/payloads/callbacks/Comptel70.json)
- [OrderProvisioning.json](../src/main/resources/payloads/callbacks/OrderProvisioning.json)

Purpose:
- store reusable request bodies outside the Java code

### Logging

- [logback.xml](../src/main/resources/logback.xml)

Purpose:
- writes logs to console and `logs/test-run.log`

---

## `src/test/java`

### Suite bootstrap

- [BaseEocTest.java](../src/test/java/com/jawwy/automation/tests/BaseEocTest.java)

Purpose:
- setup and cleanup for the whole suite
- reload config
- warm up Playwright
- close browser resources after execution

### Single-flow SP test

- [SpTest.java](../src/test/java/com/jawwy/automation/tests/orders/SpTest.java)

Purpose:
- runs one SP order end-to-end
- useful for debugging and local flow validation

### Jenkins batch runner

- [SpBatchTest.java](../src/test/java/com/jawwy/automation/tests/orders/SpBatchTest.java)

Purpose:
- runs the SP flow multiple times
- stops on first failure
- writes a summary report for Jenkins

---

## 4. Main SP Flow Walkthrough

The core flow lives in:

- [JawwyOrderJourney.java](../src/main/java/com/jawwy/automation/workflow/JawwyOrderJourney.java)

### Step-by-step

### Step 1: Create order

Method:
- `createOrder()`

What happens:
- [OrdersApiClient.java](../src/main/java/com/jawwy/automation/api/OrdersApiClient.java) loads `NewAct-Online.json`
- dynamic values are inserted
- order is created through the EOC API

### Step 2: Extract LMD ID

Method:
- `OrderMessageRepository.extractLmdId(orderId)`

What happens:
- Oracle DB is queried
- the framework scans `EOC.CWMESSAGELOG`
- it finds the row for `ESB_021_CreateLogisticsDeliveryRequest`
- it parses the generated LMD ID

### Step 3: Start manual task worker

Method:
- `manualTaskProcessor.startSharingLimitsTaskAsync(createdOrderId)`

What happens:
- a background UI worker starts early
- it prepares for the manual task while callback steps continue

### Step 4: Send biometrics callback

Method:
- `sendBiometrics()`

What happens:
- the framework posts a biometrics callback to EOC
- this simulates an external validation system

### Step 5: Send LMD106 callback

Method:
- `sendLmd106()`

What happens:
- a logistics-related update is sent using the LMD ID found from the DB

### Step 6: Send two Comptel callbacks

Method:
- `sendComptelCallbacks()`

What happens:
- the DB is polled until Comptel IDs appear
- two callbacks are sent

### Step 7: Send provisioning callback if ready

Method:
- `sendProvisioningIfAvailable()`

What happens:
- the framework waits for `ESB_027`
- if found, it sends a provisioning callback
- if not found, it logs a warning and skips it

### Step 8: Complete manual task in UI

Method:
- `handleManualTask()`

What happens:
- the framework logs into EOC
- opens the worklist page
- searches by order ID
- waits for `SHARING_LIMITS`
- starts work and skips the task

---

## 5. How Tests Are Meant To Be Used

## Local developer/tester usage

Use [SpTest.java](../src/test/java/com/jawwy/automation/tests/orders/SpTest.java) when:
- you want to debug one flow step by step
- you want to see where a single order fails

## Jenkins / batch usage

Use [SpBatchTest.java](../src/test/java/com/jawwy/automation/tests/orders/SpBatchTest.java) when:
- you want to run the same SP flow multiple times
- you want an execution summary
- you want CI/CD parameterization

Important:
- batch mode is meant to skip `SpTest`
- Jenkins should run:

```text
mvn clean test -Denv=<env> -Dtest=SpBatchTest -Dorder.count=<n> -Dbatch.mode=true
```

---

## 6. CI/CD Guide

## Jenkins

Main file:
- [Jenkinsfile](../Jenkinsfile)

What it does:
- accepts `TARGET_ENV`
- accepts `ORDER_COUNT`
- injects Jenkins credentials as environment variables
- runs `SpBatchTest`
- archives test reports

Expected credentials:
- `jawwy-api-base-url`
- `jawwy-ui-base-url`
- `jawwy-ui-username`
- `jawwy-ui-password`
- `jawwy-db-url`
- `jawwy-db-user`
- `jawwy-db-password`

## GitHub Actions

Main file:
- [.github/workflows/test-automation.yml](../.github/workflows/test-automation.yml)

What it does:
- runs Maven tests on GitHub-hosted CI
- uses repository secrets for environment values
- uploads reports/logs as artifacts

---

## 7. Beginner Reading Order

If you are new, read the project in this order:

1. [README.md](../README.md)
2. [pom.xml](../pom.xml)
3. [application.properties](../src/main/resources/config/application.properties)
4. [FrameworkConfig.java](../src/main/java/com/jawwy/automation/config/FrameworkConfig.java)
5. [JawwyOrderJourney.java](../src/main/java/com/jawwy/automation/workflow/JawwyOrderJourney.java)
6. [SpTest.java](../src/test/java/com/jawwy/automation/tests/orders/SpTest.java)
7. [SpBatchTest.java](../src/test/java/com/jawwy/automation/tests/orders/SpBatchTest.java)
8. [OrdersApiClient.java](../src/main/java/com/jawwy/automation/api/OrdersApiClient.java)
9. [OrderMessageRepository.java](../src/main/java/com/jawwy/automation/db/OrderMessageRepository.java)
10. [ManualTaskProcessor.java](../src/main/java/com/jawwy/automation/ui/ManualTaskProcessor.java)
11. [LoginPage.java](../src/main/java/com/jawwy/automation/ui/pages/LoginPage.java)
12. [WorklistPage.java](../src/main/java/com/jawwy/automation/ui/pages/WorklistPage.java)
13. [Jenkinsfile](../Jenkinsfile)

---

## 8. Tester-Focused Risk Map

These are the most fragile areas in the current project:

### Environment/config risks

- wrong `env` selection
- placeholder SIT/UAT config
- localhost assumptions
- Jenkins credentials mismatch

### API timing risks

- biometrics callback can happen before backend readiness
- provisioning depends on DB state appearing in time

### DB risks

- LMD/Comptel IDs are discovered through DB polling
- backend lag may cause false failures

### UI risks

- enterprise UI loading times
- selector changes
- wrong task row selected
- worklist page readiness

### CI risks

- Jenkins Windows-specific behavior
- browser availability
- Playwright download/network constraints

---

## 9. Suggested Improvement Plan

Priority order:

1. stabilize biometrics timing and response diagnostics
2. stabilize manual UI selectors and capture screenshots on failure
3. separate smoke tests from full regression tests
4. remove archetype leftovers and unused dependencies
5. add unit tests for config/payload helpers
6. document real environment setup more clearly

---

## 10. Quick Definitions For Beginners

### Orchestrator
A class that coordinates many smaller steps.
Here: `JawwyOrderJourney`.

### Page Object
A class that represents one UI screen.
Here: `LoginPage` and `WorklistPage`.

### Repository
A class that talks to the database.
Here: `OrderMessageRepository`.

### Callback
A request sent back by another system to report status.
Here: biometrics, LMD, Comptel, provisioning callbacks.

### CI/CD
Automation that runs tests in build pipelines.
Here: GitHub Actions and Jenkins.

---

## 11. Final Takeaway

The most important mental model is:

- tests are just triggers
- the journey class is the business brain
- API clients talk to EOC
- the repository watches backend progress
- Playwright finishes the UI step
- config decides where everything points

If you understand those five things, the rest of the project becomes much easier to follow.
