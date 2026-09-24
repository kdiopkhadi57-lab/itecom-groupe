package com.elearning.controller;

import com.elearning.dto.response.ApiResponse;
import com.elearning.dto.response.StudentExamResponse;
import com.elearning.service.ExamSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/student/exams")
@RequiredArgsConstructor
public class StudentExamController {

    private final ExamSubmissionService examSubmissionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StudentExamResponse>>> getMyExams(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(examSubmissionService.getMyExams(user.getUsername())));
    }
}
