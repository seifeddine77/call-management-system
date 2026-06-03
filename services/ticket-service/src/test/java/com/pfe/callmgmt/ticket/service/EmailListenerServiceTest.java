package com.pfe.callmgmt.ticket.service;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import com.pfe.callmgmt.ticket.dto.TicketResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailListenerService — tests unitaires")
class EmailListenerServiceTest {

    @Mock
    private TicketService ticketService;

    @InjectMocks
    private EmailListenerService emailListenerService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailListenerService, "enabled", false);
        ReflectionTestUtils.setField(emailListenerService, "imapHost", "imap.gmail.com");
        ReflectionTestUtils.setField(emailListenerService, "imapPort", 993);
        ReflectionTestUtils.setField(emailListenerService, "username", "");
        ReflectionTestUtils.setField(emailListenerService, "password", "");
        ReflectionTestUtils.setField(emailListenerService, "folderName", "INBOX");
        ReflectionTestUtils.setField(emailListenerService, "defaultAgenceId", "");
        ReflectionTestUtils.setField(emailListenerService, "filterKeywords", List.of());
        ReflectionTestUtils.setField(emailListenerService, "skipOldEmails", true);
        ReflectionTestUtils.setField(emailListenerService, "serviceStartTime", null);
    }

    // ─────────────────────────────────────────────────────
    // pollEmails — disabled / no credentials
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("pollEmails — listener désactivé → aucune action")
    void pollEmails_disabled_noAction() {
        // enabled=false → should return immediately without connecting
        emailListenerService.pollEmails();

        verifyNoInteractions(ticketService);
    }

    @Test
    @DisplayName("pollEmails — enabled=true mais username vide → aucune action")
    void pollEmails_enabledButBlankUsername_noAction() {
        ReflectionTestUtils.setField(emailListenerService, "enabled", true);
        ReflectionTestUtils.setField(emailListenerService, "username", "");
        ReflectionTestUtils.setField(emailListenerService, "password", "secret");

        emailListenerService.pollEmails();

        verifyNoInteractions(ticketService);
    }

    @Test
    @DisplayName("pollEmails — enabled=true mais password vide → aucune action")
    void pollEmails_enabledButBlankPassword_noAction() {
        ReflectionTestUtils.setField(emailListenerService, "enabled", true);
        ReflectionTestUtils.setField(emailListenerService, "username", "user@test.com");
        ReflectionTestUtils.setField(emailListenerService, "password", "");

        emailListenerService.pollEmails();

        verifyNoInteractions(ticketService);
    }

    // ─────────────────────────────────────────────────────
    // matchesKeywords — via reflection (private method)
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("matchesKeywords — aucun filtre configuré → retourne true (tous les emails passent)")
    void matchesKeywords_noFilter_returnsTrue() throws Exception {
        ReflectionTestUtils.setField(emailListenerService, "filterKeywords", List.of());
        Method m = EmailListenerService.class.getDeclaredMethod("matchesKeywords", String.class, String.class);
        m.setAccessible(true);

        boolean result = (boolean) m.invoke(emailListenerService, "Sinistre AUTO", "Mon véhicule est en panne");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("matchesKeywords — filtre avec mot-clé vide → retourne true")
    void matchesKeywords_blankKeywordOnly_returnsTrue() throws Exception {
        ReflectionTestUtils.setField(emailListenerService, "filterKeywords", List.of(""));
        Method m = EmailListenerService.class.getDeclaredMethod("matchesKeywords", String.class, String.class);
        m.setAccessible(true);

        boolean result = (boolean) m.invoke(emailListenerService, "Test", "Body");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("matchesKeywords — sujet contient mot-clé → retourne true")
    void matchesKeywords_subjectMatch_returnsTrue() throws Exception {
        ReflectionTestUtils.setField(emailListenerService, "filterKeywords", List.of("sinistre", "panne"));
        Method m = EmailListenerService.class.getDeclaredMethod("matchesKeywords", String.class, String.class);
        m.setAccessible(true);

        boolean result = (boolean) m.invoke(emailListenerService, "[SINISTRE] Déclaration urgente", "Bonjour");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("matchesKeywords — corps contient mot-clé → retourne true")
    void matchesKeywords_bodyMatch_returnsTrue() throws Exception {
        ReflectionTestUtils.setField(emailListenerService, "filterKeywords", List.of("assurance"));
        Method m = EmailListenerService.class.getDeclaredMethod("matchesKeywords", String.class, String.class);
        m.setAccessible(true);

        boolean result = (boolean) m.invoke(emailListenerService, "Demande", "Je contacte pour mon assurance");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("matchesKeywords — aucun mot-clé trouvé → retourne false")
    void matchesKeywords_noMatch_returnsFalse() throws Exception {
        ReflectionTestUtils.setField(emailListenerService, "filterKeywords", List.of("sinistre", "panne"));
        Method m = EmailListenerService.class.getDeclaredMethod("matchesKeywords", String.class, String.class);
        m.setAccessible(true);

        boolean result = (boolean) m.invoke(emailListenerService, "Hello", "Comment allez-vous ?");

        assertThat(result).isFalse();
    }

    // ─────────────────────────────────────────────────────
    // detectPriority — via reflection
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("detectPriority — sujet contient 'urgent' → priorité 1")
    void detectPriority_urgent() throws Exception {
        Method m = EmailListenerService.class.getDeclaredMethod("detectPriority", String.class, String.class);
        m.setAccessible(true);

        short priority = (short) m.invoke(emailListenerService, "URGENT: Panne voiture", "");

        assertThat(priority).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("detectPriority — corps contient 'critique' → priorité 1")
    void detectPriority_critique() throws Exception {
        Method m = EmailListenerService.class.getDeclaredMethod("detectPriority", String.class, String.class);
        m.setAccessible(true);

        short priority = (short) m.invoke(emailListenerService, "Demande", "Situation critique nécessitant intervention");

        assertThat(priority).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("detectPriority — sujet contient 'important' → priorité 2")
    void detectPriority_important() throws Exception {
        Method m = EmailListenerService.class.getDeclaredMethod("detectPriority", String.class, String.class);
        m.setAccessible(true);

        short priority = (short) m.invoke(emailListenerService, "Message important", "Merci de traiter rapidement");

        assertThat(priority).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("detectPriority — rien de spécial → priorité 3 (normale)")
    void detectPriority_normal() throws Exception {
        Method m = EmailListenerService.class.getDeclaredMethod("detectPriority", String.class, String.class);
        m.setAccessible(true);

        short priority = (short) m.invoke(emailListenerService, "Bonjour", "Je souhaite un devis");

        assertThat(priority).isEqualTo((short) 3);
    }

    // ─────────────────────────────────────────────────────
    // extractEmailAddress — via reflection
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("extractEmailAddress — null → retourne 'unknown@email.com'")
    void extractEmailAddress_null_returnsUnknown() throws Exception {
        Method m = EmailListenerService.class.getDeclaredMethod("extractEmailAddress", Address[].class);
        m.setAccessible(true);

        String result = (String) m.invoke(emailListenerService, (Object) null);

        assertThat(result).isEqualTo("unknown@email.com");
    }

    @Test
    @DisplayName("extractEmailAddress — empty array → retourne 'unknown@email.com'")
    void extractEmailAddress_emptyArray_returnsUnknown() throws Exception {
        Method m = EmailListenerService.class.getDeclaredMethod("extractEmailAddress", Address[].class);
        m.setAccessible(true);

        String result = (String) m.invoke(emailListenerService, new Object[]{new Address[0]});

        assertThat(result).isEqualTo("unknown@email.com");
    }

    @Test
    @DisplayName("extractEmailAddress — InternetAddress → retourne l'adresse email")
    void extractEmailAddress_internetAddress_returnsEmail() throws Exception {
        Method m = EmailListenerService.class.getDeclaredMethod("extractEmailAddress", Address[].class);
        m.setAccessible(true);

        Address[] addresses = {new InternetAddress("client@example.com", "Client Test")};
        String result = (String) m.invoke(emailListenerService, new Object[]{addresses});

        assertThat(result).isEqualTo("client@example.com");
    }

    // ─────────────────────────────────────────────────────
    // extractSenderName — via reflection
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("extractSenderName — null → retourne null")
    void extractSenderName_null_returnsNull() throws Exception {
        Method m = EmailListenerService.class.getDeclaredMethod("extractSenderName", Address[].class);
        m.setAccessible(true);

        String result = (String) m.invoke(emailListenerService, (Object) null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("extractSenderName — InternetAddress avec personal → retourne le nom")
    void extractSenderName_withPersonal_returnsName() throws Exception {
        Method m = EmailListenerService.class.getDeclaredMethod("extractSenderName", Address[].class);
        m.setAccessible(true);

        Address[] addresses = {new InternetAddress("client@example.com", "Jean Dupont")};
        String result = (String) m.invoke(emailListenerService, new Object[]{addresses});

        assertThat(result).isEqualTo("Jean Dupont");
    }

    // ─────────────────────────────────────────────────────
    // processSingleEmail — via reflection (old email + system notification)
    // ─────────────────────────────────────────────────────

    @Test
    @DisplayName("processSingleEmail — email ancien (avant serviceStartTime) → ignoré (status=0)")
    void processSingleEmail_oldEmail_skipped() throws Exception {
        Date pastDate = new Date(System.currentTimeMillis() - 3600_000); // 1 heure avant
        ReflectionTestUtils.setField(emailListenerService, "serviceStartTime", new Date());

        Message msg = mock(Message.class);
        when(msg.getReceivedDate()).thenReturn(pastDate);

        Method m = EmailListenerService.class.getDeclaredMethod("processSingleEmail", Message.class);
        m.setAccessible(true);

        int status = (int) m.invoke(emailListenerService, msg);

        assertThat(status).isEqualTo(0);
        verify(msg).setFlag(Flags.Flag.SEEN, true);
    }

    @Test
    @DisplayName("processSingleEmail — notification système → ignoré (status=0)")
    void processSingleEmail_systemNotification_skipped() throws Exception {
        ReflectionTestUtils.setField(emailListenerService, "serviceStartTime", new Date(0)); // epoch

        Message msg = mock(Message.class);
        when(msg.getReceivedDate()).thenReturn(new Date()); // now — not old
        when(msg.getSubject()).thenReturn("Votre demande T-20240601-123 a été traitée");

        Method m = EmailListenerService.class.getDeclaredMethod("processSingleEmail", Message.class);
        m.setAccessible(true);

        int status = (int) m.invoke(emailListenerService, msg);

        assertThat(status).isEqualTo(0);
        verify(msg).setFlag(Flags.Flag.SEEN, true);
    }

    @Test
    @DisplayName("processSingleEmail — email valide avec texte brut → crée un ticket et retourne status=1")
    void processSingleEmail_validPlainEmail_createsTicket() throws Exception {
        ReflectionTestUtils.setField(emailListenerService, "skipOldEmails", false);
        ReflectionTestUtils.setField(emailListenerService, "defaultAgenceId", "22222222-2222-2222-2222-222222222222");

        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn("Panne de voiture importante");
        when(msg.getContent()).thenReturn("Bonjour, ma voiture est en panne.");
        
        Address[] fromAddresses = {new InternetAddress("sender@test.com", "Jean Dupont")};
        when(msg.getFrom()).thenReturn(fromAddresses);

        TicketResponse mockResponse = new TicketResponse();
        mockResponse.setNumero("T-123");
        when(ticketService.createTicket(any(), eq(null), eq("SYSTEM-IMAP"))).thenReturn(mockResponse);

        Method m = EmailListenerService.class.getDeclaredMethod("processSingleEmail", Message.class);
        m.setAccessible(true);

        int status = (int) m.invoke(emailListenerService, msg);

        assertThat(status).isEqualTo(1);
        verify(msg).setFlag(Flags.Flag.SEEN, true);
        verify(ticketService).createTicket(argThat(req -> 
            req.getClientNom().equals("Jean Dupont") &&
            req.getClientEmail().equals("sender@test.com") &&
            req.getCanal().equals("EMAIL") &&
            req.getPriorite() == (short) 1 && // urgent/panne = 1
            req.getAgenceId().toString().equals("22222222-2222-2222-2222-222222222222")
        ), eq(null), eq("SYSTEM-IMAP"));
    }

    @Test
    @DisplayName("processSingleEmail — email avec multipart html → extrait html et crée ticket")
    void processSingleEmail_multipartHtml_createsTicket() throws Exception {
        ReflectionTestUtils.setField(emailListenerService, "skipOldEmails", false);

        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn("Demande importante de renseignement");
        
        // Mock MimeMultipart
        MimeMultipart multipart = mock(MimeMultipart.class);
        when(multipart.getCount()).thenReturn(1);
        BodyPart part = mock(BodyPart.class);
        when(part.isMimeType("text/plain")).thenReturn(false);
        when(part.isMimeType("text/html")).thenReturn(true);
        when(part.getContent()).thenReturn("<html><body><p>Besoin d'aide rapidement</p></body></html>");
        when(multipart.getBodyPart(0)).thenReturn(part);

        when(msg.getContent()).thenReturn(multipart);
        
        Address[] fromAddresses = {new InternetAddress("sender2@test.com", "")};
        when(msg.getFrom()).thenReturn(fromAddresses);

        TicketResponse mockResponse = new TicketResponse();
        mockResponse.setNumero("T-124");
        when(ticketService.createTicket(any(), eq(null), eq("SYSTEM-IMAP"))).thenReturn(mockResponse);

        Method m = EmailListenerService.class.getDeclaredMethod("processSingleEmail", Message.class);
        m.setAccessible(true);

        int status = (int) m.invoke(emailListenerService, msg);

        assertThat(status).isEqualTo(1);
        verify(msg).setFlag(Flags.Flag.SEEN, true);
        verify(ticketService).createTicket(argThat(req -> 
            req.getClientNom().equals("sender2@test.com") &&
            req.getPriorite() == (short) 2 // important/rapidement = 2
        ), eq(null), eq("SYSTEM-IMAP"));
    }

    @Test
    @DisplayName("processSingleEmail — email avec contenu non lisible")
    void processSingleEmail_unreadableContent() throws Exception {
        ReflectionTestUtils.setField(emailListenerService, "skipOldEmails", false);

        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn("Test Sujet");
        when(msg.getContent()).thenReturn(new Object()); // Unknown content type
        
        Address[] fromAddresses = {new InternetAddress("sender@test.com", "Nom")};
        when(msg.getFrom()).thenReturn(fromAddresses);

        TicketResponse mockResponse = new TicketResponse();
        mockResponse.setNumero("T-125");
        when(ticketService.createTicket(any(), eq(null), eq("SYSTEM-IMAP"))).thenReturn(mockResponse);

        Method m = EmailListenerService.class.getDeclaredMethod("processSingleEmail", Message.class);
        m.setAccessible(true);

        int status = (int) m.invoke(emailListenerService, msg);

        assertThat(status).isEqualTo(1);
        verify(ticketService).createTicket(argThat(req -> 
            req.getDescription().contains("(Contenu non lisible)")
        ), eq(null), eq("SYSTEM-IMAP"));
    }

    @Test
    @DisplayName("processSingleEmail — ne correspond pas aux mots-clés configurés → ignoré (status=0)")
    void processSingleEmail_noKeywordMatch_ignored() throws Exception {
        ReflectionTestUtils.setField(emailListenerService, "skipOldEmails", false);
        ReflectionTestUtils.setField(emailListenerService, "filterKeywords", List.of("panne", "urgente"));

        Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn("Bonjour");
        when(msg.getContent()).thenReturn("Je vous contacte pour un simple renseignement.");

        Method m = EmailListenerService.class.getDeclaredMethod("processSingleEmail", Message.class);
        m.setAccessible(true);

        int status = (int) m.invoke(emailListenerService, msg);

        assertThat(status).isEqualTo(0);
        verify(msg).setFlag(Flags.Flag.SEEN, true);
        verifyNoInteractions(ticketService);
    }

    @Test
    @DisplayName("processSingleEmail — lève une exception → retourne status=-1")
    void processSingleEmail_throwsException_returnsNegativeOne() throws Exception {
        Message msg = mock(Message.class);
        when(msg.getSubject()).thenThrow(new RuntimeException("Mail error"));

        Method m = EmailListenerService.class.getDeclaredMethod("processSingleEmail", Message.class);
        m.setAccessible(true);

        int status = (int) m.invoke(emailListenerService, msg);

        assertThat(status).isEqualTo(-1);
    }
}
