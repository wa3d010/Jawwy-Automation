package com.jawwy.automation.ui.pages;

import com.jawwy.automation.config.FrameworkConfig;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class WorklistPage {

    private static final double WORKLIST_LOAD_TIMEOUT_MS = 45000;
    private static final Logger LOGGER = LoggerFactory.getLogger(WorklistPage.class);

    private final FrameworkConfig config = FrameworkConfig.getInstance();
    private final Page page;

    public WorklistPage(Page page) {
        this.page = page;
    }

    public void open() {
        if (isOnWorklistPage()) {
            return;
        }

        if (isApplicationSelectionPage()) {
            clickWorklistManagementTile();
        } else {
            page.navigate(config.uiBaseUrl() + "/" + config.applicationContext() + "/worklistManagement");
        }

        waitForWorklistReady();
    }

    private boolean isApplicationSelectionPage() {
        try {
            Locator selectionHeader = page.getByText("Application Selection").first();
            return selectionHeader.count() > 0 && selectionHeader.isVisible();
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private void clickWorklistManagementTile() {
        Locator tile = page.getByText("Worklist Management").first();
        if (tile.count() == 0 || !tile.isVisible()) {
            tile = page.locator("text=/.*Worklist Management.*/i").first();
        }
        tile.click(new Locator.ClickOptions().setForce(true).setTimeout(5000));
    }

    public void openTasksPanel() {
        Locator tasksButton = findTasksPanelToggle();
        if (tasksButton == null || tasksButton.count() == 0) {
            throw new IllegalStateException("Unable to locate the Tasks pane toggle.");
        }

        try {
            tasksButton.scrollIntoViewIfNeeded();
            tasksButton.click(new Locator.ClickOptions().setForce(true).setTimeout(3000));
        } catch (PlaywrightException ignored) {
            throw new IllegalStateException("Failed to click the Tasks pane toggle.");
        }

        waitForOrderSearchVisible();
    }

    private Locator findTasksPanelToggle() {
        Locator[] tasksCandidates = new Locator[]{
                page.locator("text=Tasks").first(),
                page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Tasks")).first(),
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Tasks")).first(),
                page.locator("text=/.*Tasks.*/i").first(),
                page.locator("td.cwTasksViewMenu").first()
        };

        for (Locator candidate : tasksCandidates) {
            try {
                if (candidate.count() > 0 && candidate.isVisible()) {
                    return candidate;
                }
            } catch (PlaywrightException ignored) {
                // Try next candidate.
            }
        }

        return page.locator("text=/.*Tasks.*/i").first();
    }

    public void clearOrderSearch() {
        try {
            Locator orderInput = page.getByLabel("Order ID");
            if (orderInput.count() > 0 && orderInput.isVisible()) {
                orderInput.click(new Locator.ClickOptions().setClickCount(3));
                orderInput.fill("");
                page.waitForTimeout(100);
            }
        } catch (PlaywrightException ignored) {
            // Search field might not be visible or accessible, continue
        }
    }

    public void searchOrder(String orderId) {
        // Order ID input should already be visible after Tasks panel is opened
        Locator orderInput = page.getByLabel("Order ID");
        orderInput.click(new Locator.ClickOptions().setClickCount(3).setTimeout(1000));
        orderInput.fill(orderId);
        page.keyboard().press("Enter");
        waitForSearchResults(orderId);
    }



    private void waitForOrderSearchVisible() {
        if (isOrderSearchVisible()) {
            return;
        }
        page.getByLabel("Order ID").waitFor(new Locator.WaitForOptions().setTimeout(500));
    }


    public Locator findRow(String orderId) {
        Locator listItemRow = page.locator("tr[role='listitem']")
                .filter(new Locator.FilterOptions().setHasText(orderId))
                .first();

        if (listItemRow.count() > 0) {
            return listItemRow;
        }

        return page.locator("tr")
                .filter(new Locator.FilterOptions().setHasText(orderId))
                .first();
    }

    public void startWork(Locator row) {
        selectTaskCheckbox(row);

        if (!clickVisibleToolbarAction("Start Work", 3000)) {
            throw new IllegalStateException("Unable to find toolbar action 'Start Work'. Visible action hints: "
                    + describeVisibleActionHints());
        }

        // Wait until Start Work button disappears (means it was clicked successfully)
        waitForStartWorkToComplete();
    }

    private void waitForStartWorkToComplete() {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (!isToolbarActionVisible("Start Work")) {
                return;
            }
            page.waitForTimeout(100);
        }
        // If Start Work is still visible, try clicking it once more
        clickVisibleToolbarAction("Start Work", 2000);
        page.waitForTimeout(300);
    }


    public void skipTask(Locator row) {
        clickToolbarAction("Actions", 5000);
        page.waitForTimeout(300);

        if (!clickVisibleToolbarAction("Skip the task", 3000)
                && !clickVisibleToolbarAction("Skip Task", 3000)
                && !clickVisibleToolbarAction("Skip", 3000)) {
            throw new IllegalStateException("Unable to find toolbar action 'Skip the task'. Visible action hints: "
                    + describeVisibleActionHints());
        }

        page.waitForTimeout(300);

        if (!waitForSkipUiConfirmation(row)) {
            LOGGER.warn("Skip action was clicked, but the UI did not show a clear confirmation. Continuing to backend verification. Visible action hints: {}",
                    describeVisibleActionHints());
        }
    }

    private boolean clickVisibleToolbarAction(String label, long timeoutMs) {
        try {
            clickToolbarAction(label, timeoutMs);
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private boolean isToolbarActionVisible(String text) {
        try {
            Locator action = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
            return action.count() > 0 && action.isVisible();
        } catch (PlaywrightException ignored) {
            return false;
        }
    }

    private void waitForWorklistReady() {
        try {
            page.getByLabel("Order ID").waitFor(new Locator.WaitForOptions().setTimeout(3000));
            return;
        } catch (PlaywrightException ignored) {
            // Fall back to other page markers below.
        }

        try {
            page.getByText("User Worklist").waitFor(new Locator.WaitForOptions().setTimeout(3000));
            return;
        } catch (PlaywrightException ignored) {
            // Fall back to a looser worklist marker below.
        }

        page.locator("text=/.*Worklist.*/i").first()
                .waitFor(new Locator.WaitForOptions().setTimeout(3000));
    }


    private void selectTaskCheckbox(Locator row) {
        Locator[] candidateTriggers = new Locator[]{
                page.locator("tr[role='listitem'] span[eventpart='valueicon']").first(),
                page.locator("span[eventpart='valueicon']").first(),
                page.locator("[eventpart='valueicon']").first(),
                row.locator("span[eventpart='valueicon']").first(),
                row.locator("[eventpart='valueicon']").first(),
                row.locator("input[type='checkbox']").first()
        };

        for (Locator candidateTrigger : candidateTriggers) {
            try {
                if (candidateTrigger.count() == 0) {
                    continue;
                }
                candidateTrigger.scrollIntoViewIfNeeded();
                candidateTrigger.click(new Locator.ClickOptions().setForce(true));
                page.waitForTimeout(200); // Reduced from config.uiActionDelayMs()
                return;
            } catch (PlaywrightException ignored) {
                // Try the next possible checkbox trigger.
            }
        }

        try {
            page.evaluate("() => { " +
                    "const checkbox = document.querySelector(\"tr[role='listitem'] span[eventpart='valueicon']\"); " +
                    "if (checkbox) checkbox.click(); }");
            page.waitForTimeout(200); // Reduced from config.uiActionDelayMs()
            return;
        } catch (PlaywrightException ignored) {
            // Fall through to the final failure below.
        }

        throw new IllegalStateException("Unable to select the manual task checkbox for the matched row.");
    }

    private void clickToolbarAction(String text, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            Locator[] actionCandidates = new Locator[]{
                    page.getByText(text, new Page.GetByTextOptions().setExact(true)).first(),
                    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)).first(),
                    page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(text)).first(),
                    page.locator("text=" + text).first(),
                    page.locator("text=/.*" + java.util.regex.Pattern.quote(text) + ".*/i").first()
            };

            try {
                for (Locator actionCandidate : actionCandidates) {
                    if (actionCandidate.count() == 0 || !actionCandidate.isVisible()) {
                        continue;
                    }
                    actionCandidate.scrollIntoViewIfNeeded();
                    actionCandidate.click(new Locator.ClickOptions().setForce(true).setTimeout(5000));
                    return;
                }
            } catch (PlaywrightException ignored) {
                // Fall back to DOM-based visible text click below.
            }

            try {
                Object clicked = page.evaluate(
                        "label => {" +
                                " const isVisible = element => !!(element && (element.offsetWidth || element.offsetHeight || element.getClientRects().length));" +
                                " const nodes = Array.from(document.querySelectorAll('*'));" +
                                " const match = nodes.find(node => node.textContent && node.textContent.toLowerCase().includes(label.toLowerCase()) && isVisible(node));" +
                                " if (match) { match.click(); return true; }" +
                                " return false;" +
                                "}",
                        text
                );
                if (Boolean.TRUE.equals(clicked)) {
                    return;
                }
            } catch (PlaywrightException ignored) {
                // Retry until timeout.
            }

            page.waitForTimeout(200);
        }

        throw new IllegalStateException("Unable to find toolbar action '" + text + "'. Visible action hints: "
                + describeVisibleActionHints());
    }

    private boolean waitForSkipUiConfirmation(Locator row) {
        long deadline = System.currentTimeMillis() + 3000;

        while (System.currentTimeMillis() < deadline) {
            if (hasVisibleExactText("Skip the task completed successfully")
                    || hasVisibleTextContaining("Skip Task")
                    || hasVisibleTextContaining("Skip task")
                    || hasVisibleTextContaining("completed successfully")) {
                return true;
            }

            try {
                if (row.count() == 0) {
                    return true;
                }

                String rowText = row.innerText();
                if (!rowText.contains("SHARING_LIMITS")) {
                    return true;
                }
            } catch (PlaywrightException ignored) {
                return true;
            }

            page.waitForTimeout(200);
        }

        return false;
    }

    private boolean hasVisibleExactText(String text) {
        try {
            Locator matches = page.getByText(text, new Page.GetByTextOptions().setExact(true));
            int count = matches.count();
            for (int index = 0; index < count; index++) {
                if (matches.nth(index).isVisible()) {
                    return true;
                }
            }
        } catch (PlaywrightException ignored) {
            // Best-effort diagnostic helper.
        }
        return false;
    }

    private boolean hasVisibleTextContaining(String text) {
        try {
            Locator matches = page.getByText(text);
            int count = matches.count();
            for (int index = 0; index < count; index++) {
                if (matches.nth(index).isVisible()) {
                    return true;
                }
            }
        } catch (PlaywrightException ignored) {
            // Best-effort diagnostic helper.
        }
        return false;
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

    private void waitForSearchResults(String orderId) {
        long deadline = System.currentTimeMillis() + 5000; // 5 second timeout for search results
        while (System.currentTimeMillis() < deadline) {
            Locator row = findRow(orderId);
            if (row.count() > 0) {
                return; // Results found
            }
            page.waitForTimeout(100); // Short poll interval
        }
        // If no results found within timeout, continue anyway
    }

    private boolean isOrderSearchVisible() {
        try {
            page.getByLabel("Order ID").waitFor(new Locator.WaitForOptions().setTimeout(2000));
            return true;
        } catch (PlaywrightException exception) {
            return false;
        }
    }

    public boolean isOnWorklistPage() {
        return isOrderSearchVisible();
    }
}
