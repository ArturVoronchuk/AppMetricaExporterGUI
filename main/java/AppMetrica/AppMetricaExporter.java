package AppMetrica;

/* AppMetricaExporter – экспортер данных из AppMetrica:
 * загружает CSV через API,
 * фильтрует,
 * обрабатывает PUSH_BUTTON_CLICK с детализацией по позициям кнопок (1001–1004),
 * сохраняет в Excel.
 */

import YaMetrica.OAuth2Manager;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AppMetricaExporter extends AppMetricaCore {

    private static JTextArea consoleArea;
    private static volatile boolean isInterrupted = false;

    public static void setConsoleArea(JTextArea consoleArea) {
        AppMetricaExporter.consoleArea = consoleArea;
    }

    static void appendToConsole(String message) {
        if (consoleArea != null) {
            SwingUtilities.invokeLater(() -> {
                consoleArea.append(message + "\n");
                consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
            });
        }
        System.out.println(message);
    }

    public static void main(String[] args) {
        String outputPath = getOutputFilePath();
        String filterText = "BROADCAST_ADVERT_CLICK";
        boolean enableFilter = false;

        String dateSince = "2026-02-16+00%3A00%3A00";
        String dateUntil = "2026-02-16+23%3A59%3A59";

        appendToConsole("Файл будет сохранен в: " + outputPath);
        exportDataToExcel(outputPath, enableFilter, filterText, dateSince, dateUntil);
    }

    private static String getOutputFilePath() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        return "C:/data/Appmetrica_" + timestamp + ".xlsx";
    }

    public static void exportDataToExcel(String outputPath, boolean enableFilter,
                                         String filterText, String dateSince, String dateUntil) {
        try {
            appendToConsole("Начало экспорта AppMetrica...");
            appendToConsole("Период: " + dateSince + " - " + dateUntil);
            appendToConsole("Фильтр: " + (enableFilter ? "ВКЛ (" + filterText + ")" : "ВЫКЛ"));

            resetStatistics();
            processExportData(outputPath, enableFilter, filterText, dateSince, dateUntil);

        } catch (Exception e) {
            appendToConsole("Ошибка экспорта: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void exportDataForGUI(String outputPath, boolean enableFilter,
                                        String filterText, String dateSince, String dateUntil,
                                        JTextArea consoleArea) {
        try {
            setConsoleArea(consoleArea);
            isInterrupted = false;

            // Проверка прерывания в начале
            if (Thread.currentThread().isInterrupted()) {
                isInterrupted = true;
                appendToConsole("Экспорт прерван перед началом");
                return;
            }

            appendToConsole("Запуск экспорта из GUI...");
            appendToConsole("Период: " + dateSince + " - " + dateUntil);
            appendToConsole("Настройки фильтра: " + (enableFilter ? "ВКЛ (" + filterText + ")" : "ВЫКЛ"));

            if (enableFilter && (filterText == null || filterText.trim().isEmpty())) {
                appendToConsole("ВНИМАНИЕ: Фильтр включен, но текст фильтра пустой!");
            }

            resetStatistics();

            // Проверка прерывания перед основной обработкой
            if (Thread.currentThread().isInterrupted()) {
                isInterrupted = true;
                appendToConsole("Экспорт прерван перед обработкой данных");
                return;
            }

            processExportData(outputPath, enableFilter, filterText, dateSince, dateUntil);

            // Проверка прерывания после обработки
            if (Thread.currentThread().isInterrupted()) {
                isInterrupted = true;
                appendToConsole("Экспорт прерван пользователем - файл не сохранен");
                return;
            }

        } catch (Exception e) {
            // Обработка прерывания без вывода стека ошибок
            if (Thread.currentThread().isInterrupted() || isInterrupted) {
                appendToConsole("Экспорт прерван пользователем");
            } else {
                appendToConsole("Ошибка экспорта из GUI: " + e.getMessage());
            }
        }
    }

    private static void processExportData(String outputPath, boolean enableFilter,
                                          String filterText, String dateSince, String dateUntil) {
        CloseableHttpClient httpClient = null;
        SXSSFWorkbook workbook = null;
        InputStream inputStream = null;

        try {
            String apiUrl = buildApiUrl(dateSince, dateUntil);
            appendToConsole("URL API: " + apiUrl);

            httpClient = createHttpClient();

            inputStream = waitForData(httpClient, apiUrl);
            if (inputStream == null) {
                if (isInterrupted) {
                    appendToConsole("Загрузка данных прервана");
                } else {
                    appendToConsole("Не удалось получить данные из API после всех попыток");
                }
                return;
            }

            workbook = new SXSSFWorkbook(100);
            Sheet sheet = workbook.createSheet("Data");
            createHeaderRow(sheet);

            // Обрабатываем CSV данные
            processCsvData(inputStream, workbook, sheet, enableFilter, filterText);

            // Проверка прерывания перед сохранением файла
            if (isInterrupted) {
                appendToConsole("Обработка прервана пользователем");
                appendToConsole("Файл не сохранен.");
                return;
            }

            // Сохраняем файл только если не было прерывания
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
                appendToConsole("Создана директория: " + parentDir.getAbsolutePath());
            }

            try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
                workbook.write(fileOut);
            }

            printStatistics(outputFile);
            appendToConsole("Данные успешно сохранены в: " + outputPath);

        } catch (Exception e) {
            // Обработка прерывания без вывода ошибки
            if (isInterrupted) {
                appendToConsole("Обработка прервана пользователем");
                appendToConsole("Файл не сохранен.");
            } else {
                appendToConsole("Ошибка при обработке данных: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            // Корректное закрытие ресурсов даже при прерывании
            try {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        if (!isInterrupted) {
                            appendToConsole("Ошибка при закрытии input stream: " + e.getMessage());
                        }
                    }
                }

                if (httpClient != null) {
                    try {
                        httpClient.close();
                    } catch (IOException e) {
                        if (!isInterrupted) {
                            appendToConsole("Ошибка при закрытии HTTP клиента: " + e.getMessage());
                        }
                    }
                }

                if (workbook != null) {
                    try {
                        if (!isInterrupted) {
                            workbook.write(new ByteArrayOutputStream());
                        }
                        workbook.dispose();
                        workbook.close();
                    } catch (Exception e) {
                        if (!isInterrupted) {
                            appendToConsole("Ошибка при закрытии Excel workbook: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                if (!isInterrupted) {
                    appendToConsole("Ошибка при закрытии ресурсов: " + e.getMessage());
                }
            }
        }
    }

    // Переопределяем метод для вывода в GUI с правильным порядком
    public static void printStatistics(File outputFile) {
        appendToConsole("\n=== СТАТИСТИКА ВЫГРУЗКИ AppMetrica ===");
        appendToConsole("Всего обработано строк: " + totalRows.get());
        appendToConsole("Уникальных строк сохранено: " + uniqueRows.get());
        appendToConsole("Дубликатов пропущено: " + duplicateRows.get());
        appendToConsole("Процент дубликатов: " + String.format("%.2f",
                (double) duplicateRows.get() / totalRows.get() * 100) + "%");

        // Выводим статистику в нужном порядке с номерами
        printOrderedEventStatistics();

        long fileSize = outputFile.length();
        double fileSizeKB = fileSize / 1024.0;
        double fileSizeMB = fileSizeKB / 1024.0;
        appendToConsole(String.format("Размер файла: %.2f KB (%.2f MB)", fileSizeKB, fileSizeMB));
        appendToConsole("====================================");
    }

    private static InputStream waitForData(CloseableHttpClient httpClient, String apiUrl) {
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            // Проверка прерывания
            if (isInterrupted) {
                appendToConsole("Ожидание данных прервано");
                return null;
            }

            attempt++;
            appendToConsole("Попытка " + attempt + " из " + MAX_RETRIES);

            try {
                InputStream result = executeApiRequest(httpClient, apiUrl);

                if (result != null) {
                    appendToConsole("Данные успешно получены!");
                    return result;
                }

                appendToConsole("Данные еще не готовы. Ожидание " + RETRY_INTERVAL_SECONDS + " секунд...");

                // Проверка прерывания во время ожидания
                for (int i = 0; i < RETRY_INTERVAL_SECONDS; i++) {
                    if (isInterrupted) {
                        appendToConsole("Ожидание прервано");
                        return null;
                    }
                    TimeUnit.SECONDS.sleep(1);
                }

            } catch (InterruptedException e) {
                isInterrupted = true;
                appendToConsole("Процесс прерван во время ожидания");
                return null;
            } catch (Exception e) {
                if (isInterrupted) {
                    appendToConsole("Запрос прерван");
                    return null;
                }
                appendToConsole("Ошибка при запросе: " + e.getMessage());
                try {
                    // Проверка прерывания
                    if (isInterrupted) {
                        return null;
                    }
                    TimeUnit.SECONDS.sleep(RETRY_INTERVAL_SECONDS);
                } catch (InterruptedException ie) {
                    isInterrupted = true;
                    appendToConsole("Процесс прерван");
                    return null;
                }
            }
        }

        if (!isInterrupted) {
            appendToConsole("Превышено максимальное количество попыток (" + MAX_RETRIES + ")");
        }
        return null;
    }

    private static InputStream executeApiRequest(CloseableHttpClient httpClient, String apiUrl) {
        CloseableHttpResponse response = null;
        try {
            HttpGet request = new HttpGet(apiUrl);
            request.addHeader("Authorization", "OAuth " + OAuth2Manager.getAccessToken("МЛК"));
            request.addHeader("Accept", "text/csv");

            appendToConsole("Отправляем запрос к API AppMetrica...");

            response = httpClient.execute(request);

            int statusCode = response.getStatusLine().getStatusCode();
            appendToConsole("Статус код ответа: " + statusCode);

            if (statusCode == 200) {
                appendToConsole("Получение данных от API. Это может занять некоторое время в зависимости от временного интервала.");
                appendToConsole("Ожидайте...");
                File tempFile = File.createTempFile("appmetrica", ".csv");
                tempFile.deleteOnExit();

                try (FileOutputStream fos = new FileOutputStream(tempFile);
                     InputStream is = response.getEntity().getContent()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        if (isInterrupted) {
                            appendToConsole("Загрузка данных прервана");
                            return null;
                        }
                        fos.write(buffer, 0, bytesRead);
                    }
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(tempFile), StandardCharsets.UTF_8))) {
                    String firstLine = reader.readLine();
                    if (firstLine == null || firstLine.contains("Your query is added to the queue") ||
                            !firstLine.contains("os_name")) {
                        appendToConsole("Данные еще не готовы или не содержат ожидаемых данных");
                        return null;
                    }

                    appendToConsole("Получены валидные данные CSV");
                }

                return new FileInputStream(tempFile);

            } else if (statusCode == 202) {
                appendToConsole("Запрос принят в обработку (202 Accepted)");
                return null;
            } else {
                appendToConsole("Ошибка API: " + response.getStatusLine());
                return null;
            }
        } catch (Exception e) {
            if (!isInterrupted) {
                appendToConsole("Исключение при выполнении запроса: " + e.getMessage());
            }
            return null;
        } finally {
            if (response != null) {
                try {
                    EntityUtils.consume(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    if (!isInterrupted) {
                        appendToConsole("Ошибка при закрытии ответа: " + e.getMessage());
                    }
                }
            }
        }
    }

    private static void processCsvData(InputStream inputStream, SXSSFWorkbook workbook,
                                       Sheet sheet, boolean enableFilter, String filterText) throws IOException {

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            boolean isHeader = true;
            int rowCount = 0;
            int sheetIndex = 1;
            final int EXCEL_MAX_ROWS = 100000;

            appendToConsole("Начало обработки CSV данных...");

            while ((line = reader.readLine()) != null) {

                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                // Пропускаем нежелательный контент
                if (containsUnwantedContent(line)) {
                    continue;
                }

                // Применяем фильтр
                if (enableFilter && filterText != null && !line.contains(filterText)) {
                    continue;
                }

                String[] columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                if (columns.length >= 4) {

                    String eventName = columns.length > 2 ? columns[2].replace("\"", "") : "";

                    // ОСОБАЯ ОБРАБОТКА: PUSH_BUTTON_CLICK с JSON
                    if (eventName.equals("PUSH_BUTTON_CLICK")) {
                        String jsonData = columns[3];
                        String parsedData = parsePushButtonClickWithJson(jsonData);

                        if (parsedData != null) {
                            String[] parsedColumns = parsedData.split(",", -1);

                            // 1. УНИКАЛЬНОСТЬ для детализированного типа (1001-1004)
                            String detailUniqueKey = parsedColumns[0] + "|" + parsedColumns[1] + "|" + parsedColumns[3];

                            // 2. УНИКАЛЬНОСТЬ для общего типа (10)
                            String generalUniqueKey = parsedColumns[0] + "|" + parsedColumns[1] + "|10";

                            // Проверяем уникальность для детализированного типа
                            boolean isDetailUnique = false;
                            synchronized (uniqueCombinations) {
                                if (!uniqueCombinations.contains(detailUniqueKey)) {
                                    uniqueCombinations.add(detailUniqueKey);
                                    isDetailUnique = true;
                                }
                            }

                            // Проверяем уникальность для общего типа
                            boolean isGeneralUnique = false;
                            synchronized (uniqueCombinations) {
                                if (!uniqueCombinations.contains(generalUniqueKey)) {
                                    uniqueCombinations.add(generalUniqueKey);
                                    isGeneralUnique = true;
                                }
                            }

                            if (isDetailUnique) {
                                // Добавляем в статистику детализированный тип (1001-1004)
                                collectStatisticsFromParsed(parsedColumns);
                            }

                            if (isGeneralUnique) {
                                // Добавляем в статистику общий тип (10)
                                eventStatistics.put("PUSH_BUTTON_CLICK",
                                        eventStatistics.getOrDefault("PUSH_BUTTON_CLICK", 0) + 1);
                            }

                            // Сохраняем в Excel если уникален хотя бы один из типов
                            if (isDetailUnique || isGeneralUnique) {
                                if (rowCount >= EXCEL_MAX_ROWS) {
                                    sheetIndex++;
                                    sheet = workbook.createSheet("Data_" + sheetIndex);
                                    createHeaderRow(sheet);
                                    rowCount = 0;
                                }

                                createDataRowFromParsed(sheet, parsedColumns, columns, rowCount + 1);
                                rowCount++;
                                uniqueRows.incrementAndGet();
                            } else {
                                duplicateRows.incrementAndGet();
                            }

                            totalRows.incrementAndGet();
                        } else {
                            // Если не удалось распарсить JSON, считаем как обычный PUSH_BUTTON_CLICK
                            Map<String, String> jsonValues = parseJsonData(columns[3]);
                            String campId = jsonValues.getOrDefault("CampId", "");
                            String nls = jsonValues.getOrDefault("Nls", "");

                            String uniqueKey = campId + "|" + nls + "|10";

                            if (isUnique(uniqueKey)) {
                                // Добавляем в статистику тип 10
                                eventStatistics.put("PUSH_BUTTON_CLICK",
                                        eventStatistics.getOrDefault("PUSH_BUTTON_CLICK", 0) + 1);

                                if (rowCount >= EXCEL_MAX_ROWS) {
                                    sheetIndex++;
                                    sheet = workbook.createSheet("Data_" + sheetIndex);
                                    createHeaderRow(sheet);
                                    rowCount = 0;
                                }

                                // Создаем строку с типом 10
                                createDataRow(sheet, columns, jsonValues, rowCount + 1);
                                rowCount++;
                                uniqueRows.incrementAndGet();
                            } else {
                                duplicateRows.incrementAndGet();
                            }

                            totalRows.incrementAndGet();
                        }

                        continue; // Переходим к следующей строке
                    }

                    // Обычная обработка для остальных строк
                    Map<String, String> jsonValues = parseJsonData(columns[3]);

                    String campId = jsonValues.getOrDefault("CampId", "");
                    String nls = jsonValues.getOrDefault("Nls", "");
                    String responseType = jsonValues.getOrDefault("ResponseType", "");

                    // Для PUSH_DELIVERY (9) используем responseType
                    String uniqueKey;
                    if (eventName.equals("PUSH_DELIVERY") && !responseType.isEmpty()) {
                        uniqueKey = campId + "|" + nls + "|" + responseType;
                    } else {
                        uniqueKey = campId + "|" + nls + "|" + eventName;
                    }

                    if (isUnique(uniqueKey)) {
                        // Собираем статистику для всех типов
                        collectStatistics(line);

                        if (rowCount >= EXCEL_MAX_ROWS) {
                            sheetIndex++;
                            sheet = workbook.createSheet("Data_" + sheetIndex);
                            createHeaderRow(sheet);
                            rowCount = 0;
                        }

                        createDataRow(sheet, columns, jsonValues, rowCount + 1);
                        rowCount++;
                        uniqueRows.incrementAndGet();
                    } else {
                        duplicateRows.incrementAndGet();
                    }

                    totalRows.incrementAndGet();
                }
            }

            appendToConsole("Обработка CSV данных завершена");
        }
    }

    public static String parsePushButtonClickWithJson(String jsonData) {
        try {
            String cleanedJson = jsonData.trim();
            if (cleanedJson.startsWith("\"") && cleanedJson.endsWith("\"")) {
                cleanedJson = cleanedJson.substring(1, cleanedJson.length() - 1);
            }
            cleanedJson = cleanedJson.replace("\"\"", "\"");

            JsonNode rootNode = objectMapper.readTree(cleanedJson);

            // Находим personal_account_ узел
            Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
            JsonNode accountNode = null;

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getKey().startsWith("personal_account_")) {
                    accountNode = field.getValue();
                    break;
                }
            }

            if (accountNode == null) {
                return null;
            }

            // Проверяем, что это PBCResponseType = 10 и PBCSystem = MLK
            if (!accountNode.has("PBCResponseType") || accountNode.get("PBCResponseType").asInt() != 10) {
                return null;
            }

            if (!accountNode.has("PBCSystem") || !"MLK".equals(accountNode.get("PBCSystem").asText())) {
                return null;
            }

            // Извлекаем все нужные поля
            String campId = accountNode.has("PBCCampId") ? accountNode.get("PBCCampId").asText() : "";
            String nls = accountNode.has("PBCNls") ? accountNode.get("PBCNls").asText() : "";
            String responseDate = accountNode.has("PBCResponseDate") ? accountNode.get("PBCResponseDate").asText() : "";
            String systemName  = accountNode.has("PBCSystemName") ? accountNode.get("PBCSystemName").asText() : "";

            // Определяем тип события по наличию и значению PBCButtonPosition
            String eventType;
            if (accountNode.has("PBCButtonPosition")) {
                int buttonPosition = accountNode.get("PBCButtonPosition").asInt();
                switch (buttonPosition) {
                    case 1:
                        eventType = "1001";
                        break;
                    case 2:
                        eventType = "1002";
                        break;
                    case 3:
                        eventType = "1003";
                        break;
                    default:
                        eventType = "1004";
                }
            } else {
                eventType = "1004";
            }

            // Форматируем результат как CSV строку
            return String.format("%s,%s,%s,%s,%s",
                    campId, nls, responseDate, eventType, "MLK");

        } catch (Exception e) {
            System.out.println("Error parsing push button JSON: " + e.getMessage());
            return null;
        }
    }

    private static void createHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        String[] headers = {"os_name", "event_name", "CampId", "Nls", "ResponseDate", "ResponseType","SystemName"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);

            CellStyle style = sheet.getWorkbook().createCellStyle();
            Font font = sheet.getWorkbook().createFont();
            font.setBold(true);
            style.setFont(font);
            cell.setCellStyle(style);
        }
    }

    private static void createDataRow(Sheet sheet, String[] csvColumns, Map<String, String> jsonValues, int rowNum) {
        Row row = sheet.createRow(rowNum);

        String osName = csvColumns.length > 0 ? csvColumns[0] : "";
        if (osName.startsWith("\"") && osName.endsWith("\"")) {
            osName = osName.substring(1, osName.length() - 1);
        }
        row.createCell(0).setCellValue(osName);

        String eventName = csvColumns.length > 2 ? csvColumns[2] : "";
        if (eventName.startsWith("\"") && eventName.endsWith("\"")) {
            eventName = eventName.substring(1, eventName.length() - 1);
        }
        row.createCell(1).setCellValue(eventName);

        row.createCell(2).setCellValue(jsonValues.getOrDefault("CampId", ""));
        row.createCell(3).setCellValue(jsonValues.getOrDefault("Nls", ""));
        row.createCell(4).setCellValue(jsonValues.getOrDefault("ResponseDate", ""));
        row.createCell(5).setCellValue(jsonValues.getOrDefault("ResponseType", ""));
        row.createCell(6).setCellValue(jsonValues.getOrDefault("SystemName", ""));
    }

    public static void createDataRowFromParsed(Sheet sheet, String[] parsedColumns, String[] originalColumns, int rowNum) {
        Row row = sheet.createRow(rowNum);

        // Получаем os_name из исходных колонок (обычно первый столбец)
        String osName = originalColumns.length > 0 ? originalColumns[0] : "";
        if (osName.startsWith("\"") && osName.endsWith("\"")) {
            osName = osName.substring(1, osName.length() - 1);
        }

        // parsedColumns: [campId, nls, responseDate, eventType, systemName]
        row.createCell(0).setCellValue(osName); // os_name из исходных данных
        row.createCell(1).setCellValue("PUSH_BUTTON_CLICK"); // event_name
        row.createCell(2).setCellValue(parsedColumns[0]); // CampId
        row.createCell(3).setCellValue(parsedColumns[1]); // Nls
        row.createCell(4).setCellValue(parsedColumns[2]); // ResponseDate
        row.createCell(5).setCellValue(parsedColumns[3]); // ResponseType (1001-1004)
        row.createCell(6).setCellValue(parsedColumns[4]); // SystemName
    }

    public static void collectStatisticsFromParsed(String[] parsedColumns) {
        if (parsedColumns.length >= 4) {
            String eventType = parsedColumns[3]; // 1001, 1002, 1003, 1004
            eventStatistics.put(eventType, eventStatistics.getOrDefault(eventType, 0) + 1);
        }
    }

    // Метод для прерывания процесса извне
    public static void interruptProcess() {
        isInterrupted = true;
        appendToConsole("Отправлен запрос на остановку");
    }
}