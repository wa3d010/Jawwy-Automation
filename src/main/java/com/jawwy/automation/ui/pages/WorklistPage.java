package com.jawwy.automation.ui.pages;

import com.jawwy.automation.config.FrameworkConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

public class WorklistPage {

    private static final double WORKLIST_LOAD_TIMEOUT_MS = 45000;

    private final FrameworkConfig config = FrameworkConfig.getInstance();
    private final Page page;

    public WorklistPage(Page page) {
        this.page = page;
    }

    public void open() {
        page.navigate(config.uiBaseUrl() + "/" + config.applicationContext() + "/worklistManagement");
        waitForWorklistReady();
    }

    public void openTasksPanel() {
        page.locator("text=Tasks").click(new Locator.ClickOptions().setForce(true));
        page.getByLabel("Order ID").waitFor();
    }

    public void searchOrder(String orderId) {
        waitForWorklistReady();
        page.getByLabel("Order ID").fill(orderId);
        page.keyboard().press("Enter");
        page.waitForTimeout(config.uiTaskSearchDelayMs());
    }

    public Locator findRow(String orderId) {
        return page.locator("tr")
                .filter(new Locator.FilterOptions().setHasText(orderId))
                .first();
    }

    public void startWork() {
        page.locator("span[eventpart='valueicon']").first().click(new Locator.ClickOptions().setForce(true));
        page.getByText("Start Work").waitFor();
        page.getByText("Start Work").click(new Locator.ClickOptions().setForce(true));
        page.getByText("Actions").waitFor();
    }

    public void skipTask() {
        page.getByText("Actions").click(new Locator.ClickOptions().setForce(true));
        page.getByText("Skip the task").waitFor();
        page.getByText("Skip the task").click(new Locator.ClickOptions().setForce(true));
    }

    private void waitForWorklistReady() {
        try {
            page.getByLabel("Order ID").waitFor(new Locator.WaitForOptions().setTimeout(WORKLIST_LOAD_TIMEOUT_MS));
            return;
        } catch (PlaywrightException ignored) {
            // Fall back to other page markers below.
        }

        try {
            page.getByText("User Worklist").waitFor(new Locator.WaitForOptions().setTimeout(WORKLIST_LOAD_TIMEOUT_MS));
            return;
        } catch (PlaywrightException ignored) {
            // Fall back to a looser worklist marker below.
        }

        page.locator("text=/.*Worklist.*/i").first()
                .waitFor(new Locator.WaitForOptions().setTimeout(WORKLIST_LOAD_TIMEOUT_MS));
    }
}
