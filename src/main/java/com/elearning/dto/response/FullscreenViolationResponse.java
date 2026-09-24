package com.elearning.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class FullscreenViolationResponse {
    private Long id;
    private Integer violationNumber;
    private LocalDateTime violationTime;
    private LocalDateTime recoveredAt;
    private String details;
}
