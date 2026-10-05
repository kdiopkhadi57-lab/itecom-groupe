package com.elearning.service;

import com.elearning.dto.request.LoginRequest;
import com.elearning.dto.request.RegisterRequest;
import com.elearning.dto.response.AuthResponse;
import com.elearning.dto.response.ApiResponse;

public interface AuthService {
    ApiResponse<String> register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    /** Nouvelle session à partir d'un jeton de rafraîchissement (retour de connexion après une période hors ligne). */
    AuthResponse refresh(String refreshToken);
    ApiResponse<String> verifyEmail(String token);
    ApiResponse<String> forgotPassword(String email);
    ApiResponse<String> resetPassword(String token, String newPassword);
}
