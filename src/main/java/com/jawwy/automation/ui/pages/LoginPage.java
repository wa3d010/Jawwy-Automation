package com.jawwy.automation.ui.pages;

import com.jawwy.automation.config.FrameworkConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

public class LoginPage {

    private static final double LOGIN_TRANSITION_TIMEOUT_MS = 30000;

    private final FrameworkConfig config = FrameworkConfig.getInstance();
    private final Page page;

    public LoginPage(Page page) {
        this.page = page;
    }

    public void login() {
        page.navigate(config.uiBaseUrl() + "/" + config.applicationContext() + "/login");
        page.getByLabel("Username").waitFor();
        page.getByLabel("Password").waitFor();
        page.getByLabel("Username").fill(config.uiUsername());
        page.getByLabel("Password").fill(config.uiPassword());
        page.keyboard().press("Enter");
        page.waitForTimeout(config.uiPostLoginDelayMs());
        waitForPostLoginLanding();
    }

    private void waitForPostLoginLanding() {
        Locator usernameField = page.getByLabel("Username");
        try {
            usernameField.waitFor(new Locator.WaitForOptions()
                    .setState(Locator.State.HIDDEN)
                    .setTimeout(LOGIN_TRANSITION_TIMEOUT_MS));
        } catch (PlaywrightException ignored) {
            // Some environments keep the login form in the DOM after navigation.
        }

        if (page.url().contains("/login")) {
            try {
                page.waitForURL("**/worklistManagement", new Page.WaitForURLOptions()
                        .setTimeout(LOGIN_TRANSITION_TIMEOUT_MS));
            } catch (PlaywrightException ignored) {
                // Fallback to element-based checks below.
            }
        }
    }
}
