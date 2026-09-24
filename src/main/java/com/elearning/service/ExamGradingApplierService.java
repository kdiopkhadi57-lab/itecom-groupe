package com.elearning.service;

import com.elearning.entity.*;
import com.elearning.repository.ExamAnswerRepository;
import com.elearning.repository.ExamStudentRepository;
import com.elearning.repository.ExamSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamGradingApplierService {

    private final ExamAnswerRepository answerRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final ExamStudentRepository examStudentRepository;
    private final EmailService emailService;

    @Transactional
    public void applyGradingResult(ExamSubmission submission, ExamGradingService.GradingResult result) {
        Map<Long, ExamGradingService.AnswerScore> scoresByQuestionId = result.scores().stream()
            .collect(Collectors.toMap(ExamGradingService.AnswerScore::questionId, s -> s));

        double totalObtained = 0;
        double totalMax = 0;

        for (ExamAnswer answer : submission.getAnswers()) {
            ExamGradingService.AnswerScore score = scoresByQuestionId.get(answer.getQuestion().getId());
            if (score != null) {
                double capped = Math.min(score.score(), answer.getQuestion().getMaxScore());
                answer.setObtainedScore(Math.max(0, capped));
                answer.setAiComment(score.comment());
                if (score.transcribedAnswer() != null && !score.transcribedAnswer().isBlank()) {
                    answer.setStudentAnswer(score.transcribedAnswer());
                }
                totalObtained += answer.getObtainedScore();
            }
            totalMax += answer.getQuestion().getMaxScore();
        }

        answerRepository.saveAll(submission.getAnswers());

        submission.setTotalScore(totalObtained);
        submission.setMaxScore(totalMax);
        submission.setAiReport(result.summary());
        submission.setIsGraded(true);
        submission.setGradedAt(LocalDateTime.now());
        submissionRepository.save(submission);

        ExamStudent student = submission.getExamStudent();
        student.setStatus(StudentExamStatus.GRADED);
        examStudentRepository.save(student);

        double percentage = totalMax > 0 ? (totalObtained / totalMax) * 100 : 0;
        emailService.sendExamResult(
            student.getStudentEmail(),
            student.getStudentName(),
            student.getExam().getTitle(),
            totalObtained,
            totalMax,
            percentage,
            result.summary(),
            buildDetailedReport(submission)
        );

        log.info("Soumission {} corrigée: {}/{} ({}%)", submission.getId(), totalObtained, totalMax,
            String.format("%.1f", percentage));
    }

    public String buildDetailedReport(ExamSubmission submission) {
        StringBuilder sb = new StringBuilder();
        for (ExamAnswer answer : submission.getAnswers()) {
            sb.append("Q").append(answer.getQuestion().getOrderIndex())
              .append(": ").append(answer.getQuestion().getQuestionText()).append("\n");
            sb.append("Votre réponse: ").append(answer.getStudentAnswer() != null ? answer.getStudentAnswer() : "(aucune)").append("\n");
            sb.append("Note: ").append(answer.getObtainedScore() != null ? answer.getObtainedScore() : 0)
              .append("/").append(answer.getQuestion().getMaxScore()).append("\n");
            sb.append("Commentaire: ").append(answer.getAiComment() != null ? answer.getAiComment() : "").append("\n\n");
        }
        return sb.toString();
    }
}
