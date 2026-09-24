package com.elearning.dto.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QcmFullscreenViolationRequest {
    private Integer violationNumber;
    private String details;
    private Boolean shouldTerminate;
}
