package com.pfe.callmgmt.ticket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EmailListenerServiceTest {

    @Mock
    private TicketService ticketService;

    @InjectMocks
    private EmailListenerService emailListenerService;

    @BeforeEach
    void setUp() throws Exception {
        // Set enabled=false by default to avoid IMAP connections in tests
        setField(emailListenerService, "enabled", false);
        setField(emailListenerService, "username", "");
        setField(emailListenerService, "password", "");
        setField(emailListenerService, "filterKeywords", List.of());
    }

    @Test
    @DisplayName("pollEmails ne fait rien si le listener est désactivé")
    void pollEmails_disabled_doesNothing() {
        emailListenerService.pollEmails();
        // No exception, no interaction with ticketService
    }

    @Test
    @DisplayName("pollEmails ne fait rien si username est vide")
    void pollEmails_emptyUsername_doesNothing() throws Exception {
        setField(emailListenerService, "enabled", true);
        setField(emailListenerService, "username", "");
        setField(emailListenerService, "password", "somepass");

        emailListenerService.pollEmails();
        // No exception — early return
    }

    @Test
    @DisplayName("matchesKeywords retourne true si aucun filtre configuré")
    void matchesKeywords_noFilter_returnsTrue() throws Exception {
        setField(emailListenerService, "filterKeywords", List.of());

        boolean result = invokeMatchesKeywords("Subject test", "Body test");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("matchesKeywords retourne true si un mot-clé matche dans le sujet")
    void matchesKeywords_matchInSubject_returnsTrue() throws Exception {
        setField(emailListenerService, "filterKeywords", List.of("sinistre", "commande"));

        boolean result = invokeMatchesKeywords("Déclaration sinistre auto", "");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("matchesKeywords retourne true si un mot-clé matche dans le corps")
    void matchesKeywords_matchInBody_returnsTrue() throws Exception {
        setField(emailListenerService, "filterKeywords", List.of("assurance"));

        boolean result = invokeMatchesKeywords("Bonjour", "Je souhaite déclarer un sinistre assurance");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("matchesKeywords retourne false si aucun mot-clé ne matche")
    void matchesKeywords_noMatch_returnsFalse() throws Exception {
        setField(emailListenerService, "filterKeywords", List.of("sinistre", "assurance"));

        boolean result = invokeMatchesKeywords("Newsletter", "Promotions du mois");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("detectPriority retourne 1 pour les emails urgents")
    void detectPriority_urgent_returns1() throws Exception {
        short priority = invokeDetectPriority("URGENT: Panne moteur", "");
        assertThat(priority).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("detectPriority retourne 2 pour les emails importants")
    void detectPriority_important_returns2() throws Exception {
        short priority = invokeDetectPriority("Demande importante", "");
        assertThat(priority).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("detectPriority retourne 3 par défaut")
    void detectPriority_normal_returns3() throws Exception {
        short priority = invokeDetectPriority("Demande de renseignement", "Bonjour");
        assertThat(priority).isEqualTo((short) 3);
    }

    // ── Reflection helpers for private methods ──

    private boolean invokeMatchesKeywords(String subject, String body) throws Exception {
        Method method = EmailListenerService.class.getDeclaredMethod("matchesKeywords", String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(emailListenerService, subject, body);
    }

    private short invokeDetectPriority(String subject, String body) throws Exception {
        Method method = EmailListenerService.class.getDeclaredMethod("detectPriority", String.class, String.class);
        method.setAccessible(true);
        return (short) method.invoke(emailListenerService, subject, body);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
