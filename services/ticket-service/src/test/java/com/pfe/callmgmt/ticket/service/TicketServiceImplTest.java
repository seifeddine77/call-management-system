package com.pfe.callmgmt.ticket.service;

import com.pfe.callmgmt.ticket.dto.*;
import com.pfe.callmgmt.ticket.entity.Ticket;
import com.pfe.callmgmt.ticket.entity.TicketAction;
import com.pfe.callmgmt.ticket.event.TicketEventPublisher;
import com.pfe.callmgmt.ticket.exception.AccessDeniedException;
import com.pfe.callmgmt.ticket.exception.InvalidTransitionException;
import com.pfe.callmgmt.ticket.exception.NotFoundException;
import com.pfe.callmgmt.ticket.mapper.TicketMapper;
import com.pfe.callmgmt.ticket.repository.TicketActionRepository;
import com.pfe.callmgmt.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketActionRepository actionRepository;

    @Mock
    private TicketEventPublisher eventPublisher;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private TicketMapper ticketMapper;
    private TicketServiceImpl ticketService;

    private static final UUID AGENCE_ID = UUID.randomUUID();
    private static final UUID AGENCE_ID_OTHER = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID SUPERVISOR_ID = UUID.randomUUID();
    private static final UUID TICKET_ID = UUID.randomUUID();

    private static final String CLIENT_NOM = "Client Test";
    private static final String CLIENT_TEL = "98111222";
    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String ROLE_OPERATOR = "OPERATOR";
    private static final String OPERATEUR_NAME = "Operateur";
    private static final String STATUT_EN_COURS = "EN_COURS";

    @BeforeEach
    void setUp() {
        ticketMapper = new TicketMapper();
        ticketService = new TicketServiceImpl(
                ticketRepository, actionRepository, ticketMapper,
                eventPublisher, messagingTemplate);
    }

    private Ticket createSampleTicket(Ticket.Statut statut) {
        return Ticket.builder()
                .id(TICKET_ID)
                .numero("T-20260301-0042")
                .clientNom(CLIENT_NOM)
                .clientTel(CLIENT_TEL)
                .clientEmail("client@test.com")
                .canal(Ticket.Canal.CALL)
                .statut(statut)
                .priorite((short) 3)
                .agenceId(AGENCE_ID)
                .operateurId(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ========================================
    // CREATE TICKET
    // ========================================
    @Nested
    @DisplayName("createTicket")
    class CreateTicketTests {

        @Test
        @DisplayName("Création réussie : sauvegarde, log action, publie événement, envoie WebSocket")
        void createTicket_success() {
            CreateTicketRequest request = new CreateTicketRequest();
            request.setClientNom(CLIENT_NOM);
            request.setClientTel(CLIENT_TEL);
            request.setClientEmail("client@test.com");
            request.setCanal("CALL");
            request.setAgenceId(AGENCE_ID);
            request.setDescription("Problème moteur");
            request.setPriorite((short) 3);

            Ticket savedTicket = createSampleTicket(Ticket.Statut.NOUVEAU);
            when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);
            when(actionRepository.save(any(TicketAction.class))).thenReturn(new TicketAction());

            TicketResponse result = ticketService.createTicket(request, OPERATOR_ID, "operator@test.com");

            assertThat(result).isNotNull();
            assertThat(result.getClientNom()).isEqualTo(CLIENT_NOM);
            assertThat(result.getStatut()).isEqualTo("NOUVEAU");
            assertThat(result.getCanal()).isEqualTo("CALL");

            verify(ticketRepository).save(any(Ticket.class));
            verify(actionRepository).save(any(TicketAction.class));
            verify(eventPublisher).publishTicketCreated(any());
            verify(messagingTemplate).convertAndSend(eq("/topic/tickets.new"), any(TicketResponse.class));
        }

        @Test
        @DisplayName("Le numéro de ticket généré commence par T-")
        void createTicket_generatesUniqueNumero() {
            CreateTicketRequest request = new CreateTicketRequest();
            request.setClientNom(CLIENT_NOM);
            request.setClientTel(CLIENT_TEL);
            request.setCanal("EMAIL");
            request.setAgenceId(AGENCE_ID);

            Ticket savedTicket = createSampleTicket(Ticket.Statut.NOUVEAU);
            when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);
            when(actionRepository.save(any(TicketAction.class))).thenReturn(new TicketAction());

            TicketResponse result = ticketService.createTicket(request, OPERATOR_ID, "op");

            assertThat(result.getNumero()).startsWith("T-");
        }
    }

    // ========================================
    // ASSIGN TICKET
    // ========================================
    @Nested
    @DisplayName("assignTicket")
    class AssignTicketTests {

        @Test
        @DisplayName("Opérateur prend un ticket non assigné avec succès")
        void assignTicket_operatorTakesUnassigned_success() {
            Ticket ticket = createSampleTicket(Ticket.Statut.NOUVEAU);
            ticket.setOperateurId(null);
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
            when(actionRepository.save(any(TicketAction.class))).thenReturn(new TicketAction());

            TicketResponse result = ticketService.assignTicket(TICKET_ID, OPERATOR_ID, OPERATOR_ID, ROLE_OPERATOR, AGENCE_ID);

            assertThat(result.getOperateurId()).isEqualTo(OPERATOR_ID);
            assertThat(result.getStatut()).isEqualTo(STATUT_EN_COURS); // Auto-transition
        }

        @Test
        @DisplayName("Opérateur ne peut pas assigner un ticket à un autre opérateur")
        void assignTicket_operatorTriesToAssignToOther() {
            Ticket ticket = createSampleTicket(Ticket.Statut.NOUVEAU);
            ticket.setOperateurId(null);
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

            UUID otherOperator = UUID.randomUUID();
            assertThatThrownBy(() -> ticketService.assignTicket(TICKET_ID, otherOperator, OPERATOR_ID, ROLE_OPERATOR, AGENCE_ID))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ========================================
    // UPDATE STATUT
    // ========================================
    @Nested
    @DisplayName("updateStatut")
    class UpdateStatutTests {

        @Test
        @DisplayName("Transition valide : NOUVEAU → EN_COURS")
        void updateStatut_validTransition_NOUVEAU_to_EN_COURS() {
            Ticket ticket = createSampleTicket(Ticket.Statut.NOUVEAU);
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
            when(actionRepository.save(any(TicketAction.class))).thenReturn(new TicketAction());

            UpdateStatutRequest request = new UpdateStatutRequest(STATUT_EN_COURS, null);
            TicketResponse result = ticketService.updateStatut(TICKET_ID, request, OPERATOR_ID, OPERATEUR_NAME);

            assertThat(result.getStatut()).isEqualTo(STATUT_EN_COURS);
            verify(actionRepository).save(any(TicketAction.class));
        }

        @Test
        @DisplayName("Transition interdite : FERME → NOUVEAU lance InvalidTransitionException")
        void updateStatut_invalidTransition() {
            Ticket ticket = createSampleTicket(Ticket.Statut.FERME);
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

            UpdateStatutRequest request = new UpdateStatutRequest("NOUVEAU", null);

            assertThatThrownBy(() -> ticketService.updateStatut(TICKET_ID, request, OPERATOR_ID, OPERATEUR_NAME))
                    .isInstanceOf(InvalidTransitionException.class)
                    .hasMessageContaining("Transition interdite");
        }

        @Test
        @DisplayName("Résolution publie TicketResolvedEvent sur RabbitMQ")
        void updateStatut_toRESOLU_publishesEvent() {
            Ticket ticket = createSampleTicket(Ticket.Statut.EN_COURS);
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
            when(actionRepository.save(any(TicketAction.class))).thenReturn(new TicketAction());

            UpdateStatutRequest request = new UpdateStatutRequest("RESOLU", "Problème résolu");
            ticketService.updateStatut(TICKET_ID, request, OPERATOR_ID, OPERATEUR_NAME);

            verify(eventPublisher).publishTicketResolved(any());
        }
    }

    // ========================================
    // GET TICKETS
    // ========================================


    // ========================================
    // GET TICKET BY ID
    // ========================================
    @Nested
    @DisplayName("getTicketById")
    class GetTicketByIdTests {

        @Test
        @DisplayName("Ticket non trouvé lance NotFoundException")
        void getTicketById_notFound() {
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.getTicketById(TICKET_ID, "ADMIN", SUPERVISOR_ID, AGENCE_ID))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("OPERATOR ne peut pas voir un ticket d'une autre agence")
        void getTicketById_operatorWrongAgence() {
            Ticket ticket = createSampleTicket(Ticket.Statut.NOUVEAU);
            ticket.setAgenceId(AGENCE_ID_OTHER);
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

            assertThatThrownBy(() -> ticketService.getTicketById(TICKET_ID, ROLE_OPERATOR, OPERATOR_ID, AGENCE_ID))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ========================================
    // GET TICKET STATS
    // ========================================
    @Nested
    @DisplayName("getTicketStats")
    class GetTicketStatsTests {

        @Test
        @DisplayName("Retourne les compteurs par statut pour une agence")
        void getTicketStats_returnsCounts() {
            when(ticketRepository.countByStatutAndAgenceId(Ticket.Statut.NOUVEAU, AGENCE_ID)).thenReturn(5L);
            when(ticketRepository.countByStatutAndAgenceId(Ticket.Statut.EN_COURS, AGENCE_ID)).thenReturn(3L);
            when(ticketRepository.countByStatutAndAgenceId(Ticket.Statut.RESOLU, AGENCE_ID)).thenReturn(10L);
            when(ticketRepository.countByStatutAndAgenceId(Ticket.Statut.FERME, AGENCE_ID)).thenReturn(2L);

            TicketStats stats = ticketService.getTicketStats(AGENCE_ID);

            assertThat(stats.getNouveau()).isEqualTo(5);
            assertThat(stats.getEnCours()).isEqualTo(3);
            assertThat(stats.getResolu()).isEqualTo(10);
            assertThat(stats.getFerme()).isEqualTo(2);
            assertThat(stats.getTotal()).isEqualTo(20);
        }
    }

    // ========================================
    // ANONYMIZE TICKET (RGPD)
    // ========================================
    @Nested
    @DisplayName("anonymizeTicket (RGPD)")
    class AnonymizeTicketTests {

        @Test
        @DisplayName("Anonymise les données personnelles du client")
        void anonymizeTicket_replacesPersonalData() {
            Ticket ticket = createSampleTicket(Ticket.Statut.RESOLU);
            ticket.setTranscription("Le client a décrit une panne...");
            ticket.setAudioPath("/audio/test.wav");
            ticket.setTranscriptionKeywords("[\"panne\",\"moteur\"]");
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
            when(actionRepository.save(any(TicketAction.class))).thenReturn(new TicketAction());

            TicketResponse result = ticketService.anonymizeTicket(TICKET_ID, SUPERVISOR_ID, ADMIN_EMAIL);

            assertThat(result.getClientNom()).isEqualTo("***ANONYMISÉ***");
            assertThat(result.getClientTel()).isEqualTo("***");
            assertThat(result.getClientEmail()).isEqualTo("***@anonymise.rgpd");
        }

        @Test
        @DisplayName("Supprime audio, transcription et photos")
        void anonymizeTicket_clearsAudioTranscriptionAndPhotos() {
            Ticket ticket = createSampleTicket(Ticket.Statut.RESOLU);
            ticket.setTranscription("Transcription existante");
            ticket.setAudioPath("/audio/file.wav");
            ticket.setTranscriptionKeywords("[\"mot\"]");
            ticket.setPhotosPaths("[\"photo1.jpg\",\"photo2.jpg\"]");
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
            when(actionRepository.save(any(TicketAction.class))).thenReturn(new TicketAction());

            TicketResponse result = ticketService.anonymizeTicket(TICKET_ID, SUPERVISOR_ID, ADMIN_EMAIL);

            assertThat(result.getAudioPath()).isNull();
            assertThat(result.getTranscription()).isNull();
            assertThat(result.getTranscriptionKeywords()).isNull();
            assertThat(result.getPhotosPaths()).isNull();
        }

        @Test
        @DisplayName("Log l'action ANONYMISE dans l'audit trail")
        void anonymizeTicket_logsAction() {
            Ticket ticket = createSampleTicket(Ticket.Statut.RESOLU);
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
            when(actionRepository.save(any(TicketAction.class))).thenReturn(new TicketAction());

            ticketService.anonymizeTicket(TICKET_ID, SUPERVISOR_ID, ADMIN_EMAIL);

            ArgumentCaptor<TicketAction> actionCaptor = ArgumentCaptor.forClass(TicketAction.class);
            verify(actionRepository).save(actionCaptor.capture());
            assertThat(actionCaptor.getValue().getAction()).isEqualTo("ANONYMISE");
        }

        @Test
        @DisplayName("Ticket non trouvé lance NotFoundException")
        void anonymizeTicket_notFound() {
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.anonymizeTicket(TICKET_ID, SUPERVISOR_ID, ADMIN_EMAIL))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    // ========================================
    // UPDATE PHOTOS PATHS (CDC Module 5)
    // ========================================
    @Nested
    @DisplayName("updatePhotosPaths")
    class UpdatePhotosPathsTests {

        @Test
        @DisplayName("Sauvegarde les chemins de photos en JSONB")
        void updatePhotosPaths_savesPhotoPaths() {
            Ticket ticket = createSampleTicket(Ticket.Statut.EN_COURS);
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
            when(actionRepository.save(any(TicketAction.class))).thenReturn(new TicketAction());

            String photosJson = "[\"photo1.jpg\",\"photo2.png\"]";
            ticketService.updatePhotosPaths(TICKET_ID, photosJson);

            ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(ticketCaptor.capture());
            assertThat(ticketCaptor.getValue().getPhotosPaths()).isEqualTo(photosJson);
        }

        @Test
        @DisplayName("Log l'action PHOTOS_AJOUTEES dans l'audit trail")
        void updatePhotosPaths_logsAction() {
            Ticket ticket = createSampleTicket(Ticket.Statut.EN_COURS);
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
            when(actionRepository.save(any(TicketAction.class))).thenReturn(new TicketAction());

            ticketService.updatePhotosPaths(TICKET_ID, "[\"photo.jpg\"]");

            ArgumentCaptor<TicketAction> actionCaptor = ArgumentCaptor.forClass(TicketAction.class);
            verify(actionRepository).save(actionCaptor.capture());
            assertThat(actionCaptor.getValue().getAction()).isEqualTo("PHOTOS_AJOUTEES");
        }

        @Test
        @DisplayName("Ticket non trouvé lance NotFoundException")
        void updatePhotosPaths_notFound() {
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.updatePhotosPaths(TICKET_ID, "[\"photo.jpg\"]"))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    // ========================================
    // RÉCONCILIATION — getDailyTicketCounts
    // ========================================
    @Nested
    @DisplayName("getDailyTicketCounts")
    class GetDailyTicketCounts {

        @Test
        @DisplayName("Agrège les lignes [jour, canal, count] en un DTO par jour")
        void aggregatesRowsPerDay() {
            LocalDate d1 = LocalDate.of(2026, java.time.Month.JUNE, 1);
            LocalDate d2 = LocalDate.of(2026, java.time.Month.JUNE, 2);
            // d1: 3 CALL + 2 EMAIL ; d2: 1 WEB
            List<Object[]> rows = List.of(
                    new Object[]{java.sql.Date.valueOf(d1), "CALL", 3L},
                    new Object[]{java.sql.Date.valueOf(d1), "EMAIL", 2L},
                    new Object[]{java.sql.Date.valueOf(d2), "WEB", 1L});
            when(ticketRepository.countCreatedPerDayByCanal(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(rows);

            List<DailyTicketCount> result = ticketService.getDailyTicketCounts(d1, d2);

            assertThat(result).hasSize(2);
            DailyTicketCount first = result.get(0); // trié par date (TreeMap)
            assertThat(first.getDate()).isEqualTo(d1);
            assertThat(first.getTotalTickets()).isEqualTo(5);
            assertThat(first.getTicketsCall()).isEqualTo(3);
            assertThat(first.getTicketsEmail()).isEqualTo(2);
            assertThat(first.getTicketsWeb()).isZero();
            assertThat(result.get(1).getTicketsWeb()).isEqualTo(1);
        }

        @Test
        @DisplayName("Canal inconnu compté dans le total uniquement")
        void unknownCanal_countedInTotalOnly() {
            LocalDate d1 = LocalDate.of(2026, java.time.Month.JUNE, 1);
            List<Object[]> rows = List.of(
                    new Object[]{java.sql.Date.valueOf(d1), "CALL", 2L},
                    new Object[]{java.sql.Date.valueOf(d1), "AUTRE", 4L});
            when(ticketRepository.countCreatedPerDayByCanal(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(rows);

            List<DailyTicketCount> result = ticketService.getDailyTicketCounts(d1, d1);

            assertThat(result).singleElement().satisfies(dc -> {
                assertThat(dc.getTotalTickets()).isEqualTo(6);
                assertThat(dc.getTicketsCall()).isEqualTo(2);
                assertThat(dc.getTicketsEmail()).isZero();
                assertThat(dc.getTicketsWeb()).isZero();
            });
        }

        @Test
        @DisplayName("Aucun ticket — retourne une liste vide")
        void noTickets_returnsEmpty() {
            when(ticketRepository.countCreatedPerDayByCanal(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            assertThat(ticketService.getDailyTicketCounts(
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))).isEmpty();
        }
    }

    @Nested
    @DisplayName("detectPriority — détection unifiée de priorité (multi-canal)")
    class DetectPriority {

        @Test
        @DisplayName("Urgence explicite ou sinistre grave → P1")
        void severe_returnsP1() {
            assertThat(TicketServiceImpl.detectPriority("URGENT : panne sur autoroute")).isEqualTo((short) 1);
            assertThat(TicketServiceImpl.detectPriority("Situation critique, intervention requise")).isEqualTo((short) 1);
            assertThat(TicketServiceImpl.detectPriority("J'ai eu un accident ce matin")).isEqualTo((short) 1);
            assertThat(TicketServiceImpl.detectPriority("Début d'incendie moteur")).isEqualTo((short) 1);
            assertThat(TicketServiceImpl.detectPriority("Véhicule immobilisé, conducteur blessé")).isEqualTo((short) 1);
        }

        @Test
        @DisplayName("Panne / besoin rapide → P2")
        void rapid_returnsP2() {
            assertThat(TicketServiceImpl.detectPriority("Panne d'alternateur")).isEqualTo((short) 2);
            assertThat(TicketServiceImpl.detectPriority("Message important, merci de traiter rapidement")).isEqualTo((short) 2);
            assertThat(TicketServiceImpl.detectPriority("Besoin d'un remorquage")).isEqualTo((short) 2);
        }

        @Test
        @DisplayName("Aucun mot-clé / texte vide → P3 (normale)")
        void normal_returnsP3() {
            assertThat(TicketServiceImpl.detectPriority("Bonjour, je souhaite un devis")).isEqualTo((short) 3);
            assertThat(TicketServiceImpl.detectPriority("")).isEqualTo((short) 3);
            assertThat(TicketServiceImpl.detectPriority(null)).isEqualTo((short) 3);
        }

        @Test
        @DisplayName("L'urgence explicite prime sur la panne (P1 > P2)")
        void urgentOverridesPanne() {
            assertThat(TicketServiceImpl.detectPriority("Panne URGENTE moteur")).isEqualTo((short) 1);
        }
    }

    @Nested
    @DisplayName("updateAudioTranscription — ré-évaluation de priorité (CALL)")
    class UpdateAudioTranscriptionPriority {

        @Test
        @DisplayName("Transcription urgente fait monter un ticket P3 → P1")
        void transcriptionUpgradesPriority() {
            Ticket ticket = createSampleTicket(Ticket.Statut.NOUVEAU); // priorité 3
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
            when(actionRepository.save(any(TicketAction.class))).thenReturn(new TicketAction());

            ticketService.updateAudioTranscription(TICKET_ID, "/audio/x.enc",
                    "J'ai un problème de boîte de vitesse, c'est urgent", "[\"urgent\"]");

            assertThat(ticket.getPriorite()).isEqualTo((short) 1);
        }

        @Test
        @DisplayName("Transcription sans urgence ne dégrade pas une priorité déjà élevée")
        void transcriptionDoesNotDowngrade() {
            Ticket ticket = createSampleTicket(Ticket.Statut.NOUVEAU);
            ticket.setPriorite((short) 1); // déjà urgente (saisie explicite)
            when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
            when(actionRepository.save(any(TicketAction.class))).thenReturn(new TicketAction());

            ticketService.updateAudioTranscription(TICKET_ID, "/audio/x.enc",
                    "Bonjour, je voudrais un devis pour un rétroviseur", "[]");

            assertThat(ticket.getPriorite()).isEqualTo((short) 1);
        }
    }
}
