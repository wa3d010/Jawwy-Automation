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
        String loginUrl = config.uiBaseUrl() + "/" + config.applicationContext() + "/login";

        if (!page.url().contains("/login")) {
            page.navigate(loginUrl);
            page.waitForLoadState();
        }

        page.getByLabel("Username").waitFor(new Locator.WaitForOptions().setTimeout(LOGIN_FORM_TIMEOUT_MS));
        page.getByLabel("Username").fill(config.uiUsername());

        page.getByLabel("Password").waitFor(new Locator.WaitForOptions().setTimeout(LOGIN_FORM_TIMEOUT_MS));
        page.getByLabel("Password").fill(config.uiPassword());

        page.keyboard().press("Enter");
        waitForPostLoginLanding();

        if (!isWorklistVisible() && !isApplicationSelectionVisible()) {
            throw new IllegalStateException("Login completed but the application did not reach an expected landing page.");
        }
    }

    private void waitForPostLoginLanding() {
        long deadline = System.currentTimeMillis() + (long) LOGIN_TRANSITION_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            if (isWorklistVisible() || isApplicationSelectionVisible()) {
                return;
            }
            page.waitForTimeout(250L);
        }
    }

    private boolean isApplicationSelectionVisible() {
        try {
            page.getByText("Application Selection").first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(2000));
            return true;
        } catch (PlaywrightException ignored) {
            // Fall through to alternate checks.
        }

        try {
            page.getByText("Worklist Management").first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(2000));
            return true;
        } catch (PlaywrightException ignored) {
            return false;
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
