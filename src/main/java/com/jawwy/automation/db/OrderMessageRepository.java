package com.jawwy.automation.db;

import com.jawwy.automation.config.FrameworkConfig;
import com.jawwy.automation.reporting.ActionLogger;
import io.qameta.allure.Attachment;
import io.qameta.allure.Step;
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

    @Attachment(value = "DB Logs", type = "text/plain")
    private String attachLogs(String logs) {
        return logs;
    }

    @Step("Connect to Oracle DB")
    public Connection connect() throws Exception {
        return DriverManager.getConnection(config.dbUrl(), config.dbUser(), config.dbPassword());
    }

    @Step("Extract LMD ID for order {orderId}")
    public String extractLmdId(String orderId) throws Exception {
        StringBuilder logs = new StringBuilder();
        String sql = "SELECT stepname, utl_raw.cast_to_varchar2(dbms_lob.substr(send_data,2000,1)) AS send_data_text " +
                "FROM EOC.CWMESSAGELOG WHERE order_id = ?";

        try (Connection connection = connect()) {
            Thread.sleep(config.lmdInitialDelayMs());

            for (int attempt = 1; attempt <= config.lmdRetries(); attempt++) {
                if (attempt == 1 || attempt == config.lmdRetries() || attempt % 3 == 0) {
                    LOGGER.info("Looking for LMD ID, attempt {}/{}", attempt, config.lmdRetries());
                }

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, orderId);
                    ResultSet resultSet = statement.executeQuery();

                    while (resultSet.next()) {
                        String stepName = resultSet.getString("stepname");
                        String sendData = resultSet.getString("send_data_text");

                        logs.append("Step: ").append(stepName).append(System.lineSeparator())
                                .append("SendData: ").append(sendData).append(System.lineSeparator())
                                .append(System.lineSeparator());

                        if (stepName != null && stepName.contains("ESB_021_CreateLogisticsDeliveryRequest")) {
                            String lmdId = new JSONObject(sendData).getString("id");
                            attachLogs(logs.toString());
                            return lmdId;
                        }
                    }
                }

                Thread.sleep(config.lmdRetryIntervalMs());
            }
        }

        attachLogs(logs.toString());
        return null;
    }

    @Step("Extract Comptel IDs for order {orderId}")
    public List<String> extractComptelIds(String orderId) throws Exception {
        List<String> ids = new ArrayList<>();
        StringBuilder logs = new StringBuilder();
        String sql = "SELECT utl_raw.cast_to_varchar2(dbms_lob.substr(send_data,4000,1)) AS send_data_text " +
                "FROM EOC.CWMESSAGELOG WHERE order_id = ? AND stepname LIKE '%ESB_070%'";

        try (Connection connection = connect()) {
            ActionLogger.step(LOGGER, "Reading current Comptel IDs from DB");

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, orderId);
                ResultSet resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    String sendData = resultSet.getString("send_data_text");
                    logs.append("SendData: ").append(sendData).append(System.lineSeparator())
                            .append(System.lineSeparator());

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

        attachLogs(logs.toString());
        return ids;
    }

    @Step("Check whether ESB_027 exists for order {orderId}")
    public boolean hasEsb027(String orderId) throws Exception {
        String sql = "SELECT 1 FROM EOC.CWMESSAGELOG WHERE ORDER_ID = ? " +
                "AND stepname LIKE '%ESB_027_ProvisionProductOfferToSubscribtion_InitialData%'";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);
            return statement.executeQuery().next();
        }
    }

    @Step("Check whether order {orderId} reached CLOSED.COMPLETED after the manual task")
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

    @Step("Get recent order step names for order {orderId}")
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
}
