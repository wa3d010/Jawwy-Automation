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
        if (isOrderSearchVisible()) {
            return;
        }

        Locator tasksMenu = page.locator("td.cwTasksViewMenu").first();
        tasksMenu.waitFor(new Locator.WaitForOptions().setTimeout(WORKLIST_LOAD_TIMEOUT_MS));
        tasksMenu.click(new Locator.ClickOptions().setForce(true));
        page.getByLabel("Order ID").waitFor(new Locator.WaitForOptions().setTimeout(WORKLIST_LOAD_TIMEOUT_MS));
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

    public void startWork(Locator row) {
        selectRow(row);
        clickTextAction("Start Work");
        page.waitForTimeout(config.uiActionDelayMs());
    }

    public void skipTask(Locator row) {
        selectRow(row);
        clickTextAction("Actions");
        page.waitForTimeout(config.uiActionDelayMs());
        clickTextAction("Skip the task");
        page.waitForTimeout(config.uiActionDelayMs());
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

    private void selectRow(Locator row) {
        Locator[] candidateTriggers = new Locator[]{
                page.locator("tr[role='listitem'] span[eventpart='valueicon']").first(),
                page.locator("span[eventpart='valueicon']").first(),
                page.locator("[eventpart='valueicon']").first(),
                row,
                row.locator("span[eventpart='valueicon']").first(),
                row.locator("[eventpart='valueicon']").first(),
                row.locator("td img").first(),
                row.locator("td button").first(),
                row.locator("input[type='checkbox']").first()
        };

        for (Locator candidateTrigger : candidateTriggers) {
            try {
                if (candidateTrigger.count() == 0) {
                    continue;
                }
                candidateTrigger.scrollIntoViewIfNeeded();
                candidateTrigger.click(new Locator.ClickOptions().setForce(true));
                page.waitForTimeout(config.uiActionDelayMs());
                return;
            } catch (PlaywrightException ignored) {
                // Try the next possible row action trigger.
            }
        }

        throw new IllegalStateException("Unable to select the matched worklist row.");
    }

    private void clickTextAction(String text) {
        Locator[] candidateActions = new Locator[]{
                page.locator("text=" + text).first(),
                page.getByText(text).first(),
                page.locator("text=/.*" + java.util.regex.Pattern.quote(text) + ".*/i").first()
        };

        for (Locator candidateAction : candidateActions) {
            try {
                candidateAction.waitFor(new Locator.WaitForOptions().setTimeout(3000));
                candidateAction.click(new Locator.ClickOptions().setForce(true));
                return;
            } catch (PlaywrightException ignored) {
                // Try the next visible representation first.
            }
        }

        try {
            page.evaluate(
                    "label => {" +
                            " const nodes = Array.from(document.querySelectorAll('*'));" +
                            " const match = nodes.find(node => node.textContent && node.textContent.trim() === label);" +
                            " if (match) { match.click(); return true; }" +
                            " return false;" +
                            "}",
                    text
            );
            return;
        } catch (PlaywrightException ignored) {
            // Fall through to the final failure below.
        }

        throw new IllegalStateException("Unable to find action '" + text + "'. Visible action hints: " + describeVisibleActionHints());
    }

    private String describeVisibleActionHints() {
        String[] candidates = new String[]{
                "Start Work",
                "Actions",
                "Skip the task",
                "Skip Task",
                "Skip",
                "Work"
        };

        StringBuilder builder = new StringBuilder();
        for (String candidate : candidates) {
            try {
                int count = page.getByText(candidate).count();
                if (count > 0) {
                    if (builder.length() > 0) {
                        builder.append(", ");
                    }
                    builder.append(candidate).append("=").append(count);
                }
            } catch (PlaywrightException ignored) {
                // Ignore diagnostics lookup failures.
            }
        }
        return builder.length() == 0 ? "<none>" : builder.toString();
    }

    private boolean isOrderSearchVisible() {
        try {
            page.getByLabel("Order ID").waitFor(new Locator.WaitForOptions().setTimeout(2000));
            return true;
        } catch (PlaywrightException exception) {
            return false;
        }
    }
}
