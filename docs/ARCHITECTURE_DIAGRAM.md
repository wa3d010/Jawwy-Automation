# Jawwy Automation Architecture Diagram

This file gives a visual view of how the project is structured and how the SP activation flow moves through the framework.

## High-Level Architecture

```mermaid
flowchart TD
    A[TestNG Tests<br/>SpTest / SpBatchTest] --> B[BaseEocTest<br/>Suite setup and teardown]
    A --> C[JawwyOrderJourney<br/>Business workflow orchestrator]

    B --> D[FrameworkConfig<br/>Environment and runtime config]
    B --> E[ReportCleaner<br/>Clean old artifacts]
    B --> F[PlaywrightManager<br/>Browser warmup and lifecycle]

    C --> G[OrdersApiClient<br/>Create activation order]
    C --> H[BiometricsClient<br/>Send biometrics callback]
    C --> I[LmdCallbackClient<br/>Send LMD106 callback]
    C --> J[ComptelCallbackClient<br/>Send Comptel callbacks]
    C --> K[ProvisioningCallbackClient<br/>Send provisioning callback]
    C --> L[OrderMessageRepository<br/>Poll Oracle DB]
    C --> M[ManualTaskProcessor<br/>Complete manual UI task]

    G --> N[EOC Order API]
    H --> N
    I --> N
    J --> N
    K --> N

    L --> O[(Oracle DB<br/>EOC.CWMESSAGELOG)]

    M --> P[LoginPage]
    M --> Q[WorklistPage]
    P --> R[EOC Web UI]
    Q --> R

    S[PayloadLoader + TemplateEngine<br/>JSON payload preparation] --> G
    S --> H
    S --> I
    S --> J
    S --> K

    T[ActionLogger + Allure + Logback<br/>Reporting and logs] --> A
    T --> C
    T --> G
    T --> L
    T --> M
```

## SP Activation Flow

```mermaid
sequenceDiagram
    participant T as TestNG Test
    participant J as JawwyOrderJourney
    participant API as EOC APIs
    participant DB as Oracle DB
    participant UI as EOC UI

    T->>J: createOrder()
    J->>API: POST /eoc/om/v1/order
    API-->>J: orderId

    J->>DB: extractLmdId(orderId)
    DB-->>J: lmdId

    J->>UI: Start async manual task worker

    J->>API: POST /validate/biometrics/{orderId}
    API-->>J: 200 or retry

    J->>API: PATCH /updateOrder/lmdUpdate/{lmdId}
    API-->>J: 200

    loop until two ids found
        J->>DB: extractComptelIds(orderId)
        DB-->>J: comptelId
        J->>API: POST /updateOrder/comptelUpdate/{comptelId}
        API-->>J: 204
    end

    J->>DB: hasEsb027(orderId)
    DB-->>J: true or false

    alt ESB_027 found
        J->>API: POST /updateOrder/trigOrderProv/{orderId}
        API-->>J: 204
    else ESB_027 missing
        J-->>T: warning and continue
    end

    J->>UI: login, open worklist, search order, start work, skip task
    UI-->>J: manual task completed
    J-->>T: flow completed
```

## Layer View

```text
Tests
  SpTest
  SpBatchTest

Workflow Layer
  JawwyOrderJourney

Integration Layers
  API: OrdersApiClient, BiometricsClient, LmdCallbackClient, ComptelCallbackClient, ProvisioningCallbackClient
  DB:  OrderMessageRepository
  UI:  ManualTaskProcessor, LoginPage, WorklistPage, PlaywrightManager

Support Layers
  Config: FrameworkConfig
  Payloads: PayloadLoader, TemplateEngine, JSON files under src/main/resources/payloads
  Reporting: ActionLogger, ReportCleaner, Allure, Logback

Execution / Delivery
  Maven
  TestNG / Surefire
  Jenkins
  GitHub Actions
```

## Main Files To Open

1. `src/main/java/com/jawwy/automation/workflow/JawwyOrderJourney.java`
2. `src/test/java/com/jawwy/automation/tests/orders/SpTest.java`
3. `src/test/java/com/jawwy/automation/tests/orders/SpBatchTest.java`
4. `src/main/java/com/jawwy/automation/db/OrderMessageRepository.java`
5. `src/main/java/com/jawwy/automation/ui/ManualTaskProcessor.java`
