package com.elearning.dto.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FullscreenViolationRequest {
    private Integer violationNumber;
    private String details;
    private Boolean shouldTerminate;
}
