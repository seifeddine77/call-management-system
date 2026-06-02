package com.pfe.callmgmt.decision.service;

import com.pfe.callmgmt.decision.entity.Decision;
import com.pfe.callmgmt.decision.entity.Letter;
import com.pfe.callmgmt.decision.repository.LetterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LetterGenerationServiceTest {

    @Mock
    private LetterRepository letterRepository;

    @InjectMocks
    private LetterGenerationService letterGenerationService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        Field field = LetterGenerationService.class.getDeclaredField("storagePath");
        field.setAccessible(true);
        field.set(letterGenerationService, tempDir.toString());
    }

    @Test
    @DisplayName("Génère un PDF FAVORABLE avec contenu valide")
    void generateLetter_favorable_createsPdf() throws IOException {
        UUID decisionId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Decision decision = Decision.builder()
                .id(decisionId)
                .ticketId(ticketId)
                .decision("FAVORABLE")
                .finalDecision("FAVORABLE")
                .motif("Pièce sous garantie — remplacement approuvé")
                .score(BigDecimal.valueOf(85.0))
                .source("HYBRID")
                .build();

        when(letterRepository.findByDecisionId(decisionId)).thenReturn(Collections.emptyList());
        when(letterRepository.save(any(Letter.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] pdfBytes = letterGenerationService.generateLetter(decision);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(100); // PDF should have content
        // Check PDF magic bytes
        assertThat(pdfBytes[0]).isEqualTo((byte) '%');
        assertThat(pdfBytes[1]).isEqualTo((byte) 'P');
        assertThat(pdfBytes[2]).isEqualTo((byte) 'D');
        assertThat(pdfBytes[3]).isEqualTo((byte) 'F');

        // Verify file was written to disk
        Path expectedFile = tempDir.resolve(decisionId.toString() + ".pdf");
        assertThat(Files.exists(expectedFile)).isTrue();
        assertThat(Files.size(expectedFile)).isEqualTo(pdfBytes.length);

        // Verify letter record saved
        ArgumentCaptor<Letter> captor = ArgumentCaptor.forClass(Letter.class);
        verify(letterRepository).save(captor.capture());
        Letter saved = captor.getValue();
        assertThat(saved.getDecisionId()).isEqualTo(decisionId);
        assertThat(saved.getType()).isEqualTo("FAVORABLE");
    }

    @Test
    @DisplayName("Génère un PDF DEFAVORABLE")
    void generateLetter_defavorable_createsPdf() throws IOException {
        UUID decisionId = UUID.randomUUID();

        Decision decision = Decision.builder()
                .id(decisionId)
                .ticketId(UUID.randomUUID())
                .decision("DEFAVORABLE")
                .finalDecision("DEFAVORABLE")
                .motif("Kilométrage excessif")
                .overrideMotif("Véhicule hors garantie — 250 000 km")
                .score(BigDecimal.valueOf(25.0))
                .source("RULES")
                .build();

        when(letterRepository.findByDecisionId(decisionId)).thenReturn(Collections.emptyList());
        when(letterRepository.save(any(Letter.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] pdfBytes = letterGenerationService.generateLetter(decision);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(100);
    }

    @Test
    @DisplayName("Met à jour un Letter existant au lieu d'en créer un nouveau")
    void generateLetter_existingLetter_updates() throws IOException {
        UUID decisionId = UUID.randomUUID();

        Decision decision = Decision.builder()
                .id(decisionId)
                .ticketId(UUID.randomUUID())
                .decision("FAVORABLE")
                .finalDecision("FAVORABLE")
                .motif("Approuvé")
                .score(BigDecimal.valueOf(90.0))
                .source("RULES")
                .build();

        Letter existingLetter = Letter.builder()
                .id(UUID.randomUUID())
                .decisionId(decisionId)
                .filePath("/old/path.pdf")
                .type("DEFAVORABLE")
                .sentByEmail(false)
                .build();

        when(letterRepository.findByDecisionId(decisionId)).thenReturn(List.of(existingLetter));
        when(letterRepository.save(any(Letter.class))).thenAnswer(inv -> inv.getArgument(0));

        letterGenerationService.generateLetter(decision);

        ArgumentCaptor<Letter> captor = ArgumentCaptor.forClass(Letter.class);
        verify(letterRepository).save(captor.capture());
        Letter saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(existingLetter.getId()); // Same letter updated
        assertThat(saved.getType()).isEqualTo("FAVORABLE"); // Type updated
    }
}
