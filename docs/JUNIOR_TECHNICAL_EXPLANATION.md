# Jawwy Automation Project: Beginner-Friendly Technical Explanation

## 1. High-Level Overview

### What this project is

This project is a **test automation framework** written in **Java**.

That means:
- it is **not** the real Jawwy business application
- it is a tool that **tests** the real system
- it automates a full business journey from start to finish

In simple words:

> This project behaves like a smart robot tester.  
> It creates a new order, waits for backend processing, sends callback updates, checks the database, opens the UI, completes a manual task, and then reports whether the whole process worked.

### What problem it solves

In a real enterprise system, a new activation order does not finish in one step.
It usually needs:

- an API call to create the order
- backend systems to generate more IDs
- callback messages from external systems
- database checks to confirm progress
- a UI action to complete a manual business task

Doing this by hand is:
- slow
- repetitive
- error-prone
- hard to repeat in CI/CD tools like Jenkins

This project solves that by automating the whole flow.

### Who would use it

This project is likely used by:

- QA engineers
- software automation testers
- integration testers
- release validation teams
- developers debugging order flow issues

### Why it exists

It exists to answer questions like:

- Can the system create a new activation order successfully?
- Do all required callbacks work correctly?
- Are backend IDs generated correctly?
- Does the order move through the expected states?
- Can the remaining manual UI step be completed?
- Can we run this repeatedly in Jenkins for regression testing?

### What kind of system it is

This is best described as a:

- **Java automation framework**
- **end-to-end integration test project**
- **workflow-based test suite**

It is **not**:

- a frontend app
- a backend service
- a standalone product for customers

---

## 2. Project Structure

This section explains the folders and files in beginner-friendly language.

## Root level

### `pom.xml`

This file exists to:
- define dependencies
- define build settings
- tell Maven how to compile and run the project

It is used by:
- Maven
- Jenkins
- GitHub Actions
- developers running `mvn clean test`

Why it matters:
- without this file, Maven would not know which libraries to download or how to run tests

### `Jenkinsfile`

This file exists to:
- define how Jenkins should run this project

It is used by:
- Jenkins pipeline jobs

Why it matters:
- it lets Jenkins run the batch flow, pass parameters like order count, and archive reports

### `README.md`

This file exists to:
- explain how to run the project
- give quick setup instructions

It is mainly for:
- developers
- testers
- new team members

### `.github/workflows/`

This folder exists to:
- store GitHub Actions CI workflow files

It is used by:
- GitHub when code is pushed or workflows are triggered

### `.mvn/`

This folder exists to:
- store Maven-level settings for this repository

Important file:
- `.mvn/maven.config`

Why it matters:
- this repo uses a local Maven repository folder instead of fully relying on the global user cache

### `docs/`

This folder exists to:
- store project documentation

It is used by:
- new developers
- testers
- managers

Current documents include:
- onboarding guide
- architecture diagram
- presentation files
- this explanation document

### `scripts/`

This folder exists to:
- store helper scripts

Example:
- PowerPoint generation script

---

## `src/main/java`

This folder exists to:
- store the main automation framework code

It contains the logic that actually performs:
- API calls
- database checks
- UI automation
- workflow orchestration

Think of it as:

> “The engine of the automation project.”

### `com.jawwy.automation.api`

This package exists to:
- hold API-related classes

Files:
- `ApiSupport.java`
- `OrdersApiClient.java`

Why it exists:
- API logic should be separated from UI logic and DB logic

### `com.jawwy.automation.api.callbacks`

This package exists to:
- represent external systems sending callback updates into the main system

Files:
- `BiometricsClient.java`
- `LmdCallbackClient.java`
- `ComptelCallbackClient.java`
- `ProvisioningCallbackClient.java`

Why it exists:
- each callback is a different integration and business step
- separating them makes the code easier to understand and maintain

### `com.jawwy.automation.config`

This package exists to:
- load environment configuration
- centralize all runtime settings

File:
- `FrameworkConfig.java`

Why it exists:
- instead of scattering URLs, usernames, passwords, and timing values everywhere, the project keeps them in one place

### `com.jawwy.automation.db`

This package exists to:
- read backend state directly from the database

File:
- `OrderMessageRepository.java`

Why it exists:
- some important information is not returned directly by the API, so the framework reads message log data from Oracle

### `com.jawwy.automation.payload`

This package exists to:
- load request payload templates
- replace placeholders inside them

Files:
- `PayloadLoader.java`
- `TemplateEngine.java`

Why it exists:
- request bodies should not be hardcoded in Java line by line
- templates are cleaner and easier to edit

### `com.jawwy.automation.reporting`

This package exists to:
- clean previous reports
- standardize logging of actions

Files:
- `ActionLogger.java`
- `ReportCleaner.java`

### `com.jawwy.automation.ui`

This package exists to:
- control Playwright
- execute the manual UI task flow

Files:
- `PlaywrightManager.java`
- `ManualTaskProcessor.java`

### `com.jawwy.automation.ui.pages`

This package exists to:
- model UI pages using the Page Object pattern

Files:
- `LoginPage.java`
- `WorklistPage.java`

Why it exists:
- selectors and UI actions belong in page objects, not scattered around the tests

### `com.jawwy.automation.workflow`

This package exists to:
- coordinate the full business journey

File:
- `JawwyOrderJourney.java`

Why it exists:
- test files should stay simple
- the complicated flow should live in one orchestration class

---

## `src/main/resources`

This folder exists to:
- store non-code files needed at runtime

### `config/`

This folder exists to:
- store application configuration files

Files:
- `application.properties`
- `application-local.properties`
- `application-sit.properties`
- `application-uat.properties`

Why it exists:
- different environments need different URLs, DB values, and timing settings

### `payloads/`

This folder exists to:
- store JSON templates for order creation and callback requests

Subfolders:
- `orders/`
- `callbacks/`

Why it exists:
- makes payload maintenance easier
- avoids writing huge JSON strings in Java code

### `logback.xml`

This file exists to:
- configure logging behavior

Why it matters:
- logs are essential in automation because they help explain where a flow failed

### `META-INF/maven` and `archetype-resources`

These appear to be leftover Maven archetype files.

Simple explanation:
- an archetype is like a project template
- these files are not part of the main order automation flow

So for a beginner:
- you do **not** need to read them first

---

## `src/test/java`

This folder exists to:
- store the actual test entry points

Files:
- `BaseEocTest.java`
- `SpTest.java`
- `SpBatchTest.java`

This is where execution starts when tests are run.

---

## 3. Entry Points

### What is an entry point?

An **entry point** is the place where execution starts.

In normal applications, this is often a `main()` method.
In this project, the main entry points are **tests**.

### Main entry points in this project

#### `SpTest.java`

This file is an entry point because:
- TestNG discovers it as a test class
- Maven Surefire runs it

It is used for:
- running one complete SP journey step by step

#### `SpBatchTest.java`

This file is also an entry point.

It is used for:
- Jenkins batch execution
- running the full SP journey multiple times
- generating a simple run summary

### Files that are not run directly

Examples:
- `OrdersApiClient.java`
- `BiometricsClient.java`
- `OrderMessageRepository.java`
- `LoginPage.java`
- `WorklistPage.java`
- `JawwyOrderJourney.java`

These are not entry points because:
- they are helper or service classes
- they are called by higher-level classes

### Execution flow from entry point downward

Example flow:

1. Maven runs `SpTest` or `SpBatchTest`
2. The test creates or uses `JawwyOrderJourney`
3. `JawwyOrderJourney` calls:
   - API client classes
   - DB repository
   - UI processor
4. UI processor calls page objects
5. Page objects interact with the browser

So the flow goes from:

> Test -> Workflow -> Helpers -> External systems

---

## 4. Detailed File Explanation

This section explains the most important files one by one.

## `FrameworkConfig.java`

### Responsibility
- load configuration
- select environment
- expose settings through methods
- validate dangerous or invalid config early

### Why this logic is here

Configuration is a cross-cutting concern.
That means many parts of the project need it.

So instead of writing:
- API URL in API files
- DB URL in DB files
- username/password in UI files

the project centralizes all of that in one config class.

### Who uses it
- `ApiSupport`
- `OrdersApiClient`
- callback clients
- `OrderMessageRepository`
- `PlaywrightManager`
- `LoginPage`
- `WorklistPage`
- `ManualTaskProcessor`
- `BaseEocTest`

## `ApiSupport.java`

### Responsibility
- provide shared API request setup
- build reusable Rest Assured request specifications

### Why it exists

If each API client built its own HTTP setup from scratch, code would be repeated.

This class reduces duplication by centralizing:
- base URL handling
- content type
- relaxed HTTPS validation
- logging/reporting helpers

## `OrdersApiClient.java`

### Responsibility
- create a new order through the EOC API

### Main logic
- load the order JSON template
- replace placeholders with dynamic values
- call the order creation endpoint
- validate the response
- extract the created order ID

### Why this logic belongs here

Order creation is a specific API responsibility.
It should not live in tests or UI classes.

## `BiometricsClient.java`

### Responsibility
- send the biometrics callback

### Main logic
- wait an initial delay if configured
- build the biometrics payload
- send POST request
- retry if the backend returns a failure

### Why it belongs here

This is a dedicated integration.
It is clearer and cleaner when each callback gets its own client class.

## `LmdCallbackClient.java`

### Responsibility
- send the LMD callback for the resolved LMD ID

### Why it matters

The order cannot continue correctly unless the logistics-related callback is sent.

## `ComptelCallbackClient.java`

### Responsibility
- send the Comptel callback

### Important business detail

The project expects **two** Comptel IDs in the order journey.

## `ProvisioningCallbackClient.java`

### Responsibility
- send the provisioning callback

### Why it is separate

Provisioning is a later business step and has different timing rules.

## `OrderMessageRepository.java`

### Responsibility
- query Oracle database message logs

### Main logic
- find LMD ID
- find Comptel IDs
- check whether provisioning is ready

### Why this logic belongs here

Database code should be isolated from:
- tests
- UI code
- API code

That makes the project easier to maintain.

## `PayloadLoader.java`

### Responsibility
- load payload files from resources

### Why it exists

Without it, every API class would need to manually read files.

## `TemplateEngine.java`

### Responsibility
- replace placeholders inside payload templates

### Example

If a JSON file contains:
- `${orderId}`

this class replaces it with the actual runtime order ID.

## `ActionLogger.java`

### Responsibility
- centralize logging/reporting messages for actions

### Why it exists

Automation becomes easier to debug when actions are consistently logged.

## `ReportCleaner.java`

### Responsibility
- clean previous report folders and files

### Why it exists

Old reports can confuse test results.
This class helps each run start cleanly.

## `PlaywrightManager.java`

### Responsibility
- manage browser startup and shutdown

### Main logic
- initialize Playwright
- select local browser executable if available
- create browser/page
- stop and clean resources

### Why it belongs here

Browser lifecycle is shared infrastructure.
It should not be duplicated in page objects.

## `LoginPage.java`

### Responsibility
- perform login to the EOC UI

### Main logic
- navigate to login
- detect whether login is needed
- fill username/password
- wait for post-login state

### Why it belongs here

This is a UI page object.
Its job is to represent the login screen.

## `WorklistPage.java`

### Responsibility
- interact with the worklist page

### Main logic
- open worklist
- wait until page is ready
- search by order ID
- find the right task row
- start work
- skip task

### Why it belongs here

The worklist is a separate screen with its own selectors and actions.

## `ManualTaskProcessor.java`

### Responsibility
- complete the manual business task

### Main logic
- optionally start async worker
- log in
- open worklist
- wait for the `SHARING_LIMITS` row
- start work
- skip the task

### Why this is not in the test file

Because this is not “test orchestration.”
It is a lower-level UI business operation.

## `JawwyOrderJourney.java`

### Responsibility
- orchestrate the full business flow

### Why it is so important

This is the heart of the project.

It connects:
- order creation
- DB lookup
- callbacks
- provisioning readiness
- manual task processing

If a beginner wants to understand the project quickly, this is one of the first files to read.

## `BaseEocTest.java`

### Responsibility
- shared suite setup and teardown

### Main logic
- clean reports
- reload config
- log environment
- warm browser
- shut down async worker and browser after suite

### Why it exists

Shared setup should not be duplicated in every test class.

## `SpTest.java`

### Responsibility
- run one SP journey step by step using dependent TestNG methods

### Why it exists

It is useful for:
- debugging
- understanding the flow
- seeing exactly which step failed

## `SpBatchTest.java`

### Responsibility
- run the SP flow multiple times
- stop on first failure
- produce a small summary for Jenkins

### Why it exists

Jenkins needs a batch-friendly test entry point.

---

## 5. Technologies Used

## Java

### What it is
- a programming language

### Why it is used here
- strong ecosystem for enterprise systems and automation
- works well with Maven, TestNG, JDBC, and Playwright

### Analogy
- Java is the language the team chose to write the robot tester

## Maven

### What it is
- a build tool

### Why it is used
- downloads libraries
- compiles code
- runs tests

### Analogy
- Maven is like a project manager for the codebase

## TestNG

### What it is
- a Java testing framework

### Why it is used
- organizes tests
- supports dependencies between test methods
- supports lifecycle hooks like before-suite and after-suite

## Rest Assured

### What it is
- a Java library for API testing

### Why it is used
- makes HTTP requests easy to write and verify

## Playwright

### What it is
- a browser automation tool

### Why it is used
- automates the manual task in the EOC UI

### Analogy
- Playwright is like a robot user clicking through the browser

## JDBC

### What it is
- Java database connectivity API

### Why it is used
- lets the project query Oracle directly

## Oracle JDBC

### What it is
- Oracle-specific database driver

### Why it is used
- the system stores important runtime state in Oracle

## Logback + SLF4J

### What they are
- logging libraries

### Why they are used
- to print useful runtime information
- to make failures easier to debug

## Allure

### What it is
- a reporting tool for test runs

### Why it is used
- creates more readable test reports

## Jenkins

### What it is
- a CI/CD automation server

### Why it is used
- runs the batch flow
- accepts parameters
- archives results

## GitHub Actions

### What it is
- another CI/CD automation system

### Why it is used
- lets the project run in GitHub pipelines too

---

## 6. Design Patterns & Architecture

## Layered Architecture

### What it means

Code is separated by responsibility into layers.

In this project:
- config layer
- API layer
- DB layer
- UI layer
- workflow layer
- test layer

### Why this is good

It reduces chaos.
Each class has a clearer job.

## Page Object Pattern

### What problem it solves

UI selectors can get messy if written directly in tests.

### How it is used here

- `LoginPage.java`
- `WorklistPage.java`

These classes hide the selectors and expose meaningful actions.

### Why it is a good choice

It keeps tests readable and reduces duplication.

## Repository Pattern

### What problem it solves

Database access should be isolated from business flow code.

### How it is used here

- `OrderMessageRepository.java`

### Why it is useful

If the database queries change, they can be updated in one place.

## Orchestrator / Workflow Pattern

### What problem it solves

Complex multi-step flows become messy if spread across many tests.

### How it is used here

- `JawwyOrderJourney.java`

### Why it is useful

It gives the project a single “story of execution.”

## Base Test Pattern

### What problem it solves

Shared setup and cleanup should not be copied into every test.

### How it is used here

- `BaseEocTest.java`

---

## 7. End-to-End Flow Explanation

This is the most important “story” of the project.

## Step-by-step flow

1. The test starts
   - `SpTest` or `SpBatchTest` is launched by Maven/TestNG

2. Shared setup happens
   - `BaseEocTest` cleans reports
   - configuration is loaded
   - browser warmup begins

3. The order is created
   - `JawwyOrderJourney.createOrder()`
   - `OrdersApiClient` sends the order creation request

4. The framework waits for backend-generated data
   - `OrderMessageRepository` polls the DB
   - LMD ID is extracted

5. The manual task worker may start in parallel
   - `ManualTaskProcessor.startSharingLimitsTaskAsync(orderId)`

6. Biometrics callback is sent
   - `BiometricsClient`

7. LMD callback is sent
   - `LmdCallbackClient`

8. Comptel IDs are found in DB
   - `OrderMessageRepository`

9. Comptel callbacks are sent
   - `ComptelCallbackClient`

10. Provisioning readiness is checked
    - DB is queried

11. Provisioning callback is sent if available
    - `ProvisioningCallbackClient`

12. Manual UI task is completed
    - browser opens UI
    - login happens if needed
    - worklist page opens
    - order row is found
    - task is started and skipped

13. Test finishes
    - success or failure is reported
    - browser resources are closed

---

## 8. Important Technical Terms

## API
- a way for systems to talk over HTTP

## Callback
- a message sent back by another system to report progress or status

## Payload
- the body of an API request, often JSON

## Template
- a reusable file with placeholders to be replaced later

## Repository
- a class that reads or writes data storage, usually a database

## Page Object
- a class that represents one UI page or screen

## Workflow / Journey
- the complete business process from start to end

## CI/CD
- automation for building, testing, and delivering software

## Jenkins
- a tool that runs pipelines

## TestNG
- a Java test framework

## Playwright
- a tool for controlling browsers automatically

## JDBC
- Java’s standard way to talk to databases

## Oracle
- the database technology used by the system

## LMD
- a business-specific ID used later in the order flow
- exact business meaning is domain-specific, but in code it is an important backend-generated identifier

## Comptel
- another external/business integration step in the order journey

## Provisioning
- the step where the order is prepared to become operational in downstream systems

## SHARING_LIMITS
- the specific manual task type the UI automation is looking for

---

## 9. Strengths, Weaknesses & Risks

## Strengths

- clear package separation
- business flow centralized in one journey class
- page object pattern is used
- payload templates are externalized
- Jenkins and GitHub Actions support exist
- DB polling is isolated in a repository class

## Weaknesses

- very dependent on real environments
- timing issues can make tests flaky
- UI selectors are fragile
- almost no unit tests
- some old scaffold files and possibly unused dependencies remain

## Risks

- backend not ready when callback is sent
- DB message logs appear later than expected
- UI takes longer to load in Jenkins
- browser download or launch problems
- environment URLs or credentials are wrong
- `SpTest` and `SpBatchTest` may both run if commands are not used carefully

## What a junior engineer should be careful about

- do not hardcode values in many places
- always check which environment is active
- be careful when changing waits and retries
- keep UI selectors as stable as possible
- verify whether a failure is code-related or environment-related

---

## 10. Beginner-Friendly Summary

### Simple summary

This project is a Java automation framework that tests a full Jawwy activation flow.

It:
- creates an order
- checks the database
- sends callbacks
- uses the browser for a final manual task
- reports the result

### Mental model

The best way to think about this project is:

> A central workflow class controls several specialized helpers:
> one for APIs, one for DB checks, one for UI, and one for reporting.

So mentally, think of the project like this:

- tests start the journey
- the journey coordinates the work
- helpers perform the individual actions

### Which files a beginner should read first

Read in this order:

1. `README.md`
2. `src/main/resources/config/application.properties`
3. `src/main/java/com/jawwy/automation/config/FrameworkConfig.java`
4. `src/main/java/com/jawwy/automation/workflow/JawwyOrderJourney.java`
5. `src/test/java/com/jawwy/automation/tests/orders/SpTest.java`
6. `src/test/java/com/jawwy/automation/tests/orders/SpBatchTest.java`
7. `src/main/java/com/jawwy/automation/db/OrderMessageRepository.java`
8. `src/main/java/com/jawwy/automation/ui/ManualTaskProcessor.java`
9. `src/main/java/com/jawwy/automation/ui/pages/LoginPage.java`
10. `src/main/java/com/jawwy/automation/ui/pages/WorklistPage.java`

### Final beginner advice

Do not try to understand every file at once.

Instead:
- first understand the journey
- then understand the helpers it calls
- then understand the environment configuration

That approach will make the project feel much simpler.
