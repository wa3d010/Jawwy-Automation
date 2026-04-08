package com.jawwy.automation.ui.pages;

import com.jawwy.automation.config.FrameworkConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

public class WorklistPage {

    private final FrameworkConfig config = FrameworkConfig.getInstance();
    private final Page page;

    public WorklistPage(Page page) {
        this.page = page;
    }

    public void open() {
        page.navigate(config.uiBaseUrl() + "/" + config.applicationContext() + "/worklistManagement");
        page.getByText("User Worklist").waitFor();
    }

    public void openTasksPanel() {
        page.locator("text=Tasks").click(new Locator.ClickOptions().setForce(true));
        page.getByLabel("Order ID").waitFor();
    }

    public void searchOrder(String orderId) {
        page.getByText("User Worklist").waitFor();
        page.getByLabel("Order ID").fill(orderId);
        page.keyboard().press("Enter");
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
}
