package YaMetrica;

/*
 * DTO для десериализации JSON-ответа API Яндекс.Метрики,
 * возвращающего список всех лог-запросов для счётчика.
 *
 * <p>Используется при вызове GET /counter/{id}/logrequests.
 * Содержит коллекцию объектов LogRequestResponse, каждый из которых
 * описывает отдельный запрос (его статус, даты, размер и т.д.).</p>
 *
 * <p>Основное применение — поиск уже существующего обработанного запроса,
 * чтобы повторно использовать его вместо создания нового.</p>
 *
 * @see LogRequestResponse
 * @see LogRequest
 */
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LogRequestsListResponse {
    @JsonProperty("requests")
    private List<LogRequestResponse> requests;

    public LogRequestsListResponse() {}

    public List<LogRequestResponse> getRequests() { return requests; }
    public void setRequests(List<LogRequestResponse> requests) { this.requests = requests; }
}