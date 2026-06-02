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

        private static final DeviceRgb GREEN = new DeviceRgb(46, 125, 50);
        private static final DeviceRgb GREEN_LIGHT = new DeviceRgb(232, 245, 233);
        private static final DeviceRgb RED = new DeviceRgb(198, 40, 40);
        private static final DeviceRgb RED_LIGHT = new DeviceRgb(255, 235, 238);
        private static final DeviceRgb DARK_BLUE = new DeviceRgb(21, 101, 192);
        private static final DeviceRgb LIGHT_BLUE = new DeviceRgb(99, 91, 255);
        private static final DeviceRgb TEXT_DARK = new DeviceRgb(33, 37, 41);
        private static final DeviceRgb TEXT_MUTED = new DeviceRgb(108, 117, 125);
        private static final DeviceRgb BORDER_COLOR = new DeviceRgb(222, 226, 230);

        /**
         * Generate a professional PDF letter for the given decision.
         * Returns the PDF as byte array and saves to disk + database.
         */
        public byte[] generateLetter(Decision decision) throws IOException {
                String type = decision.getFinalDecision() != null ? decision.getFinalDecision()
                                : decision.getDecision();
                boolean isFavorable = "FAVORABLE".equals(type);
                String ref = decision.getTicketId().toString().substring(0, 8).toUpperCase();

        byte[] pdfBytes;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {
                doc.setMargins(40f, 50f, 40f, 50f);

                // ── 1. TOP ACCENT BAR ──
                com.itextpdf.layout.element.Table accentBar = new com.itextpdf.layout.element.Table(com.itextpdf.layout.properties.UnitValue.createPercentArray(new float[]{100}))
                                .useAllAvailableWidth()
                                .setMarginBottom(15f);
                com.itextpdf.layout.element.Cell accentCell = new com.itextpdf.layout.element.Cell()
                                .setBackgroundColor(LIGHT_BLUE)
                                .setHeight(5f)
                                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
                accentBar.addCell(accentCell);
                doc.add(accentBar);

                // ── 2. HEADER BLOCK (Company & Metadata) ──
                com.itextpdf.layout.element.Table headerTable = new com.itextpdf.layout.element.Table(com.itextpdf.layout.properties.UnitValue.createPercentArray(new float[]{60, 40}))
                                .useAllAvailableWidth()
                                .setMarginBottom(20f);

                com.itextpdf.layout.element.Cell companyCell = new com.itextpdf.layout.element.Cell()
                                .add(new Paragraph("ASSURANCE PIÈCES AUTO")
                                                .setFontSize(18f)
                                                .setBold()
                                                .setFontColor(DARK_BLUE))
                                .add(new Paragraph("Service Gestion des Sinistres & Garanties")
                                                .setFontSize(9f)
                                                .setFontColor(TEXT_MUTED))
                                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);

                com.itextpdf.layout.element.Cell dateCell = new com.itextpdf.layout.element.Cell()
                                .add(new Paragraph("Le " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                                                .setTextAlignment(TextAlignment.RIGHT)
                                                .setFontSize(10f)
                                                .setBold())
                                .add(new Paragraph("Réf. dossier : #" + ref)
                                                .setTextAlignment(TextAlignment.RIGHT)
                                                .setFontSize(10f)
                                                .setItalic()
                                                .setFontColor(LIGHT_BLUE))
                                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);

                headerTable.addCell(companyCell);
                headerTable.addCell(dateCell);
                doc.add(headerTable);

                // Divider
                com.itextpdf.layout.element.Table divTable = new com.itextpdf.layout.element.Table(1).useAllAvailableWidth().setMarginBottom(20f);
                divTable.addCell(new com.itextpdf.layout.element.Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(BORDER_COLOR, 1f)));
                doc.add(divTable);

                // ── 3. ADDRESSES & DETAILS BLOCK ──
                com.itextpdf.layout.element.Table addressTable = new com.itextpdf.layout.element.Table(com.itextpdf.layout.properties.UnitValue.createPercentArray(new float[]{50, 50}))
                                .useAllAvailableWidth()
                                .setMarginBottom(30f);

                com.itextpdf.layout.element.Cell senderCell = new com.itextpdf.layout.element.Cell()
                                .add(new Paragraph("Expéditeur :").setBold().setFontSize(10f).setFontColor(DARK_BLUE).setMarginBottom(3f))
                                .add(new Paragraph("ASSURANCE PIÈCES AUTO\nService Clientèle\n100 Rue de la Paix\n75008 Paris\ncontact@piecesauto-assurance.fr")
                                                .setFontSize(9f)
                                                .setFontColor(TEXT_DARK)
                                                .setMultipliedLeading(1.2f))
                                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);

                com.itextpdf.layout.element.Cell objectCell = new com.itextpdf.layout.element.Cell()
                                .add(new Paragraph("Objet :").setBold().setFontSize(10f).setFontColor(DARK_BLUE).setMarginBottom(3f))
                                .add(new Paragraph("Notification de décision officielle concernant votre demande de prise en charge sous garantie.")
                                                .setFontSize(9f)
                                                .setFontColor(TEXT_DARK)
                                                .setMultipliedLeading(1.2f))
                                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                                .setPaddingLeft(20f);

                addressTable.addCell(senderCell);
                addressTable.addCell(objectCell);
                doc.add(addressTable);

                // ── 4. SALUTATION & OPENING ──
                doc.add(new Paragraph("Madame, Monsieur,")
                                .setFontSize(11f)
                                .setBold()
                                .setFontColor(TEXT_DARK)
                                .setMarginBottom(12f));

                doc.add(new Paragraph("Nous avons examiné avec la plus grande attention votre demande de prise en charge concernant les pièces défectueuses signalées dans votre dossier sinistre.")
                                .setFontSize(10f)
                                .setFontColor(TEXT_DARK)
                                .setMarginBottom(15f));

                // ── 5. DECISION CARD BOX (iText Table Container) ──
                com.itextpdf.layout.element.Table decisionBox = new com.itextpdf.layout.element.Table(1)
                                .useAllAvailableWidth()
                                .setMarginBottom(20f);

                com.itextpdf.layout.element.Cell boxCell = new com.itextpdf.layout.element.Cell()
                                .setPadding(15f)
                                .setBorder(new com.itextpdf.layout.borders.SolidBorder(isFavorable ? GREEN : RED, 1.5f))
                                .setBackgroundColor(isFavorable ? GREEN_LIGHT : RED_LIGHT);

                boxCell.add(new Paragraph(isFavorable ? "✓ DÉCISION : DEMANDE ACCEPTÉE" : "✗ DÉCISION : DEMANDE REFUSÉE")
                                .setFontSize(12f)
                                .setBold()
                                .setFontColor(isFavorable ? GREEN : RED)
                                .setMarginBottom(8f));

                String motif = decision.getOverrideMotif() != null ? decision.getOverrideMotif() : decision.getMotif();
                boxCell.add(new Paragraph("Motif de la décision :")
                                .setFontSize(9.5f)
                                .setBold()
                                .setFontColor(TEXT_DARK)
                                .setMarginBottom(3f));
                boxCell.add(new Paragraph(motif)
                                .setFontSize(10f)
                                .setItalic()
                                .setFontColor(TEXT_DARK));

                decisionBox.addCell(boxCell);
                doc.add(decisionBox);

                // ── 6. INSTRUCTIONS ──
                if (isFavorable) {
                        doc.add(new Paragraph("Vous pouvez désormais vous présenter auprès de l'un de nos garages partenaires agréés muni de cette notification officielle et de votre référence dossier (#" + ref + ") afin de faire procéder au remplacement ou à la réparation des pièces approuvées.")
                                        .setFontSize(10f)
                                        .setFontColor(TEXT_DARK)
                                        .setMarginBottom(15f));
                } else {
                        doc.add(new Paragraph("Conformément aux conditions générales de votre contrat d'assurance, vous disposez d'un droit de contestation de cette décision. Pour soumettre un recours ou obtenir des clarifications, nous vous invitons à contacter notre Service de Réclamations à l'adresse email reclamation@callmgmt.com ou par téléphone.")
                                        .setFontSize(10f)
                                        .setFontColor(TEXT_DARK)
                                        .setMarginBottom(15f));
                }

                doc.add(new Paragraph("Nous vous prions d'agréer, Madame, Monsieur, l'expression de nos salutations distinguées.")
                                .setFontSize(10f)
                                .setFontColor(TEXT_DARK)
                                .setMarginBottom(30f));

                // ── 7. SIGNATURE ──
                Paragraph signatureBlock = new Paragraph()
                                .setTextAlignment(TextAlignment.RIGHT)
                                .add(new com.itextpdf.layout.element.Text("Cordialement,\n").setFontSize(10f).setFontColor(TEXT_DARK))
                                .add(new com.itextpdf.layout.element.Text("Le Service Clientèle Assurance\n").setFontSize(10f).setBold().setFontColor(DARK_BLUE))
                                .add(new com.itextpdf.layout.element.Text("AutoAssist France").setFontSize(9f).setFontColor(TEXT_MUTED));
                doc.add(signatureBlock);

                // ── 8. FOOTER BLOCK ──
                // Positioned at the bottom using simple styling
                doc.add(new Paragraph("\n\n\n"));
                com.itextpdf.layout.element.Table footerTable = new com.itextpdf.layout.element.Table(1).useAllAvailableWidth();
                footerTable.addCell(new com.itextpdf.layout.element.Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setBorderTop(new com.itextpdf.layout.borders.SolidBorder(BORDER_COLOR, 1f)));
                doc.add(footerTable);

                doc.add(new Paragraph("ASSURANCE PIÈCES AUTO — SA au capital de 1 500 000 € — RCS Paris B 517 384 920\nSiège social : 100 Rue de la Paix, 75008 Paris. Document généré automatiquement pour valoir ce que de droit.")
                                .setTextAlignment(TextAlignment.CENTER)
                                .setFontSize(7.5f)
                                .setFontColor(TEXT_MUTED)
                                .setMarginTop(5f));
        }
        pdfBytes = baos.toByteArray();

                // Save to file system
                Path dirPath = Paths.get(storagePath);
                Files.createDirectories(dirPath);
                String fileName = decision.getId().toString() + ".pdf";
                Path filePath = dirPath.resolve(fileName);
                Files.write(filePath, pdfBytes);

                // Save letter record
                java.util.List<Letter> existingLetters = letterRepository.findByDecisionId(decision.getId());
                Letter letter;
                if (!existingLetters.isEmpty()) {
                    letter = existingLetters.get(0);
                    letter.setFilePath(filePath.toString());
                    letter.setType(type);
                } else {
                    letter = Letter.builder()
                            .decisionId(decision.getId())
                            .filePath(filePath.toString())
                            .type(type)
                            .sentByEmail(false)
                            .build();
                }
                letterRepository.save(letter);

                log.info("📄 Premium PDF letter generated for decision {} — type: {}, path: {}",
                                decision.getId(), type, filePath);

                return pdfBytes;
        }
}
