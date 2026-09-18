package YaMetrica;

/*
 * Модель (DTO) для десериализации JSON-ответа API Яндекс.Метрики.
 * Представляет ответ на запрос о создании или получении логов.
 *
 * <p>Используется Jackson для преобразования JSON в Java-объект.
 * Поддерживает как вложенный формат (через поле log_request),
 * так и плоский формат (прямые поля requestId, counterId и т.д.).</p>
 *
 * @see LogRequest
 * @see Part
 */

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LogRequestResponse {
    @JsonProperty("log_request")
    private LogRequest logRequest;

    @JsonProperty("request_id")
    private Long requestId;
    @JsonProperty("counter_id")
    private Long counterId;
    private String source;
    private String date1;
    private String date2;
    private List<String> fields;
    private String status;
    private Long size;
    private List<Part> parts;
    private String attribution;

    // Конструктор
    public LogRequestResponse() {}

    // Геттеры и сеттеры
    public LogRequest getLogRequest() { return logRequest; }
    public void setLogRequest(LogRequest logRequest) { this.logRequest = logRequest; }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public Long getCounterId() { return counterId; }
    public void setCounterId(Long counterId) { this.counterId = counterId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDate1() { return date1; }
    public void setDate1(String date1) { this.date1 = date1; }

    public String getDate2() { return date2; }
    public void setDate2(String date2) { this.date2 = date2; }

    public List<String> getFields() { return fields; }
    public void setFields(List<String> fields) { this.fields = fields; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public List<Part> getParts() { return parts; }
    public void setParts(List<Part> parts) { this.parts = parts; }

    public String getAttribution() { return attribution; }
    public void setAttribution(String attribution) { this.attribution = attribution; }
}