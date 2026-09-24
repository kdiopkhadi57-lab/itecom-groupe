package com.elearning.dto.response;

import com.elearning.entity.Role;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class UserResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private String specialization;
    private String avatarUrl;
    private String bio;
    private boolean enabled;
    private LocalDateTime createdAt;
}
