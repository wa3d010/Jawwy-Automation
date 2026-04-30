package com.jawwy.automation.tests;

import com.jawwy.automation.config.FrameworkConfig;
import com.jawwy.automation.reporting.ReportCleaner;
import com.jawwy.automation.ui.ManualTaskProcessor;
import com.jawwy.automation.ui.PlaywrightManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

public abstract class BaseEocTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseEocTest.class);

    @BeforeSuite(alwaysRun = true)
    public void beforeSuite() {

        // Always clean previous artifacts
        ReportCleaner.cleanPreviousRunArtifacts();

        boolean batchMode = Boolean.parseBoolean(System.getProperty("batch.mode", "false"));

        try {
            FrameworkConfig.reload();
            FrameworkConfig config = FrameworkConfig.getInstance();
            LOGGER.info("Starting suite on environment '{}'", config.environmentName());

            // Browser warmup should NEVER block execution
            Thread browserWarmup = new Thread(() -> {
                try {
                    Thread.sleep(config.browserWarmupDelayMs());
                    PlaywrightManager.start();
                    LOGGER.info("Playwright browser warmup completed");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Browser warmup was interrupted");
                } catch (RuntimeException exception) {
                    LOGGER.warn("Browser warmup failed", exception);
                }
            });
            browserWarmup.setDaemon(true);
            browserWarmup.start();

        } catch (RuntimeException ex) {

            if (batchMode) {
                // ✅ CRITICAL: do NOT fail suite in batch mode
                LOGGER.error(
                        "Environment validation failed, but continuing in batch mode. " +
                        "Failure will be reported in execution report.",
                        ex
                );
            } else {
                // ❌ For interactive/local runs, fail fast as before
                throw ex;
            }
        }
    }

    @AfterSuite(alwaysRun = true)
    public void afterSuite() {
        try {
            ManualTaskProcessor.shutdownAsyncWorker();
        } catch (Exception e) {
            LOGGER.warn("Failed to shut down ManualTaskProcessor cleanly", e);
        }

        try {
            PlaywrightManager.stop();
        } catch (Exception e) {
            LOGGER.warn("Failed to stop Playwright cleanly", e);
        }

        LOGGER.info("Suite finished and browser resources were closed");
    }
}