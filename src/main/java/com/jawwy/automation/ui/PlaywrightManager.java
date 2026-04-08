package com.jawwy.automation.ui;

import com.jawwy.automation.config.FrameworkConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class PlaywrightManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaywrightManager.class);
    private static final List<Path> CHROMIUM_FALLBACK_EXECUTABLES = List.of(
            Paths.get("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"),
            Paths.get("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"),
            Paths.get("C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe"),
            Paths.get("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe")
    );

    private static Playwright playwright;
    private static Browser browser;
    private static BrowserContext context;
    private static Page page;
    private static volatile boolean started;

    private PlaywrightManager() {
    }

    public static synchronized void start() {
        if (started) {
            return;
        }

        FrameworkConfig config = FrameworkConfig.getInstance();
        LOGGER.info("Starting Playwright browser. Headless={}", config.browserHeadless());

        try {
            playwright = Playwright.create();
            browser = launchChromium(config);
            context = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true));
            page = context.newPage();
            started = true;
        } catch (RuntimeException exception) {
            stop();
            throw exception;
        }
    }

    public static synchronized Page page() {
        if (!started) {
            start();
        }
        return page;
    }

    public static synchronized void stop() {
        if (context != null) {
            context.close();
        }
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }

        page = null;
        context = null;
        browser = null;
        playwright = null;
        started = false;
    }

    private static Browser launchChromium(FrameworkConfig config) {
        BrowserType chromium = playwright.chromium();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(config.browserHeadless());

        for (Path executablePath : CHROMIUM_FALLBACK_EXECUTABLES) {
            if (!Files.exists(executablePath)) {
                continue;
            }

            try {
                LOGGER.info("Launching Playwright with local browser executable: {}", executablePath);
                return chromium.launch(new BrowserType.LaunchOptions()
                        .setHeadless(config.browserHeadless())
                        .setExecutablePath(executablePath));
            } catch (RuntimeException exception) {
                LOGGER.warn("Unable to launch browser executable {}", executablePath, exception);
            }
        }

        LOGGER.info("Launching Playwright with bundled Chromium");
        return chromium.launch(options);
    }
}
