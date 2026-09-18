package YaMetrica;

/* AppSettings – сохранение/загрузка настроек GUI в .properties файл (локально). */

import java.io.*;
import java.util.Properties;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AppSettings {
    private static final String SETTINGS_FILE = "yandex_metrika_settings.properties";
    private Properties properties;
    private String settingsPath;

    public AppSettings() {
        properties = new Properties();
        determineSettingsPath();
        loadSettings();
    }

    /**
     * Определяет правильный путь для файла настроек
     * Приоритет:
     * 1. Системное свойство app.data (задается при запуске)
     * 2. ProgramData\AppMetricaExporter (для всех пользователей)
     * 3. Папка с программой (только для разработки/портативной версии)
     */
    private void determineSettingsPath() {
        // 1. Проверяем системное свойство (задается через -Dapp.data=...)
        String dataDir = System.getProperty("app.data");
        if (dataDir != null && !dataDir.isEmpty()) {
            File dir = new File(dataDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            settingsPath = dataDir + File.separator + SETTINGS_FILE;
            System.out.println("📁 Используем настройки из: " + settingsPath);
            return;
        }

        // 2. Проверяем ProgramData (для установленной версии)
        String programData = System.getenv("ProgramData");
        if (programData != null && !programData.isEmpty()) {
            String programDataPath = programData + File.separator + "AppMetricaExporter";
            File dataDirFile = new File(programDataPath);
            if (!dataDirFile.exists()) {
                dataDirFile.mkdirs();
            }
            settingsPath = programDataPath + File.separator + SETTINGS_FILE;

            // Проверяем, есть ли старый файл настроек в папке с программой
            File oldSettings = new File(SETTINGS_FILE);
            File newSettings = new File(settingsPath);
            if (oldSettings.exists() && !newSettings.exists()) {
                try {
                    // Переносим старые настройки в новое место
                    copyFile(oldSettings, newSettings);
                    System.out.println("📦 Настройки перенесены из старого местоположения");
                } catch (IOException e) {
                    System.out.println("⚠️ Не удалось перенести настройки: " + e.getMessage());
                }
            }

            System.out.println("📁 Используем настройки из: " + settingsPath);
            return;
        }

        // 3. Fallback: папка с программой (для портативной версии)
        settingsPath = SETTINGS_FILE;
        System.out.println("📁 Используем настройки из папки с программой: " + settingsPath);
    }

    public void saveAllSettings(String counter, String date1, String date2,
                                String time1, String time2, boolean filterEnabled,
                                String filterText, String directory) {
        properties.setProperty("counter", counter != null ? counter : "ЕЛК");
        properties.setProperty("date1", date1 != null ? date1 : getDefaultDate1());
        properties.setProperty("date2", date2 != null ? date2 : getDefaultDate2());
        properties.setProperty("time1", time1 != null ? time1 : "00:00:00");
        properties.setProperty("time2", time2 != null ? time2 : "23:59:59");
        properties.setProperty("filterEnabled", String.valueOf(filterEnabled));
        properties.setProperty("filterText", filterText != null ? filterText : "");
        properties.setProperty("directory", directory != null ? directory : "");
        saveSettings();
    }

    public String loadCounter() {
        return properties.getProperty("counter", "ЕЛК");
    }

    public String loadDate1() {
        return properties.getProperty("date1", getDefaultDate1());
    }

    public String loadDate2() {
        return properties.getProperty("date2", getDefaultDate2());
    }

    public String loadTime1() {
        return properties.getProperty("time1", "00:00:00");
    }

    public String loadTime2() {
        return properties.getProperty("time2", "23:59:59");
    }

    public boolean loadFilterEnabled() {
        return Boolean.parseBoolean(properties.getProperty("filterEnabled", "false"));
    }

    public String loadFilterText() {
        return properties.getProperty("filterText", "");
    }

    public String loadDirectory() {
        return properties.getProperty("directory", "");
    }

    private void loadSettings() {
        File settingsFile = new File(settingsPath);
        if (settingsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(settingsFile)) {
                properties.load(fis);
                System.out.println("✅ Настройки загружены из: " + settingsPath);
            } catch (IOException e) {
                System.out.println("⚠️ Ошибка загрузки настроек: " + e.getMessage());
            }
        } else {
            System.out.println("📝 Файл настроек не найден, будут использованы значения по умолчанию");
            // Создаем файл с настройками по умолчанию
            saveSettings();
        }
    }

    private void saveSettings() {
        // Убеждаемся, что папка для настроек существует
        File settingsFile = new File(settingsPath);
        File parentDir = settingsFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(settingsFile)) {
            properties.store(fos, "Yandex Metrika Settings - Last saved: " + new java.util.Date());
            System.out.println("✅ Настройки сохранены в: " + settingsPath);
        } catch (IOException e) {
            System.out.println("⚠️ Ошибка сохранения настроек: " + e.getMessage());
            // Пробуем сохранить в папку пользователя как fallback
            saveSettingsFallback();
        }
    }

    private void saveSettingsFallback() {
        try {
            String userHome = System.getProperty("user.home");
            String fallbackPath = userHome + File.separator + "AppMetricaExporter" + File.separator + SETTINGS_FILE;
            File fallbackFile = new File(fallbackPath);
            File parentDir = fallbackFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(fallbackFile)) {
                properties.store(fos, "Yandex Metrika Settings - Last saved: " + new java.util.Date());
                System.out.println("✅ Настройки сохранены в fallback-месте: " + fallbackPath);
                settingsPath = fallbackPath;
            }
        } catch (IOException e2) {
            System.out.println("❌ Критическая ошибка: не удалось сохранить настройки никуда!");
        }
    }

    private void copyFile(File source, File dest) throws IOException {
        try (InputStream is = new FileInputStream(source);
             OutputStream os = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        }
    }

    private String getDefaultDate1() {
        LocalDate today = LocalDate.now();
        return today.minusDays(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private String getDefaultDate2() {
        LocalDate today = LocalDate.now();
        return today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    // Дополнительный метод для получения пути к файлу настроек (для отладки)
    public String getSettingsPath() {
        return settingsPath;
    }
}