package com.elearning.service;

import com.elearning.entity.ExamSubmission;
import com.elearning.repository.ExamSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamSchedulerService {

    private final ExamSubmissionRepository submissionRepository;
    private final ExamGradingService gradingService;
    private final ExamGradingApplierService gradingApplierService;

    @Value("${app.exam.grading-delay-minutes:60}")
    private long gradingDelayMinutes;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processGrading() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(gradingDelayMinutes);
        List<ExamSubmission> toGrade = submissionRepository.findUngraded(deadline);

        if (toGrade.isEmpty()) return;
        log.info("Correction de {} soumission(s)...", toGrade.size());

        for (ExamSubmission submission : toGrade) {
            try {
                gradeSubmission(submission);
            } catch (Exception e) {
                log.error("Erreur correction soumission {}: {}", submission.getId(), e.getMessage());
            }
        }
    }

    private void gradeSubmission(ExamSubmission submission) {
        ExamGradingService.GradingResult result = gradingService.gradeSubmission(submission);
        gradingApplierService.applyGradingResult(submission, result);
    }
}
