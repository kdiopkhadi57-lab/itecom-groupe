package com.elearning.service;

import com.elearning.entity.SchoolCertificate;
import com.elearning.entity.SchoolEnrollment;
import com.elearning.entity.SchoolPayment;
import com.elearning.entity.User;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** PDF de la scolarité : attestations, relevés de notes et reçus, chacun avec un QR code de vérification. */
@Service
@RequiredArgsConstructor
public class SchoolPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color ACCENT = new Color(0x1d, 0x6f, 0xf2);
    private static final Color TEXT = new Color(0x1c, 0x1d, 0x1f);
    private static final Color MUTED = new Color(0x6a, 0x6f, 0x73);
    private static final Color BORDER = new Color(0xd1, 0xd7, 0xdc);
    private static final float MARGIN = 56;

    private final SchoolService schoolService;

    @Value("${app.school.name:ITECOM}")
    private String schoolName;

    @Value("${app.school.city:Dakar}")
    private String schoolCity;

    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public byte[] certificatePdf(SchoolCertificate c) throws IOException {
        SchoolEnrollment e = c.getEnrollment();
        User s = e.getStudent();
        String title = SchoolService.certificateLabel(c.getType());
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float width = page.getMediaBox().getWidth();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = header(cs, page, "Référence : " + c.getReference());
                y -= 40;
                centered(cs, bold, 20, title.toUpperCase(), width, y, TEXT);
                y -= 18;
                centered(cs, regular, 11, "Année universitaire " + e.getAcademicYear(), width, y, MUTED);
                y -= 40;

                String intro = "Le Directeur des études de " + schoolName + " soussigné atteste que :";
                y = paragraph(cs, regular, 11, intro, MARGIN, y, width - 2 * MARGIN);
                y -= 12;
                y = identity(cs, s, e, y, width);
                y -= 16;

                String body = switch (c.getType()) {
                    case "INSCRIPTION" -> "est régulièrement inscrit(e) en " + e.getLevel() + specialization(e)
                        + " au titre de l'année universitaire " + e.getAcademicYear() + ".";
                    case "SCOLARITE" -> "suit régulièrement les enseignements de " + e.getLevel() + specialization(e)
                        + " dispensés par l'établissement au titre de l'année universitaire " + e.getAcademicYear() + ".";
                    case "REUSSITE" -> "a satisfait aux épreuves de " + e.getLevel() + specialization(e)
                        + " au titre de l'année universitaire " + e.getAcademicYear() + " avec une moyenne générale de "
                        + format(c.getAverage()) + "/20" + (c.getMention() != null ? ", mention « " + c.getMention() + " »." : ".");
                    default -> "a obtenu en " + e.getLevel() + specialization(e) + " les résultats suivants :";
                };
                y = paragraph(cs, regular, 11, body, MARGIN, y, width - 2 * MARGIN);

                if ("RELEVE_NOTES".equals(c.getType())) {
                    y -= 10;
                    y = gradesTable(cs, schoolService.transcript(e, true), y, width);
                }

                y -= 18;
                if (!"RELEVE_NOTES".equals(c.getType())) {
                    y = paragraph(cs, regular, 11, "En foi de quoi, la présente attestation lui est délivrée pour servir et valoir ce que de droit.",
                        MARGIN, y, width - 2 * MARGIN);
                }
                signature(cs, width, Math.min(y - 20, 250), c.getIssuedAt().format(DATE), "Le Directeur des études", c.getIssuedBy());
                qrBlock(cs, c.getVerificationCode(), 70);
                if (c.isRevoked()) watermark(cs, page, "ANNULÉE");
            }
            return save(doc);
        }
    }

    public byte[] receiptPdf(SchoolPayment p) throws IOException {
        SchoolEnrollment e = p.getEnrollment();
        User s = e.getStudent();
        long paid = schoolService.view(e).paid();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float width = page.getMediaBox().getWidth();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = header(cs, page, "Reçu n° " + p.getReceiptNumber());
                y -= 40;
                centered(cs, bold, 20, "REÇU DE PAIEMENT", width, y, TEXT);
                y -= 18;
                centered(cs, regular, 11, "Année universitaire " + e.getAcademicYear(), width, y, MUTED);
                y -= 36;
                y = identity(cs, s, e, y, width);
                y -= 20;
                List<String[]> rows = List.of(
                    new String[]{"Objet", "INSCRIPTION".equals(p.getPurpose()) ? "Frais d'inscription" : "Frais de scolarité"},
                    new String[]{"Montant reçu", SchoolService.formatAmount(p.getAmount())},
                    new String[]{"Mode de paiement", SchoolService.methodLabel(p.getMethod())
                        + (p.getPhone() != null ? " (" + p.getPhone() + ")" : "")},
                    new String[]{"Référence de transaction", p.getTransactionRef() != null ? p.getTransactionRef() : "-"},
                    new String[]{"Date de validation", p.getProcessedAt() != null ? p.getProcessedAt().format(DATE) : "-"},
                    new String[]{"Total des frais de l'année", SchoolService.formatAmount(e.totalDue())},
                    new String[]{"Total payé à ce jour", SchoolService.formatAmount(paid)},
                    new String[]{"Reste à payer", SchoolService.formatAmount(Math.max(0, e.totalDue() - paid))});
                y = keyValueTable(cs, rows, y, width);
                signature(cs, width, Math.min(y - 30, 250), p.getProcessedAt() != null ? p.getProcessedAt().format(DATE) : "",
                    "Le service de la scolarité", p.getProcessedBy());
                qrBlock(cs, p.getVerificationCode(), 70);
                if (!"VALIDATED".equals(p.getStatus())) watermark(cs, page, "NON VALIDÉ");
            }
            return save(doc);
        }
    }

    // ── Blocs communs ──────────────────────────────────────────────────────────

    private float header(PDPageContentStream cs, PDPage page, String reference) throws IOException {
        float width = page.getMediaBox().getWidth();
        float top = page.getMediaBox().getHeight() - MARGIN;
        text(cs, bold, 18, schoolName, MARGIN, top - 4, ACCENT);
        text(cs, regular, 9, "Institut de formation - Service de la scolarité", MARGIN, top - 18, MUTED);
        text(cs, regular, 9, reference, width - MARGIN - regular.getStringWidth(safe(regular, reference)) / 1000 * 9, top - 4, MUTED);
        cs.setStrokingColor(ACCENT);
        cs.setLineWidth(1.5f);
        cs.moveTo(MARGIN, top - 28);
        cs.lineTo(width - MARGIN, top - 28);
        cs.stroke();
        return top - 28;
    }

    private float identity(PDPageContentStream cs, User s, SchoolEnrollment e, float y, float width) throws IOException {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Nom et prénom", s.getLastName().toUpperCase() + " " + s.getFirstName()});
        if (s.getBirthDate() != null) {
            rows.add(new String[]{"Né(e) le", s.getBirthDate().format(DATE) + (s.getBirthPlace() != null ? " à " + s.getBirthPlace() : "")});
        }
        rows.add(new String[]{"Matricule", e.getMatricule()});
        rows.add(new String[]{"Niveau", e.getLevel() + (e.getSpecialization() != null ? " - " + specializationLabel(e.getSpecialization()) : "")});
        return keyValueTable(cs, rows, y, width);
    }

    private float keyValueTable(PDPageContentStream cs, List<String[]> rows, float y, float width) throws IOException {
        float rowH = 22, labelW = 190;
        for (String[] row : rows) {
            cs.setStrokingColor(BORDER);
            cs.setLineWidth(0.5f);
            cs.addRect(MARGIN, y - rowH, width - 2 * MARGIN, rowH);
            cs.stroke();
            text(cs, regular, 10, row[0], MARGIN + 8, y - 15, MUTED);
            text(cs, bold, 10, row[1], MARGIN + labelW, y - 15, TEXT);
            y -= rowH;
        }
        return y;
    }

    private float gradesTable(PDPageContentStream cs, SchoolService.Transcript t, float y, float width) throws IOException {
        float[] cols = {MARGIN + 8, MARGIN + 250, MARGIN + 310, MARGIN + 370, MARGIN + 430};
        float rowH = 18;
        for (SchoolService.SemesterView sem : t.semesters()) {
            text(cs, bold, 11, "Semestre " + sem.semester().substring(1), MARGIN, y - 14, ACCENT);
            y -= 22;
            String[] head = {"Matière", "Coef.", "Normale", "Rattrap.", "Note retenue"};
            for (int i = 0; i < head.length; i++) text(cs, bold, 9, head[i], cols[i], y - 12, MUTED);
            y -= rowH;
            for (SchoolService.GradeLine l : sem.lines()) {
                cs.setStrokingColor(BORDER);
                cs.setLineWidth(0.5f);
                cs.moveTo(MARGIN, y);
                cs.lineTo(width - MARGIN, y);
                cs.stroke();
                text(cs, regular, 9, ellipsize(l.subject(), 42), cols[0], y - 12, TEXT);
                text(cs, regular, 9, format(l.coefficient()), cols[1], y - 12, TEXT);
                text(cs, regular, 9, l.normale() == null ? "-" : format(l.normale()), cols[2], y - 12, TEXT);
                text(cs, regular, 9, l.rattrapage() == null ? "-" : format(l.rattrapage()), cols[3], y - 12, TEXT);
                text(cs, bold, 9, format(l.effective()) + "/20", cols[4], y - 12, TEXT);
                y -= rowH;
                if (y < 260) return y;   // une page : le reste figure sur le bulletin en ligne
            }
            text(cs, bold, 10, "Moyenne du semestre : " + format(sem.average()) + "/20", cols[0], y - 14, TEXT);
            y -= 26;
        }
        if (t.annualAverage() != null) {
            text(cs, bold, 11, "Moyenne générale : " + format(t.annualAverage()) + "/20 - " + t.decision()
                + (t.mention() != null ? " - Mention " + t.mention() : ""), MARGIN, y - 14, TEXT);
            y -= 24;
        }
        return y;
    }

    private void signature(PDPageContentStream cs, float width, float y, String date, String role, String by) throws IOException {
        float x = width - MARGIN - 200;
        text(cs, regular, 10, "Fait à " + schoolCity + ", le " + date, x, y, TEXT);
        text(cs, bold, 10, role, x, y - 18, TEXT);
        if (by != null) text(cs, regular, 9, "Délivré par : " + by, x, y - 34, MUTED);
    }

    private void qrBlock(PDPageContentStream cs, String code, float bottom) throws IOException {
        String url = schoolService.verifyUrl(code);
        float size = 96;
        drawQr(cs, url, MARGIN, bottom, size);
        float x = MARGIN + size + 14;
        text(cs, bold, 9, "Document vérifiable en ligne", x, bottom + size - 14, TEXT);
        text(cs, regular, 8, "Scannez le QR code ou ouvrez :", x, bottom + size - 30, MUTED);
        text(cs, regular, 8, url, x, bottom + size - 42, ACCENT);
        text(cs, regular, 8, "Code de vérification : " + code, x, bottom + size - 58, MUTED);
    }

    private void drawQr(PDPageContentStream cs, String content, float x, float y, float size) throws IOException {
        BitMatrix m;
        try {
            m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                Map.of(EncodeHintType.MARGIN, 0, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
        } catch (WriterException ex) {
            throw new IOException("QR code impossible : " + ex.getMessage(), ex);
        }
        float cell = size / m.getWidth();
        cs.setNonStrokingColor(Color.BLACK);
        for (int row = 0; row < m.getHeight(); row++) {
            for (int col = 0; col < m.getWidth(); col++) {
                if (m.get(col, row)) cs.addRect(x + col * cell, y + size - (row + 1) * cell, cell, cell);
            }
        }
        cs.fill();
    }

    private void watermark(PDPageContentStream cs, PDPage page, String label) throws IOException {
        cs.beginText();
        cs.setFont(bold, 72);
        cs.setNonStrokingColor(new Color(0xd6, 0x33, 0x33));
        cs.setTextMatrix(org.apache.pdfbox.util.Matrix.getRotateInstance(Math.toRadians(35), 140, 300));
        cs.showText(safe(bold, label));
        cs.endText();
    }

    // ── Texte ──────────────────────────────────────────────────────────────────

    private void text(PDPageContentStream cs, PDType1Font font, float size, String value, float x, float y, Color color) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);
        cs.showText(safe(font, value));
        cs.endText();
    }

    private void centered(PDPageContentStream cs, PDType1Font font, float size, String value, float pageWidth, float y, Color color) throws IOException {
        float w = font.getStringWidth(safe(font, value)) / 1000 * size;
        text(cs, font, size, value, (pageWidth - w) / 2, y, color);
    }

    private float paragraph(PDPageContentStream cs, PDType1Font font, float size, String value, float x, float y, float maxWidth) throws IOException {
        StringBuilder line = new StringBuilder();
        for (String word : safe(font, value).split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.getStringWidth(candidate) / 1000 * size > maxWidth && !line.isEmpty()) {
                text(cs, font, size, line.toString(), x, y, TEXT);
                y -= size * 1.5f;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            text(cs, font, size, line.toString(), x, y, TEXT);
            y -= size * 1.5f;
        }
        return y;
    }

    /** Les polices PDF standard ne couvrent que WinAnsi : on remplace les autres caractères. */
    static String safe(PDType1Font font, String value) {
        if (value == null) return "";
        String v = value.replace('’', '\'').replace(' ', ' ').replace(' ', ' ').replace('–', '-');
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            try {
                font.encode(String.valueOf(ch));
                sb.append(ch);
            } catch (Exception ex) {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static String ellipsize(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String format(Double v) {
        if (v == null) return "-";
        return v == Math.floor(v) ? String.valueOf(v.longValue()) : String.format(java.util.Locale.FRANCE, "%.2f", v);
    }

    private static String specialization(SchoolEnrollment e) {
        return e.getSpecialization() != null ? " (" + specializationLabel(e.getSpecialization()) + ")" : "";
    }

    static String specializationLabel(String s) {
        return switch (s) {
            case "genie-logiciel" -> "Génie Logiciel";
            case "reseau" -> "Réseaux et Télécommunications";
            case "comptabilite" -> "Comptabilité et Gestion";
            case "sante" -> "Sciences de la Santé";
            case "marketing-digital" -> "Marketing Digital";
            case "developpement-personnel" -> "Développement Personnel";
            default -> s;
        };
    }

    private static byte[] save(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }
}
