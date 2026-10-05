package com.elearning.controller;

import com.elearning.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Vérification publique d'une attestation ou d'un reçu à partir du code de son QR code. */
@RestController
@RequestMapping("/api/public/verify")
@RequiredArgsConstructor
public class PublicVerificationController {

    private final SchoolService schoolService;

    @GetMapping("/{code}")
    public Map<String, Object> verify(@PathVariable String code) {
        return schoolService.verify(code);
    }
}
