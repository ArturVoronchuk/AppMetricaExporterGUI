package YaMetrica;

/* YandexMetrikaProcessor – основной класс для работы с API Яндекс.Метрики:
 * создание/ожидание/удаление log-запросов, скачивание частей, парсинг строк (ELK/RT.RU),
 * фильтрация, дедупликация, сохранение в Excel, гарантированная очистка запросов при прерывании.
 */

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YandexMetrikaProcessor {
    protected String counterName;
    public static final String FIELD_TYPE_ELK_MLK = "ym:pv:parsedParamsKey3";
    public static final String FIELD_TYPE_RT_RU = "ym:pv:parsedParamsKey2";
    protected String fieldType = FIELD_TYPE_ELK_MLK;
    protected static Set<String> uniqueCombinations = new HashSet<>();

    protected boolean needsGuaranteedCleanup = false;
    protected Long currentRequestId = null;
    protected String currentAccessToken = null;
    protected volatile boolean processInterrupted = false;
    protected volatile boolean stopAlreadyCalled = false; // Флаг чтобы избежать дублирования

    protected CloseableHttpClient httpClient;

    protected static int totalSavedRows = 0;
    protected static Map<String, Integer> eventTypeStats = new HashMap<>();

    private static final String BASE_URL = "https://api-metrika.yandex.net/management/v1/counter";
    private Long COUNTER_ID = 77852878L;
    private String date1 = "2025-10-26";
    private String date2 = "2025-10-28";
    private String FILE_DIRECTORY = "";
    private static final String FILE_NAME_PREFIX = "YandexMetrika_";

    private boolean FILTER_ENABLED = false;
    private String FILTER_CAMPID = "CLM_9630";

    // Добавляем интерфейс для callback'ов
    public interface ConsoleOutputListener {
        void onConsoleOutput(String message);
    }

    private ConsoleOutputListener consoleListener;

    public YandexMetrikaProcessor() {
    }

    // Конструктор с listener
    public YandexMetrikaProcessor(ConsoleOutputListener listener) {
        this.consoleListener = listener;
    }

    public void setConsoleListener(ConsoleOutputListener listener) {
        this.consoleListener = listener;
    }

    // Геттеры и сеттеры
    public void setCounterId(Long counterId) {
        this.COUNTER_ID = counterId;
    }

    public void setDate1(String date1) {
        this.date1 = date1;
    }

    public void setDate2(String date2) {
        this.date2 = date2;
    }

    public void setFileDirectory(String fileDirectory) {
        this.FILE_DIRECTORY = fileDirectory;
    }

    public void setFilterEnabled(boolean filterEnabled) {
        this.FILTER_ENABLED = filterEnabled;
    }

    public void setFilterCampid(String filterCampid) {
        this.FILTER_CAMPID = filterCampid;
    }

    public void setCounterName(String counterName) {
        this.counterName = counterName;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    protected void printToConsole(String message) {
        System.out.println(message);
        // Также отправляем в GUI если listener установлен
        if (consoleListener != null) {
            consoleListener.onConsoleOutput(message);
        }
    }

    protected void immediateConsoleOutput(String message) {
        System.out.println(message);
        // Немедленная отправка в GUI без ожидания
        if (consoleListener != null) {
            try {
                consoleListener.onConsoleOutput(message);
            } catch (Exception e) {
                // Игнорируем ошибки в listener, чтобы не прерывать основной процесс
                System.out.println("Ошибка при отправке в GUI: " + e.getMessage());
            }
        }
    }

    public void stopProcess() {
        if (stopAlreadyCalled) {
            return; // Уже вызывали остановку, избегаем дублирования
        }
        stopAlreadyCalled = true;
        processInterrupted = true;
        // НЕМЕДЛЕННЫЙ вывод в консоль
        immediateConsoleOutput("Запрошена остановка процесса...");
    }

    protected void guaranteedCleanup() {
        if (currentRequestId == null || currentAccessToken == null) {
            printToConsole("Нет активных запросов для удаления");
            return;
        }

        try {
            String requestIdStr = String.valueOf(currentRequestId);
            int padding = 50 - requestIdStr.length(); // 56 - длина строки без ID

            printToConsole("╔══════════════════════════════════════════════════════════════╗");
            printToConsole("║                  ГАРАНТИРОВАННОЕ УДАЛЕНИЕ                    ║");
            printToConsole("╠══════════════════════════════════════════════════════════════╣");
            printToConsole("║ Запрос ID: " + requestIdStr + " ".repeat(padding) + "║");
            printToConsole("║ Статус: ожидание обработки запроса для удаления...           ║");
            printToConsole("║                                                              ║");
            printToConsole("║ ВНИМАНИЕ: Не закрывайте программу до завершения удаления!    ║");
            printToConsole("║ Это может занять несколько минут в зависимости от            ║");
            printToConsole("║ выбранного временного периода.                               ║");
            printToConsole("╚══════════════════════════════════════════════════════════════╝");

            // Ждем пока запрос перейдет в статус processed и удаляем его
            waitAndCleanRequest(currentRequestId, currentAccessToken);

            printToConsole("════════════════════════════════════════════════════════════════");

        } catch (Exception e) {
            printToConsole("✗ Критическая ошибка при гарантированном удалении: " + e.getMessage());
        }
    }

    // Новый метод: ждет пока запрос обработается и затем удаляет его
    protected void waitAndCleanRequest(Long requestId, String accessToken) {
        int attempts = 0;
        int maxAttempts = 360; // 3 часа максимум (360 * 30 сек = 3 часа)
        int checkInterval = 30; // Проверяем каждые 30 секунд

        printToConsole("Ожидание обработки запроса " + requestId + " для удаления...");

        while (attempts < maxAttempts) {
            attempts++;

            try {
                LogRequest requestInfo = getRequestInfo(requestId, accessToken);
                if (requestInfo == null) {
                    printToConsole("Попытка " + attempts + "/" + maxAttempts + ": Не удалось получить статус запроса");
                    safeSleepForCleanup(checkInterval);
                    continue;
                }

                String status = requestInfo.getStatus();
                printToConsole("Попытка " + attempts + "/" + maxAttempts + ": Статус запроса " + requestId + ": " + status);

                if ("processed".equals(status)) {
                    printToConsole("Запрос обработан, выполняется удаление...");
                    // Удаляем запрос
                    executeCleanRequest(requestId, accessToken);
                    return;
                } else if ("cleaned".equals(status)) {
                    printToConsole("Запрос уже удален");
                    return;
                } else if ("failed".equals(status)) {
                    printToConsole("Запрос завершился с ошибкой - удаление не требуется");
                    return;
                } else if ("created".equals(status) || "processing".equals(status)) {
                    printToConsole("Запрос еще обрабатывается, следующая проверка через " + checkInterval + " секунд...");
                    safeSleepForCleanup(checkInterval);
                } else {
                    printToConsole("Неизвестный статус: " + status + ", ждем " + checkInterval + " секунд...");
                    safeSleepForCleanup(checkInterval);
                }

            } catch (Exception e) {
                printToConsole("⚠Ошибка при проверке статуса: " + e.getMessage());
                try {
                    safeSleepForCleanup(checkInterval);
                } catch (InterruptedException ie) {
                    printToConsole("Прервано ожидание, продолжаем попытки...");
                    // Продолжаем цикл даже при прерывании
                    continue;
                }
            }
        }

        printToConsole("Превышено время ожидания обработки запроса");
        // Пробуем удалить принудительно даже если время вышло
        try {
            printToConsole("Пробуем принудительное удаление...");
            executeCleanRequest(requestId, accessToken);
        } catch (Exception e) {
            printToConsole("Не удалось выполнить принудительное удаление: " + e.getMessage());
        }
    }

    // Отдельный метод для выполнения удаления
    protected void executeCleanRequest(Long requestId, String accessToken) throws Exception {
        String url = BASE_URL + "/" + COUNTER_ID + "/logrequest/" + requestId + "/clean";

        HttpPost request = new HttpPost(url);
        request.setHeader("Authorization", "OAuth " + accessToken);
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(""));

        HttpResponse response = httpClient.execute(request);
        if (response.getStatusLine().getStatusCode() == 200) {
            printToConsole("Запрос " + requestId + " успешно удален");
        } else {
            String errorBody = EntityUtils.toString(response.getEntity());
            printToConsole("Ошибка при удалении запроса: " + errorBody);

            // Если запрос еще не обработан, выбрасываем исключение чтобы продолжить ожидание
            if (errorBody.contains("Only requests in status 'processed' can be cleaned")) {
                throw new Exception("Запрос еще не обработан");
            }
        }
    }

    protected void safeSleepForCleanup(int seconds) throws InterruptedException {
        for (int i = 0; i < seconds; i++) {
            // В cleanup НЕ проверяем processInterrupted - мы должны дождаться удаления!
            // Выводим прогресс каждые 30 секунд
            if (i % 30 == 0 && i > 0) {
                printToConsole("   Ожидание... ");
            }
            TimeUnit.SECONDS.sleep(1);
        }
    }

    public void processData() throws Exception {
        System.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level", "OFF");
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        java.util.logging.Logger.getLogger("org.apache.http").setLevel(java.util.logging.Level.OFF);
        java.util.logging.Logger.getLogger("org.apache.poi").setLevel(java.util.logging.Level.OFF);

        resetStatistics();
        processInterrupted = false;
        stopAlreadyCalled = false; // Сбрасываем флаг при новом запуске

        String filePath = generateFileName();

        File directory = new File(FILE_DIRECTORY);
        if (!directory.exists()) {
            directory.mkdirs();
            printToConsole("Создана директория: " + directory.getAbsolutePath());
        }

        printToConsole("Запуск Яндекс.Метрика процессора...");
        printToConsole("Счетчик: " + COUNTER_ID);
        printToConsole("Период: " + date1 + " - " + date2);
        printToConsole("Поле: " + fieldType);
        printToConsole("Файл будет сохранен в: " + filePath);
        printToConsole("Фильтрация: " + (FILTER_ENABLED ? "ВКЛ" : "ВЫКЛ"));
        if (FILTER_ENABLED) {
            printToConsole("Фильтр по campid: содержит '" + FILTER_CAMPID + "'");
        }

        httpClient = HttpClients.createDefault();

        try {
            currentAccessToken = OAuth2Manager.getAccessToken(this.counterName);
            printToConsole("Счетчик: " + COUNTER_ID + " (" + this.counterName + ")");

            // 1. Получаем запрос
            printToConsole("Получение запроса логов...");
            currentRequestId = createLogRequest(date1, date2, currentAccessToken);
            if (currentRequestId == null) {
                printToConsole("Не удалось получить запрос");
                return;
            }
            printToConsole("ID запроса: " + currentRequestId);

            needsGuaranteedCleanup = true;

            // Проверяем прерывание после создания запроса
            if (processInterrupted) {
                immediateConsoleOutput("Процесс прерван после создания запроса");
                return;
            }

            // 2. Получаем информацию о запросе
            printToConsole("Получение информации о запросе...");
            LogRequest requestInfo = getRequestInfo(currentRequestId, currentAccessToken);
            if (requestInfo == null) {
                printToConsole("Не удалось получить информацию о запросе");
                return;
            }

            printToConsole("Статус запроса: " + requestInfo.getStatus());
            printToConsole("Размер данных: " + requestInfo.getSize());

            // Проверяем прерывание после получения информации
            if (processInterrupted) {
                immediateConsoleOutput("Процесс прерван после получения информации о запросе");
                return;
            }

            // 3. Скачиваем и обрабатываем данные
            printToConsole("Загрузка данных...");
            List<MetrikaDataRow> allData = new ArrayList<>();

            if (requestInfo.getParts() != null && !requestInfo.getParts().isEmpty()) {
                for (Part part : requestInfo.getParts()) {
                    if (processInterrupted) {
                        immediateConsoleOutput("Загрузка прервана пользователем");
                        return;
                    }

                    printToConsole("Загрузка части " + part.getPartNumber() + "...");
                    List<MetrikaDataRow> partData = downloadAndProcessPart(currentRequestId, part.getPartNumber(), currentAccessToken);
                    allData.addAll(partData);
                    printToConsole("Загружено строк из части " + part.getPartNumber() + ": " + partData.size());
                }
            }

            // Проверяем прерывание после загрузки данных
            if (processInterrupted) {
                immediateConsoleOutput("Процесс прерван после загрузки данных");
                return;
            }

            // 4. Сохраняем в Excel
            printToConsole("Сохранение в Excel...");
            saveToExcel(allData, filePath);

            // Проверяем прерывание после сохранения
            if (processInterrupted) {
                immediateConsoleOutput("Процесс прерван после сохранения данных");
                return;
            }

            // 5. Удаляем запрос (нормальный сценарий)
            printToConsole("Удаление запроса...");
            executeCleanRequest(currentRequestId, currentAccessToken);
            needsGuaranteedCleanup = false;

            // 6. Выводим статистику
            printStatistics();

        } catch (Exception e) {
            if (processInterrupted) {
                immediateConsoleOutput("Процесс прерван пользователем");
            } else {
                printToConsole("Критическая ошибка: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            // ГАРАНТИРОВАННОЕ УДАЛЕНИЕ ЗАПРОСА ДАЖЕ ПРИ ПРЕРЫВАНИИ
            if (needsGuaranteedCleanup && currentRequestId != null && currentAccessToken != null) {
                try {
                    // НЕМЕДЛЕННЫЙ вывод о начале гарантированного удаления
                    immediateConsoleOutput("Инициировано удаление запроса " + currentRequestId + "...");
                    guaranteedCleanup();
                } catch (Exception finallyException) {
                    printToConsole("При удалении запроса возникла ошибка: " + finallyException.getMessage());
                }
            } else {
                printToConsole("Удаление запроса не требуется.");
            }

            if (httpClient != null) {
                try {
                    httpClient.close();
                } catch (IOException ioException) {
                    printToConsole("Ошибка при закрытии HTTP клиента: " + ioException.getMessage());
                }
            }
        }
    }

    private void resetStatistics() {
        eventTypeStats.clear();
        totalSavedRows = 0;
        uniqueCombinations.clear();
    }

    protected Long createLogRequest(String date1, String date2, String accessToken) throws Exception {
        try {
            printToConsole("Поиск существующих запросов...");

            Long existingRequestId = findExistingProcessedRequest(date1, date2, accessToken);
            if (existingRequestId != null) {
                return existingRequestId;
            }

            printToConsole("Создание нового запроса...");
            String url = BASE_URL + "/" + COUNTER_ID + "/logrequests" +
                    "?date1=" + date1 + "&date2=" + date2 +
                    "&fields=" + fieldType + "&source=hits&attribution=FIRST";

            HttpPost request = new HttpPost(url);
            request.setHeader("Authorization", "OAuth " + accessToken);
            request.setHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity("{}"));

            HttpResponse response = httpClient.execute(request);
            String responseBody = EntityUtils.toString(response.getEntity());

            if (response.getStatusLine().getStatusCode() == 200) {
                LogRequest logRequest = JsonParserUtil.parseLogRequest(responseBody);
                if (logRequest != null && logRequest.getRequestId() != null) {
                    printToConsole("Создан новый запрос: " + logRequest.getRequestId());

                    try {
                        waitForRequestProcessing(logRequest.getRequestId(), accessToken);
                        return logRequest.getRequestId();
                    } catch (InterruptedException e) {
                        //immediateConsoleOutput("Ожидание обработки прервано пользователем");
                        // Возвращаем ID запроса для гарантированного удаления
                        return logRequest.getRequestId();
                    }
                }
            }

            printToConsole("Не удалось создать запрос");
            return null;

        } catch (Exception e) {
            if (processInterrupted) {
                immediateConsoleOutput("Создание запроса прервано пользователем");
                return null;
            }
            printToConsole("Ошибка при создании запроса: " + e.getMessage());
            throw e;
        }
    }

    protected Long findExistingProcessedRequest(String date1, String date2, String accessToken) throws Exception {
        String url = BASE_URL + "/" + COUNTER_ID + "/logrequests";

        HttpGet request = new HttpGet(url);
        request.setHeader("Authorization", "OAuth " + accessToken);

        HttpResponse response = httpClient.execute(request);
        String responseBody = EntityUtils.toString(response.getEntity());

        if (response.getStatusLine().getStatusCode() == 200) {
            List<LogRequest> requests = JsonParserUtil.parseLogRequestsList(responseBody);

            for (LogRequest logRequest : requests) {
                if (logRequest != null &&
                        date1.equals(logRequest.getDate1()) &&
                        date2.equals(logRequest.getDate2()) &&
                        "processed".equals(logRequest.getStatus()) &&
                        "hits".equals(logRequest.getSource())) {
                    printToConsole("Найден существующий обработанный запрос: " + logRequest.getRequestId());
                    return logRequest.getRequestId();
                }
            }
        }

        return null;
    }

    protected void waitForRequestProcessing(Long requestId, String accessToken) throws InterruptedException {
        int attempts = 0;
        int maxAttempts = 120;
        int checkInterval = 30;

        printToConsole("Ожидание обработки запроса " + requestId + "...");

        while (attempts < maxAttempts) {
            attempts++;

            try {
                if (processInterrupted) {
                    immediateConsoleOutput("Ожидание обработки прервано пользователем");
                    throw new InterruptedException("Ожидание прервано пользователем");
                }

                LogRequest requestInfo = getRequestInfo(requestId, accessToken);
                if (requestInfo == null) {
                    printToConsole("Не удалось получить информацию о запросе, ждем " + checkInterval + " секунд...");
                    safeSleep(checkInterval);
                    continue;
                }

                String status = requestInfo.getStatus();
                printToConsole("-----------------------------------------------------------------------------");
                printToConsole("Попытка " + attempts + "/" + maxAttempts + ": Статус запроса " + requestId + ": " + status);

                if ("processed".equals(status)) {
                    printToConsole("Запрос обработан успешно!");
                    printToConsole("Размер данных: " + requestInfo.getSize() + " bytes");
                    if (requestInfo.getParts() != null) {
                        printToConsole("Частей для скачивания: " + requestInfo.getParts().size());
                    }
                    return;
                } else if ("created".equals(status) || "processing".equals(status)) {
                    printToConsole("Ждем " + checkInterval + " секунд...");
                    safeSleep(checkInterval);
                } else if ("failed".equals(status)) {
                    printToConsole("Запрос завершился с ошибкой");
                    return;
                } else {
                    printToConsole("Неизвестный статус: " + status + ", ждем " + checkInterval + " секунд...");
                    safeSleep(checkInterval);
                }
            } catch (Exception e) {
                if (processInterrupted) {
                    immediateConsoleOutput("Ожидание обработки прервано пользователем");
                    throw new InterruptedException("Ожидание прервано пользователем");
                }
                printToConsole("Ошибка при проверке статуса: " + e.getMessage());
                safeSleep(checkInterval);
            }
        }

        if (processInterrupted) {
            immediateConsoleOutput("Ожидание обработки запроса прервано пользователем");
            throw new InterruptedException("Ожидание обработки запроса прервано пользователем");
        }

        printToConsole("Превышено время ожидания обработки запроса " + requestId);
    }

    protected void safeSleep(int seconds) throws InterruptedException {
        for (int i = 0; i < seconds; i++) {
            if (processInterrupted) {
                throw new InterruptedException("Sleep прерван пользователем");
            }
            TimeUnit.SECONDS.sleep(1);
        }
    }

    protected List<MetrikaDataRow> downloadAndProcessPart(Long requestId, Integer partNumber, String accessToken) throws Exception {
        String url = BASE_URL + "/" + COUNTER_ID + "/logrequest/" + requestId + "/part/" + partNumber + "/download";

        printToConsole("Скачивание части " + partNumber + " из запроса " + requestId);

        HttpGet request = new HttpGet(url);
        request.setHeader("Authorization", "OAuth " + accessToken);

        HttpResponse response = httpClient.execute(request);
        String responseBody = EntityUtils.toString(response.getEntity());

        printToConsole("Успешно скачано " + responseBody.length() + " символов");

        return processResponseData(responseBody);
    }

    protected LogRequest getRequestInfo(Long requestId, String accessToken) throws Exception {
        String url = BASE_URL + "/" + COUNTER_ID + "/logrequest/" + requestId;

        HttpGet request = new HttpGet(url);
        request.setHeader("Authorization", "OAuth " + accessToken);

        HttpResponse response = httpClient.execute(request);
        String responseBody = EntityUtils.toString(response.getEntity());

        if (response.getStatusLine().getStatusCode() == 200) {
            return JsonParserUtil.parseLogRequest(responseBody);
        }

        return null;
    }

    private List<MetrikaDataRow> processResponseData(String responseBody) {
        List<MetrikaDataRow> result = new ArrayList<>();
        String[] lines = responseBody.split("\n");

        printToConsole("Обработка " + lines.length + " строк данных для " + counterName);

        int processedLines = 0;
        int validRows = 0;
        int duplicateRows = 0;

        for (String line : lines) {
            if (line.trim().isEmpty() || line.equals(fieldType) || line.equals("[]")) {
                continue;
            }

            processedLines++;
            MetrikaDataRow row = null;

            if ("RT.RU".equals(counterName)) {
                row = parseRTRUData(line);
            } else {
                row = parseELKData(line);
            }

            if (row != null) {
                // УНИКАЛЬНОСТЬ: campid + event_type + nls (БЕЗ systemName)
                String uniqueKey = row.getCampid() + "|" + row.getEventType() + "|" + row.getNls();

                if (uniqueCombinations.contains(uniqueKey)) {
                    duplicateRows++;
                    continue;
                }

                if (applyFilter(row)) {
                    uniqueCombinations.add(uniqueKey);
                    result.add(row);
                    updateStatistics(row);
                    validRows++;
                }
            }
        }

        printToConsole("Обработано строк: " + processedLines +
                ", найдено валидных записей: " + validRows +
                ", дубликатов: " + duplicateRows);
        return result;
    }

    private MetrikaDataRow parseRTRUData(String line) {
        Pattern rtrPattern = Pattern.compile("\\['([^']+)','([^']+)','([^']+)','([^']*)','([^']*)','([^']*)','([^']*)','([^']*)'\\]");
        Matcher matcher = rtrPattern.matcher(line);

        if (matcher.find()) {
            String campId = matcher.group(1);
            String nls = matcher.group(2);
            String dateTime = matcher.group(7);
            String responseType = matcher.group(8);
            String systemName = matcher.group(5);

            if (campId.startsWith("CLM_")) {
                return new MetrikaDataRow(campId, nls, responseType, dateTime, systemName);
            }
        }
        return null;
    }

    private MetrikaDataRow parseELKData(String line) {
        // Сначала проверяем специальные случаи (тип 8)
        if (line.contains("'broadcast','advert','8'")) {
            return parseType8(line);
        }

        // Универсальный паттерн для извлечения всех параметров
        Pattern pattern = Pattern.compile("\\['([^']*)','([^']*)','([^']*)','([^']*)','([^']*)','([^']*)','([^']*)','([^']*)'\\]");
        Matcher matcher = pattern.matcher(line);

        if (!matcher.find()) {
            return null;
        }

        // Собираем все параметры в список
        List<String> params = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            params.add(matcher.group(i));
        }

        // Автоматически определяем тип данных для каждого параметра
        String eventType = "";
        String campid = "";
        String nls = "";
        String eventDate = "";
        String systemName = "";

        for (String param : params) {
            if (param == null || param.isEmpty()) {
                continue;
            }

            // Определяем тип параметра по его содержанию
            if (isEventType(param)) {
                eventType = param;
            } else if (isCampId(param)) {
                campid = param;
            } else if (isNls(param)) {
                nls = param;
            } else if (isDateTime(param)) {
                eventDate = param;
            } else if (isSystemName(param)) {
                systemName = param;
            }
        }

        // Если eventType не найден, но есть advert - это может быть тип 8
        if (eventType.isEmpty() && params.contains("advert")) {
            // Ищем цифру 8 в параметрах
            for (String param : params) {
                if ("8".equals(param)) {
                    eventType = "8";
                    break;
                }
            }
        }

        // Если удалось определить все необходимые поля, создаем объект
        if (!eventType.isEmpty() && !campid.isEmpty() && !nls.isEmpty()) {
            System.out.println("PARSED: type=" + eventType + ", campid=" + campid + ", nls=" + nls + ", date=" + eventDate);
            return new MetrikaDataRow(campid, nls, eventType, eventDate, systemName);
        } else {
            System.out.println("FAILED TO PARSE: " + line);
            System.out.println("  Found: type=" + eventType + ", campid=" + campid + ", nls=" + nls + ", date=" + eventDate);
        }

        return null;
    }

    // Специальный метод для обработки типа 8, т.к.у типа 8 может быть разная струтура (поля могут быть в разном порядке)
    private MetrikaDataRow parseType8(String line) {
        System.out.println("PARSING type=8: " + line);

        try {
            // Извлекаем все параметры после 'broadcast','advert','8'
            Pattern pattern = Pattern.compile("\\['broadcast','advert','8','([^']*)','([^']*)','([^']*)','([^']*)'");
            Matcher matcher = pattern.matcher(line);

            if (matcher.find()) {
                String systemName = matcher.group(1); // ELK (всегда)
                String param2 = matcher.group(2);
                String param3 = matcher.group(3);
                String param4 = matcher.group(4);

                // Собираем все параметры для анализа
                List<String> params = Arrays.asList(param2, param3, param4);

                // Автоматически находим данные по типам
                String eventDate = params.stream().filter(this::isDateTime).findFirst().orElse("");
                String campid = params.stream().filter(this::isCampId).findFirst().orElse("");
                String nls = params.stream().filter(this::isNls).findFirst().orElse("");

                // Если что-то не нашли, используем резервные стратегии
                if (eventDate.isEmpty()) {
                    eventDate = params.stream()
                            .filter(p -> p.contains(".") && p.contains(":") && p.length() > 15)
                            .findFirst().orElse("");
                }

                if (campid.isEmpty()) {
                    campid = params.stream()
                            .filter(p -> p.startsWith("CLM_"))
                            .findFirst().orElse("");
                }

                if (nls.isEmpty()) {
                    nls = params.stream()
                            .filter(p -> p.matches("\\d+") && p.length() >= 9)
                            .findFirst().orElse("");
                }

                // Проверяем, что все необходимые поля найдены
                if (!campid.isEmpty() && !nls.isEmpty() && !eventDate.isEmpty()) {
                    System.out.println("SUCCESS type=8: campid=" + campid + " | nls=" + nls + " | date=" + eventDate);
                    return new MetrikaDataRow(campid, nls, "8", eventDate, systemName);
                } else {
                    System.out.println("MISSING FIELDS type=8: campid=" + campid + " | nls=" + nls + " | date=" + eventDate);
                }
            }
        } catch (Exception e) {
            System.out.println("ERROR parsing type 8: " + e.getMessage());
        }

        System.out.println("FAILED TO PARSE TYPE 8: " + line);
        return null;
    }

    // Вспомогательные методы для определения типа данных
    private boolean isEventType(String param) {
        // Типы событий: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
        return param.matches("^([1-9]|10)$");
    }

    private boolean isCampId(String param) {
        // Campid начинается с CLM_ и содержит цифры/буквы
        return param.startsWith("CLM_");
    }

    private boolean isNls(String param) {
        // НЛС - длинное число (обычно 9+ цифр)
        return param.matches("^\\d{9,}$");
    }

    private boolean isDateTime(String param) {
        // Дата в формате DD.MM.YYYY HH:MM:SS
        return param.matches("^\\d{2}\\.\\d{2}\\.\\d{4} \\d{2}:\\d{2}:\\d{2}$");
    }

    private boolean isSystemName(String param) {
        // Системное имя: ELK, MLK или другие известные системы
        return param.equals("ELK") || param.equals("MLK") || param.equals("RT.RU");
    }

    private boolean applyFilter(MetrikaDataRow row) {
        if (!FILTER_ENABLED) return true;
        return row != null && row.getCampid() != null && row.getCampid().contains(FILTER_CAMPID);
    }

    private void updateStatistics(MetrikaDataRow row) {
        totalSavedRows++;
        String eventType = row.getEventType();
        eventTypeStats.put(eventType, eventTypeStats.getOrDefault(eventType, 0) + 1);
        System.out.println("Added to stats: type=" + eventType + ", total now=" + eventTypeStats.get(eventType));
    }

    protected void saveToExcel(List<MetrikaDataRow> data, String filename) throws IOException {
        printToConsole("Сохранение " + data.size() + " строк в Excel...");

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(1000)) {
            workbook.setCompressTempFiles(true);

            int maxRowsPerSheet = 1000000;
            int sheetCount = (int) Math.ceil((double) data.size() / maxRowsPerSheet);

            for (int sheetIndex = 0; sheetIndex < sheetCount; sheetIndex++) {
                int start = sheetIndex * maxRowsPerSheet;
                int end = Math.min(start + maxRowsPerSheet, data.size());

                Sheet sheet = workbook.createSheet("Data_" + (sheetIndex + 1));

                Row headerRow = sheet.createRow(0);
                String[] headers = {"campid", "nls", "event_date", "event_type", "systemName"};
                for (int i = 0; i < headers.length; i++) {
                    headerRow.createCell(i).setCellValue(headers[i]);
                }

                int rowNum = 1;
                for (int i = start; i < end; i++) {
                    if (processInterrupted) break;

                    MetrikaDataRow rowData = data.get(i);
                    Row excelRow = sheet.createRow(rowNum++);

                    excelRow.createCell(0).setCellValue(rowData.getCampid() != null ? rowData.getCampid() : "");
                    excelRow.createCell(1).setCellValue(rowData.getNls() != null ? rowData.getNls() : "");
                    excelRow.createCell(2).setCellValue(rowData.getEventDate() != null ? rowData.getEventDate() : "");
                    excelRow.createCell(3).setCellValue(rowData.getEventType() != null ? rowData.getEventType() : "");
                    excelRow.createCell(4).setCellValue(rowData.getSystemName() != null ? rowData.getSystemName() : "");
                }
            }

            try (FileOutputStream outputStream = new FileOutputStream(filename)) {
                workbook.write(outputStream);
                workbook.dispose();
            }
        }

        printToConsole("Данные сохранены в файл: " + filename);
    }

    protected void printStatistics() {
        printToConsole("\n=== СТАТИСТИКА ===");
        printToConsole("Всего уникальных строк сохранено: " + totalSavedRows);
        printToConsole("Всего дубликатов пропущено: " + (uniqueCombinations.size() - totalSavedRows));
        printToConsole("Распределение по типу откликов:");
        printToConsole("---");

        for (Map.Entry<String, Integer> entry : eventTypeStats.entrySet()) {
            String eventTypeName;
            String eventTypeKey = entry.getKey();

            if ("RT.RU".equals(counterName)) {
                switch (eventTypeKey) {
                    case "1":
                        eventTypeName = "BANNER SHOW";
                        break;
                    case "2":
                        eventTypeName = "BANNER CLICK";
                        break;
                    default:
                        eventTypeName = "UNKNOWN (" + eventTypeKey + ")";
                        break;
                }
            } else {
                switch (eventTypeKey) {
                    case "1":
                        eventTypeName = "BANNER SHOW";
                        break;
                    case "2":
                        eventTypeName = "BANNER CLICK";
                        break;
                    case "3":
                        eventTypeName = "STORY_OPEN";
                        break;
                    case "4":
                        eventTypeName = "STORY_VIEW";
                        break;
                    case "5":
                        eventTypeName = "STORY_CLICK";
                        break;
                    case "6":
                        eventTypeName = "BROADCAST_DELIVERY";
                        break;
                    case "7":
                        eventTypeName = "BROADCAST_VIEW";
                        break;
                    case "8":
                        eventTypeName = "BROADCAST_ADVERT_CLICK";
                        break;
                    case "9":
                        eventTypeName = "PUSH_DELIVERY";
                        break;
                    case "10":
                        eventTypeName = "PUSH_BUTTON_CLICK";
                        break;
                    case "1001":
                        eventTypeName = "PUSH_BUTTON_CLICK (Position 1)";
                        break;
                    case "1002":
                        eventTypeName = "PUSH_BUTTON_CLICK (Position 2)";
                        break;
                    case "1003":
                        eventTypeName = "PUSH_BUTTON_CLICK (Position 3)";
                        break;
                    case "1004":
                        eventTypeName = "PUSH_BUTTON_CLICK (Position 0)";
                        break;
                    default:
                        eventTypeName = "UNKNOWN (" + eventTypeKey + ")";
                        break;
                }
            }

            printToConsole("  " + eventTypeName + " (" + entry.getKey() + ")" + ": " + entry.getValue() + " строк");
        }
    }

    private String generateFileName() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String timestamp = dateFormat.format(new Date());
        return FILE_DIRECTORY + FILE_NAME_PREFIX + timestamp + ".xlsx";
    }

    public static void main(String[] args) throws Exception {
        YandexMetrikaProcessor processor = new YandexMetrikaProcessor();
        processor.processData();
    }

    // Метод для парсинга значенй из PUSH_BUTTON_CLICK:
    private MetrikaDataRow parsePushButtonClick(String line) {
        try {
            // Ищем паттерн для push_button_click с button_position
            // Формат может быть разным, но обычно содержит 'push_button_click' и цифру позиции

            // Паттерн для поиска button_position в строке
            Pattern buttonPosPattern = Pattern.compile("'button_position','(\\d)'");
            Matcher posMatcher = buttonPosPattern.matcher(line);

            String buttonPosition = "0"; // по умолчанию
            if (posMatcher.find()) {
                buttonPosition = posMatcher.group(1);
            }

            // Определяем event_type на основе button_position
            String eventType;
            switch (buttonPosition) {
                case "0":
                    eventType = "1004";
                    break;
                case "1":
                    eventType = "1001";
                    break;
                case "2":
                    eventType = "1002";
                    break;
                case "3":
                    eventType = "1003";
                    break;
                default:
                    eventType = "1004"; // по умолчанию для позиции 0
            }

            // Извлекаем campid, nls, event_date из строки
            String campid = extractCampId(line);
            String nls = extractNls(line);
            String eventDate = extractEventDate(line);
            String systemName = extractSystemName(line);

            if (campid != null && nls != null && eventDate != null) {
                System.out.println("PARSED PUSH_BUTTON_CLICK: position=" + buttonPosition +
                        " -> event_type=" + eventType +
                        ", campid=" + campid +
                        ", nls=" + nls +
                        ", date=" + eventDate);
                return new MetrikaDataRow(campid, nls, eventType, eventDate, systemName);
            }
        } catch (Exception e) {
            System.out.println("ERROR parsing push_button_click: " + e.getMessage());
        }
        return null;
    }

    // Далее вспомогательные методы (extractCampId, extractNls, extractEventDate и extractSystemName)
    // для извлечения данных (Чтобы отклики 1001-1004 заполнялись значениями
    // CAMPID, NLS, EVENT_DATE зи строк PUSH_BUTTON_CLICK
    private String extractCampId(String line) {
        Pattern campidPattern = Pattern.compile("'([^']*CLM_[^']*)'");
        Matcher matcher = campidPattern.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractNls(String line) {
        Pattern nlsPattern = Pattern.compile("'(\\d{9,})'");
        Matcher matcher = nlsPattern.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractEventDate(String line) {
        Pattern datePattern = Pattern.compile("'(\\d{2}\\.\\d{2}\\.\\d{4} \\d{2}:\\d{2}:\\d{2})'");
        Matcher matcher = datePattern.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractSystemName(String line) {
        if (line.contains("MLK")) return "MLK";
        return "";
    }
}