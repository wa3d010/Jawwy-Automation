package com.jawwy.automation.ui.pages;

import com.jawwy.automation.config.FrameworkConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

public class LoginPage {

    private static final double LOGIN_TRANSITION_TIMEOUT_MS = 30000;
    private static final double LOGIN_FORM_TIMEOUT_MS = 10000;

    private final FrameworkConfig config = FrameworkConfig.getInstance();
    private final Page page;

    public LoginPage(Page page) {
        this.page = page;
    }

    public void login() {
        page.navigate(config.uiBaseUrl() + "/" + config.applicationContext() + "/login");

        if (isWorklistVisible()) {
            return;
        }

        if (!waitForLoginForm()) {
            if (isWorklistVisible()) {
                return;
            }
            throw new IllegalStateException("Neither the login form nor the worklist page became visible after navigation.");
        }

        page.getByLabel("Username").fill(config.uiUsername());
        page.getByLabel("Password").fill(config.uiPassword());
        page.keyboard().press("Enter");
        waitForPostLoginLanding();
    }

    private void waitForPostLoginLanding() {
        if (isWorklistVisible()) {
            return;
        }

        if (page.url().contains("/login")) {
            try {
                page.waitForURL("**/worklistManagement", new Page.WaitForURLOptions()
                        .setTimeout(LOGIN_TRANSITION_TIMEOUT_MS));
            } catch (PlaywrightException ignored) {
                // Some environments stay on a login-looking URL but still navigate successfully afterward.
            }
        }

        if (!isWorklistVisible() && waitForLoginForm()) {
            throw new IllegalStateException("Login completed but the application did not navigate away from the login screen.");
        }
    }

    private boolean waitForLoginForm() {
        try {
            page.getByLabel("Username").waitFor(new Locator.WaitForOptions().setTimeout(LOGIN_FORM_TIMEOUT_MS));
            page.getByLabel("Password").waitFor(new Locator.WaitForOptions().setTimeout(LOGIN_FORM_TIMEOUT_MS));
            return true;
        } catch (PlaywrightException exception) {
            return false;
        }
    }

    private boolean isWorklistVisible() {
        try {
            page.getByLabel("Order ID").waitFor(new Locator.WaitForOptions().setTimeout(2000));
            return true;
        } catch (PlaywrightException ignored) {
            // fall through
        }

        try {
            page.locator("text=/.*Worklist.*/i").first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(2000));
            return true;
        } catch (PlaywrightException ignored) {
            return false;
        }
    }
}
