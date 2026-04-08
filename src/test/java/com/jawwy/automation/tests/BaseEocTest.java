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
        ReportCleaner.cleanPreviousRunArtifacts();
        FrameworkConfig.reload();
        FrameworkConfig config = FrameworkConfig.getInstance();
        LOGGER.info("Starting suite on environment '{}'", config.environmentName());

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
    }

    @AfterSuite(alwaysRun = true)
    public void afterSuite() {
        ManualTaskProcessor.shutdownAsyncWorker();
        PlaywrightManager.stop();
        LOGGER.info("Suite finished and browser resources were closed");
    }
}
