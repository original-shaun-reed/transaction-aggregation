package za.co.reed.ingestorservice.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestResponse {
    private String status;
    private String sourceId;
}
