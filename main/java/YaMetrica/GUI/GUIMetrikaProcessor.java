package YaMetrica.GUI;

/* GUIMetrikaProcessor – обёртка над YandexMetrikaProcessor для интеграции с GUI (перенаправляет вывод в текстовую область).*/

import YaMetrica.YandexMetrikaProcessor;
import javax.swing.*;

public class GUIMetrikaProcessor extends YandexMetrikaProcessor {
    private JTextArea consoleArea;

    public GUIMetrikaProcessor(Long counterId, String counterName, String date1, String date2,
                               String fileDirectory, boolean filterEnabled, String filterCampid,
                               JTextArea consoleArea, String authToken) {
        // Устанавливаем listener для вывода в GUI консоль
        setConsoleListener(new ConsoleOutputListener() {
            @Override
            public void onConsoleOutput(String message) {
                SwingUtilities.invokeLater(() -> {
                    consoleArea.append(message + "\n");
                    consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
                });
            }
        });

        this.consoleArea = consoleArea;

        // Устанавливаем параметры
        setCounterId(counterId);
        setCounterName(counterName);
        setDate1(date1);
        setDate2(date2);
        setFileDirectory(fileDirectory);
        setFilterEnabled(filterEnabled);
        setFilterCampid(filterCampid);

        // Сохраняем токен для использования
        this.currentAccessToken = authToken;
    }

    public void process() {
        try {
            processData();
        } catch (Exception e) {
            printToConsole("Ошибка при обработке: " + e.getMessage());
            e.printStackTrace();
        }
    }
}