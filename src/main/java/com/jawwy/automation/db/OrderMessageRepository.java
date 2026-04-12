package com.jawwy.automation.db;

import com.jawwy.automation.config.FrameworkConfig;
import com.jawwy.automation.reporting.ActionLogger;
import io.qameta.allure.Allure;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrderMessageRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderMessageRepository.class);
    private static final Pattern COMPTEL_ID_PATTERN = Pattern.compile("(100\\d{5,})");

    private final FrameworkConfig config = FrameworkConfig.getInstance();

    public Connection connect() throws Exception {
        return DriverManager.getConnection(config.dbUrl(), config.dbUser(), config.dbPassword());
    }

    public String extractLmdId(String orderId) throws Exception {
        String sql = "SELECT stepname, utl_raw.cast_to_varchar2(dbms_lob.substr(send_data,2000,1)) AS send_data_text " +
                "FROM EOC.CWMESSAGELOG WHERE order_id = ?";

        try (Connection connection = connect()) {
            ActionLogger.step(LOGGER, "Waiting for LMD ID in DB for order " + orderId);
            Thread.sleep(config.lmdInitialDelayMs());

            for (int attempt = 1; attempt <= config.lmdRetries(); attempt++) {
                LOGGER.debug("Looking for LMD ID, attempt {}/{}", attempt, config.lmdRetries());

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, orderId);
                    ResultSet resultSet = statement.executeQuery();

                    while (resultSet.next()) {
                        String stepName = resultSet.getString("stepname");
                        String sendData = resultSet.getString("send_data_text");

                        if (stepName != null && stepName.contains("ESB_021_CreateLogisticsDeliveryRequest")) {
                            String lmdId = new JSONObject(sendData).getString("id");
                            maybeAttachTechnical("DB Evidence (LMD Lookup)", "Found LMD ID " + lmdId + " for order " + orderId);
                            return lmdId;
                        }
                    }
                }

                Thread.sleep(config.lmdRetryIntervalMs());
            }
        }

        maybeAttachTechnical("DB Evidence (LMD Lookup)", "LMD ID not found within retry window for order " + orderId);
        return null;
    }

    public List<String> extractComptelIds(String orderId) throws Exception {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT utl_raw.cast_to_varchar2(dbms_lob.substr(send_data,4000,1)) AS send_data_text " +
                "FROM EOC.CWMESSAGELOG WHERE order_id = ? AND stepname LIKE '%ESB_070%'";

        try (Connection connection = connect()) {
            LOGGER.debug("Reading current Comptel IDs from DB for order {}", orderId);

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, orderId);
                ResultSet resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    String sendData = resultSet.getString("send_data_text");

                    if (sendData != null) {
                        Matcher matcher = COMPTEL_ID_PATTERN.matcher(sendData);
                        while (matcher.find()) {
                            String matchedId = matcher.group();
                            if (!ids.contains(matchedId)) {
                                ids.add(matchedId);
                            }
                        }
                    }
                }
            }
        }

        return ids;
    }

    public boolean hasEsb027(String orderId) throws Exception {
        String sql = "SELECT 1 FROM EOC.CWMESSAGELOG WHERE ORDER_ID = ? " +
                "AND stepname LIKE '%ESB_027_ProvisionProductOfferToSubscribtion_InitialData%'";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);
            return statement.executeQuery().next();
        }
    }

    public boolean hasClosedCompletedState(String orderId) throws Exception {
        String sql = "SELECT 1 FROM EOC.CWMESSAGELOG WHERE ORDER_ID = ? AND (" +
                "stepname LIKE '%UPDATE_ORDER_SATATE_CLOSED.COMPLETED%' OR " +
                "stepname LIKE '%UPDATE_ORDER_STATE_CLOSED.COMPLETED%'" +
                ")";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);
            return statement.executeQuery().next();
        }
    }

    public List<String> getStepNames(String orderId) throws Exception {
        List<String> stepNames = new ArrayList<>();
        String sql = "SELECT stepname FROM EOC.CWMESSAGELOG WHERE ORDER_ID = ?";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                String stepName = resultSet.getString("stepname");
                if (stepName != null && !stepNames.contains(stepName)) {
                    stepNames.add(stepName);
                }
            }
        }

        return stepNames;
    }

    private void maybeAttachTechnical(String title, String message) {
        if (!config.attachTechnicalReportDetails()) {
            return;
        }
        Allure.addAttachment(title, "text/plain", message);
    }
}
