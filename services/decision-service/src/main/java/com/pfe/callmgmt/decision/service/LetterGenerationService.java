package com.pfe.callmgmt.decision.service;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.pfe.callmgmt.decision.entity.Decision;
import com.pfe.callmgmt.decision.entity.Letter;
import com.pfe.callmgmt.decision.repository.LetterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class LetterGenerationService {

        private final LetterRepository letterRepository;

        	@Value("${letters.storage-path:/tmp/letters}")
	private String storagePath;

        private static final DeviceRgb GREEN = new DeviceRgb(34, 139, 34);
        private static final DeviceRgb RED = new DeviceRgb(178, 34, 34);
        private static final DeviceRgb DARK_BLUE = new DeviceRgb(0, 51, 102);

        /**
         * Generate a professional PDF letter for the given decision.
         * Returns the PDF as byte array and saves to disk + database.
         */
        public byte[] generateLetter(Decision decision) throws IOException {
                String type = decision.getFinalDecision() != null ? decision.getFinalDecision()
                                : decision.getDecision();
                boolean isFavorable = "FAVORABLE".equals(type);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PdfWriter writer = new PdfWriter(baos);
                PdfDocument pdf = new PdfDocument(writer);
                Document doc = new Document(pdf);
                doc.setMargins(56.7f, 56.7f, 56.7f, 56.7f); // 2cm margins

                // ── HEADER ──
                Paragraph companyName = new Paragraph("ASSURANCE PIÈCES AUTO")
                                .setFontSize(18)
                                .setBold()
                                .setFontColor(DARK_BLUE);
                doc.add(companyName);

                Paragraph date = new Paragraph(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                                .setTextAlignment(TextAlignment.RIGHT)
                                .setFontSize(11);
                doc.add(date);

                doc.add(new Paragraph("\n"));

                // ── SUBJECT ──
                String subjectAction = isFavorable ? "Acceptation" : "Refus";
                doc.add(new Paragraph("Objet : " + subjectAction + " de votre demande de garantie")
                                .setBold()
                                .setFontSize(13));

                String ref = decision.getTicketId().toString().substring(0, 8).toUpperCase();
                doc.add(new Paragraph("Réf. dossier : " + ref)
                                .setFontSize(11)
                                .setItalic());

                doc.add(new Paragraph("\n"));

                // ── SALUTATION ──
                doc.add(new Paragraph("Madame, Monsieur,")
                                .setFontSize(12));

                doc.add(new Paragraph("\n"));

                // ── BODY ──
                doc.add(new Paragraph(
                                "Nous avons bien reçu votre demande de prise en charge concernant la pièce concernée.")
                                .setFontSize(12));

                doc.add(new Paragraph("\n"));

                if (isFavorable) {
                        Paragraph para2 = new Paragraph()
                                        .setFontSize(12)
                                        .add("Après examen de votre dossier et vérification de nos conditions de garantie, "
                                                        +
                                                        "nous avons le plaisir de vous informer que votre demande est ")
                                        .add(new com.itextpdf.layout.element.Text("ACCEPTÉE")
                                                        .setBold()
                                                        .setFontColor(GREEN))
                                        .add(".");
                        doc.add(para2);
                } else {
                        Paragraph para2 = new Paragraph()
                                        .setFontSize(12)
                                        .add("Après examen approfondi de votre dossier, nous sommes dans l'obligation de vous informer "
                                                        +
                                                        "que votre demande ne peut être prise en charge. La décision est : ")
                                        .add(new com.itextpdf.layout.element.Text("REFUSÉE")
                                                        .setBold()
                                                        .setFontColor(RED))
                                        .add(".");
                        doc.add(para2);
                }

                doc.add(new Paragraph("\n"));

                // Motif
                String motif = decision.getOverrideMotif() != null ? decision.getOverrideMotif() : decision.getMotif();
                doc.add(new Paragraph("Motif : " + motif)
                                .setFontSize(12)
                                .setItalic());

                doc.add(new Paragraph("\n"));

                doc.add(new Paragraph("Pour toute question, n'hésitez pas à contacter notre service client.")
                                .setFontSize(12));

                doc.add(new Paragraph("\n\n"));

                // ── SIGNATURE ──
                doc.add(new Paragraph("Cordialement,")
                                .setFontSize(12));
                doc.add(new Paragraph("Le Service Assurance")
                                .setFontSize(12)
                                .setBold());
                doc.add(new Paragraph("ASSURANCE PIÈCES AUTO")
                                .setFontSize(12)
                                .setFontColor(DARK_BLUE)
                                .setBold());

                doc.close();
                byte[] pdfBytes = baos.toByteArray();

                // Save to file system
                Path dirPath = Paths.get(storagePath);
                Files.createDirectories(dirPath);
                String fileName = decision.getId().toString() + ".pdf";
                Path filePath = dirPath.resolve(fileName);
                Files.write(filePath, pdfBytes);

                // Save letter record
                Letter letter = Letter.builder()
                                .decisionId(decision.getId())
                                .filePath(filePath.toString())
                                .type(type)
                                .sentByEmail(false)
                                .build();
                letterRepository.save(letter);

                log.info("📄 PDF letter generated for decision {} — type: {}, path: {}",
                                decision.getId(), type, filePath);

                return pdfBytes;
        }
}
