package AppMetrica;

/* AppMetricaCore – базовый класс с константами, утилитами для работы с API AppMetrica (формирование URL, парсинг JSON, проверка уникальности, сбор статистики). */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.config.RequestConfig;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;

import static AppMetrica.AppMetricaExporter.appendToConsole;

public class AppMetricaCore {
    // Общие константы
    public static final String APPLICATION_ID = "4081789";

    public static final int RETRY_INTERVAL_SECONDS = 15;
    public static final int MAX_RETRIES = 120;

    public static final String[] UNWANTED_PATTERNS = {
            "Переходы",
            "Оплата",
            "Ошибка SDK гибких пакетов",
            "Гибкие пакеты",
            "Ошибки WebView",
            "Опции и подписки",
            "Взаимодействие с приложением",
            "Системные настройки",
            "SERVICE_OPTIONS_CONNECT_CLICK",
            "SERVICE_OPTIONS_POPUP_CLICK",
            "Автоприсоединение ЛС",
            "Баннеры"
    };

    // Общие компоненты
    protected static final ObjectMapper objectMapper = new ObjectMapper();
    protected static final Set<String> uniqueCombinations = new HashSet<>();
    protected static final Map<String, Integer> eventStatistics = new HashMap<>();
    protected static final AtomicInteger totalRows = new AtomicInteger(0);
    protected static final AtomicInteger uniqueRows = new AtomicInteger(0);
    protected static final AtomicInteger duplicateRows = new AtomicInteger(0);

    static {
        disableLogging();
    }

    private static void disableLogging() {
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        System.setProperty("org.apache.http.client.protocol.ResponseProcessCookies", "off");
        System.setProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory");
        System.setProperty("org.apache.poi.util.POILogger", "org.apache.poi.util.NullLogger");
        System.setProperty("log4j2.disable.jmx", "true");
        java.util.logging.Logger.getLogger("org.apache").setLevel(java.util.logging.Level.OFF);
    }

    // Общие методы
    public static String buildApiUrl(String dateSince, String dateUntil) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append("https://api.appmetrica.yandex.ru/logs/v1/export/events.csv?")
                .append("fields=os_name,appmetrica_device_id,event_name,event_json&")
                .append("application_id=").append(APPLICATION_ID).append("&")
                .append("date_since=").append(dateSince).append("&")
                .append("date_until=").append(dateUntil).append("&")
                .append("date_dimension=default&")
                .append("use_utf8_bom=true");
        return urlBuilder.toString();
    }

    public static CloseableHttpClient createHttpClient() {
        RequestConfig config = RequestConfig.custom()
                .setCookieSpec("ignore")
                .setConnectTimeout(30000)
                .setSocketTimeout(30000)
                .setConnectionRequestTimeout(30000)
                .build();

        return HttpClients.custom()
                .setDefaultRequestConfig(config)
                .disableCookieManagement()
                .setMaxConnTotal(20)
                .setMaxConnPerRoute(10)
                .build();
    }

    public static boolean containsUnwantedContent(String csvLine) {
        for (String pattern : UNWANTED_PATTERNS) {
            if (csvLine.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    public static String generateUniqueKey(Map<String, String> jsonValues, String eventName) {
        String campId = jsonValues.getOrDefault("CampId", "");
        String nls = jsonValues.getOrDefault("Nls", "");
        String responseType = jsonValues.getOrDefault("ResponseType", "");

        // Для PUSH_BUTTON_CLICK используем responseType (10, 1001-1004)
        if ("PUSH_BUTTON_CLICK".equals(eventName) && !responseType.isEmpty()) {
            return campId + "|" + nls + "|" + responseType;
        }

        // Для остальных используем eventName
        return campId + "|" + nls + "|" + eventName;
    }

    public static boolean isUnique(String uniqueKey) {
        if (uniqueKey.equals("||") || uniqueKey.equals("|||")) {
            return false;
        }
        synchronized (uniqueCombinations) {
            if (uniqueCombinations.contains(uniqueKey)) {
                // Отладка для PUSH типов
                if (uniqueKey.contains("|9|") || uniqueKey.contains("|10|") ||
                        uniqueKey.contains("|1001|") || uniqueKey.contains("|1002|") ||
                        uniqueKey.contains("|1003|") || uniqueKey.contains("|1004|")) {
                    System.out.println("🔴 ДУБЛИКАТ НАЙДЕН: " + uniqueKey);
                }
                return false;
            } else {
                // Отладка для PUSH типов
                if (uniqueKey.contains("|9|") || uniqueKey.contains("|10|") ||
                        uniqueKey.contains("|1001|") || uniqueKey.contains("|1002|") ||
                        uniqueKey.contains("|1003|") || uniqueKey.contains("|1004|")) {
                    System.out.println("🟢 УНИКАЛЬНЫЙ ДОБАВЛЕН: " + uniqueKey);
                }
                uniqueCombinations.add(uniqueKey);
                return true;
            }
        }
    }

    public static Map<String, String> parseJsonData(String jsonData) {
        Map<String, String> result = new HashMap<>();
        try {
            String cleanedJson = jsonData.trim();
            if (cleanedJson.startsWith("\"") && cleanedJson.endsWith("\"")) {
                cleanedJson = cleanedJson.substring(1, cleanedJson.length() - 1);
            }
            cleanedJson = cleanedJson.replace("\"\"", "\"");

            JsonNode rootNode = objectMapper.readTree(cleanedJson);

            Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getKey().startsWith("visitor_") && field.getValue().isObject()) {
                    extractJsonValues(field.getValue(), result);
                    break;
                } else if (field.getKey().startsWith("personal_account_") && field.getValue().isObject()) {
                    extractJsonValues(field.getValue(), result);
                    break;
                }
            }

            if (result.isEmpty()) {
                extractJsonValues(rootNode, result);
            }

        } catch (Exception e) {
            System.out.println("Ошибка при парсинге JSON: " + jsonData);
        }
        return result;
    }

    private static void extractJsonValues(JsonNode node, Map<String, String> result) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String originalKey = field.getKey();
                String fieldName = removePrefix(originalKey);

                if (fieldName.equals("CampId")) {
                    result.put("CampId", field.getValue().asText());
                }
                else if (fieldName.equals("Nls")) {
                    result.put("Nls", field.getValue().asText());
                }
                else if (fieldName.equals("ResponseDate")) {
                    result.put("ResponseDate", field.getValue().asText());
                }
                else if (fieldName.equals("ResponseType")) {
                    result.put("ResponseType", field.getValue().asText());
                }
                else if (fieldName.equals("System")) {
                    result.put("SystemName", field.getValue().asText());  // ← КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ
                }

                if (field.getValue().isObject()) {
                    extractJsonValues(field.getValue(), result);
                }
            }
        }
    }


    private static void extractPushJsonValues(JsonNode node, Map<String, String> result) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();

                // Для PUSH_DELIVERY (9)
                if (fieldName.equals("PDCampId") || fieldName.equals("CampId")) {
                    result.put("CampId", field.getValue().asText());
                } else if (fieldName.equals("PDNls") || fieldName.equals("Nls")) {
                    result.put("Nls", field.getValue().asText());
                } else if (fieldName.equals("PDResponseDate") || fieldName.equals("ResponseDate")) {
                    result.put("ResponseDate", field.getValue().asText());
                } else if (fieldName.equals("PDResponseType") || fieldName.equals("ResponseType")) {
                    result.put("ResponseType", field.getValue().asText());
                } else if (fieldName.equals("PDSystem") || fieldName.equals("System")) {
                    result.put("SystemName", field.getValue().asText());  // ← ИСПРАВЛЕНИЕ
                }
                // Для PUSH_BUTTON_CLICK (10)
                else if (fieldName.equals("PBCCampId")) {
                    result.put("CampId", field.getValue().asText());
                } else if (fieldName.equals("PBCNls")) {
                    result.put("Nls", field.getValue().asText());
                } else if (fieldName.equals("PBCResponseDate")) {
                    result.put("ResponseDate", field.getValue().asText());
                } else if (fieldName.equals("PBCResponseType")) {
                    result.put("ResponseType", field.getValue().asText());
                } else if (fieldName.equals("PBCSystem") || fieldName.equals("PBCSystemName")) {
                    result.put("SystemName", field.getValue().asText());  // ← ИСПРАВЛЕНИЕ
                }

                if (field.getValue().isObject()) {
                    extractPushJsonValues(field.getValue(), result);
                }
            }
        }
    }

    private static String removePrefix(String key) {
        return key.replaceAll("^(BS|BC|SO|SV|SC|BD|BV|BAC|PD|PBC|PB)", "");
    }

    public static void collectStatistics(String csvLine) {
        try {
            String[] columns = csvLine.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            if (columns.length >= 3) {
                String eventName = columns[2].replace("\"", "");

                if (!isUnwantedEvent(eventName)) {
                    eventStatistics.put(eventName, eventStatistics.getOrDefault(eventName, 0) + 1);
                }
            }
        } catch (Exception e) {
            System.out.println("Ошибка при сборе статистики: " + e.getMessage());
        }
    }

    public static void collectStatisticsFromParsed(String[] parsedColumns) {
        if (parsedColumns.length >= 4) {
            String eventType = parsedColumns[3]; // 1001, 1002, 1003, 1004
            eventStatistics.put(eventType, eventStatistics.getOrDefault(eventType, 0) + 1);
        }
    }

    private static boolean isUnwantedEvent(String eventName) {
        return eventName.equals("Автоприсоединение ЛС") ||
                eventName.equals("Системные настройки") ||
                eventName.equals("Баннеры") ||
                eventName.equals("Взаимодействие с приложением") ||
                eventName.equals("SERVICE_OPTIONS_POPUP_CLICK") ||
                eventName.equals("SERVICE_OPTIONS_CONNECT_CLICK");
    }

    public static void resetStatistics() {
        eventStatistics.clear();
        totalRows.set(0);
        uniqueRows.set(0);
        duplicateRows.set(0);
        uniqueCombinations.clear();
    }

    static void printOrderedEventStatistics() {
        // Определяем порядок вывода событий
        String[] orderedEvents = {
                "BANNER_SHOW",
                "BANNER_CLICK",
                "STORY_OPEN",
                "STORY_VIEW",
                "STORY_CLICK",
                "BROADCAST_DELIVERY",
                "BROADCAST_VIEW",
                "BROADCAST_ADVERT_CLICK",
                "PUSH_DELIVERY",
                "PUSH_BUTTON_CLICK",
                "1001",
                "1002",
                "1003",
                "1004"
        };

        int[] eventNumbers = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 1001, 1002, 1003, 1004};

        for (int i = 0; i < orderedEvents.length; i++) {
            String eventName = orderedEvents[i];
            int count = eventStatistics.getOrDefault(eventName, 0);
            if (count > 0 || i < 10) { // Показываем все основные события и новые типы, если они есть
                appendToConsole(eventName + " (" + eventNumbers[i] + "): " + count);
            }
        }
    }
}