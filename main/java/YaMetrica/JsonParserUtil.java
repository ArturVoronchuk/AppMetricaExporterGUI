package YaMetrica;

/* JsonParserUtil – парсинг JSON-ответов API в объекты LogRequest. */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

public class JsonParserUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static LogRequest parseLogRequest(String json) throws Exception {
        JsonNode rootNode = objectMapper.readTree(json);
        JsonNode logRequestNode = rootNode.get("log_request");

        if (logRequestNode != null) {
            LogRequest logRequest = new LogRequest();
            logRequest.setRequestId(getLongValue(logRequestNode, "request_id"));
            logRequest.setCounterId(getLongValue(logRequestNode, "counter_id"));
            logRequest.setSource(getStringValue(logRequestNode, "source"));
            logRequest.setDate1(getStringValue(logRequestNode, "date1"));
            logRequest.setDate2(getStringValue(logRequestNode, "date2"));
            logRequest.setStatus(getStringValue(logRequestNode, "status"));
            logRequest.setSize(getLongValue(logRequestNode, "size"));
            logRequest.setAttribution(getStringValue(logRequestNode, "attribution"));

            // Парсим fields
            JsonNode fieldsNode = logRequestNode.get("fields");
            if (fieldsNode != null && fieldsNode.isArray()) {
                List<String> fields = new ArrayList<>();
                for (JsonNode fieldNode : fieldsNode) {
                    fields.add(fieldNode.asText());
                }
                logRequest.setFields(fields);
            }

            // Парсим parts
            JsonNode partsNode = logRequestNode.get("parts");
            if (partsNode != null && partsNode.isArray()) {
                List<Part> parts = new ArrayList<>();
                for (JsonNode partNode : partsNode) {
                    Part part = new Part();
                    part.setPartNumber(getIntValue(partNode, "part_number"));
                    part.setSize(getLongValue(partNode, "size"));
                    parts.add(part);
                }
                logRequest.setParts(parts);
            }

            return logRequest;
        }
        return null;
    }

    public static List<LogRequest> parseLogRequestsList(String json) throws Exception {
        List<LogRequest> requests = new ArrayList<>();
        JsonNode rootNode = objectMapper.readTree(json);
        JsonNode requestsNode = rootNode.get("requests");

        if (requestsNode != null && requestsNode.isArray()) {
            for (JsonNode requestNode : requestsNode) {
                JsonNode logRequestNode = requestNode.get("log_request");
                if (logRequestNode != null) {
                    LogRequest logRequest = parseLogRequestFromNode(logRequestNode);
                    requests.add(logRequest);
                }
            }
        }
        return requests;
    }

    private static LogRequest parseLogRequestFromNode(JsonNode logRequestNode) {
        LogRequest logRequest = new LogRequest();
        logRequest.setRequestId(getLongValue(logRequestNode, "request_id"));
        logRequest.setCounterId(getLongValue(logRequestNode, "counter_id"));
        logRequest.setSource(getStringValue(logRequestNode, "source"));
        logRequest.setDate1(getStringValue(logRequestNode, "date1"));
        logRequest.setDate2(getStringValue(logRequestNode, "date2"));
        logRequest.setStatus(getStringValue(logRequestNode, "status"));
        logRequest.setSize(getLongValue(logRequestNode, "size"));
        logRequest.setAttribution(getStringValue(logRequestNode, "attribution"));

        // Парсим fields
        JsonNode fieldsNode = logRequestNode.get("fields");
        if (fieldsNode != null && fieldsNode.isArray()) {
            List<String> fields = new ArrayList<>();
            for (JsonNode fieldNode : fieldsNode) {
                fields.add(fieldNode.asText());
            }
            logRequest.setFields(fields);
        }

        // Парсим parts
        JsonNode partsNode = logRequestNode.get("parts");
        if (partsNode != null && partsNode.isArray()) {
            List<Part> parts = new ArrayList<>();
            for (JsonNode partNode : partsNode) {
                Part part = new Part();
                part.setPartNumber(getIntValue(partNode, "part_number"));
                part.setSize(getLongValue(partNode, "size"));
                parts.add(part);
            }
            logRequest.setParts(parts);
        }

        return logRequest;
    }

    private static String getStringValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        return valueNode != null ? valueNode.asText() : null;
    }

    private static Long getLongValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        return valueNode != null ? valueNode.asLong() : null;
    }

    private static Integer getIntValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        return valueNode != null ? valueNode.asInt() : null;
    }
}