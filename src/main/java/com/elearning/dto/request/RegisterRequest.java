package com.elearning.dto.request;

import com.elearning.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    @Email @NotBlank private String email;
    @NotBlank @Size(min = 8) private String password;
    private Role role = Role.ROLE_STUDENT;
    private String specialization;
    @NotBlank private String paymentMethod;   // WAVE | ORANGE_MONEY
    @NotBlank private String paymentPhone;
    @NotBlank private String paymentReference;
}
