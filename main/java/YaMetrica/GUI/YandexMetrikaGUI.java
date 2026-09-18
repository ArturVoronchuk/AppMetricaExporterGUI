package YaMetrica.GUI;

/* YandexMetrikaGUI – графический интерфейс (Swing). Отвечает за:
 * выбор счётчика, дат, времени, фильтра, директории, кнопки Старт/Стоп/Сохранить, вывод консоли.
 */

import YaMetrica.AppSettings;
import YaMetrica.OAuth2Manager;
import AppMetrica.AppMetricaExporter;
import YaMetrica.YandexMetrikaProcessor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

public class YandexMetrikaGUI extends JFrame {
    private JComboBox<String> counterComboBox;
    private JCheckBox filterCheckBox;
    private JTextField filterTextField;
    private JTextField date1Field;
    private JTextField date2Field;
    private JTextField time1Field;
    private JTextField time2Field;
    private JLabel time1Label;
    private JLabel time2Label;
    private JTextField directoryField;
    private JButton date1Button;
    private JButton date2Button;
    private JButton directoryButton;
    private JButton startButton;
    private JButton stopButton;
    private JButton saveButton;
    private JTextArea consoleArea;
    private JScrollPane consoleScrollPane;

    private Thread processingThread;
    private boolean isProcessing = false;
    private boolean stopRequested = false;
    private GUIMetrikaProcessor currentProcessor; // Добавляем переменную для текущего процессора

    private AppSettings appSettings;

    public YandexMetrikaGUI() {
        appSettings = new AppSettings();
        initializeGUI();
        setupEventListeners();
        loadSavedSettings();
    }

    private JPanel createSettingsPanel() {
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setBorder(new TitledBorder("Настройки экспорта"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;

        // Выбор счетчика
        gbc.gridx = 0;
        gbc.gridy = 0;
        settingsPanel.add(new JLabel("Счетчик:"), gbc);
        gbc.gridx = 1;
        counterComboBox = new JComboBox<>(new String[]{"ЕЛК", "МЛК", "RT.RU"});
        counterComboBox.addActionListener(e -> onCounterChanged());
        settingsPanel.add(counterComboBox, gbc);

        // Пояснение для дат
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        JLabel date2Note = new JLabel("<html><font color='#666666' size='3'>Для ЕЛК и RT.RU нельзя выбрать сегодняшнюю дату</font></html>");
        date2Note.setBorder(new EmptyBorder(0, 5, 10, 0));
        settingsPanel.add(date2Note, gbc);
        gbc.gridwidth = 1;

        // Дата начала
        gbc.gridx = 0;
        gbc.gridy = 2;
        settingsPanel.add(new JLabel("Дата начала:"), gbc);
        gbc.gridx = 1;
        JPanel date1Panel = new JPanel(new BorderLayout(5, 0));
        date1Field = new JTextField(getDefaultDate1());
        date1Button = new JButton("...");
        date1Button.setPreferredSize(new Dimension(30, 25));
        date1Panel.add(date1Field, BorderLayout.CENTER);
        date1Panel.add(date1Button, BorderLayout.EAST);
        settingsPanel.add(date1Panel, gbc);

        // Время начала
        gbc.gridx = 0;
        gbc.gridy = 3;
        time1Label = new JLabel("Время начала:");
        time1Label.setVisible(false);
        settingsPanel.add(time1Label, gbc);
        gbc.gridx = 1;
        time1Field = new JTextField("00:00:00");
        time1Field.setVisible(false);
        settingsPanel.add(time1Field, gbc);

        // Дата окончания
        gbc.gridx = 0;
        gbc.gridy = 4;
        settingsPanel.add(new JLabel("Дата окончания:"), gbc);
        gbc.gridx = 1;
        JPanel date2Panel = new JPanel(new BorderLayout(5, 0));
        date2Field = new JTextField(getDefaultDate2());
        date2Button = new JButton("...");
        date2Button.setPreferredSize(new Dimension(30, 25));
        date2Panel.add(date2Field, BorderLayout.CENTER);
        date2Panel.add(date2Button, BorderLayout.EAST);
        settingsPanel.add(date2Panel, gbc);

        // Время окончания
        gbc.gridx = 0;
        gbc.gridy = 5;
        time2Label = new JLabel("Время окончания:");
        time2Label.setVisible(false);
        settingsPanel.add(time2Label, gbc);
        gbc.gridx = 1;
        time2Field = new JTextField("23:59:59");
        time2Field.setVisible(false);
        settingsPanel.add(time2Field, gbc);

        // Пояснение для фильтра
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        JLabel filterNote = new JLabel("<html><font color='#666666' size='3'>Укажите условие фильтрации, например CLM_9630 или НЛС</font></html>");
        filterNote.setBorder(new EmptyBorder(0, 5, 10, 0));
        settingsPanel.add(filterNote, gbc);
        gbc.gridwidth = 1;

        // Фильтр
        gbc.gridx = 0;
        gbc.gridy = 7;
        filterCheckBox = new JCheckBox("Фильтр");
        settingsPanel.add(filterCheckBox, gbc);

        gbc.gridx = 1;
        gbc.gridy = 7;
        filterTextField = new JTextField("");
        filterTextField.setEnabled(false);
        filterTextField.setToolTipText("Укажите условие фильтрации, например CLM_9630 или НЛС.");
        settingsPanel.add(filterTextField, gbc);

        // Директория
        gbc.gridx = 0;
        gbc.gridy = 8;
        settingsPanel.add(new JLabel("Выберите директорию для сохранения:"), gbc);
        gbc.gridx = 1;
        JPanel directoryPanel = new JPanel(new BorderLayout(5, 0));
        directoryField = new JTextField("");
        directoryButton = new JButton("...");
        directoryButton.setPreferredSize(new Dimension(30, 25));
        directoryPanel.add(directoryField, BorderLayout.CENTER);
        directoryPanel.add(directoryButton, BorderLayout.EAST);
        settingsPanel.add(directoryPanel, gbc);

        // Пояснение для директории
        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.gridwidth = 2;
        JLabel directoryNote = new JLabel("<html><font color='#666666' size='3'>Имя и расширение файла задавать не нужно. Программа автоматически создаст файл и отобразит его имя в консоли</font></html>");
        directoryNote.setBorder(new EmptyBorder(0, 5, 10, 0));
        settingsPanel.add(directoryNote, gbc);

        // Кнопки Старт, Стоп и Сохранить
        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));

        // Зеленая стрелка (Play) для Старт
        startButton = new JButton("▶");
        startButton.setFont(new Font("Segoe UI Symbol", Font.BOLD, 22));
        startButton.setForeground(new Color(0, 128, 0));
        startButton.setPreferredSize(new Dimension(50, 35));
        startButton.setToolTipText("Запустить обработку");
        // Убираем отступы и центрируем
        startButton.setMargin(new Insets(0, 0, 0, -2));
        startButton.setHorizontalAlignment(SwingConstants.CENTER);
        startButton.setVerticalAlignment(SwingConstants.CENTER);

        // Красный квадрат (Stop) для Стоп
        stopButton = new JButton("■");
        stopButton.setFont(new Font("Segoe UI Symbol", Font.BOLD, 22));
        stopButton.setForeground(new Color(200, 0, 0));
        stopButton.setPreferredSize(new Dimension(50, 35));
        stopButton.setEnabled(false);
        stopButton.setToolTipText("Остановить обработку");
        // Убираем отступы и центрируем
        stopButton.setMargin(new Insets(0, 0, 0, 0));
        stopButton.setHorizontalAlignment(SwingConstants.CENTER);
        stopButton.setVerticalAlignment(SwingConstants.CENTER);

        // Кнопка сохранения параметров - HTML версия
        saveButton = new JButton("💾");
        saveButton.setFont(new Font("Segoe UI Symbol", Font.BOLD, 22));
        saveButton.setForeground(new Color(0, 120, 215));
        saveButton.setPreferredSize(new Dimension(50, 35));
        saveButton.setToolTipText("Сохранить текущие настройки");
        saveButton.setMargin(new Insets(0, -1, 0, 0));

        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(saveButton);
        settingsPanel.add(buttonPanel, gbc);

        return settingsPanel;
    }

    private void onCounterChanged() {
        String selectedCounter = (String) counterComboBox.getSelectedItem();
        boolean isMLK = "МЛК".equals(selectedCounter);

        time1Label.setVisible(isMLK);
        time1Field.setVisible(isMLK);
        time2Label.setVisible(isMLK);
        time2Field.setVisible(isMLK);

        revalidate();
        repaint();
    }

    private void initializeGUI() {
        setTitle("Яндекс.Метрика - Экспорт данных");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 800);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel settingsPanel = createSettingsPanel();
        consoleScrollPane = createConsolePanel();

        mainPanel.add(settingsPanel, BorderLayout.NORTH);
        mainPanel.add(consoleScrollPane, BorderLayout.CENTER);

        add(mainPanel);
    }

    private JScrollPane createConsolePanel() {
        consoleArea = new JTextArea();
        consoleArea.setEditable(false);
        consoleArea.setBackground(Color.BLACK);
        consoleArea.setForeground(Color.WHITE);
        consoleArea.setFont(new Font("Consolas", Font.PLAIN, 12));

        DefaultCaret caret = (DefaultCaret) consoleArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane scrollPane = new JScrollPane(consoleArea);
        scrollPane.setBorder(new TitledBorder("Консоль"));
        scrollPane.setPreferredSize(new Dimension(600, 300));

        return scrollPane;
    }

    private void setupEventListeners() {
        filterCheckBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                filterTextField.setEnabled(e.getStateChange() == ItemEvent.SELECTED);
            }
        });

        date1Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showDatePicker(date1Field);
            }
        });

        date2Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showDatePicker(date2Field);
            }
        });

        directoryButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseDirectory();
            }
        });

        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startProcessing();
            }
        });

        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopProcessing();
            }
        });

        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveSettings();
            }
        });
    }

    private void saveSettings() {
        try {
            String counter = (String) counterComboBox.getSelectedItem();
            String date1 = date1Field.getText();
            String date2 = date2Field.getText();
            String time1 = time1Field.getText();
            String time2 = time2Field.getText();
            boolean filterEnabled = filterCheckBox.isSelected();
            String filterText = filterTextField.getText();
            String directory = directoryField.getText();

            appSettings.saveAllSettings(counter, date1, date2, time1, time2,
                    filterEnabled, filterText, directory);

            appendToConsole("Параметры выгрузки успешно сохранены!");

        } catch (Exception e) {
            appendToConsole("Ошибка при сохранении настроек: " + e.getMessage());
        }
    }

    private void loadSavedSettings() {
        SwingUtilities.invokeLater(() -> {
            try {
                counterComboBox.setSelectedItem(appSettings.loadCounter());
                date1Field.setText(appSettings.loadDate1());
                date2Field.setText(appSettings.loadDate2());
                time1Field.setText(appSettings.loadTime1());
                time2Field.setText(appSettings.loadTime2());
                filterCheckBox.setSelected(appSettings.loadFilterEnabled());
                filterTextField.setText(appSettings.loadFilterText());
                filterTextField.setEnabled(filterCheckBox.isSelected());
                directoryField.setText(appSettings.loadDirectory());

                onCounterChanged();

            } catch (Exception e) {
                appendToConsole("Ошибка при загрузке настроек: " + e.getMessage());
            }
        });
    }

    private void showDatePicker(JTextField targetField) {
        JDialog dateDialog = new JDialog(this, "Выберите дату для " +
                (targetField == date1Field ? "начала периода" : "окончания периода"), true);
        dateDialog.setSize(330, 350);
        dateDialog.setLocationRelativeTo(this);
        dateDialog.setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel calendarPanel = new JPanel(new BorderLayout());
        JPanel navigationPanel = new JPanel(new FlowLayout());

        JButton prevMonth = new JButton("<");
        prevMonth.setPreferredSize(new Dimension(50, 25));
        prevMonth.setFont(new Font("Arial", Font.BOLD, 14));

        JButton nextMonth = new JButton(">");
        nextMonth.setPreferredSize(new Dimension(50, 25));
        nextMonth.setFont(new Font("Arial", Font.BOLD, 14));

        JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
        monthLabel.setFont(new Font("Arial", Font.BOLD, 14));
        monthLabel.setPreferredSize(new Dimension(150, 25));

        navigationPanel.add(prevMonth);
        navigationPanel.add(monthLabel);
        navigationPanel.add(nextMonth);

        JPanel daysPanel = new JPanel(new GridLayout(0, 7, 2, 2));
        daysPanel.setPreferredSize(new Dimension(300, 250));

        Calendar calendar = Calendar.getInstance();
        updateCalendarDisplay(calendar, monthLabel, daysPanel, targetField);

        calendarPanel.add(navigationPanel, BorderLayout.NORTH);
        calendarPanel.add(daysPanel, BorderLayout.CENTER);

        prevMonth.addActionListener(e -> {
            calendar.add(Calendar.MONTH, -1);
            updateCalendarDisplay(calendar, monthLabel, daysPanel, targetField);
        });

        nextMonth.addActionListener(e -> {
            calendar.add(Calendar.MONTH, 1);
            updateCalendarDisplay(calendar, monthLabel, daysPanel, targetField);
        });

        panel.add(calendarPanel, BorderLayout.CENTER);
        dateDialog.add(panel);
        dateDialog.setVisible(true);
    }

    private void updateCalendarDisplay(Calendar calendar, JLabel monthLabel, JPanel daysPanel, JTextField targetField) {
        daysPanel.removeAll();

        String[] months = {"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"};
        String monthYear = months[calendar.get(Calendar.MONTH)] + " " + calendar.get(Calendar.YEAR);
        monthLabel.setText(monthYear);

        String[] daysOfWeek = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (String day : daysOfWeek) {
            JLabel dayLabel = new JLabel(day, SwingConstants.CENTER);
            dayLabel.setForeground(Color.BLUE);
            dayLabel.setPreferredSize(new Dimension(40, 25));
            dayLabel.setFont(new Font("Arial", Font.BOLD, 12));
            daysPanel.add(dayLabel);
        }

        Calendar tempCal = (Calendar) calendar.clone();
        tempCal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK);
        int offset = (firstDayOfWeek + 5) % 7;

        for (int i = 0; i < offset; i++) {
            JLabel emptyLabel = new JLabel("");
            emptyLabel.setPreferredSize(new Dimension(40, 35));
            daysPanel.add(emptyLabel);
        }

        LocalDate todayLocal = LocalDate.now();
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        String selectedCounter = (String) counterComboBox.getSelectedItem();
        boolean isMLK = "МЛК".equals(selectedCounter);

        int daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int day = 1; day <= daysInMonth; day++) {
            JButton dayButton = new JButton(String.valueOf(day));
            dayButton.setPreferredSize(new Dimension(40, 35));
            dayButton.setMargin(new Insets(2, 2, 2, 2));
            dayButton.setFont(new Font("Arial", Font.PLAIN, 12));

            Calendar currentDay = (Calendar) calendar.clone();
            currentDay.set(Calendar.DAY_OF_MONTH, day);
            currentDay.set(Calendar.HOUR_OF_DAY, 0);
            currentDay.set(Calendar.MINUTE, 0);
            currentDay.set(Calendar.SECOND, 0);
            currentDay.set(Calendar.MILLISECOND, 0);

            LocalDate currentLocalDate = LocalDate.of(
                    currentDay.get(Calendar.YEAR),
                    currentDay.get(Calendar.MONTH) + 1,
                    currentDay.get(Calendar.DAY_OF_MONTH)
            );

            boolean isFutureDate = currentLocalDate.isAfter(todayLocal);
            boolean isToday = currentLocalDate.isEqual(todayLocal);

            if (isFutureDate) {
                dayButton.setEnabled(false);
                dayButton.setBackground(Color.LIGHT_GRAY);
                dayButton.setForeground(Color.GRAY);
                dayButton.setToolTipText("Будущая дата - недоступна");
            } else if (isToday && !isMLK) {
                dayButton.setEnabled(false);
                dayButton.setBackground(new Color(255, 200, 200));
                dayButton.setForeground(Color.RED);
                dayButton.setToolTipText("Сегодня - недоступно для Яндекс.Метрики");
            } else if (isToday && isMLK) {
                dayButton.setEnabled(true);
                dayButton.setBackground(new Color(200, 255, 200));
                dayButton.setForeground(Color.BLACK);
                dayButton.setToolTipText("Сегодня - доступно для AppMetrica");
            } else {
                dayButton.setEnabled(true);
                dayButton.setBackground(Color.WHITE);
                dayButton.setForeground(Color.BLACK);
                dayButton.setToolTipText("Выбрать эту дату");
            }

            final int selectedDay = day;
            final int selectedMonth = calendar.get(Calendar.MONTH) + 1;
            final int selectedYear = calendar.get(Calendar.YEAR);
            final LocalDate selectedLocalDate = currentLocalDate;

            dayButton.addActionListener(e -> {
                String dateStr = String.format("%04d-%02d-%02d", selectedYear, selectedMonth, selectedDay);

                Component parent = dayButton.getParent();
                while (parent != null && !(parent instanceof JDialog)) {
                    parent = parent.getParent();
                }

                if (parent instanceof JDialog) {
                    JDialog dialog = (JDialog) parent;
                    String dialogTitle = dialog.getTitle();

                    if (dialogTitle.contains("окончания") && !isMLK) {
                        if (!selectedLocalDate.isBefore(todayLocal)) {
                            String errorMessage = "ОШИБКА: Для Яндекс.Метрики дата окончания не может быть сегодняшней или будущей датой!\n\n" +
                                    "Выбрана: " + selectedLocalDate + "\n" +
                                    "Сегодня: " + todayLocal + "\n" +
                                    "Максимальная допустимая дата: " + todayLocal.minusDays(1);
                            JOptionPane.showMessageDialog(YandexMetrikaGUI.this,
                                    errorMessage,
                                    "Недопустимая дата окончания",
                                    JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                    } else if (dialogTitle.contains("начала") && !isMLK) {
                        if (!selectedLocalDate.isBefore(todayLocal)) {
                            String errorMessage = "ОШИБКА: Для Яндекс.Метрики дата начала не может быть сегодняшней или будущей датой!\n\n" +
                                    "Выбрана: " + selectedLocalDate + "\n" +
                                    "Сегодня: " + todayLocal + "\n" +
                                    "Максимальная допустимая дата: " + todayLocal.minusDays(1);
                            JOptionPane.showMessageDialog(YandexMetrikaGUI.this,
                                    errorMessage,
                                    "Недопустимая дата начала",
                                    JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                    }

                    if (dialogTitle.contains("начала")) {
                        date1Field.setText(dateStr);
                    } else if (dialogTitle.contains("окончания")) {
                        date2Field.setText(dateStr);
                    }

                    dialog.dispose();
                }
            });
            daysPanel.add(dayButton);
        }

        int totalCells = daysPanel.getComponentCount();
        int remainingCells = 42 - totalCells;
        for (int i = 0; i < remainingCells; i++) {
            JLabel emptyLabel = new JLabel("");
            emptyLabel.setPreferredSize(new Dimension(40, 35));
            daysPanel.add(emptyLabel);
        }

        daysPanel.revalidate();
        daysPanel.repaint();
    }

    private void chooseDirectory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Выберите папку для сохранения");

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = fileChooser.getSelectedFile();
            directoryField.setText(selectedDir.getAbsolutePath() + File.separator);
        }
    }

    private void startProcessing() {
        if (!validateInput()) {
            return;
        }

        isProcessing = true;
        stopRequested = false;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        currentProcessor = null; // Сбрасываем текущий процессор

        consoleArea.setText(""); // Очищаем консоль GUI

        processingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (Thread.currentThread().isInterrupted() || stopRequested) {
                        appendToConsole("Процесс прерван перед началом");
                        return;
                    }

                    String counterStr = counterComboBox.getSelectedItem().toString();
                    Long counterId;
                    boolean isAppMetrica = false;

                    switch (counterStr) {
                        case "ЕЛК":
                            counterId = 77852878L;
                            break;
                        case "RT.RU":
                            counterId = 102023030L;
                            break;
                        case "МЛК":
                            counterId = 4081789L;
                            isAppMetrica = true;
                            break;
                        default:
                            throw new IllegalArgumentException("Неизвестный счетчик: " + counterStr);
                    }

                    if (Thread.currentThread().isInterrupted() || stopRequested) {
                        appendToConsole("Процесс прерван после выбора счетчика");
                        return;
                    }

                    String authToken = OAuth2Manager.getAccessToken(counterStr);

                    boolean filterEnabled = filterCheckBox.isSelected();
                    String filterCampid = filterTextField.getText();
                    String selectedDate1 = date1Field.getText();
                    String selectedDate2 = date2Field.getText();
                    String fileDirectory = directoryField.getText();

                    appendToConsole("Запуск процессора...");
                    appendToConsole("Счетчик: " + counterStr + " (ID: " + counterId + ")");
                    appendToConsole("Период: " + selectedDate1 + " - " + selectedDate2);
                    appendToConsole("Фильтр: " + (filterEnabled ? "ВКЛ (" + filterCampid + ")" : "ВЫКЛ"));

                    if (Thread.currentThread().isInterrupted() || stopRequested) {
                        appendToConsole("Процесс прерван перед началом обработки");
                        return;
                    }

                    if (isAppMetrica) {
                        appendToConsole("Используется AppMetrica API");
                        exportAppMetricaData(fileDirectory, filterEnabled, filterCampid, selectedDate1, selectedDate2);
                    } else {
                        appendToConsole("Используется Яндекс.Метрика API");
                        // Создаем и сохраняем процессор для возможности остановки
                        currentProcessor = new GUIMetrikaProcessor(
                                counterId, counterStr, selectedDate1, selectedDate2, fileDirectory,
                                filterEnabled, filterCampid, consoleArea, authToken
                        );

                        // Устанавливаем правильное поле в зависимости от счетчика
                        if ("RT.RU".equals(counterStr)) {
                            currentProcessor.setFieldType(YandexMetrikaProcessor.FIELD_TYPE_RT_RU);
                        } else {
                            currentProcessor.setFieldType(YandexMetrikaProcessor.FIELD_TYPE_ELK_MLK);
                            appendToConsole("Используется поле: " + YandexMetrikaProcessor.FIELD_TYPE_ELK_MLK);
                        }

                        currentProcessor.process(); // Теперь все сообщения будут в обеих консолях
                    }

                } catch (Exception e) {
                    if (Thread.currentThread().isInterrupted() || stopRequested) {
                        appendToConsole("Процесс прерван пользователем");
                    } else {
                        String errorMessage = "Критическая ошибка: " + e.getMessage();
                        appendToConsole(errorMessage);

                        // Также выводим в диалоговое окно
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(
                                    YandexMetrikaGUI.this,
                                    "Ошибка при выполнении запроса:\n" + e.getMessage(),
                                    "Ошибка API",
                                    JOptionPane.ERROR_MESSAGE
                            );
                        });

                        // Выводим stack trace в GUI консоль
                        appendToConsole("Stack trace: " + e.toString());
                        for (StackTraceElement element : e.getStackTrace()) {
                            appendToConsole("    at " + element.toString());
                        }
                    }
                } finally {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            isProcessing = false;
                            stopRequested = false;
                            startButton.setEnabled(true);
                            stopButton.setEnabled(false);
                            currentProcessor = null; // Освобождаем процессор
                        }
                    });
                }
            }
        });

        processingThread.start();
    }

    private void exportAppMetricaData(String outputDirectory, boolean enableFilter,
                                      String filterText, String dateSince, String dateUntil) {
        try {
            if (Thread.currentThread().isInterrupted() || stopRequested) {
                appendToConsole("Экспорт прерван перед началом");
                return;
            }

            appendToConsole("Запуск экспорта AppMetrica для МЛК...");

            String time1 = time1Field.getText().trim();
            String time2 = time2Field.getText().trim();

            String formattedDateSince = dateSince + "+" + time1.replace(":", "%3A");
            String formattedDateUntil = dateUntil + "+" + time2.replace(":", "%3A");

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String outputPath = outputDirectory + "Appmetrica_MLK_" + timestamp + ".xlsx";

            appendToConsole("Период: " + dateSince + " " + time1 + " - " + dateUntil + " " + time2);
            appendToConsole("Файл: " + outputPath);
            appendToConsole("Фильтр: " + (enableFilter ? "ВКЛ (" + filterText + ")" : "ВЫКЛ"));

            if (Thread.currentThread().isInterrupted() || stopRequested) {
                appendToConsole("Экспорт прерван перед вызовом AppMetrica");
                return;
            }

            AppMetricaExporter.exportDataForGUI(outputPath, enableFilter, filterText,
                    formattedDateSince, formattedDateUntil, consoleArea);

            if (Thread.currentThread().isInterrupted() || stopRequested) {
                appendToConsole("Экспорт прерван пользователем");
                return;
            }

            appendToConsole("Экспорт AppMetrica завершен успешно!");

        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted() || stopRequested) {
                appendToConsole("Экспорт прерван пользователем");
            } else {
                appendToConsole("Ошибка при экспорте AppMetrica: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void stopProcessing() {
        if (processingThread != null && processingThread.isAlive() && !stopRequested) {
            stopRequested = true;

            // СРАЗУ выводим все сообщения в GUI консоль
            appendToConsole("Отправлен запрос на остановку...");
            appendToConsole("Начинается гарантированное удаление запросов...");
            appendToConsole("Пожалуйста, не закрывайте программу до завершения удаления!");

            // Останавливаем AppMetrica если нужно
            AppMetricaExporter.interruptProcess();

            // Если это GUIMetrikaProcessor, вызываем его метод остановки ТОЛЬКО ОДИН РАЗ
            if (currentProcessor != null) {
                currentProcessor.stopProcess();
            }

            // Прерываем поток
            processingThread.interrupt();

            appendToConsole("Ожидание завершения гарантированного удаления запросов...");
            appendToConsole("Это может занять несколько минут...");

            // Запускаем отдельный поток для отслеживания статуса
            Thread monitorThread = new Thread(() -> {
                try {
                    processingThread.join(300000); // Ждем до 5 минут для cleanup
                    SwingUtilities.invokeLater(() -> {
                        appendToConsole("Процесс остановлен, все запросы удалены");
                        // Восстанавливаем кнопки
                        isProcessing = false;
                        stopRequested = false;
                        startButton.setEnabled(true);
                        stopButton.setEnabled(false);
                        currentProcessor = null;
                    });
                } catch (InterruptedException e) {
                    SwingUtilities.invokeLater(() -> {
                        appendToConsole("Ожидание прервано, но удаление продолжается в фоне");
                    });
                }
            });
            monitorThread.start();

        } else {
            appendToConsole("Процесс не запущен или уже завершен");
        }
    }

    private boolean validateInput() {
        if (date1Field.getText().trim().isEmpty() || date2Field.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Пожалуйста, выберите обе даты", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            LocalDate date1 = LocalDate.parse(date1Field.getText());
            LocalDate date2 = LocalDate.parse(date2Field.getText());
            LocalDate today = LocalDate.now();

            String selectedCounter = (String) counterComboBox.getSelectedItem();
            boolean isMLK = "МЛК".equals(selectedCounter);

            if (!isMLK && !date1.isBefore(today)) {
                JOptionPane.showMessageDialog(this,
                        "Для Яндекс.Метрики дата начала не может быть сегодняшней или будущей датой!\n" +
                                "Максимальная допустимая дата: " + today.minusDays(1),
                        "Ошибка даты начала",
                        JOptionPane.WARNING_MESSAGE);
                return false;
            }

            if (!isMLK && !date2.isBefore(today)) {
                JOptionPane.showMessageDialog(this,
                        "Для Яндекс.Метрики дата окончания не может быть сегодняшней или будущей датой!\n" +
                                "Максимальная допустимая дата: " + today.minusDays(1),
                        "Ошибка даты окончания",
                        JOptionPane.WARNING_MESSAGE);
                return false;
            }

            if (date1.isAfter(date2)) {
                JOptionPane.showMessageDialog(this, "Дата начала не может быть позже даты окончания", "Ошибка дат", JOptionPane.ERROR_MESSAGE);
                return false;
            }

            if (isMLK) {
                if (!isValidTime(time1Field.getText())) {
                    JOptionPane.showMessageDialog(this, "Неверный формат времени начала. Используйте формат ЧЧ:ММ:СС", "Ошибка", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                if (!isValidTime(time2Field.getText())) {
                    JOptionPane.showMessageDialog(this, "Неверный формат времени окончания. Используйте формат ЧЧ:ММ:СС", "Ошибка", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Неверный формат дат. Используйте формат ГГГГ-ММ-ДД", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (directoryField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Пожалуйста, выберите папку для сохранения", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (filterCheckBox.isSelected() && filterTextField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "При включенном фильтре необходимо указать значение campid", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }

    private boolean isValidTime(String time) {
        return time.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$");
    }

    // Метод appendToConsole для GUI
    private void appendToConsole(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                consoleArea.append(text + "\n");
                consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
            }
        });
    }

    private String getDefaultDate1() {
        LocalDate today = LocalDate.now();
        return today.minusDays(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private String getDefaultDate2() {
        LocalDate today = LocalDate.now();
        return today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new YandexMetrikaGUI().setVisible(true);
            }
        });
    }
}