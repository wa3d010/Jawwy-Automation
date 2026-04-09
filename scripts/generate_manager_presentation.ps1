param(
    [string]$OutputPath = "docs\\JawwyAutomation_Manager_Presentation.pptx"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$fullOutputPath = Join-Path $repoRoot $OutputPath
$outputDirectory = Split-Path -Parent $fullOutputPath

if (!(Test-Path $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

$ppLayoutTitle = 1
$ppLayoutText = 2

function Set-Text {
    param(
        $shape,
        [string]$text,
        [int]$fontSize = 24,
        [int]$rgb = 0x1F1F1F,
        [bool]$bold = $false
    )

    $shape.TextFrame.TextRange.Text = $text
    $shape.TextFrame.TextRange.Font.Size = $fontSize
    $shape.TextFrame.TextRange.Font.Bold = [int]$bold
    $shape.TextFrame.TextRange.Font.Color.RGB = $rgb
}

function Add-BulletsSlide {
    param(
        $presentation,
        [string]$title,
        [string[]]$bullets
    )

    $slide = $presentation.Slides.Add($presentation.Slides.Count + 1, $ppLayoutText)
    Set-Text $slide.Shapes.Title $title 28 0x203040 $true

    $body = $slide.Shapes.Item(2)
    $text = [string]::Join("`r", ($bullets | ForEach-Object { [char]0x2022 + " " + $_ }))
    Set-Text $body $text 20 0x333333 $false
    return $slide
}

function Add-TitleSlide {
    param(
        $presentation,
        [string]$title,
        [string]$subtitle
    )

    $slide = $presentation.Slides.Add($presentation.Slides.Count + 1, $ppLayoutTitle)
    Set-Text $slide.Shapes.Title $title 30 0x203040 $true
    Set-Text $slide.Shapes.Item(2) $subtitle 20 0x555555 $false
    return $slide
}

function Add-ArchitectureSlide {
    param($presentation)

    $slide = $presentation.Slides.Add($presentation.Slides.Count + 1, 12)

    $title = $slide.Shapes.AddTextbox(1, 30, 20, 620, 40)
    Set-Text $title "Architecture Overview" 28 0x203040 $true

    $boxColor = 0xD9EAF7
    $borderColor = 0x6B8BA4
    $textColor = 0x203040

    $tests = $slide.Shapes.AddShape(1, 40, 90, 170, 60)
    $workflow = $slide.Shapes.AddShape(1, 240, 90, 170, 60)
    $integrations = $slide.Shapes.AddShape(1, 440, 60, 220, 120)
    $support = $slide.Shapes.AddShape(1, 240, 200, 170, 90)
    $delivery = $slide.Shapes.AddShape(1, 40, 220, 170, 90)

    foreach ($shape in @($tests, $workflow, $integrations, $support, $delivery)) {
        $shape.Fill.ForeColor.RGB = $boxColor
        $shape.Line.ForeColor.RGB = $borderColor
    }

    Set-Text $tests "Tests`rSpTest`rSpBatchTest" 18 $textColor $true
    Set-Text $workflow "Workflow`rJawwyOrderJourney" 18 $textColor $true
    Set-Text $integrations "Integrations`rAPI Clients`rOracle DB Repository`rPlaywright UI Processor" 18 $textColor $true
    Set-Text $support "Support`rFrameworkConfig`rPayloads`rReporting" 18 $textColor $true
    Set-Text $delivery "Execution`rMaven`rJenkins`rGitHub Actions" 18 $textColor $true

    $slide.Shapes.AddConnector(1, 210, 120, 240, 120) | Out-Null
    $slide.Shapes.AddConnector(1, 410, 120, 440, 120) | Out-Null
    $slide.Shapes.AddConnector(1, 325, 150, 325, 200) | Out-Null
    $slide.Shapes.AddConnector(1, 210, 250, 240, 245) | Out-Null

    return $slide
}

$powerPoint = $null
$presentation = $null

try {
    $powerPoint = New-Object -ComObject PowerPoint.Application
    $powerPoint.Visible = -1
    $presentation = $powerPoint.Presentations.Add()

    Add-TitleSlide $presentation `
        "Jawwy Automation" `
        "Manager overview of the SP activation automation framework, CI/CD enablement, and business value"

    Add-BulletsSlide $presentation `
        "Executive Summary" `
        @(
            "This project automates the Jawwy SP activation journey end to end across API, database, and UI layers."
            "It reduces manual validation effort and makes cross-system regression testing repeatable."
            "It is already integrated with Jenkins and GitHub Actions for scalable execution."
            "The framework also supports batch execution and manager-friendly reporting."
        )

    Add-BulletsSlide $presentation `
        "Business Value" `
        @(
            "Automates a high-effort telecom activation scenario that normally spans multiple backend systems."
            "Shortens validation cycles for lower environments and release readiness."
            "Detects failures across order creation, callback processing, backend propagation, and UI task completion."
            "Provides a reusable base for smoke, regression, and CI-triggered test execution."
        )

    Add-ArchitectureSlide $presentation | Out-Null

    Add-BulletsSlide $presentation `
        "End-to-End Flow" `
        @(
            "Create activation order through EOC API."
            "Poll Oracle DB to discover generated IDs and backend readiness signals."
            "Send biometrics, LMD, Comptel, and provisioning callbacks."
            "Open the EOC UI and finish the remaining manual task automatically."
            "Publish logs and reports through TestNG, Allure, Jenkins, and GitHub Actions."
        )

    Add-BulletsSlide $presentation `
        "Current Delivery Setup" `
        @(
            "Local execution through Maven and TestNG."
            "Batch execution for Jenkins using parameterized order count."
            "GitHub Actions workflow for CI-driven test execution."
            "Reporting artifacts archived from target, logs, and allure-results."
        )

    Add-BulletsSlide $presentation `
        "Strengths" `
        @(
            "Clear layered structure: config, API, DB, UI, workflow, tests."
            "Thin test classes and reusable business journey orchestration."
            "Externalized payloads and environment-aware configuration."
            "Strong practical value for integration and release validation."
        )

    Add-BulletsSlide $presentation `
        "Current Risks" `
        @(
            "Biometrics callback timing is sensitive to backend readiness."
            "Manual task UI step is the most fragile part because it depends on enterprise page behavior."
            "Environment configuration quality is critical; wrong URLs or placeholders break the suite fast."
            "The framework currently relies heavily on live systems rather than mocks."
        )

    Add-BulletsSlide $presentation `
        "Recommended Next Steps" `
        @(
            "Stabilize callback timing and improve diagnostics for non-200 responses."
            "Capture screenshots and traces automatically on UI failure."
            "Separate smoke, regression, and batch suites more clearly."
            "Remove leftover scaffold files and unused dependencies to simplify onboarding."
        )

    Add-BulletsSlide $presentation `
        "Manager Takeaway" `
        @(
            "This framework already delivers meaningful end-to-end automation value."
            "It connects business workflow validation with CI/CD execution."
            "The remaining improvements are mainly about hardening reliability, not rebuilding architecture."
            "It is a strong foundation for release confidence in the Jawwy activation flow."
        )

    $presentation.SaveAs($fullOutputPath)
}
finally {
    if ($presentation -ne $null) {
        $presentation.Close()
    }
    if ($powerPoint -ne $null) {
        $powerPoint.Quit()
    }
}

Write-Output "Created presentation: $fullOutputPath"
