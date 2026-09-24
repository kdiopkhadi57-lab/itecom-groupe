package com.elearning.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class QcmFullscreenViolationResponse {
    private Long id;
    private Integer violationNumber;
    private LocalDateTime violationTime;
    private String details;
}
