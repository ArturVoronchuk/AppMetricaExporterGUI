package YaMetrica;

/* Part — информация об одной части данных.
* API Яндекс.Метрики может разбивать большие данные на части (parts).
* Каждая часть скачивается отдельным запросом.
 */

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Part {
    @JsonProperty("part_number")
    private Integer partNumber;

    private Long size;

    public Integer getPartNumber() { return partNumber; }
    public void setPartNumber(Integer partNumber) { this.partNumber = partNumber; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
}