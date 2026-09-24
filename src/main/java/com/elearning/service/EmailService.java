package com.elearning.service;

import com.elearning.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    public void sendVerificationEmail(String to, String firstName, String token) {
        String verifyUrl = frontendUrl + "/auth/verify-email?token=" + token + "&email=" + java.net.URLEncoder.encode(to, java.nio.charset.StandardCharsets.UTF_8);
        String subject = "Vérification de votre compte ELearning";
        String content = buildVerificationEmailContent(firstName, verifyUrl);
        sendEmail(to, subject, content);
    }

    public void sendPasswordResetEmail(String to, String firstName, String token) {
        String resetUrl = frontendUrl + "/auth/reset-password?token=" + token;
        String subject = "Réinitialisation de votre mot de passe";
        String content = buildResetEmailContent(firstName, resetUrl);
        sendEmail(to, subject, content);
    }

    public void sendExamInvitation(String to, String studentName, String examTitle, String accessToken) {
        String examUrl = frontendUrl + "/exam/" + accessToken;
        String subject = "Invitation à l'examen : " + examTitle;
        String content = buildExamInvitationContent(studentName, examTitle, examUrl);
        sendEmail(to, subject, content);
    }

    public void sendExamResult(String to, String studentName, String examTitle,
                               double obtained, double max, double percentage,
                               String summary, String detailedReport) {
        String subject = "Résultats de l'examen : " + examTitle;
        String content = buildExamResultContent(studentName, examTitle, obtained, max, percentage, summary, detailedReport);
        sendEmail(to, subject, content);
    }

    @Value("${app.frontend.url}")
    private String frontendUrlForAdmin;

    public void sendPaymentReceivedToAdmin(String adminEmail, User student) {
        String adminUrl = frontendUrlForAdmin + "/admin/registrations";
        String method = "WAVE".equals(student.getPaymentMethod()) ? "Wave" : "Orange Money";
        String content = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto'>" +
            "<div style='background:linear-gradient(135deg,#f59e0b,#d97706);padding:30px;text-align:center'>" +
            "<h1 style='color:white;margin:0'>🔔 Nouvelle inscription à valider</h1></div>" +
            "<div style='padding:30px;background:#f9f9f9'>" +
            "<p>Un étudiant a soumis son paiement et attend la validation de son compte.</p>" +
            "<table style='width:100%;border-collapse:collapse;margin:16px 0'>" +
            "<tr><td style='padding:8px;font-weight:bold;color:#555'>Nom</td><td style='padding:8px'>" + student.getFirstName() + " " + student.getLastName() + "</td></tr>" +
            "<tr style='background:#f0f0f0'><td style='padding:8px;font-weight:bold;color:#555'>Email</td><td style='padding:8px'>" + student.getEmail() + "</td></tr>" +
            "<tr><td style='padding:8px;font-weight:bold;color:#555'>Filière</td><td style='padding:8px'>" + (student.getSpecialization() != null ? student.getSpecialization() : "—") + "</td></tr>" +
            "<tr style='background:#f0f0f0'><td style='padding:8px;font-weight:bold;color:#555'>Méthode de paiement</td><td style='padding:8px'>" + method + "</td></tr>" +
            "<tr><td style='padding:8px;font-weight:bold;color:#555'>Téléphone</td><td style='padding:8px'>" + student.getPaymentPhone() + "</td></tr>" +
            "<tr style='background:#f0f0f0'><td style='padding:8px;font-weight:bold;color:#555'>Référence transaction</td><td style='padding:8px;font-weight:bold;color:#d97706'>" + student.getPaymentReference() + "</td></tr>" +
            "</table>" +
            "<div style='text-align:center;margin:24px 0'>" +
            "<a href='" + adminUrl + "' style='background:#0f3460;color:white;padding:14px 28px;text-decoration:none;border-radius:8px;font-size:15px'>Accéder aux validations</a></div>" +
            "</div></div>";
        sendEmail(adminEmail, "🔔 Nouvelle inscription en attente — " + student.getFirstName() + " " + student.getLastName(), content);
    }

    public void sendFullscreenExclusionAlert(String professorEmail, String professorName, String studentName,
                                              String studentEmail, String title, int violationCount, String type) {
        String label = "QCM".equals(type) ? "QCM" : "examen";
        String content = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto'>" +
            "<div style='background:linear-gradient(135deg,#ef4444,#dc2626);padding:30px;text-align:center'>" +
            "<h1 style='color:white;margin:0'>🚫 Étudiant exclu pour violations anti-triche</h1></div>" +
            "<div style='padding:30px;background:#f9f9f9'>" +
            "<p>Bonjour " + professorName + ",</p>" +
            "<p>Un(e) étudiant(e) a quitté le mode plein écran à " + violationCount + " reprises pendant " +
            (label.equals("QCM") ? "le QCM" : "l'examen") + " ci-dessous et a été automatiquement exclu(e).</p>" +
            "<table style='width:100%;border-collapse:collapse;margin:16px 0'>" +
            "<tr><td style='padding:8px;font-weight:bold;color:#555'>Étudiant</td><td style='padding:8px'>" + studentName + "</td></tr>" +
            "<tr style='background:#f0f0f0'><td style='padding:8px;font-weight:bold;color:#555'>Email</td><td style='padding:8px'>" + studentEmail + "</td></tr>" +
            "<tr><td style='padding:8px;font-weight:bold;color:#555'>" + (label.equals("QCM") ? "QCM" : "Examen") + "</td><td style='padding:8px'>" + title + "</td></tr>" +
            "<tr style='background:#f0f0f0'><td style='padding:8px;font-weight:bold;color:#555'>Nombre de sorties du plein écran</td><td style='padding:8px;font-weight:bold;color:#dc2626'>" + violationCount + "</td></tr>" +
            "</table>" +
            "<p style='color:#6b7280;font-size:13px'>Ses réponses ont été soumises automatiquement dans leur état au moment de l'exclusion. " +
            "L'étudiant(e) ne pourra pas se reconnecter à la plateforme ni consulter son résultat avant la fin du temps estimé de cette épreuve.</p>" +
            "</div></div>";
        sendEmail(professorEmail, "🚫 Exclusion anti-triche — " + studentName + " (" + title + ")", content);
    }

    public void sendRegistrationApproved(String to, String firstName) {
        String loginUrl = frontendUrlForAdmin + "/auth/login";
        String resetUrl = frontendUrlForAdmin + "/auth/forgot-password";
        String content = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto'>" +
            "<div style='background:linear-gradient(135deg,#10b981,#059669);padding:30px;text-align:center'>" +
            "<h1 style='color:white;margin:0'>✅ Compte activé !</h1></div>" +
            "<div style='padding:30px;background:#f9f9f9'>" +
            "<h2>Félicitations, " + firstName + " !</h2>" +
            "<p>Votre paiement a été vérifié et votre compte est maintenant <strong>actif</strong>. Vous pouvez accéder à la plateforme dès maintenant.</p>" +
            "<div style='text-align:center;margin:24px 0'>" +
            "<a href='" + loginUrl + "' style='background:#10b981;color:white;padding:14px 28px;text-decoration:none;border-radius:8px;font-size:15px'>Se connecter à la plateforme</a></div>" +
            "<hr style='border:none;border-top:1px solid #e5e7eb;margin:24px 0'>" +
            "<p style='color:#6b7280;font-size:13px'>Vous souhaitez changer votre mot de passe ? " +
            "<a href='" + resetUrl + "' style='color:#10b981'>Réinitialiser mon mot de passe</a></p>" +
            "</div></div>";
        sendEmail(to, "✅ Votre compte ELearning est activé — Bienvenue " + firstName + " !", content);
    }

    public void sendRegistrationRejected(String to, String firstName, String reason) {
        String content = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto'>" +
            "<div style='background:linear-gradient(135deg,#ef4444,#dc2626);padding:30px;text-align:center'>" +
            "<h1 style='color:white;margin:0'>❌ Demande d'inscription refusée</h1></div>" +
            "<div style='padding:30px;background:#f9f9f9'>" +
            "<h2>Bonjour " + firstName + ",</h2>" +
            "<p>Nous avons examiné votre demande d'inscription mais nous ne pouvons pas la valider pour le moment.</p>" +
            (reason != null && !reason.isBlank() ? "<p><strong>Motif :</strong> " + reason + "</p>" : "") +
            "<p>Veuillez contacter l'administration pour plus d'informations.</p>" +
            "</div></div>";
        sendEmail(to, "❌ Votre demande d'inscription ELearning", content);
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        if (!mailEnabled) {
            log.debug("Email désactivé, message non envoyé à {}: {}", to, subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            // A mail outage must not roll back grading or account operations.
            log.warn("Email non envoyé à {}: {}", to, e.getMessage());
        }
    }

    private String buildVerificationEmailContent(String firstName, String url) {
        return "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
            "<div style='background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; text-align: center;'>" +
            "<h1 style='color: white; margin: 0;'>🎓 ELearning Platform</h1></div>" +
            "<div style='padding: 30px; background: #f9f9f9;'>" +
            "<h2>Bonjour " + firstName + " !</h2>" +
            "<p>Merci de vous être inscrit sur notre plateforme. Voici les étapes pour finaliser votre inscription :</p>" +
            "<div style='background:#fff;border-radius:10px;padding:20px;margin:20px 0;border:1px solid #e5e7eb'>" +
            "<div style='display:flex;align-items:center;margin-bottom:12px'>" +
            "<span style='background:#667eea;color:white;border-radius:50%;width:28px;height:28px;display:inline-flex;align-items:center;justify-content:center;font-weight:bold;margin-right:12px;flex-shrink:0'>1</span>" +
            "<span><strong>Vérifiez votre email</strong> — cliquez sur le bouton ci-dessous</span></div>" +
            "<div style='display:flex;align-items:center;margin-bottom:12px'>" +
            "<span style='background:#f59e0b;color:white;border-radius:50%;width:28px;height:28px;display:inline-flex;align-items:center;justify-content:center;font-weight:bold;margin-right:12px;flex-shrink:0'>2</span>" +
            "<span><strong>Procédez au paiement</strong> — via Wave ou Orange Money (vous serez redirigé automatiquement)</span></div>" +
            "<div style='display:flex;align-items:center'>" +
            "<span style='background:#10b981;color:white;border-radius:50%;width:28px;height:28px;display:inline-flex;align-items:center;justify-content:center;font-weight:bold;margin-right:12px;flex-shrink:0'>3</span>" +
            "<span><strong>Activation du compte</strong> — l'administration valide votre paiement et active votre accès</span></div>" +
            "</div>" +
            "<div style='text-align: center; margin: 30px 0;'>" +
            "<a href='" + url + "' style='background: #667eea; color: white; padding: 15px 30px; " +
            "text-decoration: none; border-radius: 8px; font-size: 16px;'>✅ Vérifier mon email</a></div>" +
            "<p style='color: #666; font-size:13px;'>Ce lien expire dans <strong>24 heures</strong>. Si vous n'avez pas créé ce compte, ignorez cet email.</p>" +
            "</div></div>";
    }

    private String buildResetEmailContent(String firstName, String url) {
        return "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
            "<div style='background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); padding: 30px; text-align: center;'>" +
            "<h1 style='color: white; margin: 0;'>&#128272; Réinitialisation du mot de passe</h1></div>" +
            "<div style='padding: 30px;'>" +
            "<h2>Bonjour " + firstName + " !</h2>" +
            "<p>Vous avez demandé la réinitialisation de votre mot de passe :</p>" +
            "<div style='text-align: center; margin: 30px 0;'>" +
            "<a href='" + url + "' style='background: #f5576c; color: white; padding: 15px 30px; " +
            "text-decoration: none; border-radius: 8px; font-size: 16px;'>Réinitialiser le mot de passe</a></div>" +
            "<p style='color: #666;'>Ce lien expire dans 2 heures. Si vous n'avez pas fait cette demande, ignorez cet email.</p>" +
            "</div></div>";
    }

    private String buildExamInvitationContent(String studentName, String examTitle, String examUrl) {
        return "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
            "<div style='background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); padding: 30px; text-align: center;'>" +
            "<h1 style='color: white; margin: 0;'>&#128221; Examen en ligne</h1></div>" +
            "<div style='padding: 30px; background: #f9f9f9;'>" +
            "<h2>Bonjour " + studentName + " !</h2>" +
            "<p>Vous avez été invité(e) à passer l'examen :</p>" +
            "<h3 style='color: #4facfe;'>" + examTitle + "</h3>" +
            "<p>Cliquez sur le bouton ci-dessous pour accéder à votre examen :</p>" +
            "<div style='text-align: center; margin: 30px 0;'>" +
            "<a href='" + examUrl + "' style='background: #4facfe; color: white; padding: 15px 30px; " +
            "text-decoration: none; border-radius: 8px; font-size: 16px;'>Passer l'examen</a></div>" +
            "<p style='color: #666; font-size: 12px;'>Ce lien est personnel et ne doit pas être partagé.</p>" +
            "</div></div>";
    }

    private String buildExamResultContent(String studentName, String examTitle,
                                          double obtained, double max, double percentage,
                                          String summary, String detailedReport) {
        String color = percentage >= 50 ? "#27ae60" : "#e74c3c";
        String mention = percentage >= 90 ? "Excellent" : percentage >= 70 ? "Bien" :
                         percentage >= 50 ? "Passable" : "Insuffisant";

        return "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
            "<div style='background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; text-align: center;'>" +
            "<h1 style='color: white; margin: 0;'>&#127941; Résultats de l'examen</h1></div>" +
            "<div style='padding: 30px;'>" +
            "<h2>Bonjour " + studentName + " !</h2>" +
            "<p>Voici vos résultats pour l'examen : <strong>" + examTitle + "</strong></p>" +
            "<div style='background: #f0f0f0; border-radius: 10px; padding: 20px; text-align: center; margin: 20px 0;'>" +
            "<div style='font-size: 48px; font-weight: bold; color: " + color + ";'>" +
            String.format("%.1f", obtained) + " / " + String.format("%.1f", max) + "</div>" +
            "<div style='font-size: 24px; color: " + color + ";'>" +
            String.format("%.1f%%", percentage) + " - " + mention + "</div></div>" +
            "<h3>Appréciation générale :</h3>" +
            "<p style='background: #f9f9f9; padding: 15px; border-left: 4px solid #667eea;'>" + summary + "</p>" +
            "<h3>Détail par question :</h3>" +
            "<pre style='background: #f5f5f5; padding: 15px; border-radius: 5px; white-space: pre-wrap; font-family: Arial;'>" +
            detailedReport + "</pre>" +
            "</div></div>";
    }
}
