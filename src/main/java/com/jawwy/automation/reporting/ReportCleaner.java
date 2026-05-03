package com.jawwy.automation.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public final class ReportCleaner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportCleaner.class);

    private ReportCleaner() {
    }

    public static void cleanPreviousRunArtifacts() {
        Path workspaceRoot = Paths.get("").toAbsolutePath().normalize();
        List<Path> cleanupTargets = List.of(
                workspaceRoot.resolve("allure-results"),
                workspaceRoot.resolve("logs").resolve("archive"),
                workspaceRoot.resolve("target").resolve("allure-results"),
                workspaceRoot.resolve("target").resolve("jenkins"),
                workspaceRoot.resolve("target").resolve("surefire-reports")
        );

        for (Path target : cleanupTargets) {
            deleteIfPresent(workspaceRoot, target.normalize());
        }
    }

    private static void deleteIfPresent(Path workspaceRoot, Path target) {
        if (!target.startsWith(workspaceRoot) || !Files.exists(target)) {
            return;
        }

        try (Stream<Path> pathStream = Files.walk(target)) {
            pathStream
                    .sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException("Unable to delete report artifact: " + path, exception);
                        }
                    });
            LOGGER.info("Deleted old report artifact: {}", target);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to clean report artifact: " + target, exception);
        }
    }
}
