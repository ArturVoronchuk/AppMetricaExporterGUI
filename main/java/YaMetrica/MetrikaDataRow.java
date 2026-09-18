package YaMetrica;

/* MetrikaDataRow – DTO для хранения одной записи: campid, nls, event_type, event_date, systemName. */

public class MetrikaDataRow {
    private String campid;     // id кампании
    private String nls;        // НЛС
    private String eventType;  // тип события (1, 2, 3...8)
    private String eventDate;  // дата события
    private String systemName; // системное имя

    // campid, nls, eventType, eventDate, systemName
    public MetrikaDataRow(String campid, String nls, String eventType, String eventDate, String systemName) {
        this.campid = campid;
        this.nls = nls;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.systemName = systemName;
    }

    // Геттеры
    public String getCampid() { return campid; }
    public String getNls() { return nls; }
    public String getEventType() { return eventType; }
    public String getEventDate() { return eventDate; }
    public String getSystemName() { return systemName; }
}
