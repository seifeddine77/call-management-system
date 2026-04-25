package com.pfe.callmgmt.ticket.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Twilio VoIP.
 * Initialise le SDK Twilio au démarrage avec les credentials .env.
 */
@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "twilio")
public class TwilioConfig {

    private String accountSid;
    private String authToken;
    private String phoneNumber;
    private String webhookBaseUrl;

    @PostConstruct
    public void init() {
        if (accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank()) {
            Twilio.init(accountSid, authToken);
            log.info("✅ Twilio SDK initialized — Account: {}...{}, Phone: {}",
                    accountSid.substring(0, 6),
                    accountSid.substring(accountSid.length() - 4),
                    phoneNumber);
        } else {
            log.warn("⚠️ Twilio credentials not configured — VoIP integration disabled");
        }
    }

    public boolean isEnabled() {
        return accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank();
    }
}
