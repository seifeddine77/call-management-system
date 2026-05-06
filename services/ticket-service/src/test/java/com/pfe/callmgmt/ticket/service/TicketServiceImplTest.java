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
    @Nested
    @DisplayName("getTickets")
    class GetTicketsTests {

        @Test
        @DisplayName("OPERATOR ne voit que les tickets de son agence")
        void getTickets_operatorSeesOnlyOwnAgence() {
            TicketFilter filter = new TicketFilter();
            filter.setAgenceId(AGENCE_ID_OTHER); // Tries to see another agence

            Page<Ticket> page = new PageImpl<>(List.of(createSampleTicket(Ticket.Statut.NOUVEAU)));
            when(ticketRepository.findByFilters(any(), any(), eq(AGENCE_ID), any(), any(), any(), any(),
                    any(Pageable.class)))
                    .thenReturn(page);

            ticketService.getTickets(filter, ROLE_OPERATOR, OPERATOR_ID, AGENCE_ID);

            // Verify that agenceId was forced to OPERATOR's agence (AGENCE_ID, not
            // AGENCE_ID_OTHER)
            ArgumentCaptor<UUID> agenceCaptor = ArgumentCaptor.forClass(UUID.class);
            verify(ticketRepository).findByFilters(any(), any(), agenceCaptor.capture(), any(), any(), any(), any(),
                    any(Pageable.class));
            assertThat(agenceCaptor.getValue()).isEqualTo(AGENCE_ID);
        }

        @Test
        @DisplayName("ADMIN voit tous les tickets sans restriction d'agence")
        void getTickets_adminSeesAll() {
            TicketFilter filter = new TicketFilter();

            Page<Ticket> page = new PageImpl<>(List.of(createSampleTicket(Ticket.Statut.NOUVEAU)));
            when(ticketRepository.findByFilters(any(), any(), isNull(), any(), any(), any(), any(),
                    any(Pageable.class)))
                    .thenReturn(page);

            ticketService.getTickets(filter, "ADMIN", SUPERVISOR_ID, AGENCE_ID);

            ArgumentCaptor<UUID> agenceCaptor = ArgumentCaptor.forClass(UUID.class);
            verify(ticketRepository).findByFilters(any(), any(), agenceCaptor.capture(), any(), any(), any(), any(),
                    any(Pageable.class));
            assertThat(agenceCaptor.getValue()).isNull(); // No agence restriction
        }
    }

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
}
