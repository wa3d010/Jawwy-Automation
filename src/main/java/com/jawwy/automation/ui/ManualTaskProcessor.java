package com.jawwy.automation.ui;

import com.jawwy.automation.config.FrameworkConfig;
import com.jawwy.automation.reporting.ActionLogger;
import com.jawwy.automation.ui.pages.LoginPage;
import com.jawwy.automation.ui.pages.WorklistPage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ManualTaskProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManualTaskProcessor.class);
    private static final Object ASYNC_LOCK = new Object();

    private static ExecutorService executorService = createExecutorService();
    private static Future<?> activeTask;
    private static String activeOrderId;

    private final FrameworkConfig config = FrameworkConfig.getInstance();

    public void startSharingLimitsTaskAsync(String orderId) {
        synchronized (ASYNC_LOCK) {
            if (activeTask != null && !activeTask.isDone() && orderId.equals(activeOrderId)) {
                return;
            }

            ensureExecutor();
            activeOrderId = orderId;
            activeTask = executorService.submit(() -> processSharingLimitsTaskInternal(orderId, true));
            LOGGER.info("Started asynchronous manual task worker for order {}", orderId);
        }
    }

    public void processSharingLimitsTask(String orderId) {
        Future<?> taskToWaitFor;

        synchronized (ASYNC_LOCK) {
            if (activeTask == null || !orderId.equals(activeOrderId)) {
                processSharingLimitsTaskInternal(orderId, false);
                return;
            }
            taskToWaitFor = activeTask;
        }

        try {
            taskToWaitFor.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw ActionLogger.failure(LOGGER, "Interrupted while waiting for manual task worker of Order ID: " + orderId);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("Manual task worker failed for Order ID: " + orderId, cause);
        } finally {
            synchronized (ASYNC_LOCK) {
                if (taskToWaitFor == activeTask) {
                    activeTask = null;
                    activeOrderId = null;
                }
            }
        }
    }

    public static void shutdownAsyncWorker() {
        synchronized (ASYNC_LOCK) {
            if (activeTask != null) {
                activeTask.cancel(true);
            }
            activeTask = null;
            activeOrderId = null;
            if (executorService != null) {
                executorService.shutdownNow();
            }
            executorService = createExecutorService();
        }
    }

    private void processSharingLimitsTaskInternal(String orderId, boolean asyncMode) {
        PlaywrightManager.stop();
        PlaywrightManager.start();
        Page page = PlaywrightManager.page();

        ActionLogger.step(LOGGER, "Opening EOC login page");
        new LoginPage(page).login();

        WorklistPage worklistPage = new WorklistPage(page);
        ActionLogger.step(LOGGER, "Opening worklist management page");
        worklistPage.open();

        ActionLogger.step(LOGGER, "Opening tasks panel");
        worklistPage.openTasksPanel();

        Locator row = waitForSharingLimitsRow(worklistPage, orderId);
        if (row == null || row.count() == 0) {
            throw ActionLogger.failure(LOGGER, "No row found for Order ID: " + orderId);
        }

        ActionLogger.step(LOGGER, "SHARING_LIMITS task detected for order " + orderId);
        worklistPage.startWork(row);
        ActionLogger.step(LOGGER, "Task started successfully");
        worklistPage.skipTask(row);
        // UI feedback is not always reliable (toasts can be missing). Backend DB verification is the source of truth.
        ActionLogger.step(LOGGER, "Skip action clicked in UI");

        if (asyncMode) {
            LOGGER.info("Asynchronous manual task worker completed for order {}", orderId);
        }
    }

    private Locator waitForSharingLimitsRow(WorklistPage worklistPage, String orderId) {
        long deadline = System.currentTimeMillis() + config.manualTaskTimeoutMs();
        int attempt = 0;

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            if (attempt == 1 || attempt % 3 == 0) {
                LOGGER.info("Searching worklist by order ID {} (attempt {})", orderId, attempt);
            }
            worklistPage.searchOrder(orderId);

            Locator row = worklistPage.findRow(orderId);
            if (row.count() > 0) {
                String rowText = row.innerText();
                if (rowText.contains("SHARING_LIMITS")) {
                    return row;
                }
                LOGGER.info("Manual task row found for order {} but state is not SHARING_LIMITS yet", orderId);
            }

            try {
                if (attempt == 1 || attempt % 3 == 0) {
                    LOGGER.info("Manual task not ready for order {} on poll attempt {}", orderId, attempt);
                }
                Thread.sleep(config.manualTaskPollIntervalMs());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw ActionLogger.failure(LOGGER, "Interrupted while waiting for manual task row of Order ID: " + orderId);
            }
        }

        return null;
    }

    private static ExecutorService createExecutorService() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "manual-task-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void ensureExecutor() {
        if (executorService == null || executorService.isShutdown()) {
            executorService = createExecutorService();
        }
    }
}
