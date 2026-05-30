package com.pfe.callmgmt.ticket.service;

import com.pfe.callmgmt.ticket.dto.CreateTicketRequest;
import com.pfe.callmgmt.ticket.dto.TicketResponse;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Service d'écoute email IMAP.
 * Interroge automatiquement une boîte mail toutes les 30 secondes
 * et crée des tickets pour chaque nouvel email non lu.
 *
 * Workflow :
 * 1. Connexion IMAP à la boîte configurée
 * 2. Lecture des emails non lus dans INBOX
 * 3. Pour chaque email : extraction From/Subject/Body → création ticket
 * 4. Marquage de l'email comme lu après traitement
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailListenerService {

    private final TicketService ticketService;

    @Value("${email.listener.enabled:false}")
    private boolean enabled;

    @Value("${email.listener.host:imap.gmail.com}")
    private String imapHost;

    @Value("${email.listener.port:993}")
    private int imapPort;

    @Value("${email.listener.username:}")
    private String username;

    @Value("${email.listener.password:}")
    private String password;

    @Value("${email.listener.folder:INBOX}")
    private String folderName;

    @Value("${email.listener.default-agence-id:}")
    private String defaultAgenceId;

    /**
     * Mots-clés de filtrage : seuls les emails dont le sujet contient
     * l'un de ces mots-clés seront transformés en tickets.
     * Si la liste est vide, TOUS les emails sont traités.
     * Exemples : [DEMANDE], [SINISTRE], [COMMANDE], [RECLAMATION], pièce, assurance
     */
    @Value("${email.listener.keywords:}")
    private List<String> filterKeywords;

    /**
     * Si true, ignore les emails reçus avant le démarrage du service.
     * Évite de re-traiter les anciens emails non lus.
     */
    @Value("${email.listener.skip-old-emails:true}")
    private boolean skipOldEmails;

    private Date serviceStartTime;

    /**
     * Tâche planifiée qui surveille la boîte mail.
     * Exécutée toutes les 30 secondes.
     */
    @Scheduled(fixedDelayString = "${email.listener.poll-interval:30000}")
    public void pollEmails() {
        if (!enabled || username.isBlank() || password.isBlank()) {
            return; // Listener désactivé ou non configuré
        }

        // Initialiser le timestamp de démarrage au premier poll
        if (serviceStartTime == null) {
            serviceStartTime = new Date();
            log.info("📧 [IMAP] Listener démarré — filtrage actif : {}",
                    filterKeywords != null && !filterKeywords.isEmpty()
                            ? "mots-clés: " + filterKeywords
                            : "TOUS les emails (aucun filtre)");
        }

        // Connexion IMAP SSL
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", imapHost);
        props.put("mail.imaps.port", String.valueOf(imapPort));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.ssl.trust", "*");
        props.put("mail.imaps.timeout", "10000");
        props.put("mail.imaps.connectiontimeout", "10000");

        Session session = Session.getInstance(props);
        try (Store store = session.getStore("imaps")) {
            store.connect(imapHost, imapPort, username, password);

            try (Folder folder = store.getFolder(folderName)) {
                folder.open(Folder.READ_WRITE);

                // Recherche côté serveur IMAP : emails non lus ET reçus depuis le démarrage du service
                jakarta.mail.search.SearchTerm searchTerm;
                if (skipOldEmails && serviceStartTime != null) {
                    searchTerm = new jakarta.mail.search.AndTerm(
                            new jakarta.mail.search.FlagTerm(new Flags(Flags.Flag.SEEN), false),
                            new jakarta.mail.search.ReceivedDateTerm(jakarta.mail.search.ComparisonTerm.GE,
                                    serviceStartTime));
                } else {
                    searchTerm = new jakarta.mail.search.FlagTerm(new Flags(Flags.Flag.SEEN), false);
                }

                Message[] messages = folder.search(searchTerm);

                if (messages.length == 0) {
                    return; // Pas de nouveaux emails
                }

                log.info("📧 [IMAP] {} nouveaux emails détectés", messages.length);

                int processed = 0;
                int skipped = 0;

                for (Message message : messages) {
                    int status = processSingleEmail(message);
                    if (status == 1) {
                        processed++;
                    } else if (status == 0) {
                        skipped++;
                    }
                }

                if (processed > 0 || skipped > 0) {
                    log.info("📧 [IMAP] Résultat : {} traités, {} ignorés sur {} total",
                            processed, skipped, messages.length);
                }
            }
        } catch (AuthenticationFailedException e) {
            log.error("❌ [IMAP] Authentification échouée — vérifiez username/password: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ [IMAP] Erreur connexion IMAP: {}", e.getMessage());
        }
    }

    /**
     * Traite un email individuel : vérifie le filtre mots-clés, crée le ticket, et marque comme lu.
     *
     * @return 1 si traité avec succès, 0 si ignoré (filtre mots-clés), -1 si erreur
     */
    private int processSingleEmail(Message message) {
        try {
            // Ignorer les emails reçus avant le démarrage du service (en renfort du filtre IMAP)
            if (skipOldEmails && message.getReceivedDate() != null
                    && message.getReceivedDate().before(serviceStartTime)) {
                message.setFlag(Flags.Flag.SEEN, true); // Marquer comme lu
                return 0;
            }

            String subject = message.getSubject() != null ? message.getSubject() : "";
            // Anti-boucle : ignorer les emails de notification envoyés PAR notre système
            if (subject.matches(".*Votre demande T-\\d{8}-\\d+.*")) {
                log.debug("📧 [IMAP] Email ignoré (notification système) : '{}'", subject);
                message.setFlag(Flags.Flag.SEEN, true);
                return 0;
            }

            String body = extractTextContent(message);

            // Filtrage par mots-clés
            if (!matchesKeywords(subject, body)) {
                log.debug("📧 [IMAP] Email ignoré (filtre mots-clés): '{}'", subject);
                message.setFlag(Flags.Flag.SEEN, true);
                return 0;
            }

            processEmail(message);
            message.setFlag(Flags.Flag.SEEN, true);
            return 1;
        } catch (Exception e) {
            log.error("❌ [IMAP] Erreur traitement email: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Traite un email individuel et crée un ticket.
     */
    private void processEmail(Message message) throws MessagingException, IOException {
        String from = extractEmailAddress(message.getFrom());
        String senderName = extractSenderName(message.getFrom());
        String subject = message.getSubject() != null ? message.getSubject() : "(Sans sujet)";
        String body = extractTextContent(message);

        log.info("📧 [IMAP] Traitement email — De: {} ({}), Sujet: '{}'",
                from, senderName, subject);

        // Créer le ticket
        CreateTicketRequest ticketReq = new CreateTicketRequest();
        ticketReq.setClientNom(senderName != null && !senderName.isBlank() ? senderName : from);
        ticketReq.setClientEmail(from);
        ticketReq.setCanal("EMAIL");
        ticketReq.setDescription("[" + subject + "]\n" + body);
        ticketReq.setPriorite(detectPriority(subject, body));

        // Affecter l'agence par défaut (configurée dans application.yml)
        if (defaultAgenceId != null && !defaultAgenceId.isBlank()) {
            ticketReq.setAgenceId(java.util.UUID.fromString(defaultAgenceId));
        }

        TicketResponse ticket = ticketService.createTicket(ticketReq, null, "SYSTEM-IMAP");

        log.info("✅ [IMAP] Ticket {} créé depuis email de {}", ticket.getNumero(), from);
    }

    /**
     * Extrait l'adresse email depuis le header From.
     */
    private String extractEmailAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0)
            return "unknown@email.com";
        if (addresses[0] instanceof InternetAddress) {
            return ((InternetAddress) addresses[0]).getAddress();
        }
        return addresses[0].toString();
    }

    /**
     * Extrait le nom de l'expéditeur depuis le header From.
     */
    private String extractSenderName(Address[] addresses) {
        if (addresses == null || addresses.length == 0)
            return null;
        if (addresses[0] instanceof InternetAddress) {
            return ((InternetAddress) addresses[0]).getPersonal();
        }
        return null;
    }

    /**
     * Extrait le contenu textuel d'un email (gère multipart/mime).
     */
    private String extractTextContent(Message message) throws MessagingException, IOException {
        Object content = message.getContent();

        if (content instanceof String) {
            return (String) content;
        }

        if (content instanceof MimeMultipart multipart) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (part.isMimeType("text/plain")) {
                    sb.append(part.getContent().toString());
                } else if (part.isMimeType("text/html") && sb.isEmpty()) {
                    // Fallback sur HTML si pas de texte brut
                    String html = part.getContent().toString();
                    sb.append(html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim());
                }
            }
            return sb.toString();
        }

        return "(Contenu non lisible)";
    }

    /**
     * Détecte automatiquement la priorité en fonction de mots-clés.
     */
    private short detectPriority(String subject, String body) {
        String combined = (subject + " " + body).toLowerCase();
        if (combined.contains("urgent") || combined.contains("critique") || combined.contains("panne")) {
            return 1; // Urgente
        }
        if (combined.contains("important") || combined.contains("rapidement")) {
            return 2; // Haute
        }
        return 3; // Normale
    }

    /**
     * Vérifie si le sujet OU le corps de l'email correspond aux mots-clés configurés.
     * Si aucun mot-clé n'est configuré, tous les emails passent.
     * La recherche est effectuée sur le sujet + corps combinés pour ne rater
     * aucun email pertinent (les clients ne mettent pas toujours les bons termes dans le sujet).
     */
    private boolean matchesKeywords(String subject, String body) {
        if (filterKeywords == null || filterKeywords.isEmpty()
                || (filterKeywords.size() == 1 && filterKeywords.get(0).isBlank())) {
            return true; // Pas de filtre → tous les emails passent
        }
        String combined = ((subject != null ? subject : "") + " " + (body != null ? body : "")).toLowerCase();
        return filterKeywords.stream()
                .filter(k -> !k.isBlank())
                .anyMatch(keyword -> combined.contains(keyword.toLowerCase()));
    }
}
