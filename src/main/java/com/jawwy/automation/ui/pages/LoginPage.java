package com.jawwy.automation.ui.pages;

import com.jawwy.automation.config.FrameworkConfig;
import com.microsoft.playwright.Page;

public class LoginPage {

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
        page.waitForTimeout(Math.min(config.uiPostLoginDelayMs(), 300L));
    }
}
