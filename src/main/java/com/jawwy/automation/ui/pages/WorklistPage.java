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
        page.navigate(config.uiBaseUrl() + "/" + config.applicationContext() + "/worklistManagement");
        waitForWorklistReady();
    }

    public void openTasksPanel() {
        // In this UI, searching by Order ID works only after opening the Tasks view (top-right "Tasks (n)").
        Pattern tasksPattern = Pattern.compile("^Tasks(\\s*\\(\\d+\\))?$", Pattern.CASE_INSENSITIVE);
        Locator[] tasksCandidates = new Locator[]{
                page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(tasksPattern)).first(),
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(tasksPattern)).first(),
                page.locator("td.cwTasksViewMenu").first(),
                page.locator("text=Tasks").first(),
        };

        for (Locator tasksCandidate : tasksCandidates) {
            try {
                tasksCandidate.waitFor(new Locator.WaitForOptions().setTimeout(10000));
                tasksCandidate.click(new Locator.ClickOptions().setForce(true));
                // Some pages show the search panel but not the "User Worklist" data view until Tasks is opened.
                try {
                    page.getByText("User Worklist").waitFor(new Locator.WaitForOptions().setTimeout(15000));
                } catch (PlaywrightException ignored) {
                    // Not all environments render this label consistently.
                }
                page.getByLabel("Order ID").waitFor(new Locator.WaitForOptions().setTimeout(WORKLIST_LOAD_TIMEOUT_MS));
                return;
            } catch (PlaywrightException ignored) {
                // Try the next Tasks control representation.
            }
        }

        if (isOrderSearchVisible()) {
            return;
        }

        page.getByLabel("Order ID").waitFor(new Locator.WaitForOptions().setTimeout(WORKLIST_LOAD_TIMEOUT_MS));
    }

    public void searchOrder(String orderId) {
        waitForWorklistReady();
        page.getByLabel("Order ID").fill(orderId);
        page.keyboard().press("Enter");
        page.waitForTimeout(config.uiTaskSearchDelayMs());
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
        clickToolbarAction("Start Work", 10000);
        page.waitForTimeout(Math.max(config.uiActionDelayMs(), 800));
    }

    public void skipTask(Locator row) {
        clickToolbarAction("Actions", 8000);
        page.waitForTimeout(Math.max(config.uiActionDelayMs(), 400));
        clickToolbarAction("Skip the task", 8000);
        page.waitForTimeout(Math.max(config.uiActionDelayMs(), 600));

        if (!waitForSkipUiConfirmation(row)) {
            // Some environments do not show a reliable "skipped" toast, and the grid can refresh silently.
            // The business source of truth is the backend DB verification in JawwyOrderJourney.
            LOGGER.warn("Skip action was clicked, but the UI did not show a clear confirmation. Continuing to backend verification. Visible action hints: {}",
                    describeVisibleActionHints());
        }
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
                page.waitForTimeout(config.uiActionDelayMs());
                return;
            } catch (PlaywrightException ignored) {
                // Try the next possible checkbox trigger.
            }
        }

        try {
            page.evaluate("() => { " +
                    "const checkbox = document.querySelector(\"tr[role='listitem'] span[eventpart='valueicon']\"); " +
                    "if (checkbox) checkbox.click(); }");
            page.waitForTimeout(config.uiActionDelayMs());
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
                    page.getByText(text).first(),
                    page.locator("text=" + text).first(),
                    page.locator("text=/.*" + java.util.regex.Pattern.quote(text) + ".*/i").first()
            };

            try {
                for (Locator actionCandidate : actionCandidates) {
                    if (actionCandidate.count() == 0 || !actionCandidate.isVisible()) {
                        continue;
                    }
                    actionCandidate.scrollIntoViewIfNeeded();
                    actionCandidate.click(new Locator.ClickOptions().setForce(true));
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
                                " const match = nodes.find(node => node.textContent && node.textContent.includes(label) && isVisible(node));" +
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

            page.waitForTimeout(250);
        }

        throw new IllegalStateException("Unable to find toolbar action '" + text + "'. Visible action hints: "
                + describeVisibleActionHints());
    }

    private boolean waitForSkipUiConfirmation(Locator row) {
        long deadline = System.currentTimeMillis() + 15000;

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

            page.waitForTimeout(500);
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

    private boolean isOrderSearchVisible() {
        try {
            page.getByLabel("Order ID").waitFor(new Locator.WaitForOptions().setTimeout(2000));
            return true;
        } catch (PlaywrightException exception) {
            return false;
        }
    }
}
